package com.relli.reels.ui.player

import android.graphics.Color as AColor
import android.graphics.Typeface
import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.relli.reels.ReelsApp
import com.relli.reels.ui.components.IconButton
import com.relli.reels.ui.components.Img
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.components.PillButton
import com.relli.reels.ui.components.RemoteSlider
import com.relli.reels.ui.components.fmtClock
import com.relli.reels.ui.theme.Relli
import kotlinx.coroutines.delay

private val QUALITIES = listOf<Pair<Int?, String>>(null to "Auto (from Settings)", 0 to "Original", 20 to "20 Mbps · 1080p high", 10 to "10 Mbps · 1080p", 6 to "6 Mbps · 720p", 3 to "3 Mbps · 480p", 1 to "1 Mbps · data saver")
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun PlayerScreen(c: PlayerController, onBack: () -> Unit) {
    val tv = LocalIsTv.current
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) } // any interaction restarts the hide timer
    val playFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val subSize = ReelsApp.prefs.subtitleSize.value

    // Controls fade out after a few seconds of watching.
    LaunchedEffect(controls, poke, c.playing, panel) {
        if (controls && c.playing && panel == null) { delay(4500); controls = false }
    }
    LaunchedEffect(controls) {
        delay(80) // let the controls appear first
        if (controls) runCatching { playFocus.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            panel != null -> panel = null
            c.upNextCountdown != null -> c.cancelUpNext()
            controls && tv -> controls = false
            else -> onBack()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke++
                when (e.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY, AKeyEvent.KEYCODE_MEDIA_PAUSE -> { c.toggle(); controls = true; true }
                    AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { c.seekBy(30_000); controls = true; true }
                    AKeyEvent.KEYCODE_MEDIA_REWIND -> { c.seekBy(-10_000); controls = true; true }
                    AKeyEvent.KEYCODE_MEDIA_NEXT -> { c.playNext(); true }
                    else -> if (controls || panel != null || c.activeSkip != null || c.upNextCountdown != null) false else when (e.key.nativeKeyCode) {
                        // Controls hidden: OK pauses, Left/Right jump back/forward, Up/Down bring the controls back.
                        AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER -> { c.toggle(); controls = true; true }
                        AKeyEvent.KEYCODE_DPAD_LEFT -> { c.seekBy(-10_000); controls = true; true }
                        AKeyEvent.KEYCODE_DPAD_RIGHT -> { c.seekBy(30_000); controls = true; true }
                        AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> { controls = true; true }
                        else -> false
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; poke++ },
                    onDoubleTap = { o -> if (o.x < size.width / 2) c.seekBy(-10_000) else c.seekBy(30_000); controls = true; poke++ },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = c.exo
                    keepScreenOn = true
                    setShutterBackgroundColor(AColor.BLACK)
                    subtitleView?.setStyle(CaptionStyleCompat(AColor.WHITE, 0x99000000.toInt(), AColor.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, AColor.BLACK, Typeface.DEFAULT_BOLD))
                    subtitleView?.setFractionalTextSize(when (subSize) { "s" -> 0.045f; "l" -> 0.075f; else -> 0.058f })
                }
            },
            update = { it.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize(),
        )

        if (c.buffering && c.error == null) CircularProgressIndicator(color = Relli.gold, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(48.dp))

        AnimatedVisibility(controls && c.error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Controls(c, tv, playFocus, zoom, onZoom = { zoom = !zoom }, onPanel = { panel = it; poke++ }, onBack = onBack, onPoke = { poke++ })
        }

        // "Skip intro" sits bottom-right; on TV it takes the remote's focus so one OK press skips.
        val skip = c.activeSkip
        if (skip != null && ReelsApp.prefs.skipIntro.value != "off" && panel == null) {
            LaunchedEffect(skip.startTicks, controls) { if (!controls) runCatching { skipFocus.requestFocus() } }
            PillButton(
                when (skip.type) { "Recap" -> "Skip recap"; "Preview" -> "Skip preview"; "Commercial" -> "Skip ad"; else -> "Skip intro" },
                Icons.Rounded.SkipNext, primary = true,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = if (controls) 150.dp else 40.dp).focusRequester(skipFocus),
            ) { c.skip(skip) }
        }

        if (c.upNextCountdown != null && panel == null) UpNext(c, Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = if (controls) 150.dp else 36.dp))

        c.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 460.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, style = MaterialTheme.typography.titleMedium, color = Relli.cream)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val retry = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
                    PillButton("Try again", null, primary = true, modifier = Modifier.focusRequester(retry)) { c.retry() }
                    PillButton("Back", null) { onBack() }
                }
            }
        }

        panel?.let { p -> SidePanel(c, p, Modifier.align(Alignment.CenterEnd)) { panel = null } }
    }
}

