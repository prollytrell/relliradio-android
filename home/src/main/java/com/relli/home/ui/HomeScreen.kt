package com.relli.home.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.relli.home.HomeApp
import com.relli.home.R
import com.relli.home.data.App
import com.relli.home.data.Apps
import com.relli.home.data.Item
import com.relli.home.data.Reels
import com.relli.home.data.Weather
import com.relli.home.media.NowPlaying
import com.relli.home.ui.components.IconButton
import com.relli.home.ui.components.Img
import com.relli.home.ui.components.ProgressLine
import com.relli.home.ui.theme.Relli
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What the big area at the top describes: the focused show, movie, channel or app. */
private data class Spotlight(
    val title: String,
    val logo: String? = null,
    val meta: String? = null,
    val overview: String? = null,
    val backdrop: String? = null,
)

private val GUTTER = 48.dp

@Composable
fun HomeScreen(context: Context, appsChanged: StateFlow<Int>, resumed: StateFlow<Int>) {
    val prefs = HomeApp.prefs
    var settings by remember { mutableStateOf(false) }
    val appsVersion by appsChanged.collectAsState()
    val resumeCount by resumed.collectAsState()
    val order by prefs.appOrder.flow.collectAsState()
    val hidden by prefs.hidden.flow.collectAsState()

    // Apps
    var allApps by remember { mutableStateOf<List<App>>(emptyList()) }
    LaunchedEffect(appsVersion) { allApps = Apps.load(context) }
    val apps = remember(allApps, order, hidden) { Apps.arrange(allApps, order, hidden) }

    // Jellyfin rows (via RelliReels): refreshed when you come back home, at most once a minute.
    var reels by remember { mutableStateOf<Reels.State?>(null) }
    var loadedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(resumeCount) {
        if (System.currentTimeMillis() - loadedAt > 60_000 || reels !is Reels.State.Ready) {
            reels = Reels.load(context)
            loadedAt = System.currentTimeMillis()
        }
    }

    // Weather: every 20 minutes
    val city by prefs.city.flow.collectAsState()
    val fahrenheit by prefs.fahrenheit.flow.collectAsState()
    val weather by produceState<Weather.Now?>(null, city, fahrenheit, resumeCount) {
        if (city.isBlank()) { value = null; return@produceState }
        while (true) {
            value = runCatching { Weather.now(prefs.lat.value.toDouble(), prefs.lon.value.toDouble(), fahrenheit) }.getOrNull() ?: value
            delay(20 * 60_000L)
        }
    }

    // You're already home; Back does nothing here.
    BackHandler(enabled = !settings) {}

    if (settings) {
        SettingsScreen(allApps) { settings = false }
        return
    }

    val ready = reels as? Reels.State.Ready
    var spotlight by remember { mutableStateOf<Spotlight?>(null) }
    // Until you move the selection, feature something from your library.
    val idle = ready?.rows?.featured?.firstOrNull()?.let { spot(ready.rows.session, it) }
    val shown = spotlight ?: idle

    Box(Modifier.fillMaxSize()) {
        // Backdrop of whatever is selected, fading between pictures.
        AnimatedContent(shown?.backdrop, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "backdrop", modifier = Modifier.fillMaxSize()) { url ->
            if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopEnd)
            else Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Relli.gold.copy(alpha = 0.10f), Relli.bg), radius = 1600f)))
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Relli.bg, 0.45f to Relli.bg.copy(alpha = 0.82f), 1f to Relli.bg.copy(alpha = 0.15f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Relli.bg.copy(alpha = 0.55f), 0.25f to Color.Transparent, 0.55f to Relli.bg.copy(alpha = 0.85f), 1f to Relli.bg)))

        Column(Modifier.fillMaxSize()) {
            TopBar(weather, onSettings = { settings = true })
            SpotlightArea(shown)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 40.dp)) {
                item { NowPlayingRow { spotlight = it } }
                item { AppsRow(context, apps) { spotlight = it } }
                when (val r = reels) {
                    is Reels.State.Ready -> {
                        val s = r.rows.session
                        val cont = (r.rows.resume + r.rows.nextUp).distinctBy { it.id }
                        if (prefs.showContinue.value && cont.isNotEmpty()) item {
                            Shelf("Continue watching", cont) { i -> WideCard(s, i, onFocus = { spotlight = spot(s, i) }) { Reels.play(context, i) } }
                        }
                        if (prefs.showLive.value && r.rows.onNow.isNotEmpty()) item {
                            Shelf("On now", r.rows.onNow) { ch -> ChannelCard(s, ch, onFocus = { spotlight = spot(s, ch) }) { Reels.live(context, ch) } }
                        }
                        if (prefs.showNew.value && r.rows.latest.isNotEmpty()) item {
                            Shelf("Recently added", r.rows.latest) { i -> PosterCard(s, i, onFocus = { spotlight = spot(s, i) }) { Reels.open(context, i) } }
                        }
                    }
                    is Reels.State.SignedOut -> item { Notice("Sign in to RelliReels to see your shows and live TV here.") { Apps.launch(context, Reels.PKG) } }
                    is Reels.State.NotInstalled -> item { Notice("Install RelliReels to see Continue watching, Live TV and your newest movies here.", null) }
                    is Reels.State.Error -> item { Notice("Your Jellyfin rows didn't load: ${r.message}", null) }
                    null -> Unit
                }
            }
        }
    }
}

