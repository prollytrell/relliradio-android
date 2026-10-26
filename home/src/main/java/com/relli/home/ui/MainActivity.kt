package com.relli.home.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.relli.home.media.NowPlaying
import com.relli.home.ui.components.FocusRings
import com.relli.home.ui.components.LocalIsTv
import com.relli.home.ui.theme.Relli
import com.relli.home.ui.theme.RelliTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Bumped when apps are installed/removed or you come back to the home screen. */
    val appsChanged = MutableStateFlow(0)
    val resumed = MutableStateFlow(0)

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { appsChanged.value++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        androidx.core.content.ContextCompat.registerReceiver(this, packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            RelliTheme {
                CompositionLocalProvider(LocalIsTv provides true) {
                    FocusRings {
                        Surface(Modifier.fillMaxSize(), color = Relli.bg, contentColor = Relli.cream) {
                            HomeScreen(this, appsChanged, resumed)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NowPlaying.start(this)
        resumed.value++
    }

    override fun onPause() {
        super.onPause()
        NowPlaying.stop()
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }
}
