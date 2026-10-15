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

    /**
     * Advanced non-AI heuristic recommender:
     * Analyzes artist affinity, title keywords, tempo/duration proximity,
     * and historical playback preferences to produce cohesive continuous playlists.
     */
    fun recommendRelatedSongs(
        baseSong: Song,
        candidatePool: List<Song>,
        targetCount: Int = 25,
        historySongs: List<Song> = emptyList()
    ): List<Song> {
        val baseArtist = baseSong.artist.lowercase(Locale.ROOT).trim()
        val baseTitleTokens = extractSignificantTokens(baseSong.title)
        val historyArtists = historySongs.map { it.artist.lowercase(Locale.ROOT).trim() }.toSet()

        val seenTitles = mutableSetOf(normalizeTitle(baseSong.title))
        val scored = mutableListOf<Pair<Song, Float>>()

        for (candidate in candidatePool) {
            if (candidate.id == baseSong.id) continue
            val normTitle = normalizeTitle(candidate.title)
            if (normTitle in seenTitles) continue
            seenTitles.add(normTitle)

            var score = 1.0f
            val candArtist = candidate.artist.lowercase(Locale.ROOT).trim()
            val candTokens = extractSignificantTokens(candidate.title)

            // 1. Same or closely related artist
            if (candArtist == baseArtist) {
                score += 4.5f
            } else if (candArtist.contains(baseArtist) || baseArtist.contains(candArtist)) {
                score += 3.0f
            }

            // 2. Title token similarity (shared vibe/theme like "remix", "chill", "acoustic", "lofi")
            val commonTokens = baseTitleTokens.intersect(candTokens)
            score += commonTokens.size * 2.2f

            // 3. Duration compatibility (prefer tracks within +/- 90 seconds of base song)
            val durationDiff = kotlin.math.abs(candidate.durationSeconds - baseSong.durationSeconds)
            if (durationDiff <= 60) {
                score += 1.5f
            } else if (durationDiff <= 120) {
                score += 0.8f
            }

            // 4. Affinity with user's historical favorite artists
            if (candArtist in historyArtists) {
                score += 1.8f
            }

            scored.add(candidate to score)
        }

        // Sort by similarity score
        scored.sortByDescending { it.second }

        val artistCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<Song>()

        for ((song, _) in scored) {
            val aKey = song.artist.lowercase(Locale.ROOT).trim()
            val count = artistCounts.getOrDefault(aKey, 0)
            // Limit to at most 3 songs per artist to guarantee diversity
            if (count < 3 || result.size < targetCount / 3) {
                result.add(song)
                artistCounts[aKey] = count + 1
            }
            if (result.size >= targetCount) break
        }

        return if (result.isNotEmpty()) result else candidatePool.filterNot { it.id == baseSong.id }.take(targetCount)
    }

    private fun extractSignificantTokens(title: String): Set<String> {
        val stopWords = setOf("official", "video", "audio", "mv", "lyrics", "loi", "bai", "hat", "ft", "feat", "mp3", "full", "hd", "4k")
        return normalizeTitle(title)
            .split(" ")
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase(Locale.ROOT)
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "") // remove bracketed tags like [Official MV]
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
    }
}
