package com.relli.radio.playback

import com.relli.radio.RelliApp
import com.relli.radio.data.Song

/** Ready-made mixes, used by the Home screen, the More actions sheet, and Android Auto. */
object Mixes {
    private val api get() = RelliApp.api

    data class Mix(val name: String, val songs: List<Song>)

    suspend fun track(): Mix = Mix("Track mix", api.randomSongs(80))

    suspend fun album(): Mix {
        val albums = api.albumList("random", size = 6)
        val songs = albums.flatMap { runCatching { api.album(it.id).song }.getOrDefault(emptyList()) }
        return Mix("Album mix", songs.shuffled())
    }

    suspend fun decade(): Mix {
        val year = api.randomSongs(40).mapNotNull { it.year }.randomOrNull() ?: return track()
        val from = year / 10 * 10
        val songs = api.randomSongs(60, fromYear = from, toYear = from + 9)
        return if (songs.size < 5) track() else Mix("${from}s mix", songs)
    }

    suspend fun genre(name: String): Mix = Mix("$name mix", api.randomSongs(60, genre = name))

    suspend fun favorites(): Mix = Mix("Favorites", api.starred().songs.shuffled())

    /** Songs like this one: Navidrome's similar songs, topped up with the same artist and genre. */
    suspend fun instant(song: Song): Mix {
        var songs = api.similarSongs(song.id, 40)
        if (songs.size < 5) {
            val own = song.artistId?.let { id ->
                runCatching { api.artist(id).album.take(6).flatMap { api.album(it.id).song } }.getOrDefault(emptyList())
            } ?: emptyList()
            val genre = api.randomSongs(25, genre = song.genre)
            songs = (own + genre).filter { it.id != song.id }.shuffled().take(40)
        }
        return Mix("Instant mix: ${song.title}", listOf(song) + songs.filter { it.id != song.id })
    }

    /** More songs for Smart queue, matching the current song by the chosen mode. */
    suspend fun more(mode: String, seed: Song?, exclude: Set<String>): List<Song> {
        val pool = when (mode) {
            "genre" -> api.randomSongs(30, genre = seed?.genre)
            "artist" -> seed?.artistId?.let { id ->
                runCatching { api.artist(id).album.flatMap { api.album(it.id).song } }.getOrDefault(emptyList())
            } ?: api.randomSongs(30)
            "similar" -> seed?.let { api.similarSongs(it.id, 30) }?.takeIf { it.isNotEmpty() } ?: api.randomSongs(30, genre = seed?.genre)
            else -> api.randomSongs(30)
        }
        return pool.filter { it.id !in exclude }.shuffled().take(10)
    }
}
