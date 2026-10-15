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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.litemusic.app.core.model.Song
import com.litemusic.app.feature.aimusic.domain.AiGenerationState

@Composable
fun AiPlaylistPreviewDialog(
    state: AiGenerationState,
    onDismiss: () -> Unit,
    onPlayNow: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onFallbackLocal: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.2.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Dialog Header
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
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF3B82F6)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "AI Playlist Curator",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Body content based on state
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state) {
                        is AiGenerationState.ParsingIntent,
                        is AiGenerationState.SearchingTracks,
                        is AiGenerationState.RankingTracks -> {
                            StepperProgressView(state)
                        }

                        is AiGenerationState.Success -> {
                            PlaylistSuccessView(state)
                        }

                        is AiGenerationState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Không thể tạo playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(state.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                if (state.canFallbackLocal) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = onFallbackLocal) {
                                        Text("Thử lại bằng Thuật toán cục bộ")
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

                // Bottom Action buttons
                if (state is AiGenerationState.Success) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onAddToQueue(state.tracks)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onPlayNow(state.tracks)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("▶ Phát ngay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            title = "Phân tích ý định & lập kế hoạch",
            subtitle = if (state is AiGenerationState.ParsingIntent) "Đang phân tích prompt: \"${state.userPrompt}\"" else "Đã hoàn thành",
            isDone = step1Done,
            isActive = state is AiGenerationState.ParsingIntent
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepItem(
            stepNumber = 2,
            title = "Tìm kiếm đa nguồn song song",
            subtitle = if (state is AiGenerationState.SearchingTracks) "Đang thu thập ứng viên từ YouTube & SoundCloud..." else if (step2Done) "Đã thu thập ứng viên" else "Đang chờ",
            isDone = step2Done,
            isActive = state is AiGenerationState.SearchingTracks
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepItem(
            stepNumber = 3,
            title = "Lọc trùng & xếp hạng cục bộ",
            subtitle = if (state is AiGenerationState.RankingTracks) "Đang chấm điểm ${state.candidateCount} bài hát..." else if (step3Done) "Đã hoàn thành" else "Đang chờ",
            isDone = step3Done,
            isActive = state is AiGenerationState.RankingTracks
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepItem(
            stepNumber = 4,
            title = "AI hoàn thiện thứ tự danh sách phát",
            subtitle = if (state is AiGenerationState.RankingTracks) "Đang tối ưu giai điệu chuyển tiếp êm ái..." else "Sắp hoàn tất",
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
private fun PlaylistSuccessView(state: AiGenerationState.Success) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.playlistTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFA855F7).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${state.tracks.size} bài",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC084FC),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.playlistDescription,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Người giám tuyển: ${state.providerUsed}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Danh sách bài hát (${state.tracks.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(state.tracks) { index, song ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(20.dp))
                        if (song.thumbnailUrl.isNotEmpty()) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Text("♫", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${song.artist} • ${song.source.name.replace("_", " ")}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
