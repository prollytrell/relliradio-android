package com.relli.radio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import com.relli.radio.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.radio.RelliApp
import com.relli.radio.data.Song
import com.relli.radio.playback.Mixes
import com.relli.radio.ui.Nav
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Cover
import com.relli.radio.ui.components.Gutter
import com.relli.radio.ui.components.HeartButton
import com.relli.radio.ui.components.Loaded
import com.relli.radio.ui.components.PillButton
import com.relli.radio.ui.components.SongRow
import com.relli.radio.ui.components.Tile
import com.relli.radio.ui.components.fmtTime
import com.relli.radio.ui.components.load
import com.relli.radio.ui.components.toast
import com.relli.radio.ui.player.PlaylistPickerSheet
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.launch

private val api get() = RelliApp.api

@Composable
fun BackBar() {
    IconButton(onClick = { Nav.controller?.popBackStack() }, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
        Box(Modifier.size(40.dp).background(Relli.bg.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Relli.cream)
        }
    }
}

/** Big art with a blurred copy behind it, title, lines, and Play / Shuffle. */
private fun LazyListScope.hero(
    art: String?, kind: String, title: String, lines: List<String>, round: Boolean = false,
    onPlay: () -> Unit, onShuffle: () -> Unit, extra: @Composable () -> Unit = {},
) {
    item {
        Box(Modifier.fillMaxWidth()) {
            Cover(art, Modifier.fillMaxWidth().height(340.dp).blur(40.dp), 300, RoundedCornerShape(0.dp))
            Box(Modifier.fillMaxWidth().height(340.dp).background(Brush.verticalGradient(listOf(Relli.bg.copy(alpha = 0.55f), Relli.bg))))
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 44.dp, start = Gutter, end = Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(art, Modifier.size(230.dp), 600, if (round) CircleShape else RoundedCornerShape(18.dp))
                Text(kind, color = Relli.gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 18.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                lines.filter { it.isNotBlank() }.forEach { Text(it, color = Relli.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                Row(Modifier.padding(top = 18.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Play", Icons.Rounded.PlayArrow, primary = true, onClick = onPlay)
                    PillButton("Shuffle", Icons.Rounded.Shuffle, onClick = onShuffle)
                    extra()
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(id: String, bottom: Dp) {
    val s by load(id) { api.album(id) }
    var addAll by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Loaded(s) { album ->
            val songs = album.song
            val discs = songs.map { it.discNumber ?: 1 }.distinct()
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                hero(album.coverArt ?: album.id, "Album", album.name,
                    listOf(album.artist ?: "", listOfNotNull(album.year?.toString(), album.genre, "${songs.size} songs", fmtTime(album.duration.toLong())).joinToString(", ")),
                    onPlay = { Playback.play(songs, 0, album.name) }, onShuffle = { Playback.play(songs, 0, album.name, shuffle = true) },
                ) {
                    HeartButton(album.id, album.starred != null)
                    IconButton(onClick = { Playback.playNext(songs); toast("Album plays next") }) { Icon(Icons.Rounded.SkipNext, "Play next", tint = Relli.cream) }
                    IconButton(onClick = { addAll = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", tint = Relli.cream) }
                }
                album.artistId?.let { aid ->
                    item { Text("More by ${album.artist}", color = Relli.gold, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp).clickable { Nav.artist(aid) }) }
                }
                discs.forEach { d ->
                    if (discs.size > 1) item { Text("Disc $d", color = Relli.muted, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Gutter, top = 18.dp, bottom = 4.dp)) }
                    songs.forEachIndexed { i, song ->
                        if ((song.discNumber ?: 1) == d) item(key = song.id + i) {
                            Box(Modifier.padding(horizontal = Gutter - 6.dp)) { SongRow(song, onClick = { Playback.play(songs, i, album.name) }, number = song.track ?: (i + 1)) }
                        }
                    }
                }
            }
            if (addAll) PlaylistPickerSheet(songs) { addAll = false }
        }
        BackBar()
    }
}

@Composable
fun ArtistScreen(id: String, bottom: Dp) {
    val s by load(id) { api.artist(id) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Loaded(s) { artist ->
            val top by load(artist.name) { api.topSongs(artist.name, 10) }
            val bio by load(id + "bio") { api.artistBio(id) }
            val albums = artist.album.sortedByDescending { it.year ?: 0 }
            suspend fun allSongs(): List<Song> = albums.take(25).flatMap { runCatching { api.album(it.id).song }.getOrDefault(emptyList()) }
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                hero(artist.coverArt ?: artist.id, "Artist", artist.name, listOf("${albums.size} albums"), round = true,
                    onPlay = { scope.launchSafe { Playback.play(allSongs(), 0, artist.name) } },
                    onShuffle = { scope.launchSafe { Playback.play(allSongs(), 0, artist.name, shuffle = true) } },
                ) { HeartButton(artist.id, artist.starred != null) }
                val topSongs = (top as? com.relli.radio.ui.components.Load.Ok)?.value.orEmpty()
                if (topSongs.isNotEmpty()) {
                    item { Text("Popular", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Gutter, top = 18.dp, bottom = 8.dp)) }
                    itemsIndexed(topSongs) { i, song -> Box(Modifier.padding(horizontal = Gutter - 6.dp)) { SongRow(song, onClick = { Playback.play(topSongs, i, artist.name) }) } }
                }
                item { Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Gutter, top = 24.dp, bottom = 12.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(albums, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, a.year?.toString(), Modifier.width(150.dp)) { Nav.album(a.id) } }
                    }
                }
                val about = (bio as? com.relli.radio.ui.components.Load.Ok)?.value.orEmpty()
                if (about.isNotBlank()) {
                    item { Text("About", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Gutter, top = 24.dp, bottom = 8.dp)) }
                    item { Text(about, color = Relli.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Gutter)) }
                }
            }
        }
        BackBar()
    }
}

