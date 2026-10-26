package com.relli.home.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android only shares "what's playing right now" with apps you've allowed to see notifications.
 * This empty listener is that permission; RelliHome never reads your notifications themselves.
 */
class MediaListener : NotificationListenerService()

/** What's playing in any app (RelliRadio, Tidal, YouTube…), with play/pause/skip. */
object NowPlaying {
    data class Info(
        val app: String,
        val title: String,
        val subtitle: String?,
        val art: Bitmap?,
        val playing: Boolean,
        val controller: MediaController,
    )

    private val state = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> get() = state
    /** False until you allow RelliHome to see what's playing (one ADB command, see Settings). */
    val allowed = MutableStateFlow(false)

    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(s: PlaybackState?) = refresh()
        override fun onMetadataChanged(m: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    fun start(context: Context) {
        val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager = m
        val component = ComponentName(context, MediaListener::class.java)
        try {
            watch(m.getActiveSessions(component))
            m.addOnActiveSessionsChangedListener(sessionsListener, component)
            allowed.value = true
        } catch (e: SecurityException) {
            allowed.value = false
        }
    }

    fun stop() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list.filter { it.packageName != "com.android.systemui" }
        controllers.forEach { it.registerCallback(callback) }
        refresh()
    }

    private fun refresh() {
        // Prefer whatever is actually playing; otherwise the most recent paused one.
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
        val md = c?.metadata
        if (c == null || md == null) { state.value = null; return }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return run { state.value = null }
        val sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        state.value = Info(c.packageName, title, sub, art, c.playbackState?.state == PlaybackState.STATE_PLAYING, c)
    }
}
