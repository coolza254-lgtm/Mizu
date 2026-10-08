package com.example.mizu.data

import androidx.room.withTransaction
import com.example.mizu.core.Bottle
import com.example.mizu.core.CsvExporter
import com.example.mizu.core.DrinkLog
import com.example.mizu.core.DrinkSource
import com.example.mizu.core.WeighOutcome
import com.example.mizu.reminder.ReminderScheduler
import com.example.mizu.util.toEpochMs
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MizuRepository(
    private val db: MizuDatabase,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    private val dao = db.dao()

    val logs: Flow<List<DrinkLog>> = dao.observeLogs().map { list -> list.map { it.toModel() } }
    val bottles: Flow<List<Bottle>> = dao.observeBottles().map { list -> list.map { it.toModel() } }

    suspend fun ensureDefaultBottle(name: String) {
        if (dao.bottleCount() == 0) {
            dao.insertBottle(BottleEntity(name = name, emptyWeightG = null, currentWaterG = null, isActive = true))
        }
    }

    suspend fun addLog(amountMl: Int, source: DrinkSource, at: LocalDateTime = LocalDateTime.now()) {
        if (amountMl <= 0) return
        dao.insertLog(
            DrinkLogEntity(
                timestamp = at.toEpochMs(), amountMl = amountMl, source = source.name,
                bottleId = null, weightBeforeG = null, weightAfterG = null,
            ),
        )
        onLogsChanged()
    }

    suspend fun updateLogAmount(id: Long, amountMl: Int) {
        val existing = dao.getLog(id) ?: return
        if (amountMl <= 0) return
        dao.updateLog(existing.copy(amountMl = amountMl))
        onLogsChanged()
    }

    suspend fun deleteLog(id: Long) {
        dao.deleteLog(id)
        onLogsChanged()
    }

    /** Applies a confirmed weigh outcome. [WeighOutcome.AskRefill] here means "yes, it was refilled": new baseline, no log. */
    suspend fun saveWeigh(bottleId: Long, outcome: WeighOutcome, at: LocalDateTime = LocalDateTime.now()) {
        val bottle = dao.getBottle(bottleId) ?: return
        when (outcome) {
            is WeighOutcome.SetBaseline -> dao.updateBottle(bottle.copy(currentWaterG = outcome.waterG))
            is WeighOutcome.AskRefill -> dao.updateBottle(bottle.copy(currentWaterG = outcome.newWaterG))
            is WeighOutcome.Drink -> db.withTransaction {
                dao.insertLog(
                    DrinkLogEntity(
                        timestamp = at.toEpochMs(), amountMl = outcome.amountMl, source = DrinkSource.WEIGH.name,
                        bottleId = bottleId, weightBeforeG = outcome.weightBeforeG, weightAfterG = outcome.weightAfterG,
                    ),
                )
                dao.updateBottle(bottle.copy(currentWaterG = outcome.newWaterG))
            }
            WeighOutcome.NoChange, WeighOutcome.Invalid -> return
        }
        onLogsChanged()
    }

    suspend fun saveBottle(bottle: Bottle) {
        if (bottle.id == 0L) dao.insertBottle(bottle.toEntity()) else dao.updateBottle(bottle.toEntity())
    }

    /** Bottles are archived rather than deleted so old logs and CSV rows keep their name. */
    suspend fun archiveBottle(id: Long) {
        val b = dao.getBottle(id) ?: return
        dao.updateBottle(b.copy(isActive = false))
    }

    suspend fun buildCsv(from: LocalDate, to: LocalDate): String {
        val names = dao.allBottles().associate { it.id to it.name }
        return CsvExporter.build(dao.allLogs().map { it.toModel() }, names, from, to)
    }

    /** Any log change cancels a pending snooze and reschedules the next reminder. */
    private suspend fun onLogsChanged() {
        settings.setSnoozeUntil(null)
        scheduler.reschedule()
    }
}
