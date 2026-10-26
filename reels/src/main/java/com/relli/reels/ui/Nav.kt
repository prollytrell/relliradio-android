package com.relli.reels.ui

import android.content.Context
import androidx.navigation.NavController
import com.relli.reels.ReelsApp
import com.relli.reels.data.Item
import com.relli.reels.ui.player.PlayerActivity

/** One place that knows where tapping something goes: a details page, or straight into the player. */
object Nav {
    var controller: NavController? = null

    /** A screen to open as soon as the app is ready (sent by RelliHome). */
    var pending: String? = null

    fun go(route: String) {
        controller?.navigate(route) { launchSingleTop = true }
    }

    fun open(item: Item) {
        when (item.type) {
            "Episode", "Video", "Recording" -> play(item)
            "TvChannel" -> playChannel(item.id)
            "Program" -> item.channelId?.let { playChannel(it) }
            "Season" -> go("item/${item.seriesId ?: item.id}?season=${item.id}")
            "CollectionFolder", "UserView" -> go("browse?view=${item.id}")
            else -> go("item/${item.id}")
        }
    }

    /** Plays [item]: from where you left off, or from the start when [fromStart]. */
    fun play(item: Item, fromStart: Boolean = false) {
        val ctx: Context = ReelsApp.instance
        PlayerActivity.start(ctx, item.id, if (fromStart) 0L else -1L, live = false)
    }

    fun playChannel(channelId: String) {
        PlayerActivity.start(ReelsApp.instance, channelId, 0L, live = true)
    }
}
