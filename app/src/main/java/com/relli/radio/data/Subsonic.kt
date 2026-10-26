package com.relli.radio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

data class Credentials(val server: String, val user: String, val password: String)

/**
 * Talks to Navidrome with the Subsonic API.
 * Every request carries a fresh random "salt" and token = md5(password + salt),
 * so the password itself never travels over the network.
 */
class Subsonic(private val creds: () -> Credentials?) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val random = SecureRandom()

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun salt(): String = (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")

    /** Builds a full URL for an API method, with sign-in parameters. */
    fun url(method: String, params: List<Pair<String, Any?>> = emptyList(), c: Credentials? = creds()): HttpUrl {
        val cr = c ?: throw ApiException("Not signed in")
        val base = cr.server.trimEnd('/')
        val s = salt()
        val b = "$base/rest/$method".toHttpUrl().newBuilder()
            .addQueryParameter("u", cr.user)
            .addQueryParameter("t", md5(cr.password + s))
            .addQueryParameter("s", s)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", CLIENT)
            .addQueryParameter("f", "json")
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        return b.build()
    }

    // ---------- Short-term memory of library reads ----------
    // Going back to an album or artist you just saw is instant instead of asking the server again.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, JsonObject>>()
    private val cacheable = setOf("getAlbum", "getArtist", "getArtists", "getArtistInfo2", "getTopSongs", "getGenres",
        "getAlbumList2", "getPlaylists", "getPlaylist", "getStarred2", "getInternetRadioStations", "getLyricsBySongId", "getSong")
    private val changesLibrary = setOf("star", "unstar", "setRating", "createPlaylist", "updatePlaylist", "deletePlaylist")
    private val cacheTtlMs = 10 * 60_000L

    fun clearCache() = cache.clear()

    private suspend fun call(method: String, vararg params: Pair<String, Any?>, c: Credentials? = null): JsonObject {
        val random = params.any { it.first == "type" && it.second == "random" }
        val key = if (method in cacheable && !random && c == null) method + params.joinToString("&") { "${it.first}=${it.second}" } else null
        if (key != null) cache[key]?.let { (at, value) -> if (System.currentTimeMillis() - at < cacheTtlMs) return value }
        if (method in changesLibrary) cache.clear()
        val result = fetch(method, params, c)
        if (key != null) {
            if (cache.size > 300) cache.clear()
            cache[key] = System.currentTimeMillis() to result
        }
        return result
    }

    private suspend fun fetch(method: String, params: Array<out Pair<String, Any?>>, c: Credentials?): JsonObject =
        withContext(Dispatchers.IO) {
            val u = url(method, params.toList(), c ?: creds())
            val res = try {
                http.newCall(Request.Builder().url(u).build()).execute()
            } catch (e: Exception) {
                throw ApiException("Can't reach the server. Check the address and your connection (VPN when away from home).")
            }
            res.use {
                if (!it.isSuccessful) throw ApiException("The server answered with error ${it.code}.")
                val body = json.parseToJsonElement(it.body!!.string()).jsonObject
                val r = body["subsonic-response"]?.jsonObject ?: throw ApiException("That address isn't a Navidrome server.")
                if (r["status"]?.jsonPrimitive?.contentOrNull != "ok") {
                    val err = r["error"]?.jsonObject
                    throw ApiException(
                        err?.get("message")?.jsonPrimitive?.contentOrNull ?: "Request failed",
                        err?.get("code")?.jsonPrimitive?.intOrNull ?: 0,
                    )
                }
                r
            }
        }

    private inline fun <reified T> JsonElement?.list(): List<T> = when (this) {
        null -> emptyList()
        is JsonArray -> map { json.decodeFromJsonElement<T>(it) }
        is JsonObject -> listOf(json.decodeFromJsonElement<T>(this))
        else -> emptyList()
    }

    private inline fun <reified T> JsonElement.one(): T = json.decodeFromJsonElement(this)

    // ---------- Sign-in ----------

    suspend fun login(c: Credentials): String {
        val r = call("ping", c = c)
        val type = r["type"]?.jsonPrimitive?.contentOrNull ?: "subsonic"
        val version = r["serverVersion"]?.jsonPrimitive?.contentOrNull ?: ""
        return "$type $version".trim()
    }

    // ---------- Library ----------

    suspend fun albumList(type: String, size: Int = 30, offset: Int = 0, genre: String? = null, fromYear: Int? = null, toYear: Int? = null): List<Album> =
        call("getAlbumList2", "type" to type, "size" to size, "offset" to offset, "genre" to genre, "fromYear" to fromYear, "toYear" to toYear)["albumList2"]
            ?.jsonObject?.get("album").list()

    suspend fun album(id: String): Album = call("getAlbum", "id" to id)["album"]!!.one()

    suspend fun artists(): List<Artist> =
        call("getArtists")["artists"]?.jsonObject?.get("index")?.jsonArray
            ?.flatMap { it.jsonObject["artist"].list<Artist>() } ?: emptyList()

    suspend fun artist(id: String): Artist = call("getArtist", "id" to id)["artist"]!!.one()

    suspend fun artistBio(id: String): String =
        try {
            call("getArtistInfo2", "id" to id)["artistInfo2"]?.jsonObject?.get("biography")?.jsonPrimitive?.contentOrNull
                ?.replace(Regex("<a [^>]*>.*?</a>"), "")?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
        } catch (e: Exception) { "" }

    suspend fun topSongs(artistName: String, count: Int = 10): List<Song> =
        try { call("getTopSongs", "artist" to artistName, "count" to count)["topSongs"]?.jsonObject?.get("song").list() }
        catch (e: Exception) { emptyList() }

    suspend fun search(query: String, songCount: Int = 40): SearchResult {
        val r = call("search3", "query" to query, "artistCount" to 10, "albumCount" to 20, "songCount" to songCount)["searchResult3"]?.jsonObject
        return SearchResult(r?.get("artist").list(), r?.get("album").list(), r?.get("song").list())
    }

    suspend fun allSongs(offset: Int, size: Int = 100): List<Song> =
        call("search3", "query" to "", "artistCount" to 0, "albumCount" to 0, "songCount" to size, "songOffset" to offset)["searchResult3"]
            ?.jsonObject?.get("song").list()

    suspend fun randomSongs(size: Int = 50, genre: String? = null, fromYear: Int? = null, toYear: Int? = null): List<Song> =
        call("getRandomSongs", "size" to size, "genre" to genre, "fromYear" to fromYear, "toYear" to toYear)["randomSongs"]
            ?.jsonObject?.get("song").list()

    suspend fun genres(): List<Genre> = call("getGenres")["genres"]?.jsonObject?.get("genre").list()

    suspend fun songsByGenre(genre: String, count: Int = 100): List<Song> =
        call("getSongsByGenre", "genre" to genre, "count" to count)["songsByGenre"]?.jsonObject?.get("song").list()

    suspend fun similarSongs(id: String, count: Int = 50): List<Song> =
        try { call("getSimilarSongs2", "id" to id, "count" to count)["similarSongs2"]?.jsonObject?.get("song").list() }
        catch (e: Exception) { emptyList() }

    suspend fun song(id: String): Song = call("getSong", "id" to id)["song"]!!.one()

    suspend fun starred(): Starred {
        val r = call("getStarred2")["starred2"]?.jsonObject
        return Starred(r?.get("song").list(), r?.get("album").list(), r?.get("artist").list())
    }

    suspend fun setStarred(id: String, on: Boolean) { call(if (on) "star" else "unstar", "id" to id) }
    suspend fun setRating(id: String, rating: Int) { call("setRating", "id" to id, "rating" to rating) }

    suspend fun radioStations(): List<RadioStation> =
        try { call("getInternetRadioStations")["internetRadioStations"]?.jsonObject?.get("internetRadioStation").list() }
        catch (e: Exception) { emptyList() }

    // ---------- Playlists ----------

    suspend fun playlists(): List<Playlist> = call("getPlaylists")["playlists"]?.jsonObject?.get("playlist").list()
    suspend fun playlist(id: String): Playlist = call("getPlaylist", "id" to id)["playlist"]!!.one()

    suspend fun createPlaylist(name: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        val params = mutableListOf<Pair<String, Any?>>("name" to name)
        songIds.forEach { params += "songId" to it }
        rawCall("createPlaylist", params)
    }

    suspend fun addToPlaylist(id: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        val params = mutableListOf<Pair<String, Any?>>("playlistId" to id)
        songIds.forEach { params += "songIdToAdd" to it }
        rawCall("updatePlaylist", params)
    }

    suspend fun removeFromPlaylist(id: String, index: Int) { call("updatePlaylist", "playlistId" to id, "songIndexToRemove" to index) }

    private fun rawCall(method: String, params: List<Pair<String, Any?>>) {
        cache.clear()
        val res = http.newCall(Request.Builder().url(url(method, params)).build()).execute()
        res.use {
            val r = json.parseToJsonElement(it.body!!.string()).jsonObject["subsonic-response"]?.jsonObject
            if (r?.get("status")?.jsonPrimitive?.contentOrNull != "ok") throw ApiException("Couldn't update the playlist.")
        }
    }

    // ---------- Playback helpers ----------

    suspend fun scrobble(id: String, submission: Boolean) {
        try { call("scrobble", "id" to id, "submission" to submission, "time" to System.currentTimeMillis()) } catch (_: Exception) {}
    }

    suspend fun savePlayQueue(ids: List<String>, current: String?, positionMs: Long) = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf<Pair<String, Any?>>("current" to current, "position" to positionMs)
            ids.take(500).forEach { params += "id" to it }
            http.newCall(Request.Builder().url(url("savePlayQueue", params)).build()).execute().close()
        } catch (_: Exception) {}
    }

    suspend fun playQueue(): SavedQueue? {
        val q = call("getPlayQueue")["playQueue"]?.jsonObject ?: return null
        val songs = q["entry"].list<Song>()
        if (songs.isEmpty()) return null
        val changed = q["changed"]?.jsonPrimitive?.contentOrNull
        val at = changed?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
        return SavedQueue(songs, q["current"]?.jsonPrimitive?.contentOrNull, q["position"]?.jsonPrimitive?.longOrNull ?: 0,
            q["changedBy"]?.jsonPrimitive?.contentOrNull, at)
    }

    suspend fun lyrics(song: Song): Lyrics? {
        try {
            val list = call("getLyricsBySongId", "id" to song.id)["lyricsList"]?.jsonObject?.get("structuredLyrics")?.jsonArray
            val pick = list?.map { it.jsonObject }?.sortedByDescending { it["synced"]?.jsonPrimitive?.booleanOrNull == true }?.firstOrNull()
            if (pick != null) {
                val synced = pick["synced"]?.jsonPrimitive?.booleanOrNull == true
                val offset = pick["offset"]?.jsonPrimitive?.longOrNull ?: 0
                val lines = pick["line"]?.jsonArray?.map {
                    val o = it.jsonObject
                    LyricLine(if (synced) (o["start"]?.jsonPrimitive?.longOrNull ?: 0) + offset else null, o["value"]?.jsonPrimitive?.contentOrNull ?: "")
                } ?: emptyList()
                if (lines.any { it.text.isNotBlank() }) return Lyrics(synced, lines)
            }
        } catch (_: Exception) {}
        // Older servers: plain-text lyrics by artist and title.
        try {
            val text = call("getLyrics", "artist" to song.artist, "title" to song.title)["lyrics"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrBlank()) return parseLrc(text)
        } catch (_: Exception) {}
        return null
    }

    // ---------- URLs ----------

    fun streamUrl(id: String, maxBitRate: Int = 0): String =
        url("stream", listOf("id" to id, "maxBitRate" to maxBitRate.takeIf { it > 0 }, "format" to if (maxBitRate > 0) "mp3" else null)).toString()

    fun coverUrl(id: String?, size: Int = 300): String? =
        if (id.isNullOrEmpty()) null else try { url("getCoverArt", listOf("id" to id, "size" to size)).toString() } catch (e: Exception) { null }

    fun downloadUrl(id: String): String = url("download", listOf("id" to id)).toString()

    companion object {
        /** How this device names itself to Navidrome, so other devices can say where to pick up from. */
        val CLIENT: String = "RelliRadio (${android.os.Build.MODEL})"

        private val lrcTime = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        fun parseLrc(text: String): Lyrics {
            val out = mutableListOf<LyricLine>()
            var synced = false
            text.lines().forEach { raw ->
                val times = lrcTime.findAll(raw).toList()
                val body = raw.replace(lrcTime, "").trim()
                if (times.isEmpty()) { if (!raw.trim().startsWith("[")) out += LyricLine(null, raw.trim()) }
                else times.forEach { m ->
                    synced = true
                    out += LyricLine(((m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()) * 1000).toLong(), body)
                }
            }
            return Lyrics(synced, if (synced) out.filter { it.startMs != null }.sortedBy { it.startMs } else out)
        }
    }
}
