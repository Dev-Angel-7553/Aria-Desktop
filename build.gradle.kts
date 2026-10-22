import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "ru.muz"

fun osName(value: String): String = value.lowercase().let {
    when {
        it.startsWith("win") -> "windows"
        it.startsWith("linux") -> "linux"
        it.startsWith("mac") || it == "osx" || it == "darwin" -> "macos"
        else -> throw GradleException("Неизвестная система «$value». Можно: linux, windows")
    }
}

fun archName(value: String): String = when (value.lowercase()) {
    "amd64", "x86_64", "x64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> throw GradleException("Неизвестная архитектура «$value». Можно: x64, arm64")
}

val hostOs = osName(System.getProperty("os.name"))
val hostArch = archName(System.getProperty("os.arch"))
val targetOs = (findProperty("target") as String?)?.takeIf { it.isNotBlank() }?.let(::osName) ?: hostOs
val targetArch = (findProperty("arch") as String?)?.takeIf { it.isNotBlank() }?.let(::archName) ?: hostArch
val appVersion = version.toString()
val mainClassName = "ru.muz.desktop.MainKt"
val javaFeature = 21

val composeRuntime = when ("$targetOs-$targetArch") {
    "linux-x64" -> compose.desktop.linux_x64
    "linux-arm64" -> compose.desktop.linux_arm64
    "windows-x64" -> compose.desktop.windows_x64
    else -> throw GradleException("Сборка под $targetOs-$targetArch пока не поддерживается. Можно: linux-x64, linux-arm64, windows-x64")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(composeRuntime)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("javazoom:jlayer:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

compose.desktop {
    application {
        mainClass = mainClassName
        jvmArgs("-Daria.version=$appVersion", "-Dfile.encoding=UTF-8")
    }
}

tasks.test {
    useJUnit()
}

tasks.jar {
    archiveFileName.set("aria.jar")
    manifest {
        attributes(
            "Main-Class" to mainClassName,
            "Implementation-Version" to appVersion,
        )
    }
}

tasks.register("printVersion") {
    doLast { println(appVersion) }
}

val toolCache = File(gradle.gradleUserHomeDir, "caches/aria-desktop")

fun adoptium(os: String, arch: String, image: String): String {
    val system = if (os == "macos") "mac" else os
    val cpu = if (arch == "arm64") "aarch64" else "x64"
    return "https://api.adoptium.net/v3/binary/latest/$javaFeature/ga/$system/$cpu/$image/hotspot/normal/eclipse"
}

fun openUrl(url: String): InputStream {
    var current = url
    repeat(10) {
        val connection = URI(current).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 30_000
        connection.readTimeout = 180_000
        connection.setRequestProperty("User-Agent", "aria-desktop-build")
        val code = connection.responseCode
        if (code in 300..399) {
            val location = connection.getHeaderField("Location") ?: throw GradleException("Пустое перенаправление: $current")
            current = URI(current).resolve(location).toString()
            connection.disconnect()
            return@repeat
        }
        if (code != 200) throw GradleException("Не удалось скачать $current: HTTP $code")
        return connection.inputStream
    }
    throw GradleException("Слишком много перенаправлений: $url")
}

fun download(url: String, target: File) {
    target.parentFile.mkdirs()
    val partial = File(target.parentFile, target.name + ".part")
    openUrl(url).use { input -> partial.outputStream().use { input.copyTo(it, 1 shl 16) } }
    if (!partial.renameTo(target)) throw GradleException("Не удалось сохранить $target")
}

fun ensureJre(os: String, arch: String): File {
    val dir = File(toolCache, "jre-$javaFeature-$os-$arch")
    val marker = File(dir, ".aria-ready")
    if (marker.exists()) return dir
    dir.deleteRecursively()
    dir.mkdirs()
    val zip = os == "windows"
    val archive = File(toolCache, "jre-$javaFeature-$os-$arch." + if (zip) "zip" else "tar.gz")
    if (!archive.exists()) {
        logger.lifecycle("Скачиваем Java $javaFeature для $os-$arch, это один раз…")
        download(adoptium(os, arch, "jre"), archive)
    }
    copy {
        from(if (zip) zipTree(archive) else tarTree(resources.gzip(archive)))
        into(dir)
        includeEmptyDirs = false
        eachFile {
            val segments = relativePath.segments
            relativePath = RelativePath(true, *segments.copyOfRange(1, segments.size))
        }
    }
    archive.delete()
    marker.writeText(javaFeature.toString())
    return dir
}

fun ensureWindowsLauncher(arch: String): File {
    val target = File(toolCache, "launcher-$javaFeature-windows-$arch.exe")
    if (target.exists()) return target
    logger.lifecycle("Берём запускатор Aria.exe из JDK для Windows, это один раз…")
    val found = mutableMapOf<String, ByteArray>()
    openUrl(adoptium("windows", arch, "jdk")).use { raw ->
        val jdk = ZipInputStream(raw.buffered(1 shl 16))
        while (true) {
            val entry = jdk.nextEntry ?: break
            if (!entry.name.endsWith("jmods/jdk.jpackage.jmod")) continue
            val magic = jdk.readNBytes(4)
            if (magic.size < 2 || magic[0] != 'J'.code.toByte() || magic[1] != 'M'.code.toByte()) {
                throw GradleException("jdk.jpackage.jmod в неожиданном формате")
            }
            val jmod = ZipInputStream(jdk)
            while (true) {
                val inner = jmod.nextEntry ?: break
                val name = inner.name.substringAfterLast('/')
                if (name == "jpackageapplauncherw.exe" || name == "jpackageapplauncher.exe") found[name] = jmod.readBytes()
            }
            break
        }
    }
    val bytes = found["jpackageapplauncherw.exe"] ?: found["jpackageapplauncher.exe"]
        ?: throw GradleException("В JDK для Windows не нашёлся запускатор jpackage")
    target.parentFile.mkdirs()
    target.writeBytes(bytes)
    return target
}

fun scaled(source: BufferedImage, size: Int): BufferedImage {
    var current = source
    while (current.width / 2 >= size) {
        val half = BufferedImage(current.width / 2, current.height / 2, BufferedImage.TYPE_INT_ARGB)
        half.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(current, 0, 0, half.width, half.height, null)
            dispose()
        }
        current = half
    }
    if (current.width == size) return current
    val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    result.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        drawImage(current, 0, 0, size, size, null)
        dispose()
    }
    return result
}

