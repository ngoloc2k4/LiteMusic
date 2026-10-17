package com.litemusic.app.ui.queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.litemusic.app.R
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import com.litemusic.app.core.queue.PlaybackQueueManager
import com.litemusic.app.core.queue.QueueEntry
import com.litemusic.app.core.queue.QueueSource
import com.litemusic.app.core.queue.QueueState
import com.litemusic.app.getEffectiveArtworkUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRefreshAutoQueue: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        val context = LocalContext.current
        val queueManager = remember { PlaybackQueueManager.getInstance() }
        val queueState by queueManager.queueState.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var showSavePlaylistDialog by remember { mutableStateOf(false) }
        var playlistNameInput by remember { mutableStateOf("") }
        var showClearConfirmDialog by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 76.dp)
                        ) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                actionColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                .statusBarsPadding()
                        ) {
                            // Top Drag Handle Indicator
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                )
                            }

                            // Top Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onClose) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.action_close),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(
                                        text = stringResource(R.string.queue_header_title),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.queue_header_subtitle,
                                            queueState.totalUpcomingCount,
                                            queueState.formattedTotalDuration()
                                        ),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // Shuffle Queue
                                IconButton(onClick = {
                                    queueManager.shuffleManualQueue()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_queue_shuffled))
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = stringResource(R.string.action_shuffle),
                                        tint = if (queueState.isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Save Queue as Playlist
                                IconButton(onClick = {
                                    playlistNameInput = "Queue " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                    showSavePlaylistDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = stringResource(R.string.queue_save_playlist),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Clear Queue Options
                                IconButton(onClick = { showClearConfirmDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.queue_clear_all),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(bottom = 76.dp), // Extra room for bottom fixed mini player
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ================= SECTION 1: NOW PLAYING =================
                        item(key = "section_now_playing") {
                            Text(
                                text = stringResource(R.string.queue_now_playing_badge),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 4.dp, top = 2.dp)
                            )

                            if (currentSong != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenFullPlayer() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(46.dp)) {
                                            val effectiveArt = getEffectiveArtworkUrl(currentSong.thumbnailUrl, currentSong.sourceId, currentSong.id)
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                                                    .crossfade(true)
                                                    .placeholder(R.drawable.ic_music_note)
                                                    .error(R.drawable.ic_music_note)
                                                    .build(),
                                                contentDescription = currentSong.title,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            if (isPlaying) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.GraphicEq,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentSong.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = currentSong.artist,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                SourceBadge(source = currentSong.source)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = stringResource(R.string.player_now_playing),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ================= SECTION 2: NEXT FROM YOU (MANUAL QUEUE) =================
                        item(key = "section_manual_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${stringResource(R.string.queue_up_next_manual)} (${queueState.manualQueue.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 1.sp
                                )

                                if (queueState.manualQueue.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            queueManager.clearManualQueue()
                                            scope.launch {
                                                val res = snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.toast_queue_cleared),
                                                    actionLabel = context.getString(R.string.queue_undo),
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    queueManager.undo()
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_clear),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (queueState.manualQueue.isEmpty()) {
                            item(key = "manual_empty_card") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.queue_up_next_empty),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.queue_up_next_empty_hint),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = queueState.manualQueue,
                                key = { _, entry -> entry.queueEntryId }
                            ) { index, entry ->
                                QueueEntryCard(
                                    index = index + 1,
                                    entry = entry,
                                    isManual = true,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < queueState.manualQueue.lastIndex,
                                    onPlay = {
                                        queueManager.removeEntry(entry.queueEntryId)
                                        onPlaySong(entry.song)
                                    },
                                    onPlayNext = {
                                        queueManager.reorderManualQueue(index, 0)
                                    },
                                    onMoveUp = {
                                        queueManager.moveUp(entry.queueEntryId)
                                    },
                                    onMoveDown = {
                                        queueManager.moveDown(entry.queueEntryId)
                                    },
                                    onDelete = {
                                        queueManager.removeEntry(entry.queueEntryId)
                                        scope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.queue_removed_msg, entry.song.title),
                                                actionLabel = context.getString(R.string.queue_undo),
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                queueManager.undo()
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // ================= SECTION 3: AUTO RECOMMENDATIONS =================
                        item(key = "section_auto_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stringResource(R.string.queue_auto_suggestions)} (${queueState.autoQueue.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.outline,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.queue_auto_desc),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Refresh Suggestions Button
                                    FilledTonalButton(
                                        onClick = onRefreshAutoQueue,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(stringResource(R.string.queue_refresh_suggestions), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Autoplay Switch
                                    FilterChip(
                                        selected = queueState.isAutoQueueEnabled,
                                        onClick = { queueManager.toggleAutoQueueEnabled() },
                                        label = {
                                            Text(
                                                text = stringResource(R.string.queue_auto_toggle),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        if (queueState.isAutoQueueEnabled) {
                            itemsIndexed(
                                items = queueState.autoQueue,
                                key = { _, entry -> entry.queueEntryId }
                            ) { _, entry ->
                                QueueEntryCard(
                                    index = null,
                                    entry = entry,
                                    isManual = false,
                                    canMoveUp = false,
                                    canMoveDown = false,
                                    onPlay = {
                                        queueManager.removeEntry(entry.queueEntryId)
                                        onPlaySong(entry.song)
                                    },
                                    onPlayNext = {
                                        queueManager.removeEntry(entry.queueEntryId)
                                        queueManager.playNext(entry.song)
                                    },
                                    onMoveUp = {},
                                    onMoveDown = {},
                                    onDelete = {
                                        queueManager.removeEntry(entry.queueEntryId)
                                    },
                                    onPromoteToManual = {
                                        queueManager.promoteToManualQueue(entry.queueEntryId)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.queue_promoted_msg, entry.song.title)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ================= FIXED MINI PLAYER PINNED AT BOTTOM =================
                if (currentSong != null) {
                    FixedQueueMiniPlayer(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        song = currentSong,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                        onSkipNext = onSkipNext,
                        onClick = {
                            onClose()
                            onOpenFullPlayer()
                        }
                    )
                }
            }

            // Save Playlist Dialog
            if (showSavePlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showSavePlaylistDialog = false },
                    title = { Text(stringResource(R.string.queue_save_playlist)) },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.queue_save_playlist_title),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = playlistNameInput,
                                onValueChange = { playlistNameInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val success = queueManager.saveQueueAsPlaylist(context, playlistNameInput)
                            showSavePlaylistDialog = false
                            if (success) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.queue_save_playlist_success, playlistNameInput)
                                    )
                                }
                            }
                        }) {
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSavePlaylistDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            // Clear Confirmation Dialog
            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    title = { Text(stringResource(R.string.queue_clear_all)) },
                    text = { Text(stringResource(R.string.player_queue_empty_desc)) },
                    confirmButton = {
                        TextButton(onClick = {
                            queueManager.clearAllQueue()
                            showClearConfirmDialog = false
                            scope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.toast_queue_cleared),
                                    actionLabel = context.getString(R.string.queue_undo)
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    queueManager.undo()
                                }
                            }
                        }) {
                            Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun QueueEntryCard(
    index: Int?,
    entry: QueueEntry,
    isManual: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onPromoteToManual: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isManual) {
                // Drag handle icon for intuitive manual queue reordering
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp)
                )

                if (index != null) {
                    Text(
                        text = "$index",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(20.dp)
                    )
                }
            }

            // Thumbnail
            val effectiveArt = getEffectiveArtworkUrl(entry.song.thumbnailUrl, entry.song.sourceId, entry.song.id)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                    .crossfade(true)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .build(),
                contentDescription = entry.song.title,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(9.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.song.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = entry.song.artist,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Origin Pill (Manual, Radio, AI)
                    val originText = when (entry.source) {
                        QueueSource.MANUAL -> stringResource(R.string.queue_source_manual)
                        QueueSource.PLAY_NEXT -> stringResource(R.string.queue_play_next)
                        QueueSource.RADIO -> stringResource(R.string.queue_source_radio)
                        QueueSource.AI_RECOMMENDATION -> stringResource(R.string.queue_source_ai)
                        QueueSource.PLAYLIST -> stringResource(R.string.queue_source_playlist)
                        QueueSource.AUTOPLAY -> stringResource(R.string.queue_auto_toggle)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isManual) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = originText,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Duration
            if (entry.song.durationSeconds > 0) {
                val min = entry.song.durationSeconds / 60
                val sec = entry.song.durationSeconds % 60
                Text(
                    text = String.format("%d:%02d", min, sec),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Quick promote button for auto-suggestions
            if (!isManual && onPromoteToManual != null) {
                IconButton(
                    onClick = onPromoteToManual,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_to_queue),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3-Dots Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_options),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.queue_play_now)) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.queue_play_next)) },
                        leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onPlayNext()
                        }
                    )
                    if (isManual && canMoveUp) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_move_up)) },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (isManual && canMoveDown) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.queue_move_down)) },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FixedQueueMiniPlayer(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val effectiveArt = getEffectiveArtworkUrl(song.thumbnailUrl, song.sourceId, song.id)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                    .crossfade(true)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .build(),
                contentDescription = song.title,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${stringResource(R.string.player_tap_to_return_playing)}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun SourceBadge(source: AudioSource) {
    val (label, bg, fg) = when (source) {
        AudioSource.YOUTUBE_MUSIC -> Triple("YT Music", Color(0xFFFF0000).copy(alpha = 0.15f), Color(0xFFFF4E4E))
        AudioSource.YOUTUBE_VIDEO -> Triple("YouTube", Color(0xFFFF0000).copy(alpha = 0.15f), Color(0xFFFF4E4E))
        AudioSource.SOUNDCLOUD -> Triple("SoundCloud", Color(0xFFFF5500).copy(alpha = 0.15f), Color(0xFFFF7700))
        AudioSource.SPOTIFY -> Triple("Spotify", Color(0xFF1DB954).copy(alpha = 0.15f), Color(0xFF1ED760))
        AudioSource.ALL -> Triple("All", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
