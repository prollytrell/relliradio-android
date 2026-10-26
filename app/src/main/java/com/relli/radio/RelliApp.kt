package com.relli.radio

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.relli.radio.data.Prefs
import com.relli.radio.data.Subsonic

/** Starts once per app launch and holds the shared pieces: settings, server connection, image loader. */
class RelliApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var api: Subsonic
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        api = Subsonic { prefs.credentials }
    }

    // Album art is cached on the phone (up to 300 MB) so it loads instantly and saves data.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(api.http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("art")).maxSizeBytes(300L * 1024 * 1024).build() }
        .crossfade(true)
        .build()

    /** Streaming bitrate for the current network: Wi-Fi setting or mobile-data setting. */
    fun currentMaxBitRate(): Int {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        return if (metered) prefs.mobileQuality.value else prefs.wifiQuality.value
    }

    companion object {
        lateinit var instance: RelliApp
            private set
        val prefs get() = instance.prefs
        val api get() = instance.api
    }
}
