package ru.muz.desktop

import androidx.compose.ui.window.application
import java.io.File
import kotlin.system.exitProcess
import ru.muz.desktop.ui.AriaWindow

fun main(args: Array<String>) {
    if (args.any { it.startsWith("--self-test") }) selfTest(args)
    if ("--version" in args) {
        println("Aria ${AppDirs.version}")
        return
    }
    configurePlatform()
    application {
        AriaWindow(onExit = ::exitApplication)
    }
}

private fun configurePlatform() {
    System.setProperty("apple.awt.application.name", "Aria")
    if (AppDirs.isLinux) {
        runCatching {
            val toolkit = java.awt.Toolkit.getDefaultToolkit()
            val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
            field.isAccessible = true
            field.set(toolkit, "Aria")
        }
    }
}

private fun selfTest(args: Array<String>): Nothing {
    val target = args.firstOrNull { it.startsWith("--self-test=") }?.substringAfter('=')
    val report = buildString {
        appendLine("version=${AppDirs.version}")
        appendLine("java=${System.getProperty("java.version")}")
        appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        val result = runCatching {
            org.jetbrains.skia.Surface.makeRasterN32Premul(32, 32).close()
            appendLine("skia=ok")
            Class.forName("javazoom.jl.decoder.Decoder")
            appendLine("mp3=ok")
            Class.forName("okhttp3.OkHttpClient")
            appendLine("http=ok")
        }
        result.exceptionOrNull()?.let { appendLine("error=${it::class.java.name}: ${it.message}") }
        appendLine(if (result.isSuccess) "status=ok" else "status=fail")
    }
    if (target != null) File(target).writeText(report) else print(report)
    exitProcess(if (report.contains("status=ok")) 0 else 1)
}
