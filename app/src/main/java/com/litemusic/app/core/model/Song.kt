package com.litemusic.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AudioSource {
    ALL,
    YOUTUBE_MUSIC,
    YOUTUBE_VIDEO,
    SOUNDCLOUD,
    SPOTIFY
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String = "",
    val source: AudioSource = AudioSource.YOUTUBE_MUSIC,
    val sourceId: String = id,
    var streamUrl: String? = null,
    var videoUrl: String? = null,
    val loudnessDb: Float? = null // For ReplayGain / Volume Normalization
)
