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

    private val innerTubeUrl = "https://music.youtube.com/youtubei/v1"
    private val playerUrl = "https://www.youtube.com/youtubei/v1/player"

    suspend fun getTrending(): List<Song> {
        return search("Top thịnh hành Việt Nam")
    }

    override suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        try {
            val payload = JSONObject().apply {
                put("context", getWebRemixContext())
                put("query", query)
            }

            val request = Request.Builder()
                .url("$innerTubeUrl/search")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.youtube.com/")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)

            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return@withContext emptyList()

            val sections = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (i in 0 until sections.length()) {
                val sectionObj = sections.optJSONObject(i) ?: continue

                // Check itemSectionRenderer
                val itemSection = sectionObj.optJSONObject("itemSectionRenderer")
                val shelfContents = itemSection?.optJSONArray("contents")
                    ?: sectionObj.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")

                if (shelfContents != null) {
                    for (j in 0 until shelfContents.length()) {
                        val item = shelfContents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        parseResponsiveItem(item)?.let { results.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Search error for query: $query", e)
        }
        results
    }

    /**
     * Search standard YouTube videos (podcasts, talkshows, vlogs, gaming...)
     * Converted to 'Only Nhạc' (audio-only) without requiring music tags.
     */
    suspend fun searchRegularVideos(query: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        try {
            val payload = JSONObject().apply {
                put("context", getWebContext())
                put("query", query)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)

            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i) ?: continue
                val itemSection = section.optJSONObject("itemSectionRenderer") ?: continue
                val items = itemSection.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val vr = item.optJSONObject("videoRenderer") ?: continue
                    val videoId = vr.optString("videoId")
                    if (videoId.isEmpty()) continue

                    val title = vr.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text") ?: "YouTube Video"
                    val channel = vr.optJSONObject("ownerText")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text") ?: "YouTube"
                    val lengthText = vr.optJSONObject("lengthText")?.optString("simpleText") ?: ""

                    val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                    } else {
                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    }

                    results.add(
                        Song(
                            id = "ytv_$videoId",
                            title = title,
                            artist = if (lengthText.isNotEmpty()) "$channel • $lengthText" else channel,
                            thumbnailUrl = thumbUrl,
                            source = AudioSource.YOUTUBE_VIDEO,
                            sourceId = videoId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Regular video search error for: $query", e)
        }
        results
    }

    private fun parseResponsiveItem(item: JSONObject): Song? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null

        // Extract Title
        val title = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
            ?.optJSONObject(0)?.optString("text") ?: return null

        // Extract Artist
        val artistRuns = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
        val artist = artistRuns?.optJSONObject(0)?.optString("text") ?: "YouTube Music"

        // Extract VideoId
        val overlayWatch = item.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")

        val navWatch = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")

        val videoId = overlayWatch ?: navWatch ?: return null

        // Extract High-Res Thumbnail
        val thumbs = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        return Song(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbUrl,
            source = AudioSource.YOUTUBE_MUSIC,
            sourceId = videoId
        )
    }

    private var cachedVisitorData: String? = null
    private var cachedSts: Int = 20300

    private fun refreshVisitorData(videoId: String) {
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return
            val visMatch = Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)
            val stsMatch = Regex("\"signatureTimestamp\":(\\d+)").find(html)
            visMatch?.groupValues?.get(1)?.let { cachedVisitorData = it }
            stsMatch?.groupValues?.get(1)?.toIntOrNull()?.let { cachedSts = it }
        } catch (e: Exception) {
            Log.w(tag, "Failed to refresh visitor data", e)
        }
    }

    override suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val videoId = song.sourceId
        
        // 1. Try VISIONOS InnerTube client
        val visionUrl = resolveViaVisionOs(videoId)
        if (visionUrl != null) return@withContext visionUrl

        // 2. Try ANDROID_VR InnerTube client
        val androidVrUrl = resolveAudioViaAndroidVr(videoId)
        if (androidVrUrl != null) return@withContext androidVrUrl

        // 3. Fallback to Invidious API
        val invidiousUrl = resolveViaInvidious(videoId)
        if (invidiousUrl != null) return@withContext invidiousUrl

        Log.e(tag, "All stream resolution attempts failed for ${song.title} ($videoId)")
        null
    }

    private fun resolveViaVisionOs(videoId: String): String? {
        try {
            if (cachedVisitorData == null) {
                refreshVisitorData(videoId)
            }

            val payload = JSONObject().apply {
                put("context", getVisionOsContext(cachedVisitorData))
                put("videoId", videoId)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureTimestamp", cachedSts)
                    })
                })
            }

            val reqBuilder = Request.Builder()
                .url("$playerUrl?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .header("Content-Type", "application/json")
                .header("X-Youtube-Client-Name", "101")
                .header("X-Youtube-Client-Version", "1.02")
                .header("Origin", "https://www.youtube.com")
                .post(payload.toString().toRequestBody(jsonMediaType))

            cachedVisitorData?.let { reqBuilder.header("X-Goog-Visitor-Id", it) }

            val response = client.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val status = json.optJSONObject("playabilityStatus")?.optString("status")
            if (status != "OK") {
                Log.w(tag, "VISIONOS playabilityStatus for $videoId: $status. Retrying with fresh visitor data.")
                refreshVisitorData(videoId)
            }

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

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
            return chosenUrl
        } catch (e: Exception) {
            Log.w(tag, "VISIONOS resolution error for $videoId", e)
            return null
        }
    }

    private fun resolveAudioViaAndroidVr(videoId: String): String? {
        try {
            val payload = JSONObject().apply {
                put("context", getAndroidVrContext())
                put("videoId", videoId)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                    })
                })
            }

            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; Quest 3) AppleWebKit/537.36")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            var chosenUrl: String? = null
            var highestBitrate = 0

            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType")
                if (mimeType.contains("audio/")) {
                    val bitrate = format.optInt("bitrate", 0)
                    val url = format.optString("url")
                    if (url.isNotEmpty() && bitrate > highestBitrate) {
                        highestBitrate = bitrate
                        chosenUrl = url
                    }
                }
            }
            return chosenUrl
        } catch (e: Exception) {
            Log.w(tag, "ANDROID_VR audio resolution error for $videoId", e)
            return null
        }
    }

    private fun resolveViaInvidious(videoId: String): String? {
        val instances = listOf("https://invidious.f5.si", "https://inv.vern.cc")
        for (instance in instances) {
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val formats = json.optJSONArray("adaptiveFormats") ?: continue
                for (i in 0 until formats.length()) {
                    val format = formats.optJSONObject(i) ?: continue
                    val type = format.optString("type")
                    if (type.contains("audio/")) {
                        val url = format.optString("url")
                        if (url.isNotEmpty()) return url
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Invidious instance $instance failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * Resolves all available video resolutions (1080p, 720p, 480p, 360p, 240p)
     * using the multi-client fallback pipeline (VISIONOS -> ANDROID_VR -> Invidious).
     */
    suspend fun resolveVideoQualities(song: Song): List<VideoQuality> = withContext(Dispatchers.IO) {
        val videoId = song.sourceId
        
        // 1. Try VISIONOS
        val visionQualities = extractQualitiesViaInnerTube(videoId, getVisionOsContext(cachedVisitorData), "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15", "101", "1.02")
        if (visionQualities.isNotEmpty()) return@withContext visionQualities

        // 2. Try ANDROID_VR
        val vrQualities = extractQualitiesViaInnerTube(videoId, getAndroidVrContext(), "Mozilla/5.0 (Linux; Android 12; Quest 3) AppleWebKit/537.36")
        if (vrQualities.isNotEmpty()) return@withContext vrQualities

        // 3. Fallback to Invidious
        val invidiousQualities = extractQualitiesViaInvidious(videoId)
        if (invidiousQualities.isNotEmpty()) return@withContext invidiousQualities

        emptyList()
    }

    private fun extractQualitiesViaInnerTube(
        videoId: String,
        contextObj: JSONObject,
        userAgent: String,
        clientNameHeader: String? = null,
        clientVersionHeader: String? = null
    ): List<VideoQuality> {
        try {
            val payload = JSONObject().apply {
                put("context", contextObj)
                put("videoId", videoId)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                    })
                })
            }

            val reqBuilder = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .post(payload.toString().toRequestBody(jsonMediaType))

            if (!clientNameHeader.isNullOrEmpty()) reqBuilder.header("X-Youtube-Client-Name", clientNameHeader)
            if (!clientVersionHeader.isNullOrEmpty()) reqBuilder.header("X-Youtube-Client-Version", clientVersionHeader)

            val response = client.newCall(reqBuilder.build()).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)

            val streamingData = json.optJSONObject("streamingData") ?: return emptyList()
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return emptyList()

            var bestAudioUrl: String? = null
            var bestAudioBitrate = 0

            // Find best audio URL first
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType")
                val url = format.optString("url")
                if (url.isNotEmpty() && mimeType.contains("audio/")) {
                    val bitrate = format.optInt("bitrate", 0)
                    if (bitrate > bestAudioBitrate) {
                        bestAudioBitrate = bitrate
                        bestAudioUrl = url
                    }
                }
            }

            // Extract distinct video resolutions
            val qualityMap = mutableMapOf<Int, VideoQuality>()
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType")
                val url = format.optString("url")
                if (url.isEmpty()) continue

                if (mimeType.contains("video/")) {
                    val height = format.optInt("height", 0)
                    val label = format.optString("qualityLabel").ifEmpty { "${height}p" }
                    if (height in 144..1080 && !qualityMap.containsKey(height)) {
                        qualityMap[height] = VideoQuality(
                            label = label,
                            height = height,
                            videoUrl = url,
                            audioUrl = bestAudioUrl
                        )
                    }
                }
            }

            return qualityMap.values.sortedByDescending { it.height }
        } catch (e: Exception) {
            Log.w(tag, "Error extracting video qualities for $videoId", e)
            return emptyList()
        }
    }

    private fun extractQualitiesViaInvidious(videoId: String): List<VideoQuality> {
        val instances = listOf("https://invidious.f5.si", "https://inv.vern.cc")
        for (instance in instances) {
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val formats = json.optJSONArray("adaptiveFormats") ?: continue

                var bestAudioUrl: String? = null
                for (i in 0 until formats.length()) {
                    val format = formats.optJSONObject(i) ?: continue
                    val type = format.optString("type")
                    val url = format.optString("url")
                    if (url.isNotEmpty() && type.contains("audio/")) {
                        bestAudioUrl = url
                        break
                    }
                }

                val qualityMap = mutableMapOf<Int, VideoQuality>()
                for (i in 0 until formats.length()) {
                    val format = formats.optJSONObject(i) ?: continue
                    val type = format.optString("type")
                    val url = format.optString("url")
                    if (url.isNotEmpty() && type.contains("video/")) {
                        val qualityLabel = format.optString("qualityLabel")
                        val resolution = format.optString("resolution")
                        val height = resolution.split("x").getOrNull(1)?.toIntOrNull()
                            ?: qualityLabel.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (height in 144..1080 && !qualityMap.containsKey(height)) {
                            qualityMap[height] = VideoQuality(
                                label = if (qualityLabel.isNotEmpty()) qualityLabel else "${height}p",
                                height = height,
                                videoUrl = url,
                                audioUrl = bestAudioUrl
                            )
                        }
                    }
                }
                if (qualityMap.isNotEmpty()) {
                    return qualityMap.values.sortedByDescending { it.height }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    suspend fun resolveVideoStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val qualities = resolveVideoQualities(song)
        if (qualities.isNotEmpty()) {
            val chosen = qualities.first() // Best available quality (e.g. 1080p or 720p)
            if (chosen.audioUrl != null) {
                song.streamUrl = chosen.audioUrl
            }
            return@withContext chosen.videoUrl
        }
        null
    }

    private fun getWebContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20241001.01.00")
                put("hl", "vi")
                put("gl", "VN")
            })
        }
    }

    private fun getWebRemixContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20241001.01.00")
                put("hl", "vi")
                put("gl", "VN")
            })
        }
    }

    private fun getVisionOsContext(visitorData: String?): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "VISIONOS")
                put("clientVersion", "1.02")
                put("deviceMake", "Apple")
                put("deviceModel", "RealityDevice17,1")
                put("osName", "visionOS")
                put("osVersion", "26.5.23O471")
                put("userAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                put("hl", "en")
                put("timeZone", "UTC")
                put("utcOffsetMinutes", 0)
                if (!visitorData.isNullOrEmpty()) {
                    put("visitorData", visitorData)
                }
            })
        }
    }

    private fun getAndroidVrContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_VR")
                put("clientVersion", "1.61.48")
                put("deviceMake", "Oculus")
                put("deviceModel", "Quest 3")
                put("osName", "Android")
                put("osVersion", "12")
                put("hl", "en")
                put("gl", "US")
            })
        }
    }
}

data class VideoQuality(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?
)
