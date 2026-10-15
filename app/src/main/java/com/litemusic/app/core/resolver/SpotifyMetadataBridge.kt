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
import java.util.regex.Pattern

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
    private val nextDataPattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>")

    // Curated playlist IDs
    val defaultPlaylists = mapOf(
        "Today's Top Hits" to "37i9dQZF1DXcBWIGoYBM5M",
        "Top 50 Global" to "37i9dQZEVXbMDoHDwVN2tF",
        "Viva Latino" to "37i9dQZF1DX10zKzsJ2jva",
        "New Music Friday" to "37i9dQZF1DX4JAvHpjipBk"
    )

    /**
     * Fetch tracks from a public Spotify playlist / album embed or bridge search queries
     */
    suspend fun fetchPlaylistTracks(playlistIdOrQuery: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = playlistIdOrQuery.trim()

        // If it's a search query rather than a Spotify URL/ID, bridge directly to YouTube Music
        val isExplicitPlaylist = trimmed.contains("open.spotify.com/") ||
                trimmed.contains("spotify:playlist:") ||
                trimmed.contains("spotify:album:") ||
                (trimmed.length == 22 && trimmed.all { it.isLetterOrDigit() } && !trimmed.contains(" "))

        if (!isExplicitPlaylist && trimmed.isNotBlank()) {
            val results = ytResolver.search(trimmed)
            return@withContext results.map { song ->
                song.copy(
                    id = "sp_${song.sourceId}",
                    source = AudioSource.SPOTIFY
                )
            }
        }

        val songs = mutableListOf<Song>()
        val playlistId = extractPlaylistId(trimmed)

        try {
            val url = if (trimmed.contains("album/")) {
                val albumId = trimmed.substringAfter("album/").substringBefore("?").substringBefore("/")
                "https://open.spotify.com/embed/album/$albumId"
            } else {
                "https://open.spotify.com/embed/playlist/$playlistId"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext emptyList()

            val matcher = nextDataPattern.matcher(html)
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: return@withContext emptyList()
                val json = JSONObject(jsonStr)
                val entity = json.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity") ?: return@withContext emptyList()

                val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                val coverUrl = coverSources?.optJSONObject(0)?.optString("url") ?: ""

                val trackList = entity.optJSONArray("trackList") ?: return@withContext emptyList()
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i) ?: continue
                    val title = item.optString("title", "")
                    val subtitle = item.optString("subtitle", "Spotify Artist")
                    val durationMs = item.optLong("duration", 0L)
                    val uri = item.optString("uri", "")
                    val trackId = if (uri.isNotEmpty()) uri.substringAfterLast(":") else "sp_$i"

                    val trackCover = item.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url")
                        ?: item.optJSONObject("coverArt")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
                        ?: coverUrl

                    val cleanCover = if (trackCover.startsWith("//")) "https:$trackCover" else trackCover

                    if (title.isNotEmpty()) {
                        songs.add(
                            Song(
                                id = "sp_$trackId",
                                title = title,
                                artist = subtitle,
                                durationSeconds = durationMs / 1000,
                                thumbnailUrl = cleanCover,
                                source = AudioSource.SPOTIFY,
                                sourceId = trackId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load Spotify playlist $playlistId", e)
        }

        // Fallback to YouTube Music bridge
        if (songs.isEmpty() && trimmed.isNotBlank()) {
            val fallback = ytResolver.search(trimmed)
            return@withContext fallback.map { it.copy(id = "sp_${it.sourceId}", source = AudioSource.SPOTIFY) }
        }

        songs
    }

    private fun extractPlaylistId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("playlist/")) {
            val after = trimmed.substringAfter("playlist/")
            return after.substringBefore("?").substringBefore("/")
        }
        if (trimmed.contains("album/")) {
            val after = trimmed.substringAfter("album/")
            return after.substringBefore("?").substringBefore("/")
        }
        if (trimmed.contains("spotify:playlist:")) {
            return trimmed.substringAfter("spotify:playlist:")
        }
        if (trimmed.length == 22 && trimmed.all { it.isLetterOrDigit() }) {
            return trimmed
        }
        return "37i9dQZF1DXcBWIGoYBM5M"
    }

    /**
     * Resolves a Spotify track by searching its exact (Artist + Title) on YouTube Music
     */
    suspend fun resolveTrackToPlayableStream(spotifySong: Song): String? = withContext(Dispatchers.IO) {
        val cleanSourceId = spotifySong.sourceId.removePrefix("sp_")
        if (cleanSourceId.length == 11 && !cleanSourceId.contains(":") && !cleanSourceId.startsWith("http")) {
            val ytSong = spotifySong.copy(id = cleanSourceId, sourceId = cleanSourceId, source = AudioSource.YOUTUBE_MUSIC)
            val stream = ytResolver.resolveStreamUrl(ytSong)
            if (stream != null) return@withContext stream
        }

        val cleanTitle = spotifySong.title.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "").trim()
        val cleanArtist = spotifySong.artist.split("•", ",", "&", "feat.", "ft.")[0].trim()
        val query = if (cleanArtist.isNotEmpty() && !cleanArtist.equals("Spotify Artist", ignoreCase = true)) {
            "$cleanArtist $cleanTitle"
        } else {
            cleanTitle
        }
        Log.d(tag, "Resolving Spotify song to stream via YouTube: $query")

        val searchResults = ytResolver.search(query)
        val bestMatch = searchResults.firstOrNull() ?: return@withContext null
        ytResolver.resolveStreamUrl(bestMatch)
    }
}
