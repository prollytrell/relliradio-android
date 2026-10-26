package com.relli.reels.link

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.relli.reels.ReelsApp
import com.relli.reels.ui.MainActivity
import com.relli.reels.ui.player.PlayerActivity

/**
 * Doorway for RelliHome: "play this episode", "tune to this channel", or "open this show's page".
 * Only apps signed with your key may use it (see the permission in the manifest).
 */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        when {
            id == null || !ReelsApp.prefs.signedIn -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            intent.action == ACTION_PLAY -> PlayerActivity.start(this, id, intent.getLongExtra(EXTRA_START, -1L), live = false)
            intent.action == ACTION_LIVE -> PlayerActivity.start(this, id, 0L, live = true)
            else -> startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "item/$id")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }

    companion object {
        const val ACTION_PLAY = "com.relli.reels.action.PLAY"
        const val ACTION_LIVE = "com.relli.reels.action.LIVE"
        const val ACTION_OPEN = "com.relli.reels.action.OPEN"
        const val EXTRA_ID = "id"
        const val EXTRA_START = "startMs"
    }
}
