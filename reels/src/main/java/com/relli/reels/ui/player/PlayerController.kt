package com.relli.reels.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.data.MediaSource
import com.relli.reels.data.MediaStream
import com.relli.reels.data.Segment
import com.relli.reels.data.TICKS_PER_MS
import com.relli.reels.ui.components.Refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Runs one viewing session: asks Jellyfin how to play the item, feeds the stream to ExoPlayer
 * (Google's media player), tells Jellyfin where you are every few seconds (so any other device can
 * resume from the same spot), and handles subtitles, audio tracks, skipping intros and the next episode.
 */
class PlayerController(context: Context, private val scope: CoroutineScope, private val onFinished: () -> Unit) {
    private val api = ReelsApp.api
    private val prefs = ReelsApp.prefs

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(30_000)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    // ---- What the screen shows ----
    var item by mutableStateOf<Item?>(null); private set
    var live by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null); private set
    var source by mutableStateOf<MediaSource?>(null); private set
    var method by mutableStateOf("DirectPlay"); private set
    var subtitleIndex by mutableStateOf<Int?>(null); private set
    var audioIndex by mutableStateOf<Int?>(null); private set
    var nextEpisode by mutableStateOf<Item?>(null); private set
    var segments by mutableStateOf<List<Segment>>(emptyList()); private set
    var speed by mutableStateOf(1f); private set
    /** Quality picked in the player for this session (Mbps, 0 = original), or null to use Settings. */
    var qualityOverride by mutableStateOf<Int?>(null); private set
    /** Seconds left before the next episode starts, or null when the "Up next" card isn't showing. */
    var upNextCountdown by mutableStateOf<Int?>(null); private set

    private var playSessionId: String? = null
    private var burnedIn = false
    private var triedTranscode = false
    private var started = false
    private var reportJob: Job? = null
    private var countdownJob: Job? = null
    private var skippedSegments = mutableSetOf<Long>()
    private var upNextDismissed = false

    val subtitles: List<MediaStream> get() = source?.streams?.filter { it.type == "Subtitle" }.orEmpty()
    val audioTracks: List<MediaStream> get() = source?.streams?.filter { it.type == "Audio" }.orEmpty()

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                scope.launch { report("Progress") }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exo.duration.takeIf { it > 0 } ?: item?.runtimeMs ?: 0L
                    if (!started) { started = true; scope.launch { report("") } }
                }
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onTracksChanged(tracks: Tracks) = applySubtitle()
            override fun onPlayerError(e: PlaybackException) {
                // The device couldn't play the file as-is: ask the server to convert it instead.
                if (!triedTranscode && method != "Transcode") {
                    triedTranscode = true
                    val at = exo.currentPosition
                    scope.launch { load(at, allowDirect = false) }
                } else {
                    error = "This video couldn't play. (${e.errorCodeName})"
                }
            }
        })
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .apply { if (prefs.audioLang.value.isNotBlank()) setPreferredAudioLanguage(prefs.audioLang.value) }
            .build()
        scope.launch {
            while (isActive) {
                position = exo.currentPosition.coerceAtLeast(0)
                if (exo.duration > 0) duration = exo.duration
                checkSegments()
                delay(500)
            }
        }
    }

    /** Opens [id]. [startMs] = -1 continues from where you (or another device) left off. */
    fun open(id: String, startMs: Long, isLive: Boolean) {
        live = isLive
        com.relli.reels.ui.Continue.dismissed += id
        scope.launch {
            try {
                val i = api.item(id)
                item = i
                val start = if (startMs < 0) (if ((i.userData?.playedPercentage ?: 0.0) < 95) i.positionMs else 0L) else startMs
                load(start)
                if (i.isEpisode && !isLive) launch { nextEpisode = runCatching { api.nextEpisode(i) }.getOrNull() }
                if (!isLive) launch { segments = api.segments(id) }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't start playback."
            }
        }
    }

    private fun bitrate(): Int = qualityOverride?.let { if (it <= 0) 200_000_000 else it * 1_000_000 } ?: ReelsApp.instance.maxBitrate()

    /** (Re)loads the stream. Used at the start and when a change needs the server to convert differently. */
    private suspend fun load(startMs: Long, audio: Int? = audioIndex, burnSubtitle: Int? = null, allowDirect: Boolean = !triedTranscode) {
        try { loadInner(startMs, audio, burnSubtitle, allowDirect) }
        catch (e: Exception) { buffering = false; error = e.message ?: "Couldn't start playback." }
    }

    private suspend fun loadInner(startMs: Long, audio: Int?, burnSubtitle: Int?, allowDirect: Boolean) {
        val i = item ?: return
        error = null
        buffering = true
        val oldLive = source?.liveStreamId
        val info = api.playbackInfo(
            i.id, startMs * TICKS_PER_MS, bitrate(), audioIndex = audio, subtitleIndex = burnSubtitle,
            mediaSourceId = source?.id, live = live, allowDirect = allowDirect,
        )
        val src = info.sources.firstOrNull() ?: run { error = "Jellyfin has no playable file for this."; return }
        if (oldLive != null && oldLive != src.liveStreamId) api.closeLiveStream(oldLive)
        source = src
        playSessionId = info.playSessionId
        burnedIn = burnSubtitle != null
        if (audioIndex == null) audioIndex = src.defaultAudio

        val transcode = src.transcodingUrl != null && (!(src.directPlay || src.directStream) || !allowDirect)
        method = when {
            transcode -> "Transcode"
            src.directPlay -> "DirectPlay"
            else -> "DirectStream"
        }
        val url = if (transcode) api.absolute(src.transcodingUrl!!) else api.directUrl(i.id, src, playSessionId)

        // Text subtitles come as separate WebVTT files that ExoPlayer draws on top of the video.
        val subs = src.streams.filter { it.type == "Subtitle" && it.deliveryMethod == "External" && it.deliveryUrl != null }.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(api.absolute(s.deliveryUrl!!)))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(s.language)
                .setLabel("jf:${s.index}")
                .setId("jf-${s.index}")
                .setSelectionFlags(if (s.isForced) C.SELECTION_FLAG_FORCED else 0)
                .build()
        }
        val media = MediaItem.Builder().setUri(url).setMediaId(i.id)
            .apply { if (transcode || url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(subs)
            .build()

        if (subtitleIndex == null && !burnedIn) subtitleIndex = initialSubtitle(src)
        if (burnedIn) subtitleIndex = burnSubtitle
        exo.setMediaItem(media, if (live) C.TIME_UNSET else startMs)
        exo.prepare()
        exo.playWhenReady = true
        startReporting()
    }

    private fun initialSubtitle(src: MediaSource): Int? {
        val subs = src.streams.filter { it.type == "Subtitle" }
        val lang = prefs.subtitleLang.value
        return when (prefs.subtitles.value) {
            "off" -> -1
            "on" -> (subs.firstOrNull { it.language == lang && !it.isForced } ?: subs.firstOrNull { src.defaultSubtitle == it.index } ?: subs.firstOrNull())?.index ?: -1
            // Auto: Jellyfin's pick (forced subtitles for foreign-language parts, or your server-side preference).
            else -> src.defaultSubtitle?.takeIf { it >= 0 } ?: subs.firstOrNull { it.isForced && (it.language == lang || it.language == null) }?.index ?: -1
        }
    }

    /** Shows the chosen subtitle file, or none. Burned-in subtitles are part of the picture already. */
    private fun applySubtitle() {
        val idx = subtitleIndex
        val b = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (idx == null || idx < 0 || burnedIn) {
            exo.trackSelectionParameters = b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return
        }
        val group = exo.currentTracks.groups.firstOrNull { g ->
            g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { t ->
                val f = g.getTrackFormat(t)
                f.label == "jf:$idx" || f.id?.endsWith("jf-$idx") == true
            }
        } ?: return
        val track = (0 until group.length).first { t -> val f = group.getTrackFormat(t); f.label == "jf:$idx" || f.id?.endsWith("jf-$idx") == true }
        exo.trackSelectionParameters = b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track)).build()
    }

    fun chooseSubtitle(index: Int) {
        val stream = subtitles.firstOrNull { it.index == index }
        val needsBurn = stream != null && stream.deliveryMethod != "External"
        subtitleIndex = index
        when {
            needsBurn -> { val at = exo.currentPosition; scope.launch { load(at, burnSubtitle = index) } }
            burnedIn -> { val at = exo.currentPosition; scope.launch { load(at, burnSubtitle = null); applySubtitle() } }
            else -> applySubtitle()
        }
        scope.launch { report("Progress") }
    }

    fun chooseAudio(index: Int) {
        if (index == audioIndex) return
        val ordinal = audioTracks.indexOfFirst { it.index == index }
        audioIndex = index
        val groups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (method != "Transcode" && ordinal in groups.indices) {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(groups[ordinal].mediaTrackGroup, 0)).build()
        } else {
            val at = exo.currentPosition
            scope.launch { load(at, audio = index, burnSubtitle = if (burnedIn) subtitleIndex else null) }
        }
    }

    fun chooseQuality(mbps: Int?) {
        qualityOverride = mbps
        val at = exo.currentPosition
        scope.launch { load(at, burnSubtitle = if (burnedIn) subtitleIndex else null) }
    }

    fun chooseSpeed(s: Float) {
        speed = s
        exo.setPlaybackSpeed(s)
    }

    fun toggle() { if (exo.isPlaying) exo.pause() else { if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0); exo.play() } }
    fun seekBy(ms: Long) { if (!live) exo.seekTo((exo.currentPosition + ms).coerceIn(0, duration.coerceAtLeast(0))) }
    fun seekTo(ms: Long) { if (!live) exo.seekTo(ms) }
    fun retry() { val at = exo.currentPosition; scope.launch { load(at) } }

    // ---- Intros, credits and the next episode ----

    /** The intro/recap the player is inside right now, if your server marks them. */
    val activeSkip: Segment?
        get() = segments.firstOrNull { s ->
            s.type in SKIPPABLE && position * TICKS_PER_MS in s.startTicks until (s.endTicks - 2_000 * TICKS_PER_MS)
        }

    fun skip(seg: Segment) {
        skippedSegments += seg.startTicks
        exo.seekTo(seg.endTicks / TICKS_PER_MS)
    }

    private fun checkSegments() {
        val seg = activeSkip
        if (seg != null && prefs.skipIntro.value == "auto" && seg.startTicks !in skippedSegments) skip(seg)
        // Credits rolling: offer the next episode early.
        val outro = segments.firstOrNull { it.type == "Outro" }
        val inCredits = outro != null && position * TICKS_PER_MS >= outro.startTicks
        val nearEnd = duration > 0 && duration - position < 25_000 && outro == null
        if ((inCredits || nearEnd) && nextEpisode != null && upNextCountdown == null && !upNextDismissed && playing) startUpNext()
    }

    private fun startUpNext() {
        if (!prefs.autoPlayNext.value) { upNextCountdown = -1; return } // card without a timer
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var s = 15
            while (s > 0) { upNextCountdown = s; delay(1000); s-- }
            playNext()
        }
    }

    fun cancelUpNext() {
        countdownJob?.cancel()
        upNextCountdown = null
        upNextDismissed = true
    }

    fun playNext() {
        val next = nextEpisode ?: return
        countdownJob?.cancel()
        upNextCountdown = null
        scope.launch {
            report("Stopped")
            started = false
            upNextDismissed = false
            skippedSegments.clear()
            source = null
            subtitleIndex = null
            audioIndex = null
            nextEpisode = null
            segments = emptyList()
            open(next.id, 0L, false)
        }
    }

    private fun onEnded() {
        if (nextEpisode != null && prefs.autoPlayNext.value && !upNextDismissed) playNext()
        else { scope.launch { report("Stopped") }; onFinished() }
    }

    // ---- Telling Jellyfin where you are ----

    private fun startReporting() {
        reportJob?.cancel()
        reportJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (exo.isPlaying) report("Progress")
            }
        }
    }

    private suspend fun report(kind: String) {
        val i = item ?: return
        val body = buildJsonObject {
            put("ItemId", i.id)
            put("MediaSourceId", source?.id ?: i.id)
            playSessionId?.let { put("PlaySessionId", it) }
            source?.liveStreamId?.let { put("LiveStreamId", it) }
            put("PositionTicks", exo.currentPosition.coerceAtLeast(0) * TICKS_PER_MS)
            put("IsPaused", !exo.isPlaying)
            put("PlayMethod", method)
            put("CanSeek", !live)
            audioIndex?.let { put("AudioStreamIndex", it) }
            subtitleIndex?.let { put("SubtitleStreamIndex", it) }
        }
        api.report(kind, body)
    }

    /** Called when the player closes: save the spot, free the live tuner, release the player. */
    suspend fun close() {
        reportJob?.cancel()
        countdownJob?.cancel()
        report("Stopped")
        source?.liveStreamId?.let { api.closeLiveStream(it) }
        Refresh.bump()
    }

    companion object {
        val SKIPPABLE = setOf("Intro", "Recap", "Preview", "Commercial")
    }
}
