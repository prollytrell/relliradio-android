package com.relli.radio.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.relli.radio.RelliApp
import com.relli.radio.data.Song
import com.relli.radio.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * The music keeps playing here, even with the app closed.
 * Media3 turns this into the media notification (with the RR icon in the status bar),
 * lock-screen and headphone controls, and the Android Auto music browser.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val api get() = RelliApp.api

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(open)
            .build()
        PlayerHub.attach(player, scope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    // Swiping the app away stops the service only if nothing is playing.
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            PlayerHub.saveQueue(server = true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlayerHub.detach()
        session?.release()
        player.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Turning requests into playable songs ----------

    /** Gives every item a fresh stream address (with the current quality setting). */
    private suspend fun resolve(original: MediaItem): MediaItem {
        val item = if (original.mediaId.startsWith("ctx:"))
            original.buildUpon().setMediaId(original.mediaId.split(":", limit = 4).last()).build() else original
        if (item.isRadio) {
            val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
            if (uri != null) return item.buildUpon().setUri(uri).build()
            val id = item.mediaId.removePrefix("radio:")
            val station = api.radioStations().firstOrNull { it.id == id } ?: return item
            return station.toMediaItem()
        }
        if (item.mediaMetadata.title == null) return api.song(item.mediaId).toMediaItem()
        return item.buildUpon().setUri(api.streamUrl(item.mediaId, RelliApp.instance.currentMaxBitRate())).build()
    }

    /** Android Auto sends one tapped item; expand it into the album, playlist or mix it came from. */
    private suspend fun expand(id: String): Pair<List<Song>, Int>? {
        when {
            id == "mix:track" -> return Mixes.track().songs to 0
            id == "mix:album" -> return Mixes.album().songs to 0
            id == "mix:decade" -> return Mixes.decade().songs to 0
            id == "mix:favorites" -> return Mixes.favorites().songs to 0
            id.startsWith("mix:genre:") -> return Mixes.genre(id.removePrefix("mix:genre:")).songs to 0
            id.startsWith("album:") -> return api.album(id.removePrefix("album:")).song to 0
            id.startsWith("playlist:") -> return api.playlist(id.removePrefix("playlist:")).entry to 0
            id.startsWith("ctx:") -> {
                // ctx:<type>:<parentId>:<songId>
                val parts = id.split(":", limit = 4)
                if (parts.size < 4) return null
                val list = when (parts[1]) {
                    "album" -> api.album(parts[2]).song
                    "playlist" -> api.playlist(parts[2]).entry
                    "fav" -> api.starred().songs
                    else -> return null
                }
                return list to list.indexOfFirst { it.id == parts[3] }.coerceAtLeast(0)
            }
        }
        return null
    }

    // ---------- Session callback: the Android Auto browser and remote requests ----------

    private inner class Callback : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.map { resolve(it) }.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()
            val expanded = single?.let { runCatching { expand(it.mediaId) }.getOrNull() }
            if (expanded != null && expanded.first.isNotEmpty()) {
                MediaSession.MediaItemsWithStartPosition(expanded.first.map { it.toMediaItem() }, expanded.second, C.TIME_UNSET)
            } else {
                MediaSession.MediaItemsWithStartPosition(mediaItems.map { resolve(it) }, startIndex, startPositionMs)
            }
        }

        // "Resume" from the lock screen, Bluetooth headphones or the car, after the app was closed.
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val q = RelliApp.prefs.savedQueue ?: runCatching { api.playQueue() }.getOrNull()
            if (q == null || q.songs.isEmpty()) throw IllegalStateException("Nothing to resume")
            val idx = q.songs.indexOfFirst { it.id == q.currentId }.coerceAtLeast(0)
            MediaSession.MediaItemsWithStartPosition(q.songs.map { it.toMediaItem() }, idx, q.positionMs)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            com.google.common.util.concurrent.Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "RelliRadio"), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            try {
                if (mediaId.startsWith("ctx:")) LibraryResult.ofItem(api.song(mediaId.split(":", limit = 4)[3]).toMediaItem(), null)
                else LibraryResult.ofItem(api.song(mediaId).toMediaItem(), null)
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                val items: List<MediaItem> = when {
                    parentId == ROOT -> listOf(
                        folder("tab:mixes", "Mixes"),
                        folder("tab:albums", "Albums"),
                        folder("tab:playlists", "Playlists"),
                        folder("tab:favorites", "Favorites"),
                    )
                    parentId == "tab:mixes" -> buildList {
                        add(folder("mix:track", "Track mix", "Random songs", playable = true))
                        add(folder("mix:album", "Album mix", "Whole albums", playable = true))
                        add(folder("mix:decade", "Decade mix", "One era", playable = true))
                        add(folder("mix:favorites", "Favorites mix", "Your hearted songs", playable = true))
                        api.genres().sortedByDescending { it.songCount }.take(8).forEach {
                            add(folder("mix:genre:${it.value}", "${it.value} mix", "Genre", playable = true))
                        }
                        api.radioStations().forEach { add(it.toMediaItem()) }
                    }
                    parentId == "tab:albums" -> (api.albumList("recent", 15) + api.albumList("newest", 25))
                        .distinctBy { it.id }.map { it.toFolder() }
                    parentId == "tab:playlists" -> api.playlists().map { it.toFolder() }
                    parentId == "tab:favorites" -> api.starred().songs.map { ctxItem(it, "fav", "") }
                    parentId.startsWith("album:") -> {
                        val id = parentId.removePrefix("album:")
                        api.album(id).song.map { ctxItem(it, "album", id) }
                    }
                    parentId.startsWith("playlist:") -> {
                        val id = parentId.removePrefix("playlist:")
                        api.playlist(id).entry.map { ctxItem(it, "playlist", id) }
                    }
                    else -> emptyList()
                }
                val from = (page * pageSize).coerceAtMost(items.size)
                val to = (from + pageSize).coerceAtMost(items.size)
                LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params)
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = runCatching { api.search(query, 30).songs.size }.getOrDefault(0)
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val r = runCatching { api.search(query, 30) }.getOrNull()
            val items = buildList {
                r?.albums?.take(5)?.forEach { add(it.toFolder()) }
                r?.songs?.forEach { add(it.toMediaItem()) }
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }
    }

    private fun ctxItem(song: Song, type: String, parent: String): MediaItem {
        val base = song.toMediaItem()
        return base.buildUpon().setMediaId("ctx:$type:$parent:${song.id}").build()
    }

    companion object {
        const val ROOT = "root"
    }
}
