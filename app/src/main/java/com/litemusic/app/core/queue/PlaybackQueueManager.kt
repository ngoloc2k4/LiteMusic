package com.litemusic.app.core.queue

import android.content.Context
import com.litemusic.app.core.library.LibraryManager
import com.litemusic.app.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections
import java.util.LinkedList

/**
 * Autonomous Playback Queue Engine.
 * Decoupled from Activity and UI lifecycles.
 * Maintains strict priority of User-Curated (Manual) queue over System/AI (Auto) queue,
 * supports smooth reordering, Play Next, Swipe Dismiss, and multi-level Undo history.
 */
class PlaybackQueueManager private constructor() {

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    // Undo history stack (capped at 10 actions to prevent memory bloat)
    private val undoStack = Collections.synchronizedList(LinkedList<QueueUndoAction>())
    private val maxUndoEntries = 10

    companion object {
        @Volatile
        private var instance: PlaybackQueueManager? = null

        fun getInstance(): PlaybackQueueManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackQueueManager().also { instance = it }
            }
        }
    }

    /**
     * Updates the currently playing song in the queue state.
     */
    fun setNowPlaying(song: Song?) {
        _queueState.update { current ->
            current.copy(currentSong = song)
        }
    }

    /**
     * Adds a song to the end of the manual queue.
     */
    fun addToManualQueue(song: Song, source: QueueSource = QueueSource.MANUAL): QueueEntry {
        val entry = QueueEntry(song = song, source = source)
        _queueState.update { current ->
            current.copy(manualQueue = current.manualQueue + entry)
        }
        return entry
    }

    /**
     * Batch adds multiple songs to the end of the manual queue.
     */
    fun addMultipleToManualQueue(songs: List<Song>, source: QueueSource = QueueSource.MANUAL): List<QueueEntry> {
        val entries = songs.map { QueueEntry(song = it, source = source) }
        _queueState.update { current ->
            current.copy(manualQueue = current.manualQueue + entries)
        }
        return entries
    }

    /**
     * Inserts a song at the very top of the upcoming queue (immediately after current song).
     */
    fun playNext(song: Song): QueueEntry {
        val entry = QueueEntry(song = song, source = QueueSource.PLAY_NEXT)
        _queueState.update { current ->
            current.copy(manualQueue = listOf(entry) + current.manualQueue)
        }
        return entry
    }

    /**
     * Smoothly reorders an item inside the manual queue.
     */
    fun reorderManualQueue(fromIndex: Int, toIndex: Int) {
        _queueState.update { current ->
            val list = current.manualQueue.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                current.copy(manualQueue = list)
            } else {
                current
            }
        }
    }

    /**
     * Moves a manual entry one position up.
     */
    fun moveUp(queueEntryId: String): Boolean {
        var changed = false
        _queueState.update { current ->
            val list = current.manualQueue.toMutableList()
            val idx = list.indexOfFirst { it.queueEntryId == queueEntryId }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                changed = true
                current.copy(manualQueue = list)
            } else {
                current
            }
        }
        return changed
    }

    /**
     * Moves a manual entry one position down.
     */
    fun moveDown(queueEntryId: String): Boolean {
        var changed = false
        _queueState.update { current ->
            val list = current.manualQueue.toMutableList()
            val idx = list.indexOfFirst { it.queueEntryId == queueEntryId }
            if (idx >= 0 && idx < list.lastIndex) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                changed = true
                current.copy(manualQueue = list)
            } else {
                current
            }
        }
        return changed
    }

    /**
     * Removes an entry from either manual or auto queue.
     * Records the deletion into the undo stack.
     */
    fun removeEntry(queueEntryId: String): QueueEntry? {
        var removedEntry: QueueEntry? = null
        var wasManual = false
        var origIndex = -1

        _queueState.update { current ->
            val mIndex = current.manualQueue.indexOfFirst { it.queueEntryId == queueEntryId }
            if (mIndex >= 0) {
                removedEntry = current.manualQueue[mIndex]
                wasManual = true
                origIndex = mIndex
                current.copy(manualQueue = current.manualQueue.toMutableList().apply { removeAt(mIndex) })
            } else {
                val aIndex = current.autoQueue.indexOfFirst { it.queueEntryId == queueEntryId }
                if (aIndex >= 0) {
                    removedEntry = current.autoQueue[aIndex]
                    wasManual = false
                    origIndex = aIndex
                    current.copy(autoQueue = current.autoQueue.toMutableList().apply { removeAt(aIndex) })
                } else {
                    current
                }
            }
        }

        if (removedEntry != null) {
            pushUndo(QueueUndoAction.RemoveSingle(removedEntry!!, origIndex, wasManual))
        }
        return removedEntry
    }

    /**
     * Clears only the manual queue, leaving auto-suggestions intact.
     * Backs up to undo stack.
     */
    fun clearManualQueue(): List<QueueEntry> {
        val backup = _queueState.value.manualQueue
        if (backup.isEmpty()) return emptyList()

        pushUndo(QueueUndoAction.ClearQueue(manualBackup = backup, autoBackup = emptyList()))
        _queueState.update { it.copy(manualQueue = emptyList()) }
        return backup
    }

    /**
     * Clears all upcoming items (both manual and auto).
     * Backs up to undo stack.
     */
    fun clearAllQueue() {
        val mBackup = _queueState.value.manualQueue
        val aBackup = _queueState.value.autoQueue
        if (mBackup.isEmpty() && aBackup.isEmpty()) return

        pushUndo(QueueUndoAction.ClearQueue(manualBackup = mBackup, autoBackup = aBackup))
        _queueState.update { it.copy(manualQueue = emptyList(), autoQueue = emptyList()) }
    }

    /**
     * Restores the most recently undone action.
     * Returns a string describing what was restored, or null if undo stack was empty.
     */
    fun undo(): String? {
        val action = synchronized(undoStack) {
            if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex) else null
        } ?: return null

        return when (action) {
            is QueueUndoAction.RemoveSingle -> {
                _queueState.update { current ->
                    if (action.wasManual) {
                        val list = current.manualQueue.toMutableList()
                        val safeIndex = action.originalIndex.coerceIn(0, list.size)
                        list.add(safeIndex, action.entry)
                        current.copy(manualQueue = list)
                    } else {
                        val list = current.autoQueue.toMutableList()
                        val safeIndex = action.originalIndex.coerceIn(0, list.size)
                        list.add(safeIndex, action.entry)
                        current.copy(autoQueue = list)
                    }
                }
                action.entry.song.title
            }
            is QueueUndoAction.ClearQueue -> {
                _queueState.update { current ->
                    current.copy(
                        manualQueue = action.manualBackup,
                        autoQueue = if (action.autoBackup.isNotEmpty()) action.autoBackup else current.autoQueue
                    )
                }
                "${action.manualBackup.size + action.autoBackup.size} tracks"
            }
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    private fun pushUndo(action: QueueUndoAction) {
        synchronized(undoStack) {
            undoStack.add(action)
            if (undoStack.size > maxUndoEntries) {
                undoStack.removeAt(0)
            }
        }
    }

    /**
     * Sets or refreshes the auto-suggestions queue WITHOUT touching user-added manual songs.
     */
    fun setAutoQueueSongs(
        songs: List<Song>,
        source: QueueSource = QueueSource.RADIO,
        reason: String? = "Based on current track"
    ) {
        val entries = songs.map {
            QueueEntry(
                song = it,
                source = source,
                recommendationReason = reason
            )
        }
        _queueState.update { current ->
            current.copy(autoQueue = entries)
        }
    }

    /**
     * Toggles whether auto-suggestions are appended to the playback sequence.
     */
    fun toggleAutoQueueEnabled(enabled: Boolean? = null) {
        _queueState.update { current ->
            current.copy(isAutoQueueEnabled = enabled ?: !current.isAutoQueueEnabled)
        }
    }

    /**
     * Promotes an auto-suggested track into the user's manual queue.
     */
    fun promoteToManualQueue(queueEntryId: String): Boolean {
        var promoted = false
        _queueState.update { current ->
            val item = current.autoQueue.find { it.queueEntryId == queueEntryId }
            if (item != null) {
                promoted = true
                val newAuto = current.autoQueue.filterNot { it.queueEntryId == queueEntryId }
                val newManual = current.manualQueue + item.copy(source = QueueSource.MANUAL, recommendationReason = null)
                current.copy(manualQueue = newManual, autoQueue = newAuto)
            } else {
                current
            }
        }
        return promoted
    }

    /**
     * Retrieves and consumes the next song to play.
     * Takes from manual queue first; if manual queue is empty, takes from auto queue (if enabled).
     */
    fun popNextSong(): Song? {
        var nextSong: Song? = null
        _queueState.update { current ->
            if (current.manualQueue.isNotEmpty()) {
                val nextEntry = current.manualQueue.first()
                nextSong = nextEntry.song
                current.copy(manualQueue = current.manualQueue.drop(1), currentSong = nextSong)
            } else if (current.isAutoQueueEnabled && current.autoQueue.isNotEmpty()) {
                val nextEntry = current.autoQueue.first()
                nextSong = nextEntry.song
                current.copy(autoQueue = current.autoQueue.drop(1), currentSong = nextSong)
            } else {
                current
            }
        }
        return nextSong
    }

    /**
     * Shuffles the manual upcoming queue.
     */
    fun shuffleManualQueue() {
        _queueState.update { current ->
            if (current.manualQueue.isNotEmpty()) {
                current.copy(manualQueue = current.manualQueue.shuffled(), isShuffled = true)
            } else {
                current
            }
        }
    }

    /**
     * Saves all upcoming songs in the queue as a user playlist in LibraryManager.
     */
    fun saveQueueAsPlaylist(context: Context, playlistName: String): Boolean {
        val songs = _queueState.value.allUpcoming.map { it.song }
        if (songs.isEmpty()) return false
        val lib = LibraryManager.getInstance(context)
        lib.createPlaylist(playlistName, songs)
        return true
    }

    /**
     * Resets queue state completely (e.g. when user stops playback).
     */
    fun reset() {
        undoStack.clear()
        _queueState.value = QueueState()
    }
}
