package com.example.mizu.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.mizu.R

/** Cute water sounds for logging and refilling. Silent when the user turns sounds off. */
class Sounds(context: Context) {
    var enabled: Boolean = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val drop = pool.load(context, R.raw.water_drop, 1)
    private val fill = pool.load(context, R.raw.water_fill, 1)

    /** A drink was logged. */
    fun drop() = play(drop)

    /** The bottle was refilled. */
    fun fill() = play(fill)

    private fun play(id: Int) {
        if (enabled) pool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }
}

val LocalSounds = staticCompositionLocalOf<Sounds> { error("Sounds not provided") }
