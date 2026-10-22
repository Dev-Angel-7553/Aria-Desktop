package ru.muz.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

object AppDirs {
    private val windows = System.getProperty("os.name").lowercase().startsWith("win")
    val isWindows: Boolean get() = windows
    val isLinux: Boolean = System.getProperty("os.name").lowercase().startsWith("linux")

    val config: File by lazy {
        val base = if (windows) {
            System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Roaming")
        } else {
            System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        }
        File(base, if (windows) "Aria" else "aria").also { it.mkdirs() }
    }

    val cache: File by lazy {
        val base = if (windows) {
            System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Local")
        } else {
            System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        }
        File(base, if (windows) "Aria/cache" else "aria").also { it.mkdirs() }
    }

    val music: File by lazy {
        val home = File(System.getProperty("user.home"))
        val candidates = listOf(File(home, "Music"), File(home, "Музыка"))
        (candidates.firstOrNull { it.isDirectory } ?: File(home, "Music")).let { File(it, "Aria") }
    }

    fun writePrivate(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        restrict(temp)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        restrict(file)
    }

    private fun restrict(file: File) {
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }.onFailure {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    val version: String = System.getProperty("aria.version")
        ?: AppDirs::class.java.`package`?.implementationVersion
        ?: "dev"
}
