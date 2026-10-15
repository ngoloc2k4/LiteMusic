package com.litemusic.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litemusic.app.core.library.LibraryManager
import com.litemusic.app.core.model.Song

enum class LibraryTab {
    FAVORITES,
    RECENT,
    BLOCKLIST
}

@Composable
fun LibraryScreen(
    libraryManager: LibraryManager,
    onPlaySong: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    isGlass: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.FAVORITES) }
    var favorites by remember { mutableStateOf(libraryManager.getFavorites()) }
    var recentSongs by remember { mutableStateOf(libraryManager.getRecentlyPlayed()) }
    var blockedTitles by remember { mutableStateOf(libraryManager.getBlockedSongTitles()) }
    var blockedArtists by remember { mutableStateOf(libraryManager.getBlockedArtists()) }

    fun refreshAll() {
        favorites = libraryManager.getFavorites()
        recentSongs = libraryManager.getRecentlyPlayed()
        blockedTitles = libraryManager.getBlockedSongTitles()
        blockedArtists = libraryManager.getBlockedArtists()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Library Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📚 Thư viện cá nhân",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Quản lý bài hát yêu thích, lịch sử nghe và danh sách chặn",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Sub-tabs Pill Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                LibraryTab.FAVORITES to "❤️ Yêu thích (${favorites.size})",
                LibraryTab.RECENT to "🕒 Gần đây (${recentSongs.size})",
                LibraryTab.BLOCKLIST to "🚫 Đã chặn (${blockedTitles.size + blockedArtists.size})"
            ).forEach { (tab, label) ->
                val isSelected = selectedTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedTab = tab
                        refreshAll()
                    },
                    label = { Text(label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Content based on sub-tab
        when (selectedTab) {
            LibraryTab.FAVORITES -> {
                if (favorites.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("💔", fontSize = 48.sp)
                            Text("Chưa có bài hát yêu thích nào", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                            Text("Bấm vào biểu tượng trái tim khi tìm kiếm để lưu vào đây", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayAll(favorites) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phát tất cả", fontSize = 12.sp)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(favorites) { song ->
                            LibrarySongRow(
                                song = song,
                                onPlay = { onPlaySong(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onRemoveFav = {
                                    libraryManager.toggleFavorite(song)
                                    refreshAll()
                                },
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }

            LibraryTab.RECENT -> {
                if (recentSongs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎧", fontSize = 48.sp)
                            Text("Chưa có bài hát nào nghe gần đây", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayAll(recentSongs) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phát lại tất cả", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                libraryManager.clearRecent()
                                refreshAll()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Xóa lịch sử", fontSize = 11.5.sp)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(recentSongs) { song ->
                            LibrarySongRow(
                                song = song,
                                onPlay = { onPlaySong(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onRemoveFav = null,
                                isGlass = isGlass
                            )
                        }
                    }
                }
            }

            LibraryTab.BLOCKLIST -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Text(
                            text = "Nghệ sĩ bị chặn (${blockedArtists.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (blockedArtists.isEmpty()) {
                        item {
                            Text("Không có nghệ sĩ nào trong danh sách chặn", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                blockedArtists.forEach { artist ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            libraryManager.unblockArtist(artist)
                                            refreshAll()
                                        },
                                        label = { Text(artist, fontSize = 12.sp) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = "Bỏ chặn", modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bài hát bị chặn (${blockedTitles.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (blockedTitles.isEmpty()) {
                        item {
                            Text("Không có bài hát nào bị chặn", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        items(blockedTitles.entries.toList()) { (songId, title) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                    TextButton(
                                        onClick = {
                                            libraryManager.unblockSong(songId)
                                            refreshAll()
                                        }
                                    ) {
                                        Text("Bỏ chặn", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySongRow(
    song: Song,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFav: (() -> Unit)?,
    isGlass: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isGlass) Color(0x331E293B) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isGlass) BorderStroke(1.dp, Color(0x3393C5FD)) else CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.thumbnailUrl.isNotEmpty()) {
                coil3.compose.AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(46.dp)
                        .androidx.compose.ui.draw.clip(RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .androidx.compose.ui.draw.clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎵", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.durationFormatted}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            if (onRemoveFav != null) {
                IconButton(onClick = onRemoveFav) {
                    Icon(Icons.Default.Favorite, contentDescription = "Bỏ thích", tint = Color(0xFFEF4444))
                }
            }

            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Thêm vào hàng đợi", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
