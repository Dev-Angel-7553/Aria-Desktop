package ru.muz.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.muz.desktop.AriaModel
import ru.muz.desktop.LyricsState
import ru.muz.desktop.SidePanel
import ru.muz.lyrics.LyricLine
import ru.muz.lyrics.Lyrics

@Composable
fun SidePanelView(model: AriaModel, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val panel = model.settings.sidePanel
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelTab("Текст", Icons.Filled.Lyrics, panel == SidePanel.LYRICS) { model.updateSettings { it.copy(sidePanel = SidePanel.LYRICS) } }
            PanelTab("Очередь", Icons.Filled.QueueMusic, panel == SidePanel.QUEUE) { model.updateSettings { it.copy(sidePanel = SidePanel.QUEUE) } }
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Filled.Close, "Скрыть панель", size = 32.dp) {
                if (onClose != null) onClose() else model.updateSettings { it.copy(sidePanel = SidePanel.HIDDEN) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (panel == SidePanel.QUEUE) QueueView(model) else LyricsView(model, large = false)
        }
    }
}

@Composable
private fun PanelTab(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Row(
        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
            .background(if (active) Accent.copy(alpha = 0.16f) else if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = if (active) Accent else SoftText, modifier = Modifier.size(16.dp))
        Text(title, color = if (active) Accent else SoftText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun rememberSmoothPosition(model: AriaModel): Long {
    val s by model.player.state.collectAsState()
    var position by remember { mutableLongStateOf(s.positionMs) }
    var anchor by remember { mutableLongStateOf(s.positionMs) }
    var anchorAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.positionMs, s.isPlaying) {
        anchor = s.positionMs
        anchorAt = System.currentTimeMillis()
        position = s.positionMs
    }
    LaunchedEffect(s.isPlaying, s.status) {
        if (!s.isPlaying || s.isLoading) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            val estimate = anchor + (System.currentTimeMillis() - anchorAt)
            position = estimate.coerceAtMost(anchor + 400)
        }
    }
    return position
}

@Composable
fun LyricsView(model: AriaModel, large: Boolean) {
    val track = model.player.state.collectAsState().value.current
    when {
        track == null -> EmptyState(Icons.Filled.Lyrics, "Ничего не играет", "Включите трек, и здесь появится текст")
        model.lyricsState == LyricsState.LOADING -> {
            var slow by remember(track.id) { mutableStateOf(false) }
            LaunchedEffect(track.id) {
                delay(6_000)
                slow = true
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Loading(text = model.lyricsStage.ifBlank { "Ищем текст…" })
                if (slow) Pill("Искать заново", Icons.Filled.Refresh) { model.loadLyrics(track, refresh = true) }
            }
        }
        model.lyricsState == LyricsState.MISSING || model.lyrics == null -> EmptyState(
            Icons.Filled.Lyrics,
            "Текста нет",
            model.lyricsStage.ifBlank { "Для этого трека текст не нашёлся" },
        ) {
            Pill("Поискать ещё раз", Icons.Filled.Refresh) { model.loadLyrics(track, refresh = true) }
        }
        else -> LyricsLines(model, model.lyrics!!, large)
    }
}

@Composable
private fun LyricsLines(model: AriaModel, lyrics: Lyrics, large: Boolean) {
    val position = rememberSmoothPosition(model) + model.settings.lyricsOffsetMs
    val active = if (lyrics.synced) lyrics.activeIndexAt(position) else -1
    val state = rememberLazyListState()
    val dragged by state.interactionSource.collectIsDraggedAsState()
    var manualUntil by remember { mutableLongStateOf(0L) }
    var wheel by remember { mutableStateOf(0) }
    val fontSize = if (large) (model.settings.lyricsSize + 10) else model.settings.lyricsSize
    LaunchedEffect(dragged, wheel) {
        if (dragged || wheel > 0) manualUntil = System.currentTimeMillis() + 4_000
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight
        LaunchedEffect(active, height) {
            if (active < 0) return@LaunchedEffect
            val wait = manualUntil - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }
            if (info != null) {
                val target = info.offset - (height * 0.32f).toInt()
                state.animateScrollBy(target.toFloat(), tween(650))
            } else {
                state.scrollToItem(active, -(height * 0.32f).toInt())
            }
        }
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = if (large) 0.dp else 16.dp, end = 16.dp, top = 40.dp, bottom = 320.dp),
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.1f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                }
                .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) wheel++
                    }
                }
            },
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                LyricLineView(
                    line = line,
                    index = index,
                    active = active,
                    synced = lyrics.synced,
                    position = if (index == active) position else 0L,
                    karaoke = model.settings.karaoke,
                    fontSize = fontSize,
                    onClick = if (lyrics.synced) ({ model.player.seek((line.startMs - model.settings.lyricsOffsetMs).coerceAtLeast(0)) }) else null,
                )
            }
        }
    }
}

