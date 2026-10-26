package com.relli.home

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.relli.home.data.Prefs
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class HomeApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
    }

    // Posters and backdrops are cached so the home screen is instant when you come back to it.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()

    companion object {
        lateinit var instance: HomeApp
            private set
        val prefs get() = instance.prefs
    }
}
