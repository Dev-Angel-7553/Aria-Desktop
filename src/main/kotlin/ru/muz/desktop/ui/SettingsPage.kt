package ru.muz.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.muz.desktop.AppDirs
import ru.muz.desktop.AriaModel
import ru.muz.desktop.DesktopSettings
import ru.muz.desktop.DesktopUpdate
import ru.muz.desktop.HomeSection
import ru.muz.desktop.StreamQuality
import ru.muz.desktop.ThemeMode

private enum class SettingsCategory(val title: String) {
    APPEARANCE("Оформление"),
    HOME("Главная"),
    LYRICS("Текст"),
    PLAYBACK("Плеер"),
    SYSTEM("Приложение"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(model: AriaModel) {
    val s = model.settings
    var category by remember { mutableStateOf(SettingsCategory.APPEARANCE) }
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(category, refresh) {
        if (category != SettingsCategory.PLAYBACK) return@LaunchedEffect
        cacheBytes = withContext(Dispatchers.IO) { model.cache.usedBytes() + Images.diskBytes() }
    }
    LazyColumn(contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Настройки", style = MaterialTheme.typography.headlineLarge)
            FlowRow(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsCategory.entries.forEach { tab ->
                    Chip(tab.title, tab == category) { category = tab }
                }
            }
        }
        if (category == SettingsCategory.SYSTEM) item("Аккаунт") {
            Section("Аккаунт") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Text(model.account?.displayName?.take(1)?.uppercase() ?: "?", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(model.account?.displayName ?: "—", fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(model.account?.login, if (model.account?.serviceAvailable == true) "Плюс активен" else "Плюс не найден").joinToString(" · "),
                            color = SoftText,
                            fontSize = 13.sp,
                        )
                    }
                    Pill("Выйти") { model.logout() }
                }
            }
        }
        if (category == SettingsCategory.APPEARANCE) item("Внешний вид") {
            Section("Внешний вид") {
                Label("Тема")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { mode -> Chip(mode.title, s.theme == mode) { model.updateSettings { it.copy(theme = mode) } } }
                }
                Label("Акцент")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DesktopSettings.ACCENTS.forEach { color ->
                        Swatch(Color(color), selected = s.accent == color && !s.coverAccent) {
                            model.updateSettings { it.copy(accent = color, coverAccent = false) }
                        }
                    }
                }
                CustomAccent(s.accent) { accent ->
                    model.updateSettings { it.copy(accent = accent, coverAccent = false) }
                }
                Toggle("Цвет из обложки", "Акцент подстраивается под текущий трек", s.coverAccent) { v -> model.updateSettings { it.copy(coverAccent = v) } }
                Label("Масштаб интерфейса: ${s.uiScale}%")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(90, 100, 110, 125, 150).forEach { v -> Chip("$v%", s.uiScale == v) { model.updateSettings { it.copy(uiScale = v) } } }
                }
                Toggle("Компактные списки", "Меньше места на каждый трек", s.compactLists) { value ->
                    model.updateSettings { it.copy(compactLists = value) }
                }
                Toggle("Подписи в меню", "Показывать названия разделов рядом с иконками", s.showNavigationLabels) { value ->
                    model.updateSettings { it.copy(showNavigationLabels = value) }
                }
            }
        }
        if (category == SettingsCategory.HOME) item("Разделы главной") {
            Section("Разделы главной") {
                HomeSections(model)
                Toggle("Анимация Моей волны", "", s.animateWave) { value ->
                    model.updateSettings { it.copy(animateWave = value) }
                }
            }
        }
        if (category == SettingsCategory.LYRICS) item("Текст песен") {
            Section("Текст песен") {
                Label("Размер: ${s.lyricsSize}")
                ThinSlider(
                    value = (s.lyricsSize - 16) / 32f,
                    onChange = { v -> model.updateSettings { it.copy(lyricsSize = (16 + v * 32).toInt()) } },
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                )
                Toggle("Караоке-заливка", "Строка заливается по словам, пока её поют", s.karaoke) { v -> model.updateSettings { it.copy(karaoke = v) } }
                Toggle("Текст по центру", "", s.centerLyrics) { value ->
                    model.updateSettings { it.copy(centerLyrics = value) }
                }
                Toggle("Автопрокрутка", "Следовать за строкой, которая звучит", s.lyricsAutoScroll) { value ->
                    model.updateSettings { it.copy(lyricsAutoScroll = value) }
                }
                Toggle("Перемотка по строке", "", s.lyricsTapToSeek) { value ->
                    model.updateSettings { it.copy(lyricsTapToSeek = value) }
                }
                Label("Сдвиг текста: ${if (s.lyricsOffsetMs > 0) "+" else ""}${"%.1f".format(s.lyricsOffsetMs / 1000f)} с")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("−0,5 с", false) { model.updateSettings { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs - 500).coerceAtLeast(-5000)) } }
                    Chip("Сбросить", s.lyricsOffsetMs == 0) { model.updateSettings { it.copy(lyricsOffsetMs = 0) } }
                    Chip("+0,5 с", false) { model.updateSettings { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + 500).coerceAtMost(5000)) } }
                }
            }
        }
        if (category == SettingsCategory.PLAYBACK) item("Воспроизведение") {
            Section("Воспроизведение") {
                Label("Качество")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StreamQuality.entries.forEach { q -> Chip(q.title, s.quality == q) { model.updateSettings { it.copy(quality = q) } } }
                }
                Toggle("Засчитывать прослушивания", "Треки попадают в историю Яндекса, «Моя волна» учится на пропусках", s.reportListening) { v ->
                    model.updateSettings { it.copy(reportListening = v) }
                }
                Toggle("Продолжать с того же места", "После запуска очередь и позиция восстанавливаются", s.resumePlayback) { v ->
                    model.updateSettings { it.copy(resumePlayback = v) }
                }
            }
        }
        if (category == SettingsCategory.PLAYBACK) item("Кэш") {
            Section("Кэш") {
                Text(
                    if (cacheBytes < 0) "Считаем…" else "Занято ${formatBytes(cacheBytes)} из ${s.cacheLimitMb / 1024f} ГБ".replace(".0 ГБ", " ГБ"),
                    color = SoftText,
                    fontSize = 13.sp,
                )
                Text("Прослушанные треки сохраняются и в следующий раз играют без загрузки", color = SoftText, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(512, 1024, 2048, 5120).forEach { mb ->
                        Chip(if (mb < 1024) "$mb МБ" else "${mb / 1024} ГБ", s.cacheLimitMb == mb) { model.updateSettings { it.copy(cacheLimitMb = mb) } }
                    }
                    Pill("Очистить") {
                        model.scope.launch {
                            withContext(Dispatchers.IO) {
                                model.cache.clear(keep = setOfNotNull(model.player.state.value.current?.id))
                                Images.clearDisk()
                            }
                            refresh++
                            model.toast("Кэш очищен")
                        }
                    }
                }
            }
        }
        if (category == SettingsCategory.SYSTEM) item("Обновления") {
            Section("Обновления") {
                Toggle("Проверять при запуске", "Сообщу, если на GitHub вышла новая версия", s.autoUpdates) { v -> model.updateSettings { it.copy(autoUpdates = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Pill("Проверить сейчас", Icons.Filled.SystemUpdate) { model.checkUpdates(silent = false) }
                    model.update?.let { u -> Pill("Скачать ${u.version}", Icons.Filled.OpenInNew, primary = true) { openUrl(u.pageUrl) } }
                }
            }
        }
        if (category == SettingsCategory.SYSTEM) item("Горячие клавиши") {
            Section("Горячие клавиши") {
                listOf(
                    "Пробел" to "Пауза и продолжение",
                    "Ctrl + → / ←" to "Следующий и предыдущий трек",
                    "Shift + → / ←" to "Перемотка на 10 секунд",
                    "Ctrl + ↑ / ↓" to "Громкость",
                    "Ctrl + L" to "Мне нравится",
                    "Ctrl + F" to "Поиск",
                    "F11 или Ctrl + Enter" to "Плеер во весь экран",
                    "Esc или Alt + ←" to "Назад",
                ).forEach { (keys, what) ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            keys,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(170.dp),
                        )
                        Text(what, color = SoftText, fontSize = 13.sp)
                    }
                }
            }
        }
        if (category == SettingsCategory.SYSTEM) item("О программе") {
            Section("О программе") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    appIcon?.let { androidx.compose.foundation.Image(it, null, modifier = Modifier.size(44.dp)) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Aria ${AppDirs.version}", fontWeight = FontWeight.Bold)
                        Text("Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")}", color = SoftText, fontSize = 12.sp)
                    }
                }
                Text(
                    "Неофициальный клиент. Проект не связан с Яндексом, для музыки нужна своя подписка Плюс.",
                    color = SoftText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Pill("GitHub", Icons.Filled.OpenInNew) { openUrl(DesktopUpdate.PAGE_URL) }
                    Pill("Папка настроек", Icons.Filled.OpenInNew) { openFolder(AppDirs.config) }
                }
            }
        }
    }
}

