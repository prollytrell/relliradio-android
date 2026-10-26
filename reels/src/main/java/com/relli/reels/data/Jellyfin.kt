package com.relli.reels.data

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

class ServerError(message: String, val code: Int = 0) : Exception(message)

/**
 * Talks to your Jellyfin server over its HTTP API. Every request carries an "Authorization" header
 * naming this app and device; after sign-in it also carries the access token Jellyfin gave us.
 */
class Jellyfin(private val context: Context, private val prefs: Prefs) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val server get() = prefs.server.value
    private val user get() = prefs.userId.value
    private val device: String = (Build.MODEL ?: "Android").replace(Regex("[^A-Za-z0-9 ._-]"), "").ifBlank { "Android" }

    fun authHeader(token: String? = prefs.token.value.ifBlank { null }) = buildString {
        append("MediaBrowser Client=\"RelliReels\", Device=\"$device\", DeviceId=\"${prefs.deviceId}\", Version=\"1.0\"")
        if (token != null) append(", Token=\"$token\"")
    }

    private fun url(base: String, path: String, params: Map<String, Any?>): String {
        val b = (base.trimEnd('/') + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        return b.build().toString()
    }

    private suspend fun call(method: String, path: String, params: Map<String, Any?> = emptyMap(), body: String? = null, base: String = server, token: String? = prefs.token.value.ifBlank { null }): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url(base, path, params))
                .header("Authorization", authHeader(token))
                .header("Accept", "application/json")
                .method(method, when {
                    body != null -> body.toRequestBody("application/json".toMediaType())
                    method == "POST" -> "".toRequestBody(null)
                    else -> null
                })
                .build()
            try {
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        throw ServerError(when (r.code) {
                            401 -> if (path.contains("Authenticate")) "Wrong username or password." else "Your sign-in expired. Sign in again."
                            403 -> "Your Jellyfin account isn't allowed to do that."
                            404 -> "Not found on the server."
                            else -> "The server answered ${r.code}."
                        }, r.code)
                    }
                    text
                }
            } catch (e: ServerError) {
                throw e
            } catch (e: Exception) {
                throw ServerError("Can't reach the server. At home? Check the address. Away? Turn on WireGuard.")
            }
        }

    private suspend inline fun <reified T> get(path: String, params: Map<String, Any?> = emptyMap()): T =
        json.decodeFromString(call("GET", path, params))

    // ---------- Sign-in ----------

    suspend fun publicInfo(base: String): PublicInfo = json.decodeFromString(call("GET", "/System/Info/Public", base = base, token = null))

    suspend fun login(base: String, username: String, password: String): AuthResult {
        val body = buildJsonObject { put("Username", username); put("Pw", password) }.toString()
        return json.decodeFromString(call("POST", "/Users/AuthenticateByName", body = body, base = base, token = null))
    }

    /** Quick Connect: show a code on this screen, approve it from another signed-in device. */
    suspend fun quickConnectStart(base: String): QuickConnect = try {
        json.decodeFromString(call("POST", "/QuickConnect/Initiate", base = base, token = null))
    } catch (e: ServerError) {
        if (e.code == 401 || e.code == 403) throw ServerError("Quick Connect is turned off on your server. Turn it on in Jellyfin's Dashboard → General.")
        json.decodeFromString(call("GET", "/QuickConnect/Initiate", base = base, token = null))
    }

    suspend fun quickConnectState(base: String, secret: String): QuickConnect =
        json.decodeFromString(call("GET", "/QuickConnect/Connect", mapOf("Secret" to secret), base = base, token = null))

    suspend fun quickConnectFinish(base: String, secret: String): AuthResult =
        json.decodeFromString(call("POST", "/Users/AuthenticateWithQuickConnect", body = buildJsonObject { put("Secret", secret) }.toString(), base = base, token = null))

    // ---------- Browsing ----------

    suspend fun views(): List<Item> = get<ItemsResult>("/Users/$user/Views").items

    suspend fun resume(limit: Int = 20): List<Item> = get<ItemsResult>("/Users/$user/Items/Resume", mapOf(
        "Limit" to limit, "MediaTypes" to "Video", "Fields" to FIELDS, "EnableTotalRecordCount" to false,
    )).items

    suspend fun nextUp(limit: Int = 20, seriesId: String? = null): List<Item> = get<ItemsResult>("/Shows/NextUp", mapOf(
        "UserId" to user, "Limit" to limit, "Fields" to FIELDS, "SeriesId" to seriesId, "EnableResumable" to false,
    )).items

    suspend fun latest(parentId: String, limit: Int = 24): List<Item> = get<List<Item>>("/Users/$user/Items/Latest", mapOf(
        "ParentId" to parentId, "Limit" to limit, "Fields" to FIELDS, "GroupItems" to true,
    ))

    suspend fun items(
        parentId: String? = null,
        types: String? = "Movie,Series",
        genre: String? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        start: Int = 0,
        limit: Int = 60,
        favorites: Boolean = false,
        search: String? = null,
        unplayed: Boolean = false,
        ids: String? = null,
    ): ItemsResult = get("/Users/$user/Items", mapOf(
        "ParentId" to parentId, "IncludeItemTypes" to types, "Recursive" to true, "Genres" to genre,
        "SortBy" to sortBy, "SortOrder" to sortOrder, "StartIndex" to start, "Limit" to limit, "Fields" to FIELDS,
        "IsFavorite" to (if (favorites) true else null), "SearchTerm" to search, "Ids" to ids,
        "Filters" to (if (unplayed) "IsUnplayed" else null), "ImageTypeLimit" to 1,
    ))

    suspend fun item(id: String): Item = get("/Users/$user/Items/$id", mapOf("Fields" to "$FIELDS,People,MediaStreams"))

    suspend fun seasons(seriesId: String): List<Item> = get<ItemsResult>("/Shows/$seriesId/Seasons", mapOf("UserId" to user, "Fields" to FIELDS)).items

    suspend fun episodes(seriesId: String, seasonId: String? = null, startItemId: String? = null, limit: Int? = null): List<Item> =
        get<ItemsResult>("/Shows/$seriesId/Episodes", mapOf(
            "UserId" to user, "SeasonId" to seasonId, "StartItemId" to startItemId, "Limit" to limit, "Fields" to FIELDS,
        )).items

    /** The episode after [episode], or null at the end of the show. */
    suspend fun nextEpisode(episode: Item): Item? {
        val series = episode.seriesId ?: return null
        val list = episodes(series, startItemId = episode.id, limit = 2)
        return list.getOrNull(1)?.takeIf { it.id != episode.id }
    }

    suspend fun similar(id: String, limit: Int = 16): List<Item> = get<ItemsResult>("/Items/$id/Similar", mapOf("UserId" to user, "Limit" to limit, "Fields" to FIELDS)).items

    suspend fun genres(parentId: String?, types: String = "Movie,Series"): List<Genre> = get<GenresResult>("/Genres", mapOf(
        "UserId" to user, "ParentId" to parentId, "IncludeItemTypes" to types, "SortBy" to "SortName",
    )).items

    suspend fun setFavorite(id: String, on: Boolean) { call(if (on) "POST" else "DELETE", "/Users/$user/FavoriteItems/$id") }
    suspend fun setPlayed(id: String, on: Boolean) { call(if (on) "POST" else "DELETE", "/Users/$user/PlayedItems/$id") }

    // ---------- Live TV ----------

    suspend fun channels(filter: String = "all", limit: Int = 300): List<Item> = get<ItemsResult>("/LiveTv/Channels", mapOf(
        "UserId" to user, "AddCurrentProgram" to true, "EnableUserData" to true, "Limit" to limit, "SortBy" to "SortName",
        "IsFavorite" to (if (filter == "favorites") true else null),
        "IsMovie" to (if (filter == "movies") true else null),
        "IsNews" to (if (filter == "news") true else null),
        "IsSports" to (if (filter == "sports") true else null),
        "IsKids" to (if (filter == "kids") true else null),
        "IsSeries" to (if (filter == "series") true else null),
    )).items

    suspend fun programs(channelIds: List<String>, hours: Long = 4): List<Item> {
        if (channelIds.isEmpty()) return emptyList()
        val now = Instant.now()
        return get<ItemsResult>("/LiveTv/Programs", mapOf(
            "UserId" to user, "ChannelIds" to channelIds.joinToString(","), "MinEndDate" to now.toString(),
            "MaxStartDate" to now.plusSeconds(hours * 3600).toString(), "SortBy" to "StartDate", "EnableImages" to false,
            "Fields" to "Overview",
        )).items
    }

    suspend fun liveTvEnabled(): Boolean = try { channels(limit = 1).isNotEmpty() } catch (e: Exception) { false }

    // ---------- Playback ----------

    /** Asks Jellyfin how to play [id] on this device: straight from the file, or converted on the fly. */
    suspend fun playbackInfo(
        id: String, startTicks: Long, maxBitrate: Int, audioIndex: Int? = null, subtitleIndex: Int? = null,
        mediaSourceId: String? = null, live: Boolean = false, allowDirect: Boolean = true,
    ): PlaybackInfo {
        val body = buildJsonObject {
            put("UserId", user)
            put("MaxStreamingBitrate", maxBitrate)
            put("StartTimeTicks", startTicks)
            if (audioIndex != null) put("AudioStreamIndex", audioIndex)
            if (subtitleIndex != null) put("SubtitleStreamIndex", subtitleIndex)
            if (mediaSourceId != null) put("MediaSourceId", mediaSourceId)
            put("AutoOpenLiveStream", live)
            put("EnableDirectPlay", allowDirect)
            put("EnableDirectStream", allowDirect)
            put("EnableTranscoding", true)
            put("AllowVideoStreamCopy", true)
            put("AllowAudioStreamCopy", true)
            put("DeviceProfile", deviceProfile(maxBitrate))
        }.toString()
        return json.decodeFromString(call("POST", "/Items/$id/PlaybackInfo", mapOf("UserId" to user), body))
    }

    fun directUrl(id: String, source: MediaSource, playSessionId: String?): String = url(server, "/Videos/$id/stream", mapOf(
        "static" to true, "MediaSourceId" to (source.id ?: id), "PlaySessionId" to playSessionId,
        "DeviceId" to prefs.deviceId, "LiveStreamId" to source.liveStreamId, "api_key" to prefs.token.value,
    ))

    fun absolute(path: String): String = if (path.startsWith("http")) path else server.trimEnd('/') + path

    suspend fun report(kind: String, body: JsonObject) {
        runCatching { call("POST", "/Sessions/Playing" + if (kind.isEmpty()) "" else "/$kind", body = body.toString()) }
    }

    suspend fun closeLiveStream(liveStreamId: String) {
        runCatching { call("POST", "/LiveStreams/Close", mapOf("liveStreamId" to liveStreamId)) }
    }

    /** Intro / credits markers (Jellyfin 10.10+ with a segment provider like Intro Skipper). */
    suspend fun segments(id: String): List<Segment> = try { get<SegmentsResult>("/MediaSegments/$id").items } catch (e: Exception) { emptyList() }

    // ---------- Images ----------

    fun image(id: String?, type: String = "Primary", tag: String? = null, width: Int = 400, index: Int? = null): String? {
        if (id == null) return null
        val path = "/Items/$id/Images/$type" + (if (index != null) "/$index" else "")
        return url(server, path, mapOf("tag" to tag, "maxWidth" to width, "quality" to 90))
    }

    fun poster(i: Item, width: Int = 400): String? = when {
        i.isEpisode && i.seriesId != null -> image(i.seriesId, tag = i.seriesPrimaryTag, width = width)
        i.imageTags["Primary"] != null -> image(i.id, tag = i.imageTags["Primary"], width = width)
        i.seriesId != null -> image(i.seriesId, tag = i.seriesPrimaryTag, width = width)
        else -> image(i.id, width = width)
    }

    /** 16:9 picture: an episode's own still, or the show's/movie's thumb or backdrop. */
    fun landscape(i: Item, width: Int = 640): String? = when {
        i.isEpisode && i.imageTags["Primary"] != null -> image(i.id, tag = i.imageTags["Primary"], width = width)
        i.imageTags["Thumb"] != null -> image(i.id, "Thumb", i.imageTags["Thumb"], width)
        i.parentThumbItemId != null -> image(i.parentThumbItemId, "Thumb", i.parentThumbTag, width)
        else -> backdrop(i, width)
    }

    fun backdrop(i: Item, width: Int = 1280): String? = when {
        i.backdropTags.isNotEmpty() -> image(i.id, "Backdrop", i.backdropTags[0], width, 0)
        i.parentBackdropItemId != null && i.parentBackdropTags.isNotEmpty() -> image(i.parentBackdropItemId, "Backdrop", i.parentBackdropTags[0], width, 0)
        else -> null
    }

    fun logo(i: Item, width: Int = 600): String? = when {
        i.imageTags["Logo"] != null -> image(i.id, "Logo", i.imageTags["Logo"], width)
        i.parentLogoItemId != null -> image(i.parentLogoItemId, "Logo", i.parentLogoTag, width)
        else -> null
    }

    fun channelLogo(i: Item, width: Int = 240): String? =
        if (i.isChannel) image(i.id, tag = i.imageTags["Primary"], width = width)
        else i.channelId?.let { image(it, tag = i.channelImageTag, width = width) }

    // ---------- What this device can play ----------

    private val decoders: Set<String> by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
            .flatMap { it.supportedTypes.map(String::lowercase) }.toSet()
    }

    /**
     * Tells Jellyfin which video and audio formats this phone/TV can play itself. Anything else
     * gets converted on the server (HLS, H.264 + AAC), which every Android device can play.
     */
    fun deviceProfile(maxBitrate: Int): JsonObject {
        val video = buildList {
            add("h264")
            if ("video/hevc" in decoders) add("hevc")
            if ("video/x-vnd.on2.vp9" in decoders) add("vp9")
            if ("video/av01" in decoders) add("av1")
            add("mpeg4"); add("vp8")
        }
        @Suppress("DEPRECATION")
        val caps = androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context)
        val audio = buildList {
            add("aac"); add("mp3"); add("mp2")
            if ("audio/opus" in decoders) add("opus")
            if ("audio/flac" in decoders) add("flac")
            if ("audio/vorbis" in decoders) add("vorbis")
            if ("audio/ac3" in decoders || caps.supportsEncoding(androidx.media3.common.C.ENCODING_AC3)) add("ac3")
            if ("audio/eac3" in decoders || caps.supportsEncoding(androidx.media3.common.C.ENCODING_E_AC3)) add("eac3")
            if ("audio/vnd.dts" in decoders || caps.supportsEncoding(androidx.media3.common.C.ENCODING_DTS)) { add("dts"); add("dca") }
        }
        val surround = "ac3" in audio
        return buildJsonObject {
            put("Name", "RelliReels")
            put("MaxStreamingBitrate", maxBitrate)
            put("MaxStaticBitrate", maxBitrate)
            putJsonArray("DirectPlayProfiles") {
                addJsonObject {
                    put("Container", "mp4,m4v,mkv,webm,mov,ts,mpegts,avi")
                    put("Type", "Video")
                    put("VideoCodec", video.joinToString(","))
                    put("AudioCodec", audio.joinToString(","))
                }
            }
            putJsonArray("TranscodingProfiles") {
                addJsonObject {
                    put("Container", "ts")
                    put("Type", "Video")
                    put("VideoCodec", if ("hevc" in video) "h264,hevc" else "h264")
                    put("AudioCodec", if (surround) "aac,ac3,eac3" else "aac,mp3")
                    put("Protocol", "hls")
                    put("Context", "Streaming")
                    put("MaxAudioChannels", if (surround) "6" else "2")
                    put("MinSegments", 1)
                    put("BreakOnNonKeyFrames", true)
                }
            }
            putJsonArray("ContainerProfiles") {}
            putJsonArray("CodecProfiles") {}
            // Text subtitles come as separate WebVTT files; picture subtitles (Blu-ray PGS) get burned in.
            putJsonArray("SubtitleProfiles") {
                addJsonObject { put("Format", "vtt"); put("Method", "External") }
                addJsonObject { put("Format", "pgssub"); put("Method", "Encode") }
                addJsonObject { put("Format", "dvdsub"); put("Method", "Encode") }
            }
        }
    }

    companion object {
        const val FIELDS = "Overview,Genres,PrimaryImageAspectRatio,ChildCount,RecursiveItemCount,Taglines,DateCreated"

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
