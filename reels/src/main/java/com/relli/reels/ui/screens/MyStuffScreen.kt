package com.relli.reels.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.reels.ReelsApp
import com.relli.reels.ui.Nav
import com.relli.reels.ui.components.Empty
import com.relli.reels.ui.components.IconButton
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.Loaded
import com.relli.reels.ui.components.PosterCard
import com.relli.reels.ui.components.Shelf
import com.relli.reels.ui.components.WideCard
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.components.load
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private class Stuff(
    val resume: List<com.relli.reels.data.Item>,
    val movies: List<com.relli.reels.data.Item>,
    val shows: List<com.relli.reels.data.Item>,
    val watched: List<com.relli.reels.data.Item>,
)

/** Your list (favorites), what you're in the middle of, and what you watched lately. */
@Composable
fun MyStuffScreen(bottom: Dp) {
    val tv = LocalIsTv.current
    val state by load {
        coroutineScope {
            val api = ReelsApp.api
            val resume = async { api.resume(30) }
            val movies = async { api.items(types = "Movie,BoxSet", favorites = true, sortBy = "SortName", limit = 100).items }
            val shows = async { api.items(types = "Series", favorites = true, sortBy = "SortName", limit = 100).items }
            val watched = async { api.items(types = "Movie,Episode", sortBy = "DatePlayed", sortOrder = "Descending", limit = 30).items.filter { it.userData?.lastPlayedDate != null } }
            Stuff(resume.await(), movies.await(), shows.await(), watched.await())
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 30.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = gutter(), end = gutter() - 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("My Stuff", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (!tv) IconButton(onClick = { Nav.go("settings") }) { Icon(Icons.Rounded.Settings, "Settings", tint = Relli.muted) }
            }
        }
        item {
            Loaded(state) { s ->
                Column {
                    if (s.resume.isEmpty() && s.movies.isEmpty() && s.shows.isEmpty()) Column(Modifier.padding(horizontal = gutter())) {
                        Empty(Icons.Rounded.Bookmarks, "Nothing saved yet", "Tap \"My List\" on any movie or show to keep it here.")
                    }
                    Shelf("Continue watching", s.resume, key = { it.id }) { i -> WideCard(i) { Nav.play(i) } }
                    Shelf("My List · Movies", s.movies, key = { it.id }) { PosterCard(it) }
                    Shelf("My List · Shows", s.shows, key = { it.id }) { PosterCard(it) }
                    Shelf("Recently watched", s.watched, key = { it.id }) { i -> WideCard(i) { Nav.open(if (i.isEpisode && i.seriesId != null) i.copy(type = "Series", id = i.seriesId) else i) } }
                    Spacer(Modifier.padding(10.dp))
                }
            }
        }
    }
}
