package com.relli.reels.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.relli.reels.R
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.Nav
import com.relli.reels.ui.components.Chip
import com.relli.reels.ui.components.Img
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.Loaded
import com.relli.reels.ui.components.PillButton
import com.relli.reels.ui.components.PosterCard
import com.relli.reels.ui.components.ProgressLine
import com.relli.reels.ui.components.Refresh
import com.relli.reels.ui.components.Scrim
import com.relli.reels.ui.components.Shelf
import com.relli.reels.ui.components.WideCard
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.components.load
import com.relli.reels.ui.components.metaLine
import com.relli.reels.ui.components.toast
import com.relli.reels.ui.components.wideWidth
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.Duration

/** Libraries that hold video (not music, books or playlists). */
val VIDEO_KINDS = setOf("movies", "tvshows", "homevideos", "boxsets", "mixed", null)

fun typesFor(view: Item?): String? = when (view?.collectionType) {
    "movies" -> "Movie"
    "tvshows" -> "Series"
    "boxsets" -> "BoxSet"
    "homevideos" -> "Video"
    else -> "Movie,Series"
}

private class HomeData(
    val featured: List<Item>,
    val resume: List<Item>,
    val nextUp: List<Item>,
    val libraries: List<Item>,
    val latest: List<Pair<Item, List<Item>>>,
    val onNow: List<Item>,
    val favorites: List<Item>,
    val genres: List<Pair<String, List<Item>>>,
    val hasLive: Boolean,
)

private suspend fun loadHome(): HomeData = coroutineScope {
    val api = ReelsApp.api
    val views = api.views()
    val libs = views.filter { it.collectionType in VIDEO_KINDS && it.collectionType != "boxsets" }
    val hasLive = views.any { it.collectionType == "livetv" }
    val resume = async { runCatching { api.resume() }.getOrDefault(emptyList()) }
    val next = async { runCatching { api.nextUp() }.getOrDefault(emptyList()) }
    val latest = libs.map { v -> async { v to runCatching { api.latest(v.id) }.getOrDefault(emptyList()) } }
    val featured = async {
        runCatching { api.items(sortBy = "DateCreated,SortName", sortOrder = "Descending", limit = 30).items }
            .getOrDefault(emptyList()).filter { it.backdropTags.isNotEmpty() }.shuffled(java.util.Random(LocalDate.now().toEpochDay())).take(7)
    }
    val onNow = async { if (hasLive) runCatching { api.channels(limit = 30).filter { it.currentProgram != null } }.getOrDefault(emptyList()) else emptyList() }
    val favorites = async { runCatching { api.items(favorites = true, sortBy = "DatePlayed,SortName", sortOrder = "Descending", limit = 30).items }.getOrDefault(emptyList()) }
    // A few genre rows, Tubi-style. They change once a day.
    val genres = async {
        val all = runCatching { api.genres(null) }.getOrDefault(emptyList())
        all.shuffled(java.util.Random(LocalDate.now().toEpochDay() + 7)).take(5).map { g ->
            async { g.name to runCatching { api.items(genre = g.name, sortBy = "Random", limit = 20).items }.getOrDefault(emptyList()) }
        }.map { it.await() }.filter { it.second.size >= 4 }
    }
    HomeData(featured.await(), resume.await(), next.await(), libs, latest.map { it.await() }, onNow.await(), favorites.await(), genres.await(), hasLive)
}

@Composable
fun HomeScreen(bottom: Dp) {
    val state by load { loadHome() }
    val tv = LocalIsTv.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item {
            Loaded(state) { d ->
                Column {
                    if (d.featured.isNotEmpty()) Hero(d.featured) else Column(Modifier.statusBarsPadding().padding(top = 16.dp)) { Brand() }
                    // Library shortcuts: Movies, Shows, Anime, Live TV...
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = gutter()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        items(d.libraries, key = { it.id }) { v -> Chip(v.name, false) { Nav.go("browse?view=${v.id}") } }
                        if (d.hasLive) item { Chip("Live TV", false) { Nav.go("live") } }
                    }
                    Shelf("Continue watching", d.resume, key = { it.id }) { i -> WideCard(i) { Nav.play(i) } }
                    Shelf("Next up", d.nextUp, key = { it.id }) { i -> WideCard(i) { Nav.play(i) } }
                    Shelf("On now", d.onNow, onTitle = { Nav.go("live") }, key = { it.id }) { ch -> LiveCard(ch) }
                    d.latest.forEach { (lib, list) ->
                        Shelf("New in ${lib.name}", list, onTitle = { Nav.go("browse?view=${lib.id}") }, key = { it.id }) { i -> PosterCard(i) }
                    }
                    Shelf("My List", d.favorites, onTitle = { Nav.go("mystuff") }, key = { it.id }) { i -> PosterCard(i) }
                    d.genres.forEach { (name, list) -> Shelf(name, list, key = { it.id }) { i -> PosterCard(i) } }
                    if (d.libraries.isEmpty()) Text(
                        "No movie or TV libraries yet. Add them in Jellyfin's Dashboard → Libraries.",
                        color = Relli.muted, modifier = Modifier.padding(gutter()),
                    )
                }
            }
        }
        if (tv) item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