private fun spot(s: Reels.Session, i: Item): Spotlight = Spotlight(
    title = when {
        i.isEpisode -> i.seriesName ?: i.name
        i.isChannel -> i.currentProgram?.name ?: i.name
        else -> i.name
    },
    logo = if (i.isChannel) null else Reels.logo(s, i),
    meta = when {
        i.isEpisode -> listOfNotNull(i.episodeCode, i.name).joinToString(" · ")
        i.isChannel -> listOfNotNull("LIVE", i.channelNumber, i.name).joinToString(" · ")
        else -> listOfNotNull(i.year?.toString(), i.officialRating, i.genres.take(2).joinToString(" · ").ifBlank { null }).joinToString("  ·  ")
    },
    overview = if (i.isChannel) i.currentProgram?.overview else i.overview,
    backdrop = Reels.backdrop(s, i),
)

// ---------- Top bar: greeting, weather, clock ----------

@Composable
private fun TopBar(weather: Weather.Now?, onSettings: () -> Unit) {
    val clock24 by HomeApp.prefs.clock24.flow.collectAsState()
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(10_000) } }
    val greeting = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = GUTTER, end = GUTTER - 8.dp, top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.rr_logo), null, Modifier.size(42.dp))
        Text(greeting, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
        if (weather != null) {
            Text(weather.emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 6.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 22.dp)) {
                Text("${weather.temp}°  ${weather.label}", style = MaterialTheme.typography.titleSmall)
                if (weather.high != null) Text("H ${weather.high}°  L ${weather.low}°", style = MaterialTheme.typography.bodySmall, color = Relli.muted)
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 10.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h:mm a")), style = MaterialTheme.typography.headlineSmall)
            Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), style = MaterialTheme.typography.bodySmall, color = Relli.muted)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "RelliHome settings", tint = Relli.cream) }
        IconButton(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
            Icon(Icons.Rounded.Settings, "TV settings", tint = Relli.cream)
        }
    }
}

// ---------- The big description at the top ----------

