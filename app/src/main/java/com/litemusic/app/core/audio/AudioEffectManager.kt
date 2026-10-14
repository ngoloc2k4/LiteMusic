package com.litemusic.app.core.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.roundToInt

/**
 * Manages DSP Audio Effects for Volume Normalization (Loudness Normalizer)
 * Compatible with Android 7.0+ (API 24+) using hardware-accelerated LoudnessEnhancer.
 */
class AudioEffectManager {

    private val tag = "AudioEffectManager"
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = AudioEffect.ERROR_BAD_VALUE

    // Standard Target Loudness: -14 LUFS (EBU R128 / Spotify / YouTube standard)
    private val targetLufs = -14.0f
    private val maxGainDb = 6.0f
    private val minGainDb = -10.0f

    var isNormalizationEnabled: Boolean = true
        set(value) {
            field = value
            applyGain()
        }

    private var currentTrackLoudnessDb: Float? = null

    fun attachAudioSession(sessionId: Int) {
        if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId == 0) return
        if (currentSessionId == sessionId && loudnessEnhancer != null) return

        release()
        try {
            currentSessionId = sessionId
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = isNormalizationEnabled
            }
            applyGain()
            Log.d(tag, "Attached LoudnessEnhancer to audioSessionId: $sessionId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize LoudnessEnhancer for session $sessionId", e)
            loudnessEnhancer = null
        }
    }

    /**
     * Set the current song's measured loudness (e.g. from YouTube metadata or ReplayGain tag)
     */
    fun onTrackChanged(loudnessDb: Float?) {
        currentTrackLoudnessDb = loudnessDb
        applyGain()
    }

    private fun applyGain() {
        val enhancer = loudnessEnhancer ?: return
        if (!isNormalizationEnabled) {
            try {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            } catch (e: Exception) {
                Log.w(tag, "Could not disable LoudnessEnhancer", e)
            }
            return
        }

        try {
            val gainDb = currentTrackLoudnessDb?.let { trackLoudness ->
                // Calculate compensation gain: Difference between target and current loudness
                val diff = targetLufs - trackLoudness
                diff.coerceIn(minGainDb, maxGainDb)
            } ?: 0.0f // Neutral when loudness metadata is absent

            // 1 dB = 100 mB (millibels)
            val gainMb = (gainDb * 100).roundToInt()
            enhancer.setTargetGain(gainMb)
            enhancer.enabled = true
            Log.d(tag, "Volume Normalizer active: trackLoudness=$currentTrackLoudnessDb, targetGain=${gainMb}mB")
        } catch (e: Exception) {
            Log.e(tag, "Error applying target gain to LoudnessEnhancer", e)
        }
    }

    fun release() {
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing LoudnessEnhancer", e)
        } finally {
            loudnessEnhancer = null
            currentSessionId = AudioEffect.ERROR_BAD_VALUE
        }
    }
}
