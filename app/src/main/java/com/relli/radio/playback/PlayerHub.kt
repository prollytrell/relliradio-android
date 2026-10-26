package com.relli.radio.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.relli.radio.RelliApp
import com.relli.radio.data.SavedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * The extras that sit next to the player inside the playback service:
 * ReplayGain, speed and pitch, equalizer, sleep timer, Smart queue / Smart flow,
 * play counts (scrobbles), and saving the queue. Screens call these directly.
 */
object PlayerHub {
    private val prefs get() = RelliApp.prefs
    private val api get() = RelliApp.api

    private var player: ExoPlayer? = null
    private var scope: CoroutineScope? = null

    private val _sessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
    /** Audio session id, used by the equalizer and the visualizer. */
    val audioSessionId: StateFlow<Int> get() = _sessionId

    // ---------- Wiring ----------

    fun attach(p: ExoPlayer, s: CoroutineScope) {
        player = p
        scope = s
        p.addListener(listener)
        applySpeed()
        p.skipSilenceEnabled = prefs.skipSilence.value
        setSession(p.audioSessionId)
        restoreLocalQueue(p)
        s.launch { tick() }
    }

    fun detach() {
        saveQueue(server = true)
        player?.removeListener(listener)
        releaseEffects()
        cancelSleep()
        player = null
        scope = null
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            applyLevel()
            scrobbled = false
            item?.takeIf { !it.isRadio }?.let { scope?.launch { api.scrobble(it.mediaId, false) } }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) applyFlow(item)
            topUp()
            saveQueue(server = true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                // Playing here makes this the active device (see Handoff).
                activeSince = System.currentTimeMillis()
                Handoff.activeHere()
                scope?.launch { Handoff.baseline() }
            } else saveQueue(server = true)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = setSession(audioSessionId)

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                if (sleep.value.mode == SleepMode.QUEUE) finishSleep(fade = false)
                topUp()
            }
        }
    }

    // ---------- Play counts and periodic work ----------

    private var scrobbled = false
    private var activeSince = 0L
    private var lastHandoffCheck = 0L
    private var lastServerSave = 0L

    private suspend fun tick() {
        while (scope?.isActive == true) {
            val p = player ?: return
            val item = p.currentMediaItem
            if (item != null && !item.isRadio && p.isPlaying && !scrobbled) {
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                if (dur > 0 && (p.currentPosition > dur / 2 || p.currentPosition > 240_000)) {
                    scrobbled = true
                    api.scrobble(item.mediaId, true)
                }
            }
            sleepTick()
            // Position to Navidrome every 3 seconds while playing, so another device can pick up almost exactly here.
            if (p.isPlaying && System.currentTimeMillis() - lastServerSave >= SavedQueue.SAVE_EVERY_MS - 200) saveQueue(server = true, local = false)
            // Another device took over? Pause here so the music moves instead of doubling up.
            // (A short grace period after starting lets both devices' saves land first.)
            val now = System.currentTimeMillis()
            if (p.isPlaying && now - activeSince > 8_000 && now - lastHandoffCheck >= 2_800) {
                lastHandoffCheck = now
                if (Handoff.check() && p.isPlaying) {
                    Handoff.tookOver()
                    p.pause()
                }
            }
            delay(1000)
        }
    }

    // ---------- Queue saving ----------

    private fun queueSongs(p: Player) = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }.filter { !it.isRadio }

    fun saveQueue(server: Boolean, local: Boolean = true) {
        val p = player ?: return
        val items = queueSongs(p)
        val current = p.currentMediaItem?.takeIf { !it.isRadio }?.mediaId
        if (local) prefs.savedQueue = if (items.isEmpty()) null else SavedQueue(items.map { it.toSong() }, current, p.currentPosition)
        if (server && items.isNotEmpty() && !Handoff.passive) {
            lastServerSave = System.currentTimeMillis()
            prefs.lastSync.value = lastServerSave
            val ids = items.map { it.mediaId }
            val pos = p.currentPosition
            scope?.launch { api.savePlayQueue(ids, current, pos) }
        }
    }

    private fun restoreLocalQueue(p: ExoPlayer) {
        if (p.mediaItemCount > 0) return
        val q = prefs.savedQueue ?: return
        val idx = q.songs.indexOfFirst { it.id == q.currentId }.coerceAtLeast(0)
        p.setMediaItems(q.songs.map { it.toMediaItem() }, idx, q.positionMs)
        p.prepare()
    }

    /** Takes over the queue saved on the server by another device. */
    suspend fun resumeFromServer(): Boolean {
        val q = api.playQueue() ?: return false
        Handoff.pickedUp(q)
        val p = player ?: return false
        val idx = q.songs.indexOfFirst { it.id == q.currentId }.coerceAtLeast(0)
        // Start where the other device is now, not where it last saved.
        p.setMediaItems(q.songs.map { it.toMediaItem() }, idx, q.livePositionMs())
        p.prepare()
        p.play()
        prefs.lastSync.value = System.currentTimeMillis()
        return true
    }

    // ---------- ReplayGain and volume ----------

    private var sleepFactor = 1f

    fun applyLevel() {
        val p = player ?: return
        val song = p.currentMediaItem?.toSong()
        val mode = prefs.replayGain.value
        var gainDb: Double? = null
        if (song != null && mode != "off") {
            val rg = song.replayGain
            val albumMode = mode == "album" || (mode == "auto" && sameAlbumAround(p))
            gainDb = (if (albumMode) rg?.albumGain ?: rg?.trackGain else rg?.trackGain ?: rg?.albumGain)
        }
        // The player can only turn things down, so loud tracks get quieter; quiet ones stay at full volume.
        val rgFactor = gainDb?.let { 10.0.pow((it + prefs.rgPreamp.value) / 20.0).toFloat() }?.coerceIn(0f, 1f) ?: 1f
        p.volume = rgFactor * sleepFactor
    }

    private fun sameAlbumAround(p: Player): Boolean {
        val i = p.currentMediaItemIndex
        val album = p.currentMediaItem?.mediaMetadata?.albumTitle ?: return false
        val prev = if (i > 0) p.getMediaItemAt(i - 1).mediaMetadata.albumTitle else null
        val next = if (i + 1 < p.mediaItemCount) p.getMediaItemAt(i + 1).mediaMetadata.albumTitle else null
        return prev == album || next == album
    }

    // ---------- Speed and pitch ----------

    fun setSpeed(speed: Float) { prefs.speed.value = speed.coerceIn(0.25f, 3f); applySpeed() }
    fun setPitch(semitones: Int) { prefs.pitch.value = semitones.coerceIn(-12, 12); applySpeed() }
    fun setSkipSilence(on: Boolean) { prefs.skipSilence.value = on; player?.skipSilenceEnabled = on }

    private fun applySpeed() {
        val pitch = 2.0.pow(prefs.pitch.value / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(prefs.speed.value, pitch)
    }

    // ---------- Equalizer and effects (Android's built-in audio effects) ----------

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null

    data class EqInfo(val bandFreqsHz: List<Int>, val minMb: Int, val maxMb: Int, val presets: List<String>)
    private val _eqInfo = MutableStateFlow<EqInfo?>(null)
    val eqInfo: StateFlow<EqInfo?> get() = _eqInfo

    private fun setSession(id: Int) {
        _sessionId.value = id
        releaseEffects()
        if (id == C.AUDIO_SESSION_ID_UNSET || id == 0) return
        try {
            eq = Equalizer(0, id).also { e ->
                val range = e.bandLevelRange
                _eqInfo.value = EqInfo(
                    (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 },
                    range[0].toInt(), range[1].toInt(),
                    (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) },
                )
            }
        } catch (_: Exception) { _eqInfo.value = null }
        try { bass = BassBoost(0, id) } catch (_: Exception) {}
        try { virt = Virtualizer(0, id) } catch (_: Exception) {}
        try { loud = LoudnessEnhancer(id) } catch (_: Exception) {}
        applyEffects()
    }

    private fun releaseEffects() {
        listOf(eq, bass, virt, loud).forEach { runCatching { it?.release() } }
        eq = null; bass = null; virt = null; loud = null
    }

    /** Pushes the saved equalizer settings to the effects. */
    fun applyEffects() {
        val on = prefs.eqEnabled.value
        runCatching {
            eq?.let { e ->
                val preset = prefs.eqPreset.value
                if (preset >= 0 && preset < e.numberOfPresets) e.usePreset(preset.toShort())
                else prefs.eqBands.value.forEachIndexed { i, v ->
                    if (i < e.numberOfBands) e.setBandLevel(i.toShort(), v.toIntOrNull()?.toShort() ?: 0.toShort())
                }
                e.setEnabled(on)
            }
        }
        runCatching { bass?.let { if (it.strengthSupported) it.setStrength(prefs.bassBoost.value.toShort()); it.setEnabled(on && prefs.bassBoost.value > 0) } }
        runCatching { virt?.let { if (it.strengthSupported) it.setStrength(prefs.virtualizer.value.toShort()); it.setEnabled(on && prefs.virtualizer.value > 0) } }
        runCatching { loud?.let { it.setTargetGain(prefs.loudness.value); it.setEnabled(on && prefs.loudness.value > 0) } }
    }

    /** Current band levels (from the preset in use, or the custom levels). */
    fun bandLevels(): List<Int> {
        val e = eq ?: return emptyList()
        return runCatching { (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() } }.getOrDefault(emptyList())
    }

    // ---------- Sleep timer ----------

    enum class SleepMode { OFF, TIME, TRACK, QUEUE }
    data class SleepState(val mode: SleepMode = SleepMode.OFF, val endsAt: Long = 0) {
        val active get() = mode != SleepMode.OFF
        fun remainingMs(): Long = if (mode == SleepMode.TIME) (endsAt - System.currentTimeMillis()).coerceAtLeast(0) else 0
    }
    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> get() = _sleep

    fun sleepIn(minutes: Int) {
        player?.pauseAtEndOfMediaItems = false
        _sleep.value = SleepState(SleepMode.TIME, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun sleepExtend(minutes: Int) {
        val s = _sleep.value
        if (s.mode != SleepMode.TIME) sleepIn(minutes) else _sleep.value = s.copy(endsAt = s.endsAt + minutes * 60_000L)
    }

    fun sleepEndOfTrack() {
        player?.pauseAtEndOfMediaItems = true
        _sleep.value = SleepState(SleepMode.TRACK)
    }

    fun sleepEndOfQueue() {
        player?.pauseAtEndOfMediaItems = false
        _sleep.value = SleepState(SleepMode.QUEUE)
    }

    fun cancelSleep() {
        player?.pauseAtEndOfMediaItems = false
        _sleep.value = SleepState()
        sleepFactor = 1f
        applyLevel()
    }

    private var lastIndex = -1
    private fun sleepTick() {
        val p = player ?: return
        val s = _sleep.value
        when (s.mode) {
            SleepMode.TIME -> {
                val left = s.remainingMs()
                if (prefs.sleepFade.value && left < 30_000) { sleepFactor = left / 30_000f; applyLevel() }
                if (left <= 0) finishSleep(fade = true)
            }
            SleepMode.TRACK -> {
                // pauseAtEndOfMediaItems pauses for us; once it has, the timer is done.
                if (lastIndex != -1 && !p.playWhenReady) finishSleep(fade = false)
                lastIndex = p.currentMediaItemIndex
            }
            else -> lastIndex = -1
        }
    }

    private fun finishSleep(fade: Boolean) {
        player?.pause()
        if (fade) player?.let { it.seekTo((it.currentPosition - 30_000).coerceAtLeast(0)) }
        cancelSleep()
        lastIndex = -1
    }

    // ---------- Smart queue and Smart flow ----------

    private var toppingUp = false

    fun topUp() {
        val p = player ?: return
        val mode = prefs.smartQueue.value
        if (mode == "off" || toppingUp || p.repeatMode != Player.REPEAT_MODE_OFF) return
        if (p.currentMediaItem?.isRadio == true) return
        if (p.mediaItemCount - p.currentMediaItemIndex > 3) return
        toppingUp = true
        scope?.launch {
            try {
                val seed = p.currentMediaItem?.toSong()
                val have = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
                val more = Mixes.more(mode, seed, have)
                if (more.isNotEmpty()) {
                    val wasEnded = p.playbackState == Player.STATE_ENDED
                    p.addMediaItems(more.map { it.toMediaItem(smart = true) })
                    if (wasEnded) { p.seekToNextMediaItem(); p.play() }
                }
            } catch (_: Exception) {
            } finally { toppingUp = false }
        }
    }

    /** Smart flow "Double shot": after each song you picked, add one more by the same artist. */
    private fun applyFlow(item: MediaItem?) {
        val p = player ?: return
        if (prefs.smartFlow.value != "double" || item == null || item.isRadio || item.isSmart) return
        val song = item.toSong()
        val artistId = song.artistId ?: return
        scope?.launch {
            runCatching {
                val pick = api.artist(artistId).album.shuffled().take(3)
                    .flatMap { api.album(it.id).song }.filter { it.id != song.id }.randomOrNull() ?: return@launch
                if (p.currentMediaItem?.mediaId == song.id) p.addMediaItem(p.currentMediaItemIndex + 1, pick.toMediaItem(smart = true))
            }
        }
    }

    /** Called when the Smart queue setting changes, so it can fill the queue right away. */
    fun smartChanged() = topUp()
}