@Composable
fun PlaylistScreen(id: String, bottom: Dp) {
    var version by remember { mutableStateOf(0) }
    val s by load(id, version) { api.playlist(id) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Loaded(s) { p ->
            val songs = p.entry
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                hero(p.coverArt ?: p.id, "Playlist", p.name, listOf(p.comment ?: "", "${songs.size} songs, ${fmtTime(p.duration.toLong())}"),
                    onPlay = { Playback.play(songs, 0, p.name) }, onShuffle = { Playback.play(songs, 0, p.name, shuffle = true) })
                itemsIndexed(songs, key = { i, x -> x.id + i }) { i, song ->
                    Box(Modifier.padding(horizontal = Gutter - 6.dp)) {
                        SongRow(song, onClick = { Playback.play(songs, i, p.name) }, trailing = if (p.readonly) null else ({
                            IconButton(onClick = {
                                scope.launch {
                                    try { api.removeFromPlaylist(p.id, i); toast("Removed from playlist"); version++ }
                                    catch (e: Exception) { toast(e.message ?: "Couldn't remove it") }
                                }
                            }) { Icon(Icons.Rounded.Close, "Remove from this playlist", tint = Relli.muted) }
                        }))
                    }
                }
            }
        }
        BackBar()
    }
}

@Composable
fun GenreScreen(name: String, bottom: Dp) {
    val s by load(name) { api.albumList("byGenre", 60, genre = name) to api.songsByGenre(name, 50) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        BackBar()
        Loaded(s) { (albums, songs) ->
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                item {
                    Column(Modifier.padding(horizontal = Gutter)) {
                        Text(name, style = MaterialTheme.typography.headlineMedium)
                        Row(Modifier.padding(vertical = 14.dp)) {
                            PillButton("Shuffle $name", Icons.Rounded.Shuffle, primary = true) {
                                scope.launchSafe { val m = Mixes.genre(name); Playback.play(m.songs, 0, m.name) }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Gutter, top = 8.dp, bottom = 12.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(albums, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, a.artist, Modifier.width(150.dp)) { Nav.album(a.id) } }
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item { Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Gutter, top = 24.dp, bottom = 8.dp)) }
                    itemsIndexed(songs, key = { i, x -> x.id + i }) { i, song ->
                        Box(Modifier.padding(horizontal = Gutter - 6.dp)) { SongRow(song, onClick = { Playback.play(songs, i, name) }) }
                    }
                }
            }
        }
    }
}
