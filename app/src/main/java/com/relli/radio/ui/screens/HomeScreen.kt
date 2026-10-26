@file:OptIn(ExperimentalMaterial3Api::class)

package com.relli.radio.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.relli.radio.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.relli.radio.R
import com.relli.radio.RelliApp
import com.relli.radio.data.Album
import com.relli.radio.data.Prefs
import com.relli.radio.playback.Mixes
import com.relli.radio.ui.Nav
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Cover
import com.relli.radio.ui.components.Gutter
import com.relli.radio.ui.components.Load
import com.relli.radio.ui.components.SectionHeader
import com.relli.radio.ui.components.SongRow
import com.relli.radio.ui.components.Tile
import com.relli.radio.ui.components.load
import com.relli.radio.ui.components.toast
import com.relli.radio.ui.player.RelliSheet
import com.relli.radio.ui.player.ToggleRow
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.launch

private val api get() = RelliApp.api

/** Every widget Home can show, in its default order. Edit home turns them on/off and reorders. */
val HOME_WIDGETS = linkedMapOf(
    "mixes" to "Mixes",
    "favTracks" to "Favorite tracks",
    "newest" to "Recently added albums",
    "rediscover" to "Rediscover albums",
    "recent" to "Last played albums",
    "randomArtists" to "Random artists",
    "randomAlbums" to "Random albums",
    "continue" to "Continue album listening",
    "mostPlayed" to "Most played tracks",
    "frequent" to "Most played albums",
    "playlists" to "Playlists",
    "radio" to "Internet radio",
)

private fun playAlbum(id: String, scope: kotlinx.coroutines.CoroutineScope) = scope.launch {
    try { val a = api.album(id); Playback.play(a.song, 0, a.name) } catch (e: Exception) { toast(e.message ?: "Couldn't play") }
}

@Composable
fun HomeScreen(bottomPadding: androidx.compose.ui.unit.Dp) {
    val widgets by RelliApp.prefs.homeWidgets.flow.collectAsState()
    var editing by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.rr_logo), "RelliRadio", Modifier.size(88.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    SquareButton(Icons.Rounded.Edit, "Edit home") { editing = true }
                    SquareButton(Icons.AutoMirrored.Rounded.QueueMusic, "Now playing") {
                        if (Playback.current.value != null) Nav.playerOpen.value = true else toast("Nothing is playing yet.")
                    }
                    SquareButton(Icons.Rounded.Search, "Search") { Nav.go("search") }
                }
            }
        }
        widgets.filter { it in HOME_WIDGETS }.forEach { id ->
            item(key = id) { Widget(id) }
        }
    }
    if (editing) EditHomeSheet { editing = false }
}

@Composable
private fun SquareButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Relli.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Relli.muted)
    }
}

