package com.relli.reels.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.Nav
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.flow.MutableStateFlow

// ---------- Loading data ----------

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

/** Bumped after you watch something or change favorites, so every screen reloads its rows. */
object Refresh {
    val version = MutableStateFlow(0)
    fun bump() { version.value++ }
}

/** Runs [block] when the screen opens (or [keys] change) and tracks loading / done / error. */
@Composable
fun <T> load(vararg keys: Any?, block: suspend () -> T): State<Load<T>> {
    val retry = remember { mutableIntStateOf(0) }
    val v by Refresh.version.collectAsState()
    return produceState<Load<T>>(Load.Loading, *keys, retry.intValue, v) {
        // Keep showing the old content while it refreshes in the background.
        val result = try { Load.Ok(block()) } catch (e: Exception) { Load.Err(e.message ?: "Something went wrong") }
        if (result is Load.Err && value is Load.Ok) return@produceState
        value = result
    }
}

@Composable
fun <T> Loaded(state: Load<T>, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Relli.gold, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
        }
        is Load.Err -> Empty(Icons.Rounded.CloudOff, "This didn't load", state.message)
        is Load.Ok -> content(state.value)
    }
}

@Composable
fun Empty(icon: ImageVector, title: String, text: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Relli.gold, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (text != null) Text(text, color = Relli.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

fun toast(msg: String) = Toast.makeText(ReelsApp.instance, msg, Toast.LENGTH_SHORT).show()

// ---------- Formatting ----------

fun fmtRuntime(ms: Long): String {
    val m = (ms / 60_000).toInt()
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun timeLeft(i: Item): String? {
    if (i.runTimeTicks == null || i.positionMs <= 0) return null
    return "${fmtRuntime((i.runtimeMs - i.positionMs).coerceAtLeast(60_000))} left"
}

/** "2019 · PG-13 · 1h 52m" */
fun metaLine(i: Item): String = listOfNotNull(
    i.year?.toString(),
    i.officialRating,
    if (i.isSeriesItem) i.childCount?.let { if (it == 1) "1 season" else "$it seasons" } else i.runTimeTicks?.let { fmtRuntime(i.runtimeMs) },
    i.communityRating?.let { "★ %.1f".format(it) },
).joinToString("  ·  ")

// ---------- Pictures ----------

@Composable
fun Img(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), placeholder: String? = null, scale: ContentScale = ContentScale.Crop) {
    Box(modifier.clip(shape).background(Relli.raise), contentAlignment = Alignment.Center) {
        val fallback = @Composable {
            if (placeholder != null) Text(placeholder, style = MaterialTheme.typography.labelLarge, color = Relli.muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            else Icon(Icons.Rounded.Movie, null, tint = Relli.faint)
        }
        if (url != null) SubcomposeAsyncImage(model = url, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize(), error = { fallback() })
        else fallback()
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0.01f) return
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Relli.gold))
    }
}

// ---------- Sizes ----------

@Composable
fun posterWidth(): Dp = if (LocalIsTv.current) 150.dp else 116.dp

@Composable
fun wideWidth(): Dp = if (LocalIsTv.current) 300.dp else 240.dp

@Composable
fun gutter(): Dp = if (LocalIsTv.current) 40.dp else 18.dp

// ---------- Cards ----------

/** A 2:3 poster with the title underneath (movies and shows). */
@Composable
fun PosterCard(item: Item, modifier: Modifier = Modifier, width: Dp = posterWidth(), onClick: () -> Unit = { Nav.open(item) }) {
    Column(modifier.width(width).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            Img(ReelsApp.api.poster(item, 360), Modifier.fillMaxWidth().aspectRatio(2f / 3f), placeholder = item.name)
            item.userData?.let { u ->
                if (u.played) Badge("Watched", Modifier.align(Alignment.TopEnd))
                else if ((u.unplayedCount ?: 0) > 0 && item.isSeriesItem) Badge("${u.unplayedCount}", Modifier.align(Alignment.TopEnd))
            }
            ProgressLine(item.progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = item.year?.toString() ?: item.seriesName
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Relli.cream,
        modifier = modifier.padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Relli.bgDeep.copy(alpha = 0.85f)).padding(horizontal = 7.dp, vertical = 2.dp))
}

/** A 16:9 card with a progress bar: continue watching, next up, episodes. */
@Composable
fun WideCard(item: Item, modifier: Modifier = Modifier, width: Dp = wideWidth(), title: String? = null, subtitle: String? = null, onClick: () -> Unit) {
    Column(modifier.width(width).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            Img(ReelsApp.api.landscape(item, 640), Modifier.fillMaxWidth().aspectRatio(16f / 9f), placeholder = item.seriesName ?: item.name)
            ProgressLine(item.progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(title ?: (item.seriesName ?: item.name), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = subtitle ?: listOfNotNull(
            item.episodeCode?.let { if (item.isEpisode) "$it · ${item.name}" else null },
            timeLeft(item),
        ).joinToString(" · ").ifBlank { null }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A titled, side-scrolling row of cards, like every streaming app's home screen. */
@Composable
fun <T> Shelf(title: String, items: List<T>, onTitle: (() -> Unit)? = null, key: ((T) -> Any)? = null, card: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    val g = gutter()
    Column(Modifier.padding(top = 22.dp)) {
        Row(
            Modifier.padding(start = g - 4.dp, end = g, bottom = 8.dp).clip(RoundedCornerShape(10.dp))
                .then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (onTitle != null) Text("  See all", style = MaterialTheme.typography.labelLarge, color = Relli.gold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = g - 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = key) { card(it) }
        }
    }
}

// ---------- Buttons ----------

@Composable
fun PillButton(text: String, icon: ImageVector?, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(CircleShape)
            .then(if (primary) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.raise))
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = if (primary) Relli.onGold else Relli.cream, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) Relli.onGold else Relli.cream, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.raise))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Relli.onGold else Relli.muted, maxLines = 1) }
}

/** Darkens the bottom of a backdrop so text on top stays readable. */
@Composable
fun BoxScope.Scrim(strength: Float = 1f) {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Relli.bg.copy(alpha = 0.55f * strength), Relli.bg))))
}
