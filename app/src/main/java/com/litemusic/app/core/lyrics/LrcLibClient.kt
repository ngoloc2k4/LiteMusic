package com.litemusic.app.core.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Open-source, free synced lyrics integration via LRCLIB (no API key required)
 */
class LrcLibClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "LrcLibClient"
    private val lrcPattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        durationSeconds: Long = 0L
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
            var url = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            if (durationSeconds > 0) {
                url += "&duration=$durationSeconds"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LiteMusic/1.0.0 (https://github.com/ngoloc2k4/LiteMusic)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)

            val syncedLyrics = json.optString("syncedLyrics", "")
            if (syncedLyrics.isNotEmpty()) {
                return@withContext parseLrc(syncedLyrics)
            }

            // Fallback: Plain lyrics without sync
            val plainLyrics = json.optString("plainLyrics", "")
            if (plainLyrics.isNotEmpty()) {
                return@withContext plainLyrics.lines().mapIndexed { index, line ->
                    LyricLine(index * 4000L, line)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not fetch lyrics for $trackName - $artistName: ${e.message}")
        }
        emptyList()
    }

    private fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in lrcContent.lines()) {
            val matcher = lrcPattern.matcher(rawLine.trim())
            if (matcher.matches()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisRaw = matcher.group(3) ?: "0"
                val millis = if (millisRaw.length == 2) millisRaw.toLong() * 10 else millisRaw.toLong()
                val text = matcher.group(4)?.trim() ?: ""

                val timestampMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                lines.add(LyricLine(timestampMs, text))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
