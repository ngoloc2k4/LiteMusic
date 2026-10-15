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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.litemusic.app.core.audio.AudioEffectManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.abs
import com.litemusic.app.feature.aimusic.domain.AiGenerationState
import com.litemusic.app.feature.aimusic.domain.AiPlaylistGenerator
import com.litemusic.app.feature.aimusic.domain.LocalHeuristicRanker
import com.litemusic.app.feature.aimusic.ui.AiPlaylistPreviewDialog
import com.litemusic.app.feature.aimusic.ui.AiSearchTile
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
                    onPlaySong = { song, asVideo -> playSong(song, asVideo) },
                    onSearch = { query, source -> searchSongs(query, source) },
                    onSeek = { positionMs -> mediaController?.seekTo(positionMs) },
                    currentSong = currentPlayingSong
                )
            }
        }
    }

    private var currentPlayingSong by mutableStateOf<Song?>(null)

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
            AudioSource.YOUTUBE_VIDEO -> ytResolver.searchRegularVideos(query)
            AudioSource.SOUNDCLOUD -> scResolver.search(query)
            AudioSource.SPOTIFY -> spotifyBridge.fetchPlaylistTracks(query)
        }
    }

    private fun playSong(song: Song, asVideo: Boolean = false) {
        currentPlayingSong = song
        val controller = mediaController ?: return
        lifecycleScope.launch {
            isResolvingStream = true

            // Fetch synced lyrics in parallel
            launch {
                activeLyrics = lrcClient.getLyrics(song.title, song.artist, song.durationSeconds)
            }

            // Resolve real playable stream URL from YouTube Opus / Video MP4 / SoundCloud / Spotify Bridge
            val url = if (asVideo && (song.source == AudioSource.YOUTUBE_MUSIC || song.source == AudioSource.YOUTUBE_VIDEO)) {
                song.videoUrl ?: ytResolver.resolveVideoStreamUrl(song) ?: ytResolver.resolveStreamUrl(song)
            } else {
                song.streamUrl ?: when (song.source) {
                    AudioSource.YOUTUBE_MUSIC, AudioSource.YOUTUBE_VIDEO -> ytResolver.resolveStreamUrl(song)
                    AudioSource.SOUNDCLOUD -> scResolver.resolveStreamUrl(song)
                    AudioSource.SPOTIFY -> spotifyBridge.resolveTrackToPlayableStream(song)
                }
            }

            isResolvingStream = false

            if (url != null) {
                val curPos = controller.currentPosition
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
                if (asVideo && curPos > 0) {
                    controller.seekTo(curPos)
                }
                controller.play()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Không thể lấy luồng âm thanh hoặc video cho bài này", Toast.LENGTH_SHORT).show()
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
    onPlaySong: (Song, Boolean) -> Unit,
    onSearch: suspend (String, AudioSource) -> List<Song>,
    onSeek: (Long) -> Unit,
    currentSong: Song? = null
) {
    val context = LocalContext.current
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

    // Video, Speed, Volume, and Timer controls
    var isVideoMode by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var customVolume by remember { mutableIntStateOf(100) }
    var sleepTimerSeconds by remember { mutableStateOf<Long?>(null) }
    var isStopAtEndOfTrack by remember { mutableStateOf(false) }

    // Double-tap Skip Duration & Queue state
    var skipDurationSeconds by remember { mutableIntStateOf(ThemePreferences.getSkipDuration(context)) }
    var activeSong by remember { mutableStateOf<Song?>(currentSong) }
    var songQueue by remember { mutableStateOf<List<Song>>(emptyList()) }

    // AI Music Generator State
    val aiGenerator = remember { AiPlaylistGenerator(context) }
    var aiGenerationState by remember { mutableStateOf<AiGenerationState>(AiGenerationState.Idle) }
    var showAiDialog by remember { mutableStateOf(false) }

    fun playNextInQueue() {
        if (songQueue.isNotEmpty()) {
            val next = songQueue.first()
            songQueue = songQueue.drop(1)
            activeSong = next
            currentTitle = next.title
            currentArtist = next.artist
            currentArtwork = next.thumbnailUrl
            onPlaySong(next, isVideoMode)
        } else {
            mediaController?.seekToNextMediaItem()
        }
    }

    fun playPreviousInQueue() {
        if (currentPositionMs > 3000L) {
            mediaController?.seekTo(0)
        } else {
            mediaController?.seekToPreviousMediaItem()
            mediaController?.seekTo(0)
        }
    }

    fun forceStopPlayback() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        isPlaying = false
        currentTitle = ""
        currentArtist = ""
        currentArtwork = ""
        currentPositionMs = 0L
        durationMs = 0L
        songQueue = emptyList()
        isPlayerSheetOpen = false
        Toast.makeText(context, "Đã tắt hẳn bài đang phát và danh sách chờ", Toast.LENGTH_SHORT).show()
    }

    // Sleep Timer Live Countdown Loop
    LaunchedEffect(sleepTimerSeconds) {
        val s = sleepTimerSeconds
        if (s != null && s > 0) {
            while (sleepTimerSeconds != null && sleepTimerSeconds!! > 0) {
                delay(1000)
                sleepTimerSeconds = sleepTimerSeconds?.minus(1)
            }
            if (sleepTimerSeconds == 0L) {
                mediaController?.pause()
                sleepTimerSeconds = null
                Toast.makeText(context, "Đã tắt nhạc theo hẹn giờ", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (isStopAtEndOfTrack) {
                        mediaController?.pause()
                        isStopAtEndOfTrack = false
                        Toast.makeText(context, "Đã dừng phát khi hết bài hát", Toast.LENGTH_SHORT).show()
                    } else if (songQueue.isNotEmpty()) {
                        playNextInQueue()
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.let {
                    currentTitle = it.title?.toString() ?: ""
                    currentArtist = it.artist?.toString() ?: ""
                    currentArtwork = it.artworkUri?.toString() ?: ""
                }
                if (isStopAtEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    mediaController?.pause()
                    isStopAtEndOfTrack = false
                    Toast.makeText(context, "Đã dừng phát khi hết bài hát", Toast.LENGTH_SHORT).show()
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
                            onClearCache = onClearCache,
                            currentSkipDuration = skipDurationSeconds,
                            onSkipDurationChange = { newSec ->
                                skipDurationSeconds = newSec
                                ThemePreferences.setSkipDuration(context, newSec)
                            }
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
                                    onNext = { playNextInQueue() },
                                    onPrevious = { playPreviousInQueue() },
                                    onForceStop = { forceStopPlayback() },
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedSource == AudioSource.YOUTUBE_MUSIC,
                            onClick = { selectedSource = AudioSource.YOUTUBE_MUSIC },
                            label = { Text("YouTube Music", fontSize = 11.5.sp) },
                            leadingIcon = if (selectedSource == AudioSource.YOUTUBE_MUSIC) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedSource == AudioSource.YOUTUBE_VIDEO,
                            onClick = { selectedSource = AudioSource.YOUTUBE_VIDEO },
                            label = { Text("YouTube Video (Only Nhạc)", fontSize = 11.5.sp) },
                            leadingIcon = if (selectedSource == AudioSource.YOUTUBE_VIDEO) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedSource == AudioSource.SOUNDCLOUD,
                            onClick = { selectedSource = AudioSource.SOUNDCLOUD },
                            label = { Text("SoundCloud", fontSize = 11.5.sp) },
                            leadingIcon = if (selectedSource == AudioSource.SOUNDCLOUD) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedSource == AudioSource.SPOTIFY,
                            onClick = { selectedSource = AudioSource.SPOTIFY },
                            label = { Text("Spotify", fontSize = 11.5.sp) },
                            leadingIcon = if (selectedSource == AudioSource.SPOTIFY) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
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
                                    SongListCard(
                                        song = song,
                                        onClick = {
                                            activeSong = song
                                            currentTitle = song.title
                                            currentArtist = song.artist
                                            currentArtwork = song.thumbnailUrl
                                            onPlaySong(song, isVideoMode)
                                        },
                                        onAddToQueue = {
                                            songQueue = songQueue + song
                                            Toast.makeText(context, "Đã thêm vào danh sách chờ: ${song.title}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                // Dòng cuối cùng: Thẻ tạo Playlist bằng AI nổi bật
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AiSearchTile(
                                        query = searchQuery,
                                        onClick = {
                                            showAiDialog = true
                                            coroutineScope.launch {
                                                aiGenerator.generatePlaylist(
                                                    userPrompt = searchQuery,
                                                    targetSongCount = 20
                                                ) { state ->
                                                    aiGenerationState = state
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
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
                                                            activeSong = song
                                                            currentTitle = song.title
                                                            currentArtist = song.artist
                                                            currentArtwork = song.thumbnailUrl
                                                            onPlaySong(song, isVideoMode)
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
                                                    activeSong = song
                                                    currentTitle = song.title
                                                    currentArtist = song.artist
                                                    currentArtwork = song.thumbnailUrl
                                                    onPlaySong(song, isVideoMode)
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
                                    SongListCard(
                                        song = song,
                                        onClick = {
                                            activeSong = song
                                            currentTitle = song.title
                                            currentArtist = song.artist
                                            currentArtwork = song.thumbnailUrl
                                            onPlaySong(song, isVideoMode)
                                        },
                                        onAddToQueue = {
                                            songQueue = songQueue + song
                                            Toast.makeText(context, "Đã thêm vào danh sách chờ: ${song.title}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
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
                                onNext = { playNextInQueue() },
                                onPrevious = { playPreviousInQueue() },
                                onForceStop = { forceStopPlayback() },
                                onClick = { isPlayerSheetOpen = true }
                            )
                        }
                    }
                }
            }
        }
    }

        // Full Screen Player Sheet with Synced Lyrics & Queue
        AnimatedVisibility(
            visible = isPlayerSheetOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            M3FullScreenPlayer(
                currentSong = activeSong ?: currentSong,
                title = currentTitle,
                artist = currentArtist,
                artworkUrl = currentArtwork,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                lyrics = lyrics,
                playbackSpeed = playbackSpeed,
                onSpeedChange = { newSpeed ->
                    playbackSpeed = newSpeed
                    mediaController?.setPlaybackSpeed(newSpeed)
                },
                customVolume = customVolume,
                onVolumeChange = { newVol ->
                    customVolume = newVol
                    AudioEffectManager.instance.setCustomVolume(newVol) { volFloat ->
                        mediaController?.volume = volFloat
                    }
                },
                sleepTimerSeconds = sleepTimerSeconds,
                onSetTimerMinutes = { mins ->
                    sleepTimerSeconds = mins * 60L
                    isStopAtEndOfTrack = false
                    Toast.makeText(context, "Đã hẹn giờ tắt sau $mins phút", Toast.LENGTH_SHORT).show()
                },
                onSetEndOfTrack = {
                    isStopAtEndOfTrack = true
                    sleepTimerSeconds = null
                    Toast.makeText(context, "Sẽ dừng khi hết bài hát này", Toast.LENGTH_SHORT).show()
                },
                onCancelTimer = {
                    sleepTimerSeconds = null
                    isStopAtEndOfTrack = false
                    Toast.makeText(context, "Đã hủy hẹn giờ tắt nhạc", Toast.LENGTH_SHORT).show()
                },
                isVideoMode = isVideoMode,
                onToggleVideoMode = { enableVideo ->
                    isVideoMode = enableVideo
                    (activeSong ?: currentSong)?.let { song ->
                        onPlaySong(song, enableVideo)
                    }
                },
                mediaController = mediaController,
                onClose = { isPlayerSheetOpen = false },
                onPlayPause = {
                    mediaController?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                },
                onSeek = onSeek,
                skipDurationSeconds = skipDurationSeconds,
                songQueue = songQueue,
                onPlayQueueSong = { qSong ->
                    songQueue = songQueue.filterNot { it.id == qSong.id }
                    activeSong = qSong
                    currentTitle = qSong.title
                    currentArtist = qSong.artist
                    currentArtwork = qSong.thumbnailUrl
                    onPlaySong(qSong, isVideoMode)
                },
                onRemoveFromQueue = { idx ->
                    if (idx in songQueue.indices) {
                        songQueue = songQueue.toMutableList().apply { removeAt(idx) }
                    }
                },
                onClearQueue = {
                    songQueue = emptyList()
                    Toast.makeText(context, "Đã xóa toàn bộ danh sách chờ", Toast.LENGTH_SHORT).show()
                },
                onSkipNext = { playNextInQueue() },
                onSkipPrevious = { playPreviousInQueue() }
            )
        }

        // AI Playlist Preview & Generation Dialog
        if (showAiDialog) {
            AiPlaylistPreviewDialog(
                state = aiGenerationState,
                onDismiss = { showAiDialog = false },
                onPlayNow = { tracks ->
                    if (tracks.isNotEmpty()) {
                        val first = tracks.first()
                        songQueue = tracks.drop(1)
                        activeSong = first
                        currentTitle = first.title
                        currentArtist = first.artist
                        currentArtwork = first.thumbnailUrl
                        onPlaySong(first, isVideoMode)
                    }
                },
                onAddToQueue = { tracks ->
                    songQueue = songQueue + tracks
                    Toast.makeText(context, "Đã thêm ${tracks.size} bài vào danh sách chờ", Toast.LENGTH_SHORT).show()
                },
                onFallbackLocal = {
                    coroutineScope.launch {
                        val localTracks = LocalHeuristicRanker.rankAndFilter(
                            candidates = if (searchResults.isNotEmpty()) searchResults else homeSongs,
                            prompt = searchQuery,
                            targetCount = 20
                        )
                        aiGenerationState = AiGenerationState.Success(
                            playlistTitle = "Playlist: $searchQuery",
                            playlistDescription = "Tuyển tập tạo bằng thuật toán cục bộ.",
                            tracks = localTracks,
                            providerUsed = "Thuật toán cục bộ (Offline)"
                        )
                    }
                }
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
fun SongListCard(
    song: Song,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null
) {
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
            if (onAddToQueue != null) {
                IconButton(onClick = onAddToQueue) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Thêm vào danh sách chờ", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.outline)
            }
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
    onPrevious: () -> Unit,
    onForceStop: () -> Unit,
    onClick: () -> Unit
) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.coerceAtLeast(0f).roundToInt()) }
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                    },
                    onDragEnd = {
                        val absX = abs(dragOffsetX)
                        val absY = abs(dragOffsetY)
                        val threshold = 65f

                        if (dragOffsetY < -threshold) {
                            // Vuốt từ dưới lên -> mở playback chi tiết
                            onClick()
                        } else if (dragOffsetY > threshold && dragOffsetY > absX) {
                            // Vuốt từ trên xuống -> force stop và tắt hẳn bài đang phát và queue list
                            onForceStop()
                        } else if (dragOffsetX < -threshold) {
                            // Vuốt sang trái -> skip next
                            onNext()
                        } else if (dragOffsetX > threshold) {
                            // Vuốt sang phải -> skip previous
                            onPrevious()
                        }

                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                )
            }
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

enum class FullPlayerTab {
    NOW_PLAYING,
    LYRICS,
    QUEUE
}

@Composable
fun M3FullScreenPlayer(
    currentSong: Song?,
    title: String,
    artist: String,
    artworkUrl: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    lyrics: List<LyricLine>,
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    customVolume: Int,
    onVolumeChange: (Int) -> Unit,
    sleepTimerSeconds: Long?,
    onSetTimerMinutes: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    isVideoMode: Boolean,
    onToggleVideoMode: (Boolean) -> Unit,
    mediaController: MediaController?,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    skipDurationSeconds: Int = 10,
    songQueue: List<Song> = emptyList(),
    onPlayQueueSong: (Song) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(FullPlayerTab.NOW_PLAYING) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    // Double-Tap to Skip Feedback State
    var skipFeedbackText by remember { mutableStateOf<String?>(null) }
    var skipFeedbackSide by remember { mutableIntStateOf(0) } // -1 for left, 1 for right

    LaunchedEffect(skipFeedbackText) {
        if (skipFeedbackText != null) {
            delay(700)
            skipFeedbackText = null
        }
    }

    val lyricsListState = rememberLazyListState()

    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        val idx = lyrics.indexOfLast { it.timestampMs <= currentPositionMs }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(activeLyricIndex, currentTab) {
        if (currentTab == FullPlayerTab.LYRICS && lyrics.isNotEmpty()) {
            lyricsListState.animateScrollToItem((activeLyricIndex - 2).coerceAtLeast(0))
        }
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSpeedChange = onSpeedChange,
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showVolumeDialog) {
        VolumeBoostDialog(
            currentVolume = customVolume,
            onVolumeChange = onVolumeChange,
            onDismiss = { showVolumeDialog = false }
        )
    }

    if (showTimerDialog) {
        SleepTimerDialog(
            remainingSeconds = sleepTimerSeconds,
            onSetTimerMinutes = onSetTimerMinutes,
            onSetEndOfTrack = onSetEndOfTrack,
            onCancelTimer = onCancelTimer,
            onDismiss = { showTimerDialog = false }
        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }

                // Video vs Only Nhạc switch pill for YouTube sources
                if (currentSong?.source == AudioSource.YOUTUBE_VIDEO || currentSong?.source == AudioSource.YOUTUBE_MUSIC) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (!isVideoMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.clickable { onToggleVideoMode(false) }
                            ) {
                                Text(
                                    text = "🎵 Only Nhạc",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isVideoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isVideoMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.clickable { onToggleVideoMode(true) }
                            ) {
                                Text(
                                    text = "🎬 Video",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVideoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = when (currentTab) {
                            FullPlayerTab.NOW_PLAYING -> "ĐANG PHÁT"
                            FullPlayerTab.LYRICS -> "LỜI BÀI HÁT"
                            FullPlayerTab.QUEUE -> "DANH SÁCH CHỜ"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Queue Button with Badge
                    IconButton(onClick = {
                        currentTab = if (currentTab == FullPlayerTab.QUEUE) FullPlayerTab.NOW_PLAYING else FullPlayerTab.QUEUE
                    }) {
                        BadgedBox(
                            badge = {
                                if (songQueue.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("${songQueue.size}", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Danh sách chờ",
                                tint = if (currentTab == FullPlayerTab.QUEUE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Lyrics Button
                    IconButton(onClick = {
                        currentTab = if (currentTab == FullPlayerTab.LYRICS) FullPlayerTab.NOW_PLAYING else FullPlayerTab.LYRICS
                    }) {
                        Icon(
                            imageVector = if (currentTab == FullPlayerTab.LYRICS) Icons.Default.MusicNote else Icons.Default.Lyrics,
                            contentDescription = "Toggle Lyrics",
                            tint = if (currentTab == FullPlayerTab.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Middle Section: Switch between Queue, Synced Karaoke Lyrics, or Big Album Art with double-tap gesture
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when (currentTab) {
                    FullPlayerTab.QUEUE -> {
                        // Queue Song List (Optimized like YouTube & Spotify)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                        ) {
                            // Section 1: Đang phát hiện tại
                            Text(
                                text = "ĐANG PHÁT HIỆN TẠI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (artworkUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = artworkUrl,
                                            contentDescription = title,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) { Text("♫", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "ĐANG PHÁT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Section 2: Tiếp theo trong danh sách chờ
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TIẾP THEO (${songQueue.size} BÀI)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 1.sp
                                )
                                if (songQueue.isNotEmpty()) {
                                    TextButton(
                                        onClick = onClearQueue,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Xóa tất cả", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (songQueue.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                                        Text("Danh sách chờ đang trống", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "Chạm vào biểu tượng (+) ở danh sách bài hát để thêm vào hàng đợi phát.",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(songQueue) { idx, qSong ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onPlayQueueSong(qSong) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.width(22.dp)
                                                )
                                                if (qSong.thumbnailUrl.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = qSong.thumbnailUrl,
                                                        contentDescription = qSong.title,
                                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                                        contentAlignment = Alignment.Center
                                                    ) { Text("♫", fontSize = 16.sp) }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(qSong.title, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(qSong.artist, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                IconButton(
                                                    onClick = { onRemoveFromQueue(idx) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Xóa khỏi danh sách chờ", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FullPlayerTab.LYRICS -> {
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
                    }
                    FullPlayerTab.NOW_PLAYING -> {
                        // Big Album Art or Video with Double Tap Gesture
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(skipDurationSeconds) {
                                    detectTapGestures(
                                        onDoubleTap = { tapOffset ->
                                            val isLeft = tapOffset.x < size.width / 2
                                            if (isLeft) {
                                                val newPos = (currentPositionMs - skipDurationSeconds * 1000L).coerceAtLeast(0L)
                                                onSeek(newPos)
                                                skipFeedbackText = "-${skipDurationSeconds}s"
                                                skipFeedbackSide = -1
                                            } else {
                                                val newPos = (currentPositionMs + skipDurationSeconds * 1000L).coerceAtMost(durationMs)
                                                onSeek(newPos)
                                                skipFeedbackText = "+${skipDurationSeconds}s"
                                                skipFeedbackSide = 1
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideoMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                player = mediaController
                                                useController = false
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
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

                            // Animated Overlay for Double-Tap Skip Feedback
                            if (skipFeedbackText != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = if (skipFeedbackSide == -1) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color.Black.copy(alpha = 0.82f),
                                        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.4f)),
                                        tonalElevation = 8.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (skipFeedbackSide == -1) Icons.Default.FastRewind else Icons.Default.FastForward,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = skipFeedbackText ?: "",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Song Info & Badges
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, fontSize = 14.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(10.dp))

                // Feature Action Cluster: Timer, Speed, Volume Boost
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep Timer Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (sleepTimerSeconds != null && sleepTimerSeconds > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { showTimerDialog = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (sleepTimerSeconds != null && sleepTimerSeconds > 0) formatTime(sleepTimerSeconds * 1000) else "Hẹn giờ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Playback Speed Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { showSpeedDialog = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${String.format("%.2f", playbackSpeed)}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Custom Volume Boost Chip (0% to 250%)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (customVolume > 100) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (customVolume > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { showVolumeDialog = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (customVolume > 100) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (customVolume > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$customVolume%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customVolume > 100) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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

            // Controls Cluster (Previous, Play/Pause, Next)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.outline) }
                IconButton(onClick = onSkipPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface) }

                FilledTonalIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = onSkipNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = {}) { Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = MaterialTheme.colorScheme.outline) }
            }

            // Bottom Bar to switch views
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        currentTab = when (currentTab) {
                            FullPlayerTab.NOW_PLAYING -> FullPlayerTab.LYRICS
                            FullPlayerTab.LYRICS -> FullPlayerTab.QUEUE
                            FullPlayerTab.QUEUE -> FullPlayerTab.NOW_PLAYING
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            text = when (currentTab) {
                                FullPlayerTab.NOW_PLAYING -> "Chạm để xem Lời bài hát & Danh sách chờ"
                                FullPlayerTab.LYRICS -> "Chạm để mở Danh sách chờ (${songQueue.size} bài)"
                                FullPlayerTab.QUEUE -> "Chạm để quay lại giao diện Đang phát"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (currentTab) {
                                FullPlayerTab.NOW_PLAYING -> if (lyrics.isNotEmpty()) "${lyrics.size} câu lời đồng bộ • Chạm đúp để tua ${skipDurationSeconds}s" else "Chạm đúp trái/phải để tua ${skipDurationSeconds}s"
                                FullPlayerTab.LYRICS -> "Lời bài hát chạy theo thời gian thực"
                                FullPlayerTab.QUEUE -> if (songQueue.isNotEmpty()) "${songQueue.size} bài tiếp theo trong hàng đợi" else "Hàng đợi trống"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lyrics, contentDescription = null, tint = if (currentTab == FullPlayerTab.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = if (currentTab == FullPlayerTab.QUEUE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

/**
 * Volume Boost Controller Dialog (0% - 250%) with high volume warning
 */
@Composable
fun VolumeBoostDialog(
    currentVolume: Int,
    onVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (currentVolume > 100) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = if (currentVolume > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text("Âm lượng ứng dụng ($currentVolume%)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Tùy biến âm lượng từ 0% đến 250% (kết hợp DSP Gain Boost khi vượt quá 100%).",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..250f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (currentVolume > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        activeTrackColor = if (currentVolume > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0%", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("100% (Chuẩn)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("250% (DSP Boost)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }

                if (currentVolume > 100) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️", fontSize = 18.sp)
                            Text(
                                text = "Cảnh báo âm lượng cao (> 100%): Mức âm lượng khuyếch đại DSP Boost có thể gây tổn hại thính giác nếu nghe bằng tai nghe trong thời gian dài và có thể gây méo tiếng trên một số thiết bị loa.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Playback Speed Controller Dialog with 0.05 steppers and 0.25 quick preset chips
 */
@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Xong", fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Tốc độ phát (${String.format("%.2f", currentSpeed)}x)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Điều chỉnh tốc độ phát lại từ 0.25x đến 3.0x bằng thanh trượt hoặc nút cộng/trừ 0.05x.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                // Stepper Row: [-] [ Slider ] [+]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((currentSpeed - 0.05f) * 100.0) / 100.0).toFloat().coerceIn(0.25f, 3.0f)
                            onSpeedChange(next)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("-0.05", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = currentSpeed,
                        onValueChange = {
                            val rounded = (Math.round(it * 100.0) / 100.0).toFloat().coerceIn(0.25f, 3.0f)
                            onSpeedChange(rounded)
                        },
                        valueRange = 0.25f..3.0f,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((currentSpeed + 0.05f) * 100.0) / 100.0).toFloat().coerceIn(0.25f, 3.0f)
                            onSpeedChange(next)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("+0.05", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text("Chọn nhanh (bước 0.25x):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        val isSelected = Math.abs(currentSpeed - preset) < 0.02f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSpeedChange(preset) },
                            label = { Text("${preset}x", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Sleep Timer Dialog with quick options, end of track, and custom slider
 */
@Composable
fun SleepTimerDialog(
    remainingSeconds: Long?,
    onSetTimerMinutes: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember { mutableFloatStateOf(30f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (remainingSeconds != null && remainingSeconds > 0)
                        "Hẹn giờ (${formatTime(remainingSeconds * 1000)})"
                    else "Hẹn giờ tắt nhạc",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (remainingSeconds != null && remainingSeconds > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Đang chạy hẹn giờ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Tắt sau ${formatTime(remainingSeconds * 1000)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            FilledTonalButton(onClick = { onCancelTimer(); onDismiss() }) {
                                Text("Hủy")
                            }
                        }
                    }
                }

                Text("Tùy chọn nhanh:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilledTonalButton(
                            onClick = { onSetTimerMinutes(mins); onDismiss() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("${mins}p", fontSize = 11.5.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onSetEndOfTrack(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dừng khi phát hết bài hát này", fontSize = 12.sp)
                }

                HorizontalDivider()

                Text("Tùy chỉnh: ${customMinutes.toInt()} phút", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = customMinutes,
                    onValueChange = { customMinutes = it },
                    valueRange = 1f..120f
                )
                Button(
                    onClick = { onSetTimerMinutes(customMinutes.toInt()); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bắt đầu hẹn giờ (${customMinutes.toInt()} phút)")
                }
            }
        }
    )
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
