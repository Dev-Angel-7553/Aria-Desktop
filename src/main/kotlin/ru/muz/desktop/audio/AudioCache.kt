package ru.muz.desktop.audio

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

class AudioCache(private val dir: File, private val http: OkHttpClient, var limitBytes: Long = 1L shl 30) {
    init {
        dir.mkdirs()
    }

    fun file(trackId: String): File = File(dir, safe(trackId) + ".mp3")

    private fun marker(trackId: String): File = File(dir, safe(trackId) + ".ok")

    fun isComplete(trackId: String): Boolean = marker(trackId).exists() && file(trackId).length() > 0

    fun open(trackId: String): Download? {
        if (!isComplete(trackId)) return null
        val target = file(trackId)
        target.setLastModified(System.currentTimeMillis())
        return Download.complete(trackId, target)
    }

    fun start(trackId: String, url: String, onComplete: () -> Unit = {}): Download {
        marker(trackId).delete()
        val target = file(trackId)
        target.parentFile?.mkdirs()
        runCatching { target.outputStream().close() }
        val download = Download(trackId, target, http)
        download.begin(url) {
            marker(trackId).writeText("ok")
            trim(setOf(trackId))
            onComplete()
        }
        return download
    }

    fun usedBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    fun clear(keep: Set<String> = emptySet()) {
        val kept = keep.map(::safe).toSet()
        dir.listFiles().orEmpty()
            .filter { it.nameWithoutExtension !in kept && it.lastModified() < System.currentTimeMillis() - 60_000L }
            .forEach { it.delete() }
    }

    fun trim(keep: Set<String> = emptySet()) {
        val kept = keep.map(::safe).toSet()
        val fresh = System.currentTimeMillis() - 30 * 60_000L
        val files = dir.listFiles().orEmpty().filter { it.extension == "mp3" && it.lastModified() < fresh }
        var total = files.sumOf { it.length() }
        if (total <= limitBytes) return
        files.filter { it.nameWithoutExtension !in kept }.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= limitBytes) return
            total -= file.length()
            File(dir, file.nameWithoutExtension + ".ok").delete()
            file.delete()
        }
    }

    private fun safe(id: String): String = id.substringBefore(':').filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "track" }
}

class Download internal constructor(
    val trackId: String,
    val file: File,
    private val http: OkHttpClient?,
) {
    @Volatile var written: Long = 0
        private set
    @Volatile var total: Long = -1
        private set
    @Volatile var finished: Boolean = false
        private set
    @Volatile var error: Throwable? = null
        private set
    @Volatile private var cancelled = false
    private var call: Call? = null
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    val fraction: Float get() = if (finished) 1f else if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f

    internal fun begin(url: String, onComplete: () -> Unit) {
        val client = http ?: return
        Thread({
            try {
                val request = Request.Builder().url(url).header("User-Agent", "Aria Desktop").build()
                val active = client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build().newCall(request)
                call = active
                active.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Сервер ответил ${response.code}")
                    val body = response.body ?: throw IOException("Пустой ответ")
                    total = body.contentLength()
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out ->
                        val source = body.byteStream()
                        val buffer = ByteArray(64 * 1024)
                        while (!cancelled) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            out.flush()
                            written += read
                            signal()
                        }
                    }
                }
                if (!cancelled) {
                    if (total > 0 && written < total) throw IOException("Загрузка оборвалась")
                    total = written
                    finished = true
                    onComplete()
                }
            } catch (failure: Throwable) {
                if (!cancelled) error = failure
            } finally {
                signal()
            }
        }, "aria-download").apply { isDaemon = true }.start()
    }

    fun cancel() {
        cancelled = true
        runCatching { call?.cancel() }
        signal()
    }

    val isCancelled: Boolean get() = cancelled

    fun awaitBytes(position: Long, timeoutMs: Long = 250) {
        lock.withLock {
            if (written <= position && !finished && error == null && !cancelled) changed.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    fun awaitTotal(timeoutMs: Long = 15_000): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (total < 0 && !finished && error == null && !cancelled && System.currentTimeMillis() < deadline) {
            lock.withLock { changed.await(100, TimeUnit.MILLISECONDS) }
        }
        return total
    }

    private fun signal() {
        lock.withLock { changed.signalAll() }
    }

    fun stream(offset: Long): InputStream = GrowingInput(this, offset)

    companion object {
        fun complete(trackId: String, file: File) = Download(trackId, file, null).apply {
            written = file.length()
            total = written
            finished = true
        }
    }
}

private class GrowingInput(private val download: Download, start: Long) : InputStream() {
    private var raf: RandomAccessFile? = null
    private var position = start
    @Volatile private var closed = false

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (!closed) {
            val available = download.written - position
            val file = if (available > 0) handle() else null
            if (available > 0 && file != null) {
                file.seek(position)
                val n = file.read(b, off, minOf(len.toLong(), available).toInt())
                if (n > 0) {
                    position += n
                    return n
                }
            }
            if (available > 0 && file == null && download.file.exists().not()) throw IOException("Файл кэша пропал, загружаем заново")
            if (available > 0 && file == null && !download.file.exists()) throw IOException("Файл кэша пропал, загружаем заново")
            if (download.finished) return -1
            download.error?.let { throw IOException(it.message ?: "Ошибка загрузки", it) }
            if (download.isCancelled) return -1
            download.awaitBytes(position)
        }
        return -1
    }

    private fun handle(): RandomAccessFile? {
        raf?.let { return it }
        if (!download.file.exists()) return null
        return runCatching { RandomAccessFile(download.file, "r") }.getOrNull()?.also { raf = it }
    }

    override fun close() {
        closed = true
        runCatching { raf?.close() }
    }
}
