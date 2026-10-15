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
import com.litemusic.app.core.resolver.VideoQuality
import com.litemusic.app.core.library.LibraryManager
import com.litemusic.app.ui.search.SearchScreen
import com.litemusic.app.ui.library.LibraryScreen
import com.litemusic.app.ui.settings.SettingsScreen
import com.litemusic.app.ui.theme.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.graphicsLayer
import com.litemusic.app.core.debug.DebugLogServer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var playJob: Job? = null
    private var availableVideoQualities by mutableStateOf<List<VideoQuality>>(emptyList())
    private var selectedVideoQuality by mutableStateOf<VideoQuality?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initMediaController()

        if (ThemePreferences.isDebugMode(this)) {
            DebugLogServer.start(lifecycleScope)
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(this)) }
            var colorPalette by remember { mutableStateOf(ThemePreferences.getColorPalette(this)) }
            var themeStyle by remember { mutableStateOf(ThemePreferences.getThemeStyle(this)) }
            var isDspEnabled by remember { mutableStateOf(ThemePreferences.getDspEnabled(this)) }
            var isDebugMode by remember { mutableStateOf(ThemePreferences.isDebugMode(this)) }

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
                    isDebugMode = isDebugMode,
                    onDebugModeToggle = { enabled ->
                        isDebugMode = enabled
                        ThemePreferences.setDebugMode(this, enabled)
                        if (enabled) {
                            DebugLogServer.start(lifecycleScope)
                        } else {
                            DebugLogServer.stop()
                        }
                    },
                    debugServerUrl = DebugLogServer.getLocalUrl(),
                    onCopyLogcat = {
                        DebugLogServer.copyLogcatToClipboard(this)
                    },
                    onClearLogcat = {
                        DebugLogServer.clearLogcat()
                        Toast.makeText(this, "Đã xóa sạch bộ đệm Logcat!", Toast.LENGTH_SHORT).show()
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
                    currentSong = currentPlayingSong,
                    availableVideoQualities = availableVideoQualities,
                    selectedVideoQuality = selectedVideoQuality,
                    onChangeVideoQuality = { quality ->
                        val target = currentPlayingSong
                        if (target != null) {
                            playSong(target, asVideo = true, explicitQuality = quality)
                        }
                    }
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

    private fun playSong(song: Song, asVideo: Boolean = false, explicitQuality: VideoQuality? = null) {
        val controller = mediaController ?: return

        // Cancel any in-flight stream resolution to prevent race conditions & interleaving
        playJob?.cancel()

        // Preserve current playback position if switching mode or resolution for the same song
        val isSameSong = (currentPlayingSong?.id == song.id && controller.currentMediaItem != null)
        val curPos = if (isSameSong) controller.currentPosition else 0L

        if (!isSameSong) {
            controller.pause()
        }

        currentPlayingSong = song

        playJob = lifecycleScope.launch {
            isResolvingStream = true

            // Fetch synced lyrics in parallel
            launch {
                activeLyrics = lrcClient.getLyrics(song.title, song.artist, song.durationSeconds)
            }

            val isVideoSource = (song.source == AudioSource.YOUTUBE_MUSIC || song.source == AudioSource.YOUTUBE_VIDEO)
            val effectiveAsVideo = asVideo && isVideoSource

            // Resolve real playable stream URL from YouTube Opus / Video MP4 / SoundCloud / Spotify Bridge
            val url = if (effectiveAsVideo) {
                if (explicitQuality != null) {
                    selectedVideoQuality = explicitQuality
                    song.videoUrl = explicitQuality.videoUrl
                    if (explicitQuality.audioUrl != null) {
                        song.streamUrl = explicitQuality.audioUrl
                    }
                    explicitQuality.videoUrl
                } else {
                    val qualities = ytResolver.resolveVideoQualities(song)
                    availableVideoQualities = qualities
                    val chosen = qualities.firstOrNull()
                    selectedVideoQuality = chosen
                    if (chosen?.audioUrl != null) {
                        song.streamUrl = chosen.audioUrl
                    }
                    chosen?.videoUrl ?: (song.videoUrl ?: ytResolver.resolveVideoStreamUrl(song)?.also { song.videoUrl = it }
                        ?: ytResolver.resolveStreamUrl(song))
                }
            } else {
                song.streamUrl ?: when (song.source) {
                    AudioSource.YOUTUBE_MUSIC, AudioSource.YOUTUBE_VIDEO -> ytResolver.resolveStreamUrl(song)?.also { song.streamUrl = it }
                    AudioSource.SOUNDCLOUD -> scResolver.resolveStreamUrl(song)?.also { song.streamUrl = it }
                    AudioSource.SPOTIFY -> spotifyBridge.resolveTrackToPlayableStream(song)?.also { song.streamUrl = it }
                }
            }

            isResolvingStream = false

            if (url != null) {
                val extras = android.os.Bundle().apply {
                    if (effectiveAsVideo && !song.streamUrl.isNullOrEmpty() && song.streamUrl != url) {
                        putString("audioUrl", song.streamUrl)
                    }
                }
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(if (song.thumbnailUrl.isNotEmpty()) Uri.parse(song.thumbnailUrl) else null)
                            .setExtras(extras)
                            .build()
                    )

                if (url.contains(".m3u8") || url.contains("/hls")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }

                val mediaItem = mediaItemBuilder.build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                if (isSameSong && curPos > 0) {
                    controller.seekTo(curPos)
                }
                controller.play()
            } else {
                controller.pause()
                controller.clearMediaItems()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Không thể lấy luồng âm thanh hoặc video cho bài này", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        DebugLogServer.stop()
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
    currentSong: Song? = null,
    isDebugMode: Boolean = false,
    onDebugModeToggle: (Boolean) -> Unit = {},
    debugServerUrl: String = "http://localhost:8888",
    onCopyLogcat: () -> Unit = {},
    onClearLogcat: () -> Unit = {},
    availableVideoQualities: List<VideoQuality> = emptyList(),
    selectedVideoQuality: VideoQuality? = null,
    onChangeVideoQuality: (VideoQuality) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(AudioSource.YOUTUBE_MUSIC) }
    var isPlayerSheetOpen by remember { mutableStateOf(false) }

    // Live Data State
    val libraryManager = remember { LibraryManager.getInstance(context) }
    var homeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    val isAudioLoading = isResolving || isBuffering
    var currentTitle by remember { mutableStateOf("") }
    var currentArtist by remember { mutableStateOf("") }
    var currentArtwork by remember { mutableStateOf("") }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var selectedNavTab by remember { mutableIntStateOf(0) }

    // Infinite Playback & Auto-Queue State
    var isInfinitePlayback by remember { mutableStateOf(true) }
    var infiniteBatchSize by remember { mutableIntStateOf(25) }
    var isAutoQueueRelated by remember { mutableStateOf(true) }
    var isLoadingInfinite by remember { mutableStateOf(false) }

    // Repeat & Shuffle State
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) } // 0: OFF, 1: ONE, 2: ALL
    var isShuffleEnabled by remember { mutableStateOf(false) }

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

    fun playTrack(song: Song, requestedVideo: Boolean = isVideoMode) {
        val canVideo = (song.source == AudioSource.YOUTUBE_MUSIC || song.source == AudioSource.YOUTUBE_VIDEO)
        val actualVideo = requestedVideo && canVideo
        if (!canVideo && isVideoMode) {
            isVideoMode = false
        }
        activeSong = song
        currentTitle = song.title
        currentArtist = song.artist
        currentArtwork = song.thumbnailUrl

        // Auto-queue related tracks if standalone track and queue is empty
        if (isAutoQueueRelated && songQueue.isEmpty()) {
            coroutineScope.launch(Dispatchers.Default) {
                try {
                    val pool = (homeSongs + searchResults + libraryManager.getFavorites()).distinctBy { it.id }
                    val related = LocalHeuristicRanker.recommendRelatedSongs(
                        baseSong = song,
                        candidatePool = pool,
                        targetCount = 15,
                        historySongs = libraryManager.getRecentlyPlayed()
                    )
                    val filtered = libraryManager.filterBlocked(related).filterNot { it.id == song.id }
                    if (filtered.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            songQueue = filtered
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        onPlaySong(song, actualVideo)
    }

    fun playNextInQueue() {
        if (songQueue.isNotEmpty()) {
            val next = songQueue.first()
            songQueue = songQueue.drop(1)
            playTrack(next, isVideoMode)
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

    var personalizedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Fetch initial live trending music & generate personalized recommendations
    LaunchedEffect(Unit) {
        isLoadingHome = true
        val trending = onSearch("Top thịnh hành Việt Nam", AudioSource.YOUTUBE_MUSIC)
        homeSongs = libraryManager.filterBlocked(trending)
        if (homeSongs.isNotEmpty()) {
            currentTitle = homeSongs.first().title
            currentArtist = homeSongs.first().artist
            currentArtwork = homeSongs.first().thumbnailUrl
        }

        val history = libraryManager.getRecentlyPlayed()
        if (history.isNotEmpty() && homeSongs.isNotEmpty()) {
            val related = LocalHeuristicRanker.recommendRelatedSongs(
                baseSong = history.first(),
                candidatePool = homeSongs,
                targetCount = 8,
                historySongs = history
            )
            personalizedSongs = libraryManager.filterBlocked(related).filterNot { it.id == history.first().id }
        }
        isLoadingHome = false
    }

    // Debounced search when user types
    LaunchedEffect(searchQuery, selectedSource) {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            delay(400) // Debounce
            isSearching = true
            val rawResults = onSearch(q, selectedSource)
            searchResults = libraryManager.filterBlocked(rawResults)
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
                if (playing) isBuffering = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) {
                    if (isStopAtEndOfTrack) {
                        mediaController?.pause()
                        isStopAtEndOfTrack = false
                        Toast.makeText(context, "Đã dừng phát khi hết bài hát", Toast.LENGTH_SHORT).show()
                    } else if (repeatMode == Player.REPEAT_MODE_ONE) {
                        mediaController?.seekTo(0)
                        mediaController?.play()
                    } else if (songQueue.isNotEmpty()) {
                        playNextInQueue()
                    } else if (isInfinitePlayback && !isLoadingInfinite) {
                        coroutineScope.launch {
                            isLoadingInfinite = true
                            try {
                                val base = activeSong ?: currentSong
                                if (base != null) {
                                    val candidatePool = (homeSongs + searchResults + libraryManager.getFavorites()).distinctBy { it.id }
                                    val newSongs = LocalHeuristicRanker.recommendRelatedSongs(
                                        baseSong = base,
                                        candidatePool = candidatePool,
                                        targetCount = infiniteBatchSize,
                                        historySongs = libraryManager.getRecentlyPlayed()
                                    )
                                    val filtered = libraryManager.filterBlocked(newSongs).filterNot { it.id == base.id }
                                    if (filtered.isNotEmpty()) {
                                        songQueue = filtered
                                        playTrack(filtered.first(), isVideoMode)
                                        Toast.makeText(context, "Phát vô hạn: Đã thêm +${filtered.size} bài tiếp theo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (_: Exception) {} finally {
                                isLoadingInfinite = false
                            }
                        }
                    }
                }
            }
            override fun onRepeatModeChanged(newRepeatMode: Int) {
                repeatMode = newRepeatMode
            }
            override fun onShuffleModeEnabledChanged(shuffleEnabled: Boolean) {
                isShuffleEnabled = shuffleEnabled
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

    // Progress Ticker & 20-second history verification
    var accumulatedPlayMs by remember { mutableLongStateOf(0L) }
    var recordedSongIdToHistory by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isPlaying, activeSong?.id) {
        accumulatedPlayMs = 0L
        while (isPlaying) {
            mediaController?.let {
                currentPositionMs = it.currentPosition
                if (it.duration > 0) durationMs = it.duration
            }
            delay(500)
            accumulatedPlayMs += 500L
            val cur = activeSong
            if (cur != null && accumulatedPlayMs >= 20_000L && recordedSongIdToHistory != cur.id) {
                recordedSongIdToHistory = cur.id
                libraryManager.addToRecent(cur)
            }
        }
    }

    BackHandler(enabled = isPlayerSheetOpen) {
        isPlayerSheetOpen = false
    }

    val isGlass = currentThemeStyle == AppThemeStyle.LIQUID_GLASS

    val bgModifier = if (isGlass) {
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    Color(0xFF070B12)
                ),
                radius = 1600f
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
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                when (selectedNavTab) {
                    0 -> {
                        // Tab 0: Khám phá (Discover / Home)
                        Column(
                            modifier = Modifier
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

                            Spacer(modifier = Modifier.height(14.dp))

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
                                    // Section 0: Personalized Recommendations (From >20s History)
                                    if (personalizedSongs.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "✨ Dành riêng cho bạn (Từ lịch sử xem)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                items(personalizedSongs) { song ->
                                                    val isSongActive = activeSong?.id == song.id
                                                    AlbumGridCard(
                                                        song = song,
                                                        onClick = { playTrack(song) },
                                                        isActive = isSongActive,
                                                        isLoading = isSongActive && isAudioLoading
                                                    )
                                                }
                                            }
                                        }
                                    }

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
                                                        val isSongActive = activeSong?.id == song.id
                                                        Box(modifier = Modifier.weight(1f)) {
                                                            QuickPickTile(
                                                                song = song,
                                                                onClick = { playTrack(song) },
                                                                isActive = isSongActive,
                                                                isLoading = isSongActive && isAudioLoading
                                                            )
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
                                                val isSongActive = activeSong?.id == song.id
                                                AlbumGridCard(
                                                    song = song,
                                                    onClick = { playTrack(song) },
                                                    isActive = isSongActive,
                                                    isLoading = isSongActive && isAudioLoading
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
                                        val isSongActive = activeSong?.id == song.id
                                        SongListCard(
                                            song = song,
                                            onClick = { playTrack(song) },
                                            isActive = isSongActive,
                                            isLoading = isSongActive && isAudioLoading,
                                            isFavorite = libraryManager.isFavorite(song.id),
                                            onToggleFavorite = {
                                                val isFav = libraryManager.toggleFavorite(song)
                                                Toast.makeText(context, if (isFav) "Đã thêm vào yêu thích" else "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show()
                                            },
                                            onAddToQueue = {
                                                songQueue = songQueue + song
                                                Toast.makeText(context, "Đã thêm vào danh sách chờ: ${song.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            onBlockSong = {
                                                libraryManager.blockSong(song)
                                                homeSongs = libraryManager.filterBlocked(homeSongs)
                                                Toast.makeText(context, "Đã chặn bài hát: ${song.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            onBlockArtist = {
                                                libraryManager.blockArtist(song.artist)
                                                homeSongs = libraryManager.filterBlocked(homeSongs)
                                                Toast.makeText(context, "Đã chặn nghệ sĩ: ${song.artist}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Tab 1: Tìm kiếm (Dedicated SearchScreen)
                        SearchScreen(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            selectedSource = selectedSource,
                            onSourceSelect = { selectedSource = it },
                            isSearching = isSearching,
                            searchResults = searchResults,
                            onPlaySong = { song -> playTrack(song) },
                            onAddToQueue = { song ->
                                songQueue = songQueue + song
                                Toast.makeText(context, "Đã thêm vào danh sách chờ: ${song.title}", Toast.LENGTH_SHORT).show()
                            },
                            onToggleFavorite = { song ->
                                val isFav = libraryManager.toggleFavorite(song)
                                Toast.makeText(context, if (isFav) "Đã thêm vào yêu thích" else "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show()
                            },
                            isFavorite = { id -> libraryManager.isFavorite(id) },
                            onBlockSong = { song ->
                                libraryManager.blockSong(song)
                                searchResults = libraryManager.filterBlocked(searchResults)
                                homeSongs = libraryManager.filterBlocked(homeSongs)
                                Toast.makeText(context, "Đã chặn bài hát: ${song.title}", Toast.LENGTH_SHORT).show()
                            },
                            onBlockArtist = { artist ->
                                libraryManager.blockArtist(artist)
                                searchResults = libraryManager.filterBlocked(searchResults)
                                homeSongs = libraryManager.filterBlocked(homeSongs)
                                Toast.makeText(context, "Đã chặn nghệ sĩ: $artist", Toast.LENGTH_SHORT).show()
                            },
                            onAiSearchClick = {
                                showAiDialog = true
                                coroutineScope.launch {
                                    aiGenerator.generatePlaylist(
                                        userPrompt = searchQuery.ifEmpty { "Nhạc hay tuyển chọn" },
                                        targetSongCount = 20
                                    ) { state ->
                                        aiGenerationState = state
                                    }
                                }
                            },
                            isGlass = isGlass
                        )
                    }
                    2 -> {
                        // Tab 2: Thư viện (Dedicated LibraryScreen)
                        LibraryScreen(
                            libraryManager = libraryManager,
                            onPlaySong = { song -> playTrack(song) },
                            onPlayAll = { songs ->
                                if (songs.isNotEmpty()) {
                                    playTrack(songs.first())
                                    songQueue = songs.drop(1)
                                }
                            },
                            onAddToQueue = { song ->
                                songQueue = songQueue + song
                                Toast.makeText(context, "Đã thêm vào danh sách chờ: ${song.title}", Toast.LENGTH_SHORT).show()
                            },
                            isGlass = isGlass
                        )
                    }
                    3 -> {
                        // Tab 3: Cài đặt (SettingsScreen)
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
                                },
                                isDebugMode = isDebugMode,
                                onDebugModeToggle = onDebugModeToggle,
                                debugServerUrl = debugServerUrl,
                                onCopyLogcat = onCopyLogcat,
                                onClearLogcat = onClearLogcat,
                                isInfinitePlayback = isInfinitePlayback,
                                onInfinitePlaybackToggle = { isInfinitePlayback = it },
                                infiniteBatchSize = infiniteBatchSize,
                                onInfiniteBatchSizeChange = { infiniteBatchSize = it },
                                isAutoQueueRelated = isAutoQueueRelated,
                                onAutoQueueRelatedToggle = { isAutoQueueRelated = it },
                                onOpenBlocklist = { selectedNavTab = 2 }
                            )
                        }
                    }
                }

                // Floating MiniPlayer (Present across all tabs above the Bottom Navigation Bar)
                if (currentTitle.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        M3MiniPlayer(
                            title = currentTitle,
                            artist = currentArtist,
                            artworkUrl = currentArtwork,
                            isPlaying = isPlaying,
                            isResolving = isAudioLoading,
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
                    val cur = activeSong ?: currentSong
                    val canVideo = cur?.source == AudioSource.YOUTUBE_MUSIC || cur?.source == AudioSource.YOUTUBE_VIDEO
                    if (enableVideo && !canVideo) {
                        Toast.makeText(context, "Chỉ hỗ trợ chế độ Video cho nguồn YouTube", Toast.LENGTH_SHORT).show()
                    } else {
                        isVideoMode = enableVideo
                        cur?.let { song ->
                            onPlaySong(song, enableVideo)
                        }
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
                    playTrack(qSong, isVideoMode)
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
                onRefreshQueue = {
                    val base = activeSong ?: currentSong
                    if (base != null) {
                        coroutineScope.launch {
                            val candidatePool = (homeSongs + searchResults + libraryManager.getFavorites() + libraryManager.getRecentlyPlayed()).distinctBy { it.id }
                            val newSongs = LocalHeuristicRanker.recommendRelatedSongs(
                                baseSong = base,
                                candidatePool = candidatePool,
                                targetCount = 20,
                                historySongs = libraryManager.getRecentlyPlayed()
                            )
                            val filtered = libraryManager.filterBlocked(newSongs).filterNot { it.id == base.id }
                            if (filtered.isNotEmpty()) {
                                songQueue = filtered
                                Toast.makeText(context, "Đã làm mới danh sách chờ (+${filtered.size} bài)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Đang tải thêm danh sách đề xuất...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onRecommendQueueAi = {
                    val cur = activeSong ?: currentSong
                    val prompt = if (cur != null) "Tạo playlist phong cách và giai điệu tương tự '${cur.title}' của ${cur.artist}" else "Playlist tuyển chọn thịnh hành"
                    showAiDialog = true
                    coroutineScope.launch {
                        aiGenerator.generatePlaylist(
                            userPrompt = prompt,
                            targetSongCount = 20
                        ) { state ->
                            aiGenerationState = state
                        }
                    }
                },
                onSkipNext = { playNextInQueue() },
                onSkipPrevious = { playPreviousInQueue() },
                isLoading = isAudioLoading,
                repeatMode = repeatMode,
                onToggleRepeat = {
                    val next = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    repeatMode = next
                    mediaController?.repeatMode = next
                    val msg = when (next) {
                        Player.REPEAT_MODE_ALL -> "Lặp lại danh sách"
                        Player.REPEAT_MODE_ONE -> "Lặp lại 1 bài"
                        else -> "Tắt lặp lại"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                isShuffleEnabled = isShuffleEnabled,
                onToggleShuffle = {
                    val next = !isShuffleEnabled
                    isShuffleEnabled = next
                    mediaController?.shuffleModeEnabled = next
                    if (next && songQueue.isNotEmpty()) {
                        songQueue = songQueue.shuffled()
                        Toast.makeText(context, "Đã xáo trộn danh sách chờ", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (next) "Bật phát ngẫu nhiên" else "Tắt phát ngẫu nhiên", Toast.LENGTH_SHORT).show()
                    }
                },
                availableVideoQualities = availableVideoQualities,
                selectedVideoQuality = selectedVideoQuality,
                onChangeVideoQuality = onChangeVideoQuality
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
                        playTrack(first, isVideoMode)
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
fun QuickPickTile(
    song: Song,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isLoading: Boolean = false
) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isActive) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else if (isGlass) BorderStroke(1.dp, GlassBorderDark) else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
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
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) { Text("🎵", fontSize = 18.sp) }
                }
                if (isActive && isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isActive && isLoading) "Đang tải luồng..." else song.artist,
                    fontSize = 10.5.sp,
                    color = if (isActive && isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AlbumGridCard(
    song: Song,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isLoading: Boolean = false
) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    Card(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isActive) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else if (isGlass) BorderStroke(1.dp, GlassBorderDark) else CardDefaults.outlinedCardBorder(),
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
                    if (isActive && isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = song.title,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isActive && isLoading) "Đang tải luồng..." else song.artist,
                fontSize = 10.5.sp,
                color = if (isActive && isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongListCard(
    song: Song,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    isActive: Boolean = false,
    isLoading: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onBlockSong: (() -> Unit)? = null,
    onBlockArtist: (() -> Unit)? = null
) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else if (isGlass) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = if (isActive) {
            BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        } else if (isGlass) {
            BorderStroke(1.dp, GlassBorderDark)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
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
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) { Text("♫", fontSize = 20.sp) }
                }

                // Loading animation spinner when active song is resolving / buffering
                if (isActive && isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = song.source.name.replace("_", " "),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    if (isActive && isLoading) {
                        Text(
                            text = "Đang tải luồng...",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = song.artist,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Options & Quick actions
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleFavorite != null) {
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onAddToQueue != null) {
                        IconButton(onClick = onAddToQueue, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Thêm vào danh sách chờ",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (onBlockSong != null || onBlockArtist != null) {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Thêm vào danh sách chờ", fontSize = 12.5.sp) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            }
                        )
                    }
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Bỏ khỏi yêu thích" else "Thêm vào yêu thích", fontSize = 12.5.sp) },
                            leadingIcon = { Icon(if (isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                    }
                    if (onBlockSong != null) {
                        DropdownMenuItem(
                            text = { Text("Chặn bài hát này", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onBlockSong()
                            }
                        )
                    }
                    if (onBlockArtist != null) {
                        DropdownMenuItem(
                            text = { Text("Chặn nghệ sĩ: ${song.artist}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onBlockArtist()
                            }
                        )
                    }
                }
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
    var lockedAxis by remember { mutableIntStateOf(0) } // 0: uncommitted, 1: horizontal, 2: vertical

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        lockedAxis = 0
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = dragOffsetX + dragAmount.x
                        val newY = dragOffsetY + dragAmount.y
                        if (lockedAxis == 0) {
                            val absX = kotlin.math.abs(newX)
                            val absY = kotlin.math.abs(newY)
                            if (absX > 16f && absX > absY * 1.25f) {
                                lockedAxis = 1
                            } else if (absY > 16f && absY > absX * 1.25f) {
                                lockedAxis = 2
                            }
                        }
                        if (lockedAxis == 1) {
                            dragOffsetX = newX.coerceIn(-160f, 160f)
                            dragOffsetY = 0f
                        } else if (lockedAxis == 2) {
                            dragOffsetY = newY.coerceIn(-180f, 80f)
                            dragOffsetX = 0f
                        } else {
                            dragOffsetX += dragAmount.x * 0.5f
                            dragOffsetY += dragAmount.y * 0.5f
                        }
                    },
                    onDragEnd = {
                        val thresholdX = 45f
                        val thresholdY = 35f

                        if (lockedAxis == 2 || kotlin.math.abs(dragOffsetY) > kotlin.math.abs(dragOffsetX)) {
                            if (dragOffsetY < -thresholdY) {
                                // Vuốt từ dưới lên -> mở playback chi tiết ngay lập tức đồng bộ theo vị trí ngón tay
                                onClick()
                            } else if (dragOffsetY > thresholdY + 15f) {
                                // Vuốt từ trên xuống -> force stop và tắt hẳn bài đang phát
                                onForceStop()
                            }
                        } else if (lockedAxis == 1 || kotlin.math.abs(dragOffsetX) > kotlin.math.abs(dragOffsetY)) {
                            if (dragOffsetX < -thresholdX) {
                                // Vuốt sang trái -> skip next
                                onNext()
                            } else if (dragOffsetX > thresholdX) {
                                // Vuốt sang phải -> skip previous
                                onPrevious()
                            }
                        }

                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        lockedAxis = 0
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                        lockedAxis = 0
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
                Text(
                    text = if (isResolving) "Đang tải luồng phát..." else artist,
                    fontSize = 11.sp,
                    color = if (isResolving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = if (isResolving) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    onRefreshQueue: () -> Unit = {},
    onRecommendQueueAi: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    isLoading: Boolean = false,
    repeatMode: Int = 0,
    onToggleRepeat: () -> Unit = {},
    isShuffleEnabled: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    availableVideoQualities: List<VideoQuality> = emptyList(),
    selectedVideoQuality: VideoQuality? = null,
    onChangeVideoQuality: (VideoQuality) -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(FullPlayerTab.NOW_PLAYING) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragDownOffsetY"
    )

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionRatio by remember { mutableFloatStateOf(0f) }
    val displayedRatio = if (isScrubbing) scrubPositionRatio else (if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f)
    val displayedTimeMs = if (isScrubbing) (scrubPositionRatio * durationMs).toLong() else currentPositionMs

    val currentPosLatest by rememberUpdatedState(currentPositionMs)
    val durationLatest by rememberUpdatedState(durationMs)

    // Double-Tap to Skip Feedback State with Accumulator and Auto-reset
    var skipFeedbackSide by remember { mutableIntStateOf(0) } // -1 for left, 1 for right
    var accumulatedSkipSeconds by remember { mutableIntStateOf(0) }
    var skipAnimTrigger by remember { mutableIntStateOf(0) }
    var lastDoubleTapTime by remember { mutableLongStateOf(0L) }
    var pendingSeekTargetMs by remember { mutableLongStateOf(-1L) }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (accumulatedSkipSeconds > 0 && skipFeedbackSide != 0) 1f else 0f,
        animationSpec = tween(140),
        label = "skipOverlayAlpha"
    )
    val overlayScale by animateFloatAsState(
        targetValue = if (accumulatedSkipSeconds > 0 && skipFeedbackSide != 0) 1f else 0.8f,
        animationSpec = tween(140),
        label = "skipOverlayScale"
    )

    LaunchedEffect(skipAnimTrigger) {
        if (skipAnimTrigger > 0) {
            delay(850)
            accumulatedSkipSeconds = 0
            skipFeedbackSide = 0
            pendingSeekTargetMs = -1L
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

    if (showQualityDialog) {
        VideoQualityDialog(
            currentQuality = selectedVideoQuality,
            availableQualities = availableVideoQualities,
            onSelectQuality = { q -> onChangeVideoQuality(q) },
            onDismiss = { showQualityDialog = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedDragOffsetY.roundToInt()) }
            .pointerInput(lyrics.size, currentTab) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 160f) {
                            onClose()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0 || dragOffsetY > 0) {
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        } else if (dragAmount < -25f && currentTab == FullPlayerTab.NOW_PLAYING) {
                            if (lyrics.isNotEmpty()) {
                                currentTab = FullPlayerTab.LYRICS
                            } else {
                                currentTab = FullPlayerTab.QUEUE
                            }
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle pill at top
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                        if (isVideoMode) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable { showQualityDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.HighQuality, contentDescription = "Chất lượng", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = selectedVideoQuality?.label ?: "Auto",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
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

                    // Lyrics Button (hidden if no lyrics)
                    if (lyrics.isNotEmpty()) {
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

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Refresh recommendations button
                                    FilledTonalButton(
                                        onClick = onRefreshQueue,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Làm mới", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Recommend with AI button
                                    FilledTonalButton(
                                        onClick = onRecommendQueueAi,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (songQueue.isNotEmpty()) {
                                        IconButton(
                                            onClick = onClearQueue,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa tất cả", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        }
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
                                            val now = System.currentTimeMillis()
                                            val isLeft = tapOffset.x < size.width / 2
                                            val side = if (isLeft) -1 else 1

                                            // Accumulate skip seconds if consecutive taps occur in same direction within 900ms
                                            if (side == skipFeedbackSide && (now - lastDoubleTapTime) < 900L) {
                                                accumulatedSkipSeconds += skipDurationSeconds
                                            } else {
                                                accumulatedSkipSeconds = skipDurationSeconds
                                                skipFeedbackSide = side
                                            }
                                            lastDoubleTapTime = now

                                            val basePos = if (pendingSeekTargetMs >= 0) pendingSeekTargetMs else currentPosLatest
                                            val targetPos = if (isLeft) {
                                                (basePos - skipDurationSeconds * 1000L).coerceAtLeast(0L)
                                            } else {
                                                (basePos + skipDurationSeconds * 1000L).coerceAtMost(durationLatest)
                                            }
                                            pendingSeekTargetMs = targetPos
                                            onSeek(targetPos)
                                            skipAnimTrigger++
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

                            // Loading state overlay for audio/video buffering
                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.Black.copy(alpha = 0.72f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Đang tải luồng phát...",
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Animated Overlay for Double-Tap Skip Feedback with GPU accelerated fade/scale
                            if (overlayAlpha > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                        .graphicsLayer(
                                            alpha = overlayAlpha,
                                            scaleX = overlayScale,
                                            scaleY = overlayScale
                                        ),
                                    contentAlignment = if (skipFeedbackSide == -1) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color.Black.copy(alpha = 0.85f),
                                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                        tonalElevation = 8.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (skipFeedbackSide == -1) Icons.Default.FastRewind else Icons.Default.FastForward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "${if (skipFeedbackSide == -1) "-" else "+"}${accumulatedSkipSeconds}s",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp
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

                    // Custom Volume Boost Chip (0% to 300%)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (customVolume >= 150) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (customVolume >= 150) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { showVolumeDialog = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (customVolume >= 150) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (customVolume >= 150) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$customVolume%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customVolume >= 150) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Scrubber Slider with seeking animation & preview tooltip
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Seek Preview Bubble
                AnimatedVisibility(
                    visible = isScrubbing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "Tua đến: ${formatTime(displayedTimeMs)}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Slider(
                    value = displayedRatio,
                    onValueChange = { newRatio ->
                        isScrubbing = true
                        scrubPositionRatio = newRatio
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        val targetMs = (scrubPositionRatio * durationMs).toLong()
                        onSeek(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(displayedTimeMs), fontSize = 11.sp, color = if (isScrubbing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal)
                    Text(formatTime(durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Controls Cluster (Shuffle, Previous, Play/Pause, Next, Repeat)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onSkipPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                }

                FilledTonalIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(32.dp))
                    }
                }

                IconButton(onClick = onSkipNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Bottom Bar to switch views
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        currentTab = when (currentTab) {
                            FullPlayerTab.NOW_PLAYING -> if (lyrics.isNotEmpty()) FullPlayerTab.LYRICS else FullPlayerTab.QUEUE
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
                                FullPlayerTab.NOW_PLAYING -> if (lyrics.isNotEmpty()) "Chạm để xem Lời bài hát & Danh sách chờ" else "Chạm để mở Danh sách chờ"
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
                        if (lyrics.isNotEmpty()) {
                            Icon(Icons.Default.Lyrics, contentDescription = null, tint = if (currentTab == FullPlayerTab.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        }
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = if (currentTab == FullPlayerTab.QUEUE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

/**
 * Volume Boost Controller Dialog (0% - 300%) with high volume warning
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VolumeBoostDialog(
    currentVolume: Int,
    onVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isHighVolume = currentVolume >= 150
    val presets = listOf(25, 50, 75, 100, 125, 150, 200, 250, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isHighVolume) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = if (isHighVolume) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text("Âm lượng ứng dụng ($currentVolume%)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Tùy biến âm lượng từ 0% đến 300% (kết hợp DSP Gain Boost khi vượt quá 100%).",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Slider(
                    value = currentVolume.toFloat(),
                    onValueChange = { newVol -> onVolumeChange(newVol.toInt()) },
                    valueRange = 0f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (isHighVolume) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        activeTrackColor = if (isHighVolume) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0%", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("100% (Chuẩn)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("300% (Max DSP)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }

                // Quick preset buttons: 25%, 50%, 75%, 100%, 125%, 150%, 200%, 250%, 300%
                Text("Tùy chọn nhanh:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = currentVolume == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onVolumeChange(preset) },
                            label = { Text("$preset%", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (preset >= 150) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = if (preset >= 150) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (isHighVolume) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️", fontSize = 18.sp)
                            Text(
                                text = "Cảnh báo âm lượng cao (≥ 150%): Mức âm lượng khuyếch đại DSP Boost mạnh có thể gây hại thính giác nếu nghe qua tai nghe liên tục và có thể gây méo âm thanh trên loa nhỏ.",
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

@Composable
fun VideoQualityDialog(
    currentQuality: VideoQuality?,
    availableQualities: List<VideoQuality>,
    onSelectQuality: (VideoQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Chất lượng video", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chọn độ phân giải video mong muốn:",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                if (availableQualities.isEmpty()) {
                    Text(
                        text = "Không có độ phân giải thay thế hoặc đang dùng nguồn mặc định.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableQualities) { quality ->
                            val isSelected = currentQuality?.label == quality.label
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectQuality(quality)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = quality.label,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

