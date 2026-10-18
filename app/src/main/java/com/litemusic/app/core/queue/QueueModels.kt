package com.litemusic.app.core.queue

import com.litemusic.app.core.model.Song
import java.util.UUID

/**
 * Origin of a song placed into the playback queue.
 */
enum class QueueSource {
    MANUAL,             // Explicitly added by the user (Add to Queue)
    PLAY_NEXT,          // Prioritized by user to play immediately after current track
    AI_RECOMMENDATION,  // Generated via AI Playlist or AI Prompt
    RADIO,              // Official YouTube Music RDAMVM Auto-Mix Radio
    PLAYLIST,           // Added from an imported or saved playlist
    AUTOPLAY            // Endless stream fallback (Infinite playback)
}

/**
 * Unique entry in the playback queue.
 * Uses unique [queueEntryId] to gracefully support duplicate songs in the queue
 * without index or key collision errors during reordering or deletion.
 */
data class QueueEntry(
    val queueEntryId: String = UUID.randomUUID().toString(),
    val song: Song,
    val source: QueueSource = QueueSource.MANUAL,
    val addedAt: Long = System.currentTimeMillis(),
    val recommendationReason: String? = null
)

/**
 * Complete immutable snapshot of the playback queue.
 */
data class QueueState(
    val currentSong: Song? = null,
    val manualQueue: List<QueueEntry> = emptyList(),
    val autoQueue: List<QueueEntry> = emptyList(),
    val isAutoQueueEnabled: Boolean = true,
    val isShuffled: Boolean = false
) {
    /**
     * Complete upcoming list: Manual entries take strict priority over auto-suggestions.
     */
    val allUpcoming: List<QueueEntry>
        get() = manualQueue + (if (isAutoQueueEnabled) autoQueue else emptyList())

    val totalUpcomingCount: Int
        get() = allUpcoming.size

    val hasUpcoming: Boolean
        get() = totalUpcomingCount > 0

    val totalDurationSeconds: Long
        get() = allUpcoming.sumOf { it.song.durationSeconds }

    fun formattedTotalDuration(): String {
        val totalSec = totalDurationSeconds
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes} min"
            else -> "${totalSec}s"
        }
    }
}

/**
 * Sealed class for the Undo history stack.
 */
sealed class QueueUndoAction {
    data class RemoveSingle(
        val entry: QueueEntry,
        val originalIndex: Int,
        val wasManual: Boolean
    ) : QueueUndoAction()

    data class ClearQueue(
        val manualBackup: List<QueueEntry>,
        val autoBackup: List<QueueEntry>
    ) : QueueUndoAction()
}

/**
 * UI Action bundle for a queue entry item.
 * Eliminates parameter clumps across queue list rendering.
 */
data class QueueItemActions(
    val onPlay: () -> Unit,
    val onPlayNext: () -> Unit,
    val onMoveUp: () -> Unit = {},
    val onMoveDown: () -> Unit = {},
    val onDelete: () -> Unit,
    val onPromoteToManual: (() -> Unit)? = null
)

