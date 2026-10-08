package com.example.mizu.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mizu.container
import com.example.mizu.core.ReminderEngine
import com.example.mizu.data.toModel
import com.example.mizu.util.toEpochMs
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val now = LocalDateTime.now()
        val settings = c.settings.settings.first()
        val logs = c.dao.logsSince(now.toLocalDate().atStartOfDay().toEpochMs()).map { it.toModel() }
        val content = ReminderEngine.contentAt(now, settings, logs.sumOf { it.amountMl })

        // Re-check at fire time: the goal may have been reached, or the job may have been delayed past the window.
        if (settings.remindersEnabled && content != null && ReminderEngine.isWithinWindow(now, settings)) {
            NotificationHelper.show(applicationContext, settings.language, content, settings.goalMl)
            c.settings.setLastReminder(now)
        }
        c.settings.setSnoozeUntil(null)
        c.scheduler.reschedule(fromWorker = true)
        return Result.success()
    }
}
