package com.example.mizu.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Small, distinct vibrations for touches. Uses the vibrator directly so the feel is consistent,
 * and does nothing when the user turns haptics off in Settings.
 */
class Haptics(context: Context) {
    var enabled: Boolean = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Lightest: selection changes, keypad digits, chart scrubbing. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK, 8)

    /** A normal button press. */
    fun click() = predefined(VibrationEffect.EFFECT_CLICK, 14)

    /** Destructive or important: delete, refill. */
    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 28)

    /** Drink logged, goal reached: a soft double pulse. */
    fun success() = play {
        VibrationEffect.createWaveform(longArrayOf(0, 22, 80, 34), intArrayOf(0, 140, 0, 255), -1)
    }

    private fun predefined(effect: Int, fallbackMs: Long) = play {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(effect)
        } else {
            VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private inline fun play(effect: () -> VibrationEffect) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(effect())
        } catch (_: Exception) {
            // Some devices refuse custom waveforms; vibration is decoration only.
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }
