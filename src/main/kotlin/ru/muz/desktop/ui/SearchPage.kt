package ru.muz.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.muz.desktop.AriaModel
import ru.muz.desktop.Screen
import ru.muz.text.tracksLabel
import ru.muz.yandex.PlaySource

@Composable
fun SearchField(model: AriaModel, focus: FocusRequester, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = SoftText, modifier = Modifier.size(20.dp))
        Box(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            if (model.query.isEmpty()) Text("Треки, исполнители, альбомы", color = SoftText, fontSize = 15.sp)
            BasicTextField(
                value = model.query,
                onValueChange = { model.search(it) },
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (model.query.isNotEmpty()) RoundIcon(Icons.Filled.Close, "Очистить", size = 26.dp) { model.search("") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchPage(model: AriaModel, focus: FocusRequester) {
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val s by model.player.state.collectAsState()
    val r = model.results
    LazyColumn(contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Поиск", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            SearchField(model, focus)
        }
        when {
            model.query.isBlank() -> item {
                SectionHeader("Быстрый старт")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Моя волна", Icons.Filled.PlayArrow, Accent) { model.playWave() }
                    Tile("Мне нравится", Icons.Filled.Favorite, Color(0xFFFF6B9E)) { model.navigate(Screen.Liked) }
                    Tile("Чарт", Icons.Filled.QueueMusic, Color(0xFF77B8FF)) { model.navigate(Screen.Chart) }
                    Tile("Коллекция", Icons.Filled.LibraryMusic, Color(0xFF73DC8C)) { model.root(Screen.Collection) }
                }
            }
            model.searching && r.tracks.isEmpty() -> item { Loading(text = "Ищем…") }
            model.searchError.isNotBlank() -> item { EmptyState(Icons.Filled.Search, "Поиск не удался", model.searchError) }
            r.tracks.isEmpty() && r.artists.isEmpty() && r.albums.isEmpty() && r.playlists.isEmpty() -> item {
                EmptyState(Icons.Filled.Search, "Ничего не нашлось", "Попробуйте написать иначе")
            }
            else -> {
                if (r.tracks.isNotEmpty()) {
                    item { SectionHeader("Треки") }
                    itemsIndexed(r.tracks.take(12)) { index, track ->
                        TrackRow(model, track, index + 1, showAlbum = true, current = s.current?.id == track.id, playing = s.isPlaying) {
                            model.play(r.tracks, index, PlaySource.search())
                        }
                    }
                }
                if (r.artists.isNotEmpty()) {
                    item {
                        SectionHeader("Исполнители")
                        LazyRow {
                            items(r.artists) { a ->
                                MediaCard(a.name, if (a.trackCount > 0) tracksLabel(a.trackCount) else "Исполнитель", a.coverUrl, width = 150.dp, round = true) {
                                    model.navigate(Screen.Artist(a))
                                }
                            }
                        }
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item {
                        SectionHeader("Альбомы")
                        LazyRow {
                            items(r.albums) { a ->
                                MediaCard(a.title, a.artist, a.coverUrl, width = 160.dp, onPlay = { model.playScreen(Screen.Album(a)) }) { model.navigate(Screen.Album(a)) }
                            }
                        }
                    }
                }
                if (r.playlists.isNotEmpty()) {
                    item {
                        SectionHeader("Плейлисты")
                        LazyRow {
                            items(r.playlists) { p ->
                                MediaCard(p.title, tracksLabel(p.trackCount), p.coverUrl, width = 160.dp, onPlay = { model.playScreen(Screen.Playlist(p)) }) { model.navigate(Screen.Playlist(p)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Row(
        Modifier.size(width = 230.dp, height = 84.dp).clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = if (hovered) 0.32f else 0.2f))
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionPage(model: AriaModel) {
    var tab by remember { mutableStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Коллекция", modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.headlineLarge)
            Row(Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Плейлисты", "Альбомы").forEachIndexed { i, title ->
                    Chip(title, i == tab) { tab = i }
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tab == 0) {
                        MediaCard("Мне нравится", tracksLabel(model.liked.size), model.liked.firstOrNull()?.coverUrl, width = 160.dp, onPlay = { model.playScreen(Screen.Liked) }) {
                            model.navigate(Screen.Liked)
                        }
                        model.playlists.forEach { p ->
                            MediaCard(p.title, tracksLabel(p.trackCount), p.coverUrl, width = 160.dp, onPlay = { model.playScreen(Screen.Playlist(p)) }) { model.navigate(Screen.Playlist(p)) }
                        }
                        model.personal.forEach { p ->
                            MediaCard(p.title, "Подборка Яндекса", p.coverUrl, width = 160.dp, onPlay = { model.playScreen(Screen.Playlist(p)) }) { model.navigate(Screen.Playlist(p)) }
                        }
                    } else {
                        model.albums.forEach { a ->
                            MediaCard(a.title, a.artist, a.coverUrl, width = 160.dp, onPlay = { model.playScreen(Screen.Album(a)) }) { model.navigate(Screen.Album(a)) }
                        }
                    }
                }
            }
            if (tab == 1 && model.albums.isEmpty() && !model.libraryLoading) {
                EmptyState(Icons.Filled.Album, "Альбомов нет", "Отметьте альбомы в Яндекс Музыке, и они появятся здесь")
            }
            if (model.libraryLoading && model.playlists.isEmpty()) Loading()
        }
    }
}

@Composable
fun Chip(title: String, active: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHover()
    Text(
        title,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (active) Accent else if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