private fun openFolder(dir: java.io.File) {
    Thread {
        runCatching { java.awt.Desktop.getDesktop().open(dir) }.onFailure {
            val command = if (AppDirs.isWindows) listOf("explorer", dir.absolutePath) else listOf("xdg-open", dir.absolutePath)
            runCatching { ProcessBuilder(command).start() }
        }
    }.apply { isDaemon = true }.start()
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} МБ"
    else -> "${bytes / 1024} КБ"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Accent)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .pointerHoverIcon(PointerIcon.Hand).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = SoftText, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CustomAccent(accent: Int, onApply: (Int) -> Unit) {
    var input by remember(accent) { mutableStateOf("%06X".format(accent and 0xFFFFFF)) }
    val rgb = input.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(7) },
            label = { Text("Свой цвет") },
            prefix = { Text("#") },
            singleLine = true,
            isError = input.isNotBlank() && rgb == null,
            modifier = Modifier.width(132.dp),
        )
        androidx.compose.material3.TextButton(enabled = rgb != null, onClick = {
            rgb?.let { onApply(it or 0xFF000000.toInt()) }
        }) { Text("Применить") }
    }
}

@Composable
private fun HomeSections(model: AriaModel) {
    val visible = model.settings.homeSections
    val hidden = HomeSection.entries.filterNot(visible::contains)
    (visible + hidden).forEach { section ->
        androidx.compose.runtime.key(section) {
            val index = visible.indexOf(section)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(section.title, Modifier.weight(1f), fontSize = 14.sp)
                IconButton(enabled = index > 0, onClick = { moveSection(model, section, -1) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Переместить выше: ${section.title}")
                }
                IconButton(enabled = index >= 0 && index < visible.lastIndex, onClick = { moveSection(model, section, 1) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Переместить ниже: ${section.title}")
                }
                Switch(checked = index >= 0, onCheckedChange = { show ->
                    model.updateSettings { current ->
                        current.copy(homeSections = if (show) current.homeSections + section else current.homeSections - section)
                    }
                }, modifier = Modifier.padding(start = 6.dp).then(Modifier.semantics { contentDescription = section.title }))
            }
        }
    }
}

private fun moveSection(model: AriaModel, section: HomeSection, delta: Int) {
    model.updateSettings { settings ->
        val sections = settings.homeSections.toMutableList()
        val from = sections.indexOf(section)
        val to = from + delta
        if (from !in sections.indices || to !in sections.indices) return@updateSettings settings
        sections.add(to, sections.removeAt(from))
        settings.copy(homeSections = sections)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (hovered && !selected) color.copy(alpha = 0.8f) else color)
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
    }
}
