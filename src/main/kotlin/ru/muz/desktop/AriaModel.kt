package ru.muz.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.muz.auth.TokenInput
import ru.muz.desktop.audio.AudioCache
import ru.muz.desktop.ui.Images
import ru.muz.export.PlaylistCsv
import ru.muz.lyrics.Lyrics
import ru.muz.lyrics.LyricsRepository
import ru.muz.yandex.PlaySource
import ru.muz.yandex.YandexClient

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Collection : Screen
    data object Settings : Screen
    data object Liked : Screen
    data object Chart : Screen
    data class Playlist(val playlist: YandexClient.Playlist) : Screen
    data class Album(val album: YandexClient.Album) : Screen
    data class Artist(val artist: YandexClient.Artist) : Screen
}

data class TrackPage(
    val loading: Boolean = true,
    val tracks: List<YandexClient.Track> = emptyList(),
    val error: String = "",
)

enum class LyricsState { IDLE, LOADING, READY, MISSING }

class AriaModel(val scope: CoroutineScope) {
    private val settingsStore = SettingsStore()
    private val vault = TokenVault()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val cache = AudioCache(File(AppDirs.cache, "audio"), http)
    val player = PlayerController(scope, cache)

    var settings by mutableStateOf(settingsStore.load())
        private set

    var client by mutableStateOf<YandexClient?>(null)
        private set
    var account by mutableStateOf<YandexClient.Account?>(null)
        private set
    var starting by mutableStateOf(true)
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginMessage by mutableStateOf("")
        private set

    var liked by mutableStateOf<List<YandexClient.Track>>(emptyList())
        private set
    var likedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var playlists by mutableStateOf<List<YandexClient.Playlist>>(emptyList())
        private set
    var personal by mutableStateOf<List<YandexClient.Playlist>>(emptyList())
        private set
    var albums by mutableStateOf<List<YandexClient.Album>>(emptyList())
        private set
    var chart by mutableStateOf<List<YandexClient.Track>>(emptyList())
        private set
    var libraryLoading by mutableStateOf(false)
        private set
    var libraryError by mutableStateOf("")
        private set

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private val history = mutableStateListOf<Screen>()
    val canGoBack: Boolean get() = history.isNotEmpty()

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf(YandexClient.SearchResults())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf("")
        private set
    private var searchJob: Job? = null

    val pages = mutableStateMapOf<String, TrackPage>()

    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsState by mutableStateOf(LyricsState.IDLE)
        private set
    var lyricsStage by mutableStateOf("")
        private set
    private var lyricsJob: Job? = null
    private var lyricsRepo: LyricsRepository? = null

    var coverAccent by mutableStateOf<Int?>(null)
        private set
    var update by mutableStateOf<DesktopRelease?>(null)
        private set
    var nowPlayingOpen by mutableStateOf(false)

    private val messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = messageFlow

    private var saveJob: Job? = null

    init {
        applyToPlayer(settings)
        player.onTrackChanged = { track -> trackChanged(track) }
        if (settings.resumePlayback) player.restore()
    }

    fun start() {
        val saved = vault.read()
        if (saved == null) {
            starting = false
            return
        }
        connect(saved, remember = false)
        if (settings.autoUpdates) checkUpdates(silent = true)
    }

    fun toast(text: String) {
        messageFlow.tryEmit(text)
    }

    fun connect(raw: String, remember: Boolean = true) {
        val token = TokenInput.extract(raw)
        if (token.isBlank()) {
            loginMessage = "Вставьте токен или адрес страницы после входа"
            starting = false
            return
        }
        if (!TokenInput.looksValid(token)) {
            loginMessage = "Это не похоже на токен. Скопируйте адрес music.yandex.ru/#access_token=… целиком"
            starting = false
            return
        }
        loginBusy = true
        loginMessage = "Подключаемся к Яндекс Музыке…"
        scope.launch {
            val api = YandexClient(token, http)
            val info = api.accountInfo().getOrElse {
                loginMessage = it.message ?: "Не удалось войти"
                loginBusy = false
                starting = false
                return@launch
            }
            if (remember) runCatching { vault.save(token) }
            client = api
            account = info
            player.client = api
            lyricsRepo = LyricsRepository(api, http)
            loginBusy = false
            starting = false
            loginMessage = if (info.serviceAvailable) "" else "Подписка Плюс не найдена: часть треков может не играть"
            refreshLibrary()
            player.state.value.current?.let { trackChanged(it) }
        }
    }

