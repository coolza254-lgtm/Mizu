package com.example.mizu.data

import androidx.room.withTransaction
import com.example.mizu.core.Bottle
import com.example.mizu.core.BottlePlanner
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

    /** @return the new log id, or -1 when [amountMl] is not positive. */
    suspend fun addLog(amountMl: Int, source: DrinkSource, at: LocalDateTime = LocalDateTime.now()): Long {
        if (amountMl <= 0) return -1
        val id = dao.insertLog(
            DrinkLogEntity(
                timestamp = at.toEpochMs(), amountMl = amountMl, source = source.name,
                bottleId = null, weightBeforeG = null, weightAfterG = null,
            ),
        )
        onLogsChanged()
        return id
    }

    /** Puts back a deleted log (undo). */
    suspend fun restoreLog(log: DrinkLog) {
        dao.insertLog(log.toEntity())
        onLogsChanged()
    }

    /** Debug builds only: fills an empty database with sample days for screenshots. */
    suspend fun seedDemo(logs: List<DrinkLog>) {
        if (dao.allLogs().isNotEmpty()) return
        logs.forEach { dao.insertLog(it.toEntity().copy(id = 0)) }
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
        val stamp = at.toEpochMs()
        // A fill (first weigh or refill) also teaches the bottle its capacity: the fullest it has been.
        fun filled(water: Int) = bottle.copy(
            currentWaterG = water,
            waterUpdatedAt = stamp,
            capacityMl = BottlePlanner.learnCapacity(bottle.capacityMl, water),
        )
        when (outcome) {
            is WeighOutcome.SetBaseline -> dao.updateBottle(filled(outcome.waterG))
            is WeighOutcome.AskRefill -> dao.updateBottle(filled(outcome.newWaterG))
            is WeighOutcome.Drink -> db.withTransaction {
                dao.insertLog(
                    DrinkLogEntity(
                        timestamp = stamp, amountMl = outcome.amountMl, source = DrinkSource.WEIGH.name,
                        bottleId = bottleId, weightBeforeG = outcome.weightBeforeG, weightAfterG = outcome.weightAfterG,
                    ),
                )
                dao.updateBottle(bottle.copy(currentWaterG = outcome.newWaterG, waterUpdatedAt = stamp))
            }
            WeighOutcome.NoChange, WeighOutcome.Invalid -> return
        }
        onLogsChanged()
    }

    /** One-tap "filled to the top": water = known capacity, no weighing and no log. */
    suspend fun fillBottle(bottleId: Long, at: LocalDateTime = LocalDateTime.now()) {
        val bottle = dao.getBottle(bottleId) ?: return
        val capacity = bottle.capacityMl ?: return
        dao.updateBottle(bottle.copy(currentWaterG = capacity, waterUpdatedAt = at.toEpochMs()))
    }

    /** Debug demo only: give the first bottle a level and capacity. */
    suspend fun seedDemoBottle(waterMl: Int, capacityMl: Int, at: LocalDateTime) {
        val bottle = dao.allBottles().firstOrNull() ?: return
        if (bottle.currentWaterG != null) return
        dao.updateBottle(bottle.copy(currentWaterG = waterMl, capacityMl = capacityMl, waterUpdatedAt = at.toEpochMs()))
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
