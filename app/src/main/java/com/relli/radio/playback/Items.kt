package com.relli.radio.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.relli.radio.RelliApp
import com.relli.radio.data.Album
import com.relli.radio.data.ArtistRef
import com.relli.radio.data.Playlist
import com.relli.radio.data.RadioStation
import com.relli.radio.data.ReplayGain
import com.relli.radio.data.Song

// Converting between Navidrome songs and the MediaItems the player works with.
// Song details ride along in the metadata "extras", so any screen (or Android Auto)
// can rebuild the Song from what's playing.

private const val K_ALBUM_ID = "albumId"
private const val K_ARTIST_ID = "artistId"
private const val K_ARTISTS = "artists"
private const val K_GENRE = "genre"
private const val K_YEAR = "year"
private const val K_SUFFIX = "suffix"
private const val K_BITRATE = "bitRate"
private const val K_SAMPLING = "samplingRate"
private const val K_BITDEPTH = "bitDepth"
private const val K_TRACK_GAIN = "trackGain"
private const val K_ALBUM_GAIN = "albumGain"
private const val K_STARRED = "starred"
private const val K_RATING = "rating"
private const val K_COVER = "coverArt"
private const val K_DURATION = "durationSec"
const val K_RADIO = "radio"
const val K_SMART = "smart"

fun Song.toMediaItem(smart: Boolean = false): MediaItem {
    val api = RelliApp.api
    val extras = Bundle().apply {
        putString(K_ALBUM_ID, albumId)
        putString(K_ARTIST_ID, artistId)
        putStringArray(K_ARTISTS, artistList.map { "${it.id}\u0001${it.name}" }.toTypedArray())
        putString(K_GENRE, genre)
        putInt(K_YEAR, year ?: 0)
        putString(K_SUFFIX, suffix)
        putInt(K_BITRATE, bitRate ?: 0)
        putInt(K_SAMPLING, samplingRate ?: 0)
        putInt(K_BITDEPTH, bitDepth ?: 0)
        replayGain?.trackGain?.let { putDouble(K_TRACK_GAIN, it) }
        replayGain?.albumGain?.let { putDouble(K_ALBUM_GAIN, it) }
        putBoolean(K_STARRED, starred != null)
        putInt(K_RATING, userRating ?: 0)
        putString(K_COVER, coverArt ?: albumId)
        putInt(K_DURATION, duration)
        putBoolean(K_SMART, smart)
    }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(artist)
        .setGenre(genre)
        .setTrackNumber(track)
        .setDiscNumber(discNumber)
        .setReleaseYear(year)
        .setArtworkUri(api.coverUrl(coverArt ?: albumId, 600)?.let(Uri::parse))
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(api.streamUrl(id, RelliApp.instance.currentMaxBitRate()))
        .setMediaMetadata(meta)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(api.streamUrl(id, RelliApp.instance.currentMaxBitRate()))).build())
        .build()
}

fun RadioStation.toMediaItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(name)
        .setArtist("Internet radio")
        .setStation(name)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        .setExtras(Bundle().apply { putBoolean(K_RADIO, true) })
        .build()
    return MediaItem.Builder()
        .setMediaId("radio:$id")
        .setUri(streamUrl)
        .setMediaMetadata(meta)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(streamUrl)).build())
        .build()
}

/** Rebuilds the Song from a MediaItem (only the fields RelliRadio stores). */
fun MediaItem.toSong(): Song {
    val m = mediaMetadata
    val e = m.extras ?: Bundle()
    val artists = e.getStringArray(K_ARTISTS)?.mapNotNull {
        val p = it.split('\u0001'); if (p.size == 2 && p[0].isNotEmpty()) ArtistRef(p[0], p[1]) else null
    }
    val tg = if (e.containsKey(K_TRACK_GAIN)) e.getDouble(K_TRACK_GAIN) else null
    val ag = if (e.containsKey(K_ALBUM_GAIN)) e.getDouble(K_ALBUM_GAIN) else null
    return Song(
        id = mediaId,
        title = m.title?.toString() ?: "",
        album = m.albumTitle?.toString(),
        albumId = e.getString(K_ALBUM_ID),
        artist = m.artist?.toString(),
        artistId = e.getString(K_ARTIST_ID),
        artists = artists,
        coverArt = e.getString(K_COVER),
        duration = e.getInt(K_DURATION),
        track = m.trackNumber,
        year = e.getInt(K_YEAR).takeIf { it > 0 },
        genre = e.getString(K_GENRE),
        suffix = e.getString(K_SUFFIX),
        bitRate = e.getInt(K_BITRATE).takeIf { it > 0 },
        samplingRate = e.getInt(K_SAMPLING).takeIf { it > 0 },
        bitDepth = e.getInt(K_BITDEPTH).takeIf { it > 0 },
        starred = if (e.getBoolean(K_STARRED)) "yes" else null,
        userRating = e.getInt(K_RATING).takeIf { it > 0 },
        replayGain = if (tg != null || ag != null) ReplayGain(trackGain = tg, albumGain = ag) else null,
    )
}

val MediaItem.isRadio: Boolean get() = mediaMetadata.extras?.getBoolean(K_RADIO) == true || mediaId.startsWith("radio:")
val MediaItem.isSmart: Boolean get() = mediaMetadata.extras?.getBoolean(K_SMART) == true

/** Browsable folders for Android Auto. */
fun folder(id: String, title: String, subtitle: String? = null, art: String? = null, playable: Boolean = false): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(art?.let { RelliApp.api.coverUrl(it, 300) }?.let(Uri::parse))
                .setIsBrowsable(!playable)
                .setIsPlayable(playable)
                .setMediaType(if (playable) MediaMetadata.MEDIA_TYPE_PLAYLIST else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        ).build()

fun Album.toFolder(): MediaItem = MediaItem.Builder()
    .setMediaId("album:$id")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name).setArtist(artist).setSubtitle(artist)
            .setArtworkUri(RelliApp.api.coverUrl(coverArt ?: id, 300)?.let(Uri::parse))
            .setIsBrowsable(true).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .build()
    ).build()

fun Playlist.toFolder(): MediaItem = MediaItem.Builder()
    .setMediaId("playlist:$id")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name).setSubtitle("$songCount songs")
            .setArtworkUri(RelliApp.api.coverUrl(coverArt ?: id, 300)?.let(Uri::parse))
            .setIsBrowsable(true).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .build()
    ).build()

/** "Lossless", "Hi-Res lossless", "MP3 320 kbps"... for the seek bar label. */
fun qualityLabel(song: Song, maxBitRate: Int): String {
    val lossless = setOf("flac", "alac", "wav", "aiff", "aif", "ape", "wv", "dsf", "dff")
    val suffix = song.suffix?.lowercase() ?: ""
    if (maxBitRate > 0 && (suffix in lossless || (song.bitRate ?: 0) > maxBitRate)) return "MP3 $maxBitRate kbps"
    if (suffix in lossless) return if ((song.bitDepth ?: 16) > 16 || (song.samplingRate ?: 44100) > 48000) "Hi-Res lossless" else "Lossless"
    return listOfNotNull(suffix.uppercase().takeIf { it.isNotEmpty() }, song.bitRate?.let { "$it kbps" }).joinToString(" ")
}
