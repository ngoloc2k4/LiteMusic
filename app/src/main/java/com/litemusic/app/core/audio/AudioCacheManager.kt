package com.litemusic.app.core.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object AudioCacheManager {

    private const val PREFS_NAME = "litemusic_cache_prefs"
    private const val KEY_MAX_CACHE_MB = "max_cache_size_mb"
    private const val DEFAULT_CACHE_SIZE_MB = 500
    private var simpleCache: SimpleCache? = null

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getMaxCacheSizeMb(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MAX_CACHE_MB, DEFAULT_CACHE_SIZE_MB).coerceIn(200, 1500)
    }

    fun setMaxCacheSizeMb(context: Context, mb: Int) {
        val clamped = mb.coerceIn(200, 1500)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_CACHE_MB, clamped)
            .apply()
        enforceCacheLimit(context)
    }

    @Synchronized
    fun enforceCacheLimit(context: Context) {
        val maxBytes = getMaxCacheSizeMb(context).toLong() * 1024L * 1024L
        val cacheDir = File(context.cacheDir, "audio_stream_cache")
        if (!cacheDir.exists()) return

        var currentSize = calculateDirectorySize(cacheDir)
        if (currentSize <= maxBytes) return

        // 1. Evict oldest spans through SimpleCache if initialized
        simpleCache?.let { cache ->
            try {
                val spans = cache.keys.flatMap { cache.getCachedSpans(it) }
                    .sortedBy { it.lastTouchTimestamp }
                for (span in spans) {
                    if (currentSize <= maxBytes) break
                    val len = span.length
                    cache.removeSpan(span)
                    currentSize -= len
                }
            } catch (_: Exception) {}
        }

        // 2. Direct pruning of oldest files if directory size still exceeds maxBytes
        if (currentSize > maxBytes) {
            val allFiles = cacheDir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".uid") }
                .sortedBy { it.lastModified() }
                .toList()
            for (file in allFiles) {
                if (currentSize <= maxBytes) break
                val len = file.length()
                if (file.delete()) {
                    currentSize -= len
                }
            }
        }
    }

    fun getUsedCacheSizeBytes(context: Context): Long {
        enforceCacheLimit(context)
        val cacheDir = File(context.cacheDir, "audio_stream_cache")
        return calculateDirectorySize(cacheDir)
    }

    fun getUsedCacheSizeMb(context: Context): Double {
        return getUsedCacheSizeBytes(context).toDouble() / (1024.0 * 1024.0)
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "audio_stream_cache")
            val maxBytes = getMaxCacheSizeMb(context).toLong() * 1024L * 1024L
            val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
            try {
                enforceCacheLimit(context)
                val databaseProvider = StandaloneDatabaseProvider(context)
                simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
            } catch (e: Exception) {
                // If cache directory is corrupted or locked from a previous crash, purge and retry
                try {
                    deleteRecursively(cacheDir)
                    cacheDir.mkdirs()
                    val databaseProvider = StandaloneDatabaseProvider(context)
                    simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
                } catch (_: Exception) {
                    // Fallback to simpleCache without standalone database
                    simpleCache = SimpleCache(cacheDir, evictor)
                }
            }
        }
        return simpleCache!!
    }

    @Synchronized
    fun clearAllCache(context: Context): Long {
        val bytesBefore = getUsedCacheSizeBytes(context)
        try {
            simpleCache?.release()
        } catch (_: Exception) {}
        simpleCache = null

        val cacheDir = File(context.cacheDir, "audio_stream_cache")
        deleteRecursively(cacheDir)

        // Recreate directory and reinitialize
        cacheDir.mkdirs()
        runCatching { getCache(context) }
        return bytesBefore
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    fun buildCacheDataSourceFactory(context: Context): DataSource.Factory {
        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")

        return try {
            val cache = getCache(context)
            val customKeyFactory = CacheKeyFactory { dataSpec ->
                val uri = dataSpec.uri
                val uriStr = uri.toString()
                if (uriStr.contains("googlevideo.com") || uriStr.contains("youtube.com")) {
                    val id = uri.getQueryParameter("id") ?: uri.getQueryParameter("docid")
                    val itag = uri.getQueryParameter("itag")
                    if (!id.isNullOrEmpty()) {
                        return@CacheKeyFactory if (!itag.isNullOrEmpty()) "yt_${id}_$itag" else "yt_$id"
                    }
                }
                dataSpec.key ?: dataSpec.uri.toString()
            }

            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory(customKeyFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } catch (_: Throwable) {
            upstreamFactory
        }
    }
}
