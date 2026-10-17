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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.litemusic.app.core.audio.AudioCacheManager
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import com.litemusic.app.R
import com.litemusic.app.core.i18n.LocaleHelper
import com.litemusic.app.core.debug.DebugLogServer
import java.io.File
import com.litemusic.app.core.network.EndpointHealthManager
import com.litemusic.app.core.network.AnonymousSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.litemusic.app.core.queue.PlaybackQueueManager
import com.litemusic.app.core.queue.QueueSource
import com.litemusic.app.ui.queue.QueueSheet

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    val ytResolver = YouTubeStreamResolver()
    val scResolver = SoundCloudStreamResolver()
    val spotifyBridge = SpotifyMetadataBridge(ytResolver)
    private val lrcClient = LrcLibClient()

    private var activeLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    private var isLyricsLoading by mutableStateOf(false)
    private var isResolvingStream by mutableStateOf(false)
    private var playJob: Job? = null
    private var availableVideoQualities by mutableStateOf<List<VideoQuality>>(emptyList())
    private var selectedVideoQuality by mutableStateOf<VideoQuality?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initMediaController()

        // Background multi-endpoint latency measurement & anonymous session refresh
        EndpointHealthManager.getInstance().startBackgroundPinger(lifecycleScope)
        AnonymousSessionManager.getInstance().ensureVisitorDataAsync(lifecycleScope)

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
                    isLyricsLoading = isLyricsLoading,
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
                        Toast.makeText(this, getString(R.string.toast_logcat_cleared), Toast.LENGTH_SHORT).show()
                    },
                    onClearCache = {
                        try {
                            val freedBytes = AudioCacheManager.clearAllCache(this)
                            val freedMb = String.format(java.util.Locale.US, "%.1f", freedBytes.toDouble() / (1024.0 * 1024.0))
                            Toast.makeText(this, getString(R.string.toast_cache_cleared, freedMb), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, getString(R.string.toast_cache_cleared, "0"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPlaySong = { song, asVideo -> playSong(song, asVideo) },
                    onSearch = { query, source -> searchSongs(query, source) },
                    onGetRelatedSongs = { song -> ytResolver.getRelatedSongs(song) },
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
            AudioSource.ALL -> searchAllSources(query)
            AudioSource.YOUTUBE_MUSIC -> ytResolver.search(query)
            AudioSource.YOUTUBE_VIDEO -> ytResolver.searchRegularVideos(query)
            AudioSource.SOUNDCLOUD -> scResolver.search(query)
            AudioSource.SPOTIFY -> spotifyBridge.fetchPlaylistTracks(query)
        }
    }

    private suspend fun searchAllSources(query: String): List<Song> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        coroutineScope {
            val ytMusicDeferred = async { withTimeoutOrNull(2500L) { runCatching { ytResolver.search(q) }.getOrNull() } ?: emptyList() }
            val ytVideoDeferred = async { withTimeoutOrNull(2500L) { runCatching { ytResolver.searchRegularVideos(q) }.getOrNull() } ?: emptyList() }
            val scDeferred = async { withTimeoutOrNull(2500L) { runCatching { scResolver.search(q) }.getOrNull() } ?: emptyList() }
            val spotifyDeferred = async { withTimeoutOrNull(2500L) { runCatching { spotifyBridge.fetchPlaylistTracks(q) }.getOrNull() } ?: emptyList() }

            val results = awaitAll(ytMusicDeferred, ytVideoDeferred, scDeferred, spotifyDeferred)
            val ytMusic = results[0]
            val ytVideo = results[1]
            val sc = results[2]
            val spotify = results[3]

            val combined = mutableListOf<Song>()
            combined.addAll(ytMusic)
            combined.addAll(ytVideo)
            combined.addAll(sc)
            combined.addAll(spotify)

            if (combined.isEmpty()) return@coroutineScope emptyList()

            fun normalizeKey(song: Song): String {
                val cleanTitle = song.title.lowercase()
                    .replace(Regex("""[\(\[\{].*?[\)\]\}]"""), "")
                    .replace(Regex("""[^a-z0-9\u00C0-\u1EF9]"""), "")
                    .trim()
                val cleanArtist = song.artist.lowercase()
                    .substringBefore("•")
                    .substringBefore("·")
                    .replace(Regex("""[^a-z0-9\u00C0-\u1EF9]"""), "")
                    .trim()
                return "${cleanTitle}___$cleanArtist"
            }

            fun calculateScore(song: Song): Int {
                var score = 0
                val qLower = q.lowercase()
                val titleLower = song.title.lowercase()
                val artistLower = song.artist.lowercase()

                if (titleLower == qLower) {
                    score += 120
                } else if (titleLower.startsWith(qLower)) {
                    score += 70
                } else if (titleLower.contains(qLower)) {
                    score += 45
                }

                if (artistLower.contains(qLower)) {
                    score += 35
                }

                val qWords = qLower.split(Regex("""\s+""")).filter { it.length > 1 }
                for (word in qWords) {
                    if (titleLower.contains(word)) score += 15
                    if (artistLower.contains(word)) score += 10
                }

                when (song.source) {
                    AudioSource.YOUTUBE_MUSIC -> score += 12
                    AudioSource.YOUTUBE_VIDEO -> score += 8
                    AudioSource.SPOTIFY -> score += 6
                    AudioSource.SOUNDCLOUD -> score += 5
                    else -> {}
                }

                return score
            }

            val sorted = combined.sortedByDescending { calculateScore(it) }
            val deduped = mutableListOf<Song>()
            val seenKeys = mutableSetOf<String>()

            for (song in sorted) {
                val key = normalizeKey(song)
                if (key.length > 3) {
                    if (!seenKeys.contains(key)) {
                        seenKeys.add(key)
                        deduped.add(song)
                    }
                } else {
                    deduped.add(song)
                }
            }

            deduped
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

            // Fetch synced lyrics in parallel with robust cleaner & multi-tier fallbacks
            launch {
                isLyricsLoading = true
                activeLyrics = emptyList()
                try {
                    activeLyrics = lrcClient.getLyrics(song.title, song.artist, song.durationSeconds)
                } finally {
                    isLyricsLoading = false
                }
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
                    AudioSource.ALL, AudioSource.YOUTUBE_MUSIC, AudioSource.YOUTUBE_VIDEO -> ytResolver.resolveStreamUrl(song)?.also { song.streamUrl = it }
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
                    Toast.makeText(this@MainActivity, getString(R.string.toast_stream_error), Toast.LENGTH_SHORT).show()
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


data class HomeSectionsData(
    val quickPicks: List<Song> = emptyList(),
    val recommended: List<Song> = emptyList(),
    val trending: List<Song> = emptyList(),
    val discovery: List<Song> = emptyList(),
    val allSectionIds: Set<String> = emptySet()
)

enum class MoodCategory(val titleRes: Int) {
    ALL(R.string.mood_all),
    RELAX(R.string.mood_relax),
    ENERGY(R.string.mood_energy),
    FOCUS(R.string.mood_focus),
    ROMANCE(R.string.mood_romance),
    PARTY(R.string.mood_party)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    mediaController: MediaController?,
    lyrics: List<LyricLine>,
    isLyricsLoading: Boolean = false,
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
    onChangeVideoQuality: (VideoQuality) -> Unit = {},
    onGetRelatedSongs: (suspend (Song) -> List<Song>)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(AudioSource.ALL) }
    var isPlayerSheetOpen by remember { mutableStateOf(false) }
    var showHomeSearchDialog by remember { mutableStateOf(false) }

    // Live Data State
    val libraryManager = remember { LibraryManager.getInstance(context) }
    var homeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    // Homepage Reload & Pull-to-Refresh State
    var refreshHomeTrigger by remember { mutableIntStateOf(0) }
    var isRefreshingHome by remember { mutableStateOf(false) }
    val homeLazyListState = rememberLazyListState()
    var homePullOffset by remember { mutableFloatStateOf(0f) }
    val homePullThreshold = 130f

    val homePullRefreshConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (homePullOffset > 0 && available.y < 0) {
                    val newOffset = (homePullOffset + available.y).coerceAtLeast(0f)
                    val consumed = newOffset - homePullOffset
                    homePullOffset = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && homeLazyListState.firstVisibleItemIndex == 0 && homeLazyListState.firstVisibleItemScrollOffset == 0) {
                    homePullOffset = (homePullOffset + available.y * 0.45f).coerceAtMost(220f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (homePullOffset >= homePullThreshold) {
                    isRefreshingHome = true
                    refreshHomeTrigger++
                }
                homePullOffset = 0f
                return Velocity.Zero
            }
        }
    }

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
    val playbackQueueManager = remember { PlaybackQueueManager.getInstance() }
    val queueState by playbackQueueManager.queueState.collectAsState()
    var isQueueSheetOpen by remember { mutableStateOf(false) }
    val songQueue = remember(queueState) {
        queueState.manualQueue.map { it.song } + queueState.autoQueue.map { it.song }
    }

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
        playbackQueueManager.setNowPlaying(song)

        // Auto-queue related tracks if standalone track and auto queue is empty
        if (isAutoQueueRelated && playbackQueueManager.queueState.value.autoQueue.isEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val liveRelated = onGetRelatedSongs?.invoke(song) ?: emptyList()
                    val pool = (liveRelated + homeSongs + searchResults + libraryManager.getFavorites()).distinctBy { it.id }
                    val related = LocalHeuristicRanker.recommendRelatedSongs(
                        baseSong = song,
                        candidatePool = pool,
                        targetCount = 20,
                        historySongs = libraryManager.getRecentlyPlayed()
                    )
                    val filtered = libraryManager.filterBlocked(related).filterNot { it.id == song.id }
                    if (filtered.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (playbackQueueManager.queueState.value.autoQueue.isEmpty()) {
                                playbackQueueManager.setAutoQueueSongs(filtered, QueueSource.RADIO)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        onPlaySong(song, actualVideo)
    }

    fun playNextInQueue() {
        val next = playbackQueueManager.popNextSong()
        if (next != null) {
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
        playbackQueueManager.clearAllQueue()
        isPlayerSheetOpen = false
        isQueueSheetOpen = false
        Toast.makeText(context, context.getString(R.string.toast_stopped_playback), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.toast_timer_stopped), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var personalizedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedMoodFilter by remember { mutableStateOf(MoodCategory.ALL) }
    val moodFilters = remember { MoodCategory.entries }

    var moodSongsCache by remember { mutableStateOf<Map<MoodCategory, List<Song>>>(emptyMap()) }
    var isLoadingMood by remember { mutableStateOf(false) }

    var infiniteDiscoverySongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingMoreDiscovery by remember { mutableStateOf(false) }
    var hasMoreDiscovery by remember { mutableStateOf(true) }

    // Unified Home & Mood Data Fetching (eliminates race conditions and concurrent overwrites)
    LaunchedEffect(selectedMoodFilter, refreshHomeTrigger) {
        val isRefresh = refreshHomeTrigger > 0
        if (isRefresh) {
            isRefreshingHome = true
        }

        if (selectedMoodFilter == MoodCategory.ALL) {
            if (homeSongs.isEmpty() || isRefresh) {
                isLoadingHome = true
                infiniteDiscoverySongs = emptyList()
                hasMoreDiscovery = true

                try {
                    val trending = onSearch("Top Music Trending", AudioSource.YOUTUBE_MUSIC)
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
                } catch (_: Exception) {}
                isLoadingHome = false
            }
        } else {
            // Specific Mood Category Selected
            if (!moodSongsCache.containsKey(selectedMoodFilter) || isRefresh) {
                isLoadingMood = true
                infiniteDiscoverySongs = emptyList()
                hasMoreDiscovery = true

                val query = when (selectedMoodFilter) {
                    MoodCategory.RELAX -> "Chill acoustic thư giãn acoustic"
                    MoodCategory.ENERGY -> "High energy edm remix sôi động"
                    MoodCategory.FOCUS -> "Lofi beats study work tập trung"
                    MoodCategory.ROMANCE -> "Nhạc tình yêu ballad lãng mạn"
                    MoodCategory.PARTY -> "Party edm club remix vinahouse"
                    else -> "Top Music Trending"
                }
                try {
                    val res = onSearch(query, AudioSource.YOUTUBE_MUSIC)
                    val filtered = libraryManager.filterBlocked(res)
                    if (filtered.isNotEmpty()) {
                        moodSongsCache = moodSongsCache + (selectedMoodFilter to filtered)
                    }
                } catch (_: Exception) {}
                isLoadingMood = false
            }
        }
        isRefreshingHome = false
    }

    val currentMoodPool = remember(selectedMoodFilter, homeSongs, moodSongsCache) {
        if (selectedMoodFilter == MoodCategory.ALL) {
            homeSongs
        } else {
            moodSongsCache[selectedMoodFilter] ?: emptyList()
        }
    }

    // Deduplicated sections data across Quick Picks, Recommended, Trending, and Discovery
    val homeSections = remember(selectedMoodFilter, currentMoodPool, personalizedSongs) {
        val seen = mutableSetOf<String>()
        val distinctPool = currentMoodPool.distinctBy { it.id }

        val qp = distinctPool.take(4).also { list -> list.forEach { seen.add(it.id) } }

        // Section 2: For ALL, use personalized recommendations from history + pool.
        // For Mood categories, populate strictly from mood pool so recommendations match the mood!
        val rec = if (selectedMoodFilter == MoodCategory.ALL) {
            val recCandidates = (personalizedSongs + distinctPool).filterNot { it.id in seen }.distinctBy { it.id }
            recCandidates.take(6).also { list -> list.forEach { seen.add(it.id) } }
        } else {
            distinctPool.filterNot { it.id in seen }.take(6).also { list -> list.forEach { seen.add(it.id) } }
        }

        val tr = distinctPool.filterNot { it.id in seen }.take(8).also { list -> list.forEach { seen.add(it.id) } }
        val disc = distinctPool.filterNot { it.id in seen }.also { list -> list.forEach { seen.add(it.id) } }

        HomeSectionsData(
            quickPicks = qp,
            recommended = rec,
            trending = tr,
            discovery = disc,
            allSectionIds = seen
        )
    }

    // Infinite scroll detection: triggers when user scrolls near the bottom or needs initial seed
    val shouldLoadMoreDiscovery = remember {
        derivedStateOf {
            val layoutInfo = homeLazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMoreDiscovery.value, selectedMoodFilter, currentMoodPool.size) {
        val combinedCount = homeSections.discovery.size + infiniteDiscoverySongs.size
        val needsSeed = combinedCount < 6 && currentMoodPool.isNotEmpty()
        if ((shouldLoadMoreDiscovery.value || needsSeed) && !isLoadingMoreDiscovery && !isLoadingHome && !isLoadingMood && hasMoreDiscovery) {
            isLoadingMoreDiscovery = true
            try {
                val seedSong = (homeSections.discovery + infiniteDiscoverySongs + homeSections.trending + homeSections.quickPicks).shuffled().firstOrNull()
                val candidateSongs = if (selectedMoodFilter != MoodCategory.ALL) {
                    val moodKeyword = when (selectedMoodFilter) {
                        MoodCategory.RELAX -> "chill acoustic thư giãn"
                        MoodCategory.ENERGY -> "high energy edm dance remix"
                        MoodCategory.FOCUS -> "lofi beats study focus"
                        MoodCategory.ROMANCE -> "tình yêu ballad lãng mạn"
                        MoodCategory.PARTY -> "party edm club remix"
                        else -> "top music"
                    }
                    if (seedSong != null) {
                        val related = onGetRelatedSongs?.invoke(seedSong) ?: emptyList()
                        related.ifEmpty {
                            onSearch("${seedSong.artist} $moodKeyword", AudioSource.YOUTUBE_MUSIC)
                        }
                    } else {
                        onSearch("$moodKeyword hits", AudioSource.YOUTUBE_MUSIC)
                    }
                } else {
                    if (seedSong != null) {
                        val related = onGetRelatedSongs?.invoke(seedSong) ?: emptyList()
                        related.ifEmpty {
                            onSearch("${seedSong.artist} hit songs", AudioSource.YOUTUBE_MUSIC)
                        }
                    } else {
                        onSearch("Trending Music Explore", AudioSource.YOUTUBE_MUSIC)
                    }
                }
                val filtered = libraryManager.filterBlocked(candidateSongs)
                val allKnownIds = homeSections.allSectionIds + infiniteDiscoverySongs.map { it.id }.toSet()
                val uniqueNew = filtered.filterNot { it.id in allKnownIds }
                if (uniqueNew.isEmpty()) {
                    hasMoreDiscovery = false
                } else {
                    infiniteDiscoverySongs = infiniteDiscoverySongs + uniqueNew
                }
            } catch (_: Exception) {
                hasMoreDiscovery = false
            }
            isLoadingMoreDiscovery = false
        }
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
                        Toast.makeText(context, context.getString(R.string.toast_timer_track_end), Toast.LENGTH_SHORT).show()
                    } else if (repeatMode == Player.REPEAT_MODE_ONE) {
                        mediaController?.seekTo(0)
                        mediaController?.play()
                    } else if (playbackQueueManager.hasUpcoming()) {
                        playNextInQueue()
                    } else if (isInfinitePlayback && !isLoadingInfinite) {
                        coroutineScope.launch {
                            isLoadingInfinite = true
                            try {
                                val base = activeSong ?: currentSong
                                if (base != null) {
                                    val liveRelated = withContext(Dispatchers.IO) { onGetRelatedSongs?.invoke(base) ?: emptyList() }
                                    val candidatePool = (liveRelated + homeSongs + searchResults + libraryManager.getFavorites()).distinctBy { it.id }
                                    val newSongs = LocalHeuristicRanker.recommendRelatedSongs(
                                        baseSong = base,
                                        candidatePool = candidatePool,
                                        targetCount = infiniteBatchSize,
                                        historySongs = libraryManager.getRecentlyPlayed()
                                    )
                                    val filtered = libraryManager.filterBlocked(newSongs).filterNot { it.id == base.id }
                                    if (filtered.isNotEmpty()) {
                                        playbackQueueManager.setAutoQueueSongs(filtered, QueueSource.AUTOPLAY)
                                        playNextInQueue()
                                        Toast.makeText(context, context.getString(R.string.toast_infinite_added, filtered.size), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, context.getString(R.string.toast_timer_track_end), Toast.LENGTH_SHORT).show()
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
                        stringResource(R.string.nav_home) to Icons.Default.Home,
                        stringResource(R.string.nav_search) to Icons.Default.Search,
                        stringResource(R.string.nav_library) to Icons.Default.LibraryMusic,
                        stringResource(R.string.nav_settings) to Icons.Default.Settings
                    )
                    items.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = selectedNavTab == index,
                            onClick = {
                                if (selectedNavTab == 0 && index == 0) {
                                    coroutineScope.launch {
                                        homeLazyListState.animateScrollToItem(0)
                                    }
                                    isRefreshingHome = true
                                    refreshHomeTrigger++
                                } else if (selectedNavTab == 1 && index == 1) {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                } else {
                                    selectedNavTab = index
                                }
                            },
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(homePullRefreshConnection)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))

                                // Top Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
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
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.tertiary
                                                        )
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = "LiteMusic",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.5).sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (isLoadingHome && homeSongs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(stringResource(R.string.player_loading_ytm_data), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                } else {
                                    val combinedDiscovery = remember(homeSections.discovery, infiniteDiscoverySongs, homeSections.allSectionIds) {
                                        (homeSections.discovery + infiniteDiscoverySongs.filterNot { it.id in homeSections.allSectionIds }).distinctBy { it.id }
                                    }
                                    LazyColumn(
                                        state = homeLazyListState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(bottom = 140.dp)
                                    ) {
                                        // Section 0: YouTube Music Mood Filter Pills
                                        item {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                                            ) {
                                                items(moodFilters) { mood ->
                                                    val isSelected = selectedMoodFilter == mood
                                                    Surface(
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                                        modifier = Modifier.clickable { selectedMoodFilter = mood }
                                                    ) {
                                                        Text(
                                                            text = stringResource(mood.titleRes),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Section 0.5: Mood Switch Loading Indicator
                                        if (isLoadingMood && currentMoodPool.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 48.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(28.dp),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            strokeWidth = 2.5.dp
                                                        )
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        Text(
                                                            text = stringResource(R.string.player_loading_ytm_data),
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Section 1: Quick Picks Grid
                                        if (homeSections.quickPicks.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = stringResource(R.string.home_section_quick_grid),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    homeSections.quickPicks.chunked(2).forEach { pair ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
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
                                        }

                                        // Section 2: Recommended for You (Visual Carousel)
                                        if (homeSections.recommended.isNotEmpty()) {
                                            item {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(R.string.home_section_for_you),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    itemsIndexed(homeSections.recommended, key = { index, song -> "rec_${song.id}_$index" }) { _, song ->
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

                                        // Section 3: Trending Chart (Ranked List)
                                        if (homeSections.trending.isNotEmpty()) {
                                            item {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(R.string.home_section_trending),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }

                                            itemsIndexed(homeSections.trending, key = { index, s -> "trend_${s.id}_$index" }) { index, song ->
                                                val isSongActive = activeSong?.id == song.id
                                                SongListCard(
                                                    song = song,
                                                    rank = index + 1,
                                                    onClick = { playTrack(song) },
                                                    isActive = isSongActive,
                                                    isLoading = isSongActive && isAudioLoading,
                                                    isFavorite = libraryManager.isFavorite(song.id),
                                                    onToggleFavorite = {
                                                        val isFav = libraryManager.toggleFavorite(song)
                                                        Toast.makeText(context, if (isFav) context.getString(R.string.toast_added_to_favorites) else context.getString(R.string.toast_removed_from_favorites), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onAddToQueue = {
                                                        playbackQueueManager.addToManualQueue(song)
                                                        Toast.makeText(context, context.getString(R.string.toast_added_to_queue, song.title), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onBlockSong = {
                                                        libraryManager.blockSong(song)
                                                        homeSongs = libraryManager.filterBlocked(homeSongs)
                                                        Toast.makeText(context, context.getString(R.string.toast_blocked_song, song.title), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onBlockArtist = {
                                                        libraryManager.blockArtist(song.artist)
                                                        homeSongs = libraryManager.filterBlocked(homeSongs)
                                                        Toast.makeText(context, context.getString(R.string.toast_blocked_artist, song.artist), Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }

                                        // Section 4: Infinite Discovery Feed
                                        if (combinedDiscovery.isNotEmpty() || (isLoadingMoreDiscovery && currentMoodPool.isNotEmpty())) {
                                            item {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = stringResource(R.string.home_section_infinite_feed),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }

                                            itemsIndexed(combinedDiscovery, key = { index, song -> "disc_${song.id}_$index" }) { _, song ->
                                                val isSongActive = activeSong?.id == song.id
                                                SongListCard(
                                                    song = song,
                                                    rank = null,
                                                    onClick = { playTrack(song) },
                                                    isActive = isSongActive,
                                                    isLoading = isSongActive && isAudioLoading,
                                                    isFavorite = libraryManager.isFavorite(song.id),
                                                    onToggleFavorite = {
                                                        val isFav = libraryManager.toggleFavorite(song)
                                                        Toast.makeText(context, if (isFav) context.getString(R.string.toast_added_to_favorites) else context.getString(R.string.toast_removed_from_favorites), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onAddToQueue = {
                                                        playbackQueueManager.addToManualQueue(song)
                                                        Toast.makeText(context, context.getString(R.string.toast_added_to_queue, song.title), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onBlockSong = {
                                                        libraryManager.blockSong(song)
                                                        homeSongs = libraryManager.filterBlocked(homeSongs)
                                                        Toast.makeText(context, context.getString(R.string.toast_blocked_song, song.title), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onBlockArtist = {
                                                        libraryManager.blockArtist(song.artist)
                                                        homeSongs = libraryManager.filterBlocked(homeSongs)
                                                        Toast.makeText(context, context.getString(R.string.toast_blocked_artist, song.artist), Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }

                                        if (isLoadingMoreDiscovery) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.home_section_loading_more),
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                }
                            }

                            // Floating circular pull-to-refresh popup badge
                            val isIndicatorVisible = homePullOffset > 8f || isRefreshingHome
                            val indicatorOffsetDp by animateFloatAsState(
                                targetValue = when {
                                    isRefreshingHome -> 60f
                                    homePullOffset > 0f -> (10f + (homePullOffset * 0.42f)).coerceAtMost(75f)
                                    else -> 0f
                                },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "pull_indicator_offset"
                            )

                            AnimatedVisibility(
                                visible = isIndicatorVisible,
                                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.5f),
                                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.5f),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = indicatorOffsetDp.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isRefreshingHome) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            val progressFraction = (homePullOffset / homePullThreshold).coerceIn(0f, 1f)
                                            val rotation = progressFraction * 180f
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (homePullOffset >= homePullThreshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .graphicsLayer { rotationZ = rotation }
                                            )
                                        }
                                    }
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
                                playbackQueueManager.addToManualQueue(song)
                                Toast.makeText(context, context.getString(R.string.toast_added_to_queue, song.title), Toast.LENGTH_SHORT).show()
                            },
                            onToggleFavorite = { song ->
                                val isFav = libraryManager.toggleFavorite(song)
                                Toast.makeText(context, if (isFav) context.getString(R.string.toast_added_to_favorites) else context.getString(R.string.toast_removed_from_favorites), Toast.LENGTH_SHORT).show()
                            },
                            isFavorite = { id -> libraryManager.isFavorite(id) },
                            onBlockSong = { song ->
                                libraryManager.blockSong(song)
                                searchResults = libraryManager.filterBlocked(searchResults)
                                homeSongs = libraryManager.filterBlocked(homeSongs)
                                Toast.makeText(context, context.getString(R.string.toast_blocked_song, song.title), Toast.LENGTH_SHORT).show()
                            },
                            onBlockArtist = { artist ->
                                libraryManager.blockArtist(artist)
                                searchResults = libraryManager.filterBlocked(searchResults)
                                homeSongs = libraryManager.filterBlocked(homeSongs)
                                Toast.makeText(context, context.getString(R.string.toast_blocked_artist, artist), Toast.LENGTH_SHORT).show()
                            },
                            onAiSearchClick = {
                                showAiDialog = true
                                coroutineScope.launch {
                                    aiGenerator.generatePlaylist(
                                        userPrompt = searchQuery.ifEmpty { context.getString(R.string.home_curated_desc) },
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
                                    playbackQueueManager.clearAllQueue()
                                    playbackQueueManager.addMultipleToManualQueue(songs.drop(1), QueueSource.PLAYLIST)
                                }
                            },
                            onAddToQueue = { song ->
                                playbackQueueManager.addToManualQueue(song)
                                Toast.makeText(context, context.getString(R.string.toast_added_to_queue, song.title), Toast.LENGTH_SHORT).show()
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
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
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
                isLyricsLoading = isLyricsLoading,
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
                    Toast.makeText(context, context.getString(R.string.toast_timer_set, mins), Toast.LENGTH_SHORT).show()
                },
                onSetEndOfTrack = {
                    isStopAtEndOfTrack = true
                    sleepTimerSeconds = null
                    Toast.makeText(context, context.getString(R.string.toast_timer_track_end), Toast.LENGTH_SHORT).show()
                },
                onCancelTimer = {
                    sleepTimerSeconds = null
                    isStopAtEndOfTrack = false
                    Toast.makeText(context, context.getString(R.string.toast_timer_cancelled), Toast.LENGTH_SHORT).show()
                },
                isVideoMode = isVideoMode,
                onToggleVideoMode = { enableVideo ->
                    val cur = activeSong ?: currentSong
                    val canVideo = cur?.source == AudioSource.YOUTUBE_MUSIC || cur?.source == AudioSource.YOUTUBE_VIDEO
                    if (enableVideo && !canVideo) {
                        Toast.makeText(context, context.getString(R.string.player_video_yt_only), Toast.LENGTH_SHORT).show()
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
                    val entry = queueState.manualQueue.find { it.song.id == qSong.id } ?: queueState.autoQueue.find { it.song.id == qSong.id }
                    if (entry != null) {
                        playbackQueueManager.removeEntry(entry.queueEntryId)
                    }
                    playTrack(qSong, isVideoMode)
                },
                onRemoveFromQueue = { idx ->
                    if (idx in songQueue.indices) {
                        val entry = queueState.manualQueue.getOrNull(idx) ?: queueState.autoQueue.getOrNull(idx - queueState.manualQueue.size)
                        entry?.let { playbackQueueManager.removeEntry(it.queueEntryId) }
                    }
                },
                onClearQueue = {
                    playbackQueueManager.clearAllQueue()
                    Toast.makeText(context, context.getString(R.string.toast_queue_cleared), Toast.LENGTH_SHORT).show()
                },
                onRefreshQueue = {
                    val base = activeSong ?: currentSong
                    if (base != null) {
                        coroutineScope.launch {
                            val liveRelated = withContext(Dispatchers.IO) { onGetRelatedSongs?.invoke(base) ?: emptyList() }
                            val candidatePool = (liveRelated + homeSongs + searchResults + libraryManager.getFavorites() + libraryManager.getRecentlyPlayed()).distinctBy { it.id }
                            val newSongs = LocalHeuristicRanker.recommendRelatedSongs(
                                baseSong = base,
                                candidatePool = candidatePool,
                                targetCount = 25,
                                historySongs = libraryManager.getRecentlyPlayed()
                            )
                            val filtered = libraryManager.filterBlocked(newSongs).filterNot { it.id == base.id }
                            if (filtered.isNotEmpty()) {
                                playbackQueueManager.setAutoQueueSongs(filtered, QueueSource.RADIO)
                                Toast.makeText(context, context.getString(R.string.toast_queue_refreshed, filtered.size), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_home_refreshing), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onRecommendQueueAi = {
                    val cur = activeSong ?: currentSong
                    val prompt = if (cur != null) context.getString(R.string.player_ai_playlist_prompt, cur.title, cur.artist) else context.getString(R.string.home_section_curated_playlists)
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
                        Player.REPEAT_MODE_ALL -> context.getString(R.string.player_repeat_all)
                        Player.REPEAT_MODE_ONE -> context.getString(R.string.player_repeat_one)
                        else -> context.getString(R.string.player_repeat_off)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                isShuffleEnabled = isShuffleEnabled,
                onToggleShuffle = {
                    val next = !isShuffleEnabled
                    isShuffleEnabled = next
                    mediaController?.shuffleModeEnabled = next
                    playbackQueueManager.setShuffled(next)
                    if (next && playbackQueueManager.hasUpcoming()) {
                        Toast.makeText(context, context.getString(R.string.toast_queue_shuffled), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, if (next) context.getString(R.string.player_shuffle_on) else context.getString(R.string.player_shuffle_off), Toast.LENGTH_SHORT).show()
                    }
                },
                availableVideoQualities = availableVideoQualities,
                selectedVideoQuality = selectedVideoQuality,
                onChangeVideoQuality = onChangeVideoQuality,
                onOpenQueue = { isQueueSheetOpen = true },
                queueUpcomingCount = queueState.totalUpcomingCount
            )
        }

        // Dedicated Professional Queue Sheet (P0 Redesign)
        if (isQueueSheetOpen) {
            QueueSheet(
                isOpen = isQueueSheetOpen,
                onClose = { isQueueSheetOpen = false },
                currentSong = activeSong ?: currentSong,
                isPlaying = isPlaying,
                onPlayPause = {
                    mediaController?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                },
                onSkipNext = { playNextInQueue() },
                onPlaySong = { song ->
                    playTrack(song, isVideoMode)
                },
                onRefreshAutoQueue = {
                    val base = activeSong ?: currentSong
                    if (base != null) {
                        coroutineScope.launch {
                            val liveRelated = withContext(Dispatchers.IO) { onGetRelatedSongs?.invoke(base) ?: emptyList() }
                            val candidatePool = (liveRelated + homeSongs + searchResults + libraryManager.getFavorites() + libraryManager.getRecentlyPlayed()).distinctBy { it.id }
                            val newSongs = LocalHeuristicRanker.recommendRelatedSongs(
                                baseSong = base,
                                candidatePool = candidatePool,
                                targetCount = 25,
                                historySongs = libraryManager.getRecentlyPlayed()
                            )
                            val filtered = libraryManager.filterBlocked(newSongs).filterNot { it.id == base.id }
                            if (filtered.isNotEmpty()) {
                                playbackQueueManager.setAutoQueueSongs(filtered, QueueSource.RADIO)
                                Toast.makeText(context, context.getString(R.string.toast_queue_refreshed, filtered.size), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_home_refreshing), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onOpenFullPlayer = {
                    isQueueSheetOpen = false
                    isPlayerSheetOpen = true
                }
            )
        }

        BackHandler(enabled = isQueueSheetOpen) {
            isQueueSheetOpen = false
        }

        if (showHomeSearchDialog) {
            HomeSearchDialog(
                onSearchSubmit = { query ->
                    searchQuery = query
                    selectedNavTab = 1
                },
                onAiSearch = { prompt ->
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
                onDismiss = { showHomeSearchDialog = false }
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
                        playbackQueueManager.clearAllQueue()
                        playbackQueueManager.addMultipleToManualQueue(tracks.drop(1), QueueSource.PLAYLIST)
                        playTrack(first, isVideoMode)
                    }
                },
                onAddToQueue = { tracks ->
                    playbackQueueManager.addMultipleToManualQueue(tracks, QueueSource.MANUAL)
                    Toast.makeText(context, context.getString(R.string.toast_added_multiple_to_queue, tracks.size), Toast.LENGTH_SHORT).show()
                },
                onPlaySingleTrack = { clickedSong, trackList ->
                    playbackQueueManager.clearAllQueue()
                    playbackQueueManager.addMultipleToManualQueue(trackList.filterNot { it.id == clickedSong.id }, QueueSource.PLAYLIST)
                    playTrack(clickedSong, isVideoMode)
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
                            playlistDescription = context.getString(R.string.home_offline_algorithm_desc),
                            tracks = localTracks,
                            providerUsed = context.getString(R.string.home_offline_algorithm)
                        )
                    }
                }
            )
        }
    }
}

fun getEffectiveArtworkUrl(url: String, sourceId: String = "", songId: String = ""): String {
    if (url.isNotBlank() && !url.contains("invidious") && !url.contains("f5.si") && !url.contains("vern.cc") && !url.contains("drgns.space")) {
        return url
    }
    val cleanVid = sourceId.removePrefix("ytv_").ifEmpty { songId.removePrefix("ytv_") }
    return if (cleanVid.length in 8..15) {
        "https://i.ytimg.com/vi/$cleanVid/hqdefault.jpg"
    } else url
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
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else if (isGlass) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = if (isActive) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                 else if (isGlass) BorderStroke(1.dp, GlassBorderDark)
                 else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val effectiveArt = getEffectiveArtworkUrl(song.thumbnailUrl, song.sourceId, song.id)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                        .crossfade(true)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .fallback(R.drawable.ic_music_note)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isActive) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
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
                    text = if (isActive && isLoading) stringResource(R.string.player_loading_stream) else song.artist,
                    fontSize = 11.sp,
                    color = if (isActive && isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (isActive) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    else if (isGlass) Modifier.border(1.dp, GlassBorderDark, RoundedCornerShape(12.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val effectiveArt = getEffectiveArtworkUrl(song.thumbnailUrl, song.sourceId, song.id)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                    .crossfade(true)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .fallback(R.drawable.ic_music_note)
                    .build(),
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isActive && isLoading) stringResource(R.string.player_loading_stream) else song.artist,
            fontSize = 11.sp,
            color = if (isActive && isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    onBlockArtist: (() -> Unit)? = null,
    rank: Int? = null
) {
    val isGlass = LocalThemeStyle.current == AppThemeStyle.LIQUID_GLASS
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else if (isGlass) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
            }
        ),
        border = if (isActive) {
            BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        } else if (isGlass) {
            BorderStroke(1.dp, GlassBorderDark)
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Text(
                    text = if (rank < 10) "0$rank" else "$rank",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val effectiveArt = getEffectiveArtworkUrl(song.thumbnailUrl, song.sourceId, song.id)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                        .crossfade(true)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .fallback(R.drawable.ic_music_note)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isActive) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isActive && isLoading) stringResource(R.string.player_loading_stream) else song.artist,
                    fontSize = 11.5.sp,
                    color = if (isActive && isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Options & Quick actions in dropdown menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_options),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_queue), fontSize = 12.5.sp) },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            }
                        )
                    }
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) stringResource(R.string.action_remove_from_favorites) else stringResource(R.string.action_add_to_favorites), fontSize = 12.5.sp) },
                            leadingIcon = { Icon(if (isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                    }
                    if (onBlockSong != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.block_song), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onBlockSong()
                            }
                        )
                    }
                    if (onBlockArtist != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.block_artist) + ": ${song.artist}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error) },
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
            val effectiveArt = getEffectiveArtworkUrl(artworkUrl)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                    .crossfade(true)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .fallback(R.drawable.ic_music_note)
                    .build(),
                contentDescription = title,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (isResolving) stringResource(R.string.player_loading_stream) else artist,
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
    isLyricsLoading: Boolean = false,
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
    onChangeVideoQuality: (VideoQuality) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    queueUpcomingCount: Int = 0
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

    // Hierarchical back handling: if in Lyrics or Queue, back returns to Now Playing; if in Now Playing, back minimizes to MiniPlayer
    BackHandler(enabled = currentTab != FullPlayerTab.NOW_PLAYING) {
        currentTab = FullPlayerTab.NOW_PLAYING
    }

    val playerSheetNestedScrollConnection = remember(currentTab) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 10f && currentTab != FullPlayerTab.NOW_PLAYING) {
                    currentTab = FullPlayerTab.NOW_PLAYING
                    return available
                }
                return Offset.Zero
            }
        }
    }

    var accumulatedPlayerDragY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, if (currentTab == FullPlayerTab.NOW_PLAYING) animatedDragOffsetY.roundToInt() else 0) }
            .then(
                if (currentTab == FullPlayerTab.NOW_PLAYING) {
                    Modifier.pointerInput(lyrics.size, isLyricsLoading) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                accumulatedPlayerDragY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedPlayerDragY += dragAmount
                                if (accumulatedPlayerDragY > 0) {
                                    // Dragging DOWN -> smoothly translate player sheet following finger
                                    dragOffsetY = accumulatedPlayerDragY
                                } else {
                                    // Dragging UP -> if passed threshold (-45f), switch to lyrics or queue
                                    dragOffsetY = 0f
                                    if (accumulatedPlayerDragY < -45f) {
                                        currentTab = if (lyrics.isNotEmpty() || isLyricsLoading) FullPlayerTab.LYRICS else FullPlayerTab.QUEUE
                                        accumulatedPlayerDragY = 0f
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragOffsetY > 110f) {
                                    onClose()
                                }
                                dragOffsetY = 0f
                                accumulatedPlayerDragY = 0f
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                                accumulatedPlayerDragY = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }
            ),
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
                    .fillMaxWidth()
                    .clickable(enabled = currentTab != FullPlayerTab.NOW_PLAYING) {
                        currentTab = FullPlayerTab.NOW_PLAYING
                    }
                    .pointerInput(currentTab) {
                        if (currentTab != FullPlayerTab.NOW_PLAYING) {
                            detectVerticalDragGestures { change, dragAmount ->
                                if (dragAmount > 10f) {
                                    change.consume()
                                    currentTab = FullPlayerTab.NOW_PLAYING
                                }
                            }
                        }
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(currentTab) {
                        if (currentTab != FullPlayerTab.NOW_PLAYING) {
                            detectVerticalDragGestures { change, dragAmount ->
                                if (dragAmount > 12f) {
                                    change.consume()
                                    currentTab = FullPlayerTab.NOW_PLAYING
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (currentTab != FullPlayerTab.NOW_PLAYING) {
                        currentTab = FullPlayerTab.NOW_PLAYING
                    } else {
                        onClose()
                    }
                }) {
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
                                        text = stringResource(R.string.home_section_music_only),
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
                                    Icon(Icons.Default.HighQuality, contentDescription = stringResource(R.string.player_video_quality), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
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
                            FullPlayerTab.NOW_PLAYING -> stringResource(R.string.player_now_playing)
                            FullPlayerTab.LYRICS -> stringResource(R.string.player_lyrics)
                            FullPlayerTab.QUEUE -> stringResource(R.string.player_queue)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Queue Button with Badge (opens dedicated Queue Sheet)
                    IconButton(onClick = onOpenQueue) {
                        BadgedBox(
                            badge = {
                                val totalCount = if (queueUpcomingCount > 0) queueUpcomingCount else songQueue.size
                                if (totalCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("$totalCount", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = stringResource(R.string.player_queue_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Lyrics Button (always accessible like YouTube Music / Spotify)
                    IconButton(onClick = {
                        currentTab = if (currentTab == FullPlayerTab.LYRICS) FullPlayerTab.NOW_PLAYING else FullPlayerTab.LYRICS
                    }) {
                        if (isLyricsLoading && lyrics.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (currentTab == FullPlayerTab.LYRICS) Icons.Default.MusicNote else Icons.Default.Lyrics,
                                contentDescription = "Toggle Lyrics",
                                tint = if (currentTab == FullPlayerTab.LYRICS) MaterialTheme.colorScheme.primary else (if (lyrics.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
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
                                .nestedScroll(playerSheetNestedScrollConnection)
                                .padding(vertical = 4.dp)
                        ) {
                            // Section 1: Đang phát hiện tại
                            Text(
                                text = stringResource(R.string.player_now_playing),
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
                                    val effectiveArt = getEffectiveArtworkUrl(artworkUrl)
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                                            .crossfade(true)
                                            .placeholder(R.drawable.ic_music_note)
                                            .error(R.drawable.ic_music_note)
                                            .fallback(R.drawable.ic_music_note)
                                            .build(),
                                        contentDescription = title,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
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
                                            text = stringResource(R.string.player_now_playing),
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
                                    text = stringResource(R.string.player_queue_next_header, songQueue.size),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 1.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Open Dedicated Queue Sheet button
                                    FilledTonalButton(
                                        onClick = onOpenQueue,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(stringResource(R.string.queue_sheet_title), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Refresh recommendations button
                                    FilledTonalButton(
                                        onClick = onRefreshQueue,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(stringResource(R.string.action_refresh), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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
                                        Text(stringResource(R.string.player_queue_empty), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            stringResource(R.string.player_queue_empty_desc),
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
                                                val effectiveArt = getEffectiveArtworkUrl(qSong.thumbnailUrl, qSong.sourceId, qSong.id)
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                                                        .crossfade(true)
                                                        .placeholder(R.drawable.ic_music_note)
                                                        .error(R.drawable.ic_music_note)
                                                        .fallback(R.drawable.ic_music_note)
                                                        .build(),
                                                    contentDescription = qSong.title,
                                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(qSong.title, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(qSong.artist, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                IconButton(
                                                    onClick = { onRemoveFromQueue(idx) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FullPlayerTab.LYRICS -> {
                        if (isLyricsLoading && lyrics.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                                    Text(stringResource(R.string.player_loading_lyrics), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else if (lyrics.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    Text(stringResource(R.string.player_no_lyrics), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        stringResource(R.string.player_no_lyrics_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(playerSheetNestedScrollConnection),
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
                                    val effectiveArt = getEffectiveArtworkUrl(artworkUrl)
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(effectiveArt.ifEmpty { R.drawable.ic_music_note })
                                            .crossfade(true)
                                            .placeholder(R.drawable.ic_music_note)
                                            .error(R.drawable.ic_music_note)
                                            .fallback(R.drawable.ic_music_note)
                                            .build(),
                                        contentDescription = title,
                                        modifier = Modifier
                                            .size(260.dp)
                                            .clip(RoundedCornerShape(28.dp)),
                                        contentScale = ContentScale.Crop
                                    )
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
                                                text = stringResource(R.string.player_loading_stream),
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
                                text = if (sleepTimerSeconds != null && sleepTimerSeconds > 0) formatTime(sleepTimerSeconds * 1000) else stringResource(R.string.player_timer_title),
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

                // Real-Time Lyrics Peek Banner (YouTube Music / Spotify Style)
                if (lyrics.isNotEmpty()) {
                    val currentLine = lyrics.getOrNull(activeLyricIndex)?.text?.ifBlank { null }
                    if (currentLine != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentTab = FullPlayerTab.LYRICS }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lyrics,
                                    contentDescription = stringResource(R.string.player_lyrics_title),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentLine,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.player_video_fullscreen),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
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
                            text = stringResource(R.string.player_seek_to, formatTime(displayedTimeMs)),
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
                        if (currentTab == FullPlayerTab.LYRICS) {
                            currentTab = FullPlayerTab.NOW_PLAYING
                        } else if (lyrics.isNotEmpty()) {
                            currentTab = FullPlayerTab.LYRICS
                        } else {
                            onOpenQueue()
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (currentTab) {
                                FullPlayerTab.NOW_PLAYING -> if (lyrics.isNotEmpty()) stringResource(R.string.player_tap_to_view_lyrics_queue) else stringResource(R.string.player_tap_to_open_queue_simple)
                                FullPlayerTab.LYRICS -> stringResource(R.string.player_tap_to_return_playing)
                                FullPlayerTab.QUEUE -> stringResource(R.string.player_tap_to_return_playing)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val totalQueueCount = if (queueUpcomingCount > 0) queueUpcomingCount else songQueue.size
                        Text(
                            text = when (currentTab) {
                                FullPlayerTab.NOW_PLAYING -> if (lyrics.isNotEmpty()) stringResource(R.string.player_lyrics_count_seek, lyrics.size, skipDurationSeconds) else stringResource(R.string.player_double_tap_seek_hint, skipDurationSeconds)
                                FullPlayerTab.LYRICS -> stringResource(R.string.player_lyrics_realtime)
                                FullPlayerTab.QUEUE -> if (totalQueueCount > 0) stringResource(R.string.player_queue_count_desc, totalQueueCount) else stringResource(R.string.player_queue_empty)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (lyrics.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    currentTab = if (currentTab == FullPlayerTab.LYRICS) FullPlayerTab.NOW_PLAYING else FullPlayerTab.LYRICS
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Lyrics, contentDescription = null, tint = if (currentTab == FullPlayerTab.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            }
                        }
                        IconButton(
                            onClick = onOpenQueue,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = stringResource(R.string.player_queue_title), tint = MaterialTheme.colorScheme.primary)
                        }
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isHighVolume) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = if (isHighVolume) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.player_volume_title, currentVolume), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.player_volume_desc),
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
                    Text(stringResource(R.string.player_volume_standard), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.player_volume_max_dsp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }

                // Quick preset buttons: 25%, 50%, 75%, 100%, 125%, 150%, 200%, 250%, 300%
                Text(stringResource(R.string.player_volume_quick), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                text = stringResource(R.string.player_volume_warning),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.player_speed_title, String.format("%.2f", currentSpeed)), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.player_speed_desc),
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

                Text(stringResource(R.string.player_speed_presets), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (remainingSeconds != null && remainingSeconds > 0)
                        stringResource(R.string.player_timer_title) + " (${formatTime(remainingSeconds * 1000)})"
                    else stringResource(R.string.player_timer_title),
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
                                Text(stringResource(R.string.player_timer_running), fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(stringResource(R.string.player_timer_remaining, formatTime(remainingSeconds * 1000)), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            FilledTonalButton(onClick = { onCancelTimer(); onDismiss() }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                }

                Text(stringResource(R.string.player_volume_quick), fontWeight = FontWeight.Bold, fontSize = 12.sp)

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
                            Text(stringResource(R.string.player_timer_minutes, mins), fontSize = 11.5.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onSetEndOfTrack(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.player_timer_stop_after_track), fontSize = 12.sp)
                }

                HorizontalDivider()

                Text(stringResource(R.string.player_timer_custom, customMinutes.toInt()), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = customMinutes,
                    onValueChange = { customMinutes = it },
                    valueRange = 1f..120f
                )
                Button(
                    onClick = { onSetTimerMinutes(customMinutes.toInt()); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.player_timer_start_custom, customMinutes.toInt()))
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.player_video_quality), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.player_video_quality_desc),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                if (availableQualities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_video_no_alt_qualities),
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

@Composable
fun HomeSearchDialog(
    onSearchSubmit: (String) -> Unit,
    onAiSearch: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onAiSearch != null) {
                    FilledTonalButton(
                        onClick = {
                            val prompt = query.trim().ifEmpty { "Nhạc hay tuyển chọn" }
                            onAiSearch(prompt)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("✨ AI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            onSearchSubmit(query.trim())
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.quick_search), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.search_cancel))
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.quick_search), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                onSearchSubmit(query.trim())
                                onDismiss()
                            }
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    )
}


