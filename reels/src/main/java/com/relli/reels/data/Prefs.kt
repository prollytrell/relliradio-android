package com.relli.reels.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** App settings, kept in the app's private storage. Each one is a StateFlow so screens update right away. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("rellireels", Context.MODE_PRIVATE)

    inner class Pref<T>(private val key: String, default: T, read: (String, T) -> T, private val write: SharedPreferences.Editor.(String, T) -> Unit) {
        private val state = MutableStateFlow(read(key, default))
        val flow: StateFlow<T> get() = state
        var value: T
            get() = state.value
            set(v) { state.value = v; sp.edit().apply { write(key, v) }.apply() }
    }

    private fun string(key: String, d: String) = Pref(key, d, { k, def -> sp.getString(k, def) ?: def }, { k, v -> putString(k, v) })
    private fun int(key: String, d: Int) = Pref(key, d, { k, def -> sp.getInt(k, def) }, { k, v -> putInt(k, v) })
    private fun bool(key: String, d: Boolean) = Pref(key, d, { k, def -> sp.getBoolean(k, def) }, { k, v -> putBoolean(k, v) })

    // Sign-in. Jellyfin hands out an access token, so the password is never stored.
    val server = string("server", "")
    val userId = string("userId", "")
    val userName = string("userName", "")
    val token = string("token", "")
    val serverName = string("serverName", "")

    /** A random id for this device, so Jellyfin can tell your phone and TV apart. */
    val deviceId: String = sp.getString("deviceId", null) ?: UUID.randomUUID().toString().also { sp.edit().putString("deviceId", it).apply() }

    // Streaming: max bitrate in Mbps, 0 = original quality (no limit).
    val wifiQuality = int("wifiQuality", 0)
    val mobileQuality = int("mobileQuality", 4)

    // Playback
    val subtitles = string("subtitles", "auto") // auto (forced/foreign only) | on | off
    val subtitleLang = string("subtitleLang", "eng")
    val audioLang = string("audioLang", "")
    val subtitleSize = string("subtitleSize", "m") // s | m | l
    val autoPlayNext = bool("autoPlayNext", true)
    val skipIntro = string("skipIntro", "button") // button | auto | off

    val signedIn get() = server.value.isNotBlank() && token.value.isNotBlank() && userId.value.isNotBlank()

    fun signOut() {
        token.value = ""
    }
}
