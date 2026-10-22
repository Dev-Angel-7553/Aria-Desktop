package ru.muz.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Density
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import ru.muz.desktop.ui.AriaTheme
import ru.muz.desktop.ui.LoginScreen
import ru.muz.desktop.ui.Shell

class ScreensTest {
    @Test
    fun render() {
        val target = System.getenv("ARIA_SCREENS") ?: return
        val dir = File(target).apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val model = runBlocking { withContext(Dispatchers.Main) { AriaModel(scope) } }

        fun shot(name: String, width: Int = 1440, height: Int = 900, prepare: () -> Unit = {}, content: @Composable () -> Unit) {
            runBlocking { withContext(Dispatchers.Main) { prepare() } }
            val scene = ImageComposeScene(width, height, Density(1f)) {
                AriaTheme(model.settings, model.coverAccent) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
            var time = 0L
            repeat(45) {
                scene.render(time)
                time += 100_000_000L
                Thread.sleep(120)
            }
            val image = scene.render(time)
            File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }

        shot("01-login") { LoginScreen(model) }
        runBlocking { withContext(Dispatchers.Main) { model.loadDemo() } }
        val focus = FocusRequester()
        shot("02-home") { Shell(model, focus) }
        shot("03-liked", prepare = { model.root(Screen.Liked) }) { Shell(model, focus) }
        shot("04-queue", prepare = { model.updateSettings { it.copy(sidePanel = SidePanel.QUEUE) } }) { Shell(model, focus) }
        shot("05-search", prepare = {
            model.updateSettings { it.copy(sidePanel = SidePanel.LYRICS) }
            model.root(Screen.Search)
        }) { Shell(model, focus) }
        shot("06-settings", prepare = { model.root(Screen.Settings) }) { Shell(model, focus) }
        shot("07-now-playing", prepare = {
            model.root(Screen.Home)
            model.nowPlayingOpen = true
        }) { Shell(model, focus) }
        shot("08-compact", width = 900, height = 700, prepare = { model.nowPlayingOpen = false }) { Shell(model, focus) }
        shot("09-light", prepare = { model.updateSettings { it.copy(theme = ThemeMode.LIGHT, accent = DesktopSettings.ACCENTS[4], coverAccent = false) } }) { Shell(model, focus) }
    }
}
