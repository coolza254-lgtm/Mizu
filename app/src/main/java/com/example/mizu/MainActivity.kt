package com.example.mizu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.Feasibility
import com.example.mizu.core.ReminderContent
import com.example.mizu.quick.QuickActions
import com.example.mizu.reminder.NotificationHelper
import com.example.mizu.ui.MizuRoot
import com.example.mizu.ui.QuickRequest

class MainActivity : ComponentActivity() {
    /** Latest request from a Quick panel tile or launcher shortcut (also when the app is already open). */
    private val quickRequest = mutableStateOf<QuickRequest?>(null)

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
        if (BuildConfig.DEBUG && intent.getBooleanExtra("mizu_alarm", false)) {
            // Sample alarm-level reminder so CI can screenshot the full-screen alarm.
            startActivity(
                NotificationHelper.alarmIntent(
                    this,
                    ReminderContent(remainingMl = 1300, suggestedMl = 300, feasibility = Feasibility.TIGHT, urgent = false, deficitMl = 850),
                ),
            )
        }
        if (savedInstanceState == null) handleQuick(intent)
        setContent { MizuRoot(startScreen = startScreen, demo = demo, quickRequest = quickRequest.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleQuick(intent)
    }

    private fun handleQuick(intent: Intent?) {
        val request = when {
            intent?.getStringExtra(QuickActions.EXTRA_OPEN) == QuickActions.OPEN_WEIGH -> QuickRequest.OpenWeigh()
            intent?.getStringExtra(QuickActions.EXTRA_ACTION) == QuickActions.ACTION_FILL -> QuickRequest.Fill()
            else -> null
        }
        if (request != null) quickRequest.value = request
    }
}
