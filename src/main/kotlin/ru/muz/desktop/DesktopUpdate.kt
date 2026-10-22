package ru.muz.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import ru.muz.update.AppUpdate

data class DesktopRelease(
    val version: String,
    val pageUrl: String,
    val downloadUrl: String,
    val notes: String,
)

object DesktopUpdate {
    const val REPO = "Dev-Angel-7553/Aria-Desktop"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val PAGE_URL = "https://github.com/$REPO"

    private val json = Json { ignoreUnknownKeys = true }

    fun platformSuffix(osName: String = System.getProperty("os.name"), arch: String = System.getProperty("os.arch")): String {
        val os = if (osName.lowercase().startsWith("win")) "windows" else "linux"
        val cpu = if (arch.lowercase() in setOf("aarch64", "arm64")) "arm64" else "x64"
        return "-$os-$cpu." + if (os == "windows") "zip" else "tar.gz"
    }

    fun parse(body: String, suffix: String = platformSuffix()): DesktopRelease? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if ((root["draft"] as? JsonPrimitive)?.content == "true" || (root["prerelease"] as? JsonPrimitive)?.content == "true") return null
        val tag = root.text("tag_name") ?: return null
        val asset = (root["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it.text("name").orEmpty().endsWith(suffix, ignoreCase = true) }
        DesktopRelease(
            version = tag.removePrefix("v").removePrefix("V"),
            pageUrl = root.text("html_url") ?: "$PAGE_URL/releases",
            downloadUrl = asset?.text("browser_download_url") ?: root.text("html_url") ?: "$PAGE_URL/releases",
            notes = root.text("body").orEmpty().trim(),
        )
    }.getOrNull()

    fun isNewer(candidate: String, current: String): Boolean = current != "dev" && AppUpdate.isNewer(candidate, current)
}
