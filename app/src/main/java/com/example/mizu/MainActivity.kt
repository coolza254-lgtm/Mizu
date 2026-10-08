package com.example.mizu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.Feasibility
import com.example.mizu.core.ReminderContent
import com.example.mizu.reminder.NotificationHelper
import com.example.mizu.ui.MizuRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug-only extras used by the CI screenshot job; ignored in release builds.
        val startScreen = if (BuildConfig.DEBUG) intent.getStringExtra("mizu_screen") else null
        val demo = BuildConfig.DEBUG && intent.getBooleanExtra("mizu_demo", false)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("mizu_notif", false)) {
            // Sample reminder so CI can screenshot the notification shade.
            NotificationHelper.show(
                this, AppLanguage.TH,
                ReminderContent(remainingMl = 880, suggestedMl = 250, feasibility = Feasibility.REACHABLE, urgent = false, deficitMl = 240),
                goalMl = 2100,
            )
        }
        setContent { MizuRoot(startScreen = startScreen, demo = demo) }
    }
}
