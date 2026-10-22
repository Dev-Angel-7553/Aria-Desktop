package ru.muz.desktop

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLogicTest {
    @Test
    fun settingsRoundTrip() {
        val original = DesktopSettings(theme = ThemeMode.AMOLED, accent = 0xFF77B8FF.toInt(), volume = 0.35f, lyricsSize = 30, sidePanel = SidePanel.QUEUE, uiScale = 125)
        assertEquals(original, SettingsStore.decode(SettingsStore.encode(original)))
    }

    @Test
    fun brokenSettingsFallBackToDefaults() {
        val decoded = SettingsStore.decode(JsonObject(emptyMap()))
        assertEquals(DesktopSettings(), decoded)
    }

    @Test
    fun settingsAreClamped() {
        val raw = SettingsStore.encode(DesktopSettings(volume = 3f, lyricsSize = 400, uiScale = 10))
        val decoded = SettingsStore.decode(raw)
        assertEquals(1f, decoded.volume)
        assertEquals(48, decoded.lyricsSize)
        assertEquals(75, decoded.uiScale)
    }

    @Test
    fun platformAssetIsPicked() {
        val body = """
            {"tag_name":"v0.3.0","html_url":"https://github.com/x/releases/v0.3.0","draft":false,"prerelease":false,
             "assets":[{"name":"Aria-0.3.0-windows-x64.zip","browser_download_url":"https://d/win.zip"},
                       {"name":"Aria-0.3.0-linux-x64.tar.gz","browser_download_url":"https://d/linux.tar.gz"}]}
        """.trimIndent()
        val linux = DesktopUpdate.parse(body, "-linux-x64.tar.gz")
        assertEquals("0.3.0", linux?.version)
        assertEquals("https://d/linux.tar.gz", linux?.downloadUrl)
        assertEquals("https://d/win.zip", DesktopUpdate.parse(body, "-windows-x64.zip")?.downloadUrl)
    }

    @Test
    fun draftsAreIgnored() {
        assertNull(DesktopUpdate.parse("""{"tag_name":"v9.0.0","draft":true,"assets":[]}"""))
    }

    @Test
    fun devBuildNeverOffersUpdates() {
        assertFalse(DesktopUpdate.isNewer("9.9.9", "dev"))
        assertTrue(DesktopUpdate.isNewer("0.3.0", "0.2.0"))
    }

    @Test
    fun platformSuffix() {
        assertEquals("-windows-x64.zip", DesktopUpdate.platformSuffix("Windows 11", "amd64"))
        assertEquals("-linux-arm64.tar.gz", DesktopUpdate.platformSuffix("Linux", "aarch64"))
    }

    @Test
    fun trackJsonRoundTrip() {
        val track = ru.muz.yandex.YandexClient.Track(
            id = "123", albumId = "45", title = "Песня", artist = "Артист", album = "Альбом",
            durationMs = 201_000, coverUrl = "https://x/600x600", explicit = true, lyricsAvailable = true, artistId = "9",
        )
        assertEquals(track, TrackJson.decode(TrackJson.encode(track)))
    }
}
