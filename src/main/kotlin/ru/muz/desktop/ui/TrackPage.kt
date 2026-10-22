package ru.muz.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.muz.desktop.AriaModel
import ru.muz.desktop.Screen
import ru.muz.desktop.TrackPage
import ru.muz.export.PlaylistCsv
import ru.muz.text.tracksLabel
import ru.muz.yandex.YandexClient

private data class PageInfo(val kind: String, val title: String, val subtitle: String, val cover: String?, val round: Boolean = false)

@Composable
fun TracksPage(model: AriaModel, screen: Screen) {
    LaunchedEffect(screen, model.client) { if (screen != Screen.Liked) model.loadPage(screen) }
    val page: TrackPage = if (screen == Screen.Liked) {
        TrackPage(loading = model.libraryLoading && model.liked.isEmpty(), tracks = model.liked, error = if (model.liked.isEmpty()) model.libraryError else "")
    } else {
        model.pages[model.pageKey(screen)] ?: TrackPage()
    }
    val info = when (screen) {
        is Screen.Playlist -> PageInfo("ПЛЕЙЛИСТ", screen.playlist.title, screen.playlist.description, screen.playlist.coverUrl)
        is Screen.Album -> PageInfo("АЛЬБОМ", screen.album.title, screen.album.artist, screen.album.coverUrl ?: page.tracks.firstOrNull()?.coverUrl)
        is Screen.Artist -> PageInfo("ИСПОЛНИТЕЛЬ", screen.artist.name, "Популярные треки", screen.artist.coverUrl ?: page.tracks.firstOrNull()?.coverUrl, round = screen.artist.coverUrl != null)
        Screen.Chart -> PageInfo("ЧАРТ", "Чарт Яндекс Музыки", "Самое популярное прямо сейчас", page.tracks.firstOrNull()?.coverUrl)
        else -> PageInfo("КОЛЛЕКЦИЯ", "Мне нравится", model.account?.displayName.orEmpty(), null)
    }
    var filter by remember(screen) { mutableStateOf("") }
    val shown = remember(page.tracks, filter) {
        if (filter.isBlank()) page.tracks
        else page.tracks.filter { it.title.contains(filter, true) || it.artist.contains(filter, true) || it.album.contains(filter, true) }
    }
    val s by model.player.state.collectAsState()
    val source = model.sourceFor(screen)
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showAlbum = maxWidth > 820.dp && screen !is Screen.Album
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Header(model, info, page, screen, shown)
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Pill("Играть", Icons.Filled.PlayArrow, primary = true) { model.play(shown, 0, source) }
                    Pill("Вперемешку", Icons.Filled.Shuffle) { model.shuffle(shown, source) }
                    if (page.tracks.isNotEmpty()) {
                        RoundIcon(Icons.Filled.FileDownload, "Сохранить список в CSV") {
                            model.exportCsv(
                                info.title,
                                page.tracks,
                                if (screen == Screen.Liked) PlaylistCsv.Kind.FAVORITE else if (screen is Screen.Album) PlaylistCsv.Kind.ALBUM else PlaylistCsv.Kind.PLAYLIST,
                            )
                        }
                    }
                    if (screen != Screen.Liked) RoundIcon(Icons.Filled.Refresh, "Обновить") { model.loadPage(screen, refresh = true) }
                    Spacer(Modifier.weight(1f))
                    if (page.tracks.size > 12) FilterField(filter) { filter = it }
                }
            }
            if (shown.isNotEmpty()) item { Box(Modifier.padding(horizontal = 12.dp)) { TrackHeader(showAlbum) } }
            itemsIndexed(shown, key = { i, t -> "$i:${t.id}" }) { index, track ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    TrackRow(
                        model = model,
                        track = track,
                        number = index + 1,
                        showAlbum = showAlbum,
                        current = s.current?.id == track.id,
                        playing = s.isPlaying,
                    ) { model.play(shown, index, source) }
                }
            }
            when {
                page.loading && page.tracks.isEmpty() -> item { Loading(text = "Загружаем треки…") }
                page.error.isNotBlank() && page.tracks.isEmpty() -> item {
                    EmptyState(Icons.Filled.LibraryMusic, "Не загрузилось", page.error) {
                        Pill("Повторить", Icons.Filled.Refresh) { model.loadPage(screen, refresh = true) }
                    }
                }
                !page.loading && shown.isEmpty() -> item {
                    EmptyState(Icons.Filled.LibraryMusic, if (filter.isBlank()) "Здесь пусто" else "Ничего не нашлось", if (filter.isBlank()) "Треков пока нет" else "Попробуйте другой запрос")
                }
            }
        }
    }
}

@Composable
private fun Header(model: AriaModel, info: PageInfo, page: TrackPage, screen: Screen, shown: List<YandexClient.Track>) {
    val color = Accent
    val image = info.cover
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), MaterialTheme.colorScheme.background)),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            if (screen == Screen.Liked) {
                Box(
                    Modifier.size(200.dp).shadow(24.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.45f).compositeOver(Color(0xFF241433)), Color(0xFF241433)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(84.dp))
                }
            } else {
                val shape = if (info.round) CircleShape else RoundedCornerShape(16.dp)
                Cover(image, 200.dp, shape = shape, modifier = Modifier.shadow(24.dp, shape), px = 600)
            }
            Column(Modifier.padding(start = 28.dp).weight(1f)) {
                Text(info.kind, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = SoftText)
                Text(
                    info.title,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (info.subtitle.isNotBlank()) {
                    Text(info.subtitle, color = SoftText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
                if (page.tracks.isNotEmpty()) {
                    val total = page.tracks.sumOf { it.durationMs }
                    Text(
                        "${tracksLabel(page.tracks.size)} · ${formatLength(total)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FilterField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.width(240.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = SoftText, modifier = Modifier.size(18.dp))
        Box(Modifier.padding(start = 8.dp).weight(1f)) {
            if (value.isEmpty()) Text("Найти в списке", color = SoftText, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(0.dp))
}
