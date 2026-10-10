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
import com.example.mizu.core.ReminderLevel
import com.example.mizu.util.localized
import java.util.Locale

object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val CHANNEL_STRONG = "reminders_strong"
    const val CHANNEL_ALARM = "reminders_alarm"
    const val EXTRA_REMAINING_ML = "remaining_ml"
    const val EXTRA_DEFICIT_ML = "deficit_ml"
    private val STRONG_VIBRATION = longArrayOf(0, 400, 200, 400, 200, 700)
    const val NOTIFICATION_ID = 1001
    const val ACTION_DRANK = "com.example.mizu.action.DRANK"
    const val ACTION_SNOOZE = "com.example.mizu.action.SNOOZE"
    const val ACTION_REPLY = "com.example.mizu.action.REPLY"
    const val EXTRA_AMOUNT_ML = "amount_ml"
    const val KEY_REPLY = "mizu_reply"

    /** Three channels so each level can have its own importance, sound and vibration (users can tune them in system settings too). */
    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STRONG, context.getString(R.string.channel_strong), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = STRONG_VIBRATION
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = STRONG_VIBRATION
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build(),
                )
                setBypassDnd(false)
            },
        )
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
        val amountBig = ctx.getString(R.string.notif_amount, consumed.grouped(), goalMl.grouped())
        val detail = ctx.getString(R.string.notif_progress_detail, content.suggestedMl.grouped(), content.remainingMl.grouped())
        val body = ctx.getString(R.string.notif_body, content.remainingMl, content.suggestedMl, feasibility)

        fun views(layout: Int, big: Boolean) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.notif_title, if (big) title else "$title · $amountLine")
            setProgressBar(R.id.notif_bar, 1000, (progress * 1000).toInt(), false)
            setTextViewText(R.id.notif_detail, detail)
            if (big) {
                setTextViewText(R.id.notif_amount, amountBig)
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
        // Phone: a plain Reply (inline text). Wearables: the same action with Drank / Snooze quick choices.
        // Choices are kept off the phone copy so they don't show up as duplicate smart-reply chips.
        fun replyAction(withChoices: Boolean): NotificationCompat.Action {
            val input = RemoteInput.Builder(KEY_REPLY).setLabel(ctx.getString(R.string.notif_reply_label))
            if (withChoices) {
                input.setChoices(arrayOf<CharSequence>(ctx.getString(R.string.notif_action_drank), ctx.getString(R.string.notif_action_snooze)))
            }
            return NotificationCompat.Action.Builder(
                R.drawable.ic_stat_drop, ctx.getString(R.string.notif_action_reply),
                actionIntent(context, ACTION_REPLY, content.suggestedMl, mutable = true),
            )
                .addRemoteInput(input.build())
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setAllowGeneratedReplies(false)
                .build()
        }

        val channel = when (content.level) {
            ReminderLevel.GENTLE -> CHANNEL_ID
            ReminderLevel.STRONG -> CHANNEL_STRONG
            ReminderLevel.ALARM -> CHANNEL_ALARM
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF2479C7.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("$percent%")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views(R.layout.notif_progress_small, big = false))
            .setCustomBigContentView(views(R.layout.notif_progress_big, big = true))
            .setPriority(if (content.level == ReminderLevel.GENTLE) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (content.level == ReminderLevel.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(drank)
            .addAction(snooze)
            .addAction(replyAction(withChoices = false))
            .extend(NotificationCompat.WearableExtender().addAction(drank).addAction(snooze).addAction(replyAction(withChoices = true)))
        if (content.level == ReminderLevel.ALARM) {
            // Alarm style: full screen over the lock screen (system shows a heads-up instead while the phone is in use).
            val alarm = PendingIntent.getActivity(
                context, 2, alarmIntent(context, content),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setFullScreenIntent(alarm, true).setOngoing(true)
        }
        val notification = builder.build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the call.
        }
    }

    fun alarmIntent(context: Context, content: ReminderContent): Intent =
        Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_AMOUNT_ML, content.suggestedMl)
            .putExtra(EXTRA_REMAINING_ML, content.remainingMl)
            .putExtra(EXTRA_DEFICIT_ML, content.deficitMl)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    /** Android 14+ lets users revoke full-screen alarms; true when this app may use them. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

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
