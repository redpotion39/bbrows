package com.phlox.tvwebbrowser.player

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C

/**
 * Safely manages Android [LoudnessEnhancer] for [NativePlayer] to boost quiet dialogue on TV speakers.
 * All audio effect calls are guarded because some Android TV firmwares do not expose [LoudnessEnhancer].
 */
class PlayerAudioBoost {
    private var enhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var currentGainMb: Int = 0

    fun apply(audioSessionId: Int, gainMb: Int) {
        currentGainMb = gainMb.coerceAtLeast(0)
        if (currentGainMb <= 0 || audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            releaseEnhancer()
            currentSessionId = audioSessionId
            return
        }
        if (enhancer == null || currentSessionId != audioSessionId) {
            releaseEnhancer()
            currentSessionId = audioSessionId
            enhancer = try {
                LoudnessEnhancer(audioSessionId)
            } catch (_: Throwable) {
                null
            }
        }
        try {
            enhancer?.setTargetGain(currentGainMb)
            enhancer?.enabled = true
        } catch (_: Throwable) {
            releaseEnhancer()
        }
    }

    fun onAudioSessionChanged(audioSessionId: Int) {
        if (audioSessionId != currentSessionId) {
            apply(audioSessionId, currentGainMb)
        }
    }

    fun release() {
        releaseEnhancer()
        currentSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun releaseEnhancer() {
        try {
            enhancer?.enabled = false
        } catch (_: Throwable) {
        }
        try {
            enhancer?.release()
        } catch (_: Throwable) {
        }
        enhancer = null
    }
}