@Composable
private fun SpotlightArea(s: Spotlight?) {
    Box(Modifier.fillMaxWidth().height(210.dp).padding(horizontal = GUTTER, vertical = 14.dp), contentAlignment = Alignment.BottomStart) {
        AnimatedContent(s, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "spotlight") { sp ->
            if (sp != null) Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sp.logo != null) AsyncImage(sp.logo, sp.title, Modifier.heightIn(max = 90.dp).widthIn(max = 360.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(sp.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sp.meta.isNullOrBlank()) Text(sp.meta, style = MaterialTheme.typography.labelLarge, color = Relli.muted, maxLines = 1)
                if (!sp.overview.isNullOrBlank()) Text(sp.overview, style = MaterialTheme.typography.bodyMedium, color = Relli.cream.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- Rows ----------

@Composable
private fun <T> Shelf(title: String, items: List<T>, card: @Composable (T) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = GUTTER, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = GUTTER - 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(items) { _, it -> card(it) }
        }
    }
}

/** Grows a little when selected, like every TV home screen. */
@Composable
private fun Modifier.lift(focused: Boolean): Modifier {
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "lift")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
private fun AppsRow(context: Context, apps: List<App>, onFocus: (Spotlight) -> Unit) {
    val first = remember { FocusRequester() }
    var menuFor by remember { mutableStateOf<App?>(null) }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) { delay(150); runCatching { first.requestFocus() } } }
    Column(Modifier.padding(top = 10.dp)) {
        Text("Apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = GUTTER, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = GUTTER - 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { idx, app ->
                var focused by remember { mutableStateOf(false) }
                var longPressed by remember { mutableStateOf(false) }
                Box(
                    Modifier.width(192.dp).aspectRatio(16f / 9f)
                        .lift(focused)
                        .then(if (idx == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(Spotlight(app.label)) }
                        // Hold OK (or press Menu) for options: move, hide, app info.
                        .onPreviewKeyEvent { e ->
                            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                            when {
                                e.key == Key.Menu && e.type == KeyEventType.KeyUp -> { menuFor = app; true }
                                ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 1 -> { longPressed = true; menuFor = app; true }
                                ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 1 -> true
                                ok && e.type == KeyEventType.KeyUp && longPressed -> { longPressed = false; true }
                                else -> false
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(Relli.raise)
                        .clickable { Apps.launch(context, app.pkg) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (app.banner != null) Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        app.icon?.let { Image(it, null, Modifier.size(48.dp)) }
                        Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp))
                    }
                }
            }
        }
    }
    menuFor?.let { app -> AppMenu(context, app, apps) { menuFor = null } }
}

@Composable
private fun WideCard(s: Reels.Session, i: Item, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(260.dp).lift(focused).clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick).padding(4.dp),
    ) {
        Box {
            Img(Reels.landscape(s, i), Modifier.fillMaxWidth().aspectRatio(16f / 9f), placeholder = i.seriesName ?: i.name)
            ProgressLine(i.progress, Modifier.align(Alignment.BottomCenter))
        }
        Text(i.seriesName ?: i.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val sub = if (i.isEpisode) listOfNotNull(i.episodeCode, i.name).joinToString(" · ") else i.year?.toString()
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterCard(s: Reels.Session, i: Item, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(140.dp).lift(focused).clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick).padding(4.dp),
    ) {
        Img(Reels.poster(s, i), Modifier.fillMaxWidth().aspectRatio(2f / 3f), placeholder = i.name)
        Text(i.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChannelCard(s: Reels.Session, ch: Item, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p = ch.currentProgram
    Column(
        Modifier.width(260.dp).lift(focused).clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Relli.raise), contentAlignment = Alignment.Center) {
            Img(Reels.channelLogo(s, ch), Modifier.fillMaxSize(0.55f), RoundedCornerShape(0.dp), placeholder = ch.name, scale = ContentScale.Fit)
            Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Relli.cream,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFB3261E)).padding(horizontal = 6.dp, vertical = 2.dp))
            ProgressLine(programProgress(p), Modifier.align(Alignment.BottomCenter))
        }
        Text(p?.name ?: ch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(listOfNotNull(ch.channelNumber, ch.name).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1)
    }
}

private fun programProgress(p: Item?): Float {
    val s = p?.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val e = p.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val total = Duration.between(s, e).seconds.toFloat()
    return if (total <= 0) 0f else (Duration.between(s, Instant.now()).seconds / total).coerceIn(0f, 1f)
}

// ---------- Now playing (any app) ----------

@Composable
private fun NowPlayingRow(onFocus: (Spotlight) -> Unit) {
    val info by NowPlaying.info.collectAsState()
    val np = info ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val label = remember(np.app) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(np.app, 0)).toString() }.getOrDefault("") }
    val controls = np.controller.transportControls
    Column(Modifier.padding(top = 4.dp)) {
        Text("Now playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = GUTTER, bottom = 8.dp))
        Row(
            Modifier.padding(horizontal = GUTTER - 6.dp).clip(RoundedCornerShape(18.dp)).background(Relli.surface.copy(alpha = 0.92f)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .onFocusChanged { if (it.isFocused) onFocus(Spotlight(np.title, meta = listOfNotNull(np.subtitle, label).joinToString(" · "))) }
                    .clickable {
                        // Open the app that's playing.
                        np.controller.sessionActivity?.let { runCatching { it.send() } } ?: Apps.launch(context, np.app)
                    }.padding(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (np.art != null) Image(np.art.asImageBitmap(), null, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(horizontal = 14.dp).widthIn(max = 420.dp)) {
                        Text(np.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(np.subtitle, label.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = { controls.skipToPrevious() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Relli.cream) }
            IconButton(onClick = { if (np.playing) controls.pause() else controls.play() }) {
                Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (np.playing) "Pause" else "Play", tint = Relli.gold)
            }
            IconButton(onClick = { controls.skipToNext() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = Relli.cream) }
        }
    }
}

@Composable
private fun Notice(text: String, onClick: (() -> Unit)?) {
    Box(
        Modifier.padding(start = GUTTER - 6.dp, end = GUTTER, top = 18.dp).clip(RoundedCornerShape(16.dp)).background(Relli.surface.copy(alpha = 0.9f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(18.dp),
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, color = Relli.muted) }
}