    fun logout() {
        player.clearQueue()
        vault.clear()
        client = null
        account = null
        player.client = null
        liked = emptyList()
        likedIds = emptySet()
        playlists = emptyList()
        personal = emptyList()
        albums = emptyList()
        chart = emptyList()
        pages.clear()
        history.clear()
        screen = Screen.Home
        loginMessage = ""
    }

    fun refreshLibrary() {
        val api = client ?: return
        libraryLoading = true
        libraryError = ""
        scope.launch {
            val likedJob = async { api.likedTracks() }
            val playlistsJob = async { api.playlists() }
            val personalJob = async { api.personalPlaylists() }
            val albumsJob = async { api.likedAlbums() }
            val chartJob = async { api.chart() }
            likedJob.await().onSuccess {
                liked = it
                likedIds = it.map { t -> t.id.substringBefore(':') }.toSet()
                api.seedTracks(it)
            }.onFailure { libraryError = it.message ?: "Не удалось загрузить коллекцию" }
            playlistsJob.await().onSuccess { playlists = it }
            personalJob.await().onSuccess { personal = it }
            albumsJob.await().onSuccess { albums = it }
            chartJob.await().onSuccess { chart = it }
            libraryLoading = false
        }
    }

    fun navigate(target: Screen) {
        if (target == screen) return
        history.add(screen)
        if (history.size > 50) history.removeAt(0)
        screen = target
        nowPlayingOpen = false
    }

    fun root(target: Screen) {
        history.clear()
        screen = target
        nowPlayingOpen = false
    }

    fun back() {
        if (nowPlayingOpen) {
            nowPlayingOpen = false
            return
        }
        val previous = history.removeLastOrNull() ?: return
        screen = previous
    }

    fun search(text: String) {
        query = text
        searchJob?.cancel()
        if (text.isBlank()) {
            results = YandexClient.SearchResults()
            searching = false
            searchError = ""
            return
        }
        val api = client ?: return
        searchJob = scope.launch {
            delay(350)
            searching = true
            searchError = ""
            api.search(text).onSuccess { results = it }.onFailure { searchError = it.message ?: "Поиск не удался" }
            searching = false
        }
    }

    fun pageKey(screen: Screen): String = when (screen) {
        is Screen.Playlist -> "playlist:${screen.playlist.ownerUid}:${screen.playlist.kind}:${screen.playlist.id}:${screen.playlist.uuid}"
        is Screen.Album -> "album:${screen.album.id}"
        is Screen.Artist -> "artist:${screen.artist.id}"
        Screen.Chart -> "chart"
        else -> ""
    }

    fun loadPage(screen: Screen, refresh: Boolean = false) {
        val key = pageKey(screen)
        if (key.isEmpty()) return
        val existing = pages[key]
        if (!refresh && existing != null && (existing.loading || existing.error.isEmpty())) return
        val api = client ?: return
        pages[key] = TrackPage(loading = true, tracks = existing?.tracks.orEmpty())
        scope.launch {
            val result = fetch(api, screen)
            pages[key] = result.fold(
                onSuccess = { TrackPage(loading = false, tracks = it) },
                onFailure = { TrackPage(loading = false, error = it.message ?: "Не удалось загрузить") },
            )
        }
    }

    private suspend fun fetch(api: YandexClient, screen: Screen): Result<List<YandexClient.Track>> = when (screen) {
        is Screen.Playlist -> api.playlistTracks(screen.playlist)
        is Screen.Album -> api.albumTracks(screen.album)
        is Screen.Artist -> api.artistTracks(screen.artist)
        Screen.Chart -> api.chart()
        Screen.Liked -> Result.success(liked)
        else -> Result.success(emptyList())
    }

