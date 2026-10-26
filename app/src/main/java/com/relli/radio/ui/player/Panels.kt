package com.relli.radio.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.relli.radio.RelliApp
import com.relli.radio.data.Lyrics
import com.relli.radio.data.Song
import com.relli.radio.playback.PlayerHub
import com.relli.radio.playback.isSmart
import com.relli.radio.playback.toSong
import com.relli.radio.ui.Playback
import com.relli.radio.ui.components.Cover
import com.relli.radio.ui.theme.Manrope
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------- Waveform seek bar ----------

/** Bar shapes for a song. Navidrome doesn't provide waveforms, so these are drawn from the song's id (same song, same shape). */
fun peaksFor(id: String, count: Int = 64): FloatArray {
    var seed = id.hashCode().toLong()
    val out = FloatArray(count)
    var prev = 0.5f
    for (i in 0 until count) {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        val r = ((seed ushr 33) and 0xFFFF).toFloat() / 0xFFFF
        val env = 0.55f + 0.45f * sin(PI * i / count).toFloat()
        prev = (prev * 0.45f + r * 0.55f)
        out[i] = (0.18f + 0.82f * prev * env).coerceIn(0.12f, 1f)
    }
    return out
}

@Composable
fun WaveSeek(id: String, position: Long, duration: Long, live: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val peaks = remember(id) { peaksFor(id) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val pct = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Canvas(
        modifier.fillMaxWidth().height(58.dp)
            .pointerInput(duration, live) {
                if (live || duration <= 0) return@pointerInput
                detectTapGestures { o -> onSeek((o.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration, live) {
                if (live || duration <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { onSeek((it * duration).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        val n = peaks.size
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val base = size.height * 0.68f
        for (i in 0 until n) {
            val v = if (live) 0.25f else peaks[i]
            val h = (v * base).coerceAtLeast(3f)
            val x = i * (bw + gap)
            val lit = (i + 0.5f) / n <= pct
            val c = if (lit) Relli.cream else Relli.cream.copy(alpha = 0.26f)
            drawRoundRect(c, Offset(x, base - h), Size(bw, h), CornerRadius(bw / 2))
            drawRoundRect(c.copy(alpha = c.alpha * 0.22f), Offset(x, base + 2.dp.toPx()), Size(bw, h * 0.45f), CornerRadius(bw / 2))
        }
    }
}

// ---------- Lyrics ----------

@Composable
fun LyricsPanel(lyrics: Lyrics, modifier: Modifier = Modifier) {
    var pos by remember { mutableStateOf(Playback.position) }
    LaunchedEffect(lyrics) { while (true) { pos = Playback.position; delay(200) } }
    val active = if (lyrics.synced) lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= pos + 250 } else -1
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem(active, -280) }
    val size = when (RelliApp.prefs.lyricsSize.value) { "s" -> 22; "l" -> 34; else -> 28 }
    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 140.dp)) {
        itemsIndexed(lyrics.lines) { i, line ->
            val color by animateColorAsState(
                when {
                    !lyrics.synced -> Relli.cream
                    i == active -> Color.White
                    i < active -> Relli.cream.copy(alpha = 0.42f)
                    else -> Relli.cream.copy(alpha = 0.3f)
                }, label = "lyric",
            )
            Text(
                line.text.ifBlank { "♪" },
                color = color,
                fontFamily = Manrope,
                fontWeight = if (lyrics.synced) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = (if (lyrics.synced) size else (size * 0.66f).toInt()).sp,
                lineHeight = ((if (lyrics.synced) size else (size * 0.66f).toInt()) * 1.25f).sp,
                modifier = Modifier.fillMaxWidth()
                    .then(if (line.startMs != null) Modifier.clickable { Playback.seekTo(line.startMs) } else Modifier)
                    .padding(vertical = if (lyrics.synced) 7.dp else 3.dp),
            )
        }
    }
}

// ---------- Visualizer ----------

@Composable
fun VisualizerPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val session by PlayerHub.audioSessionId.collectAsState()
    var fft by remember { mutableStateOf(FloatArray(48)) }
    var wave by remember { mutableStateOf(FloatArray(128)) }
    var style by remember { mutableStateOf(RelliApp.prefs.vizStyle.value) }
    val tone by ArtTone.tone.collectAsState()

    if (granted && session > 0) {
        DisposableEffect(session) {
            val viz = runCatching { Visualizer(session) }.getOrNull()
            val smooth = FloatArray(48)
            viz?.runCatching {
                setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                        data ?: return
                        val out = FloatArray(128) { i -> ((data[i * data.size / 128].toInt() and 0xFF) - 128) / 128f }
                        wave = out
                    }
                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                        data ?: return
                        val bins = data.size / 2
                        for (b in 0 until 48) {
                            // Spread bars on a log scale so bass and treble both get room.
                            val lo = (Math.pow(bins.toDouble(), b / 48.0)).toInt().coerceIn(1, bins - 1)
                            val hi = (Math.pow(bins.toDouble(), (b + 1) / 48.0)).toInt().coerceIn(lo + 1, bins)
                            var m = 0f
                            for (k in lo until hi) {
                                val re = data[2 * k].toFloat(); val im = data[2 * k + 1].toFloat()
                                m = maxOf(m, kotlin.math.sqrt(re * re + im * im))
                            }
                            val db = (20 * kotlin.math.log10(m + 1f) / 42f).coerceIn(0f, 1f)
                            smooth[b] = if (db > smooth[b]) db else smooth[b] * 0.82f + db * 0.18f
                        }
                        fft = smooth.copyOf()
                    }
                }, Visualizer.getMaxCaptureRate(), true, true)
                setEnabled(true)
            }
            onDispose { runCatching { viz?.setEnabled(false); viz?.release() } }
        }
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!granted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
                    Text("The visualizer listens to RelliRadio's own audio. Android calls that microphone access, but nothing is recorded.",
                        color = Relli.cream.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.clip(CircleShape).background(Relli.goldMetal).clickable { ask.launch(Manifest.permission.RECORD_AUDIO) }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text("Allow", color = Relli.onGold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                val c1 = Relli.cream
                val c2 = tone.button
                Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    val brush = Brush.verticalGradient(listOf(c1, c2), startY = 0f, endY = size.height)
                    when (style) {
                        "wave" -> {
                            val path = Path()
                            wave.forEachIndexed { i, v ->
                                val x = i / (wave.size - 1f) * size.width
                                val y = size.height / 2 + v * size.height * 0.38f
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, brush, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        }
                        "ring" -> {
                            val cx = size.width / 2; val cy = size.height / 2
                            val r = min(size.width, size.height) * 0.24f
                            fft.forEachIndexed { i, v ->
                                for (side in listOf(1, -1)) {
                                    val a = (-PI / 2 + side * (i / fft.size.toDouble()) * PI).toFloat()
                                    val len = 6.dp.toPx() + v * r
                                    drawLine(brush, Offset(cx + cos(a) * r, cy + sin(a) * r), Offset(cx + cos(a) * (r + len), cy + sin(a) * (r + len)), 4.dp.toPx(), StrokeCap.Round)
                                }
                            }
                        }
                        else -> {
                            val n = fft.size
                            val gap = 4.dp.toPx()
                            val bw = (size.width - gap * (n - 1)) / n
                            val base = size.height * 0.62f
                            fft.forEachIndexed { i, v ->
                                val h = (v * base * 0.95f).coerceAtLeast(3.dp.toPx())
                                val x = i * (bw + gap)
                                drawRoundRect(brush, Offset(x, base - h), Size(bw, h), CornerRadius(bw / 2))
                                drawRoundRect(c1.copy(alpha = 0.16f), Offset(x, base + 3.dp.toPx()), Size(bw, h * 0.4f), CornerRadius(bw / 2))
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("bars" to "Bars", "wave" to "Wave", "ring" to "Ring").forEach { (id, label) ->
                val on = style == id
                Box(Modifier.clip(CircleShape).background(Relli.cream.copy(alpha = if (on) 0.2f else 0.08f))
                    .clickable { style = id; RelliApp.prefs.vizStyle.value = id }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Relli.cream.copy(alpha = if (on) 1f else 0.7f))
                }
            }
        }
    }
}

// ---------- Queue ----------

@Composable
fun QueuePanel(modifier: Modifier = Modifier, onSave: () -> Unit, onSmart: () -> Unit) {
    val queue by Playback.queue.collectAsState()
    val index by Playback.index.collectAsState()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0))
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Smart queue", style = MaterialTheme.typography.labelLarge, color = Relli.cream.copy(alpha = 0.75f), modifier = Modifier.clickable(onClick = onSmart).padding(8.dp))
            Text("Save", style = MaterialTheme.typography.labelLarge, color = Relli.cream.copy(alpha = 0.75f), modifier = Modifier.clickable(onClick = onSave).padding(8.dp))
            Text("Clear", style = MaterialTheme.typography.labelLarge, color = Relli.cream.copy(alpha = 0.75f), modifier = Modifier.clickable { Playback.clearUpcoming() }.padding(8.dp))
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f)) {
            itemsIndexed(queue, key = { i, item -> "$i-${item.mediaId}" }) { i, item ->
                QueueRow(item, i == index, onClick = { if (i != index) Playback.jump(i) }, onRemove = { Playback.remove(i) },
                    onLongPress = { if (i > index + 1) Playback.move(i, index + 1) })
            }
        }
    }
}

@Composable
private fun QueueRow(item: MediaItem, now: Boolean, onClick: () -> Unit, onRemove: () -> Unit, onLongPress: () -> Unit) {
    val song: Song = remember(item) { item.toSong() }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (now) Relli.cream.copy(alpha = 0.1f) else Color.Transparent)
            .pointerInput(item) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.coverArt ?: song.albumId, Modifier.size(46.dp), 120, RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isSmart) Icon(Icons.Rounded.AutoAwesome, "Added by Smart queue", tint = Relli.gold, modifier = Modifier.size(13.dp).padding(end = 3.dp))
                Text(song.artist ?: "", style = MaterialTheme.typography.bodySmall, color = Relli.cream.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (now) Text("Playing", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 10.dp))
        else IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove from queue", tint = Relli.cream.copy(alpha = 0.6f)) }
    }
}
