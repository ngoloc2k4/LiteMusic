package com.litemusic.app.feature.aimusic.domain

import kotlinx.serialization.Serializable

@Serializable
data class MusicIntent(
    val title: String,
    val description: String,
    val mood: String = "",
    val genres: List<String> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val targetCount: Int = 20
)
