package com.litemusic.app

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.litemusic.app.core.audio.PlaybackService
import com.litemusic.app.core.lyrics.LrcLibClient
import com.litemusic.app.core.lyrics.LyricLine
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import com.litemusic.app.core.resolver.SoundCloudStreamResolver
import com.litemusic.app.core.resolver.SpotifyMetadataBridge
import com.litemusic.app.core.resolver.YouTubeStreamResolver
import com.litemusic.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    private val ytResolver = YouTubeStreamResolver()
    private val scResolver = SoundCloudStreamResolver()
    private val spotifyBridge = SpotifyMetadataBridge(ytResolver)
    private val lrcClient = LrcLibClient()

    private var activeLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    private var isResolvingStream by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initMediaController()

        setContent {
            LiteMusicTheme(dynamicColor = true) {
                MainAppScreen(
                    mediaController = mediaController,
                    lyrics = activeLyrics,
                    isResolving = isResolvingStream,
                    onPlaySong = { song -> playSong(song) },
                    onSearch = { query, source -> searchSongs(query, source) },
                    onSeek = { positionMs -> mediaController?.seekTo(positionMs) }
                )
            }
        }
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun searchSongs(query: String, source: AudioSource): List<Song> {
        return when (source) {
            AudioSource.YOUTUBE_MUSIC -> ytResolver.search(query)
            AudioSource.SOUNDCLOUD -> scResolver.search(query)
            AudioSource.SPOTIFY -> spotifyBridge.fetchPlaylistTracks(query)
        }
    }

    private fun playSong(song: Song) {
        val controller = mediaController ?: return
        lifecycleScope.launch {
            isResolvingStream = true

            // Fetch synced lyrics in parallel
            launch {
                activeLyrics = lrcClient.getLyrics(song.title, song.artist, song.durationSeconds)
            }

            // Resolve streaming URL
            val url = song.streamUrl ?: when (song.source) {
                AudioSource.YOUTUBE_MUSIC -> ytResolver.resolveStreamUrl(song)
                AudioSource.SOUNDCLOUD -> scResolver.resolveStreamUrl(song)
                AudioSource.SPOTIFY -> spotifyBridge.resolveTrackToPlayableStream(song)
            }

            isResolvingStream = false

            if (url != null) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(Uri.parse(song.thumbnailUrl))
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    mediaController: MediaController?,
    lyrics: List<LyricLine>,
    isResolving: Boolean,
    onPlaySong: (Song) -> Unit,
    onSearch: suspend (String, AudioSource) -> List<Song>,
    onSeek: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(AudioSource.YOUTUBE_MUSIC) }
    var isPlayerSheetOpen by remember { mutableStateOf(false) }

    val sampleSongs = remember {
        listOf(
            Song("1", "Die With A Smile", "Lady Gaga, Bruno Mars", durationSeconds = 251, source = AudioSource.YOUTUBE_MUSIC),
            Song("2", "Espresso (VIP Remix)", "Sabrina Carpenter", durationSeconds = 205, source = AudioSource.SOUNDCLOUD),
            Song("3", "Birds of a Feather", "Billie Eilish", durationSeconds = 198, source = AudioSource.SPOTIFY),
            Song("4", "Cắt Đôi Nỗi Sầu (Acoustic)", "Tăng Duy Tân", durationSeconds = 182, source = AudioSource.YOUTUBE_MUSIC),
            Song("5", "Starboy (Slowed Reverb)", "The Weeknd", durationSeconds = 285, source = AudioSource.SOUNDCLOUD),
            Song("6", "Nơi Này Có Anh", "Sơn Tùng M-TP", durationSeconds = 260, source = AudioSource.YOUTUBE_MUSIC)
        )
    }

    val trendingAlbums = remember {
        listOf(
            "HIT ME HARD AND SOFT" to "Billie Eilish",
            "Top Hits V-Pop 2026" to "Trending Playlist",
            "Starboy Chill Radio" to "SoundCloud Mix",
            "Acoustic Coffee" to "Peaceful Vibes"
        )
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("Die With A Smile") }
    var currentArtist by remember { mutableStateOf("Lady Gaga, Bruno Mars") }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(251000L) }
    var selectedNavTab by remember { mutableStateOf(0) }

    // MediaController state sync
    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.let {
                    currentTitle = it.title?.toString() ?: ""
                    currentArtist = it.artist?.toString() ?: ""
                }
            }
        }
        mediaController?.addListener(listener)
        onDispose { mediaController?.removeListener(listener) }
    }

    // Ticker coroutine to update progress smooth scrubber
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaController?.let {
                currentPositionMs = it.currentPosition
                if (it.duration > 0) durationMs = it.duration
            }
            delay(500)
        }
    }

    BackHandler(enabled = isPlayerSheetOpen) {
        isPlayerSheetOpen = false
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(72.dp)
                ) {
                    val items = listOf(
                        "Khám phá" to Icons.Default.Home,
                        "Tìm kiếm" to Icons.Default.Search,
                        "Thư viện" to Icons.Default.LibraryMusic,
                        "Cài đặt" to Icons.Default.Settings
                    )
                    items.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = selectedNavTab == index,
                            onClick = { selectedNavTab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("♫", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            text = "LiteMusic",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    AssistChip(
                        onClick = { /* Toggle Normalizer */ },
                        label = { Text("⚡ -14 LUFS DSP", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = AssistChipDefaults.assistChipBorder(borderColor = MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // M3 Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tìm bài hát, ca sĩ, link...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.outline) },
                    trailingIcon = {
                        if (isResolving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // M3 Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSource == AudioSource.YOUTUBE_MUSIC,
                        onClick = { selectedSource = AudioSource.YOUTUBE_MUSIC },
                        label = { Text("YouTube (Opus)", fontSize = 11.5.sp) },
                        leadingIcon = if (selectedSource == AudioSource.YOUTUBE_MUSIC) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = selectedSource == AudioSource.SOUNDCLOUD,
                        onClick = { selectedSource = AudioSource.SOUNDCLOUD },
                        label = { Text("SoundCloud", fontSize = 11.5.sp) },
                        leadingIcon = if (selectedSource == AudioSource.SOUNDCLOUD) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = selectedSource == AudioSource.SPOTIFY,
                        onClick = { selectedSource = AudioSource.SPOTIFY },
                        label = { Text("Spotify Bridge", fontSize = 11.5.sp) },
                        leadingIcon = if (selectedSource == AudioSource.SPOTIFY) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // HYBRID CONTENT FEED: CẢ GRID VÀ LIST CÙNG XUẤT HIỆN
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        // Section 1: Quick Grid (2 Cột)
                        item {
                            Text(
                                text = "⚡ Nghe lại nhanh (Quick Grid)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val chunked = sampleSongs.take(4).chunked(2)
                                chunked.forEach { pair ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { song ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                QuickPickTile(song = song, onClick = {
                                                    currentTitle = song.title
                                                    currentArtist = song.artist
                                                    isPlayerSheetOpen = true
                                                    onPlaySong(song)
                                                })
                                            }
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Visual Grid (Album Cards)
                        item {
                            Text(
                                text = "🎨 Album & Playlists Nổi bật (Visual Grid)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(trendingAlbums) { (title, subtitle) ->
                                    AlbumGridCard(
                                        title = title,
                                        subtitle = subtitle,
                                        onClick = {
                                            currentTitle = title
                                            currentArtist = subtitle
                                            isPlayerSheetOpen = true
                                        }
                                    )
                                }
                            }
                        }

                        // Section 3: Detailed List Rows như cũ
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📋 Bảng Xếp Hạng & Bài Hát (List Detail)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tất cả",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(sampleSongs) { song ->
                            SongListCard(
                                song = song,
                                onClick = {
                                    currentTitle = song.title
                                    currentArtist = song.artist
                                    isPlayerSheetOpen = true
                                    onPlaySong(song)
                                }
                            )
                        }
                    }

                    // Floating MiniPlayer
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                        M3MiniPlayer(
                            title = currentTitle,
                            artist = currentArtist,
                            isPlaying = isPlaying,
                            onPlayPause = {
                                mediaController?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                } ?: run { isPlaying = !isPlaying }
                            },
                            onNext = { mediaController?.seekToNextMediaItem() },
                            onClick = { isPlayerSheetOpen = true }
                        )
                    }
                }
            }
        }

        // Full Screen Player Sheet with Synced Lyrics
        AnimatedVisibility(
            visible = isPlayerSheetOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            M3FullScreenPlayer(
                title = currentTitle,
                artist = currentArtist,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                lyrics = lyrics,
                onClose = { isPlayerSheetOpen = false },
                onPlayPause = {
                    mediaController?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    } ?: run { isPlaying = !isPlaying }
                },
                onSeek = onSeek
            )
        }
    }
}

@Composable
fun QuickPickTile(song: Song, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text("🎵", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AlbumGridCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text("🎨", fontSize = 36.sp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SongListCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text("♫", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = song.source.name.replace("_", " "),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    Text(song.artist, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun M3MiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun M3FullScreenPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    lyrics: List<LyricLine>,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var isShowingLyrics by remember { mutableStateOf(false) }
    val lyricsListState = rememberLazyListState()

    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Find active lyric line index
    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        val idx = lyrics.indexOfLast { it.timestampMs <= currentPositionMs }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(activeLyricIndex, isShowingLyrics) {
        if (isShowingLyrics && lyrics.isNotEmpty()) {
            lyricsListState.animateScrollToItem((activeLyricIndex - 2).coerceAtLeast(0))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("ĐANG PHÁT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.outline)
                IconButton(onClick = { isShowingLyrics = !isShowingLyrics }) {
                    Icon(
                        imageVector = if (isShowingLyrics) Icons.Default.MusicNote else Icons.Default.Lyrics,
                        contentDescription = "Toggle Lyrics",
                        tint = if (isShowingLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Middle Section: Toggle between Big Album Art and Synced Karaoke Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = isShowingLyrics, label = "lyricsCrossfade") { showLyrics ->
                    if (showLyrics) {
                        if (lyrics.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Đang tải hoặc chưa có lời bài hát đồng bộ...", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 40.dp)
                            ) {
                                items(lyrics.size) { index ->
                                    val line = lyrics[index]
                                    val isActive = index == activeLyricIndex
                                    Text(
                                        text = line.text,
                                        fontSize = if (isActive) 18.sp else 14.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSeek(line.timestampMs) }
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎸", fontSize = 80.sp)
                            }
                        }
                    }
                }
            }

            // Song Info & DSP Badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, fontSize = 14.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text("⚡ -14.0 LUFS DSP Active", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // Scrubber Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = { newRatio ->
                        onSeek((newRatio * durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPositionMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(formatTime(durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Controls Cluster
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.outline) }
                IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface) }

                FilledTonalIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = {}) { Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = MaterialTheme.colorScheme.outline) }
            }

            // Bottom Bar to open Lyrics quickly
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isShowingLyrics = !isShowingLyrics },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (isShowingLyrics) "Chạm để quay lại Album Art" else "Chạm để xem Lời bài hát Karaoke (LRCLIB)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (lyrics.isNotEmpty()) "${lyrics.size} câu lời đã đồng bộ" else "Hỗ trợ lời chạy theo nhạc thời gian thực", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(if (isShowingLyrics) Icons.Default.MusicNote else Icons.Default.Lyrics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
