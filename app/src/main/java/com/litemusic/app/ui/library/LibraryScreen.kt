package com.litemusic.app.ui.library

import androidx.compose.ui.res.stringResource
import com.litemusic.app.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.litemusic.app.core.library.LibraryManager
import com.litemusic.app.core.model.Song

enum class LibraryTab {
    FAVORITES,
    RECENT,
    BLOCKLIST
}

@OptIn(ExperimentalLayoutApi::class)
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
    var blockedKeywords by remember { mutableStateOf(libraryManager.getBlockedKeywords()) }
    var showAddBlockDialog by remember { mutableStateOf(false) }

    fun refreshAll() {
        favorites = libraryManager.getFavorites()
        recentSongs = libraryManager.getRecentlyPlayed()
        blockedTitles = libraryManager.getBlockedSongTitles()
        blockedArtists = libraryManager.getBlockedArtists()
        blockedKeywords = libraryManager.getBlockedKeywords()
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
                    text = stringResource(R.string.library_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.library_subtitle),
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
                LibraryTab.FAVORITES to stringResource(R.string.library_tab_favorites, favorites.size),
                LibraryTab.RECENT to stringResource(R.string.library_tab_recent, recentSongs.size),
                LibraryTab.BLOCKLIST to stringResource(R.string.library_tab_blocklist, blockedTitles.size + blockedArtists.size)
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
                            Text(stringResource(R.string.library_empty_favorites), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                            Text(stringResource(R.string.library_empty_favorites_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        // YouTube Music Playlist Hero Header
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val firstFavArt = favorites.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl ?: ""
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (firstFavArt.isNotEmpty()) {
                                        AsyncImage(
                                            model = firstFavArt,
                                            contentDescription = stringResource(R.string.action_favorite),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text("❤️", fontSize = 40.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(stringResource(R.string.library_favorites_header), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Text(stringResource(R.string.library_favorites_count, favorites.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { onPlayAll(favorites) },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_play_all), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onPlayAll(favorites.shuffled()) },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_shuffle), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(
                                        onClick = { favorites.forEach { onAddToQueue(it) } },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_add_to_queue), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

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
                            Text(stringResource(R.string.library_empty_recent), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val firstRecentArt = recentSongs.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl ?: ""
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (firstRecentArt.isNotEmpty()) {
                                        AsyncImage(
                                            model = firstRecentArt,
                                            contentDescription = stringResource(R.string.nav_library),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text("🕒", fontSize = 40.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(stringResource(R.string.library_recent_header), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Text(stringResource(R.string.library_recent_count, recentSongs.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { onPlayAll(recentSongs) },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_play_all), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onPlayAll(recentSongs.shuffled()) },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_shuffle), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            libraryManager.clearRecent()
                                            refreshAll()
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.library_clear_history), fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

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
                if (showAddBlockDialog) {
                    AddBlockItemDialog(
                        onDismiss = { showAddBlockDialog = false },
                        onAdd = { text, isArtist ->
                            if (isArtist) {
                                libraryManager.blockArtist(text)
                            } else {
                                libraryManager.blockKeyword(text)
                            }
                            refreshAll()
                            showAddBlockDialog = false
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.library_blocklist_header),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = { showAddBlockDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.library_add_manually), fontSize = 12.sp)
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.library_blocked_artists, blockedArtists.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (blockedArtists.isEmpty()) {
                        item {
                            Text(stringResource(R.string.library_empty_blocked_artists), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_unblock), modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.library_blocked_keywords, blockedKeywords.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (blockedKeywords.isEmpty()) {
                        item {
                            Text(stringResource(R.string.library_empty_blocked_keywords), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                blockedKeywords.forEach { keyword ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            libraryManager.unblockKeyword(keyword)
                                            refreshAll()
                                        },
                                        label = { Text(keyword, fontSize = 12.sp) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_unblock), modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.library_blocked_keywords, blockedTitles.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (blockedTitles.isEmpty()) {
                        item {
                            Text(stringResource(R.string.library_empty_blocked_keywords), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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
                                        Text(stringResource(R.string.action_unblock), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
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
fun AddBlockItemDialog(
    onDismiss: () -> Unit,
    onAdd: (text: String, isArtist: Boolean) -> Unit
) {
    var blockText by remember { mutableStateOf("") }
    var isArtistType by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.library_add_block_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.library_blocklist_desc),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isArtistType,
                        onClick = { isArtistType = true },
                        label = { Text(stringResource(R.string.library_type_channel_artist), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = !isArtistType,
                        onClick = { isArtistType = false },
                        label = { Text(stringResource(R.string.library_type_title_keyword), fontSize = 12.sp) }
                    )
                }

                OutlinedTextField(
                    value = blockText,
                    onValueChange = { blockText = it },
                    placeholder = {
                        Text(
                            if (isArtistType) stringResource(R.string.library_input_artist_hint) else stringResource(R.string.library_input_title_hint),
                            fontSize = 12.5.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (blockText.isNotBlank()) {
                        onAdd(blockText.trim(), isArtistType)
                    }
                },
                enabled = blockText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_block))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
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
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp)),
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
                    overflow = TextOverflow.Ellipsis
                )
                val durationText = if (song.durationSeconds > 0) {
                    val m = song.durationSeconds / 60
                    val s = song.durationSeconds % 60
                    String.format("%02d:%02d", m, s)
                } else ""
                val subtitle = if (durationText.isNotEmpty()) "${song.artist} • $durationText" else song.artist
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onRemoveFav != null) {
                IconButton(onClick = onRemoveFav) {
                    Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.action_remove_from_favorites), tint = Color(0xFFEF4444))
                }
            }

            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.action_add_to_queue), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
