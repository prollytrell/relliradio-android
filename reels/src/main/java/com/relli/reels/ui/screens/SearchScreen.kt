package com.relli.reels.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.Nav
import com.relli.reels.ui.components.Empty
import com.relli.reels.ui.components.PosterCard
import com.relli.reels.ui.components.Shelf
import com.relli.reels.ui.components.WideCard
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(bottom: Dp) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by androidx.compose.runtime.remember { mutableStateOf<List<Item>?>(null) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = null; return@LaunchedEffect }
        delay(350) // wait until you stop typing
        try {
            results = ReelsApp.api.items(search = q, types = "Movie,Series,Episode,TvChannel,BoxSet", sortBy = "SortName", limit = 80).items
            error = null
        } catch (e: Exception) { error = e.message }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 30.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = gutter()).padding(top = 14.dp)) {
                Text("Search", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text("Movies, shows, episodes, channels") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Relli.muted) },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        item {
            val r = results
            when {
                error != null -> Column(Modifier.padding(horizontal = gutter())) { Empty(Icons.Rounded.SearchOff, "Search didn't work", error) }
                r == null -> Unit
                r.isEmpty() -> Column(Modifier.padding(horizontal = gutter())) { Empty(Icons.Rounded.SearchOff, "Nothing found for \"$query\"") }
                else -> Column {
                    Shelf("Movies", r.filter { it.type == "Movie" }, key = { it.id }) { PosterCard(it) }
                    Shelf("Shows", r.filter { it.type == "Series" }, key = { it.id }) { PosterCard(it) }
                    Shelf("Collections", r.filter { it.type == "BoxSet" }, key = { it.id }) { PosterCard(it) }
                    Shelf("Episodes", r.filter { it.type == "Episode" }, key = { it.id }) { WideCard(it) { Nav.play(it) } }
                    Shelf("Channels", r.filter { it.type == "TvChannel" }, key = { it.id }) { LiveCard(it) }
                }
            }
        }
    }
}
