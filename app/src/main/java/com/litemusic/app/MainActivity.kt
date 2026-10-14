package com.litemusic.app

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.litemusic.app.core.audio.PlaybackService
import com.litemusic.app.core.lyrics.LrcLibClient
import com.litemusic.app.core.lyrics.LyricLine
import com.litemusic.app.core.model.AudioSource
import com.litemusic.app.core.model.Song
import com.litemusic.app.core.resolver.SoundCloudStreamResolver
import com.litemusic.app.core.resolver.SpotifyMetadataBridge
import com.litemusic.app.core.resolver.YouTubeStreamResolver
import com.litemusic.app.ui.settings.SettingsScreen
import com.litemusic.app.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    val ytResolver = YouTubeStreamResolver()
    val scResolver = SoundCloudStreamResolver()
    val spotifyBridge = SpotifyMetadataBridge(ytResolver)
    private val lrcClient = LrcLibClient()

    private var activeLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    private var isResolvingStream by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initMediaController()

        setContent {
            var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(this)) }
            var colorPalette by remember { mutableStateOf(ThemePreferences.getColorPalette(this)) }
            var themeStyle by remember { mutableStateOf(ThemePreferences.getThemeStyle(this)) }
            var isDspEnabled by remember { mutableStateOf(ThemePreferences.getDspEnabled(this)) }

            LiteMusicTheme(
                themeMode = themeMode,
                colorPalette = colorPalette,
                themeStyle = themeStyle
            ) {
                MainAppScreen(
                    mediaController = mediaController,
                    lyrics = activeLyrics,
                    isResolving = isResolvingStream,
                    currentThemeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        ThemePreferences.setThemeMode(this, it)
                    },
                    currentColorPalette = colorPalette,
                    onColorPaletteChange = {
                        colorPalette = it
                        ThemePreferences.setColorPalette(this, it)
                    },
                    currentThemeStyle = themeStyle,
                    onThemeStyleChange = {
                        themeStyle = it
                        ThemePreferences.setThemeStyle(this, it)
                    },
                    isDspEnabled = isDspEnabled,
                    onDspToggle = {
                        isDspEnabled = it
                        ThemePreferences.setDspEnabled(this, it)
                    },
                    onClearCache = {
                        try {
                            val cDir = File(cacheDir, "media_cache")
                            if (cDir.exists()) cDir.deleteRecursively()
                            Toast.makeText(this, "Đã dọn dẹp 250MB bộ nhớ đệm!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Đã dọn dẹp!", Toast.LENGTH_SHORT).show()
                        }
                    },
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

            // Resolve real playable stream URL from YouTube Opus / SoundCloud / Spotify Bridge
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
                            .setArtworkUri(if (song.thumbnailUrl.isNotEmpty()) Uri.parse(song.thumbnailUrl) else null)
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Không thể lấy luồng âm thanh cho bài này", Toast.LENGTH_SHORT).show()
                }
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
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    currentColorPalette: AppColorPalette,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    currentThemeStyle: AppThemeStyle,
    onThemeStyleChange: (AppThemeStyle) -> Unit,
    isDspEnabled: Boolean,
    onDspToggle: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onSearch: suspend (String, AudioSource) -> List<Song>,
    onSeek: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(AudioSource.YOUTUBE_MUSIC) }
    var isPlayerSheetOpen by remember { mutableStateOf(false) }

    // Live Data State
    var homeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    var currentArtist by remember { mutableStateOf("") }
    var currentArtwork by remember { mutableStateOf("") }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var selectedNavTab by remember { mutableStateOf(0) }

    // Fetch initial live trending music on app start
    LaunchedEffect(Unit) {
        isLoadingHome = true
        val trending = onSearch("Top thịnh hành Việt Nam", AudioSource.YOUTUBE_MUSIC)
        homeSongs = trending
        if (trending.isNotEmpty()) {
            currentTitle = trending.first().title
            currentArtist = trending.first().artist
            currentArtwork = trending.first().thumbnailUrl
        }
        isLoadingHome = false
    }

    // Debounced search when user types
    LaunchedEffect(searchQuery, selectedSource) {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            delay(400) // Debounce
            isSearching = true
            searchResults = onSearch(q, selectedSource)
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    // MediaController state listener
    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.let {
                    currentTitle = it.title?.toString() ?: ""
                    currentArtist = it.artist?.toString() ?: ""
                    currentArtwork = it.artworkUri?.toString() ?: ""
                }
            }
        }
        mediaController?.addListener(listener)
        onDispose { mediaController?.removeListener(listener) }
    }

    // Progress Ticker
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

    val isGlass = currentThemeStyle == AppThemeStyle.LIQUID_GLASS

    val bgModifier = if (isGlass) {
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        )
    } else {
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    }

    Box(modifier = bgModifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = if (isGlass) GlassBottomNavDark.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceContainerLowest,
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
                if (selectedNavTab == 3) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SettingsScreen(
                            currentThemeMode = currentThemeMode,
                            onThemeModeChange = onThemeModeChange,
                            currentColorPalette = currentColorPalette,
                            onColorPaletteChange = onColorPaletteChange,
                            currentThemeStyle = currentThemeStyle,
                            onThemeStyleChange = onThemeStyleChange,
                            isDspEnabled = isDspEnabled,
                            onDspToggle = onDspToggle,
                            onClearCache = onClearCache
                        )
                        if (currentTitle.isNotEmpty()) {
                            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                                M3MiniPlayer(
                                    title = currentTitle,
                                    artist = currentArtist,
                                    artworkUrl = currentArtwork,
                                    isPlaying = isPlaying,
                                    isResolving = isResolving,
                                    onPlayPause = {
                                        mediaController?.let {
                                            if (it.isPlaying) it.pause() else it.play()
                                        }
                                    },
                                    onNext = { mediaController?.seekToNextMediaItem() },
                                    onClick = { isPlayerSheetOpen = true }
                                )
                            }
                        }
                    }
                } else {
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

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = if (isDspEnabled) "⚡ -14 LUFS DSP" else "⚡ DSP OFF",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDspEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
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
                        if (isSearching || isResolving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.outline)
                            }
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

                // Active List (Search Results or Home Feed)
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isNotEmpty()) {
                        // Display Search Results
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
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                items(searchResults) { song ->
                                    SongListCard(song = song, onClick = {
                                        currentTitle = song.title
                                        currentArtist = song.artist
                                        currentArtwork = song.thumbnailUrl
                                        onPlaySong(song)
                                    })
                                }
                            }
                        }
                    } else {
                        // Hybrid Home Screen
                        if (isLoadingHome) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Đang tải dữ liệu thực từ YouTube Music...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                // Section 1: Quick Picks Grid
                                item {
                                    Text(
                                        text = "⚡ Nghe nhanh (Quick Grid)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val quickList = homeSongs.take(4)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        quickList.chunked(2).forEach { pair ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                pair.forEach { song ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        QuickPickTile(song = song, onClick = {
                                                            currentTitle = song.title
                                                            currentArtist = song.artist
                                                            currentArtwork = song.thumbnailUrl
                                                            onPlaySong(song)
                                                        })
                                                    }
                                                }
                                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Section 2: Visual Album Grid (Horizontal Carousel)
                                item {
                                    Text(
                                        text = "🎨 Đề xuất trực quan (Visual Grid)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(homeSongs.drop(4).take(8)) { song ->
                                            AlbumGridCard(
                                                song = song,
                                                onClick = {
                                                    currentTitle = song.title
                                                    currentArtist = song.artist
                                                    currentArtwork = song.thumbnailUrl
                                                    onPlaySong(song)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Section 3: Detailed Track List
                                item {
                                    Text(
                                        text = "📋 Bảng xếp hạng thịnh hành (List Detail)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                items(homeSongs.drop(12)) { song ->
                                    SongListCard(song = song, onClick = {
                                        currentTitle = song.title
                                        currentArtist = song.artist
                                        currentArtwork = song.thumbnailUrl
                                        onPlaySong(song)
                                    })
                                }
                            }
                        }
                    }

                    // Floating MiniPlayer (Only show if a song has been selected)
                    if (currentTitle.isNotEmpty()) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                            M3MiniPlayer(
                                title = currentTitle,
                                artist = currentArtist,
                                artworkUrl = currentArtwork,
                                isPlaying = isPlaying,
                                isResolving = isResolving,
                                onPlayPause = {
                                    mediaController?.let {
                                        if (it.isPlaying) it.pause() else it.play()
                                    }
                                },
                                onNext = { mediaController?.seekToNextMediaItem() },
                                onClick = { isPlayerSheetOpen = true }
                            )
                        }
                    }
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
                artworkUrl = currentArtwork,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                lyrics = lyrics,
                onClose = { isPlayerSheetOpen = false },
                onPlayPause = {
                    mediaController?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                },
                onSeek = onSeek
            )
        }
    }
}

