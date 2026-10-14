package com.litemusic.app.core.resolver

import android.util.Log
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SoundCloudStreamResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : StreamResolver {

    private val tag = "SoundCloudResolver"
    // Public mobile web client ID
    private var clientId = "2t9loNfh0ekOfbnFeO3wEiHGMIWqDUud"

    override suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$clientId&limit=25"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val collection = json.optJSONArray("collection") ?: return@withContext emptyList()

            for (i in 0 until collection.length()) {
                val item = collection.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L).toString()
                val title = item.optString("title", "Unknown")
                val user = item.optJSONObject("user")
                val artist = user?.optString("username", "SoundCloud Artist") ?: "SoundCloud Artist"
                val artworkUrl = item.optString("artwork_url", "").replace("-large", "-t500x500")
                val duration = item.optLong("duration", 0L) / 1000

                // Get progressive or hls streaming url from media.transcodings
                val media = item.optJSONObject("media")
                val transcodings = media?.optJSONArray("transcodings")
                var streamApiUrl: String? = null

                if (transcodings != null) {
                    for (j in 0 until transcodings.length()) {
                        val trans = transcodings.optJSONObject(j) ?: continue
                        val format = trans.optJSONObject("format")?.optString("protocol")
                        if (format == "progressive" || format == "hls") {
                            streamApiUrl = trans.optString("url")
                            break
                        }
                    }
                }

                results.add(
                    Song(
                        id = "sc_$id",
                        title = title,
                        artist = artist,
                        durationSeconds = duration,
                        thumbnailUrl = artworkUrl,
                        source = AudioSource.SOUNDCLOUD,
                        sourceId = streamApiUrl ?: id
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "SoundCloud search error: $query", e)
        }
        results
    }

    override suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val streamApiUrl = song.sourceId
        if (!streamApiUrl.startsWith("http")) return@withContext null

        try {
            val url = "$streamApiUrl?client_id=$clientId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            return@withContext json.optString("url", null)
        } catch (e: Exception) {
            Log.e(tag, "Error resolving SoundCloud stream for: ${song.title}", e)
            null
        }
    }
}
