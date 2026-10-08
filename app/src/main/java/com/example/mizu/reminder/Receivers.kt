package com.example.mizu.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.mizu.container
import com.example.mizu.core.DrinkSource
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Handles the "Drank" and "Snooze" notification buttons. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.applicationContext.container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    NotificationHelper.ACTION_DRANK -> {
                        val amount = intent.getIntExtra(NotificationHelper.EXTRA_AMOUNT_ML, 0)
                        c.repository.addLog(amount, DrinkSource.QUICK)
                    }
                    NotificationHelper.ACTION_SNOOZE -> snooze(c)
                    NotificationHelper.ACTION_REPLY -> {
                        // Quick-response reply from a watch/band: "snooze" words snooze, anything else logs.
                        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationHelper.KEY_REPLY)
                        if (NotificationHelper.isSnoozeReply(reply)) {
                            snooze(c)
                        } else {
                            val amount = intent.getIntExtra(NotificationHelper.EXTRA_AMOUNT_ML, 0)
                            c.repository.addLog(amount, DrinkSource.QUICK)
                        }
                    }
                }
                NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIFICATION_ID)
            } finally {
                pending.finish()
            }
        }
    }
}

/** A snooze is not a drink: nothing is logged, the reminder just moves later. */
private suspend fun snooze(c: com.example.mizu.AppContainer) {
    val minutes = c.settings.settings.first().snoozeMinutes
    val now = LocalDateTime.now()
    c.settings.snooze(now, now.plusMinutes(minutes.toLong()))
    c.scheduler.reschedule()
}

/** Re-plans the next reminder after a reboot or an app update (including in-app updates). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val c = context.applicationContext.container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                c.scheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
