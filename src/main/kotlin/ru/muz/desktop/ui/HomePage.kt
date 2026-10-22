package ru.muz.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import ru.muz.desktop.AriaModel
import ru.muz.desktop.Screen
import ru.muz.text.playlistsLabel
import ru.muz.text.tracksLabel
import ru.muz.yandex.PlaySource

@Composable
fun HomePage(model: AriaModel) {
    val s by model.player.state.collectAsState()
    LazyColumn(contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.greeting(), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Filled.Refresh, "Обновить") { model.refreshLibrary() }
            }
            if (model.libraryError.isNotBlank()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(model.libraryError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth > 760.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        WaveCard(model, s.source.wave && s.isPlaying, Modifier.weight(1.6f))
                        LikedCard(model, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WaveCard(model, s.source.wave && s.isPlaying, Modifier.fillMaxWidth())
                        LikedCard(model, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (model.personal.isNotEmpty()) {
            item {
                SectionHeader("Собрано для вас")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(model.personal) { p ->
                        MediaCard(p.title, p.description.ifBlank { tracksLabel(p.trackCount) }, p.coverUrl, onPlay = { model.playScreen(Screen.Playlist(p)) }) {
                            model.navigate(Screen.Playlist(p))
                        }
                    }
                }
            }
        }
        if (model.playlists.isNotEmpty()) {
            item {
                SectionHeader("Ваши плейлисты", action = "Все") { model.root(Screen.Collection) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(model.playlists) { p ->
                        MediaCard(p.title, tracksLabel(p.trackCount), p.coverUrl, onPlay = { model.playScreen(Screen.Playlist(p)) }) {
                            model.navigate(Screen.Playlist(p))
                        }
                    }
                }
            }
        }
        if (model.chart.isNotEmpty()) {
            item {
                SectionHeader("Чарт", action = "Весь чарт") { model.navigate(Screen.Chart) }
            }
            itemsIndexed(model.chart.take(10)) { index, track ->
                TrackRow(
                    model = model,
                    track = track,
                    number = index + 1,
                    showAlbum = true,
                    current = s.current?.id == track.id,
                    playing = s.isPlaying,
                ) { model.play(model.chart, index, PlaySource.chart()) }
            }
        }
        if (model.libraryLoading && model.liked.isEmpty()) {
            item { Loading(text = "Загружаем вашу музыку…") }
        }
        if (model.albums.isNotEmpty()) {
            item {
                SectionHeader("Любимые альбомы")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(model.albums) { a ->
                        MediaCard(a.title, a.artist, a.coverUrl) { model.navigate(Screen.Album(a)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WaveCard(model: AriaModel, active: Boolean, modifier: Modifier) {
    val accent = Accent
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(if (active) 6000 else 14000, easing = LinearEasing), RepeatMode.Restart))
    val (source, hovered) = rememberHover()
    Box(
        modifier.height(220.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f), Color(0xFF1A1020))))
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null) { model.playWave() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (k in 0 until 4) {
                val t = (phase + k * 0.21f) * 2 * PI.toFloat()
                val center = Offset(w * (0.62f + 0.22f * cos(t + k)), h * (0.5f + 0.32f * sin(t * 1.3f + k)))
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f - k * 0.04f), Color.Transparent), center, radius = h * (0.7f + 0.15f * k)),
                    radius = h * (0.7f + 0.15f * k),
                    center = center,
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("МОЯ ВОЛНА", color = Color.Black.copy(alpha = 0.6f), fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                Text(
                    if (active) "Звучит сейчас" else "Бесконечный поток под ваш вкус",
                    color = Color.Black.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(if (hovered) 60.dp else 56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) PlayingBars(true, color = accent) else Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text(
                    "Учится на ваших лайках и пропусках",
                    color = Color.Black.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LikedCard(model: AriaModel, modifier: Modifier) {
    val (source, hovered) = rememberHover()
    val covers = model.liked.mapNotNull { it.coverUrl }.distinct().take(4)
    Box(
        modifier.height(220.dp).clip(RoundedCornerShape(28.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null) { model.navigate(Screen.Liked) },
    ) {
        Box(Modifier.align(Alignment.TopEnd).padding(20.dp).size(width = (72 + 40 * (covers.size - 1).coerceAtLeast(0)).dp, height = (72 + 8 * covers.size).dp)) {
            covers.forEachIndexed { i, url ->
                Cover(url, 72.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.offset(x = (i * 40).dp, y = (i * 8).dp).shadow(6.dp, RoundedCornerShape(12.dp)), px = 200)
            }
        }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
            Icon(Icons.Filled.Favorite, null, tint = Accent, modifier = Modifier.size(28.dp))
            Text("Мне нравится", fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
            Text(tracksLabel(model.liked.size) + " · " + playlistsLabel(model.playlists.size), color = SoftText, fontSize = 13.sp)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Играть", Icons.Filled.PlayArrow, primary = true) { model.play(model.liked, 0, PlaySource.liked()) }
                Pill("Вперемешку", Icons.Filled.Shuffle) { model.shuffle(model.liked, PlaySource.liked()) }
            }
        }
    }
}
