package com.fastingshift.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FastDao {
    @Query("SELECT * FROM fast WHERE ended_at IS NULL ORDER BY start_at DESC LIMIT 1")
    fun observeActive(): Flow<FastEntity?>

    @Query("SELECT * FROM fast WHERE ended_at IS NULL ORDER BY start_at DESC LIMIT 1")
    suspend fun getActive(): FastEntity?

    @Query("SELECT * FROM fast ORDER BY start_at DESC")
    fun observeAll(): Flow<List<FastEntity>>

    @Query("SELECT * FROM fast ORDER BY start_at DESC")
    suspend fun getAll(): List<FastEntity>

    @Query("SELECT * FROM fast WHERE id = :id")
    suspend fun getById(id: Long): FastEntity?

    @Insert
    suspend fun insert(fast: FastEntity): Long

    /** Check-and-insert in one transaction; returns the new id, or null if a fast is active. */
    @Transaction
    suspend fun insertIfNoActive(fast: FastEntity): Long? = if (getActive() != null) null else insert(fast)

    @Update
    suspend fun update(fast: FastEntity)

    @Query("DELETE FROM fast WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScreeningDao {
    @Query("SELECT * FROM screening ORDER BY answered_at DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<ScreeningEntity?>

    @Query("SELECT * FROM screening ORDER BY answered_at DESC, id DESC LIMIT 1")
    suspend fun getLatest(): ScreeningEntity?

    @Insert
    suspend fun insert(entity: ScreeningEntity): Long
}

@Dao
interface StreakFreezeDao {
    @Query("SELECT * FROM streak_freeze")
    fun observeAll(): Flow<List<StreakFreezeEntity>>

    @Query("SELECT * FROM streak_freeze")
    suspend fun getAll(): List<StreakFreezeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: StreakFreezeEntity)
}

@Dao
interface LogDao {
    @Insert suspend fun insertWeight(entity: WeightLogEntity): Long
    @Insert suspend fun insertWater(entity: WaterLogEntity): Long
    @Insert suspend fun insertElectrolyte(entity: ElectrolyteLogEntity): Long

    @Query("SELECT * FROM log_weight ORDER BY at DESC")
    fun observeWeights(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM log_water WHERE at >= :fromMillis ORDER BY at DESC")
    fun observeWaterSince(fromMillis: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM log_electrolyte WHERE at >= :fromMillis ORDER BY at DESC")
    fun observeElectrolytesSince(fromMillis: Long): Flow<List<ElectrolyteLogEntity>>

    @Query("SELECT * FROM log_weight ORDER BY at DESC") suspend fun getWeights(): List<WeightLogEntity>
    @Query("SELECT * FROM log_weight WHERE id = :id") suspend fun getWeight(id: Long): WeightLogEntity?
    @Query("SELECT * FROM log_water WHERE id = :id") suspend fun getWater(id: Long): WaterLogEntity?
    @Query("DELETE FROM log_weight WHERE id = :id") suspend fun deleteWeight(id: Long)
    @Query("DELETE FROM log_water WHERE id = :id") suspend fun deleteWater(id: Long)
    @Query("DELETE FROM log_electrolyte WHERE id = :id") suspend fun deleteElectrolyte(id: Long)
}

@Dao
interface SymptomCheckinDao {
    @Insert suspend fun insert(entity: SymptomCheckinEntity): Long

    @Query("SELECT * FROM symptom_checkin WHERE fast_id = :fastId ORDER BY at")
    suspend fun forFast(fastId: Long): List<SymptomCheckinEntity>

    @Query("SELECT * FROM symptom_checkin WHERE fast_id = :fastId ORDER BY at")
    fun observeForFast(fastId: Long): Flow<List<SymptomCheckinEntity>>
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule ORDER BY id") fun observeAll(): Flow<List<ScheduleEntity>>
    @Query("SELECT * FROM schedule ORDER BY id") suspend fun getAll(): List<ScheduleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ScheduleEntity): Long
    @Query("DELETE FROM schedule WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ProtocolDao {
    @Query("SELECT * FROM protocol ORDER BY fast_hours") fun observeAll(): Flow<List<ProtocolEntity>>
    @Query("SELECT * FROM protocol") suspend fun getAll(): List<ProtocolEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ProtocolEntity)
}

@Dao
interface ProgramProgressDao {
    @Query("SELECT * FROM program_progress WHERE program_id = :programId ORDER BY day")
    fun observe(programId: String): Flow<List<ProgramProgressEntity>>
    @Query("SELECT * FROM program_progress") suspend fun getAll(): List<ProgramProgressEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity: ProgramProgressEntity)
}

/** Bulk access used only by export and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM log_weight") suspend fun weights(): List<WeightLogEntity>
    @Query("SELECT * FROM log_water") suspend fun water(): List<WaterLogEntity>
    @Query("SELECT * FROM log_electrolyte") suspend fun electrolytes(): List<ElectrolyteLogEntity>
    @Query("SELECT * FROM symptom_checkin") suspend fun checkins(): List<SymptomCheckinEntity>
    @Query("SELECT * FROM screening ORDER BY answered_at") suspend fun screenings(): List<ScreeningEntity>

    @Query("DELETE FROM symptom_checkin") suspend fun clearCheckins()
    @Query("DELETE FROM fast") suspend fun clearFasts()
    @Query("DELETE FROM protocol") suspend fun clearProtocols()
    @Query("DELETE FROM schedule") suspend fun clearSchedules()
    @Query("DELETE FROM log_weight") suspend fun clearWeights()
    @Query("DELETE FROM log_water") suspend fun clearWater()
    @Query("DELETE FROM log_electrolyte") suspend fun clearElectrolytes()
    @Query("DELETE FROM screening") suspend fun clearScreenings()
    @Query("DELETE FROM program_progress") suspend fun clearProgress()
    @Query("DELETE FROM streak_freeze") suspend fun clearFreezes()

    @Insert suspend fun insertFasts(items: List<FastEntity>)
    @Insert suspend fun insertProtocols(items: List<ProtocolEntity>)
    @Insert suspend fun insertSchedules(items: List<ScheduleEntity>)
    @Insert suspend fun insertWeights(items: List<WeightLogEntity>)
    @Insert suspend fun insertWater(items: List<WaterLogEntity>)
    @Insert suspend fun insertElectrolytes(items: List<ElectrolyteLogEntity>)
    @Insert suspend fun insertCheckins(items: List<SymptomCheckinEntity>)
    @Insert suspend fun insertScreenings(items: List<ScreeningEntity>)
    @Insert suspend fun insertProgress(items: List<ProgramProgressEntity>)
    @Insert suspend fun insertFreezes(items: List<StreakFreezeEntity>)
}