@Composable
private fun Controls(
    c: PlayerController, tv: Boolean, playFocus: FocusRequester, zoom: Boolean,
    onZoom: () -> Unit, onPanel: (String) -> Unit, onBack: () -> Unit, onPoke: () -> Unit,
) {
    val item = c.item
    Box(Modifier.fillMaxSize()) {
        // Top: title
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!tv) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Relli.cream) }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(item?.seriesName ?: item?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, color = Relli.cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    c.live -> item?.currentProgram?.name
                    item?.isEpisode == true -> listOfNotNull(item.episodeCode, item.name).joinToString(" · ")
                    else -> null
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Relli.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (c.live) Text("LIVE", style = MaterialTheme.typography.labelLarge, color = Relli.cream,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFB3261E)).padding(horizontal = 10.dp, vertical = 4.dp))
        }

        // Middle: jump back, play/pause, jump forward
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!c.live) RoundButton(Icons.Rounded.Replay10, "Back 10 seconds", 56) { c.seekBy(-10_000); onPoke() }
            RoundButton(if (c.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (c.playing) "Pause" else "Play", 76, Modifier.focusRequester(playFocus)) { c.toggle(); onPoke() }
            if (!c.live) RoundButton(Icons.Rounded.Forward30, "Forward 30 seconds", 56) { c.seekBy(30_000); onPoke() }
        }

        // Bottom: seek bar and options
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            if (!c.live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtClock(c.position), style = MaterialTheme.typography.labelMedium, color = Relli.cream)
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    RemoteSlider(
                        value = dragging ?: c.position.toFloat(),
                        onValueChange = { dragging = it; onPoke() },
                        onValueChangeFinished = { dragging?.let { c.seekTo(it.toLong()) }; dragging = null },
                        valueRange = 0f..c.duration.coerceAtLeast(1L).toFloat(),
                        steps = 0,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(thumbColor = Relli.gold, activeTrackColor = Relli.gold, inactiveTrackColor = Relli.cream.copy(alpha = 0.25f)),
                    )
                    Text("-" + fmtClock((c.duration - c.position).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = Relli.cream)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OptionButton(Icons.Rounded.ClosedCaption, "Subtitles") { onPanel("subs") }
                if (c.audioTracks.size > 1) OptionButton(Icons.Rounded.Translate, "Audio") { onPanel("audio") }
                OptionButton(Icons.Rounded.HighQuality, "Quality") { onPanel("quality") }
                if (!c.live) OptionButton(Icons.Rounded.Speed, "Speed") { onPanel("speed") }
                if (!tv) OptionButton(Icons.Rounded.AspectRatio, if (zoom) "Fit" else "Fill", onClick = onZoom)
                Spacer(Modifier.weight(1f))
                if (c.nextEpisode != null) OptionButton(Icons.Rounded.SkipNext, "Next episode") { c.playNext() }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Relli.cream, modifier = Modifier.size((size * 0.55f).dp)) }
}

@Composable
private fun OptionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(42.dp).clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Relli.cream, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Relli.cream)
    }
}

/** The list that slides in from the right for subtitles, audio, quality and speed. */
@Composable
private fun SidePanel(c: PlayerController, kind: String, modifier: Modifier, onClose: () -> Unit) {
    data class Choice(val label: String, val detail: String?, val selected: Boolean, val pick: () -> Unit)
    val (title, choices) = when (kind) {
        "subs" -> "Subtitles" to (listOf(Choice("Off", null, (c.subtitleIndex ?: -1) < 0) { c.chooseSubtitle(-1) }) +
            c.subtitles.map { s ->
                Choice(s.label, listOfNotNull(if (s.isForced) "Forced" else null, if (s.deliveryMethod != "External") "Burned in by the server" else null).joinToString(" · ").ifBlank { null },
                    c.subtitleIndex == s.index) { c.chooseSubtitle(s.index) }
            })
        "audio" -> "Audio" to c.audioTracks.map { a -> Choice(a.label, null, c.audioIndex == a.index) { c.chooseAudio(a.index) } }
        "quality" -> "Quality" to QUALITIES.map { (v, l) -> Choice(l, null, c.qualityOverride == v) { c.chooseQuality(v) } }
        else -> "Speed" to SPEEDS.map { s -> Choice(if (s == 1f) "Normal" else "${s}x", null, c.speed == s) { c.chooseSpeed(s) } }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(kind) { delay(50); runCatching { first.requestFocus() } }
    Column(
        modifier.fillMaxHeight().width(340.dp).background(Relli.bgDeep.copy(alpha = 0.96f)).padding(vertical = 20.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Relli.cream, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        if (kind == "quality") Text("Now: " + when (c.method) { "Transcode" -> "converted by the server"; "DirectStream" -> "original video, repackaged"; else -> "original file" },
            style = MaterialTheme.typography.bodySmall, color = Relli.muted, modifier = Modifier.padding(horizontal = 22.dp))
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(choices.size) { idx ->
                val ch = choices[idx]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(12.dp))
                        .then(if (idx == 0) Modifier.focusRequester(first) else Modifier)
                        .clickable { ch.pick(); onClose() }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ch.label, style = MaterialTheme.typography.titleSmall, color = if (ch.selected) Relli.gold else Relli.cream)
                        if (ch.detail != null) Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
                    }
                    if (ch.selected) Icon(Icons.Rounded.Check, null, tint = Relli.gold)
                }
            }
        }
    }
}

@Composable
private fun UpNext(c: PlayerController, modifier: Modifier) {
    val next = c.nextEpisode ?: return
    val play = remember { FocusRequester() }
    LaunchedEffect(next.id) { runCatching { play.requestFocus() } }
    Row(
        modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(18.dp)).background(Relli.bgDeep.copy(alpha = 0.94f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Img(ReelsApp.api.landscape(next, 400), Modifier.width(150.dp).aspectRatio(16f / 9f), RoundedCornerShape(10.dp))
        Column(Modifier.padding(horizontal = 14.dp).weight(1f, fill = false)) {
            val n = c.upNextCountdown ?: 0
            Text(if (n > 0) "Up next in $n" else "Up next", style = MaterialTheme.typography.labelLarge, color = Relli.gold)
            Text(listOfNotNull(next.episodeCode, next.name).joinToString(" · "), style = MaterialTheme.typography.titleSmall, color = Relli.cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Play now", Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(play)) { c.playNext() }
                PillButton("Cancel", null) { c.cancelUpNext() }
            }
        }
    }
}