@Composable
private fun Widget(id: String) {
    val scope = rememberCoroutineScope()
    val pad = Modifier.padding(horizontal = Gutter)
    val rowPad = PaddingValues(horizontal = Gutter)
    when (id) {
        "mixes" -> MixesWidget()
        "favTracks" -> {
            val s by load { api.starred().songs.shuffled().take(5) }
            (s as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { songs ->
                Column(pad) {
                    SectionHeader("Favorite tracks") { Nav.go("library/favorites") }
                    songs.forEachIndexed { i, song -> SongRow(song, onClick = { Playback.play(songs, i, "Favorites") }) }
                }
            }
        }
        "newest" -> AlbumWidget("Recently added albums", "library/albums?sort=newest", { api.albumList("newest", 20) }) { albums ->
            LazyHorizontalGrid(GridCells.Fixed(2), Modifier.height(430.dp), contentPadding = rowPad,
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(albums, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, a.artist, Modifier.width(140.dp)) { Nav.album(a.id) } }
            }
        }
        "rediscover" -> AlbumWidget("Rediscover albums", "library/albums?sort=random", { api.albumList("random", 12) }) { albums ->
            LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(albums, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, a.artist, Modifier.width(220.dp)) { Nav.album(a.id) } }
            }
        }
        "recent" -> AlbumWidget("Last played albums", "library/albums?sort=recent", { api.albumList("recent", 12) }) { albums ->
            LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(albums, key = { it.id }) { a -> WideCard(a) { Nav.album(a.id) } }
            }
        }
        "randomArtists" -> {
            val s by load { api.artists().shuffled().take(16) }
            (s as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { artists ->
                Column {
                    Box(pad) { SectionHeader("Random artists") { Nav.go("library/artists") } }
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(artists, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, null, Modifier.width(108.dp), round = true) { Nav.artist(a.id) } }
                    }
                }
            }
        }
        "randomAlbums" -> AlbumWidget("Random albums", "library/albums?sort=random", { api.albumList("random", 10) }) { albums ->
            LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(albums, key = { it.id }) { a -> BlurCard(a, onPlay = { playAlbum(a.id, scope) }) { Nav.album(a.id) } }
            }
        }
        "continue" -> AlbumWidget("Continue album listening", "library/albums?sort=recent", { api.albumList("recent", 12) }) { albums ->
            LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(albums, key = { it.id }) { a ->
                    Column(Modifier.width(150.dp).clickable { Nav.album(a.id) }) {
                        Box {
                            Cover(a.coverArt ?: a.id, Modifier.fillMaxWidth().aspectRatio(1f), 300, RoundedCornerShape(14.dp))
                            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp).clip(CircleShape).background(Relli.bgDeep.copy(alpha = 0.75f))
                                .clickable { playAlbum(a.id, scope) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, "Play ${a.name}", tint = Relli.cream)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        "mostPlayed" -> {
            val s by load {
                api.albumList("frequent", 6).flatMap { runCatching { api.album(it.id).song }.getOrDefault(emptyList()) }
                    .filter { (it.playCount ?: 0) > 0 }.sortedByDescending { it.playCount }.take(5)
            }
            (s as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { songs ->
                Column(pad) {
                    SectionHeader("Most played tracks") { Nav.go("library/albums?sort=frequent") }
                    songs.forEachIndexed { i, song -> SongRow(song, onClick = { Playback.play(songs, i, "Most played") }) }
                }
            }
        }
        "frequent" -> AlbumWidget("Most played albums", "library/albums?sort=frequent", { api.albumList("frequent", 16) }) { albums ->
            LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(albums, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, a.artist, Modifier.width(150.dp)) { Nav.album(a.id) } }
            }
        }
        "playlists" -> {
            val s by load { api.playlists() }
            (s as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { lists ->
                Column {
                    Box(pad) { SectionHeader("Playlists") { Nav.go("library/playlists") } }
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(lists, key = { it.id }) { p -> Tile(p.coverArt ?: p.id, p.name, "${p.songCount} songs", Modifier.width(150.dp)) { Nav.playlist(p.id) } }
                    }
                }
            }
        }
        "radio" -> {
            val s by load { api.radioStations() }
            (s as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { stations ->
                Column {
                    Box(pad) { SectionHeader("Internet radio") { Nav.go("library/radio") } }
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(stations, key = { it.id }) { st ->
                            Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Relli.surface).clickable { Playback.playRadio(st) }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Radio, null, tint = Relli.gold)
                                Spacer(Modifier.width(10.dp))
                                Text(st.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumWidget(title: String, route: String, fetch: suspend () -> List<Album>, content: @Composable (List<Album>) -> Unit) {
    val s by load(title) { fetch() }
    (s as? Load.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { albums ->
        Column {
            Box(Modifier.padding(horizontal = Gutter)) { SectionHeader(title) { Nav.go(route) } }
            content(albums)
        }
    }
}

@Composable
private fun WideCard(a: Album, onClick: () -> Unit) {
    Box(Modifier.width(300.dp).aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Cover(a.coverArt ?: a.id, Modifier.fillMaxSize(), 500, RoundedCornerShape(0.dp))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0100B08))))
                .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 14.dp)
        ) {
            Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.cream.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

@Composable
private fun BlurCard(a: Album, onPlay: () -> Unit, onClick: () -> Unit) {
    Box(Modifier.width(320.dp).height(128.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Cover(a.coverArt ?: a.id, Modifier.fillMaxSize().blur(26.dp), 200, RoundedCornerShape(0.dp))
        Box(Modifier.fillMaxSize().background(Color(0x80140F0C)))
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(a.coverArt ?: a.id, Modifier.size(100.dp), 300, RoundedCornerShape(12.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(a.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.cream.copy(alpha = 0.75f), maxLines = 1)
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(Relli.goldMetal).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Play ${a.name}", tint = Relli.onGold)
            }
        }
    }
}

@Composable
private fun MixesWidget() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun start(build: suspend () -> Mixes.Mix) {
        if (busy) return
        busy = true
        scope.launch {
            try { val m = build(); if (m.songs.isEmpty()) toast("Nothing to play yet.") else Playback.play(m.songs, 0, m.name) }
            catch (e: Exception) { toast(e.message ?: "Couldn't start the mix") }
            finally { busy = false }
        }
    }
    val genres by load { api.genres().sortedByDescending { it.songCount }.take(8) }
    Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MixTile("Track mix", "Random songs", Icons.Rounded.Shuffle, gold = true, Modifier.weight(1f)) { start { Mixes.track() } }
            MixTile("Album mix", "Whole albums", Icons.Rounded.Album, gold = false, Modifier.weight(1f)) { start { Mixes.album() } }
            MixTile("Decade mix", "One era", Icons.Rounded.CalendarMonth, gold = false, Modifier.weight(1f)) { start { Mixes.decade() } }
        }
        (genres as? Load.Ok)?.value?.let { list ->
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.value }) { g ->
                    Row(Modifier.clip(CircleShape).background(Relli.surface).clickable { start { Mixes.genre(g.value) } }.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Relli.goldSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Label, null, tint = Relli.gold, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(g.value, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MixTile(label: String, sub: String, icon: ImageVector, gold: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(116.dp).clip(RoundedCornerShape(20.dp))
            .then(if (gold) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.surface))
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (gold) Relli.onGold.copy(alpha = 0.12f) else Relli.goldSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (gold) Relli.onGold else Relli.gold)
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (gold) Relli.onGold else Relli.cream, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = if (gold) Relli.onGold.copy(alpha = 0.7f) else Relli.muted, maxLines = 1)
        }
    }
}

@Composable
fun EditHomeSheet(onDismiss: () -> Unit) {
    val prefs = RelliApp.prefs
    val on = remember { mutableStateListOf<String>().apply { addAll(prefs.homeWidgets.value.filter { it in HOME_WIDGETS }) } }
    val order = remember { mutableStateListOf<String>().apply { addAll(on); addAll(HOME_WIDGETS.keys.filter { it !in on }) } }
    fun save() { prefs.homeWidgets.value = order.filter { it in on } }
    RelliSheet("Edit home", Icons.Rounded.Edit, onDismiss) {
        Text("Turn widgets on or off and move them up or down. Changes save right away.", color = Relli.muted, style = MaterialTheme.typography.bodySmall)
        order.toList().forEachIndexed { i, id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ToggleRow(HOME_WIDGETS[id] ?: id, id in on) { checked -> if (checked) on.add(id) else on.remove(id); save() }
                }
                IconButton(onClick = { if (i > 0) { order.removeAt(i); order.add(i - 1, id); save() } }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = Relli.muted) }
                IconButton(onClick = { if (i < order.lastIndex) { order.removeAt(i); order.add(i + 1, id); save() } }) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = Relli.muted) }
            }
        }
        Text("Reset to default", color = Relli.gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable {
            on.clear(); on.addAll(Prefs.DEFAULT_HOME); order.clear(); order.addAll(Prefs.DEFAULT_HOME); order.addAll(HOME_WIDGETS.keys.filter { it !in Prefs.DEFAULT_HOME }); save()
        }.padding(8.dp))
    }
}
