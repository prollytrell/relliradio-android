package com.relli.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import com.relli.radio.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.radio.RelliApp
import com.relli.radio.data.Album
import com.relli.radio.data.Song
import com.relli.radio.ui.Nav
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Cover
import com.relli.radio.ui.components.Empty
import com.relli.radio.ui.components.Gutter
import com.relli.radio.ui.components.Loaded
import com.relli.radio.ui.components.PillButton
import com.relli.radio.ui.components.SongRow
import com.relli.radio.ui.components.Tile
import com.relli.radio.ui.components.load
import com.relli.radio.ui.player.Chip
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val api get() = RelliApp.api

val LIBRARY_TABS = listOf(
    "albums" to "Albums", "artists" to "Artists", "songs" to "Songs", "playlists" to "Playlists",
    "genres" to "Genres", "favorites" to "Favorites", "radio" to "Radio",
)

private val ALBUM_SORTS = listOf(
    "newest" to "Recently added", "recent" to "Recently played", "frequent" to "Most played",
    "alphabeticalByName" to "Title", "alphabeticalByArtist" to "Artist", "random" to "Random",
)

@Composable
fun LibraryScreen(tab: String, sort: String?, bottom: Dp) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = Gutter, top = 18.dp, bottom = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LIBRARY_TABS.forEach { (id, label) -> Chip(label, id == tab) { Nav.controller?.navigate("library/$id") { launchSingleTop = true; popUpTo("library/{tab}?sort={sort}") { inclusive = true } } } }
        }
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                "artists" -> ArtistsTab(bottom)
                "songs" -> SongsTab(bottom)
                "playlists" -> PlaylistsTab(bottom)
                "genres" -> GenresTab(bottom)
                "favorites" -> FavoritesTab(bottom)
                "radio" -> RadioTab(bottom)
                else -> AlbumsTab(sort ?: "newest", bottom)
            }
        }
    }
}

@Composable
private fun AlbumsTab(initialSort: String, bottom: Dp) {
    var sort by remember { mutableStateOf(initialSort) }
    val grid by RelliApp.prefs.albumGrid.flow.collectAsState()
    val libraryVersion by com.relli.radio.ui.Library.version.collectAsState()
    val albums = remember(sort, libraryVersion) { mutableStateListOf<Album>() }
    var more by remember(sort, libraryVersion) { mutableStateOf(true) }
    var loading by remember(sort, libraryVersion) { mutableStateOf(false) }
    val state = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 8 } }
    LaunchedEffect(sort, nearEnd, albums.size, libraryVersion) {
        if (nearEnd && more && !loading) {
            loading = true
            val page = runCatching { api.albumList(sort, 60, albums.size) }.getOrDefault(emptyList())
            albums.addAll(page)
            more = page.size == 60 && sort != "random"
            loading = false
        }
    }
    LazyVerticalGrid(
        if (grid) GridCells.Adaptive(150.dp) else GridCells.Fixed(1), state = state,
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom + 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(if (grid) 20.dp else 4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { RelliApp.prefs.albumGrid.value = !grid }) {
                    Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "Show as list" else "Show as grid", tint = Relli.muted)
                }
                ALBUM_SORTS.forEach { (id, label) -> Chip(label, id == sort) { sort = id } }
            }
        }
        items(albums, key = { it.id }) { a ->
            if (grid) Tile(a.coverArt ?: a.id, a.name, a.artist) { Nav.album(a.id) }
            else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Nav.album(a.id) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(a.coverArt ?: a.id, Modifier.size(56.dp), 120, RoundedCornerShape(10.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(a.artist, a.year?.toString()).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(bottom: Dp) {
    val s by load { api.artists() }
    Loaded(s) { artists ->
        LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = bottom + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(artists, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, "${a.albumCount} albums", round = true) { Nav.artist(a.id) } }
        }
    }
}

@Composable
private fun SongsTab(bottom: Dp) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val songs = remember { mutableStateListOf<Song>() }
    var more by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 10 } }
    LaunchedEffect(nearEnd, songs.size) {
        if (nearEnd && more && !loading) {
            loading = true
            val page = runCatching { api.allSongs(songs.size, 100) }.getOrDefault(emptyList())
            songs.addAll(page); more = page.size == 100; loading = false
        }
    }
    LazyColumn(state = state, contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom + 20.dp)) {
        item {
            Row(Modifier.padding(vertical = 8.dp)) {
                PillButton("Shuffle all", Icons.Rounded.Shuffle, primary = true) {
                    scope.launchSafe { Playback.play(api.randomSongs(200), 0, "All songs") }
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> SongRow(s, onClick = { Playback.play(songs.toList(), i, "All songs") }) }
    }
}

@Composable
private fun PlaylistsTab(bottom: Dp) {
    val s by load { api.playlists() }
    Loaded(s) { lists ->
        if (lists.isEmpty()) Box(Modifier.padding(horizontal = Gutter)) { Empty(Icons.AutoMirrored.Rounded.QueueMusic, "No playlists yet", "Use “Add to playlist” on any song to start one.") }
        else LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = bottom + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(lists, key = { it.id }) { p -> Tile(p.coverArt ?: p.id, p.name, "${p.songCount} songs") { Nav.playlist(p.id) } }
        }
    }
}

