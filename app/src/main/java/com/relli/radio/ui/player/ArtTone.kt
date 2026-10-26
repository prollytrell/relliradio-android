package com.relli.radio.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.relli.radio.RelliApp
import com.relli.radio.ui.theme.Relli
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Colors taken from the current album art, so Now Playing, the play button and
 * the mini player take on each song's look (like Symfonium).
 */
data class Tone(val bg: Color, val deep: Color, val button: Color, val pill: Color)

object ArtTone {
    val default = Tone(Color(0xFF3A2A20), Relli.bgDeep, Relli.gold, Relli.raise)
    private val _tone = MutableStateFlow(default)
    val tone: StateFlow<Tone> get() = _tone
    private var lastId: String? = null

    suspend fun update(coverId: String?) {
        if (coverId == lastId) return
        lastId = coverId
        if (coverId == null) { _tone.value = default; return }
        val url = RelliApp.api.coverUrl(coverId, 128) ?: return
        val app = RelliApp.instance
        val result = app.imageLoader.execute(ImageRequest.Builder(app).data(url).allowHardware(false).size(128).build())
        val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return
        val tone = withContext(Dispatchers.Default) { toneFrom(bmp) }
        if (lastId == coverId) _tone.value = tone
    }

    private fun toneFrom(bmp: Bitmap): Tone {
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        val sw = p.dominantSwatch ?: p.vibrantSwatch ?: p.mutedSwatch ?: return default
        val hsl = sw.hsl.copyOf()
        fun make(sMax: Float, l: Float): Color {
            val c = floatArrayOf(hsl[0], hsl[1].coerceAtMost(sMax), l)
            return Color(ColorUtils.HSLToColor(c))
        }
        return Tone(bg = make(0.30f, 0.21f), deep = make(0.24f, 0.10f), button = make(0.26f, 0.60f), pill = make(0.20f, 0.33f))
    }
}
