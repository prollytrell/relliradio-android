package com.relli.reels.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.components.Chip
import com.relli.reels.ui.components.Empty
import com.relli.reels.ui.components.Loaded
import com.relli.reels.ui.components.PosterCard
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.components.load
import com.relli.reels.ui.components.posterWidth
import com.relli.reels.ui.theme.Relli

private val SORTS = listOf(
    Triple("Recently added", "DateCreated,SortName", "Descending"),
    Triple("A–Z", "SortName", "Ascending"),
    Triple("Newest", "PremiereDate,ProductionYear,SortName", "Descending"),
    Triple("Top rated", "CommunityRating,SortName", "Descending"),
    Triple("Random", "Random", "Ascending"),
)

/** One library (Movies, Shows, Anime…) as a poster grid, with genre and sort chips. */
@Composable
fun BrowseScreen(viewId: String?, bottom: Dp) {
    val views by load { ReelsApp.api.views().filter { it.collectionType in VIDEO_KINDS } }
    Loaded(views) { list ->
        if (list.isEmpty()) {
            Column(Modifier.statusBarsPadding().padding(gutter())) { Empty(Icons.Rounded.Movie, "No video libraries yet", "Add your Movies, Shows and Anime folders in Jellyfin's Dashboard → Libraries.") }
            return@Loaded
        }
        var current by rememberSaveable(viewId) { mutableStateOf(viewId ?: list.first().id) }
        val view = list.firstOrNull { it.id == current } ?: list.first()
        Library(list, view, bottom) { current = it }
    }
}

@Composable
private fun Library(views: List<Item>, view: Item, bottom: Dp, onView: (String) -> Unit) {
    val api = ReelsApp.api
    var genre by rememberSaveable(view.id) { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(view.id) { mutableStateOf(0) }
    val genres by load(view.id) { api.genres(view.id, typesFor(view) ?: "Movie,Series").map { it.name } }

    // Pages of 60 load as you scroll.
    val items = remember(view.id, genre, sort) { mutableStateListOf<Item>() }
    var total by remember(view.id, genre, sort) { mutableStateOf(-1) }
    var loading by remember(view.id, genre, sort) { mutableStateOf(false) }
    var error by remember(view.id, genre, sort) { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val needMore by remember(view.id, genre, sort) {
        derivedStateOf { total < 0 || (items.size < total && (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > items.size - 20) }
    }
    LaunchedEffect(view.id, genre, sort) {
        while (error == null) {
            // Wait until the grid is near the end of what's loaded, then fetch the next page.
            snapshotFlow { needMore }.first { it }
            loading = true
            try {
                val (_, by, order) = SORTS[sort]
                val page = api.items(parentId = view.id, types = typesFor(view), genre = genre, sortBy = by, sortOrder = order, start = items.size, limit = 60)
                items.addAll(page.items)
                total = page.total
                if (page.items.isEmpty()) break
            } catch (e: Exception) {
                error = e.message
                if (total < 0) total = 0
            } finally { loading = false }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(posterWidth()),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gutter() - 4.dp, end = gutter() - 4.dp, bottom = bottom + 30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 14.dp)) {
                Text("Browse", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(views, key = { it.id }) { v -> Chip(v.name, v.id == view.id) { onView(v.id) } }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    items(SORTS.size) { s -> Chip(SORTS[s].first, s == sort) { sort = s } }
                }
                val g = (genres as? com.relli.reels.ui.components.Load.Ok)?.value.orEmpty()
                if (g.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)) {
                    item { Chip("All genres", genre == null) { genre = null } }
                    items(g) { name -> Chip(name, genre == name) { genre = if (genre == name) null else name } }
                }
                if (total >= 0) Text(if (total == 1) "1 title" else "$total titles", style = MaterialTheme.typography.bodySmall, color = Relli.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
        }
        items(items, key = { it.id }) { i -> PosterCard(i, width = posterWidth()) }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Relli.gold, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }
        }
        if (total == 0 && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(error ?: "Nothing here yet.", color = Relli.muted, modifier = Modifier.padding(8.dp))
        }
    }
}
