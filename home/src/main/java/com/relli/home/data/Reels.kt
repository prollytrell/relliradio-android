package com.relli.home.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.relli.home.HomeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Your Jellyfin rows. RelliHome borrows the sign-in from RelliReels (through a door only your own
 * apps can open), and tapping something hands it back to RelliReels to play.
 */
object Reels {
    const val PKG = "com.relli.reels"
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    data class Session(val server: String, val userId: String, val token: String, val deviceId: String, val userName: String)

    sealed interface State {
        data object NotInstalled : State
        data object SignedOut : State
        data class Error(val message: String) : State
        data class Ready(val rows: Rows) : State
    }

    data class Rows(
        val featured: List<Item>,
        val resume: List<Item>,
        val nextUp: List<Item>,
        val onNow: List<Item>,
        val latest: List<Item>,
        val session: Session,
    )

    fun session(context: Context): Session? = try {
        context.contentResolver.query(Uri.parse("content://com.relli.reels.session"), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else Session(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
        }
    } catch (e: Exception) { null }

    private suspend fun get(s: Session, path: String, params: Map<String, Any?> = emptyMap()): String = withContext(Dispatchers.IO) {
        val b = (s.server.trimEnd('/') + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        val req = Request.Builder().url(b.build())
            .header("Authorization", "MediaBrowser Client=\"RelliReels\", Device=\"RelliHome\", DeviceId=\"${s.deviceId}\", Version=\"1.0\", Token=\"${s.token}\"")
            .header("Accept", "application/json").build()
        HomeApp.instance.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception(if (r.code == 401) "Sign in to RelliReels again." else "Jellyfin answered ${r.code}.")
            r.body?.string().orEmpty()
        }
    }

    private const val FIELDS = "Overview,Genres,PrimaryImageAspectRatio,ChildCount"

    suspend fun load(context: Context): State {
        if (!Apps.installed(context, PKG)) return State.NotInstalled
        val s = session(context) ?: return State.SignedOut
        return try {
            coroutineScope {
                val u = s.userId
                val resume = async { runCatching { json.decodeFromString<ItemsResult>(get(s, "/Users/$u/Items/Resume", mapOf("Limit" to 16, "MediaTypes" to "Video", "Fields" to FIELDS))).items }.getOrDefault(emptyList()) }
                val next = async { runCatching { json.decodeFromString<ItemsResult>(get(s, "/Shows/NextUp", mapOf("UserId" to u, "Limit" to 16, "Fields" to FIELDS, "EnableResumable" to false))).items }.getOrDefault(emptyList()) }
                val latest = async {
                    runCatching {
                        json.decodeFromString<ItemsResult>(get(s, "/Users/$u/Items", mapOf("IncludeItemTypes" to "Movie,Series", "Recursive" to true,
                            "SortBy" to "DateCreated,SortName", "SortOrder" to "Descending", "Limit" to 24, "Fields" to FIELDS, "ImageTypeLimit" to 1))).items
                    }.getOrDefault(emptyList())
                }
                val onNow = async {
                    runCatching {
                        json.decodeFromString<ItemsResult>(get(s, "/LiveTv/Channels", mapOf("UserId" to u, "AddCurrentProgram" to true, "Limit" to 24, "SortBy" to "SortName", "EnableUserData" to true))).items
                            .filter { it.currentProgram != null }
                    }.getOrDefault(emptyList())
                }
                val l = latest.await()
                val featured = l.filter { it.backdropTags.isNotEmpty() }.shuffled(java.util.Random(java.time.LocalDate.now().toEpochDay())).take(6)
                State.Ready(Rows(featured, resume.await(), next.await(), onNow.await(), l, s))
            }
        } catch (e: Exception) {
            State.Error(e.message ?: "Couldn't reach Jellyfin.")
        }
    }

    // ---------- Pictures ----------

    private fun image(s: Session, id: String?, type: String, tag: String?, width: Int, index: Int? = null): String? {
        if (id == null) return null
        val b = (s.server.trimEnd('/') + "/Items/$id/Images/$type" + (if (index != null) "/$index" else "")).toHttpUrl().newBuilder()
        if (tag != null) b.addQueryParameter("tag", tag)
        b.addQueryParameter("maxWidth", width.toString()).addQueryParameter("quality", "90")
        return b.build().toString()
    }

    fun backdrop(s: Session, i: Item, width: Int = 1920): String? = when {
        i.backdropTags.isNotEmpty() -> image(s, i.id, "Backdrop", i.backdropTags[0], width, 0)
        i.parentBackdropItemId != null && i.parentBackdropTags.isNotEmpty() -> image(s, i.parentBackdropItemId, "Backdrop", i.parentBackdropTags[0], width, 0)
        i.isChannel -> null
        else -> null
    }

    fun landscape(s: Session, i: Item, width: Int = 560): String? = when {
        i.isEpisode && i.imageTags["Primary"] != null -> image(s, i.id, "Primary", i.imageTags["Primary"], width)
        i.imageTags["Thumb"] != null -> image(s, i.id, "Thumb", i.imageTags["Thumb"], width)
        i.parentThumbItemId != null -> image(s, i.parentThumbItemId, "Thumb", i.parentThumbTag, width)
        else -> backdrop(s, i, width)
    }

    fun poster(s: Session, i: Item, width: Int = 320): String? = when {
        i.imageTags["Primary"] != null -> image(s, i.id, "Primary", i.imageTags["Primary"], width)
        i.seriesId != null -> image(s, i.seriesId, "Primary", i.seriesPrimaryTag, width)
        else -> image(s, i.id, "Primary", null, width)
    }

    fun logo(s: Session, i: Item): String? = when {
        i.imageTags["Logo"] != null -> image(s, i.id, "Logo", i.imageTags["Logo"], 600)
        i.parentLogoItemId != null -> image(s, i.parentLogoItemId, "Logo", i.parentLogoTag, 600)
        else -> null
    }

    fun channelLogo(s: Session, i: Item): String? = image(s, i.id, "Primary", i.imageTags["Primary"], 240)

    // ---------- Handing off to RelliReels ----------

    private fun send(context: Context, action: String, id: String) {
        try {
            context.startActivity(Intent(action).setPackage(PKG).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Apps.launch(context, PKG)
        }
    }

    fun play(context: Context, i: Item) = send(context, "com.relli.reels.action.PLAY", i.id)
    fun open(context: Context, i: Item) = send(context, "com.relli.reels.action.OPEN", i.id)
    fun live(context: Context, i: Item) = send(context, "com.relli.reels.action.LIVE", i.id)
}
