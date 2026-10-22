package ru.muz.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import ru.muz.desktop.AriaModel

@Composable
fun NowPlaying(model: AriaModel) {
    val s by model.player.state.collectAsState()
    val track = s.current
    var showQueue by remember { mutableStateOf(false) }
    val background = rememberImage(track?.coverUrl, 300)
    val base = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(base)) {
        if (background != null) {
            Image(
                background,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f; alpha = 0.55f }.blur(90.dp),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(base.copy(alpha = 0.35f), base.copy(alpha = 0.75f)))))
        if (track == null) {
            EmptyState(Icons.Filled.Lyrics, "Ничего не играет", "Включите что-нибудь", Modifier.align(Alignment.Center))
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
            val wide = maxWidth > 900.dp
            val coverSize = if (wide) min(maxWidth * 0.36f, maxHeight * 0.62f) else min(maxWidth * 0.6f, maxHeight * 0.42f)
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.then(if (wide) Modifier.fillMaxHeight().widthIn(max = coverSize + 40.dp) else Modifier.fillMaxWidth()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Cover(track.coverUrl, coverSize, shape = RoundedCornerShape(20.dp), modifier = Modifier.shadow(40.dp, RoundedCornerShape(20.dp)), px = 1000)
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.widthIn(max = coverSize + 40.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            LinkText(track.artist, size = 16, onClick = { model.openArtist(track) })
                        }
                        val liked = model.isLiked(track)
                        RoundIcon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Мне нравится", size = 44.dp, active = liked) {
                            model.toggleLike(track)
                        }
                    }
                    Transport(model, s, Modifier.widthIn(max = coverSize + 40.dp).fillMaxWidth().padding(top = 18.dp), large = true)
                    if (s.message.isNotBlank()) Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (wide) {
                    Spacer(Modifier.padding(start = 48.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (showQueue) QueueView(model) else LyricsView(model, large = true)
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wide) {
                    RoundIcon(if (showQueue) Icons.Filled.Lyrics else Icons.Filled.QueueMusic, if (showQueue) "Текст" else "Очередь", size = 40.dp) { showQueue = !showQueue }
                }
                RoundIcon(Icons.Filled.CloseFullscreen, "Свернуть (Esc)", size = 40.dp) { model.nowPlayingOpen = false }
            }
        }
    }
}
