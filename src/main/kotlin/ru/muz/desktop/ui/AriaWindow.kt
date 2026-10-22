package ru.muz.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.muz.desktop.AriaModel
import ru.muz.desktop.Screen
import ru.muz.desktop.SidePanel
import ru.muz.desktop.audio.EngineStatus

@Composable
fun AriaWindow(onExit: () -> Unit) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val model = remember { AriaModel(scope) }
    val initial = model.settings
    val state = rememberWindowState(
        size = DpSize(initial.windowWidth.dp, initial.windowHeight.dp),
        position = if (initial.windowX != Int.MIN_VALUE) WindowPosition(initial.windowX.dp, initial.windowY.dp) else WindowPosition(Alignment.Center),
        placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
    )
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { model.start() }
    DisposableEffect(Unit) { onDispose { scope.cancel() } }
    fun close() {
        val position = state.position
        model.updateSettings {
            it.copy(
                maximized = state.placement == WindowPlacement.Maximized,
                windowWidth = if (state.placement == WindowPlacement.Floating) state.size.width.value.toInt() else it.windowWidth,
                windowHeight = if (state.placement == WindowPlacement.Floating) state.size.height.value.toInt() else it.windowHeight,
                windowX = if (position is WindowPosition.Absolute && state.placement == WindowPlacement.Floating) position.x.value.toInt() else it.windowX,
                windowY = if (position is WindowPosition.Absolute && state.placement == WindowPlacement.Floating) position.y.value.toInt() else it.windowY,
            )
        }
        model.close()
        onExit()
    }
    val s by model.player.state.collectAsState()
    val title = s.current?.let { "${it.title} — ${it.artist} · Aria" } ?: "Aria"
    Window(
        onCloseRequest = ::close,
        state = state,
        title = title,
        icon = appIcon?.let { BitmapPainter(it) },
        onPreviewKeyEvent = { event -> shortcut(model, event, searchFocus, state) },
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Spacebar && model.client != null) {
                model.player.toggle()
                true
            } else {
                false
            }
        },
    ) {
        window.minimumSize = java.awt.Dimension(720, 520)
        AriaTheme(model.settings, model.coverAccent) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    model.starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Loading(text = "Открываем Aria…") }
                    model.client == null -> LoginScreen(model)
                    else -> Shell(model, searchFocus)
                }
            }
        }
    }
}

private fun shortcut(model: AriaModel, event: KeyEvent, searchFocus: FocusRequester, window: androidx.compose.ui.window.WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown || model.client == null) return false
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    val player = model.player
    val s = player.state.value
    return when {
        ctrl && event.key == Key.DirectionRight -> { player.next(); true }
        ctrl && event.key == Key.DirectionLeft -> { player.previous(); true }
        ctrl && event.key == Key.DirectionUp -> { player.setVolume(s.volume + 0.05f); model.updateSettings { it.copy(volume = player.state.value.volume) }; true }
        ctrl && event.key == Key.DirectionDown -> { player.setVolume(s.volume - 0.05f); model.updateSettings { it.copy(volume = player.state.value.volume) }; true }
        ctrl && event.key == Key.L -> { s.current?.let(model::toggleLike); true }
        ctrl && event.key == Key.F -> {
            model.root(Screen.Search)
            runCatching { searchFocus.requestFocus() }
            true
        }
        ctrl && event.key == Key.Enter -> { model.nowPlayingOpen = !model.nowPlayingOpen; true }
        event.isShiftPressed && event.key == Key.DirectionRight -> { player.seek((s.positionMs + 10_000).coerceAtMost(s.durationMs)); true }
        event.isShiftPressed && event.key == Key.DirectionLeft -> { player.seek((s.positionMs - 10_000).coerceAtLeast(0)); true }
        event.key == Key.F11 -> {
            window.placement = if (window.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
            model.nowPlayingOpen = window.placement == WindowPlacement.Fullscreen
            true
        }
        event.key == Key.Escape && window.placement == WindowPlacement.Fullscreen -> { window.placement = WindowPlacement.Floating; true }
        event.key == Key.Escape && model.nowPlayingOpen -> { model.nowPlayingOpen = false; true }
        event.isAltPressed && event.key == Key.DirectionLeft -> { model.back(); true }
        else -> false
    }
}

@Composable
private fun Shell(model: AriaModel, searchFocus: FocusRequester) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        model.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxWidth < 980.dp
            val wideEnoughForPanel = maxWidth >= 1120.dp
            val panelOpen = model.settings.sidePanel != SidePanel.HIDDEN
            Row(Modifier.fillMaxSize()) {
                Sidebar(model, compact)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (panelOpen && !wideEnoughForPanel && !model.nowPlayingOpen) {
                        SidePanelView(model, Modifier.fillMaxSize())
                    } else {
                        Content(model, searchFocus)
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                }
                if (panelOpen && wideEnoughForPanel && !model.nowPlayingOpen) {
                    PanelDivider(model)
                    SidePanelView(model, Modifier.width(model.settings.sidePanelWidth.dp).fillMaxHeight())
                }
            }
        }
        PlayerBar(model)
    }
}

