package com.relli.reels.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.Nav
import com.relli.reels.ui.components.Chip
import com.relli.reels.ui.components.IconButton
import com.relli.reels.ui.components.Img
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.Loaded
import com.relli.reels.ui.components.PillButton
import com.relli.reels.ui.components.PosterCard
import com.relli.reels.ui.components.ProgressLine
import com.relli.reels.ui.components.Refresh
import com.relli.reels.ui.components.Scrim
import com.relli.reels.ui.components.Shelf
import com.relli.reels.ui.components.fmtClock
import com.relli.reels.ui.components.fmtRuntime
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.components.load
import com.relli.reels.ui.components.metaLine
import com.relli.reels.ui.components.toast
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private class Details(
    val item: Item,
    val seasons: List<Item>,
    val nextUp: Item?,
    val similar: List<Item>,
    val children: List<Item>,
)

private suspend fun loadDetails(id: String): Details = coroutineScope {
    val api = ReelsApp.api
    val item = api.item(id)
    val similar = async { runCatching { api.similar(id) }.getOrDefault(emptyList()) }
    val seasons = async { if (item.isSeriesItem) runCatching { api.seasons(id) }.getOrDefault(emptyList()) else emptyList() }
    val next = async { if (item.isSeriesItem) runCatching { api.nextUp(1, id).firstOrNull() }.getOrNull() else null }
    // Collections ("boxsets") list the movies inside them.
    val children = async { if (item.type == "BoxSet") runCatching { api.items(parentId = id, types = null, sortBy = "ProductionYear,SortName", limit = 200).items }.getOrDefault(emptyList()) else emptyList() }
    Details(item, seasons.await(), next.await(), similar.await(), children.await())
}

