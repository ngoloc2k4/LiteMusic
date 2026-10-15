package com.litemusic.app.ui.search

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import com.litemusic.app.feature.aimusic.ui.AiSearchTile

data class SearchBrowseCategory(
    val title: String,
    val query: String,
    val subtitle: String,
    val icon: String,
    val gradient: List<Color>
)

val BROWSE_CATEGORIES = listOf(
    SearchBrowseCategory("V-Pop Thịnh Hành", "V-Pop Thịnh Hành", "Các hit nóng nhất hiện nay", "🔥", listOf(Color(0xFFE11D48), Color(0xFF9333EA))),
    SearchBrowseCategory("Lo-fi Thư Giãn", "Lo-fi Chill Buổi Tối", "Thư giãn, tập trung & ngủ ngon", "☕", listOf(Color(0xFF0284C7), Color(0xFF0D9488))),
    SearchBrowseCategory("Acoustic & Cafe", "Acoustic Nhẹ Nhàng", "Giai điệu mộc mạc lắng đọng", "🎸", listOf(Color(0xFFD97706), Color(0xFFEA580C))),
    SearchBrowseCategory("EDM & Remix", "EDM Remix Cực Bốc", "Bùng nổ năng lượng tiệc tùng", "⚡", listOf(Color(0xFF7C3AED), Color(0xFFDB2777))),
    SearchBrowseCategory("Gym & Workout", "Gym Workout Động Lực", "Đốt cháy calo cùng nhịp bass", "🏋️", listOf(Color(0xFFDC2626), Color(0xFF991B1B))),
    SearchBrowseCategory("Rap Việt Đỉnh Cao", "Rap Việt", "Punchline chất, flow mượt mà", "🎤", listOf(Color(0xFF059669), Color(0xFF10B981))),
    SearchBrowseCategory("Nhạc Mưa & Buồn", "Nhạc Mưa Chill", "Tâm trạng những ngày mưa rơi", "🌧️", listOf(Color(0xFF475569), Color(0xFF1E293B))),
    SearchBrowseCategory("Bolero Tuyển Chọn", "Bolero Trữ Tình Hay Nhất", "Tình khúc quê hương ngọt ngào", "📻", listOf(Color(0xFFB45309), Color(0xFF78350F)))
)

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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm bài hát, nghệ sĩ, tâm trạng...", fontSize = 13.5.sp) },
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
            // Browse & Discovery Mode
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // Section: Recent searches
                item {
                    Text(
                        text = "Gợi ý tìm kiếm phổ biến",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentSearches) { tag ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { onSearchQueryChange(tag) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Section: Browse Categories
                item {
                    Column {
                        Text(
                            text = "Khám phá danh mục",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Duyệt tìm theo thể loại & tâm trạng phổ biến",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 2-column grid of categories
                items(BROWSE_CATEGORIES.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { cat ->
                            Box(modifier = Modifier.weight(1f)) {
                                BrowseCategoryCard(
                                    category = cat,
                                    onClick = { onSearchQueryChange(cat.query) }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            // Search Results Mode
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                        Text(
                            text = "Đang tìm kiếm '$searchQuery'...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 42.sp)
                        Text(
                            text = "Không tìm thấy kết quả phù hợp",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Thử tìm với tên bài hát khác hoặc đổi nguồn phát.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    // Top Result Hero Card
                    val topResult = searchResults.first()
                    item {
                        TopResultHeroCard(
                            song = topResult,
                            onPlay = { onPlaySong(topResult) },
                            onAddToQueue = { onAddToQueue(topResult) },
                            onToggleFav = { onToggleFavorite(topResult) },
                            isFav = isFavorite(topResult.id),
                            isGlass = isGlass
                        )
                    }

                    if (searchResults.size > 1) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bài hát khác (${searchResults.size - 1})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        items(searchResults.drop(1)) { song ->
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
                    }

                    // Bottom AI Search Tile - only shown for playlists, vibes, or compilations (not for single song/artist search)
                    if (isVibeOrPlaylistQuery(searchQuery)) {
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
}

@Composable
fun BrowseCategoryCard(
    category: SearchBrowseCategory,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(category.gradient))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = category.title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = category.icon,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
fun TopResultHeroCard(
    song: Song,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFav: () -> Unit,
    isFav: Boolean,
    isGlass: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isGlass) Color(0x401E293B) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "KẾT QUẢ HÀNG ĐẦU",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onToggleFav) {
                    Icon(
                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Yêu thích",
                        tint = if (isFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Phát ngay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onAddToQueue,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Hàng chờ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun isVibeOrPlaylistQuery(query: String): Boolean {
    val q = query.trim().lowercase()
    val vibeKeywords = listOf(
        "list", "lofi", "chill", "tết", "mưa", "playlist", "tuyển chọn", 
        "tập hợp", "liên khúc", "vibe", "mood", "relax", "thư giãn", 
        "ngủ ngon", "buổi sáng", "đêm", "coffee", "quán cà phê", "học bài", 
        "study", "gym", "workout", "remix", "acoustic", "nhẹ nhàng", "buồn",
        "tâm trạng", "sôi động", "nhạc xuân", "giáng sinh", "summer", "mùa hè", "đám cưới",
        "nhạc trẻ", "nhạc đỏ", "nhạc vàng", "bolero", "top", "bảng xếp hạng"
    )
    return vibeKeywords.any { q.contains(it) }
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