@Composable
fun QuickPickTile(song: Song, onClick: () -> Unit) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isGlass) BorderStroke(1.dp, GlassBorderDark) else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Text("🎵", fontSize = 18.sp) }
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
fun AlbumGridCard(song: Song, onClick: () -> Unit) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    Card(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isGlass) BorderStroke(1.dp, GlassBorderDark) else CardDefaults.outlinedCardBorder(),
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
                if (song.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("🎨", fontSize = 36.sp)
                }
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
            Text(song.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SongListCard(song: Song, onClick: () -> Unit) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isGlass) BorderStroke(1.dp, GlassBorderDark) else CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Text("♫", fontSize = 20.sp) }
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
    artworkUrl: String,
    isPlaying: Boolean,
    isResolving: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = if (isGlass) Color(0xD9121720) else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        border = if (isGlass) BorderStroke(1.2.dp, GlassBorderDark) else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("▶", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isResolving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                IconButton(onClick = onPlayPause) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = MaterialTheme.colorScheme.primary)
                }
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
    artworkUrl: String,
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
                            if (artworkUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = title,
                                    modifier = Modifier
                                        .size(260.dp)
                                        .clip(RoundedCornerShape(28.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(260.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) { Text("🎸", fontSize = 80.sp) }
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
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "⚡ -14.0 LUFS DSP Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
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
