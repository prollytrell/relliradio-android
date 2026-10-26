package com.relli.radio.ui

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.relli.radio.RelliApp
import com.relli.radio.data.RadioStation
import com.relli.radio.data.Song
import com.relli.radio.playback.PlaybackService
import com.relli.radio.playback.isRadio
import com.relli.radio.playback.toMediaItem
import com.relli.radio.playback.toSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The screens' remote control for the playback service.
 * It connects a MediaController (the same kind of link the lock screen and Android Auto use)
 * and mirrors the player's state into flows the UI watches.
 */
object Playback {
    private var controller: MediaController? = null

    private val _current = MutableStateFlow<MediaItem?>(null)
    val current: StateFlow<MediaItem?> get() = _current
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> get() = _playing
    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> get() = _queue
    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> get() = _index
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> get() = _shuffle
    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> get() = _repeat
    private val _source = MutableStateFlow("")
    val source: StateFlow<String> get() = _source

    /** Original order, kept while shuffle is on so turning it off puts things back. */
    private var unshuffled: List<MediaItem>? = null

    val position: Long get() = controller?.currentPosition ?: 0
    val duration: Long get() = controller?.duration?.takeIf { it > 0 } ?: ((_current.value?.toSong()?.duration ?: 0) * 1000L)
    val buffered: Long get() = controller?.bufferedPosition ?: 0

    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            })
            sync()
        }, MoreExecutors.directExecutor())
    }

    private fun sync() {
        val c = controller ?: return
        _current.value = c.currentMediaItem
        _playing.value = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)
        _queue.value = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        _index.value = c.currentMediaItemIndex
        _repeat.value = c.repeatMode
    }

    // ---------- Starting music ----------

    fun play(songs: List<Song>, start: Int = 0, source: String = "", shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        var list = songs
        var idx = start.coerceIn(0, songs.lastIndex)
        if (shuffle) { list = songs.shuffled(); idx = 0 }
        unshuffled = null
        _shuffle.value = shuffle
        _source.value = source
        c.setMediaItems(list.map { it.toMediaItem() }, idx, 0)
        c.prepare()
        c.play()
    }

    fun playRadio(station: RadioStation) {
        val c = controller ?: return
        _source.value = "Internet radio"
        unshuffled = null
        _shuffle.value = false
        c.setMediaItem(station.toMediaItem())
        c.prepare()
        c.play()
    }

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs)
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    // ---------- Controls ----------

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun jump(i: Int) { controller?.let { it.seekTo(i, 0); it.play() } }
    fun remove(i: Int) { controller?.removeMediaItem(i) }
    fun move(from: Int, to: Int) { controller?.moveMediaItem(from, to) }

    fun clearUpcoming() {
        val c = controller ?: return
        if (c.currentMediaItemIndex + 1 < c.mediaItemCount) c.removeMediaItems(c.currentMediaItemIndex + 1, c.mediaItemCount)
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Shuffle rearranges the songs after the current one (so the queue you see is the order you'll hear). */
    fun setShuffle(on: Boolean) {
        val c = controller ?: return
        if (on == _shuffle.value || c.mediaItemCount == 0) { _shuffle.value = on; return }
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val i = c.currentMediaItemIndex
        if (on) {
            unshuffled = items
            if (i + 1 < items.size) {
                val rest = items.subList(i + 1, items.size).shuffled()
                c.removeMediaItems(i + 1, items.size)
                c.addMediaItems(rest)
            }
        } else {
            val original = unshuffled
            if (original != null) {
                val now = items[i].mediaId
                val pos = original.indexOfFirst { it.mediaId == now }
                if (pos >= 0) {
                    c.removeMediaItems(i + 1, items.size)
                    if (i > 0) c.removeMediaItems(0, i)
                    c.addMediaItems(0, original.subList(0, pos))
                    c.addMediaItems(original.subList(pos + 1, original.size))
                }
            }
            unshuffled = null
        }
        _shuffle.value = on
    }

    val currentSong: Song? get() = _current.value?.takeIf { !it.isRadio }?.toSong()

    fun maxBitRate() = RelliApp.instance.currentMaxBitRate()
}
