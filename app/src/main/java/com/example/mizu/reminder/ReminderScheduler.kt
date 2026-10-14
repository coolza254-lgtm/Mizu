package com.example.mizu.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mizu.core.ReminderEngine
import com.example.mizu.data.MizuDao
import com.example.mizu.data.SettingsRepository
import com.example.mizu.data.toModel
import com.example.mizu.util.toEpochMs
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Keeps exactly one pending WorkManager job: the next reminder from [ReminderEngine.nextReminder].
 * WorkManager persists across reboots; [BootReceiver] re-plans anyway so the time is fresh.
 * Timing is inexact (Doze may delay a reminder by minutes), which is fine for hydration nudges,
 * so no exact-alarm permission is requested.
 */
class ReminderScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val dao: MizuDao,
) {
    /** [fromWorker]: called from inside the running reminder job, so append instead of replacing (replacing would cancel itself). */
    suspend fun reschedule(fromWorker: Boolean = false, now: LocalDateTime = LocalDateTime.now()) {
        val current = settings.settings.first()
        val state = settings.reminderState.first()
        val logs = dao.logsSince(now.toLocalDate().atStartOfDay().toEpochMs()).map { it.toModel() }
        val plan = ReminderEngine.nextReminder(now, current, logs, state.lastReminderAt, state.snoozeUntil)

        val workManager = WorkManager.getInstance(context)
        if (plan == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delayMs = Duration.between(now, plan.at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME,
            if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "mizu_next_reminder"
    }
}
