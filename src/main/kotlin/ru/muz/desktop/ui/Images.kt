package ru.muz.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import ru.muz.desktop.AppDirs
import ru.muz.theme.ColorMath

object Images {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val dir = File(AppDirs.cache, "images").also { it.mkdirs() }
    private val gate = Semaphore(6)
    private val memory = object : LinkedHashMap<String, ImageBitmap>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 400
    }
    private val accents = HashMap<String, Int>()

    fun cached(url: String?): ImageBitmap? = url?.let { synchronized(memory) { memory[it] } }

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cached(url) ?: runCatching {
                    val bytes = bytes(url) ?: return@runCatching null
                    Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()?.also { synchronized(memory) { memory[url] = it } }
            }
        }
    }

    suspend fun accent(url: String): Int? {
        synchronized(accents) { accents[url] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = bytes(sized(url, 100) ?: url) ?: return@runCatching null
                val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
                val small = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
                small.createGraphics().apply {
                    drawImage(source, 0, 0, 32, 32, null)
                    dispose()
                }
                ColorMath.dominantVibrant(small.getRGB(0, 0, 32, 32, null, 0, 32))
            }.getOrNull()?.also { synchronized(accents) { accents[url] = it } }
        }
    }

    private fun bytes(url: String): ByteArray? {
        val file = File(dir, hash(url))
        if (file.isFile && file.length() > 0) return file.readBytes()
        val request = Request.Builder().url(url).header("User-Agent", "Aria Desktop").build()
        val bytes = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()
        } ?: return null
        runCatching { file.writeBytes(bytes) }
        return bytes
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    fun clearDisk() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    fun diskBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }
}

private val SIZES = listOf(50, 75, 100, 150, 200, 300, 400, 600, 1000)

fun sized(url: String?, px: Int): String? {
    if (url == null) return null
    val target = SIZES.firstOrNull { it >= px } ?: 1000
    return url.replace(Regex("\\d{2,4}x\\d{2,4}"), "${target}x$target")
}

@Composable
fun rememberImage(url: String?, px: Int = 300): ImageBitmap? {
    val resolved = sized(url, px)
    val image by produceState(Images.cached(resolved), resolved) {
        value = resolved?.let { Images.load(it) }
    }
    return image
}
