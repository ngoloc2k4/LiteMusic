package com.litemusic.app.core.resolver

import android.util.Log
import com.litemusic.app.core.cache.StreamUrlCache
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import com.litemusic.app.core.network.AnonymousSessionManager
import com.litemusic.app.core.network.EndpointHealthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class YouTubeStreamResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .build()
) : StreamResolver {

    private val tag = "YTStreamResolver"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val innerTubeUrl = "https://music.youtube.com/youtubei/v1"
    private val playerUrl = "https://www.youtube.com/youtubei/v1/player"
    private val playerGapisUrl = "https://youtubei.googleapis.com/youtubei/v1/player"
    private val visitorIdUrl = "https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false"

    private fun sanitizeThumbnailUrl(rawUrl: String?, videoId: String): String {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val canonicalFallback = if (cleanId.isNotEmpty() && cleanId.length in 8..15) {
            "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"
        } else ""

        if (rawUrl.isNullOrBlank()) {
            return canonicalFallback
        }

        var url = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/vi/") -> "https://i.ytimg.com$rawUrl"
            rawUrl.startsWith("/") -> "https://i.ytimg.com$rawUrl"
            !rawUrl.startsWith("http://") && !rawUrl.startsWith("https://") -> "https://$rawUrl"
            else -> rawUrl
        }

        // If from dead or rate-limited Invidious instances, switch directly to reliable YouTube CDN
        if (url.contains("invidious") || url.contains("vern.cc") || url.contains("f5.si") || url.contains("drgns.space")) {
            return if (canonicalFallback.isNotEmpty()) canonicalFallback else url
        }

        // If YouTube Music small artwork, upgrade to high-res square
        if (url.contains("googleusercontent.com") && url.contains("=w")) {
            url = url.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w544-h544-l90-rj")
        }

        return url
    }

    suspend fun getTrending(): List<Song> {
        return search("Top Music Trending")
    }

    /**
     * Retrieve related songs for the currently playing track.
     * Uses YouTube Music's native Auto-Mix Radio (RDAMVM$videoId) via InnerTube Next endpoint,
     * falling back to artist radio search if needed.
     */
    suspend fun getRelatedSongs(song: Song): List<Song> = withContext(Dispatchers.IO) {
        val videoId = song.sourceId.ifEmpty { song.id.removePrefix("ytv_") }
        val results = mutableListOf<Song>()

        if (videoId.isNotEmpty()) {
            try {
                val payload = JSONObject().apply {
                    put("context", getWebRemixContext())
                    put("videoId", videoId)
                    put("playlistId", "RDAMVM$videoId")
                }

                val request = Request.Builder()
                    .url("$innerTubeUrl/next")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://music.youtube.com/")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val tabs = json.optJSONObject("contents")
                        ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                        ?.optJSONObject("tabbedRenderer")
                        ?.optJSONObject("watchNextTabbedResultsRenderer")
                        ?.optJSONArray("tabs")

                    if (tabs != null) {
                        for (i in 0 until tabs.length()) {
                            val queue = tabs.optJSONObject(i)
                                ?.optJSONObject("tabRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("musicQueueRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("playlistPanelRenderer")
                                ?.optJSONArray("contents")

                            if (queue != null) {
                                for (j in 0 until queue.length()) {
                                    val vr = queue.optJSONObject(j)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                                    val itemVid = vr.optString("videoId")
                                    if (itemVid.isEmpty() || itemVid == videoId) continue

                                    val title = vr.optJSONObject("title")?.optJSONArray("runs")
                                        ?.optJSONObject(0)?.optString("text") ?: ""
                                    val artist = vr.optJSONObject("longBylineText")?.optJSONArray("runs")
                                        ?.optJSONObject(0)?.optString("text")
                                        ?: vr.optJSONObject("shortBylineText")?.optJSONArray("runs")
                                            ?.optJSONObject(0)?.optString("text") ?: "YouTube Music"

                                    val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val rawThumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
                                    val thumbUrl = sanitizeThumbnailUrl(rawThumb, itemVid)

                                    if (title.isNotEmpty() && results.none { it.sourceId == itemVid }) {
                                        results.add(
                                            Song(
                                                id = itemVid,
                                                title = title,
                                                artist = artist,
                                                thumbnailUrl = thumbUrl,
                                                source = AudioSource.YOUTUBE_MUSIC,
                                                sourceId = itemVid
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch related songs via RDAMVM radio", e)
            }
        }

        // Secondary fallback if radio returned fewer than 15 songs
        if (results.size < 15) {
            val cleanArtist = song.artist.split("•", ",", "&", "feat.", "ft.", "/")[0].trim()
            val queries = listOfNotNull(
                if (cleanArtist.isNotEmpty() && !cleanArtist.equals("YouTube", ignoreCase = true)) "$cleanArtist radio" else null,
                if (cleanArtist.isNotEmpty() && !cleanArtist.equals("YouTube", ignoreCase = true)) cleanArtist else null,
                if (song.title.isNotEmpty()) "${song.title} mix" else null
            )

            for (q in queries) {
                if (results.size >= 30) break
                try {
                    val searchItems = search(q)
                    for (item in searchItems) {
                        if (item.sourceId != videoId && results.none { it.sourceId == item.sourceId }) {
                            results.add(item)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        results
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
            // Fallback to regular YouTube search only if Music search returned no results
            if (results.isEmpty()) {
                try {
                    val additional = searchRegularVideos(query)
                    for (song in additional) {
                        results.add(song.copy(source = AudioSource.YOUTUBE_MUSIC))
                    }
                } catch (_: Exception) {}
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
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Content-Type", "application/json")
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
                    val rawVideoId = vr.optString("videoId")
                    if (rawVideoId.isEmpty()) continue
                    val videoId = rawVideoId.removePrefix("ytv_").trim()

                    // Extract full title across runs or simpleText
                    val titleRuns = vr.optJSONObject("title")?.optJSONArray("runs")
                    val title = if (titleRuns != null && titleRuns.length() > 0) {
                        val sb = StringBuilder()
                        for (k in 0 until titleRuns.length()) {
                            sb.append(titleRuns.optJSONObject(k)?.optString("text").orEmpty())
                        }
                        sb.toString().trim()
                    } else {
                        vr.optJSONObject("title")?.optString("simpleText")?.ifEmpty { null } ?: "YouTube Video"
                    }

                    // Extract channel name from ownerText or byline
                    val ownerRuns = vr.optJSONObject("ownerText")?.optJSONArray("runs")
                        ?: vr.optJSONObject("longBylineText")?.optJSONArray("runs")
                        ?: vr.optJSONObject("shortBylineText")?.optJSONArray("runs")
                    val channel = if (ownerRuns != null && ownerRuns.length() > 0) {
                        val sb = StringBuilder()
                        for (k in 0 until ownerRuns.length()) {
                            sb.append(ownerRuns.optJSONObject(k)?.optString("text").orEmpty())
                        }
                        sb.toString().trim()
                    } else "YouTube"

                    val lengthText = vr.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                    val durationSec = parseDurationToSeconds(lengthText)

                    val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val rawThumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
                    val thumbUrl = sanitizeThumbnailUrl(rawThumb, videoId)

                    results.add(
                        Song(
                            id = "ytv_$videoId",
                            title = title,
                            artist = if (lengthText.isNotEmpty()) "$channel • $lengthText" else channel,
                            thumbnailUrl = thumbUrl,
                            durationSeconds = durationSec,
                            source = AudioSource.YOUTUBE_VIDEO,
                            sourceId = videoId
                        )
                    )
                }
            }

            // Only query Invidious if YouTube search returned 0 results
            if (results.isEmpty()) {
                try {
                    val invidiousResults = searchViaInvidious(query)
                    results.addAll(invidiousResults)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(tag, "Regular video search error for: $query", e)
        }
        results
    }

    private fun parseDurationToSeconds(lengthText: String): Long {
        if (lengthText.isBlank()) return 0L
        return try {
            val parts = lengthText.trim().split(":").map { it.toLong() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun searchViaInvidious(query: String): List<Song> {
        val instances = EndpointHealthManager.getInstance().getRankedInvidiousInstances().take(2)
        for (instance in instances) {
            val start = System.currentTimeMillis()
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder()
                    .url("$instance/api/v1/search?q=$encoded&type=video")
                    .header("User-Agent", AnonymousSessionManager.UA_DESKTOP_CHROME)
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val arr = org.json.JSONArray(body)
                val list = mutableListOf<Song>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val videoId = obj.optString("videoId")
                    if (videoId.isEmpty()) continue
                    val title = obj.optString("title").ifEmpty { "Video" }
                    val author = obj.optString("author").ifEmpty { "YouTube" }
                    val thumbs = obj.optJSONArray("videoThumbnails")
                    val rawThumb = thumbs?.optJSONObject(0)?.optString("url")
                    val thumbUrl = sanitizeThumbnailUrl(rawThumb, videoId)
                    val lengthSeconds = obj.optLong("lengthSeconds", 0L)
                    list.add(
                        Song(
                            id = "ytv_$videoId",
                            title = title,
                            artist = author,
                            thumbnailUrl = thumbUrl,
                            durationSeconds = lengthSeconds,
                            source = AudioSource.YOUTUBE_VIDEO,
                            sourceId = videoId
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    EndpointHealthManager.getInstance().recordSuccess(instance, System.currentTimeMillis() - start)
                    return list
                }
            } catch (e: Exception) {
                EndpointHealthManager.getInstance().recordFailure(instance)
            }
        }
        return emptyList()
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
        val rawThumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
        val thumbUrl = sanitizeThumbnailUrl(rawThumb, videoId)

        return Song(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbUrl,
            source = AudioSource.YOUTUBE_MUSIC,
            sourceId = videoId
        )
    }

    private suspend fun getOrFetchVisitorData(forceRefresh: Boolean = false): String? {
        return AnonymousSessionManager.getInstance().getOrFetchVisitorData(force = forceRefresh)
    }

    override suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val videoId = song.sourceId.removePrefix("ytv_").trim()

        // 0. Check ultra-fast in-memory LRU cache (0ms instant playback for repeated/cached tracks)
        val cached = StreamUrlCache.get(videoId)
        if (!cached?.streamUrl.isNullOrEmpty()) {
            Log.d(tag, "StreamUrlCache HIT for $videoId")
            return@withContext cached!!.streamUrl
        }
        
        // 1. Try VISIONOS InnerTube client with fresh visitorData & ranked endpoints
        val visionUrl = resolveViaVisionOs(videoId)
        if (visionUrl != null) {
            StreamUrlCache.put(videoId, streamUrl = visionUrl)
            return@withContext visionUrl
        }

        // 2. Try dynamically ranked Piped API fallback
        val pipedUrl = resolveViaPiped(videoId)
        if (pipedUrl != null) {
            StreamUrlCache.put(videoId, streamUrl = pipedUrl)
            return@withContext pipedUrl
        }

        // 3. Fallback to dynamically ranked Invidious API
        val invidiousUrl = resolveViaInvidious(videoId)
        if (invidiousUrl != null) {
            StreamUrlCache.put(videoId, streamUrl = invidiousUrl)
            return@withContext invidiousUrl
        }

        Log.e(tag, "All stream resolution attempts failed for ${song.title} ($videoId)")
        null
    }

    private suspend fun resolveViaVisionOs(videoId: String): String? {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val sessionMgr = AnonymousSessionManager.getInstance()
        val healthMgr = EndpointHealthManager.getInstance()
        var visitorData = sessionMgr.getOrFetchVisitorData(force = false)
        val endpoints = healthMgr.getRankedInnerTubeEndpoints()

        for (endpoint in endpoints) {
            for (attempt in 0..1) {
                val start = System.currentTimeMillis()
                try {
                    val payload = JSONObject().apply {
                        put("context", sessionMgr.getVisionOsContext(visitorData))
                        put("videoId", cleanId)
                        put("contentCheckOk", true)
                        put("racyCheckOk", true)
                        put("playbackContext", JSONObject().apply {
                            put("contentPlaybackContext", JSONObject().apply {
                                put("html5Preference", "HTML5_PREF_WANTS")
                            })
                        })
                    }

                    val reqBuilder = Request.Builder()
                        .url("$endpoint/player?prettyPrint=false&id=$cleanId&key=${sessionMgr.apiKey}")
                        .header("User-Agent", AnonymousSessionManager.UA_VISION_OS)
                        .header("Content-Type", "application/json")
                        .header("X-Goog-Api-Format-Version", "2")
                        .header("X-Youtube-Client-Name", "101")
                        .header("X-Youtube-Client-Version", "1.02")
                        .header("Origin", "https://www.youtube.com")
                        .header("Cookie", sessionMgr.getAnonymousCookieHeader(visitorData))
                        .post(payload.toString().toRequestBody(jsonMediaType))

                    if (!visitorData.isNullOrEmpty()) {
                        reqBuilder.header("X-Goog-Visitor-Id", visitorData)
                    }

                    val response = client.newCall(reqBuilder.build()).execute()
                    val body = response.body?.string() ?: continue
                    val json = JSONObject(body)

                    val playability = json.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status")
                    if (status != "OK") {
                        Log.w(tag, "VISIONOS playabilityStatus for $cleanId: $status (attempt $attempt) via $endpoint")
                        if (attempt == 0) {
                            visitorData = sessionMgr.getOrFetchVisitorData(force = true)
                            continue
                        }
                        break
                    }

                    val streamingData = json.optJSONObject("streamingData") ?: continue
                    val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: continue

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
                    if (!chosenUrl.isNullOrEmpty()) {
                        healthMgr.recordSuccess(endpoint, System.currentTimeMillis() - start)
                        return chosenUrl
                    }
                } catch (e: Exception) {
                    healthMgr.recordFailure(endpoint)
                    Log.w(tag, "VISIONOS resolution error for $cleanId via $endpoint: ${e.message}")
                }
            }
        }
        return null
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
        val cleanId = videoId.removePrefix("ytv_").trim()
        val instances = EndpointHealthManager.getInstance().getRankedInvidiousInstances().take(3)
        for (instance in instances) {
            val start = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$cleanId")
                    .header("User-Agent", AnonymousSessionManager.UA_DESKTOP_CHROME)
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
                        if (url.isNotEmpty()) {
                            EndpointHealthManager.getInstance().recordSuccess(instance, System.currentTimeMillis() - start)
                            return url
                        }
                    }
                }
            } catch (e: Exception) {
                EndpointHealthManager.getInstance().recordFailure(instance)
                Log.w(tag, "Invidious instance $instance failed for $cleanId: ${e.message}")
            }
        }
        return null
    }

    private fun resolveViaPiped(videoId: String): String? {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val instances = EndpointHealthManager.getInstance().getRankedPipedInstances().take(3)
        for (instance in instances) {
            val start = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("$instance/streams/$cleanId")
                    .header("User-Agent", AnonymousSessionManager.UA_DESKTOP_CHROME)
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val audioStreams = json.optJSONArray("audioStreams") ?: continue
                var bestUrl: String? = null
                var bestBitrate = 0
                for (i in 0 until audioStreams.length()) {
                    val stream = audioStreams.optJSONObject(i) ?: continue
                    val url = stream.optString("url")
                    val bitrate = stream.optInt("bitrate", 0)
                    if (url.isNotEmpty() && bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
                if (!bestUrl.isNullOrEmpty()) {
                    EndpointHealthManager.getInstance().recordSuccess(instance, System.currentTimeMillis() - start)
                    return bestUrl
                }
            } catch (e: Exception) {
                EndpointHealthManager.getInstance().recordFailure(instance)
                Log.w(tag, "Piped instance $instance audio failed for $cleanId: ${e.message}")
            }
        }
        return null
    }

    /**
     * Resolves all available video resolutions (1080p, 720p, 480p, 360p, 240p)
     * using the multi-client fallback pipeline (VISIONOS -> Piped -> Invidious).
     */
    suspend fun resolveVideoQualities(song: Song): List<VideoQuality> = withContext(Dispatchers.IO) {
        val videoId = song.sourceId.removePrefix("ytv_").trim()

        // 0. Check ultra-fast LRU quality cache
        val cached = StreamUrlCache.get(videoId)
        if (!cached?.qualities.isNullOrEmpty()) {
            Log.d(tag, "StreamUrlCache qualities HIT for $videoId")
            return@withContext cached!!.qualities
        }

        val sessionMgr = AnonymousSessionManager.getInstance()
        val visitorData = sessionMgr.getOrFetchVisitorData(force = false)
        
        // 1. Try VISIONOS InnerTube client with fresh visitorData
        val visionQualities = extractQualitiesViaInnerTube(
            videoId = videoId,
            contextObj = sessionMgr.getVisionOsContext(visitorData),
            userAgent = AnonymousSessionManager.UA_VISION_OS,
            clientNameHeader = "101",
            clientVersionHeader = "1.02",
            visitorData = visitorData
        )
        if (visionQualities.isNotEmpty()) {
            StreamUrlCache.put(videoId, streamUrl = visionQualities.firstOrNull()?.audioUrl, qualities = visionQualities)
            return@withContext visionQualities
        }

        // 2. Try Piped API fallback with dynamic latency ranking
        val pipedQualities = extractQualitiesViaPiped(videoId)
        if (pipedQualities.isNotEmpty()) {
            StreamUrlCache.put(videoId, streamUrl = pipedQualities.firstOrNull()?.audioUrl, qualities = pipedQualities)
            return@withContext pipedQualities
        }

        // 3. Fallback to Invidious with dynamic latency ranking
        val invidiousQualities = extractQualitiesViaInvidious(videoId)
        if (invidiousQualities.isNotEmpty()) {
            StreamUrlCache.put(videoId, streamUrl = invidiousQualities.firstOrNull()?.audioUrl, qualities = invidiousQualities)
            return@withContext invidiousQualities
        }

        emptyList()
    }

    private fun extractQualitiesViaInnerTube(
        videoId: String,
        contextObj: JSONObject,
        userAgent: String,
        clientNameHeader: String? = null,
        clientVersionHeader: String? = null,
        visitorData: String? = null
    ): List<VideoQuality> {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val sessionMgr = AnonymousSessionManager.getInstance()
        val healthMgr = EndpointHealthManager.getInstance()
        val endpoints = healthMgr.getRankedInnerTubeEndpoints()

        for (endpoint in endpoints) {
            val start = System.currentTimeMillis()
            try {
                val payload = JSONObject().apply {
                    put("context", contextObj)
                    put("videoId", cleanId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                        })
                    })
                }

                val reqBuilder = Request.Builder()
                    .url("$endpoint/player?prettyPrint=false&id=$cleanId&key=${sessionMgr.apiKey}")
                    .header("User-Agent", userAgent)
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Format-Version", "2")
                    .header("Origin", "https://www.youtube.com")
                    .header("Cookie", sessionMgr.getAnonymousCookieHeader(visitorData))
                    .post(payload.toString().toRequestBody(jsonMediaType))

                if (!clientNameHeader.isNullOrEmpty()) reqBuilder.header("X-Youtube-Client-Name", clientNameHeader)
                if (!clientVersionHeader.isNullOrEmpty()) reqBuilder.header("X-Youtube-Client-Version", clientVersionHeader)
                if (!visitorData.isNullOrEmpty()) reqBuilder.header("X-Goog-Visitor-Id", visitorData)

                val response = client.newCall(reqBuilder.build()).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)

                val streamingData = json.optJSONObject("streamingData") ?: continue
                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: continue

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

                if (qualityMap.isNotEmpty()) {
                    healthMgr.recordSuccess(endpoint, System.currentTimeMillis() - start)
                    return qualityMap.values.sortedByDescending { it.height }
                }
            } catch (e: Exception) {
                healthMgr.recordFailure(endpoint)
                Log.w(tag, "Error extracting video qualities for $cleanId via $endpoint: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun extractQualitiesViaPiped(videoId: String): List<VideoQuality> {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val instances = EndpointHealthManager.getInstance().getRankedPipedInstances().take(3)
        for (instance in instances) {
            val start = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("$instance/streams/$cleanId")
                    .header("User-Agent", AnonymousSessionManager.UA_DESKTOP_CHROME)
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val audioStreams = json.optJSONArray("audioStreams")
                var bestAudioUrl: String? = null
                var bestAudioBitrate = 0
                if (audioStreams != null) {
                    for (i in 0 until audioStreams.length()) {
                        val stream = audioStreams.optJSONObject(i) ?: continue
                        val url = stream.optString("url")
                        val bitrate = stream.optInt("bitrate", 0)
                        if (url.isNotEmpty() && bitrate > bestAudioBitrate) {
                            bestAudioBitrate = bitrate
                            bestAudioUrl = url
                        }
                    }
                }

                val videoStreams = json.optJSONArray("videoStreams") ?: continue
                val qualityMap = mutableMapOf<Int, VideoQuality>()
                for (i in 0 until videoStreams.length()) {
                    val stream = videoStreams.optJSONObject(i) ?: continue
                    val url = stream.optString("url")
                    val quality = stream.optString("quality")
                    val height = quality.replace("p", "").toIntOrNull() ?: stream.optInt("height", 0)
                    if (url.isNotEmpty() && height in 144..1080 && !qualityMap.containsKey(height)) {
                        qualityMap[height] = VideoQuality(
                            label = if (quality.isNotEmpty()) quality else "${height}p",
                            height = height,
                            videoUrl = url,
                            audioUrl = if (stream.optBoolean("videoOnly", false)) bestAudioUrl else null
                        )
                    }
                }
                if (qualityMap.isNotEmpty()) {
                    EndpointHealthManager.getInstance().recordSuccess(instance, System.currentTimeMillis() - start)
                    return qualityMap.values.sortedByDescending { it.height }
                }
            } catch (e: Exception) {
                EndpointHealthManager.getInstance().recordFailure(instance)
                Log.w(tag, "Piped instance $instance video qualities failed for $cleanId: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun extractQualitiesViaInvidious(videoId: String): List<VideoQuality> {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val instances = EndpointHealthManager.getInstance().getRankedInvidiousInstances().take(3)
        for (instance in instances) {
            val start = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$cleanId")
                    .header("User-Agent", AnonymousSessionManager.UA_DESKTOP_CHROME)
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
                    EndpointHealthManager.getInstance().recordSuccess(instance, System.currentTimeMillis() - start)
                    return qualityMap.values.sortedByDescending { it.height }
                }
            } catch (e: Exception) {
                EndpointHealthManager.getInstance().recordFailure(instance)
            }
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
        return AnonymousSessionManager.getInstance().getWebContext()
    }

    private fun getWebRemixContext(): JSONObject {
        return AnonymousSessionManager.getInstance().getWebRemixContext()
    }

    private fun getVisionOsContext(visitorData: String?): JSONObject {
        return AnonymousSessionManager.getInstance().getVisionOsContext(visitorData)
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
                put("hl", "vi")
                put("gl", "VN")
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
