package com.example.mizu.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mizu.MainActivity
import com.example.mizu.R
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.Feasibility
import com.example.mizu.core.ReminderContent
import com.example.mizu.util.localized

object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val NOTIFICATION_ID = 1001
    const val ACTION_DRANK = "com.example.mizu.action.DRANK"
    const val ACTION_SNOOZE = "com.example.mizu.action.SNOOZE"
    const val EXTRA_AMOUNT_ML = "amount_ml"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, language: AppLanguage, content: ReminderContent) {
        val ctx = context.localized(language)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val feasibility = ctx.getString(
            when (content.feasibility) {
                Feasibility.REACHABLE -> R.string.feasibility_reachable
                Feasibility.TIGHT -> R.string.feasibility_tight
                Feasibility.NOT_REACHABLE -> R.string.feasibility_not_reachable
            },
        )
        val body = ctx.getString(R.string.notif_body, content.remainingMl, content.suggestedMl, feasibility)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(ctx.getString(if (content.urgent) R.string.notif_title_urgent else R.string.notif_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (content.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.notif_action_drank), actionIntent(context, ACTION_DRANK, content.suggestedMl))
            .addAction(0, ctx.getString(R.string.notif_action_snooze), actionIntent(context, ACTION_SNOOZE, content.suggestedMl))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the call.
        }
    }

    private fun actionIntent(context: Context, action: String, amountMl: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_AMOUNT_ML, amountMl)
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
