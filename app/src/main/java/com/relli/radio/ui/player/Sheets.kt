@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.relli.radio.ui.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.relli.radio.RelliApp
import com.relli.radio.data.Playlist
import com.relli.radio.data.Song
import com.relli.radio.playback.Mixes
import com.relli.radio.playback.PlayerHub
import com.relli.radio.playback.qualityLabel
import com.relli.radio.ui.Nav
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Load
import com.relli.radio.ui.components.Loaded
import com.relli.radio.ui.components.Stars
import com.relli.radio.ui.components.fmtTime
import com.relli.radio.ui.components.load
import com.relli.radio.ui.components.toast
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val prefs get() = RelliApp.prefs
private val api get() = RelliApp.api

// ---------- Building blocks ----------

@Composable
fun RelliSheet(title: String, icon: ImageVector, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Relli.surface,
        contentColor = Relli.cream,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Relli.gold)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Full-width rounded row, like Symfonium's "More actions" items. */
@Composable
fun ActionRow(icon: ImageVector, label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(Relli.raise).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Relli.gold, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .then(if (selected) Modifier.background(Relli.goldMetal) else Modifier.background(Relli.raise))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Relli.onGold else Relli.muted) }
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> Chip(label, v == value) { onPick(v) } }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Relli.raise).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = Relli.cream, checkedTrackColor = Color(0xFF8A6A34),
            uncheckedThumbColor = Relli.muted, uncheckedTrackColor = Relli.line, uncheckedBorderColor = Relli.line,
        ))
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.titleSmall, color = Relli.gold)
        }
        Slider(value, onChange, valueRange = range, steps = steps, colors = sliderColors())
    }
}

@Composable
fun sliderColors() = SliderDefaults.colors(
    thumbColor = Relli.cream, activeTrackColor = Relli.gold, inactiveTrackColor = Relli.line,
    activeTickColor = Relli.cream, inactiveTickColor = Relli.faint,
)

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Relli.muted)

private fun Modifier.vertical() = this
    .graphicsLayer { rotationZ = 270f; transformOrigin = TransformOrigin(0f, 0f) }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(minWidth = constraints.minHeight, maxWidth = constraints.maxHeight, minHeight = constraints.minWidth, maxHeight = constraints.maxWidth)
        )
        layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
    }

// ---------- More actions ----------

