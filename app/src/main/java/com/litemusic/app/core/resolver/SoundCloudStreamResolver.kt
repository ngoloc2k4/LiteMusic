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
                    val rawArt = item.optString("artwork_url").ifEmpty {
                        user?.optString("avatar_url") ?: ""
                    }
                    val artworkUrl = if (rawArt.isNotBlank()) {
                        rawArt.replace("-large", "-t500x500")
                    } else {
                        "https://a-v2.sndcdn.com/assets/images/default/avatar-large.png"
                    }
                    val cleanArtwork = if (artworkUrl.startsWith("//")) "https:$artworkUrl" else artworkUrl
                    val duration = item.optLong("duration", 0L) / 1000

                    // Prioritize progressive streams over HLS for faster and more reliable playback
                    val media = item.optJSONObject("media")
                    val transcodings = media?.optJSONArray("transcodings")
                    val candidateUrls = mutableListOf<String>()

                    if (transcodings != null) {
                        // First pass: progressive protocol
                        for (j in 0 until transcodings.length()) {
                            val trans = transcodings.optJSONObject(j) ?: continue
                            val format = trans.optJSONObject("format")?.optString("protocol")
                            val u = trans.optString("url")
                            if (format == "progressive" && u.isNotEmpty()) {
                                candidateUrls.add(u)
                            }
                        }
                        // Second pass: hls protocol
                        for (j in 0 until transcodings.length()) {
                            val trans = transcodings.optJSONObject(j) ?: continue
                            val format = trans.optJSONObject("format")?.optString("protocol")
                            val u = trans.optString("url")
                            if (format == "hls" && u.isNotEmpty() && !candidateUrls.contains(u)) {
                                candidateUrls.add(u)
                            }
                        }
                    }

                    val sourceIdentifier = if (candidateUrls.isNotEmpty()) {
                        candidateUrls.joinToString("|")
                    } else {
                        id
                    }

                    results.add(
                        Song(
                            id = "sc_$id",
                            title = title,
                            artist = artist,
                            durationSeconds = duration,
                            thumbnailUrl = cleanArtwork,
                            source = AudioSource.SOUNDCLOUD,
                            sourceId = sourceIdentifier
                        )
                    )
                }

                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                Log.e(tag, "SoundCloud search attempt error with client ID $cId", e)
                rotateClientId()
            }
        }

        // Seamless fallback if SoundCloud API fails or returns no tracks
        if (results.isEmpty() && query.isNotBlank()) {
            try {
                val fallback = YouTubeStreamResolver().search("$query soundcloud")
                for (s in fallback) {
                    results.add(
                        s.copy(
                            id = "sc_${s.sourceId}",
                            source = AudioSource.SOUNDCLOUD
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        results
    }

    override suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val rawSourceId = song.sourceId.removePrefix("sc_")
        if (rawSourceId.length == 11 && !rawSourceId.contains(":") && !rawSourceId.startsWith("http")) {
            val ytSong = song.copy(id = rawSourceId, sourceId = rawSourceId, source = AudioSource.YOUTUBE_MUSIC)
            val stream = YouTubeStreamResolver().resolveStreamUrl(ytSong)
            if (stream != null) return@withContext stream
        }

        val candidates = if (rawSourceId.contains("http")) {
            rawSourceId.split("|").filter { it.startsWith("http") }
        } else {
            emptyList()
        }

        // Try direct transcodings first
        for (candidateUrl in candidates) {
            for (attempt in 0 until 2) {
                val cId = getClientId()
                try {
                    val delimiter = if (candidateUrl.contains("?")) "&" else "?"
                    val url = "$candidateUrl${delimiter}client_id=$cId"
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
                    val directUrl = json.optString("url", null)
                    if (!directUrl.isNullOrEmpty()) {
                        Log.i(tag, "Resolved SoundCloud stream for ${song.title} via $candidateUrl")
                        return@withContext directUrl
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Error resolving SoundCloud transcode $candidateUrl", e)
                    rotateClientId()
                }
            }
        }

        // Fallback: If candidates failed or rawSourceId is a track ID (sc_123 or 123)
        val numericTrackId = song.id.removePrefix("sc_").ifEmpty { rawSourceId }
        if (numericTrackId.all { it.isDigit() }) {
            for (attempt in clientIds.indices) {
                val cId = getClientId()
                try {
                    val trackUrl = "https://api-v2.soundcloud.com/tracks/$numericTrackId?client_id=$cId"
                    val request = Request.Builder()
                        .url(trackUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.code == 401 || response.code == 403) {
                        rotateClientId()
                        continue
                    }
                    val body = response.body?.string() ?: continue
                    val json = JSONObject(body)
                    val media = json.optJSONObject("media")
                    val transcodings = media?.optJSONArray("transcodings") ?: continue

                    for (j in 0 until transcodings.length()) {
                        val trans = transcodings.optJSONObject(j) ?: continue
                        val transUrl = trans.optString("url")
                        if (transUrl.isEmpty()) continue

                        val transDelimiter = if (transUrl.contains("?")) "&" else "?"
                        val streamReq = Request.Builder()
                            .url("$transUrl${transDelimiter}client_id=$cId")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                        val streamResp = client.newCall(streamReq).execute()
                        if (!streamResp.isSuccessful) continue
                        val streamBody = streamResp.body?.string() ?: continue
                        val directUrl = JSONObject(streamBody).optString("url", null)
                        if (!directUrl.isNullOrEmpty()) {
                            Log.i(tag, "Resolved SoundCloud stream via track fallback for ${song.title}")
                            return@withContext directUrl
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error during SoundCloud track fallback for $numericTrackId", e)
                    rotateClientId()
                }
            }
        }

        Log.e(tag, "Failed to resolve any SoundCloud stream for ${song.title} (${song.id})")
        null
    }
}
