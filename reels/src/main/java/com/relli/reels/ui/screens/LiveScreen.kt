package com.relli.reels.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.Nav
import com.relli.reels.ui.components.Chip
import com.relli.reels.ui.components.Empty
import com.relli.reels.ui.components.IconButton
import com.relli.reels.ui.components.Img
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.Loaded
import com.relli.reels.ui.components.ProgressLine
import com.relli.reels.ui.components.gutter
import com.relli.reels.ui.components.load
import com.relli.reels.ui.components.toast
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FILTERS = listOf("all" to "All channels", "favorites" to "Favorites", "movies" to "Movies", "series" to "Shows", "news" to "News", "sports" to "Sports", "kids" to "Kids")
private val TIME = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

private class Guide(val channels: List<Item>, val programs: Map<String, List<Item>>)

/** Live TV: every channel with what's on now and next, like a cable guide. Pick a channel to watch. */
@Composable
fun LiveScreen(bottom: Dp) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val state by load(filter) {
        val channels = ReelsApp.api.channels(filter)
        val programs = runCatching { ReelsApp.api.programs(channels.take(150).map { it.id }) }.getOrDefault(emptyList())
        Guide(channels, programs.groupBy { it.channelId ?: "" })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 30.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 14.dp)) {
                Text("Live TV", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = gutter()))
                LazyRow(contentPadding = PaddingValues(horizontal = gutter()), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    items(FILTERS) { (k, label) -> Chip(label, filter == k) { filter = k } }
                }
            }
        }
        item {
            Loaded(state) { g ->
                if (g.channels.isEmpty()) {
                    Column(Modifier.padding(horizontal = gutter())) {
                        if (filter == "all") Empty(Icons.Rounded.LiveTv, "Live TV isn't set up yet",
                            "In Jellyfin, open Dashboard → Live TV and add a tuner: an HDHomeRun for antenna channels, or an M3U playlist for free streaming channels. Then add a guide (XMLTV) so you can see what's on.")
                        else Text("No channels in this category.", color = Relli.muted, modifier = Modifier.padding(vertical = 20.dp))
                    }
                } else Column { g.channels.forEach { ch -> ChannelRow(ch, g.programs[ch.id].orEmpty()) } }
            }
        }
    }
}

@Composable
private fun ChannelRow(ch: Item, programs: List<Item>) {
    val tv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    var fav by remember(ch.id) { mutableStateOf(ch.userData?.isFavorite == true) }
    val list = programs.ifEmpty { listOfNotNull(ch.currentProgram) }
    Row(Modifier.fillMaxWidth().height(if (tv) 96.dp else 84.dp).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        // Channel: logo, number, favorite
        Row(
            Modifier.padding(start = gutter() - 6.dp).width(if (tv) 200.dp else 128.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                .clickable { Nav.playChannel(ch.id) }.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(if (tv) 64.dp else 50.dp).clip(RoundedCornerShape(10.dp)).background(Relli.raise), contentAlignment = Alignment.Center) {
                Img(ReelsApp.api.channelLogo(ch, 200), Modifier.fillMaxSize().padding(5.dp), RoundedCornerShape(0.dp), placeholder = ch.channelNumber ?: ch.name.take(3), scale = ContentScale.Fit)
            }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(ch.channelNumber ?: "", style = MaterialTheme.typography.labelMedium, color = Relli.gold, maxLines = 1)
                Text(ch.name, style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (tv) IconButton(onClick = {
            fav = !fav
            scope.launch { try { ReelsApp.api.setFavorite(ch.id, fav) } catch (e: Exception) { fav = !fav; toast(e.message ?: "Couldn't update") } }
        }) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Remove favorite" else "Favorite channel", tint = if (fav) Relli.gold else Relli.faint) }
        // Programs: now, next, later
        LazyRow(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(end = gutter()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (list.isEmpty()) item { ProgramCell(null, ch, true) }
            items(list.size) { idx -> ProgramCell(list[idx], ch, idx == 0) }
        }
    }
}

@Composable
private fun ProgramCell(p: Item?, ch: Item, now: Boolean) {
    val tv = LocalIsTv.current
    Box(
        Modifier.width(if (now) (if (tv) 320.dp else 220.dp) else (if (tv) 240.dp else 170.dp)).fillMaxHeight().clip(RoundedCornerShape(12.dp))
            .background(if (now) Relli.raise else Relli.surface).clickable { Nav.playChannel(ch.id) },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            val start = p?.startDate?.let { runCatching { TIME.format(Instant.parse(it)) }.getOrNull() }
            val end = p?.endDate?.let { runCatching { TIME.format(Instant.parse(it)) }.getOrNull() }
            Text(if (now) "On now" + (end?.let { " · until $it" } ?: "") else start ?: "", style = MaterialTheme.typography.labelSmall, color = if (now) Relli.gold else Relli.muted)
            Text(p?.name ?: ch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p?.episodeTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (now) ProgressLine(programProgress(p), Modifier.align(Alignment.BottomCenter))
    }
}