@Composable
fun MoreActionsSheet(song: Song, nowPlaying: Boolean, onDismiss: () -> Unit) {
    var sub by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val stars by Stars.overrides.collectAsState()
    val starred = stars[song.id] ?: (song.starred != null)
    when (sub) {
        "eq" -> return EqualizerSheet(onDismiss)
        "speed" -> return SpeedSheet(onDismiss)
        "sleep" -> return SleepSheet(onDismiss)
        "smart" -> return SmartSheet(onDismiss)
        "rate" -> return RatingSheet(song, onDismiss)
        "playlist" -> return PlaylistPickerSheet(listOf(song), onDismiss)
        "details" -> return DetailsSheet(song, onDismiss)
    }
    val done = { action: () -> Unit -> { onDismiss(); action() } }
    fun mix(build: suspend () -> Mixes.Mix, radio: Boolean = false) = done {
        scope.launch {
            try {
                val m = build()
                Playback.play(m.songs, 0, m.name)
                if (radio) { prefs.smartQueue.value = "similar"; PlayerHub.smartChanged(); toast("Radio mix on. Smart queue will keep it going.") }
            } catch (e: Exception) { toast(e.message ?: "Couldn't start the mix") }
        }
    }
    fun open(url: String) = done { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val q = Uri.encode("${song.artist ?: ""} ${song.title}")

    RelliSheet(song.title, Icons.AutoMirrored.Rounded.QueueMusic, onDismiss) {
        if (nowPlaying) {
            ActionRow(Icons.Rounded.Equalizer, "Equalizer and DSP", if (prefs.eqEnabled.value) "On" else "Off") { sub = "eq" }
            ActionRow(Icons.Rounded.Speed, "Speed: ${"%.2f".format(prefs.speed.value).trimEnd('0').trimEnd('.')}x") { sub = "speed" }
            ActionRow(Icons.Rounded.Bedtime, "Sleep timer", if (PlayerHub.sleep.value.active) "On" else null) { sub = "sleep" }
            ActionRow(Icons.Rounded.AutoAwesome, "Smart flow and Smart queue") { sub = "smart" }
        }
        ActionRow(Icons.Rounded.Shuffle, "Instant mix", onClick = mix({ Mixes.instant(song) }))
        ActionRow(Icons.Rounded.Radio, "Radio mix", onClick = mix({ Mixes.instant(song) }, radio = true))
        if (!nowPlaying) {
            ActionRow(Icons.Rounded.SkipNext, "Play next", onClick = done { Playback.playNext(listOf(song)); toast("Plays next") })
            ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", onClick = done { Playback.addToQueue(listOf(song)); toast("Added to queue") })
        }
        ActionRow(if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (starred) "Remove from favorites" else "Add to favorites",
            onClick = done { scope.launch { Stars.set(song.id, !starred) } })
        ActionRow(Icons.Rounded.Star, "Rate", song.userRating?.let { "★".repeat(it) }) { sub = "rate" }
        ActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { sub = "playlist" }
        song.albumId?.let { id -> ActionRow(Icons.Rounded.Album, "Go to album", onClick = done { Nav.album(id) }) }
        song.artistList.forEach { a -> ActionRow(Icons.Rounded.Person, "Go to ${a.name}", onClick = done { Nav.artist(a.id) }) }
        song.genre?.let { g -> ActionRow(Icons.AutoMirrored.Rounded.Label, "Go to $g", onClick = done { Nav.genre(g) }) }
        if (nowPlaying) ActionRow(Icons.Rounded.FormatQuote, "Lyrics", onClick = done { prefs.visual.value = "lyrics" })
        ActionRow(Icons.Rounded.Info, "Details") { sub = "details" }
        ActionRow(Icons.Rounded.Public, "Web search", onClick = open("https://www.google.com/search?q=$q"))
        ActionRow(Icons.Rounded.FormatQuote, "Genius search", onClick = open("https://genius.com/search?q=$q"))
        ActionRow(Icons.Rounded.Share, "Share", onClick = done {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${song.title} by ${song.artist ?: "Unknown artist"}")
            context.startActivity(Intent.createChooser(send, "Share song"))
        })
    }
}

// ---------- Equalizer and DSP ----------

@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val info by PlayerHub.eqInfo.collectAsState()
    var enabled by remember { mutableStateOf(prefs.eqEnabled.value) }
    var preset by remember { mutableIntStateOf(prefs.eqPreset.value) }
    val bands = remember { mutableStateListOf<Int>().apply { addAll(PlayerHub.bandLevels()) } }
    var bass by remember { mutableFloatStateOf(prefs.bassBoost.value.toFloat()) }
    var virt by remember { mutableFloatStateOf(prefs.virtualizer.value.toFloat()) }
    var loud by remember { mutableFloatStateOf(prefs.loudness.value.toFloat()) }
    var rg by remember { mutableStateOf(prefs.replayGain.value) }
    var rgPre by remember { mutableFloatStateOf(prefs.rgPreamp.value) }

    fun saveBands() {
        prefs.eqPreset.value = -1; preset = -1
        prefs.eqBands.value = bands.map { it.toString() }
        PlayerHub.applyEffects()
    }

    RelliSheet("Equalizer and DSP", Icons.Rounded.Equalizer, onDismiss) {
        ToggleRow("Equalizer", enabled, "Uses your phone's built-in audio effects.") {
            enabled = it; prefs.eqEnabled.value = it; PlayerHub.applyEffects()
        }
        val eq = info
        if (eq == null) {
            Hint("This phone didn't offer an equalizer for playback. Start a song, then open this again.")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Custom", preset < 0) { saveBands() }
                eq.presets.forEachIndexed { i, name ->
                    Chip(name, preset == i) {
                        preset = i; prefs.eqPreset.value = i; PlayerHub.applyEffects()
                        bands.clear(); bands.addAll(PlayerHub.bandLevels())
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                eq.bandFreqsHz.forEachIndexed { i, hz ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val v = bands.getOrElse(i) { 0 }
                        Text("${if (v > 0) "+" else ""}${v / 100}", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.width(40.dp).height(170.dp), contentAlignment = Alignment.Center) {
                            Slider(
                                value = v.toFloat(),
                                onValueChange = { nv -> if (i < bands.size) bands[i] = nv.roundToInt() else bands.add(nv.roundToInt()) },
                                onValueChangeFinished = { saveBands() },
                                valueRange = eq.minMb.toFloat()..eq.maxMb.toFloat(),
                                colors = sliderColors(),
                                modifier = Modifier.width(170.dp).height(40.dp).vertical(),
                            )
                        }
                        Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", style = MaterialTheme.typography.labelSmall, color = Relli.muted)
                    }
                }
            }
        }
        Heading("DSP")
        LabeledSlider("Bass boost", bass, 0f..1000f, format = { "${(it / 10).roundToInt()}%" }) { bass = it; prefs.bassBoost.value = it.roundToInt(); PlayerHub.applyEffects() }
        LabeledSlider("Virtualizer (headphones)", virt, 0f..1000f, format = { "${(it / 10).roundToInt()}%" }) { virt = it; prefs.virtualizer.value = it.roundToInt(); PlayerHub.applyEffects() }
        LabeledSlider("Volume boost", loud, 0f..1000f, format = { "+${"%.1f".format(it / 100)} dB" }) { loud = it; prefs.loudness.value = it.roundToInt(); PlayerHub.applyEffects() }
        Heading("ReplayGain")
        Hint("Evens out volume between songs using the loudness tags in your files.")
        ChipRow(listOf("off" to "Off", "track" to "Track", "album" to "Album", "auto" to "Auto"), rg) { rg = it; prefs.replayGain.value = it; PlayerHub.applyLevel() }
        LabeledSlider("Preamp", rgPre, -15f..0f, steps = 14, format = { "${it.roundToInt()} dB" }) { rgPre = it; prefs.rgPreamp.value = it; PlayerHub.applyLevel() }
    }
}