    fun playScreen(target: Screen, shuffled: Boolean = false) {
        val key = pageKey(target)
        val page = pages[key]
        val ready = if (target == Screen.Liked) liked else page?.tracks.orEmpty()
        fun begin(tracks: List<YandexClient.Track>) {
            if (shuffled) shuffle(tracks, sourceFor(target)) else play(tracks, 0, sourceFor(target))
        }
        if (ready.isNotEmpty() && page?.loading != true) {
            begin(ready)
            return
        }
        val api = client ?: return
        scope.launch {
            fetch(api, target).onSuccess { tracks ->
                if (key.isNotEmpty()) pages[key] = TrackPage(loading = false, tracks = tracks)
                if (tracks.isEmpty()) toast("Здесь пока пусто") else begin(tracks)
            }.onFailure { toast(it.message ?: "Не удалось загрузить") }
        }
    }

    fun sourceFor(screen: Screen): PlaySource = when (screen) {
        is Screen.Playlist -> PlaySource.playlist(screen.playlist.ownerUid, screen.playlist.kind)
        is Screen.Album -> PlaySource.album()
        is Screen.Artist -> PlaySource.artist()
        Screen.Chart -> PlaySource.chart()
        Screen.Liked -> PlaySource.liked()
        Screen.Search -> PlaySource.search()
        else -> PlaySource.other()
    }

    fun play(tracks: List<YandexClient.Track>, index: Int, source: PlaySource) {
        if (tracks.isEmpty()) return
        player.playQueue(tracks, index, source)
    }

    fun shuffle(tracks: List<YandexClient.Track>, source: PlaySource) {
        if (tracks.isEmpty()) return
        if (!player.state.value.shuffle) player.toggleShuffle()
        player.playQueue(tracks, tracks.indices.random(), source)
    }

    fun playWave() {
        val api = client ?: return
        scope.launch {
            toast("Запускаем «Мою волну»…")
            api.myWave().onSuccess { tracks ->
                if (tracks.isEmpty()) toast("Волна пока пустая") else player.playQueue(tracks, 0, PlaySource.wave())
            }.onFailure { toast(it.message ?: "Волна не запустилась") }
        }
    }

    fun isLiked(track: YandexClient.Track): Boolean = track.id.substringBefore(':') in likedIds

    fun toggleLike(track: YandexClient.Track) {
        val api = client ?: return
        val id = track.id.substringBefore(':')
        val target = id !in likedIds
        likedIds = if (target) likedIds + id else likedIds - id
        liked = if (target) listOf(track) + liked.filterNot { it.id.substringBefore(':') == id } else liked.filterNot { it.id.substringBefore(':') == id }
        scope.launch {
            api.setLiked(track.id, target).onFailure {
                likedIds = if (target) likedIds - id else likedIds + id
                if (target) liked = liked.filterNot { t -> t.id.substringBefore(':') == id } else liked = listOf(track) + liked
                toast(it.message ?: "Не удалось изменить «Мне нравится»")
            }.onSuccess {
                toast(if (target) "Добавлено в «Мне нравится»" else "Убрано из «Мне нравится»")
            }
        }
    }

    fun trackLink(track: YandexClient.Track): String = PlaylistCsv.trackUrl(track.id, track.albumId)

