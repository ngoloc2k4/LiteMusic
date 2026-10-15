package com.litemusic.app.feature.aimusic.domain

import com.litemusic.app.core.model.Song
import java.util.Locale

object LocalHeuristicRanker {

    /**
     * Deduplicates songs across sources (YouTube Music, YouTube Video, SoundCloud)
     * and scores them based on relevance to prompt keywords.
     */
    fun rankAndFilter(
        candidates: List<Song>,
        prompt: String,
        targetCount: Int = 20
    ): List<Song> {
        val keywords = prompt.lowercase(Locale.ROOT)
            .split(" ", ",", "-", "_", ".", "|")
            .filter { it.length > 1 }
            .toSet()

        val seenTitles = mutableSetOf<String>()
        val scoredList = mutableListOf<Pair<Song, Float>>()

        for (song in candidates) {
            val normalizedTitle = normalizeTitle(song.title)
            if (normalizedTitle in seenTitles) continue
            seenTitles.add(normalizedTitle)

            // Calculate heuristic score
            var score = 1.0f
            val lowerTitle = song.title.lowercase(Locale.ROOT)
            val lowerArtist = song.artist.lowercase(Locale.ROOT)

            for (kw in keywords) {
                if (lowerTitle.contains(kw)) score += 2.0f
                if (lowerArtist.contains(kw)) score += 1.5f
            }

            // Prefer songs with reasonable duration (between 1.5 mins and 12 mins)
            if (song.durationSeconds in 90..720) {
                score += 0.5f
            }

            scoredList.add(song to score)
        }

        // Sort descending by score and balance artist frequency
        scoredList.sortByDescending { it.second }

        val artistCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<Song>()

        for ((song, _) in scoredList) {
            val artistKey = song.artist.lowercase(Locale.ROOT).trim()
            val currentCount = artistCounts.getOrDefault(artistKey, 0)

            // Avoid having one single artist dominate more than 3 songs in a 20-song playlist
            if (currentCount < 3 || result.size < targetCount / 2) {
                result.add(song)
                artistCounts[artistKey] = currentCount + 1
            }

            if (result.size >= targetCount) break
        }

        return if (result.isNotEmpty()) result else candidates.take(targetCount)
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase(Locale.ROOT)
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "") // remove bracketed tags like [Official MV]
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
    }
}