// ---------- Speed and pitch ----------

@Composable
fun SpeedSheet(onDismiss: () -> Unit) {
    var speed by remember { mutableFloatStateOf(prefs.speed.value) }
    var pitch by remember { mutableFloatStateOf(prefs.pitch.value.toFloat()) }
    var skip by remember { mutableStateOf(prefs.skipSilence.value) }
    val fmt = { v: Float -> "%.2f".format(v).trimEnd('0').trimEnd('.') + "x" }
    RelliSheet("Speed and pitch", Icons.Rounded.Speed, onDismiss) {
        LabeledSlider("Speed", speed, 0.5f..2f, steps = 29, format = fmt) { speed = it; PlayerHub.setSpeed(it) }
        ChipRow(listOf("0.75", "1", "1.25", "1.5", "2").map { it to "${it}x" }, fmt(speed).removeSuffix("x")) {
            speed = it.toFloat(); PlayerHub.setSpeed(speed)
        }
        LabeledSlider("Pitch", pitch, -12f..12f, steps = 23, format = { val n = it.roundToInt(); if (n == 0) "Normal" else "${if (n > 0) "+" else ""}$n semitones" }) {
            pitch = it; PlayerHub.setPitch(it.roundToInt())
        }
        ToggleRow("Skip silence", skip, "Trims quiet gaps, handy for audiobooks.") { skip = it; PlayerHub.setSkipSilence(it) }
        ActionRow(Icons.Rounded.Speed, "Reset to normal") { speed = 1f; pitch = 0f; PlayerHub.setSpeed(1f); PlayerHub.setPitch(0) }
    }
}

// ---------- Sleep timer ----------

@Composable
fun SleepSheet(onDismiss: () -> Unit) {
    val state by PlayerHub.sleep.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var fade by remember { mutableStateOf(prefs.sleepFade.value) }
    LaunchedEffect(state) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    RelliSheet("Sleep timer", Icons.Rounded.Bedtime, onDismiss) {
        when (state.mode) {
            PlayerHub.SleepMode.TIME -> Text(fmtTime((state.endsAt - now).coerceAtLeast(0) / 1000), style = MaterialTheme.typography.displaySmall)
            PlayerHub.SleepMode.TRACK -> Text("End of this song", style = MaterialTheme.typography.headlineSmall)
            PlayerHub.SleepMode.QUEUE -> Text("End of queue", style = MaterialTheme.typography.headlineSmall)
            PlayerHub.SleepMode.OFF -> Hint("Pick when playback should stop.")
        }
        if (state.active) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.mode == PlayerHub.SleepMode.TIME) Chip("+5 min", false) { PlayerHub.sleepExtend(5) }
                Chip("Turn off", false) { PlayerHub.cancelSleep() }
            }
        }
        ChipRow(listOf(5, 10, 15, 30, 45, 60, 90).map { "$it" to "$it min" }, "") { PlayerHub.sleepIn(it.toInt()) }
        ActionRow(Icons.Rounded.SkipNext, "End of this song") { PlayerHub.sleepEndOfTrack() }
        ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, "End of queue") { PlayerHub.sleepEndOfQueue() }
        ToggleRow("Fade out", fade, "Lowers the volume over the last 30 seconds.") { fade = it; prefs.sleepFade.value = it }
    }
}

// ---------- Smart queue and Smart flow ----------

val QUEUE_MODES = listOf(
    "off" to ("Off" to "Stop when the queue ends."),
    "random" to ("Random" to "Any songs from your library."),
    "genre" to ("Same genre" to "More from the current song's genre."),
    "artist" to ("Same artist" to "More by the current artist."),
    "similar" to ("Similar songs" to "Songs like the current one."),
)
val FLOW_MODES = listOf(
    "off" to ("Off" to "Play the queue as it is."),
    "double" to ("Double shot" to "After each song you picked, one more by the same artist."),
)

