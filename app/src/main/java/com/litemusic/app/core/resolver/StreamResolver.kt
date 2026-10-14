package com.litemusic.app.core.resolver

import com.litemusic.app.core.model.Song

interface StreamResolver {
    /**
     * Search songs from this source
     */
    suspend fun search(query: String): List<Song>

    /**
     * Resolve playable direct audio stream URL (Opus, AAC, or MP3)
     */
    suspend fun resolveStreamUrl(song: Song): String?
}
