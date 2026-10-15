package com.litemusic.app.ui.search

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
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import com.litemusic.app.feature.aimusic.ui.AiSearchTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSource: AudioSource,
    onSourceSelect: (AudioSource) -> Unit,
    isSearching: Boolean,
    searchResults: List<Song>,
    onPlaySong: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    isFavorite: (String) -> Boolean,
    onBlockSong: (Song) -> Unit,
    onBlockArtist: (String) -> Unit,
    onAiSearchClick: () -> Unit,
    isGlass: Boolean = false
) {
    var recentSearches by remember {
        mutableStateOf(listOf("V-Pop Thịnh Hành", "Lo-fi Chill Buổi Tối", "Sơn Tùng M-TP", "Rap Việt", "Acoustic Nhẹ Nhàng"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm bài hát, ca sĩ, album...", fontSize = 13.5.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isGlass) Color(0x331E293B) else MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = if (isGlass) Color(0x221E293B) else MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        // Source Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val sources = listOf(
                AudioSource.YOUTUBE_MUSIC to "🎵 YouTube Music",
                AudioSource.YOUTUBE_VIDEO to "🎬 YouTube Video",
                AudioSource.SOUNDCLOUD to "☁️ SoundCloud",
                AudioSource.SPOTIFY to "🟢 Spotify"
            )
            items(sources) { (src, label) ->
                val isSelected = selectedSource == src
                FilterChip(
                    selected = isSelected,
                    onClick = { onSourceSelect(src) },
                    label = { Text(label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        if (searchQuery.isEmpty()) {
            // Recent searches & trending suggestions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gợi ý tìm kiếm phổ biến", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentSearches) { tag ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { onSearchQueryChange(tag) }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            // Results List
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Text(
                            text = "Kết quả tìm kiếm (${searchResults.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    items(searchResults) { song ->
                        SearchSongCard(
                            song = song,
                            isFav = isFavorite(song.id),
                            onPlay = { onPlaySong(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onToggleFav = { onToggleFavorite(song) },
                            onBlockSong = { onBlockSong(song) },
                            onBlockArtist = { onBlockArtist(song.artist) },
                            isGlass = isGlass
                        )
                    }

                    // Bottom AI Search Tile
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        AiSearchTile(
                            query = searchQuery,
                            onClick = onAiSearchClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchSongCard(
    song: Song,
    isFav: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFav: () -> Unit,
    onBlockSong: () -> Unit,
    onBlockArtist: () -> Unit,
    isGlass: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

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
            // Artwork
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

            // Favorite button
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Yêu thích",
                    tint = if (isFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                )
            }

            // 3-dot dropdown menu for Block options & Queue
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn", tint = MaterialTheme.colorScheme.outline)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Thêm vào danh sách chờ") },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            onAddToQueue()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Chặn bài hát này") },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onBlockSong()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Chặn nghệ sĩ '${song.artist}'") },
                        leadingIcon = { Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onBlockArtist()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}
