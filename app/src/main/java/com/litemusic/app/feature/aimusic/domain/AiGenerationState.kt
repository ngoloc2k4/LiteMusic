package com.litemusic.app.feature.aimusic.domain

import com.litemusic.app.core.model.Song

sealed interface AiGenerationState {
    object Idle : AiGenerationState

    data class ParsingIntent(val userPrompt: String) : AiGenerationState

    data class SearchingTracks(val queries: List<String>) : AiGenerationState

    data class RankingTracks(val candidateCount: Int, val isUsingAi: Boolean) : AiGenerationState

    data class Success(
        val playlistTitle: String,
        val playlistDescription: String,
        val tracks: List<Song>,
        val providerUsed: String
    ) : AiGenerationState

    data class Error(
        val message: String,
        val canFallbackLocal: Boolean
    ) : AiGenerationState
}
