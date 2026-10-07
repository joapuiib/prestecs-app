package com.fpmislata.prestecs.ui.scanner

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Beep and vibration after a scan, so the teacher can keep their eyes on the
 * laptops instead of the screen.
 */
class ScanFeedback(private val haptics: HapticFeedback, private val tones: ToneGenerator?) {
    fun accepted() {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        tones?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun rejected() {
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        tones?.startTone(ToneGenerator.TONE_SUP_ERROR, 300)
    }
}

@Composable
fun rememberScanFeedback(): ScanFeedback {
    val haptics = LocalHapticFeedback.current
    val tones = remember {
        // Fails on some devices when audio resources run out: vibrate only.
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: RuntimeException) {
            Log.w("ScanFeedback", "No tone generator", e)
            null
        }
    }
    DisposableEffect(tones) {
        onDispose { tones?.release() }
    }
    return remember(haptics, tones) { ScanFeedback(haptics, tones) }
}
