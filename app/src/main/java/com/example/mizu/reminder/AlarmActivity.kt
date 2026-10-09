package com.example.mizu.reminder

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.example.mizu.R
import com.example.mizu.container
import com.example.mizu.core.DrinkSource
import com.example.mizu.ui.BottleGauge
import com.example.mizu.ui.Haptics
import com.example.mizu.ui.HapticKind
import com.example.mizu.ui.LocalHaptics
import com.example.mizu.ui.MizuColors
import com.example.mizu.ui.MizuTheme
import com.example.mizu.ui.MonoKicker
import com.example.mizu.ui.PrimaryButton
import com.example.mizu.ui.SoftButton
import com.example.mizu.ui.dotGrid
import com.example.mizu.ui.grouped
import com.example.mizu.util.localizedWrapper
import kotlinx.coroutines.launch

/** Alarm-style reminder: full screen (also over the lock screen), looping alarm sound and vibration until answered. */
class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val amount = intent.getIntExtra(NotificationHelper.EXTRA_AMOUNT_ML, 250)
        val remaining = intent.getIntExtra(NotificationHelper.EXTRA_REMAINING_ML, 0)
        val deficit = intent.getIntExtra(NotificationHelper.EXTRA_DEFICIT_ML, 0)
        startAlarm()

        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings
            val base = LocalContext.current
            val haptics = remember(base) { Haptics(base.applicationContext) }
            MizuTheme(font = s?.font ?: com.example.mizu.core.AppFont.EDITORIAL) {
                if (s != null) {
                    val localized = remember(s.language, base) { base.localizedWrapper(s.language) }
                    CompositionLocalProvider(
                        LocalContext provides localized,
                        LocalConfiguration provides localized.resources.configuration,
                        LocalHaptics provides haptics,
                    ) {
                        AlarmScreen(
                            amountMl = amount,
                            remainingMl = remaining,
                            deficitMl = deficit,
                            onDrank = { finishWith { container.repository.addLog(amount, DrinkSource.QUICK) } },
                            onSnooze = { finishWith { snooze(container) } },
                            onDismiss = { finishWith { } },
                        )
                    }
                }
            }
        }
    }

    private fun finishWith(action: suspend () -> Unit) {
        stopAlarm()
        lifecycleScope.launch {
            action()
            NotificationManagerCompat.from(this@AlarmActivity).cancel(NotificationHelper.NOTIFICATION_ID)
            finish()
        }
    }

    private fun startAlarm() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 900), 0))
        } catch (_: Exception) {
        }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(
    amountMl: Int,
    remainingMl: Int,
    deficitMl: Int,
    onDrank: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ml = stringResource(R.string.unit_ml)
    Column(
        Modifier.fillMaxSize().dotGrid().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        MonoKicker("mizu / alarm")
        Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        BottleGauge(previousWater = 0, newWater = 0, capacityMl = 1000, modifier = Modifier.size(width = 90.dp, height = 186.dp))
        Text(
            stringResource(R.string.alarm_behind, deficitMl.coerceAtLeast(0).grouped(), remainingMl.grouped()),
            style = MaterialTheme.typography.bodyLarge,
            color = MizuColors.InkSoft,
            textAlign = TextAlign.Center,
        )
        Text("${amountMl.grouped()} $ml", style = MaterialTheme.typography.displayMedium)
        PrimaryButton(stringResource(R.string.alarm_drank, amountMl.grouped()), onDrank, Modifier.fillMaxWidth(), haptic = HapticKind.SUCCESS)
        SoftButton(stringResource(R.string.notif_action_snooze), onSnooze, Modifier.fillMaxWidth())
        SoftButton(stringResource(R.string.alarm_dismiss), onDismiss, Modifier.fillMaxWidth(), color = MizuColors.InkSoft, haptic = HapticKind.TICK)
    }
}
