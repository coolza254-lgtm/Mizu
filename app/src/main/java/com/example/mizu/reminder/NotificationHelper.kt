package com.example.mizu.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.mizu.MainActivity
import com.example.mizu.R
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.Feasibility
import com.example.mizu.core.GoalCalculator
import com.example.mizu.core.ReminderContent
import com.example.mizu.util.localized
import java.util.Locale

object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val NOTIFICATION_ID = 1001
    const val ACTION_DRANK = "com.example.mizu.action.DRANK"
    const val ACTION_SNOOZE = "com.example.mizu.action.SNOOZE"
    const val ACTION_REPLY = "com.example.mizu.action.REPLY"
    const val EXTRA_AMOUNT_ML = "amount_ml"
    const val KEY_REPLY = "mizu_reply"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun Int.grouped(): String = String.format(Locale.US, "%,d", this)

    /**
     * Reminder with a water-gradient progress bar (custom views), plus plain title/text so watches and bands
     * that only mirror text still read well. Actions: Drank, Snooze, and Reply (for wearables such as the
     * Galaxy Fit3 that only offer quick-response replies; a reply of "snooze" snoozes, anything else logs).
     */
    fun show(context: Context, language: AppLanguage, content: ReminderContent, goalMl: Int) {
        val ctx = context.localized(language)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val consumed = (goalMl - content.remainingMl).coerceAtLeast(0)
        val progress = GoalCalculator.progress(consumed, goalMl)
        val percent = (progress * 100).toInt()
        val feasibility = ctx.getString(
            when (content.feasibility) {
                Feasibility.REACHABLE -> R.string.feasibility_reachable
                Feasibility.TIGHT -> R.string.feasibility_tight
                Feasibility.NOT_REACHABLE -> R.string.feasibility_not_reachable
            },
        )
        val title = ctx.getString(if (content.urgent) R.string.notif_title_urgent else R.string.notif_title)
        val amountLine = ctx.getString(R.string.notif_progress_amount, consumed.grouped(), goalMl.grouped(), percent)
        val detail = ctx.getString(R.string.notif_progress_detail, content.suggestedMl.grouped(), content.remainingMl.grouped())
        val body = ctx.getString(R.string.notif_body, content.remainingMl, content.suggestedMl, feasibility)

        fun views(layout: Int, big: Boolean) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.notif_title, if (big) title else "$title · $amountLine")
            setProgressBar(R.id.notif_bar, 1000, (progress * 1000).toInt(), false)
            setTextViewText(R.id.notif_detail, detail)
            if (big) {
                setTextViewText(R.id.notif_amount, amountLine)
                setTextViewText(R.id.notif_feasibility, feasibility)
            }
        }

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val drank = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_drop, ctx.getString(R.string.notif_action_drank),
            actionIntent(context, ACTION_DRANK, content.suggestedMl),
        ).build()
        val snooze = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_drop, ctx.getString(R.string.notif_action_snooze),
            actionIntent(context, ACTION_SNOOZE, content.suggestedMl),
        ).build()
        val reply = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_drop, ctx.getString(R.string.notif_action_reply),
            actionIntent(context, ACTION_REPLY, content.suggestedMl, mutable = true),
        )
            .addRemoteInput(
                RemoteInput.Builder(KEY_REPLY)
                    .setLabel(ctx.getString(R.string.notif_reply_label))
                    .setChoices(arrayOf<CharSequence>(ctx.getString(R.string.notif_action_drank), ctx.getString(R.string.notif_action_snooze)))
                    .build(),
            )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF2479C7.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("$percent%")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views(R.layout.notif_progress_small, big = false))
            .setCustomBigContentView(views(R.layout.notif_progress_big, big = true))
            .setPriority(if (content.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(drank)
            .addAction(snooze)
            .addAction(reply)
            .extend(NotificationCompat.WearableExtender().addAction(drank).addAction(snooze).addAction(reply))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the call.
        }
    }

    /** Words in a quick-response reply that mean "snooze" (any other reply logs the suggested amount). */
    fun isSnoozeReply(text: CharSequence?): Boolean {
        val t = text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: return false
        return listOf("เลื่อน", "ทีหลัง", "snooze", "later", "スヌーズ", "あとで", "後で").any { t.contains(it) }
    }

    private fun actionIntent(context: Context, action: String, amountMl: Int, mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_AMOUNT_ML, amountMl)
        // RemoteInput needs a mutable PendingIntent so the system can attach the reply. The intent is explicit.
        val mutability = if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else if (mutable) {
            0
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, mutability or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
