package com.relli.radio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.radio.RelliApp
import com.relli.radio.data.SearchResult
import com.relli.radio.ui.Nav
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Gutter
import com.relli.radio.ui.components.SongRow
import com.relli.radio.ui.components.Tile
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(bottom: Dp) {
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.isBlank()) { result = null; return@LaunchedEffect }
        delay(250) // wait for typing to pause
        try { result = RelliApp.api.search(query.trim()); error = null } catch (e: Exception) { error = e.message }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
        item {
            OutlinedTextField(
                query, { query = it }, singleLine = true, shape = CircleShape, colors = fieldColors(),
                placeholder = { Text("Songs, albums, artists") }, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Relli.muted) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 14.dp).focusRequester(focus),
            )
        }
        error?.let { e -> item { Text(e, color = Relli.muted, modifier = Modifier.padding(horizontal = Gutter)) } }
        val r = result
        if (r != null) {
            if (r.artists.isEmpty() && r.albums.isEmpty() && r.songs.isEmpty()) {
                item { Text("Nothing matches “$query”.", color = Relli.muted, modifier = Modifier.padding(horizontal = Gutter)) }
            }
            if (r.artists.isNotEmpty()) {
                item { Header("Artists") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(r.artists, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, null, Modifier.width(108.dp), round = true) { Nav.artist(a.id) } }
                    }
                }
            }
            if (r.albums.isNotEmpty()) {
                item { Header("Albums") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(r.albums, key = { it.id }) { a -> Tile(a.coverArt ?: a.id, a.name, a.artist, Modifier.width(150.dp)) { Nav.album(a.id) } }
                    }
                }
            }
            if (r.songs.isNotEmpty()) {
                item { Header("Songs") }
                itemsIndexed(r.songs, key = { _, s -> s.id }) { i, s ->
                    Box(Modifier.padding(horizontal = Gutter - 6.dp)) { SongRow(s, onClick = { Playback.play(r.songs, i, "Search") }) }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Gutter, top = 22.dp, bottom = 12.dp))