fun writeIco(png: File, target: File) {
    val source = ImageIO.read(png)
    val images = listOf(16, 24, 32, 48, 64, 128, 256).map { size ->
        size to ByteArrayOutputStream().also { ImageIO.write(scaled(source, size), "png", it) }.toByteArray()
    }
    val header = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
    header.putShort(0).putShort(1).putShort(images.size.toShort())
    var offset = 6 + 16 * images.size
    images.forEach { (size, bytes) ->
        header.put((if (size >= 256) 0 else size).toByte())
        header.put((if (size >= 256) 0 else size).toByte())
        header.put(0).put(0)
        header.putShort(1).putShort(32)
        header.putInt(bytes.size).putInt(offset)
        offset += bytes.size
    }
    target.parentFile.mkdirs()
    target.outputStream().use { out ->
        out.write(header.array())
        images.forEach { out.write(it.second) }
    }
}

fun hostTool(name: String): File? {
    val exe = if (hostOs == "windows") "$name.exe" else name
    val home = File(System.getProperty("java.home"))
    return listOf(File(home, "bin/$exe"), File(home.parentFile, "bin/$exe")).firstOrNull { it.isFile }
}

fun runCommand(vararg command: String) {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) throw GradleException("Команда упала: ${command.joinToString(" ")}\n$output")
}

val javaOptions = listOf(
    "-Daria.version=$appVersion",
    "-Dfile.encoding=UTF-8",
    "-Dsun.stdout.encoding=UTF-8",
    "-Xss2m",
)

val folderName = if (targetOs == "windows") "Aria" else "aria"
val imageRoot = layout.buildDirectory.dir("dist/image")
val archiveBase = "Aria-$appVersion-$targetOs-$targetArch"

