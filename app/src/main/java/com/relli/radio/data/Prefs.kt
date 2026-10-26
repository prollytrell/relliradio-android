package com.relli.radio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App settings, kept in the app's private storage on the phone.
 * Each setting is also a StateFlow so screens update the moment it changes.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("relliradio", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    inner class Pref<T>(private val key: String, private val default: T, private val read: (String, T) -> T, private val write: SharedPreferences.Editor.(String, T) -> Unit) {
        private val state = MutableStateFlow(read(key, default))
        val flow: StateFlow<T> get() = state
        var value: T
            get() = state.value
            set(v) { state.value = v; sp.edit().apply { write(key, v) }.apply() }
    }

    private fun string(key: String, d: String) = Pref(key, d, { k, def -> sp.getString(k, def) ?: def }, { k, v -> putString(k, v) })
    private fun int(key: String, d: Int) = Pref(key, d, { k, def -> sp.getInt(k, def) }, { k, v -> putInt(k, v) })
    private fun float(key: String, d: Float) = Pref(key, d, { k, def -> sp.getFloat(k, def) }, { k, v -> putFloat(k, v) })
    private fun bool(key: String, d: Boolean) = Pref(key, d, { k, def -> sp.getBoolean(k, def) }, { k, v -> putBoolean(k, v) })
    private fun list(key: String, d: List<String>) = Pref(key, d,
        { k, def -> sp.getString(k, null)?.split(",")?.filter { it.isNotEmpty() } ?: def },
        { k, v -> putString(k, v.joinToString(",")) })

    // Sign-in
    val server = string("server", "")
    val user = string("user", "")
    val password = string("password", "")
    val serverName = string("serverName", "")

    // Streaming: 0 = original files, otherwise MP3 kbps
    val wifiQuality = int("wifiQuality", 0)
    val mobileQuality = int("mobileQuality", 320)

    // Playback
    val speed = float("speed", 1f)
    val pitch = int("pitch", 0) // semitones
    val replayGain = string("replayGain", "off") // off | track | album | auto
    val rgPreamp = float("rgPreamp", 0f)
    val smartQueue = string("smartQueue", "off") // off | random | genre | artist | similar
    val smartFlow = string("smartFlow", "off") // off | double | artistfan
    val sleepFade = bool("sleepFade", true)
    val skipSilence = bool("skipSilence", false)

    // Equalizer (Android's built-in audio effects)
    val eqEnabled = bool("eqEnabled", false)
    val eqPreset = int("eqPreset", -1) // -1 = custom
    val eqBands = list("eqBands", emptyList()) // millibels per band
    val bassBoost = int("bassBoost", 0) // 0..1000
    val virtualizer = int("virtualizer", 0) // 0..1000
    val loudness = int("loudness", 0) // millibels of gain

    // Interface
    val visual = string("visual", "cover") // cover | lyrics | visualizer
    val vizStyle = string("vizStyle", "bars") // bars | wave | ring
    val lyricsSize = string("lyricsSize", "m")
    val homeWidgets = list("homeWidgets", DEFAULT_HOME)
    val albumGrid = bool("albumGrid", true)
    /** Picking a song opens Now Playing right away. */
    val openPlayerOnSelect = bool("openPlayerOnSelect", true)

    // Saved queue (so the app resumes where you left off)
    var savedQueue: SavedQueue?
        get() = try {
            val s = sp.getString("queue", null) ?: return null
            val q = json.decodeFromString<StoredQueue>(s)
            SavedQueue(q.songs, q.current, q.position)
        } catch (e: Exception) { null }
        set(v) {
            sp.edit().putString("queue", v?.let { json.encodeToString(StoredQueue(it.songs.take(500), it.currentId, it.positionMs)) }).apply()
        }

    /** When this device last saved the queue to the server (milliseconds). */
    val lastSync = Pref("lastSync", 0L, { k, d -> sp.getLong(k, d) }, { k, v -> putLong(k, v) })

    val credentials: Credentials?
        get() = if (server.value.isBlank() || user.value.isBlank()) null else Credentials(server.value, user.value, password.value)

    fun signOut() {
        password.value = ""
        sp.edit().remove("queue").apply()
    }

    companion object {
        val DEFAULT_HOME = listOf("mixes", "favTracks", "newest", "rediscover", "recent", "randomArtists", "randomAlbums", "continue", "mostPlayed")
    }
}

@kotlinx.serialization.Serializable
private data class StoredQueue(val songs: List<Song>, val current: String?, val position: Long)
