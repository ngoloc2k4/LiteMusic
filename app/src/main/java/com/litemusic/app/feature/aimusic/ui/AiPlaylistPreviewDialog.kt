package com.litemusic.app.feature.aimusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.litemusic.app.R
import com.litemusic.app.core.model.Song
import com.litemusic.app.feature.aimusic.domain.AiGenerationState

@Composable
fun AiPlaylistPreviewDialog(
    state: AiGenerationState,
    onDismiss: () -> Unit,
    onPlayNow: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onPlaySingleTrack: ((Song, List<Song>) -> Unit)? = null,
    onFallbackLocal: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.2.dp, Color(0xFFA855F7).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Dialog Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF3B82F6)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = stringResource(R.string.ai_playlist_curator_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Body content based on state
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state) {
                        is AiGenerationState.ParsingIntent,
                        is AiGenerationState.SearchingTracks,
                        is AiGenerationState.RankingTracks -> {
                            StepperProgressView(state)
                        }

                        is AiGenerationState.Success -> {
                            PlaylistSuccessView(
                                state = state,
                                onPlayAll = { tracks ->
                                    onPlayNow(tracks)
                                    onDismiss()
                                },
                                onShufflePlay = { tracks ->
                                    onPlayNow(tracks.shuffled())
                                    onDismiss()
                                },
                                onAddToQueue = { tracks ->
                                    onAddToQueue(tracks)
                                    onDismiss()
                                },
                                onPlaySingleTrack = { clickedSong, trackList ->
                                    if (onPlaySingleTrack != null) {
                                        onPlaySingleTrack(clickedSong, trackList)
                                    } else {
                                        onPlayNow(listOf(clickedSong) + trackList.filterNot { it.id == clickedSong.id })
                                    }
                                    onDismiss()
                                }
                            )
                        }

                        is AiGenerationState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(stringResource(R.string.ai_playlist_error_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(state.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                if (state.canFallbackLocal) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = onFallbackLocal) {
                                        Text(stringResource(R.string.ai_playlist_retry_local))
                                    }
                                }
                            }
                        }

                        AiGenerationState.Idle -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFA855F7))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperProgressView(state: AiGenerationState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        val step1Done = state is AiGenerationState.SearchingTracks || state is AiGenerationState.RankingTracks || state is AiGenerationState.Success
        val step2Done = state is AiGenerationState.RankingTracks || state is AiGenerationState.Success
        val step3Done = state is AiGenerationState.Success

        StepItem(
            stepNumber = 1,
            title = stringResource(R.string.ai_playlist_step_intent),
            subtitle = if (state is AiGenerationState.ParsingIntent) stringResource(R.string.ai_playlist_step_intent_sub, state.userPrompt) else stringResource(R.string.ai_playlist_step_completed),
            isDone = step1Done,
            isActive = state is AiGenerationState.ParsingIntent
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepItem(
            stepNumber = 2,
            title = stringResource(R.string.ai_playlist_step_search),
            subtitle = if (state is AiGenerationState.SearchingTracks) stringResource(R.string.ai_playlist_step_search_sub) else if (step2Done) stringResource(R.string.ai_playlist_step_search_done) else stringResource(R.string.ai_playlist_step_waiting),
            isDone = step2Done,
            isActive = state is AiGenerationState.SearchingTracks
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepItem(
            stepNumber = 3,
            title = stringResource(R.string.ai_playlist_step_rank),
            subtitle = if (state is AiGenerationState.RankingTracks) stringResource(R.string.ai_playlist_step_rank_sub, state.candidateCount) else if (step3Done) stringResource(R.string.ai_playlist_step_completed) else stringResource(R.string.ai_playlist_step_waiting),
            isDone = step3Done,
            isActive = state is AiGenerationState.RankingTracks
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepItem(
            stepNumber = 4,
            title = stringResource(R.string.ai_playlist_step_sequence),
            subtitle = if (state is AiGenerationState.RankingTracks) stringResource(R.string.ai_playlist_step_sequence_sub) else stringResource(R.string.ai_playlist_step_almost_done),
            isDone = false,
            isActive = state is AiGenerationState.RankingTracks
        )
    }
}

@Composable
private fun StepItem(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isDone: Boolean,
    isActive: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isDone) MaterialTheme.colorScheme.primary
                    else if (isActive) Color(0xFFA855F7)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            } else if (isActive) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("$stepNumber", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isActive || isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaylistSuccessView(
    state: AiGenerationState.Success,
    onPlayAll: (List<Song>) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onPlaySingleTrack: (Song, List<Song>) -> Unit
) {
    val firstArtwork = state.tracks.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // YouTube Music Playlist Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hero Artwork with ambient shadow
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (firstArtwork.isNotEmpty()) {
                            AsyncImage(
                                model = firstArtwork,
                                contentDescription = state.playlistTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFA855F7), Color(0xFF3B82F6))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Playlist Title
                    Text(
                        text = state.playlistTitle,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Metadata Subtitle
                    Text(
                        text = stringResource(R.string.ai_playlist_meta_info, state.tracks.size),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (state.playlistDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.playlistDescription,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // YouTube Music Action Pill Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play All Button
                        Button(
                            onClick = { onPlayAll(state.tracks) },
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.home_play_all), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Shuffle Button
                        OutlinedButton(
                            onClick = { onShufflePlay(state.tracks) },
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_shuffle), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Add to Queue Button
                        FilledTonalButton(
                            onClick = { onAddToQueue(state.tracks) },
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            // Section Header
            item {
                Text(
                    text = stringResource(R.string.ai_playlist_tracks_header, state.tracks.size),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            // Track Items
            itemsIndexed(state.tracks) { index, song ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySingleTrack(song, state.tracks) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(24.dp)
                        )

                        if (song.thumbnailUrl.isNotEmpty()) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("♫", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song.artist} • ${song.source.name.replace("_", " ")}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onPlaySingleTrack(song, state.tracks) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.ai_playlist_play_track),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
