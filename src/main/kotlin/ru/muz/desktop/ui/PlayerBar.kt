package ru.muz.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.muz.desktop.AriaModel
import ru.muz.desktop.PlayerSnapshot
import ru.muz.desktop.RepeatMode
import ru.muz.desktop.SidePanel

@Composable
fun PlayerBar(model: AriaModel) {
    val s by model.player.state.collectAsState()
    val track = s.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(84.dp).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp),
    ) {
        val compact = maxWidth < 860.dp
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(if (compact) 1.2f else 1f), verticalAlignment = Alignment.CenterVertically) {
                if (track != null) {
                    val (source, hovered) = rememberHover()
                    Box(
                        Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable { model.nowPlayingOpen = !model.nowPlayingOpen },
                    ) {
                        Cover(track.coverUrl, 56.dp, px = 200)
                        if (hovered) {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.OpenInFull, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f, fill = false)) {
                        Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinkText(track.artist, onClick = { model.openArtist(track) })
                    }
                    val liked = model.isLiked(track)
                    RoundIcon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "Убрать из «Мне нравится» (Ctrl+L)" else "Мне нравится (Ctrl+L)",
                        active = liked,
                        modifier = Modifier.padding(start = 6.dp),
                    ) { model.toggleLike(track) }
                } else {
                    Text("Ничего не играет", color = SoftText)
                }
            }
            Transport(model, s, Modifier.weight(if (compact) 1.6f else 1.3f).widthIn(max = 640.dp))
            Row(Modifier.weight(if (compact) 0.8f else 1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val panel = model.settings.sidePanel
                RoundIcon(Icons.Filled.Lyrics, "Текст песни", active = panel == SidePanel.LYRICS) {
                    val show = panel != SidePanel.LYRICS || (!model.wideLayout && !model.panelOverlay)
                    model.panelOverlay = show
                    model.updateSettings { it.copy(sidePanel = if (show) SidePanel.LYRICS else SidePanel.HIDDEN) }
                }
                RoundIcon(Icons.Filled.QueueMusic, "Очередь", active = panel == SidePanel.QUEUE) {
                    val show = panel != SidePanel.QUEUE || (!model.wideLayout && !model.panelOverlay)
                    model.panelOverlay = show
                    model.updateSettings { it.copy(sidePanel = if (show) SidePanel.QUEUE else SidePanel.HIDDEN) }
                }
                if (!compact) {
                    Volume(model, s)
                }
                RoundIcon(Icons.Filled.OpenInFull, "Во весь экран", active = model.nowPlayingOpen) { model.nowPlayingOpen = !model.nowPlayingOpen }
            }
        }
    }
}

@Composable
fun Transport(model: AriaModel, s: PlayerSnapshot, modifier: Modifier = Modifier, large: Boolean = false) {
    val player = model.player
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    val duration = s.durationMs.coerceAtLeast(1)
    val position = if (seeking) (seekValue * duration).toLong() else s.positionMs
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 18.dp else 8.dp)) {
            RoundIcon(Icons.Filled.Shuffle, "Перемешать", active = s.shuffle, size = if (large) 44.dp else 34.dp) { player.toggleShuffle() }
            RoundIcon(Icons.Filled.SkipPrevious, "Назад (Ctrl+←)", size = if (large) 52.dp else 38.dp, enabled = s.current != null) { player.previous() }
            PlayButton(s.isPlaying, s.isLoading, if (large) 68.dp else 42.dp, enabled = s.current != null) { player.toggle() }
            RoundIcon(Icons.Filled.SkipNext, "Вперёд (Ctrl+→)", size = if (large) 52.dp else 38.dp, enabled = s.current != null && s.hasNext) { player.next() }
            RoundIcon(
                if (s.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                when (s.repeat) {
                    RepeatMode.OFF -> "Повтор выключен"
                    RepeatMode.ALL -> "Повтор очереди"
                    RepeatMode.ONE -> "Повтор трека"
                },
                active = s.repeat != RepeatMode.OFF,
                size = if (large) 44.dp else 34.dp,
            ) { player.cycleRepeat() }
        }
        Row(Modifier.fillMaxWidth().padding(top = if (large) 10.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(position), color = SoftText, fontSize = 11.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            ThinSlider(
                value = (position.toFloat() / duration).coerceIn(0f, 1f),
                buffered = s.buffered,
                onChange = {
                    seeking = true
                    seekValue = it
                },
                onFinished = {
                    seeking = false
                    player.seek((it * duration).toLong())
                },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text(formatTime(s.durationMs), color = SoftText, fontSize = 11.sp, modifier = Modifier.width(44.dp))
        }
        if (s.message.isNotBlank() && !large) {
            Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PlayButton(playing: Boolean, loading: Boolean, size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Tip(if (playing) "Пауза (пробел)" else "Играть (пробел)") {
        Box(
            Modifier.size(size).scale(if (hovered && enabled) 1.06f else 1f).clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(size * 0.56f),
            )
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(size),
                    color = Accent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
internal fun Volume(model: AriaModel, s: PlayerSnapshot) {
    val icon = when {
        s.muted || s.volume == 0f -> Icons.Filled.VolumeOff
        s.volume < 0.5f -> Icons.Filled.VolumeDown
        else -> Icons.Filled.VolumeUp
    }
    RoundIcon(icon, if (s.muted) "Включить звук" else "Выключить звук") { model.player.toggleMute() }
    ThinSlider(
        value = if (s.muted) 0f else s.volume,
        onChange = { model.player.setVolume(it) },
        onFinished = { v -> model.updateSettings { it.copy(volume = v) } },
        modifier = Modifier.width(110.dp).padding(end = 8.dp),
    )
    Spacer(Modifier.width(4.dp))
}
