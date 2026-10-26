package com.relli.reels

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.relli.reels.data.Jellyfin
import com.relli.reels.data.Prefs

/** Starts once per app launch and holds the shared pieces: settings, server connection, image loader. */
class ReelsApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var api: Jellyfin
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        api = Jellyfin(this, prefs)
    }

    // Posters and backdrops are cached on the device (up to 500 MB) so rows scroll smoothly.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(api.http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(500L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()

    /** Max streaming bitrate (bits/s) for the current network: the Wi-Fi or mobile-data setting. */
    fun maxBitrate(): Int {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        val mbps = if (metered) prefs.mobileQuality.value else prefs.wifiQuality.value
        return if (mbps <= 0) 200_000_000 else mbps * 1_000_000
    }

    companion object {
        lateinit var instance: ReelsApp
            private set
        val prefs get() = instance.prefs
        val api get() = instance.api
    }
}