@Composable
private fun LyricLineView(
    line: LyricLine,
    index: Int,
    active: Int,
    synced: Boolean,
    position: Long,
    karaoke: Boolean,
    fontSize: Int,
    onClick: (() -> Unit)?,
) {
    val isActive = synced && index == active
    val distance = if (!synced || active < 0) 0 else kotlin.math.abs(index - active)
    val sung = synced && active >= 0 && index < active
    val base = MaterialTheme.colorScheme.onSurface
    val alpha by animateFloatAsState(
        when {
            !synced -> 0.9f
            isActive -> 1f
            sung -> 0.42f
            distance <= 2 -> 0.34f
            else -> 0.22f
        },
        tween(350),
    )
    val scale by animateFloatAsState(if (isActive) 1f else 0.94f, tween(350))
    val dim by animateColorAsState(if (isActive && karaoke) base.copy(alpha = 0.35f) else base.copy(alpha = alpha), tween(300))
    val (source, hovered) = rememberHover()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = line.text.ifBlank { "♪" }
    val progress = if (isActive && karaoke) line.progressAt(position) else 0f
    Box(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered && onClick != null) base.copy(alpha = 0.05f) else Color.Transparent)
            .then(
                if (onClick != null) Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = source, indication = null, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 8.dp, vertical = (fontSize * 0.28f).dp),
    ) {
        Text(
            text,
            color = dim,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.22f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            onTextLayout = { layout = it },
        )
        if (isActive) {
            Text(
                text,
                color = base,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.22f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.drawWithContent {
                    val result = layout
                    if (!karaoke || result == null) {
                        drawContent()
                        return@drawWithContent
                    }
                    val path = fillPath(result, progress, text.length)
                    clipPath(path) { this@drawWithContent.drawContent() }
                },
            )
        }
    }
}

private fun fillPath(layout: TextLayoutResult, progress: Float, length: Int): Path {
    val path = Path()
    if (progress <= 0f || length == 0) return path
    val chars = (progress * length).coerceIn(0f, length.toFloat())
    val whole = chars.toInt()
    for (line in 0 until layout.lineCount) {
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        when {
            whole >= end -> path.addRect(Rect(left, top, right, bottom))
            whole >= start -> {
                val x0 = layout.getHorizontalPosition(whole.coerceAtMost(end), usePrimaryDirection = true)
                val x1 = if (whole + 1 <= end) layout.getHorizontalPosition(whole + 1, usePrimaryDirection = true) else x0
                val x = x0 + (x1 - x0) * (chars - whole)
                path.addRect(Rect(left, top, x.coerceIn(left, right), bottom))
                break
            }
            else -> break
        }
    }
    return path
}

@Composable
fun QueueView(model: AriaModel) {
    val s by model.player.state.collectAsState()
    if (s.queue.isEmpty()) {
        EmptyState(Icons.Filled.QueueMusic, "Очередь пуста", "Включите плейлист или волну")
        return
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = s.index.coerceAtLeast(0))
    val upcoming by remember(s.queue, s.index) { derivedStateOf { s.queue.size - s.index - 1 } }
    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Далее: $upcoming", color = SoftText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (s.source.wave) Text("«Моя волна» подгружается сама", color = SoftText, fontSize = 12.sp)
            }
        }
        itemsIndexed(s.queue, key = { i, t -> "$i:${t.id}" }) { index, track ->
            QueueRow(model, track, index, index == s.index, s.isPlaying, past = index < s.index)
        }
    }
}

@Composable
private fun QueueRow(model: AriaModel, track: ru.muz.yandex.YandexClient.Track, index: Int, current: Boolean, playing: Boolean, past: Boolean) {
    val (source, hovered) = rememberHover()
    androidx.compose.foundation.ContextMenuArea(items = {
        listOf(
            androidx.compose.foundation.ContextMenuItem("Играть") { model.player.jumpTo(index) },
            androidx.compose.foundation.ContextMenuItem("Убрать из очереди") { model.player.removeAt(index) },
            androidx.compose.foundation.ContextMenuItem("Открыть исполнителя") { model.openArtist(track) },
            androidx.compose.foundation.ContextMenuItem("Скопировать ссылку") { model.copyLink(track) },
        )
    }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        current -> Accent.copy(alpha = 0.14f)
                        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null) { model.player.jumpTo(index) }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer { alpha = if (past && !current) 0.55f else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Cover(track.coverUrl, 40.dp, px = 100)
                if (current) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        PlayingBars(playing, color = Color.White)
                    }
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(track.title, color = if (current) Accent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = SoftText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hovered && !current) {
                RoundIcon(Icons.Filled.Close, "Убрать", size = 28.dp) { model.player.removeAt(index) }
            } else {
                Text(formatTime(track.durationMs), color = SoftText, fontSize = 12.sp)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
