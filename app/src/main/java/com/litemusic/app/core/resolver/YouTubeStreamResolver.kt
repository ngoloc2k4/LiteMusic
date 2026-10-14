package com.litemusic.app.core.resolver

import android.util.Log
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class YouTubeStreamResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : StreamResolver {

    private val tag = "YTStreamResolver"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // InnerTube Web Client configuration for YouTube Music
    private val innerTubeUrl = "https://music.youtube.com/youtubei/v1"
    private val playerUrl = "https://www.youtube.com/youtubei/v1/player"

    override suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        try {
            val payload = JSONObject().apply {
                put("context", getInnerTubeContext())
                put("query", query)
                put("params", "Eg-KAQwIABAAGAAgACgAMABqChAEEAMQCRAFEAo%3D") // Music songs filter
            }

            val request = Request.Builder()
                .url("$innerTubeUrl/search")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)

            // Parse search contents
            val sectionList = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (sectionList != null) {
                for (i in 0 until sectionList.length()) {
                    val shelf = sectionList.optJSONObject(i)?.optJSONObject("musicShelfRenderer")
                    val items = shelf?.optJSONArray("contents") ?: continue
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val flexColumns = item.optJSONArray("flexColumns") ?: continue

                        // Extract Title
                        val titleRuns = flexColumns.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")
                        val title = titleRuns?.optJSONObject(0)?.optString("text") ?: "Unknown"

                        // Extract Artist & Duration
                        val subtitleRuns = flexColumns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")
                        val artist = subtitleRuns?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"

                        // Extract Video/Audio ID
                        val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                            ?: item.optJSONObject("doubleTapCommand")
                                ?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: continue

                        // Extract Thumbnail
                        val thumbnails = item.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""

                        results.add(
                            Song(
                                id = videoId,
                                title = title,
                                artist = artist,
                                thumbnailUrl = thumbUrl,
                                source = AudioSource.YOUTUBE_MUSIC,
                                sourceId = videoId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Search error for query: $query", e)
        }
        results
    }

    override suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val videoId = song.sourceId
        try {
            // Request direct streaming format via InnerTube Android Client (bypasses cipher signatures)
            val payload = JSONObject().apply {
                put("context", getAndroidContext())
                put("videoId", videoId)
            }

            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", "com.google.android.youtube/19.29.35 (Linux; U; Android 14)")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val streamingData = json.optJSONObject("streamingData") ?: return@withContext null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return@withContext null

            // Prioritize Opus audio format (approx 160kbps, mimeType audio/webm)
            var chosenUrl: String? = null
            var highestBitrate = 0

            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType")
                if (mimeType.contains("audio/webm") || mimeType.contains("audio/mp4")) {
                    val bitrate = format.optInt("bitrate", 0)
                    val url = format.optString("url")
                    if (url.isNotEmpty() && bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        chosenUrl = url
                    }
                }
            }

            return@withContext chosenUrl
        } catch (e: Exception) {
            Log.e(tag, "Failed to resolve stream for ${song.title} ($videoId)", e)
            null
        }
    }

    private fun getInnerTubeContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20241001.01.00")
                put("hl", "vi")
                put("gl", "VN")
            })
        }
    }

    private fun getAndroidContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "19.29.35")
                put("androidSdkVersion", 34)
                put("hl", "vi")
                put("gl", "VN")
            })
        }
    }
}
