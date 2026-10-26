package com.relli.radio.ui.player

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import com.relli.radio.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.relli.radio.RelliApp
import com.relli.radio.data.Lyrics
import com.relli.radio.playback.Mixes
import com.relli.radio.playback.PlayerHub
import com.relli.radio.playback.isRadio
import com.relli.radio.playback.qualityLabel
import com.relli.radio.playback.toSong
import com.relli.radio.ui.Nav
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Cover
import com.relli.radio.ui.components.HeartButton
import com.relli.radio.ui.components.fmtTime
import com.relli.radio.ui.components.toast
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val prefs get() = RelliApp.prefs
private val arrowKeys = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter)

/** Position of the current song, refreshed a few times a second while visible. */
@Composable
fun rememberPosition(): Long {
    var pos by remember { mutableLongStateOf(Playback.position) }
    LaunchedEffect(Unit) { while (true) { pos = Playback.position; delay(250) } }
    return pos
}

@Composable
fun NowPlaying() {
    val item by Playback.current.collectAsState()
    val playing by Playback.playing.collectAsState()
    val shuffle by Playback.shuffle.collectAsState()
    val repeat by Playback.repeat.collectAsState()
    val source by Playback.source.collectAsState()
    val sleep by PlayerHub.sleep.collectAsState()
    val tone by ArtTone.tone.collectAsState()
    val visual by prefs.visual.flow.collectAsState()
    val speed by prefs.speed.flow.collectAsState()
    val pitch by prefs.pitch.flow.collectAsState()
    val eqOn by prefs.eqEnabled.flow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val current = item ?: return
    val radio = current.isRadio
    val song = remember(current) { current.toSong() }
    val position = rememberPosition()
    val duration = Playback.duration

    var queueOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var lyricsChecked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id, visual) {
        if (visual == "lyrics" && lyricsChecked != song.id && !radio) {
            lyrics = null
            lyrics = RelliApp.api.lyrics(song)
            lyricsChecked = song.id
        }
    }

    BackHandler { if (queueOpen) queueOpen = false else Nav.playerOpen.value = false }

    // TV remotes start on the play button, so the arrows can reach everything from there.
    val playFocus = remember { FocusRequester() }
    var anyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(350); runCatching { playFocus.requestFocus() } }
    // When a sheet (More actions, Equalizer, Speed…) closes, bring the selection back to Play.
    LaunchedEffect(sheet) { if (sheet == null) { delay(250); if (!anyFocused) runCatching { playFocus.requestFocus() } } }

    val bg by animateColorAsState(tone.bg, tween(700), label = "bg")
    val deep by animateColorAsState(tone.deep, tween(700), label = "deep")
    val btn by animateColorAsState(tone.button, tween(700), label = "btn")

    // What the big area shows. Lyrics mode falls back to the visualizer when a song has none.
    val show = when {
        queueOpen -> "queue"
        visual == "lyrics" && (radio || (lyricsChecked == song.id && lyrics == null)) -> "visualizer"
        visual == "lyrics" && lyrics == null -> "cover"
        else -> visual
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to bg, 0.45f to bg, 1f to deep))
            // If the remote's selection ever ends up nowhere (for example after a sheet closes),
            // the next arrow press puts it back on the play button.
            .onFocusChanged { anyFocused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (!anyFocused && e.type == KeyEventType.KeyDown && e.key in arrowKeys) { runCatching { playFocus.requestFocus() }; true } else false
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val wide = maxWidth > 700.dp
            Column(Modifier.fillMaxSize()) {
                // Header
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { Nav.playerOpen.value = false }) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Relli.cream) }
                    Text(
                        if (source.isNotEmpty()) "Playing from $source" else "Now playing",
                        style = MaterialTheme.typography.labelMedium, color = Relli.cream.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.width(48.dp))
                }

                val stage: @Composable (Modifier) -> Unit = { m ->
                    AnimatedContent(show, m, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "stage") { s ->
                        when (s) {
                            "queue" -> QueuePanel(onSave = { sheet = "save" }, onSmart = { sheet = "smart" })
                            "lyrics" -> lyrics?.let { LyricsPanel(it) }
                            "visualizer" -> VisualizerPanel()
                            else -> BoxWithConstraints(
                                Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dy -> if (dy > 40) Nav.playerOpen.value = false }
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                // As big as fits, in either direction (phones are tall, TVs are wide).
                                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                                Cover(song.coverArt ?: song.albumId, Modifier.size(side).shadow(30.dp, RoundedCornerShape(22.dp)), 800, RoundedCornerShape(22.dp))
                            }
                        }
                    }
                }

                val controls: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Title, artists, album
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(current.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (radio) Text("Internet radio", color = Relli.cream.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge)
                                else {
                                    Row {
                                        song.artistList.ifEmpty { listOf(com.relli.radio.data.ArtistRef("", song.artist ?: "")) }.forEachIndexed { i, a ->
                                            Text(
                                                (if (i > 0) ", " else "") + a.name,
                                                color = Relli.cream.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                                                modifier = if (a.id.isNotEmpty()) Modifier.clickable { Nav.artist(a.id) } else Modifier,
                                            )
                                        }
                                    }
                                    Text(song.album ?: "", color = Relli.cream.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { song.albumId?.let { Nav.album(it) } })
                                }
                            }
                            if (!radio) HeartButton(song.id, song.starred != null)
                            IconButton(onClick = { if (!radio) sheet = "more" }) { Icon(Icons.Rounded.MoreHoriz, "More actions", tint = Relli.cream) }
                        }

                        // Waveform seek bar and times
                        WaveSeek(song.id, position, duration, radio, onSeek = { Playback.seekTo(it.coerceIn(0L, duration)) })
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmtTime(position / 1000), style = MaterialTheme.typography.labelMedium, color = Relli.cream.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                            Text(if (radio) "Live" else qualityLabel(song, Playback.maxBitRate()), style = MaterialTheme.typography.labelMedium, color = Relli.cream.copy(alpha = 0.85f))
                            Text(if (radio) "Live" else "-" + fmtTime((duration - position).coerceAtLeast(0) / 1000), style = MaterialTheme.typography.labelMedium,
                                color = Relli.cream.copy(alpha = 0.75f), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }

                        // Transport
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            ModeButton(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", repeat != Player.REPEAT_MODE_OFF, !radio) { Playback.cycleRepeat() }
                            IconButton(onClick = { Playback.prev() }, enabled = !radio, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Relli.cream, modifier = Modifier.size(36.dp)) }
                            Box(
                                Modifier.size(84.dp).shadow(14.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(btn)
                                    .focusRequester(playFocus).clickable { Playback.toggle() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Relli.cream, modifier = Modifier.size(40.dp)) }
                            IconButton(onClick = { Playback.next() }, enabled = !radio, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", tint = Relli.cream, modifier = Modifier.size(36.dp)) }
                            ModeButton(Icons.Rounded.Shuffle, "Shuffle", shuffle, !radio) { Playback.setShuffle(!shuffle) }
                        }

                        // Toolbar
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Tool(Icons.Rounded.Bedtime, "Sleep timer", sleep.active, if (sleep.mode == PlayerHub.SleepMode.TIME) "${(sleep.remainingMs() / 60000) + 1}m" else null) { sheet = "sleep" }
                            Tool(Icons.Rounded.Radio, "Radio mix", false) {
                                if (!radio) scope.launch {
                                    try {
                                        val m = Mixes.instant(song); Playback.play(m.songs, 0, "Radio: ${song.title}")
                                        prefs.smartQueue.value = "similar"; PlayerHub.smartChanged(); toast("Radio mix on")
                                    } catch (e: Exception) { toast(e.message ?: "Couldn't start the radio mix") }
                                }
                            }
                            Tool(Icons.Rounded.Speed, "Speed and pitch", speed != 1f || pitch != 0, if (speed != 1f) "%.2f".format(speed).trimEnd('0').trimEnd('.') + "x" else null) { sheet = "speed" }
                            Tool(Icons.Rounded.KeyboardDoubleArrowUp, "Queue", queueOpen) { queueOpen = !queueOpen }
                            Tool(
                                when (visual) { "lyrics" -> Icons.Rounded.FormatQuote; "visualizer" -> Icons.Rounded.GraphicEq; else -> Icons.Rounded.Image },
                                "Switch between cover, lyrics and visualizer", visual != "cover",
                            ) {
                                queueOpen = false
                                val next = when (visual) { "cover" -> "lyrics"; "lyrics" -> "visualizer"; else -> "cover" }
                                prefs.visual.value = next
                                toast(when (next) { "lyrics" -> "Showing lyrics"; "visualizer" -> "Showing the visualizer"; else -> "Showing the cover" })
                            }
                            Tool(Icons.Rounded.Equalizer, "Equalizer", eqOn) { sheet = "eq" }
                            Tool(Icons.Rounded.Cast, "Output device", false) { openOutputSwitcher(context) }
                        }
                    }
                }

                if (wide) {
                    // TVs, tablets, landscape: the visual (cover, lyrics, visualizer or queue) on the left,
                    // everything you control on the right, sized to fit the screen.
                    Row(
                        Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        stage(Modifier.weight(1f).fillMaxHeight())
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            controls(Modifier.widthIn(max = 560.dp).fillMaxWidth())
                        }
                    }
                } else {
                    stage(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp))
                    controls(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 10.dp))
                }
            }
        }
    }

    when (sheet) {
        "more" -> MoreActionsSheet(song, nowPlaying = true) { sheet = null }
        "eq" -> EqualizerSheet { sheet = null }
        "speed" -> SpeedSheet { sheet = null }
        "sleep" -> SleepSheet { sheet = null }
        "smart" -> SmartSheet { sheet = null }
        "save" -> PlaylistPickerSheet(Playback.queue.value.filter { !it.isRadio }.map { it.toSong() }) { sheet = null }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Icon(icon, label, tint = if (on) Relli.cream else Relli.cream.copy(alpha = if (enabled) 0.6f else 0.25f))
            if (on) Box(Modifier.padding(top = 30.dp).size(4.dp).clip(CircleShape).background(Relli.cream))
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, on: Boolean, badge: String? = null, onClick: () -> Unit) {
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (on) Relli.cream.copy(alpha = 0.12f) else Color.Transparent).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Relli.cream else Relli.cream.copy(alpha = 0.72f)) }
        if (badge != null) Text(badge, style = MaterialTheme.typography.labelSmall, color = Relli.cream, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Opens Android's "play on" picker (phone speaker, Bluetooth, Cast-enabled speakers on newer phones). */
private fun openOutputSwitcher(context: android.content.Context) {
    try {
        val i = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui")
            .putExtra("package_name", context.packageName)
        context.sendBroadcast(i)
    } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

// ---------- Mini player ----------

@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val item by Playback.current.collectAsState()
    val playing by Playback.playing.collectAsState()
    val tone by ArtTone.tone.collectAsState()
    val pill by animateColorAsState(tone.pill, tween(700), label = "pill")
    val current = item ?: return
    val song = remember(current) { current.toSong() }
    val position = rememberPosition()
    val duration = Playback.duration
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clickable { Nav.playerOpen.value = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.coverArt ?: song.albumId, Modifier.size(54.dp).shadow(8.dp, RoundedCornerShape(14.dp)), 160, RoundedCornerShape(14.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(54.dp).clip(CircleShape).background(pill)) {
            Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(current.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.mediaMetadata.artist?.toString() ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.cream.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { Playback.toggle() }) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Relli.cream, modifier = Modifier.size(28.dp))
                }
            }
            if (duration > 0) {
                Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp).fillMaxWidth().height(2.dp).background(Relli.cream.copy(alpha = 0.14f))) {
                    Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).fillMaxHeight().background(Relli.cream))
                }
            }
        }
    }
}
