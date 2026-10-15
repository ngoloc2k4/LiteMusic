package com.litemusic.app.core.library

import android.content.Context
import android.content.SharedPreferences
import com.litemusic.app.core.model.Song
import org.json.JSONArray
import org.json.JSONObject

class LibraryManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("litemusic_library_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: LibraryManager? = null

        fun getInstance(context: Context): LibraryManager {
            return instance ?: synchronized(this) {
                instance ?: LibraryManager(context).also { instance = it }
            }
        }
    }

    // --- FAVORITES ---
    fun getFavorites(): List<Song> {
        val json = prefs.getString("favorites_json", "[]") ?: "[]"
        return parseSongList(json)
    }

    fun isFavorite(songId: String): Boolean {
        return getFavorites().any { it.id == songId }
    }

    fun toggleFavorite(song: Song): Boolean {
        val current = getFavorites().toMutableList()
        val index = current.indexOfFirst { it.id == song.id }
        val isNowFav: Boolean
        if (index >= 0) {
            current.removeAt(index)
            isNowFav = false
        } else {
            current.add(0, song)
            isNowFav = true
        }
        prefs.edit().putString("favorites_json", serializeSongList(current)).apply()
        return isNowFav
    }

    // --- RECENTLY PLAYED ---
    fun getRecentlyPlayed(): List<Song> {
        val json = prefs.getString("recent_json", "[]") ?: "[]"
        return parseSongList(json)
    }

    fun addToRecent(song: Song) {
        val current = getRecentlyPlayed().toMutableList()
        current.removeAll { it.id == song.id }
        current.add(0, song)
        if (current.size > 100) {
            current.removeAt(current.lastIndex)
        }
        prefs.edit().putString("recent_json", serializeSongList(current)).apply()
    }

    fun clearRecent() {
        prefs.edit().remove("recent_json").apply()
    }

    // --- BLOCKLIST (SONGS & ARTISTS) ---
    fun getBlockedSongIds(): Set<String> {
        return prefs.getStringSet("blocked_song_ids", emptySet()) ?: emptySet()
    }

    fun getBlockedSongTitles(): Map<String, String> {
        val json = prefs.getString("blocked_song_titles", "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
        } catch (_: Exception) {}
        return map
    }

    fun getBlockedArtists(): Set<String> {
        return prefs.getStringSet("blocked_artists", emptySet()) ?: emptySet()
    }

    fun blockSong(song: Song) {
        val set = getBlockedSongIds().toMutableSet().apply { add(song.id) }
        val titles = getBlockedSongTitles().toMutableMap().apply { put(song.id, "${song.title} • ${song.artist}") }
        prefs.edit()
            .putStringSet("blocked_song_ids", set)
            .putString("blocked_song_titles", JSONObject(titles as Map<*, *>).toString())
            .apply()
    }

    fun unblockSong(songId: String) {
        val set = getBlockedSongIds().toMutableSet().apply { remove(songId) }
        val titles = getBlockedSongTitles().toMutableMap().apply { remove(songId) }
        prefs.edit()
            .putStringSet("blocked_song_ids", set)
            .putString("blocked_song_titles", JSONObject(titles as Map<*, *>).toString())
            .apply()
    }

    fun blockArtist(artistName: String) {
        val cleanName = artistName.trim()
        if (cleanName.isEmpty()) return
        val set = getBlockedArtists().toMutableSet().apply { add(cleanName) }
        prefs.edit().putStringSet("blocked_artists", set).apply()
    }

    fun unblockArtist(artistName: String) {
        val set = getBlockedArtists().toMutableSet().apply { remove(artistName) }
        prefs.edit().putStringSet("blocked_artists", set).apply()
    }

    fun isSongBlocked(song: Song): Boolean {
        if (getBlockedSongIds().contains(song.id)) return true
        val artists = getBlockedArtists()
        val songArtist = song.artist.trim().lowercase()
        return artists.any { it.trim().lowercase() == songArtist }
    }

    fun filterBlocked(songs: List<Song>): List<Song> {
        val blockedIds = getBlockedSongIds()
        val blockedArtists = getBlockedArtists().map { it.trim().lowercase() }.toSet()
        return songs.filterNot { song ->
            blockedIds.contains(song.id) || blockedArtists.contains(song.artist.trim().lowercase())
        }
    }

    // --- JSON HELPERS ---
    private fun serializeSongList(songs: List<Song>): String {
        val arr = JSONArray()
        for (song in songs) {
            val obj = JSONObject().apply {
                put("id", song.id)
                put("title", song.title)
                put("artist", song.artist)
                put("durationSeconds", song.durationSeconds)
                put("thumbnailUrl", song.thumbnailUrl)
                put("source", song.source.name)
                put("sourceId", song.sourceId)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseSongList(json: String): List<Song> {
        val list = mutableListOf<Song>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sourceName = obj.optString("source", "YOUTUBE_MUSIC")
                val source = try {
                    com.litemusic.app.core.model.AudioSource.valueOf(sourceName)
                } catch (_: Exception) {
                    com.litemusic.app.core.model.AudioSource.YOUTUBE_MUSIC
                }
                list.add(
                    Song(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        source = source,
                        sourceId = obj.optString("sourceId", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