@Composable
private fun PanelDivider(model: AriaModel) {
    val density = LocalDensity.current
    Box(
        Modifier.width(6.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, delta ->
                    val dp = with(density) { delta.toDp().value }
                    model.updateSettings { it.copy(sidePanelWidth = (it.sidePanelWidth - dp).toInt().coerceIn(300, 640)) }
                }
            },
    )
}

@Composable
private fun Content(model: AriaModel, searchFocus: FocusRequester) {
    val target: Any = if (model.nowPlayingOpen) "now" else model.screen
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (targetState == "now" || initialState == "now") {
                (slideInVertically { it / 6 } + fadeIn()) togetherWith (slideOutVertically { it / 6 } + fadeOut())
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
    ) { current ->
        Column(Modifier.fillMaxSize()) {
            if (current != "now") TopBar(model)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (current) {
                    "now" -> NowPlaying(model)
                    Screen.Home -> HomePage(model)
                    Screen.Search -> SearchPage(model, searchFocus)
                    Screen.Collection -> CollectionPage(model)
                    Screen.Settings -> SettingsPage(model)
                    is Screen -> TracksPage(model, current)
                }
            }
        }
    }
}

@Composable
private fun TopBar(model: AriaModel) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(Icons.Filled.ArrowBack, "Назад (Alt+←)", enabled = model.canGoBack, size = 34.dp) { model.back() }
        Spacer(Modifier.weight(1f))
        model.update?.let { u ->
            Pill("Новая версия ${u.version}", Icons.Filled.SystemUpdate, primary = true) { openUrl(u.pageUrl) }
        }
    }
}

@Composable
private fun Sidebar(model: AriaModel, compact: Boolean) {
    val s by model.player.state.collectAsState()
    Column(
        Modifier.width(if (compact) 76.dp else 248.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(Modifier.padding(start = if (compact) 6.dp else 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            appIcon?.let { androidx.compose.foundation.Image(it, null, modifier = Modifier.size(32.dp)) }
            if (!compact) Text("Aria", fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(start = 10.dp))
        }
        NavItem(Icons.Filled.Home, "Главная", model.screen == Screen.Home && !model.nowPlayingOpen, compact) { model.root(Screen.Home) }
        NavItem(Icons.Filled.Search, "Поиск", model.screen == Screen.Search && !model.nowPlayingOpen, compact) { model.root(Screen.Search) }
        NavItem(Icons.Filled.LibraryMusic, "Коллекция", model.screen == Screen.Collection && !model.nowPlayingOpen, compact) { model.root(Screen.Collection) }
        NavItem(Icons.Filled.Favorite, "Мне нравится", model.screen == Screen.Liked && !model.nowPlayingOpen, compact) { model.root(Screen.Liked) }
        if (!compact) {
            Text("ПЛЕЙЛИСТЫ", color = SoftText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(model.playlists) { p ->
                    val open = (model.screen as? Screen.Playlist)?.playlist?.let { it.kind == p.kind && it.ownerUid == p.ownerUid && it.id == p.id } == true
                    PlaylistItem(p, open && !model.nowPlayingOpen, playing = s.isPlaying && s.source.playlistId == "${p.ownerUid}:${p.kind}") {
                        model.navigate(Screen.Playlist(p))
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        NavItem(Icons.Filled.Settings, "Настройки", model.screen == Screen.Settings && !model.nowPlayingOpen, compact) { model.root(Screen.Settings) }
        if (s.status == EngineStatus.ERROR && !compact && s.message.isNotBlank()) {
            Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 3, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, title: String, active: Boolean, compact: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    val content = @Composable {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        active -> Accent.copy(alpha = 0.16f)
                        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, title, tint = if (active) Accent else if (hovered) MaterialTheme.colorScheme.onSurface else SoftText, modifier = Modifier.size(22.dp))
            if (!compact) {
                Text(
                    title,
                    color = if (active) MaterialTheme.colorScheme.onSurface else if (hovered) MaterialTheme.colorScheme.onSurface else SoftText,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
    }
    if (compact) Tip(title) { content() } else content()
}

@Composable
private fun PlaylistItem(p: ru.muz.yandex.YandexClient.Playlist, active: Boolean, playing: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (active) Accent.copy(alpha = 0.12f) else if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(p.coverUrl, 36.dp, px = 100)
        Text(
            p.title,
            fontSize = 13.sp,
            color = if (active) Accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        if (playing) PlayingBars(true)
    }
}