fun Brand() {
    Row(Modifier.padding(horizontal = gutter()), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.rr_logo), null, Modifier.size(34.dp))
        Text("RelliReels", style = MaterialTheme.typography.titleLarge, color = Relli.gold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The big rotating banner at the top, with Play, More info and My List. */
@Composable
private fun Hero(items: List<Item>) {
    val tv = LocalIsTv.current
    val pager = rememberPagerState { items.size }
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, focused) {
        while (!focused) {
            delay(8000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val height = if (tv) 430.dp else 520.dp
    Box(Modifier.fillMaxWidth().height(height)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !tv) { page ->
            val i = items[page]
            Box(Modifier.fillMaxSize()) {
                Img(
                    if (tv) ReelsApp.api.backdrop(i, 1600) else ReelsApp.api.poster(i, 900) ?: ReelsApp.api.backdrop(i, 1200),
                    Modifier.fillMaxSize(), RoundedCornerShape(0.dp),
                )
                Scrim(1.1f)
            }
        }
        if (!tv) Box(Modifier.statusBarsPadding().padding(top = 10.dp)) { Brand() }
        val i = items[pager.currentPage]
        var fav by remember(i.id) { mutableStateOf(i.userData?.isFavorite == true) }
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = gutter()).padding(bottom = 6.dp).widthIn(max = 620.dp)
                .onFocusChanged { focused = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val logo = ReelsApp.api.logo(i)
            if (logo != null) AsyncImage(logo, i.name, Modifier.heightIn(max = if (tv) 110.dp else 80.dp).widthIn(max = 320.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else Text(i.name, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(metaLine(i), style = MaterialTheme.typography.labelLarge, color = Relli.muted)
            if (i.genres.isNotEmpty()) Text(i.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Relli.muted)
            if (tv) i.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Relli.cream.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                PillButton("Play", Icons.Rounded.PlayArrow, primary = true) { if (i.isSeriesItem) Nav.open(i) else Nav.play(i) }
                PillButton("More info", Icons.Rounded.Info) { Nav.open(i) }
                PillButton(if (fav) "In My List" else "My List", if (fav) Icons.Rounded.Check else Icons.Rounded.Add) {
                    fav = !fav
                    scope.launch {
                        try { ReelsApp.api.setFavorite(i.id, fav); toast(if (fav) "Added to My List" else "Removed from My List"); Refresh.bump() }
                        catch (e: Exception) { fav = !fav; toast(e.message ?: "Couldn't update My List") }
                    }
                }
            }
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items.indices.forEach { p ->
                    Box(Modifier.size(if (p == pager.currentPage) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                        .background(if (p == pager.currentPage) Relli.gold else Relli.cream.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

/** A live channel: what's on now, how far into it, and the channel's logo. */
@Composable
fun LiveCard(ch: Item, width: Dp = wideWidth()) {
    val prog = ch.currentProgram
    Column(Modifier.width(width).clip(RoundedCornerShape(14.dp)).clickable { Nav.playChannel(ch.id) }.padding(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Relli.raise), contentAlignment = Alignment.Center) {
            Img(ReelsApp.api.channelLogo(ch, 320), Modifier.fillMaxSize(0.6f), RoundedCornerShape(0.dp), placeholder = ch.name, scale = ContentScale.Fit)
            Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Relli.cream,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0xFFB3261E)).padding(horizontal = 6.dp, vertical = 2.dp))
            ProgressLine(programProgress(prog), Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(prog?.name ?: ch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(ch.channelNumber, ch.name).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1)
    }
}

fun programProgress(p: Item?): Float {
    val s = p?.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val e = p.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val total = Duration.between(s, e).seconds.toFloat()
    if (total <= 0) return 0f
    return (Duration.between(s, Instant.now()).seconds / total).coerceIn(0f, 1f)
}
