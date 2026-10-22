package ru.muz.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.muz.desktop.AriaModel
import ru.muz.yandex.YandexClient

@Composable
fun TrackHeader(showAlbum: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", color = SoftText, fontSize = 12.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        Text("НАЗВАНИЕ", color = SoftText, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1.4f).padding(start = 56.dp))
        if (showAlbum) Text("АЛЬБОМ", color = SoftText, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Box(Modifier.width(40.dp))
        Text("ВРЕМЯ", color = SoftText, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    model: AriaModel,
    track: YandexClient.Track,
    number: Int,
    showAlbum: Boolean,
    current: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    val (source, hovered) = rememberHover()
    val liked = model.isLiked(track)
    ContextMenuArea(items = {
        listOf(
            ContextMenuItem("Играть") { onPlay() },
            ContextMenuItem("Играть следующим") { model.player.playTrackNext(track); model.toast("Сыграет следующим") },
            ContextMenuItem("Добавить в очередь") { model.player.enqueue(track); model.toast("Добавлено в очередь") },
            ContextMenuItem(if (liked) "Убрать из «Мне нравится»" else "Мне нравится") { model.toggleLike(track) },
            ContextMenuItem("Открыть исполнителя") { model.openArtist(track) },
            ContextMenuItem("Открыть альбом") { model.openAlbum(track) },
            ContextMenuItem("Скопировать ссылку") { model.copyLink(track) },
        )
    }) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        current -> Accent.copy(alpha = 0.12f)
                        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(source)
                .combinedClickable(interactionSource = source, indication = null, onDoubleClick = onPlay, onClick = {})
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                when {
                    hovered -> RoundIcon(Icons.Filled.PlayArrow, "Играть", size = 30.dp, onClick = onPlay)
                    current -> PlayingBars(playing)
                    else -> Text("$number", color = SoftText, fontSize = 13.sp)
                }
            }
            Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.coverUrl, 42.dp, modifier = Modifier.padding(start = 6.dp), px = 100)
                Column(Modifier.padding(start = 12.dp, end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            track.title,
                            color = if (current) Accent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (track.explicit) {
                            Box(
                                Modifier.padding(start = 6.dp).size(15.dp).clip(RoundedCornerShape(3.dp)).background(SoftText.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) { Text("E", fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.surface) }
                        }
                    }
                    LinkText(track.artist, onClick = { model.openArtist(track) })
                }
            }
            if (showAlbum) {
                Box(Modifier.weight(1f).padding(end = 12.dp)) {
                    LinkText(track.album, onClick = { model.openAlbum(track) })
                }
            }
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                if (liked || hovered) {
                    RoundIcon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "Убрать из «Мне нравится»" else "Мне нравится",
                        size = 30.dp,
                        active = liked,
                    ) { model.toggleLike(track) }
                }
            }
            Text(formatTime(track.durationMs), color = SoftText, fontSize = 13.sp, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun LinkText(text: String, modifier: Modifier = Modifier, size: Int = 13, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Text(
        text,
        color = if (hovered) MaterialTheme.colorScheme.onSurface else SoftText,
        fontSize = size.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (hovered) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        modifier = modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .combinedClickableNoRipple(onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(interactionSource = null, indication = null, onClick = onClick))
