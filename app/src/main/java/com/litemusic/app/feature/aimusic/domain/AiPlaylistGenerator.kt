package com.litemusic.app.feature.aimusic.domain

import android.content.Context
import com.litemusic.app.core.ai.LlmRequest
import com.litemusic.app.core.ai.LlmResult
import com.litemusic.app.core.ai.LlmRouter
import com.litemusic.app.core.model.Song
import com.litemusic.app.core.resolver.SoundCloudStreamResolver
import com.litemusic.app.core.resolver.YouTubeStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AiPlaylistGenerator(
    private val context: Context,
    private val router: LlmRouter = LlmRouter(context),
    private val ytResolver: YouTubeStreamResolver = YouTubeStreamResolver(),
    private val scResolver: SoundCloudStreamResolver = SoundCloudStreamResolver()
) {

    suspend fun generatePlaylist(
        userPrompt: String,
        targetSongCount: Int = 20,
        onStateChange: (AiGenerationState) -> Unit
    ): List<Song> = withContext(Dispatchers.IO) {
        val trimmedPrompt = userPrompt.trim()

        // 1. Parse Prompt into MusicIntent
        onStateChange(AiGenerationState.ParsingIntent(trimmedPrompt))
        val intent = parseIntent(trimmedPrompt, targetSongCount)

        // 2. Parallel Search across Multi-Source (YouTube + SoundCloud)
        onStateChange(AiGenerationState.SearchingTracks(intent.searchQueries))
        val rawCandidates = searchParallel(intent.searchQueries)

        if (rawCandidates.isEmpty()) {
            onStateChange(AiGenerationState.Error("Không tìm thấy bài hát nào từ YouTube và SoundCloud.", canFallbackLocal = false))
            return@withContext emptyList()
        }

        // 3. Local Heuristic Ranking & Deduplication (Base Candidates)
        val initialFiltered = LocalHeuristicRanker.rankAndFilter(rawCandidates, trimmedPrompt, targetCount = 35)

        // 4. LLM Final Selection & Ordering
        onStateChange(AiGenerationState.RankingTracks(initialFiltered.size, isUsingAi = true))
        val (finalTracks, providerName) = rankWithLlmOrFallback(trimmedPrompt, initialFiltered, targetSongCount)

        // 5. Emit Success
        onStateChange(
            AiGenerationState.Success(
                playlistTitle = intent.title,
                playlistDescription = intent.description,
                tracks = finalTracks,
                providerUsed = providerName
            )
        )

        return@withContext finalTracks
    }

    private suspend fun parseIntent(prompt: String, targetCount: Int): MusicIntent {
        val defaultIntent = MusicIntent(
            title = "Playlist: $prompt",
            description = "Playlist được chọn lọc theo yêu cầu: \"$prompt\"",
            searchQueries = listOf(prompt, "$prompt chill", "$prompt acoustic"),
            targetCount = targetCount
        )

        val systemPrompt = """
            You are a music playlist curator AI. Analyze the user's playlist request and return ONLY a valid JSON object matching this schema:
            {
               "title": "Short creative playlist title in Vietnamese",
               "description": "1 sentence playlist description in Vietnamese",
               "mood": "chill/energetic/sad/focus/etc",
               "genres": ["genre1", "genre2"],
               "searchQueries": ["3 specific short search queries to find real songs on YouTube & SoundCloud"]
            }
        """.trimIndent()

        val req = LlmRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            temperature = 0.5f,
            maxTokens = 350,
            responseJsonSchema = true
        )

        val res = router.generateWithFallback(req)
        if (res is LlmResult.Success) {
            try {
                val json = JSONObject(res.text)
                val queries = mutableListOf<String>()
                val qArr = json.optJSONArray("searchQueries")
                if (qArr != null) {
                    for (i in 0 until qArr.length()) {
                        queries.add(qArr.getString(i))
                    }
                }
                if (queries.isEmpty()) queries.add(prompt)

                return MusicIntent(
                    title = json.optString("title", defaultIntent.title),
                    description = json.optString("description", defaultIntent.description),
                    mood = json.optString("mood", ""),
                    searchQueries = queries,
                    targetCount = targetCount
                )
            } catch (e: Exception) {
                return defaultIntent
            }
        }

        return defaultIntent
    }

    private suspend fun searchParallel(queries: List<String>): List<Song> = coroutineScope {
        val deferredList = queries.flatMap { q ->
            listOf(
                async {
                    try { ytResolver.search(q).take(15) } catch (e: Exception) { emptyList() }
                },
                async {
                    try { scResolver.search(q).take(10) } catch (e: Exception) { emptyList() }
                }
            )
        }
        deferredList.flatMap { it.await() }
    }

    private suspend fun rankWithLlmOrFallback(
        prompt: String,
        candidates: List<Song>,
        targetCount: Int
    ): Pair<List<Song>, String> {
        val candidateMap = candidates.associateBy { it.id }

        // Build candidate snippet array for LLM (id, title, artist)
        val candidateJsonArr = JSONArray()
        candidates.forEachIndexed { idx, s ->
            candidateJsonArr.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("artist", s.artist)
            })
        }

        val systemPrompt = """
            You are a music playlist curator. Given a user request and a candidate pool of songs, select the best $targetCount songs that match the mood and flow naturally together.
            Return ONLY a valid JSON object:
            {
               "selectedIds": ["id1", "id2", "..."],
               "reason": "1 short sentence explaining why these were chosen"
            }
        """.trimIndent()

        val userMessage = "User request: \"$prompt\"\nCandidates:\n$candidateJsonArr"

        val req = LlmRequest(
            prompt = userMessage,
            systemPrompt = systemPrompt,
            temperature = 0.4f,
            maxTokens = 600,
            responseJsonSchema = true
        )

        val result = router.generateWithFallback(req)
        if (result is LlmResult.Success) {
            try {
                val json = JSONObject(result.text)
                val idArr = json.optJSONArray("selectedIds")
                if (idArr != null && idArr.length() > 0) {
                    val selected = mutableListOf<Song>()
                    for (i in 0 until idArr.length()) {
                        val id = idArr.getString(i)
                        candidateMap[id]?.let { selected.add(it) }
                    }
                    if (selected.isNotEmpty()) {
                        return Pair(selected, result.modelUsed)
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors and fallback
            }
        }

        // Local Heuristic Fallback
        val fallback = LocalHeuristicRanker.rankAndFilter(candidates, prompt, targetCount)
        return Pair(fallback, "Thuật toán cục bộ (Local Fallback)")
    }
}
