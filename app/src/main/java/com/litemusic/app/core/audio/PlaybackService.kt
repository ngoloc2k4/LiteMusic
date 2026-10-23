package com.litemusic.app.core.audio

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val audioEffectManager = AudioEffectManager.instance

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val cacheDataSourceFactory = AudioCacheManager.buildCacheDataSourceFactory(this)
            val defaultMediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory)
            val mediaSourceFactory = object : androidx.media3.exoplayer.source.MediaSource.Factory {
                override fun setDrmSessionManagerProvider(drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider): androidx.media3.exoplayer.source.MediaSource.Factory {
                    defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                    return this
                }
                override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy): androidx.media3.exoplayer.source.MediaSource.Factory {
                    defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                    return this
                }
                override fun getSupportedTypes(): IntArray {
                    return defaultMediaSourceFactory.supportedTypes
                }
                override fun createMediaSource(mediaItem: MediaItem): androidx.media3.exoplayer.source.MediaSource {
                    val audioUrl = mediaItem.mediaMetadata.extras?.getString("audioUrl")
                    val mainSource = defaultMediaSourceFactory.createMediaSource(mediaItem)
                    val mainUri = mediaItem.requestMetadata.mediaUri?.toString() 
                        ?: mediaItem.localConfiguration?.uri?.toString()
                    if (!audioUrl.isNullOrEmpty() && audioUrl != mainUri) {
                        val audioMediaItem = MediaItem.Builder()
                            .setUri(audioUrl)
                            .build()
                        val audioSource = defaultMediaSourceFactory.createMediaSource(audioMediaItem)
                        return androidx.media3.exoplayer.source.MergingMediaSource(
                            /* adjustPeriodTimeOffsets = */ true,
                            /* clipDurations = */ true,
                            mainSource,
                            audioSource
                        )
                    }
                    return mainSource
                }
            }

            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 10_000,
                    /* maxBufferMs = */ 50_000,
                    /* bufferForPlaybackMs = */ 400,
                    /* bufferForPlaybackAfterRebufferMs = */ 800
                )
                .setBackBuffer(/* backBufferDurationMs = */ 60_000, /* retainBackBufferFromKeyframe = */ true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                .setHandleAudioBecomingNoisy(true) // Auto pause when unplugging headphones
                .setWakeMode(C.WAKE_MODE_NETWORK) // Keep CPU & Wifi active while streaming
                .build()

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        audioEffectManager.attachAudioSession(player.audioSessionId)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // If extra metadata has loudnessDb, pass it to AudioEffectManager
                    val extras = mediaItem?.mediaMetadata?.extras
                    val loudness = extras?.getFloat("loudnessDb")
                    audioEffectManager.onTrackChanged(loudness)
                }
            })

            val sessionCallback = object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val currentItem = player.currentMediaItem
                    return if (currentItem != null) {
                        com.google.common.util.concurrent.Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(
                                listOf(currentItem),
                                player.currentMediaItemIndex,
                                player.currentPosition
                            )
                        )
                    } else {
                        com.google.common.util.concurrent.Futures.immediateFailedFuture(
                            UnsupportedOperationException("No active track to resume")
                        )
                    }
                }
            }

            val openAppIntent = Intent(this, com.litemusic.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sessionActivityPendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            mediaSession = MediaSession.Builder(this, player)
                .setCallback(sessionCallback)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
        } catch (e: Throwable) {
            android.util.Log.e("PlaybackService", "Error initializing PlaybackService", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        audioEffectManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
