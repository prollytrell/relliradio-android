package com.relli.radio.ui

import android.net.Uri
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow

/** Lets any screen or sheet jump somewhere (an album, an artist) or open/close Now Playing. */
object Nav {
    var controller: NavHostController? = null
    val playerOpen = MutableStateFlow(false)

    fun go(route: String) {
        playerOpen.value = false
        controller?.navigate(route) { launchSingleTop = true }
    }

    fun album(id: String) = go("album/$id")
    fun artist(id: String) = go("artist/$id")
    fun playlist(id: String) = go("playlist/$id")
    fun genre(name: String) = go("genre/${Uri.encode(name)}")
}