@Composable
private fun ModeList(modes: List<Pair<String, Pair<String, String>>>, value: String, onPick: (String) -> Unit) {
    modes.forEach { (id, text) ->
        val on = id == value
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Relli.raise).clickable { onPick(id) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(if (on) Relli.gold else Relli.line))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text.first, style = MaterialTheme.typography.titleSmall)
                Text(text.second, style = MaterialTheme.typography.bodySmall, color = Relli.muted)
            }
        }
    }
}

@Composable
fun SmartSheet(onDismiss: () -> Unit) {
    var q by remember { mutableStateOf(prefs.smartQueue.value) }
    var f by remember { mutableStateOf(prefs.smartFlow.value) }
    RelliSheet("Smart queue and Smart flow", Icons.Rounded.AutoAwesome, onDismiss) {
        Heading("Smart queue")
        Hint("Keeps the music going by adding songs when the queue is about to end.")
        ModeList(QUEUE_MODES, q) { q = it; prefs.smartQueue.value = it; PlayerHub.smartChanged() }
        Heading("Smart flow")
        Hint("Reshapes the queue as it plays.")
        ModeList(FLOW_MODES, f) { f = it; prefs.smartFlow.value = it }
    }
}

// ---------- Details, rating, playlists ----------

@Composable
fun DetailsSheet(song: Song, onDismiss: () -> Unit) {
    val full by load(song.id) { api.song(song.id) }
    RelliSheet("Details", Icons.Rounded.Info, onDismiss) {
        Loaded(full) { s ->
            val rows = listOfNotNull(
                "Title" to s.title, "Artist" to s.artist, "Album" to s.album, "Track" to s.track?.toString(),
                "Year" to s.year?.toString(), "Genre" to s.genre, "Length" to fmtTime(s.duration.toLong()),
                "Format" to qualityLabel(s, 0), "Bit rate" to s.bitRate?.let { "$it kbps" },
                "Sample rate" to s.samplingRate?.let { "${it / 1000.0} kHz" }, "Bit depth" to s.bitDepth?.let { "$it-bit" },
                "Size" to s.size?.let { "%.1f MB".format(it / 1_048_576.0) }, "Plays" to s.playCount?.toString(),
                "Track gain" to s.replayGain?.trackGain?.let { "$it dB" }, "Album gain" to s.replayGain?.albumGain?.let { "$it dB" },
                "File" to s.path,
            ).filter { it.second != null }
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(k, color = Relli.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                    Text(v!!, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
fun RatingSheet(song: Song, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableIntStateOf(song.userRating ?: 0) }
    fun set(n: Int) {
        rating = n
        scope.launch {
            try { api.setRating(song.id, n); toast(if (n == 0) "Rating cleared" else "Rated $n star${if (n > 1) "s" else ""}"); onDismiss() }
            catch (e: Exception) { toast(e.message ?: "Couldn't save the rating") }
        }
    }
    RelliSheet("Rate “${song.title}”", Icons.Rounded.Star, onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { n ->
                Icon(if (n <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$n stars", tint = Relli.gold,
                    modifier = Modifier.size(52.dp).clip(CircleShape).clickable { set(n) }.padding(6.dp))
            }
        }
        Chip("Clear rating", false) { set(0) }
    }
}

@Composable
fun PlaylistPickerSheet(songs: List<Song>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lists by load { api.playlists().filter { !it.readonly } }
    var name by remember { mutableStateOf("") }
    fun create() {
        if (name.isBlank()) return
        scope.launch {
            try { api.createPlaylist(name.trim(), songs.map { it.id }); toast("Created “${name.trim()}”"); onDismiss() }
            catch (e: Exception) { toast(e.message ?: "Couldn't create the playlist") }
        }
    }
    fun add(p: Playlist) = scope.launch {
        try { api.addToPlaylist(p.id, songs.map { it.id }); toast("Added to ${p.name}"); onDismiss() }
        catch (e: Exception) { toast(e.message ?: "Couldn't add to the playlist") }
    }
    RelliSheet("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                name, { name = it }, placeholder = { Text("New playlist name") }, singleLine = true,
                modifier = Modifier.weight(1f), shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { create() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Relli.gold, unfocusedBorderColor = Relli.line, cursorColor = Relli.gold),
            )
            Spacer(Modifier.width(8.dp))
            Chip("Create", name.isNotBlank()) { create() }
        }
        Loaded(lists) { all -> all.forEach { p -> ActionRow(Icons.AutoMirrored.Rounded.QueueMusic, p.name, "${p.songCount}") { add(p) } } }
    }
}
