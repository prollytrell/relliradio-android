package com.relli.reels.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Jellyfin counts time in "ticks": 10 million per second. */
const val TICKS_PER_MS = 10_000L

fun ticksToMs(t: Long?) = (t ?: 0L) / TICKS_PER_MS

@Serializable
data class UserData(
    @SerialName("PlaybackPositionTicks") val positionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedCount: Int? = null,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

@Serializable
data class Person(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val imageTag: String? = null,
)

@Serializable
data class MediaStream(
    @SerialName("Type") val type: String = "",
    @SerialName("Index") val index: Int = 0,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsTextSubtitleStream") val isText: Boolean = false,
    @SerialName("DeliveryMethod") val deliveryMethod: String? = null,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
) {
    val label: String get() = displayTitle ?: title ?: language ?: "Track ${index + 1}"
}

@Serializable
data class Item(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("CriticRating") val criticRating: Double? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("RecursiveItemCount") val recursiveCount: Int? = null,
    // TV
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    // Images
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropTags: List<String> = emptyList(),
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbTag: String? = null,
    @SerialName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerialName("ParentLogoImageTag") val parentLogoTag: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryTag: String? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryAspect: Double? = null,
    @SerialName("UserData") val userData: UserData? = null,
    @SerialName("People") val people: List<Person> = emptyList(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList(),
    // Live TV
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("ChannelId") val channelId: String? = null,
    @SerialName("ChannelName") val channelName: String? = null,
    @SerialName("ChannelPrimaryImageTag") val channelImageTag: String? = null,
    @SerialName("CurrentProgram") val currentProgram: Item? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EpisodeTitle") val episodeTitle: String? = null,
    @SerialName("IsMovie") val isMovie: Boolean? = null,
    @SerialName("IsSeries") val isSeries: Boolean? = null,
) {
    val isSeriesItem get() = type == "Series"
    val isEpisode get() = type == "Episode"
    val isChannel get() = type == "TvChannel"
    val runtimeMs get() = ticksToMs(runTimeTicks)
    val positionMs get() = ticksToMs(userData?.positionTicks)
    val progress: Float
        get() = (userData?.playedPercentage?.toFloat()?.div(100f))
            ?: if (runTimeTicks != null && runTimeTicks > 0 && userData != null) userData.positionTicks.toFloat() / runTimeTicks else 0f

    /** "S1:E4" for episodes. */
    val episodeCode: String?
        get() = if (indexNumber != null) "S${parentIndexNumber ?: 1}:E$indexNumber" else null
}

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<Item> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0,
)

@Serializable
data class MediaSource(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val directPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val directStream: Boolean = false,
    @SerialName("SupportsTranscoding") val transcoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val streams: List<MediaStream> = emptyList(),
    @SerialName("DefaultAudioStreamIndex") val defaultAudio: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitle: Int? = null,
)

@Serializable
data class PlaybackInfo(
    @SerialName("MediaSources") val sources: List<MediaSource> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("ErrorCode") val errorCode: String? = null,
)

@Serializable
data class Segment(
    @SerialName("Type") val type: String = "",
    @SerialName("StartTicks") val startTicks: Long = 0,
    @SerialName("EndTicks") val endTicks: Long = 0,
)

@Serializable
data class SegmentsResult(@SerialName("Items") val items: List<Segment> = emptyList())

@Serializable
data class AuthUser(@SerialName("Id") val id: String, @SerialName("Name") val name: String = "")

@Serializable
data class AuthResult(
    @SerialName("User") val user: AuthUser,
    @SerialName("AccessToken") val token: String,
)

@Serializable
data class PublicInfo(
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("Id") val id: String? = null,
)

@Serializable
data class QuickConnect(
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String = "",
    @SerialName("Authenticated") val authenticated: Boolean = false,
)

@Serializable
data class Genre(@SerialName("Id") val id: String = "", @SerialName("Name") val name: String = "")

@Serializable
data class GenresResult(@SerialName("Items") val items: List<Genre> = emptyList())
