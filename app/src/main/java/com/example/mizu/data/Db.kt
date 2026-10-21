package com.example.mizu.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mizu.core.Bottle
import com.example.mizu.core.DrinkLog
import com.example.mizu.core.DrinkSource
import com.example.mizu.util.toEpochMs
import com.example.mizu.util.toLocalDateTime
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bottles")
data class BottleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emptyWeightG: Int?,
    val currentWaterG: Int?,
    val isActive: Boolean,
    val capacityMl: Int? = null,
    val waterUpdatedAt: Long? = null,
)

@Entity(tableName = "drink_logs", indices = [Index("timestamp")])
data class DrinkLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val amountMl: Int,
    val source: String,
    val bottleId: Long?,
    val weightBeforeG: Int?,
    val weightAfterG: Int?,
    val estimatedFromMs: Long? = null,
)

fun BottleEntity.toModel() = Bottle(id, name, emptyWeightG, currentWaterG, isActive, capacityMl, waterUpdatedAt?.toLocalDateTime())

fun Bottle.toEntity() = BottleEntity(id, name, emptyWeightG, currentWaterG, isActive, capacityMl, waterUpdatedAt?.toEpochMs())

fun DrinkLogEntity.toModel() = DrinkLog(
    id = id,
    timestamp = timestamp.toLocalDateTime(),
    amountMl = amountMl,
    source = runCatching { DrinkSource.valueOf(source) }.getOrDefault(DrinkSource.MANUAL),
    bottleId = bottleId,
    weightBeforeG = weightBeforeG,
    weightAfterG = weightAfterG,
    estimatedFrom = estimatedFromMs?.toLocalDateTime(),
)

fun DrinkLog.toEntity() = DrinkLogEntity(
    id = id,
    timestamp = timestamp.toEpochMs(),
    amountMl = amountMl,
    source = source.name,
    bottleId = bottleId,
    weightBeforeG = weightBeforeG,
    weightAfterG = weightAfterG,
    estimatedFromMs = estimatedFrom?.toEpochMs(),
)

@Dao
interface MizuDao {
    @Query("SELECT * FROM drink_logs ORDER BY timestamp DESC")
    fun observeLogs(): Flow<List<DrinkLogEntity>>

    @Query("SELECT * FROM drink_logs ORDER BY timestamp ASC")
    suspend fun allLogs(): List<DrinkLogEntity>

    @Query("SELECT * FROM drink_logs WHERE timestamp >= :fromMs ORDER BY timestamp ASC")
    suspend fun logsSince(fromMs: Long): List<DrinkLogEntity>

    @Query("SELECT * FROM drink_logs WHERE id = :id")
    suspend fun getLog(id: Long): DrinkLogEntity?

    @Insert
    suspend fun insertLog(log: DrinkLogEntity): Long

    @Update
    suspend fun updateLog(log: DrinkLogEntity)

    @Query("DELETE FROM drink_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM bottles ORDER BY id ASC")
    fun observeBottles(): Flow<List<BottleEntity>>

    @Query("SELECT * FROM bottles ORDER BY id ASC")
    suspend fun allBottles(): List<BottleEntity>

    @Query("SELECT * FROM bottles WHERE id = :id")
    suspend fun getBottle(id: Long): BottleEntity?

    @Query("SELECT COUNT(*) FROM bottles")
    suspend fun bottleCount(): Int

    @Insert
    suspend fun insertBottle(bottle: BottleEntity): Long

    @Update
    suspend fun updateBottle(bottle: BottleEntity)
}

@Database(entities = [BottleEntity::class, DrinkLogEntity::class], version = 3, exportSchema = false)
abstract class MizuDatabase : RoomDatabase() {
    abstract fun dao(): MizuDao

    companion object {
        /** v2: bottle capacity and when its water level was last set. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bottles ADD COLUMN capacityMl INTEGER")
                db.execSQL("ALTER TABLE bottles ADD COLUMN waterUpdatedAt INTEGER")
            }
        }

        /** v3: start of the estimated drinking period for weighed drinks. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drink_logs ADD COLUMN estimatedFromMs INTEGER")
            }
        }

        fun create(context: Context): MizuDatabase =
            Room.databaseBuilder(context, MizuDatabase::class.java, "mizu.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
