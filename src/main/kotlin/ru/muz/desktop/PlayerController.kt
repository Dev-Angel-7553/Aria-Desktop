package ru.muz.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.muz.desktop.audio.AudioCache
import ru.muz.desktop.audio.Download
import ru.muz.desktop.audio.EngineStatus
import ru.muz.desktop.audio.Mp3Engine
import ru.muz.yandex.ListenEvent
import ru.muz.yandex.ListenSession
import ru.muz.yandex.PlaySource
import ru.muz.yandex.YandexClient

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerSnapshot(
    val queue: List<YandexClient.Track> = emptyList(),
    val index: Int = -1,
    val source: PlaySource = PlaySource.other(),
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val status: EngineStatus = EngineStatus.IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffered: Float = 0f,
    val message: String = "",
    val volume: Float = 0.8f,
    val muted: Boolean = false,
) {
    val current: YandexClient.Track? get() = queue.getOrNull(index)
    val isPlaying: Boolean get() = status == EngineStatus.PLAYING || status == EngineStatus.LOADING
    val isLoading: Boolean get() = status == EngineStatus.LOADING
    val hasNext: Boolean get() = index < queue.lastIndex || repeat == RepeatMode.ALL || source.wave
}

class PlayerController(
    private val scope: CoroutineScope,
    private val cache: AudioCache,
    private val resume: ResumeStore = ResumeStore(),
) {
    private val engine = Mp3Engine()
    private val snapshot = MutableStateFlow(PlayerSnapshot())
    val state: StateFlow<PlayerSnapshot> = snapshot.asStateFlow()

    var client: YandexClient? = null
    var quality: YandexClient.AudioQuality = YandexClient.AudioQuality.HIGH
    var reportListening: Boolean = true
    var onTrackChanged: (YandexClient.Track?) -> Unit = {}

    private val listen = ListenSession()
    private var original: List<YandexClient.Track> = emptyList()
    private var loadJob: Job? = null
    private var prefetch: Download? = null
    private var waveLoading = false
    private val retried = mutableSetOf<String>()
    private var pendingStartMs: Long? = null

    init {
        engine.onEnded = { id -> scope.launch { trackEnded(id) } }
        engine.onFailure = { id, error -> scope.launch { engineFailed(id, error) } }
        scope.launch {
            engine.states.collect { e ->
                snapshot.update {
                    if (pendingStartMs != null && e.status == EngineStatus.IDLE) it
                    else it.copy(
                        status = e.status,
                        positionMs = e.positionMs,
                        durationMs = e.durationMs.takeIf { d -> d > 0 } ?: it.current?.durationMs ?: 0,
                        buffered = e.buffered,
                        message = if (e.status == EngineStatus.ERROR) e.error else it.message,
                    )
                }
            }
        }
        scope.launch {
            var last = System.nanoTime()
            var ticks = 0
            while (isActive) {
                delay(250)
                val e = engine.tick()
                val now = System.nanoTime()
                val delta = (now - last) / 1_000_000
                last = now
                if (e.status == EngineStatus.PLAYING) send(listen.tick(delta, e.positionMs, e.durationMs))
                if (++ticks % 20 == 0) saveResume()
            }
        }
    }

    fun restore() {
        val saved = resume.load() ?: return
        original = saved.tracks
        pendingStartMs = saved.positionMs
        snapshot.update {
            it.copy(
                queue = saved.tracks,
                index = saved.index,
                source = saved.source,
                status = EngineStatus.IDLE,
                positionMs = saved.positionMs,
                durationMs = saved.tracks[saved.index].durationMs,
            )
        }
        onTrackChanged(saved.tracks[saved.index])
    }

    fun playQueue(tracks: List<YandexClient.Track>, index: Int, source: PlaySource) {
        if (tracks.isEmpty()) return
        val start = index.coerceIn(0, tracks.lastIndex)
        original = tracks
        val shuffle = snapshot.value.shuffle
        val queue = if (shuffle) listOf(tracks[start]) + (tracks.filterIndexed { i, _ -> i != start }).shuffled() else tracks
        snapshot.update { it.copy(queue = queue, index = if (shuffle) 0 else start, source = source, message = "") }
        retried.clear()
        startCurrent()
    }

    fun playTrackNext(track: YandexClient.Track) {
        val s = snapshot.value
        if (s.queue.isEmpty()) {
            playQueue(listOf(track), 0, PlaySource.other())
            return
        }
        val queue = s.queue.toMutableList().apply { add(s.index + 1, track) }
        original = original + track
        snapshot.update { it.copy(queue = queue) }
    }

    fun enqueue(track: YandexClient.Track) {
        val s = snapshot.value
        if (s.queue.isEmpty()) {
            playQueue(listOf(track), 0, PlaySource.other())
            return
        }
        original = original + track
        snapshot.update { it.copy(queue = it.queue + track) }
    }

    fun removeAt(position: Int) {
        val s = snapshot.value
        if (position !in s.queue.indices || position == s.index) return
        val removed = s.queue[position]
        original = original - removed
        snapshot.update {
            it.copy(queue = it.queue.filterIndexed { i, _ -> i != position }, index = if (position < it.index) it.index - 1 else it.index)
        }
    }

    fun move(from: Int, to: Int) {
        val s = snapshot.value
        if (from !in s.queue.indices || to !in s.queue.indices || from == to) return
        val list = s.queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        val current = s.current
        snapshot.update { it.copy(queue = list, index = list.indexOfFirst { t -> t === current }.coerceAtLeast(0)) }
    }

    fun jumpTo(position: Int) {
        if (position !in snapshot.value.queue.indices) return
        snapshot.update { it.copy(index = position) }
        startCurrent()
    }

    fun toggle() {
        val s = snapshot.value
        val current = s.current ?: return
        when {
            pendingStartMs != null -> startCurrent(pendingStartMs ?: 0)
            s.status == EngineStatus.IDLE -> startCurrent(0)
            s.status == EngineStatus.ENDED && engine.currentDownload?.trackId != current.id -> startCurrent(0)
            else -> engine.toggle()
        }
    }

    fun seek(positionMs: Long) {
        val pending = pendingStartMs
        if (pending != null || engine.currentDownload == null) {
            pendingStartMs = positionMs
            snapshot.update { it.copy(positionMs = positionMs) }
            return
        }
        snapshot.update { it.copy(positionMs = positionMs) }
        engine.seek(positionMs)
    }

    fun next() = advance(auto = false)

    fun previous() {
        val s = snapshot.value
        if (s.current == null) return
        if (s.positionMs > 3_000 || (s.index == 0 && s.repeat != RepeatMode.ALL)) {
            seek(0)
            return
        }
        val target = if (s.index == 0) s.queue.lastIndex else s.index - 1
        snapshot.update { it.copy(index = target) }
        startCurrent()
    }

    fun toggleShuffle() {
        val s = snapshot.value
        val current = s.current
        if (!s.shuffle) {
            val rest = s.queue.filterIndexed { i, _ -> i != s.index }.shuffled()
            val queue = listOfNotNull(current) + rest
            snapshot.update { it.copy(shuffle = true, queue = queue, index = if (current != null) 0 else -1) }
        } else {
            val queue = original.ifEmpty { s.queue }
            val index = current?.let { c -> queue.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
            snapshot.update { it.copy(shuffle = false, queue = queue, index = if (queue.isEmpty()) -1 else index) }
        }
    }

    fun cycleRepeat() {
        snapshot.update {
            it.copy(repeat = when (it.repeat) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            })
        }
    }

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        engine.volume = v
        engine.muted = false
        snapshot.update { it.copy(volume = v, muted = false) }
    }

    fun toggleMute() {
        val muted = !snapshot.value.muted
        engine.muted = muted
        snapshot.update { it.copy(muted = muted) }
    }

    fun stop() {
        loadJob?.cancel()
        engine.stop()
        send(listen.finish())
    }

    fun clearQueue() {
        stop()
        original = emptyList()
        pendingStartMs = null
        snapshot.value = PlayerSnapshot(volume = snapshot.value.volume, muted = snapshot.value.muted, shuffle = snapshot.value.shuffle, repeat = snapshot.value.repeat)
        resume.clear()
        onTrackChanged(null)
    }

    private fun advance(auto: Boolean) {
        val s = snapshot.value
        if (s.queue.isEmpty()) return
        val nextIndex = s.index + 1
        when {
            nextIndex <= s.queue.lastIndex -> {
                snapshot.update { it.copy(index = nextIndex) }
                startCurrent()
            }
            s.source.wave -> scope.launch {
                extendWave(force = true)
                if (snapshot.value.index + 1 <= snapshot.value.queue.lastIndex) {
                    snapshot.update { it.copy(index = it.index + 1) }
                    startCurrent()
                }
            }
            s.repeat == RepeatMode.ALL -> {
                snapshot.update { it.copy(index = 0) }
                startCurrent()
            }
            auto -> send(listen.finish())
            else -> Unit
        }
    }

    private fun startCurrent(startMs: Long = 0) {
        val track = snapshot.value.current ?: return
        pendingStartMs = null
        loadJob?.cancel()
        send(listen.start(track.id, track.albumId, track.durationMs, snapshot.value.source))
        onTrackChanged(track)
        snapshot.update {
            it.copy(status = EngineStatus.LOADING, positionMs = startMs, durationMs = track.durationMs, buffered = 0f, message = "")
        }
        loadJob = scope.launch {
            val download = prefetch?.takeIf { it.trackId == track.id && !it.isCancelled && it.error == null }
                ?: cache.open(track.id)
                ?: run {
                    val api = client ?: run {
                        fail("Сначала войдите в аккаунт")
                        return@launch
                    }
                    val url = api.streamUrl(track.id, quality).getOrElse {
                        fail(it.message ?: "Не удалось получить поток")
                        return@launch
                    }
                    cache.start(track.id, url) { scope.launch { prefetchNext() } }
                }
            prefetch?.takeIf { it !== download && !it.finished }?.cancel()
            prefetch = null
            engine.play(download, track.durationMs, startMs)
            if (download.finished) prefetchNext()
            saveResume()
            extendWave(force = false)
        }
    }

    private suspend fun prefetchNext() {
        val s = snapshot.value
        val next = s.queue.getOrNull(s.index + 1) ?: return
        if (cache.isComplete(next.id) || prefetch?.trackId == next.id) return
        val current = engine.currentDownload
        if (current != null && !current.finished) return
        val api = client ?: return
        val url = api.streamUrl(next.id, quality).getOrNull() ?: return
        if (snapshot.value.queue.getOrNull(snapshot.value.index + 1)?.id != next.id) return
        prefetch?.cancel()
        prefetch = cache.start(next.id, url)
    }

    private suspend fun extendWave(force: Boolean) {
        val s = snapshot.value
        if (!s.source.wave || waveLoading) return
        if (!force && s.index < s.queue.size - 3) return
        val api = client ?: return
        waveLoading = true
        try {
            val more = api.myWave(queue = s.current?.id).getOrNull().orEmpty()
            val known = snapshot.value.queue.map { it.id }.toSet()
            val fresh = more.filter { it.id !in known }
            if (fresh.isNotEmpty()) {
                original = original + fresh
                snapshot.update { it.copy(queue = it.queue + fresh) }
            }
        } finally {
            waveLoading = false
        }
    }

    private fun trackEnded(id: String) {
        val s = snapshot.value
        if (s.current?.id != id) return
        if (s.repeat == RepeatMode.ONE) {
            startCurrent(0)
            return
        }
        advance(auto = true)
    }

    private suspend fun engineFailed(id: String, error: Throwable) {
        val s = snapshot.value
        val track = s.current ?: return
        if (track.id != id) return
        val position = s.positionMs
        if (retried.add(id)) {
            val api = client ?: return fail(error.message ?: "Ошибка воспроизведения")
            val url = api.freshStreamUrl(id, quality).getOrNull() ?: return fail(error.message ?: "Ошибка воспроизведения")
            engine.play(cache.start(id, url), track.durationMs, position)
            return
        }
        fail("Трек не играет: ${error.message ?: "ошибка"}")
        delay(2_000)
        if (snapshot.value.current?.id == id && s.index < s.queue.lastIndex) advance(auto = true)
    }

    private fun fail(message: String) {
        snapshot.update { it.copy(status = EngineStatus.ERROR, message = message) }
    }

    private fun send(events: List<ListenEvent>) {
        if (events.isEmpty() || !reportListening) return
        val api = client ?: return
        scope.launch(Dispatchers.IO) {
            events.forEach { event ->
                when (event) {
                    is ListenEvent.PlayAudio -> api.reportPlay(event)
                    else -> api.waveFeedback(event)
                }
            }
        }
    }

    fun saveResume() {
        val s = snapshot.value
        if (s.queue.isEmpty() || s.index < 0) return
        resume.save(
            ResumeState(
                tracks = s.queue,
                index = s.index,
                positionMs = s.positionMs,
                from = s.source.from,
                playlistId = s.source.playlistId,
                wave = s.source.wave,
            ),
        )
    }

    fun close() {
        saveResume()
        val events = listen.finish()
        val api = client
        if (reportListening && api != null && events.isNotEmpty()) {
            runBlocking {
                withTimeoutOrNull(2_500) {
                    withContext(Dispatchers.IO) {
                        events.forEach { event ->
                            if (event is ListenEvent.PlayAudio) api.reportPlay(event) else api.waveFeedback(event)
                        }
                    }
                }
            }
        }
        prefetch?.cancel()
        engine.close()
    }
}
