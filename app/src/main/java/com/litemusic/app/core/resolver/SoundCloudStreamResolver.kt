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
    
    // Public SoundCloud web client IDs with automatic fallback rotation
    private val clientIds = listOf(
        "kR7dAfT5UXQgmgde1pLjfsRGiaoANLvT",
        "2t9loNMn90VaAG10D8WYu5gahQIvdqYT",
        "bJv1YvV85fU7X9yBwM1F91j15RzRj83a",
        "iZIs9mchVcX5lhVRjh3ZqbUaegaQQjhg"
    )
    private var currentClientIndex = 0

    @Synchronized
    private fun getClientId(): String = clientIds[currentClientIndex % clientIds.size]

    @Synchronized
    private fun rotateClientId() {
        currentClientIndex = (currentClientIndex + 1) % clientIds.size
        Log.i(tag, "Rotated SoundCloud client ID to index $currentClientIndex")
    }

    suspend fun getTrending(): List<Song> {
        return search("Top Trending Vietnam")
    }

    override suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (attempt in clientIds.indices) {
            val cId = getClientId()
            val url = "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$cId&limit=25"

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401 || response.code == 403) {
                    rotateClientId()
                    continue
                }

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val collection = json.optJSONArray("collection") ?: continue

                for (i in 0 until collection.length()) {
                    val item = collection.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L).toString()
                    val title = item.optString("title", "Unknown")
                    val user = item.optJSONObject("user")
                    val artist = user?.optString("username", "SoundCloud Artist") ?: "SoundCloud Artist"
                    val artworkUrl = item.optString("artwork_url", "")
                        .replace("-large", "-t500x500")
                    val duration = item.optLong("duration", 0L) / 1000

                    // Prioritize progressive streams over HLS for faster and more reliable playback
                    val media = item.optJSONObject("media")
                    val transcodings = media?.optJSONArray("transcodings")
                    var streamApiUrl: String? = null

                    if (transcodings != null) {
                        // First pass: progressive protocol
                        for (j in 0 until transcodings.length()) {
                            val trans = transcodings.optJSONObject(j) ?: continue
                            val format = trans.optJSONObject("format")?.optString("protocol")
                            if (format == "progressive") {
                                streamApiUrl = trans.optString("url")
                                break
                            }
                        }
                        // Second pass: fallback to hls if no progressive
                        if (streamApiUrl == null) {
                            for (j in 0 until transcodings.length()) {
                                val trans = transcodings.optJSONObject(j) ?: continue
                                val format = trans.optJSONObject("format")?.optString("protocol")
                                if (format == "hls") {
                                    streamApiUrl = trans.optString("url")
                                    break
                                }
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

                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                Log.e(tag, "SoundCloud search attempt error with client ID $cId", e)
                rotateClientId()
            }
        }
        results
    }

    override suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val streamApiUrl = song.sourceId
        if (!streamApiUrl.startsWith("http")) return@withContext null

        for (attempt in clientIds.indices) {
            val cId = getClientId()
            try {
                val url = "$streamApiUrl?client_id=$cId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401 || response.code == 403) {
                    rotateClientId()
                    continue
                }

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val directUrl = json.optString("url", null)
                if (!directUrl.isNullOrEmpty()) {
                    return@withContext directUrl
                }
            } catch (e: Exception) {
                Log.e(tag, "Error resolving SoundCloud stream (attempt $attempt) for: ${song.title}", e)
                rotateClientId()
            }
        }
        null
    }
}
