package com.relli.radio.data

import kotlinx.serialization.Serializable

// Shapes of the data Navidrome sends back (OpenSubsonic API). Unknown fields are ignored.

@Serializable
data class ReplayGain(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
)

@Serializable
data class ArtistRef(val id: String = "", val name: String = "")

@Serializable
data class Song(
    val id: String,
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val artists: List<ArtistRef>? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val size: Long? = null,
    val playCount: Long? = null,
    val starred: String? = null,
    val userRating: Int? = null,
    val replayGain: ReplayGain? = null,
    val path: String? = null,
) {
    val artistList: List<ArtistRef>
        get() = artists?.filter { it.id.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(artistId?.let { ArtistRef(it, artist ?: "") })
}

@Serializable
data class Album(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val playCount: Long? = null,
    val starred: String? = null,
    val song: List<Song> = emptyList(),
)

@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val album: List<Album> = emptyList(),
)

@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val comment: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val readonly: Boolean = false,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class Genre(val value: String = "", val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class RadioStation(val id: String, val name: String = "", val streamUrl: String = "", val homePageUrl: String? = null)

data class Starred(val songs: List<Song>, val albums: List<Album>, val artists: List<Artist>)
data class SearchResult(val artists: List<Artist>, val albums: List<Album>, val songs: List<Song>)
data class LyricLine(val startMs: Long?, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>)
data class SavedQueue(val songs: List<Song>, val currentId: String?, val positionMs: Long)