@Composable
private fun GenresTab(bottom: Dp) {
    val s by load { api.genres().sortedBy { it.value.lowercase() } }
    Loaded(s) { genres ->
        LazyVerticalGrid(GridCells.Adaptive(170.dp), contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = bottom + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(genres, key = { it.value }) { g ->
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Relli.surface).clickable { Nav.genre(g.value) }.padding(18.dp)) {
                    Text(g.value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.songCount} songs", style = MaterialTheme.typography.bodySmall, color = Relli.muted)
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(bottom: Dp) {
    val s by load { api.starred() }
    Loaded(s) { st ->
        if (st.songs.isEmpty() && st.albums.isEmpty() && st.artists.isEmpty()) {
            Box(Modifier.padding(horizontal = Gutter)) { Empty(Icons.Rounded.FavoriteBorder, "No favorites yet", "Tap the heart on songs, albums or artists to keep them here.") }
        } else LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = bottom + 20.dp)) {
            if (st.songs.isNotEmpty()) {
                item {
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Play", Icons.Rounded.PlayArrow, primary = true) { Playback.play(st.songs, 0, "Favorites") }
                        PillButton("Shuffle", Icons.Rounded.Shuffle) { Playback.play(st.songs, 0, "Favorites", shuffle = true) }
                    }
                }
                itemsIndexed(st.songs, key = { _, x -> "s" + x.id }) { i, x -> SongRow(x, onClick = { Playback.play(st.songs, i, "Favorites") }) }
            }
            if (st.albums.isNotEmpty()) {
                item { Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) }
                items(st.albums, key = { "a" + it.id }) { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Nav.album(a.id) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(a.coverArt ?: a.id, Modifier.size(56.dp), 120, RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(14.dp))
                        Column { Text(a.name, style = MaterialTheme.typography.titleSmall); Text(a.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.muted) }
                    }
                }
            }
            if (st.artists.isNotEmpty()) {
                item { Text("Artists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) }
                items(st.artists, key = { "r" + it.id }) { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Nav.artist(a.id) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(a.coverArt ?: a.id, Modifier.size(56.dp), 120, CircleShape)
                        Spacer(Modifier.width(14.dp))
                        Text(a.name, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioTab(bottom: Dp) {
    val s by load { api.radioStations() }
    Loaded(s) { stations ->
        if (stations.isEmpty()) Box(Modifier.padding(horizontal = Gutter)) {
            Empty(Icons.Rounded.Radio, "No radio stations", "Add internet radio stations in Navidrome's web page (Radios, in the menu) and they'll show up here.")
        } else LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = bottom + 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stations, key = { it.id }) { st ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Relli.surface).clickable { Playback.playRadio(st) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Relli.goldSoft), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Relli.gold) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.name, style = MaterialTheme.typography.titleSmall)
                        Text(st.homePageUrl?.removePrefix("https://")?.removePrefix("http://") ?: "Live stream", style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1)
                    }
                    Icon(Icons.Rounded.PlayArrow, "Play", tint = Relli.gold)
                }
            }
        }
    }
}

/** Runs a background task and shows a short message if it fails. */
fun CoroutineScope.launchSafe(block: suspend () -> Unit) = launch {
    try { block() } catch (e: Exception) { com.relli.radio.ui.components.toast(e.message ?: "Something went wrong") }
}
