package ru.muz.desktop

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import ru.muz.yandex.PlaySource
import ru.muz.yandex.YandexClient

data class ResumeState(
    val tracks: List<YandexClient.Track>,
    val index: Int,
    val positionMs: Long,
    val from: String,
    val playlistId: String,
    val wave: Boolean,
) {
    val source: PlaySource get() = PlaySource(from = from, playlistId = playlistId, wave = wave)
}

object TrackJson {
    fun encode(track: YandexClient.Track): JsonObject = buildJsonObject {
        put("id", track.id)
        put("albumId", track.albumId)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("durationMs", track.durationMs)
        track.coverUrl?.let { put("coverUrl", it) }
        put("explicit", track.explicit)
        put("lyricsAvailable", track.lyricsAvailable)
        put("artistId", track.artistId)
    }

    fun decode(o: JsonObject): YandexClient.Track? {
        fun text(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = text("id")?.takeIf { it.isNotBlank() } ?: return null
        return YandexClient.Track(
            id = id,
            albumId = text("albumId").orEmpty(),
            title = text("title").orEmpty(),
            artist = text("artist").orEmpty(),
            album = text("album").orEmpty(),
            durationMs = (o["durationMs"] as? JsonPrimitive)?.longOrNull ?: 0L,
            coverUrl = text("coverUrl"),
            explicit = (o["explicit"] as? JsonPrimitive)?.booleanOrNull ?: false,
            lyricsAvailable = (o["lyricsAvailable"] as? JsonPrimitive)?.booleanOrNull ?: false,
            artistId = text("artistId").orEmpty(),
        )
    }
}

class ResumeStore(private val file: File = File(AppDirs.config, "resume.json")) {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(state: ResumeState) {
        val body = buildJsonObject {
            put("index", state.index)
            put("positionMs", state.positionMs)
            put("from", state.from)
            put("playlistId", state.playlistId)
            put("wave", state.wave)
            put("tracks", buildJsonArray { state.tracks.take(500).forEach { add(TrackJson.encode(it)) } })
        }
        runCatching { AppDirs.writePrivate(file, body.toString()) }
    }

    fun load(): ResumeState? = runCatching {
        if (!file.exists()) return null
        val o = json.parseToJsonElement(file.readText()).jsonObject
        val tracks = (o["tracks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(TrackJson::decode) }
        if (tracks.isEmpty()) return null
        ResumeState(
            tracks = tracks,
            index = ((o["index"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, tracks.lastIndex),
            positionMs = (o["positionMs"] as? JsonPrimitive)?.longOrNull ?: 0L,
            from = (o["from"] as? JsonPrimitive)?.content ?: PlaySource.other().from,
            playlistId = (o["playlistId"] as? JsonPrimitive)?.content.orEmpty(),
            wave = (o["wave"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }.getOrNull()

    fun clear() {
        file.delete()
    }
}