val distImage = tasks.register("distImage") {
    group = "distribution"
    description = "Собирает папку приложения с Java внутри под -Ptarget (по умолчанию текущая система)"
    dependsOn(tasks.jar)
    inputs.files(configurations.runtimeClasspath)
    inputs.files(tasks.jar)
    inputs.dir("packaging")
    inputs.property("target", "$targetOs-$targetArch")
    outputs.dir(imageRoot)
    doLast {
        val root = imageRoot.get().asFile
        root.deleteRecursively()
        root.mkdirs()
        val image = File(root, folderName)
        val jre = ensureJre(targetOs, targetArch)
        val libs = configurations.runtimeClasspath.get().files + tasks.jar.get().archiveFile.get().asFile
        val icon = file("src/main/resources/aria.png")
        if (targetOs == "linux") {
            copy { from(jre); into(File(image, "runtime")); exclude(".aria-ready") }
            copy { from(libs); into(File(image, "lib")) }
            copy {
                from("packaging/linux")
                into(image)
                filter { line ->
                    line.replace("@VERSION@", appVersion)
                        .replace("@MAIN@", mainClassName)
                        .replace("@JAVA_OPTIONS@", javaOptions.joinToString(" "))
                }
            }
            File(image, "bin").mkdirs()
            File(image, "aria.sh").renameTo(File(image, "bin/aria"))
            copy { from(icon); into(image) }
            listOf("bin/aria", "install.sh", "uninstall.sh").forEach { File(image, it).setExecutable(true, false) }
        } else {
            val ico = File(temporaryDir, "Aria.ico")
            writeIco(icon, ico)
            val jpackage = if (hostOs == "windows") hostTool("jpackage") else null
            if (jpackage != null) {
                val input = File(temporaryDir, "input")
                input.deleteRecursively()
                copy { from(libs); into(input) }
                val runtime = File(temporaryDir, "runtime")
                runtime.deleteRecursively()
                copy { from(jre); into(runtime); exclude(".aria-ready") }
                val command = mutableListOf(
                    jpackage.absolutePath,
                    "--type", "app-image",
                    "--name", "Aria",
                    "--app-version", appVersion,
                    "--vendor", "Dev-Angel-7553",
                    "--description", "Aria — клиент Яндекс Музыки",
                    "--input", input.absolutePath,
                    "--main-jar", "aria.jar",
                    "--main-class", mainClassName,
                    "--runtime-image", runtime.absolutePath,
                    "--icon", ico.absolutePath,
                    "--dest", root.absolutePath,
                )
                javaOptions.forEach { command += listOf("--java-options", it) }
                runCommand(*command.toTypedArray())
            } else {
                copy { from(jre); into(File(image, "runtime")); exclude(".aria-ready") }
                copy { from(libs); into(File(image, "app")) }
                val cfg = buildString {
                    appendLine("[Application]")
                    libs.sortedBy { if (it.name == "aria.jar") 0 else 1 }.forEach { appendLine("app.classpath=\$APPDIR\\${it.name}") }
                    appendLine("app.mainclass=$mainClassName")
                    appendLine()
                    appendLine("[JavaOptions]")
                    appendLine("java-options=-Djpackage.app-version=$appVersion")
                    javaOptions.forEach { appendLine("java-options=$it") }
                }
                File(image, "app/Aria.cfg").writeText(cfg.replace("\n", "\r\n"))
                ensureWindowsLauncher(targetArch).copyTo(File(image, "Aria.exe"), overwrite = true)
                ico.copyTo(File(image, "Aria.ico"), overwrite = true)
                File(image, "runtime/bin").listFiles().orEmpty()
                    .filter { it.name.lowercase().let { n -> n.startsWith("vcruntime") || n.startsWith("msvcp") } && it.name.endsWith(".dll", true) }
                    .forEach { it.copyTo(File(image, it.name), overwrite = true) }
            }
            val debug = buildString {
                appendLine("@echo off")
                appendLine("cd /d \"%~dp0\"")
                appendLine("\"%~dp0runtime\\bin\\java.exe\" ${javaOptions.joinToString(" ")} -cp \"%~dp0app\\*\" $mainClassName %*")
                appendLine("pause")
            }
            File(image, "aria-debug.cmd").writeText(debug.replace("\n", "\r\n"))
        }
        logger.lifecycle("Готово: $image")
    }
}

val dist = if (targetOs == "windows") {
    tasks.register<Zip>("dist") {
        group = "distribution"
        description = "Архив для Windows с Aria.exe"
        dependsOn(distImage)
        from(imageRoot)
        archiveFileName.set("$archiveBase.zip")
        destinationDirectory.set(layout.buildDirectory.dir("dist"))
    }
} else {
    tasks.register<Tar>("dist") {
        group = "distribution"
        description = "Архив для Linux с установщиком"
        dependsOn(distImage)
        from(imageRoot) {
            eachFile {
                val inside = path.removePrefix("$folderName/")
                val executable = inside.startsWith("bin/") || inside.startsWith("runtime/bin/") ||
                    inside.endsWith(".sh") || inside == "runtime/lib/jspawnhelper" || inside == "runtime/lib/jexec"
                permissions { unix(if (executable) "755" else "644") }
            }
        }
        compression = Compression.GZIP
        archiveFileName.set("$archiveBase.tar.gz")
        destinationDirectory.set(layout.buildDirectory.dir("dist"))
    }
}

tasks.register("distAll") {
    group = "distribution"
    description = "Показывает, как собрать сразу под обе системы"
    doLast {
        println("Gradle собирает одну цель за запуск. Под обе системы:")
        println("  ./gradlew dist -Ptarget=linux")
        println("  ./gradlew dist -Ptarget=windows")
    }
}
