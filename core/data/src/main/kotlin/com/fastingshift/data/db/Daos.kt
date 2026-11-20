package com.fastingshift.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}

@Dao
interface SymptomCheckinDao {
    @Insert suspend fun insert(entity: SymptomCheckinEntity): Long

    @Query("SELECT * FROM symptom_checkin WHERE fast_id = :fastId ORDER BY at")
    suspend fun forFast(fastId: Long): List<SymptomCheckinEntity>
}
