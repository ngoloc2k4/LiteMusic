package com.litemusic.app.core.cache

import com.litemusic.app.core.resolver.VideoQuality
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Ultra-Fast In-Memory LRU Stream URL & Video Quality Cache.
 * YouTube stream URLs are signed and valid for 6 hours; caching them for 3.5 hours
 * allows subsequent plays, repeats, and seeks to start instantaneously (0ms) without network calls.
 */
object StreamUrlCache {

    data class CachedStream(
        val streamUrl: String?,
        val videoUrl: String?,
        val qualities: List<VideoQuality>,
        val timestampMs: Long = System.currentTimeMillis()
    ) {
        val isValid: Boolean
            get() = System.currentTimeMillis() - timestampMs < (3.5 * 3600 * 1000L).toLong()
    }

    private const val MAX_ENTRIES = 200

    private val cacheMap = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedStream>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStream>?): Boolean {
                return size > MAX_ENTRIES
            }
        }
    )

    fun get(videoId: String): CachedStream? {
        val cleanId = videoId.removePrefix("ytv_").trim()
        val entry = cacheMap[cleanId] ?: return null
        return if (entry.isValid) {
            entry
        } else {
            cacheMap.remove(cleanId)
            null
        }
    }

    fun put(
        videoId: String,
        streamUrl: String?,
        videoUrl: String? = null,
        qualities: List<VideoQuality> = emptyList()
    ) {
        if (streamUrl.isNullOrEmpty() && videoUrl.isNullOrEmpty() && qualities.isEmpty()) return
        val cleanId = videoId.removePrefix("ytv_").trim()
        val existing = cacheMap[cleanId]
        val mergedAudio = streamUrl ?: existing?.streamUrl
        val mergedVideo = videoUrl ?: existing?.videoUrl
        val mergedQualities = if (qualities.isNotEmpty()) qualities else existing?.qualities.orEmpty()

        cacheMap[cleanId] = CachedStream(
            streamUrl = mergedAudio,
            videoUrl = mergedVideo,
            qualities = mergedQualities
        )
    }

    fun clear() {
        cacheMap.clear()
    }
}