    fun copyLink(track: YandexClient.Track) {
        val link = trackLink(track)
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(link), null)
            toast("Ссылка скопирована")
        }
    }

    fun openArtist(track: YandexClient.Track) {
        if (track.artistId.isBlank()) {
            search(track.artist)
            navigate(Screen.Search)
            return
        }
        navigate(Screen.Artist(YandexClient.Artist(track.artistId, track.artist, null, 0)))
    }

    fun openAlbum(track: YandexClient.Track) {
        if (track.albumId.isBlank()) return
        navigate(Screen.Album(YandexClient.Album(track.albumId, track.album.ifBlank { track.title }, track.artist, track.coverUrl, 0)))
    }

    fun exportCsv(title: String, tracks: List<YandexClient.Track>, kind: PlaylistCsv.Kind = PlaylistCsv.Kind.PLAYLIST) {
        if (tracks.isEmpty()) return
        val dialog = FileDialog(null as Frame?, "Сохранить CSV", FileDialog.SAVE)
        dialog.file = PlaylistCsv.fileName(title)
        dialog.isVisible = true
        val name = dialog.file ?: return
        val target = File(dialog.directory ?: System.getProperty("user.home"), if (name.endsWith(".csv", true)) name else "$name.csv")
        scope.launch {
            runCatching {
                val rows = tracks.map {
                    PlaylistCsv.Row(it.title, it.artist, it.album, title, kind, it.durationMs, it.id, it.albumId)
                }
                withContext(Dispatchers.IO) { target.writeBytes(PlaylistCsv.encode(rows)) }
                toast("Сохранено: ${target.name}")
            }.onFailure { toast("Не удалось сохранить CSV") }
        }
    }

    fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) {
        val next = transform(settings)
        if (next == settings) return
        val coverChanged = next.coverAccent != settings.coverAccent
        settings = next
        applyToPlayer(next)
        if (coverChanged) player.state.value.current?.let { trackChanged(it, lyricsToo = false) }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            withContext(Dispatchers.IO) { settingsStore.save(next) }
        }
    }

    fun saveNow() {
        settingsStore.save(settings)
    }

    private fun applyToPlayer(s: DesktopSettings) {
        player.reportListening = s.reportListening
        player.quality = if (s.quality == StreamQuality.HIGH) YandexClient.AudioQuality.HIGH else YandexClient.AudioQuality.ECONOMY
        cache.limitBytes = s.cacheLimitMb.toLong() * 1024 * 1024
        if (player.state.value.volume != s.volume) player.setVolume(s.volume)
    }

    private fun trackChanged(track: YandexClient.Track?, lyricsToo: Boolean = true) {
        if (track == null) {
            lyrics = null
            lyricsState = LyricsState.IDLE
            coverAccent = null
            return
        }
        val cover = track.coverUrl
        if (settings.coverAccent && cover != null) {
            scope.launch { coverAccent = Images.accent(cover) }
        } else {
            coverAccent = null
        }
        if (lyricsToo) loadLyrics(track)
    }

    fun loadLyrics(track: YandexClient.Track, refresh: Boolean = false) {
        val repo = lyricsRepo ?: return
        lyricsJob?.cancel()
        lyrics = null
        lyricsState = LyricsState.LOADING
        lyricsStage = ""
        lyricsJob = scope.launch {
            repo.load(track, refresh) { stage -> lyricsStage = stage }
                .onSuccess {
                    lyrics = it
                    lyricsState = LyricsState.READY
                }
                .onFailure {
                    lyricsState = LyricsState.MISSING
                    lyricsStage = it.message ?: "Текста нет"
                }
        }
    }

    fun checkUpdates(silent: Boolean) {
        scope.launch {
            val release = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(DesktopUpdate.LATEST_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Aria Desktop")
                        .build()
                    http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
                }.getOrNull()?.let(DesktopUpdate::parse)
            }
            when {
                release != null && DesktopUpdate.isNewer(release.version, AppDirs.version) -> {
                    update = release
                    if (!silent) toast("Есть версия ${release.version}")
                }
                !silent && release == null -> toast("Не удалось проверить обновления")
                !silent -> toast("Стоит последняя версия")
            }
        }
    }

    fun greeting(): String {
        val hour = LocalTime.now().hour
        val part = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val name = account?.displayName?.takeIf { it.isNotBlank() }?.substringBefore(' ')
        return if (name != null) "$part, $name" else part
    }

    fun close() {
        settingsStore.save(settings)
        player.close()
    }
}
