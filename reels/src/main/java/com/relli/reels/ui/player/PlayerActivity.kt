package com.relli.reels.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.relli.reels.ui.components.FocusRings
import com.relli.reels.ui.components.LocalIsTv
import com.relli.reels.ui.isTelevision
import com.relli.reels.ui.theme.RelliTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Full-screen video player. Phones turn sideways for it; the browsing screens stay upright. */
class PlayerActivity : ComponentActivity() {
    private lateinit var controller: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        controller = PlayerController(this, lifecycleScope) { finish() }
        open(intent)
        val tv = isTelevision(this)
        setContent {
            RelliTheme {
                CompositionLocalProvider(LocalIsTv provides tv) {
                    FocusRings { PlayerScreen(controller, onBack = { finish() }) }
                }
            }
        }
    }

    private fun open(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        controller.open(id, intent.getLongExtra(EXTRA_START, -1L), intent.getBooleanExtra(EXTRA_LIVE, false))
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (or turning the screen off) pauses, like any streaming app.
        if (!isFinishing) controller.exo.pause()
    }

    override fun onDestroy() {
        // Save your spot and release the tuner even though this screen is going away.
        val c = controller
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            c.close()
            c.exo.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_START = "start"
        private const val EXTRA_LIVE = "live"

        /** [startMs] = -1 resumes from the saved spot. */
        fun start(context: Context, id: String, startMs: Long, live: Boolean) {
            context.startActivity(Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_START, startMs).putExtra(EXTRA_LIVE, live)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
