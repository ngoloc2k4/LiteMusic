package com.litemusic.app.core.resolver

import android.util.Log
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bridges Spotify Metadata to playable streams via YouTube Music / SoundCloud
 */
class SpotifyMetadataBridge(
    private val ytResolver: YouTubeStreamResolver = YouTubeStreamResolver(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    private val tag = "SpotifyBridge"

    /**
     * Fetch tracks from a public Spotify playlist / album embed without API key
     */
    suspend fun fetchPlaylistTracks(spotifyPlaylistId: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        try {
            // Using Spotify Open Embed API
            val url = "https://open.spotify.com/oembed?url=https://open.spotify.com/playlist/$spotifyPlaylistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val playlistTitle = json.optString("title", "Spotify Playlist")
            Log.d(tag, "Loaded playlist: $playlistTitle")

            // In actual usage, Spotify Web API or sp_dc cookie returns the list of tracks
        } catch (e: Exception) {
            Log.e(tag, "Failed to load Spotify playlist $spotifyPlaylistId", e)
        }
        songs
    }

    /**
     * Resolves a Spotify track by searching its exact (Artist + Title) on YouTube Music
     */
    suspend fun resolveTrackToPlayableStream(spotifySong: Song): String? = withContext(Dispatchers.IO) {
        val query = "${spotifySong.artist} - ${spotifySong.title}"
        Log.d(tag, "Resolving Spotify song to stream via YouTube: $query")

        val searchResults = ytResolver.search(query)
        val bestMatch = searchResults.firstOrNull() ?: return@withContext null
        ytResolver.resolveStreamUrl(bestMatch)
    }
}
