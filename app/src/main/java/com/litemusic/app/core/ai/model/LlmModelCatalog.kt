package com.litemusic.app.core.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class CachedModelCatalog(
    val providerId: String,
    val models: List<String>,
    val lastFetchedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CACHE_DURATION_MS = 3 * 24 * 60 * 60 * 1000L // 3 days (72 hours)
    }

    val isExpired: Boolean
        get() = System.currentTimeMillis() - lastFetchedTimestamp > CACHE_DURATION_MS

    val remainingHours: Long
        get() = ((lastFetchedTimestamp + CACHE_DURATION_MS) - System.currentTimeMillis()).coerceAtLeast(0L) / (1000L * 60 * 60)
}