@Composable
fun DetailScreen(id: String, seasonId: String?, bottom: Dp) {
    val state by load(id) { loadDetails(id) }
    val tv = LocalIsTv.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 30.dp)) {
        item {
            Loaded(state) { d ->
                Column {
                    Header(d)
                    if (d.item.isSeriesItem) Seasons(d, seasonId)
                    if (d.children.isNotEmpty()) Shelf("In this collection", d.children, key = { it.id }) { PosterCard(it) }
                    Cast(d.item)
                    Shelf("More like this", d.similar, key = { it.id }) { PosterCard(it) }
                    if (tv) Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(d: Details) {
    val i = d.item
    val tv = LocalIsTv.current
    val api = ReelsApp.api
    val scope = rememberCoroutineScope()
    var fav by remember(i.id) { mutableStateOf(i.userData?.isFavorite == true) }
    var watched by remember(i.id) { mutableStateOf(i.userData?.played == true) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(i.id) { if (tv) runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxWidth().heightIn(min = if (tv) 460.dp else 0.dp)) {
        // Backdrop behind everything (TV), or a wide picture on top (phone).
        if (tv) {
            Img(api.backdrop(i, 1600), Modifier.matchParentSize(), RoundedCornerShape(0.dp))
            Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Relli.bg, Relli.bg.copy(alpha = 0.85f), Relli.bg.copy(alpha = 0.2f)))))
            Scrim(0.6f)
        }
        Column {
            if (!tv) Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                Img(api.backdrop(i, 1200) ?: api.poster(i, 800), Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                Scrim()
                IconButton(onClick = { Nav.controller?.popBackStack() }, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Relli.cream)
                }
            }
            Column(
                Modifier.padding(horizontal = gutter()).padding(top = if (tv) 40.dp else 0.dp).widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val logo = api.logo(i)
                if (logo != null && tv) AsyncImage(logo, i.name, Modifier.heightIn(max = 120.dp).widthIn(max = 400.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(i.name, style = MaterialTheme.typography.headlineMedium)
                Text(metaLine(i), style = MaterialTheme.typography.labelLarge, color = Relli.muted)
                if (i.genres.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    i.genres.take(4).forEach { g ->
                        Text(g, style = MaterialTheme.typography.labelMedium, color = Relli.cream,
                            modifier = Modifier.clip(CircleShape).background(Relli.raise).padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
                i.taglines.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Relli.gold) }
                i.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Relli.cream.copy(alpha = 0.88f), maxLines = if (tv) 4 else 8, overflow = TextOverflow.Ellipsis) }

                // Buttons: Play / Resume, Start over, My List, Watched
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    val target = if (i.isSeriesItem) d.nextUp else i
                    val resumeAt = target?.positionMs ?: 0L
                    val label = when {
                        i.isSeriesItem && target != null -> (if (resumeAt > 0) "Resume " else "Play ") + (target.episodeCode ?: "")
                        resumeAt > 0 -> "Resume from ${fmtClock(resumeAt)}"
                        i.type == "BoxSet" -> null
                        else -> "Play"
                    }
                    if (label != null && target != null) PillButton(label.trim(), Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(playFocus)) { Nav.play(target) }
                    if (resumeAt > 0 && target != null) PillButton("Start over", Icons.Rounded.Replay) { Nav.play(target, fromStart = true) }
                    PillButton(if (fav) "In My List" else "My List", if (fav) Icons.Rounded.Check else Icons.Rounded.Add) {
                        fav = !fav
                        scope.launch {
                            try { api.setFavorite(i.id, fav); Refresh.bump() } catch (e: Exception) { fav = !fav; toast(e.message ?: "Couldn't update My List") }
                        }
                    }
                    if (i.type != "BoxSet") PillButton(if (watched) "Watched" else "Mark watched", if (watched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked) {
                        watched = !watched
                        scope.launch {
                            try { api.setPlayed(i.id, watched); Refresh.bump() } catch (e: Exception) { watched = !watched; toast(e.message ?: "Couldn't update") }
                        }
                    }
                }
                if (!i.isSeriesItem && i.positionMs > 0) ProgressLine(i.progress, Modifier.widthIn(max = 320.dp).clip(CircleShape))
            }
        }
    }
}

@Composable
private fun Seasons(d: Details, initial: String?) {
    if (d.seasons.isEmpty()) return
    val start = initial ?: d.nextUp?.seasonId ?: d.seasons.firstOrNull { (it.userData?.unplayedCount ?: 0) > 0 }?.id ?: d.seasons.first().id
    var season by remember(d.item.id) { mutableStateOf(start) }
    val episodes by load(d.item.id, season) { ReelsApp.api.episodes(d.item.id, season) }
    Column(Modifier.padding(top = 26.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = gutter()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(d.seasons, key = { it.id }) { s -> Chip(s.name, s.id == season) { season = s.id } }
        }
        Loaded(episodes) { list ->
            Column(Modifier.padding(top = 10.dp)) { list.forEach { ep -> EpisodeRow(ep) } }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Item) {
    val tv = LocalIsTv.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = gutter() - 6.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .clickable { Nav.play(ep) }.padding(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(if (tv) 240.dp else 150.dp)) {
            Img(ReelsApp.api.landscape(ep, 480), Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(10.dp))
            ProgressLine(ep.progress, Modifier.align(Alignment.BottomCenter))
            if (ep.userData?.played == true) Icon(Icons.Rounded.CheckCircle, "Watched", tint = Relli.gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).background(Relli.bgDeep, CircleShape))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text("${ep.indexNumber ?: ""}. ${ep.name}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(ep.runTimeTicks?.let { fmtRuntime(ep.runtimeMs) }, ep.premiereDate?.take(4)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Relli.muted)
            ep.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Relli.cream.copy(alpha = 0.75f), maxLines = if (tv) 3 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun Cast(i: Item) {
    val people = i.people.filter { it.type == "Actor" || it.type == "Director" }.take(16)
    if (people.isEmpty()) return
    Shelf("Cast & crew", people, key = { it.id + it.role }) { p ->
        Column(Modifier.width(96.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Img(ReelsApp.api.image(p.id, tag = p.imageTag, width = 200), Modifier.size(80.dp), CircleShape, placeholder = p.name.take(1))
            Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(if (p.type == "Director") "Director" else p.role ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
