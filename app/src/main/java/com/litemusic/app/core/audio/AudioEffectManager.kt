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

    companion object {
        val instance = AudioEffectManager()
    }

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

    // Custom volume boost percent from 0 to 250 (100 = 100% normal)
    var volumePercent: Int = 100
        private set

    private var currentTrackLoudnessDb: Float? = null

    fun attachAudioSession(sessionId: Int) {
        if (sessionId == AudioEffect.ERROR_BAD_VALUE || sessionId == 0) return
        if (currentSessionId == sessionId && loudnessEnhancer != null) return

        release()
        try {
            currentSessionId = sessionId
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = true
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

    /**
     * Set custom volume from 0% to 250%.
     * 0%..100%: Adjusts player software volume (0.0f to 1.0f).
     * 101%..250%: Sets player software volume to 1.0f, and boosts hardware DSP gain via LoudnessEnhancer up to +12 dB.
     */
    fun setCustomVolume(percent: Int, setPlayerVolume: (Float) -> Unit) {
        val clamped = percent.coerceIn(0, 300)
        volumePercent = clamped

        if (clamped <= 100) {
            setPlayerVolume(clamped / 100f)
        } else {
            setPlayerVolume(1.0f)
        }
        applyGain()
    }

    private fun applyGain() {
        val enhancer = loudnessEnhancer ?: return

        var normalizerGainMb = 0
        if (isNormalizationEnabled) {
            val gainDb = currentTrackLoudnessDb?.let { trackLoudness ->
                val diff = targetLufs - trackLoudness
                diff.coerceIn(minGainDb, maxGainDb)
            } ?: 0.0f
            normalizerGainMb = (gainDb * 100).roundToInt()
        }

        var boostGainMb = 0
        if (volumePercent > 100) {
            // Scale excess volume (101..300%) to 0..1500 mB (+15 dB DSP Boost)
            val excessRatio = (volumePercent - 100) / 200f
            boostGainMb = (excessRatio * 1500f).roundToInt()
        }

        val totalGainMb = normalizerGainMb + boostGainMb

        try {
            if (totalGainMb != 0 || isNormalizationEnabled || volumePercent > 100) {
                enhancer.setTargetGain(totalGainMb)
                enhancer.enabled = true
            } else {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            }
            Log.d(tag, "Volume & Normalizer active: normalizer=${normalizerGainMb}mB, boost=${boostGainMb}mB, total=${totalGainMb}mB")
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
