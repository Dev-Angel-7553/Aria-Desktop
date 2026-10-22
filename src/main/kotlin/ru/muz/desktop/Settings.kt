package ru.muz.desktop

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class ThemeMode(val title: String) { DARK("Тёмная"), AMOLED("Чёрная"), LIGHT("Светлая"), SYSTEM("Как в системе") }

enum class SidePanel { LYRICS, QUEUE, HIDDEN }

enum class StreamQuality(val title: String) { HIGH("Высокое"), ECONOMY("Экономия трафика") }

data class DesktopSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0xFFFFD33D.toInt(),
    val coverAccent: Boolean = true,
    val volume: Float = 0.8f,
    val reportListening: Boolean = true,
    val quality: StreamQuality = StreamQuality.HIGH,
    val lyricsSize: Int = 26,
    val karaoke: Boolean = true,
    val lyricsOffsetMs: Int = 0,
    val sidePanel: SidePanel = SidePanel.LYRICS,
    val sidePanelWidth: Int = 380,
    val cacheLimitMb: Int = 1024,
    val autoUpdates: Boolean = true,
    val uiScale: Int = 100,
    val resumePlayback: Boolean = true,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 820,
    val windowX: Int = Int.MIN_VALUE,
    val windowY: Int = Int.MIN_VALUE,
    val maximized: Boolean = false,
) {
    companion object {
        val ACCENTS = listOf(
            0xFFFFD33D.toInt(),
            0xFFFF8A3D.toInt(),
            0xFFFF6B9E.toInt(),
            0xFFB388FF.toInt(),
            0xFF77B8FF.toInt(),
            0xFF73DC8C.toInt(),
        )
    }
}

class SettingsStore(private val file: File = File(AppDirs.config, "settings.json")) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): DesktopSettings = runCatching {
        if (!file.exists()) return DesktopSettings()
        decode(json.parseToJsonElement(file.readText()).jsonObject)
    }.getOrElse { DesktopSettings() }

    fun save(settings: DesktopSettings) {
        runCatching { AppDirs.writePrivate(file, json.encodeToString(JsonObject.serializer(), encode(settings))) }
    }

    companion object {
        fun encode(s: DesktopSettings): JsonObject = buildJsonObject {
            put("theme", s.theme.name)
            put("accent", s.accent)
            put("coverAccent", s.coverAccent)
            put("volume", s.volume)
            put("reportListening", s.reportListening)
            put("quality", s.quality.name)
            put("lyricsSize", s.lyricsSize)
            put("karaoke", s.karaoke)
            put("lyricsOffsetMs", s.lyricsOffsetMs)
            put("sidePanel", s.sidePanel.name)
            put("sidePanelWidth", s.sidePanelWidth)
            put("cacheLimitMb", s.cacheLimitMb)
            put("autoUpdates", s.autoUpdates)
            put("uiScale", s.uiScale)
            put("resumePlayback", s.resumePlayback)
            put("windowWidth", s.windowWidth)
            put("windowHeight", s.windowHeight)
            put("windowX", s.windowX)
            put("windowY", s.windowY)
            put("maximized", s.maximized)
        }

        fun decode(o: JsonObject): DesktopSettings {
            val d = DesktopSettings()
            fun int(key: String, default: Int) = (o[key] as? JsonPrimitive)?.intOrNull ?: default
            fun bool(key: String, default: Boolean) = (o[key] as? JsonPrimitive)?.booleanOrNull ?: default
            fun float(key: String, default: Float) = (o[key] as? JsonPrimitive)?.floatOrNull ?: default
            fun text(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return DesktopSettings(
                theme = text("theme")?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.theme,
                accent = int("accent", d.accent) or 0xFF000000.toInt(),
                coverAccent = bool("coverAccent", d.coverAccent),
                volume = float("volume", d.volume).coerceIn(0f, 1f),
                reportListening = bool("reportListening", d.reportListening),
                quality = text("quality")?.let { v -> StreamQuality.entries.firstOrNull { it.name == v } } ?: d.quality,
                lyricsSize = int("lyricsSize", d.lyricsSize).coerceIn(16, 48),
                karaoke = bool("karaoke", d.karaoke),
                lyricsOffsetMs = int("lyricsOffsetMs", d.lyricsOffsetMs).coerceIn(-5000, 5000),
                sidePanel = text("sidePanel")?.let { v -> SidePanel.entries.firstOrNull { it.name == v } } ?: d.sidePanel,
                sidePanelWidth = int("sidePanelWidth", d.sidePanelWidth).coerceIn(300, 640),
                cacheLimitMb = int("cacheLimitMb", d.cacheLimitMb).coerceIn(128, 20_480),
                autoUpdates = bool("autoUpdates", d.autoUpdates),
                uiScale = int("uiScale", d.uiScale).coerceIn(75, 200),
                resumePlayback = bool("resumePlayback", d.resumePlayback),
                windowWidth = int("windowWidth", d.windowWidth).coerceAtLeast(640),
                windowHeight = int("windowHeight", d.windowHeight).coerceAtLeast(480),
                windowX = int("windowX", d.windowX),
                windowY = int("windowY", d.windowY),
                maximized = bool("maximized", d.maximized),
            )
        }
    }
}

class TokenVault(private val file: File = File(AppDirs.config, "session")) {
    fun read(): String? = runCatching { file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() } }.getOrNull()
        ?: System.getenv("YANDEX_MUSIC_TOKEN")?.trim()?.takeIf { it.isNotBlank() }

    fun save(token: String) = AppDirs.writePrivate(file, token)

    fun clear() {
        file.delete()
    }
}
