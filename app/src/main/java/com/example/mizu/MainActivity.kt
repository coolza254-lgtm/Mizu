package com.example.mizu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mizu.ui.MizuRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug-only extras used by the CI screenshot job; ignored in release builds.
        val startScreen = if (BuildConfig.DEBUG) intent.getStringExtra("mizu_screen") else null
        val demo = BuildConfig.DEBUG && intent.getBooleanExtra("mizu_demo", false)
        setContent { MizuRoot(startScreen = startScreen, demo = demo) }
    }
}
