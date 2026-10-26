package com.relli.radio.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.relli.radio.RelliApp
import com.relli.radio.data.Song
import com.relli.radio.ui.Playback
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ---------- Loading data ----------

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

/** Runs [block] when the screen opens (or [keys] change) and tracks loading / done / error. */
@Composable
fun <T> load(vararg keys: Any?, block: suspend () -> T): State<Load<T>> {
    val retry = remember { mutableIntStateOf(0) }
    return produceState<Load<T>>(Load.Loading, *keys, retry.intValue) {
        value = try { Load.Ok(block()) } catch (e: Exception) { Load.Err(e.message ?: "Something went wrong") }
    }
}

@Composable
fun <T> Loaded(state: Load<T>, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Relli.gold, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
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

fun toast(msg: String) = Toast.makeText(RelliApp.instance, msg, Toast.LENGTH_SHORT).show()

// ---------- Album art ----------

@Composable
fun Cover(id: String?, modifier: Modifier = Modifier, size: Int = 300, shape: Shape = RoundedCornerShape(12.dp)) {
    val url = remember(id, size) { RelliApp.api.coverUrl(id, size) }
    Box(modifier.clip(shape).background(Relli.raise), contentAlignment = Alignment.Center) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                error = { Icon(Icons.Rounded.MusicNote, null, tint = Relli.faint) },
            )
        } else Icon(Icons.Rounded.MusicNote, null, tint = Relli.faint)
    }
}

// ---------- Favorites (hearts stay in sync on every screen) ----------

object Stars {
    val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    fun isStarred(id: String, fallback: Boolean) = overrides.value[id] ?: fallback
    suspend fun set(id: String, on: Boolean) {
        overrides.value = overrides.value + (id to on)
        try {
            RelliApp.api.setStarred(id, on)
            toast(if (on) "Added to favorites" else "Removed from favorites")
        } catch (e: Exception) {
            overrides.value = overrides.value + (id to !on)
            toast(e.message ?: "Couldn't update favorites")
        }
    }
}

@Composable
fun HeartButton(id: String, starred: Boolean, tint: Color = Relli.cream) {
    val map by Stars.overrides.collectAsState()
    val on = map[id] ?: starred
    val scope = rememberCoroutineScope()
    IconButton(onClick = { scope.launch { Stars.set(id, !on) } }) {
        Icon(if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (on) "Remove from favorites" else "Add to favorites", tint = if (on) Relli.gold else tint)
    }
}

// ---------- Rows and tiles ----------

fun fmtTime(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit, number: Int? = null, showCover: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val current by Playback.current.collectAsState()
    val playing = current?.mediaId == song.id
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            number != null -> Text("$number", color = if (playing) Relli.gold else Relli.faint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp), maxLines = 1)
            showCover -> Cover(song.coverArt ?: song.albumId, Modifier.size(48.dp), 120, RoundedCornerShape(8.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, color = if (playing) Relli.gold else Relli.cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmtTime(song.duration.toLong()), style = MaterialTheme.typography.bodySmall, color = Relli.muted)
        if (trailing != null) trailing()
        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions", tint = Relli.muted) }
    }
    if (menu) com.relli.radio.ui.player.MoreActionsSheet(song, nowPlaying = false, onDismiss = { menu = false })
}

@Composable
fun SectionHeader(title: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.padding(top = 30.dp, bottom = 12.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Relli.muted)
    }
}

@Composable
fun Tile(art: String?, title: String, subtitle: String?, modifier: Modifier = Modifier, round: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Cover(art, Modifier.fillMaxWidth().aspectRatio(1f), 300, if (round) CircleShape else RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector?, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(CircleShape)
            .then(if (primary) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.raise))
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = if (primary) Relli.onGold else Relli.cream, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) Relli.onGold else Relli.cream)
    }
}

val ScreenPadding = PaddingValues(horizontal = 18.dp)
val Gutter: Dp = 18.dp
