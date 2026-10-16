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
 * Robust Multi-Source Synced & Plain Lyrics Resolver (LRCLIB + Lyrics.ovh + Karalyr)
 * Includes intelligent title/artist cleaner for YouTube music & videos.
 */
class LrcLibClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "LrcLibClient"
    private val lrcPattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
    private val lyricsCache = mutableMapOf<String, List<LyricLine>>()

    suspend fun getLyrics(
        trackName: String,
        artistName: String,
        durationSeconds: Long = 0L
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val cacheKey = "${trackName.trim().lowercase()}__${artistName.trim().lowercase()}"
        lyricsCache[cacheKey]?.let { return@withContext it }

        val cleanTrack = cleanTitle(trackName, artistName)
        val cleanArt = cleanArtist(artistName)

        // Tier 1: LRCLIB with cleaned title & artist
        var result = fetchLrcLib(cleanTrack, cleanArt)
        if (result.isNotEmpty()) {
            lyricsCache[cacheKey] = result
            return@withContext result
        }

        // Tier 2: LRCLIB with raw track and clean artist
        if (cleanTrack != trackName) {
            result = fetchLrcLib(trackName, cleanArt)
            if (result.isNotEmpty()) {
                lyricsCache[cacheKey] = result
                return@withContext result
            }
        }

        // Tier 3: Lyrics.ovh fallback (excellent for Vietnamese & international songs)
        result = fetchLyricsOvh(cleanArt, cleanTrack, durationSeconds)
        if (result.isNotEmpty()) {
            lyricsCache[cacheKey] = result
            return@withContext result
        }

        // Tier 4: Karalyr mirror fallback
        result = fetchKaralyr(cleanTrack, cleanArt)
        if (result.isNotEmpty()) {
            lyricsCache[cacheKey] = result
            return@withContext result
        }

        emptyList()
    }

    private val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun fetchLrcLib(title: String, artist: String): List<LyricLine> {
        if (title.isBlank()) return emptyList()

        // 1. Exact match attempt
        try {
            val encTrack = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encTrack&artist_name=$encArtist"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val synced = json.optString("syncedLyrics", "")
                    if (synced.isNotEmpty()) return parseLrc(synced)
                    val plain = json.optString("plainLyrics", "")
                    if (plain.isNotEmpty()) return parsePlain(plain, 0L)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "LRCLIB get error for $title - $artist: ${e.message}")
        }

        // 2. Flexible search by track & artist
        try {
            val encTrack = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/search?track_name=$encTrack&artist_name=$encArtist"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val lyrics = parseLrcLibSearchResults(body)
                if (lyrics.isNotEmpty()) return lyrics
            }
        } catch (e: Exception) {
            Log.w(tag, "LRCLIB search track+artist error: ${e.message}")
        }

        // 3. Search by combined query (title + artist)
        try {
            val combined = "$title $artist".trim()
            val encQuery = URLEncoder.encode(combined, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val lyrics = parseLrcLibSearchResults(body)
                if (lyrics.isNotEmpty()) return lyrics
            }
        } catch (e: Exception) {
            Log.w(tag, "LRCLIB search query error: ${e.message}")
        }

        // 4. Search by title alone if title has multiple words
        if (title.contains(" ")) {
            try {
                val encQuery = URLEncoder.encode(title.trim(), "UTF-8")
                val url = "https://lrclib.net/api/search?q=$encQuery"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", browserUserAgent)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val lyrics = parseLrcLibSearchResults(body)
                    if (lyrics.isNotEmpty()) return lyrics
                }
            } catch (_: Exception) {}
        }

        return emptyList()
    }

    private fun parseLrcLibSearchResults(jsonString: String): List<LyricLine> {
        try {
            val arr = org.json.JSONArray(jsonString)
            var plainCandidate: String? = null
            for (i in 0 until arr.length().coerceAtMost(8)) {
                val obj = arr.optJSONObject(i) ?: continue
                val synced = obj.optString("syncedLyrics", "")
                if (synced.isNotBlank()) {
                    val parsed = parseLrc(synced)
                    if (parsed.isNotEmpty()) return parsed
                }
                if (plainCandidate == null) {
                    val plain = obj.optString("plainLyrics", "")
                    if (plain.isNotBlank()) {
                        plainCandidate = plain
                    }
                }
            }
            if (plainCandidate != null) {
                return parsePlain(plainCandidate, 0L)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun fetchLyricsOvh(artist: String, title: String, durationSeconds: Long): List<LyricLine> {
        try {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val plain = json.optString("lyrics", "")
            if (plain.isNotEmpty()) {
                return parsePlain(plain, durationSeconds)
            }
        } catch (e: Exception) {
            Log.w(tag, "Lyrics.ovh error for $title - $artist: ${e.message}")
        }
        return emptyList()
    }

    private fun fetchKaralyr(title: String, artist: String): List<LyricLine> {
        try {
            val encTrack = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://www.karalyr.com/api/get?track_name=$encTrack&artist_name=$encArtist"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)

            val synced = json.optString("syncedLyrics", "")
            if (synced.isNotEmpty()) {
                return parseLrc(synced)
            }

            val plain = json.optString("plainLyrics", "")
            if (plain.isNotEmpty()) {
                return parsePlain(plain, 0L)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun parsePlain(plain: String, durationSeconds: Long): List<LyricLine> {
        val rawLines = plain.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawLines.isEmpty()) return emptyList()

        val intervalMs = if (durationSeconds > 0) {
            ((durationSeconds * 1000L) / rawLines.size.coerceAtLeast(1)).coerceIn(2500L, 5000L)
        } else {
            3500L
        }

        return rawLines.mapIndexed { idx, line ->
            LyricLine(idx * intervalMs, line)
        }
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

    fun cleanTitle(rawTitle: String, artist: String): String {
        var t = rawTitle
        // Remove brackets containing video/metadata keywords
        t = t.replace(Regex("""[\(\[\{].*?(official|music video|mv|audio|lyric|lyrics|vietsub|karaoke|live|prod|ft\.|feat\.|teaser|trailer|remix).*?[\)\]\}]""", RegexOption.IGNORE_CASE), "")
        // Remove standalone noise phrases
        t = t.replace(Regex("""\b(official music video|official video|official audio|music video|lyric video|lyrics video|mv)\b""", RegexOption.IGNORE_CASE), "")

        // Handle pipe separators (|)
        if (t.contains("|")) {
            val parts = t.split("|").map { it.trim() }.filter { it.length > 1 }
            val nonNoise = parts.filterNot { it.contains(Regex("""official|mv|video|audio""", RegexOption.IGNORE_CASE)) }
            if (nonNoise.size > 1 && artist.isNotBlank()) {
                val nonArtist = nonNoise.filterNot { it.contains(artist, ignoreCase = true) }
                t = if (nonArtist.isNotEmpty()) nonArtist.first() else nonNoise.first()
            } else if (nonNoise.isNotEmpty()) {
                t = nonNoise.first()
            }
        }

        // Handle dash separators ( - )
        if (t.contains(" - ")) {
            val parts = t.split(" - ").map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                t = if (artist.isNotBlank() && parts[0].contains(artist, ignoreCase = true)) {
                    parts[1]
                } else if (artist.isNotBlank() && parts[1].contains(artist, ignoreCase = true)) {
                    parts[0]
                } else {
                    parts[0]
                }
            }
        }

        // Trim leading and trailing punctuation & spaces
        t = t.replace(Regex("""^[\|\-\:\s]+|[\|\-\:\s]+$"""), "")
        return t.trim()
    }

    fun cleanArtist(rawArtist: String): String {
        return rawArtist
            .replace(" - Topic", "", ignoreCase = true)
            .replace(" Official Channel", "", ignoreCase = true)
            .replace(" Official", "", ignoreCase = true)
            .replace("VEVO", "", ignoreCase = true)
            .replace(" Records", "", ignoreCase = true)
            .trim()
    }
}
