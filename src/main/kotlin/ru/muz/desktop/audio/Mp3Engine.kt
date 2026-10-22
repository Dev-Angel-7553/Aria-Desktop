package ru.muz.desktop.audio

import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.withLock

enum class EngineStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

data class EngineState(
    val status: EngineStatus = EngineStatus.IDLE,
    val trackId: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffered: Float = 0f,
    val error: String = "",
)

class Mp3Engine {
    private val state = MutableStateFlow(EngineState())
    val states: StateFlow<EngineState> = state.asStateFlow()

    @Volatile var volume: Float = 0.8f
    @Volatile var muted: Boolean = false
    var onEnded: (String) -> Unit = {}
    var onFailure: (String, Throwable) -> Unit = { _, _ -> }

    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var session: Session? = null
    private var line: SourceDataLine? = null
    private var lineFormat: AudioFormat? = null

    private inner class Session(
        val download: Download,
        val durationMs: Long,
        val startMs: Long,
    ) {
        @Volatile var stopped = false
        @Volatile var paused = false
        @Volatile var framesWritten = 0L
        @Volatile var sampleRate = 44_100f
        @Volatile var frameBytes = 4
        @Volatile var started = false
        val thread = Thread(::decode, "aria-decoder").apply { isDaemon = true }

        fun positionMs(): Long {
            if (!started) return startMs
            val active = line
            val buffered = if (active != null && active.isOpen) ((active.bufferSize - active.available()) / frameBytes).toLong() else 0L
            val played = (framesWritten - buffered).coerceAtLeast(0)
            return startMs + (played * 1000f / sampleRate).toLong()
        }

        private fun decode() {
            var input: InputStream? = null
            try {
                val offset = offsetFor(download, startMs, durationMs)
                input = download.stream(offset)
                val bitstream = Bitstream(input)
                val decoder = Decoder()
                var failures = 0
                while (!stopped) {
                    if (paused) {
                        line?.takeIf { it.isRunning }?.stop()
                        publish(EngineStatus.PAUSED)
                        lock.withLock { while (paused && !stopped) wake.await(200, TimeUnit.MILLISECONDS) }
                        if (stopped) break
                        line?.start()
                        publish(EngineStatus.PLAYING)
                    }
                    val header = try {
                        bitstream.readFrame()
                    } catch (error: Exception) {
                        if (++failures > 40) throw error
                        continue
                    } ?: break
                    val output = try {
                        decoder.decodeFrame(header, bitstream) as SampleBuffer
                    } catch (error: Exception) {
                        bitstream.closeFrame()
                        if (++failures > 40) throw error
                        continue
                    }
                    bitstream.closeFrame()
                    failures = 0
                    val channels = decoder.outputChannels.coerceAtLeast(1)
                    val rate = decoder.outputFrequency.toFloat()
                    val target = ensureLine(rate, channels) ?: throw IllegalStateException("Нет доступного звукового устройства")
                    sampleRate = rate
                    frameBytes = channels * 2
                    val samples = output.buffer
                    val count = output.bufferLength
                    val gain = if (muted) 0f else volume.coerceIn(0f, 1f).let { it * it }
                    val bytes = ByteArray(count * 2)
                    for (i in 0 until count) {
                        val value = (samples[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        bytes[i * 2] = value.toByte()
                        bytes[i * 2 + 1] = (value shr 8).toByte()
                    }
                    if (!started) {
                        started = true
                        target.start()
                        publish(EngineStatus.PLAYING)
                    }
                    var written = 0
                    while (written < bytes.size && !stopped) {
                        if (paused) break
                        written += target.write(bytes, written, bytes.size - written)
                    }
                    framesWritten += written / frameBytes
                    if (paused && written < bytes.size && !stopped) {
                        lock.withLock { while (paused && !stopped) wake.await(200, TimeUnit.MILLISECONDS) }
                        if (!stopped) {
                            target.start()
                            target.write(bytes, written, bytes.size - written)
                            framesWritten += (bytes.size - written) / frameBytes
                            publish(EngineStatus.PLAYING)
                        }
                    }
                }
                if (!stopped) {
                    line?.drain()
                    if (!stopped) {
                        publish(EngineStatus.ENDED, positionMs = durationMs.takeIf { it > 0 } ?: positionMs())
                        onEnded(download.trackId)
                    }
                }
            } catch (error: Throwable) {
                if (!stopped) {
                    state.value = state.value.copy(status = EngineStatus.ERROR, error = error.message ?: "Не удалось воспроизвести")
                    onFailure(download.trackId, error)
                }
            } finally {
                runCatching { input?.close() }
            }
        }

        fun publish(status: EngineStatus, positionMs: Long = positionMs()) {
            if (stopped) return
            state.value = state.value.copy(status = status, positionMs = positionMs, buffered = download.fraction, error = "")
        }
    }

    private fun offsetFor(download: Download, startMs: Long, durationMs: Long): Long {
        if (startMs <= 0 || durationMs <= 0) return 0
        val total = download.awaitTotal()
        if (total <= 0) return 0
        val header = ByteArray(10)
        download.stream(0).use { input ->
            var read = 0
            while (read < 10) {
                val n = input.read(header, read, 10 - read)
                if (n < 0) break
                read += n
            }
        }
        val tag = if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            10L + ((header[6].toLong() and 0x7F) shl 21) + ((header[7].toLong() and 0x7F) shl 14) +
                ((header[8].toLong() and 0x7F) shl 7) + (header[9].toLong() and 0x7F)
        } else {
            0L
        }
        val ratio = (startMs.toDouble() / durationMs).coerceIn(0.0, 0.999)
        return tag + ((total - tag) * ratio).toLong()
    }

    private fun ensureLine(rate: Float, channels: Int): SourceDataLine? {
        val format = AudioFormat(rate, 16, channels, true, false)
        val current = line
        if (current != null && current.isOpen && lineFormat?.matches(format) == true) return current
        runCatching {
            current?.stop()
            current?.close()
        }
        val created = runCatching {
            AudioSystem.getSourceDataLine(format).apply {
                open(format, (rate * channels * 2 * 0.35f).toInt().let { it - it % (channels * 2) })
            }
        }.getOrNull()
        line = created
        lineFormat = if (created != null) format else null
        return created
    }

    fun play(download: Download, durationMs: Long, startMs: Long = 0, paused: Boolean = false) {
        lock.withLock {
            stopSession(keepDownload = download)
            line?.let { runCatching { it.stop(); it.flush() } }
            val next = Session(download, durationMs, startMs.coerceAtLeast(0))
            next.paused = paused
            session = next
            state.value = EngineState(
                status = if (paused) EngineStatus.PAUSED else EngineStatus.LOADING,
                trackId = download.trackId,
                positionMs = startMs,
                durationMs = durationMs,
                buffered = download.fraction,
            )
            next.thread.start()
        }
    }

    fun seek(positionMs: Long) {
        val current = lock.withLock { session } ?: return
        val target = positionMs.coerceIn(0, (current.durationMs - 500).coerceAtLeast(0))
        play(current.download, current.durationMs, target, paused = current.paused)
        if (current.paused) state.value = state.value.copy(status = EngineStatus.PAUSED, positionMs = target)
    }

    fun pause() {
        val current = session ?: return
        current.paused = true
        line?.takeIf { it.isRunning }?.stop()
        state.value = state.value.copy(status = EngineStatus.PAUSED, positionMs = current.positionMs())
    }

    fun resume() {
        val current = session ?: return
        if (state.value.status == EngineStatus.ENDED || state.value.status == EngineStatus.ERROR) {
            play(current.download, current.durationMs, if (state.value.status == EngineStatus.ENDED) 0 else current.positionMs())
            return
        }
        current.paused = false
        lock.withLock { wake.signalAll() }
    }

    fun toggle() {
        if (state.value.status == EngineStatus.PLAYING || state.value.status == EngineStatus.LOADING) pause() else resume()
    }

    fun stop() {
        lock.withLock {
            stopSession(keepDownload = null)
            line?.let { runCatching { it.stop(); it.flush() } }
            state.value = EngineState()
        }
    }

    fun tick(): EngineState {
        val current = session ?: return state.value
        val now = state.value
        if (now.status == EngineStatus.PLAYING || now.status == EngineStatus.PAUSED || now.status == EngineStatus.LOADING) {
            val updated = now.copy(positionMs = current.positionMs().coerceAtMost(current.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE), buffered = current.download.fraction)
            state.value = updated
            return updated
        }
        return now
    }

    val currentDownload: Download? get() = session?.download

    private fun stopSession(keepDownload: Download?) {
        val old = session ?: return
        old.stopped = true
        old.paused = false
        lock.withLock { wake.signalAll() }
        if (old.download !== keepDownload && !old.download.finished) old.download.cancel()
        session = null
    }

    fun close() {
        stop()
        runCatching { line?.close() }
        line = null
    }
}
