package ru.muz.desktop

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.muz.lyrics.LrcParser
import ru.muz.lyrics.Lyrics
import ru.muz.yandex.YandexClient

class DesktopLyrics(
    private val yandex: YandexClient,
    http: OkHttpClient,
    private val dir: File = File(AppDirs.cache, "lyrics"),
) {
    private val lrclib = http.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = object : LinkedHashMap<String, Lyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 60
    }
    private val running = HashMap<String, Deferred<String?>>()

    init {
        dir.mkdirs()
    }

    fun cached(track: YandexClient.Track): Lyrics? {
        val key = key(track)
        synchronized(memory) { memory[key] }?.let { return it }
        val file = File(dir, "$key.lrc")
        if (!file.isFile) return null
        return runCatching { LrcParser.parse(file.readText()) }.getOrNull()
            ?.takeIf { it.lines.any { l -> l.text.isNotBlank() } }
            ?.also { synchronized(memory) { memory[key] = it } }
    }

    suspend fun load(track: YandexClient.Track, refresh: Boolean = false, onStage: (String) -> Unit = {}): Result<Lyrics> = runCatching {
        if (!refresh) cached(track)?.let { return@runCatching it }
        onStage("Ищем текст…")
        val raw = find(track, refresh) ?: error("Текст для этого трека не нашёлся")
        val parsed = LrcParser.parse(raw).takeIf { it.lines.any { l -> l.text.isNotBlank() } } ?: error("Текст для этого трека пуст")
        val key = key(track)
        synchronized(memory) { memory[key] = parsed }
        withContext(Dispatchers.IO) { runCatching { File(dir, "$key.lrc").writeText(raw) } }
        parsed
    }

    fun prefetch(track: YandexClient.Track) {
        if (cached(track) != null) return
        background.async { load(track) }
    }

    private suspend fun find(track: YandexClient.Track, refresh: Boolean): String? {
        val fromYandex = shared("y:${track.id}", refresh) { yandex.lyrics(track).getOrNull() }
        val fromLrclib = shared("l:${track.id}", refresh) { searchLrcLib(track) }
        val yandexText = withTimeoutOrNull(if (track.lyricsAvailable) 7_000L else 2_500L) { fromYandex.await() }?.takeIf { it.isNotBlank() }
        if (yandexText != null && isSynced(yandexText)) return yandexText
        val lrcText = withTimeoutOrNull(11_000) { fromLrclib.await() }?.takeIf { it.isNotBlank() }
        return when {
            lrcText != null && isSynced(lrcText) -> lrcText
            yandexText != null -> yandexText
            else -> lrcText
        }
    }

    private fun shared(key: String, refresh: Boolean, block: suspend () -> String?): Deferred<String?> = synchronized(running) {
        val existing = running[key]
        if (existing != null && !refresh && (existing.isActive || (existing.isCompleted && !existing.isCancelled))) {
            existing
        } else {
            background.async { runCatching { block() }.getOrNull() }.also { job ->
                running[key] = job
                if (running.size > 40) running.keys.take(running.size - 40).forEach(running::remove)
            }
        }
    }

    private fun isSynced(text: String): Boolean = Regex("\\[\\d{1,2}:\\d{2}").containsMatchIn(text)

    private suspend fun searchLrcLib(track: YandexClient.Track): String? {
        val title = clean(track.title)
        val artist = track.artist.split(",", " & ", " feat", " ft.").first().trim()
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                if (track.album.isNotBlank()) addQueryParameter("album_name", track.album)
                if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1000).toString())
            }
            .build().toString()
        get(exact)?.let { body ->
            val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            pick(obj)?.let { return it }
        }
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build().toString()
        val found = get(search)?.let { body -> runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() }
            .orEmpty().mapNotNull { it as? JsonObject }
        val best = found.sortedWith(
            compareBy<JsonObject> { if (text(it, "syncedLyrics").isNullOrBlank()) 1 else 0 }
                .thenBy { kotlin.math.abs((text(it, "duration")?.toDoubleOrNull() ?: 0.0) - track.durationMs / 1000.0) },
        ).firstOrNull { pick(it) != null }
        return pick(best)
    }

    private fun pick(obj: JsonObject?): String? {
        obj ?: return null
        return text(obj, "syncedLyrics")?.takeIf { it.isNotBlank() } ?: text(obj, "plainLyrics")?.takeIf { it.isNotBlank() }
    }

    private fun text(obj: JsonObject, key: String): String? = (obj[key] as? JsonPrimitive)?.takeUnless { it.content == "null" }?.content

    private suspend fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "Aria Desktop (github.com/Dev-Angel-7553/Aria-Desktop)").build()
        val call = lrclib.newCall(request)
        return runCatching {
            suspendCancellableCoroutine<String?> { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.use { if (it.isSuccessful) it.body?.string() else null }
                        if (cont.isActive) cont.resume(body)
                    }
                })
            }
        }.getOrNull()
    }

    private fun clean(value: String): String = value
        .replace(Regex("\\s*[\\[(].*?[\\])]\\s*"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun key(track: YandexClient.Track): String =
        MessageDigest.getInstance("SHA-1").digest(track.id.substringBefore(':').toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
}
