package ru.muz.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatLength(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes >= 60) "${minutes / 60} ч ${minutes % 60} мин" else "$minutes мин"
}

fun openUrl(url: String) {
    Thread {
        val opened = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!opened) {
            val command = if (System.getProperty("os.name").lowercase().startsWith("win")) {
                listOf("rundll32", "url.dll,FileProtocolHandler", url)
            } else {
                listOf("xdg-open", url)
            }
            runCatching { ProcessBuilder(command).start() }
        }
    }.apply { isDaemon = true }.start()
}

@Composable
fun Cover(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.08f),
    px: Int = (size.value * 2).toInt(),
) {
    val image = rememberImage(url, px)
    Box(
        modifier.size(size).clip(shape).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = SoftText.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.38f))
        }
    }
}

@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
            ) {
                Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
    ) { content() }
}

@Composable
fun RoundIcon(
    icon: ImageVector,
    tip: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = if (active) Accent else MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val (source, hovered) = rememberHover()
    Tip(tip) {
        Box(
            modifier.size(size).clip(CircleShape)
                .background(if (hovered && enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, tip, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.58f))
        }
    }
}

@Composable
fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    buffered: Float = 0f,
    onFinished: (Float) -> Unit = {},
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val (source, hovered) = rememberHover()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else value.coerceIn(0f, 1f)
    val active = hovered || dragging
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val bufferColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val fill = if (active) Accent else color
    Canvas(
        modifier.height(18.dp)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val v = (offset.x / size.width).coerceIn(0f, 1f)
                    onChange(v)
                    onFinished(v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        onChange(dragValue)
                    },
                    onDragEnd = {
                        dragging = false
                        onFinished(dragValue)
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange(dragValue)
                }
            },
    ) {
        val h = if (active) 5.dp.toPx() else 3.5.dp.toPx()
        val y = size.height / 2 - h / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
        if (buffered > 0f) drawRoundRect(bufferColor, Offset(0f, y), Size(size.width * buffered.coerceIn(0f, 1f), h), r)
        drawRoundRect(fill, Offset(0f, y), Size(size.width * shown, h), r)
        if (active) drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * shown, size.height / 2))
    }
}

@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Accent) {
    val transition = rememberInfiniteTransition()
    val phases = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420 + i * 130, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        )
    }
    Canvas(modifier.size(14.dp)) {
        val bar = size.width / 5f
        phases.forEachIndexed { i, phase ->
            val k = if (playing) phase.value else listOf(0.45f, 0.75f, 0.55f)[i]
            val h = size.height * k
            drawRoundRect(color, Offset(i * bar * 2f, size.height - h), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 32.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            val (source, hovered) = rememberHover()
            Text(
                action,
                color = if (hovered) Accent else SoftText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onAction),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SoftText, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, color = SoftText, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier, text: String = "") {
    val transition = rememberInfiniteTransition()
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart))
    val accent = Accent
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                val local = ((t + i * 0.18f) % 1f)
                val k = if (local < 0.5f) local * 2 else (1 - local) * 2
                Box(Modifier.width(6.dp).height((10 + 22 * k).dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.5f + 0.5f * k)))
            }
        }
        if (text.isNotBlank()) Text(text, color = SoftText, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    val bg = when {
        primary -> if (hovered) Accent.copy(alpha = 0.9f) else Accent
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = if (icon != null) 16.dp else 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
