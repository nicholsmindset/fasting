package com.fastingshift.data.db

import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
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
    @Query("DELETE FROM log_metric") suspend fun clearMetrics()
    @Query("DELETE FROM qada_repayment") suspend fun clearRepayments()
    @Query("DELETE FROM qada_debt") suspend fun clearQada()
    @Query("DELETE FROM challenge") suspend fun clearChallenges()
    @Query("SELECT * FROM log_metric") suspend fun metrics(): List<MetricLogEntity>
    @Query("SELECT * FROM qada_debt") suspend fun qada(): List<QadaDebtEntity>
    @Query("SELECT * FROM challenge") suspend fun challenges(): List<ChallengeEntity>
    @Insert suspend fun insertMetrics(items: List<MetricLogEntity>)
    @Insert suspend fun insertRepayments(items: List<QadaRepaymentEntity>)
    @Insert suspend fun insertQada(items: List<QadaDebtEntity>)
    @Insert suspend fun insertChallenges(items: List<ChallengeEntity>)

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

@Dao
interface MetricDao {
    @Insert suspend fun insert(entity: MetricLogEntity): Long
    @Query("SELECT * FROM log_metric ORDER BY at DESC") fun observeAll(): Flow<List<MetricLogEntity>>
    @Query("SELECT * FROM log_metric WHERE at >= :fromMillis ORDER BY at DESC") suspend fun since(fromMillis: Long): List<MetricLogEntity>
    @Query("SELECT * FROM log_metric WHERE id = :id") suspend fun get(id: Long): MetricLogEntity?
    @Query("DELETE FROM log_metric WHERE id = :id") suspend fun delete(id: Long)
}

data class QadaYearRows(
    @Embedded val debt: QadaDebtEntity,
    @Relation(parentColumn = "hijri_year", entityColumn = "hijri_year")
    val repayments: List<QadaRepaymentEntity>,
)

@Dao
abstract class QadaDao {
    @Query("SELECT * FROM qada_debt ORDER BY hijri_year") abstract fun observeAll(): Flow<List<QadaDebtEntity>>
    @Query("SELECT * FROM qada_debt ORDER BY hijri_year") abstract suspend fun getAll(): List<QadaDebtEntity>

    @Transaction
    @Query("SELECT * FROM qada_debt ORDER BY hijri_year DESC")
    abstract fun observeLedger(): Flow<List<QadaYearRows>>

    @Transaction
    @Query("SELECT * FROM qada_debt ORDER BY hijri_year")
    abstract suspend fun ledgerSnapshot(): List<QadaYearRows>

    @Upsert protected abstract suspend fun putDebt(entity: QadaDebtEntity)
    @Query("DELETE FROM qada_debt WHERE hijri_year = :year") protected abstract suspend fun deleteDebt(year: Int)
    @Query("SELECT days_owed FROM qada_debt WHERE hijri_year = :year") protected abstract suspend fun owed(year: Int): Int?
    @Query("SELECT COUNT(*) FROM qada_repayment WHERE hijri_year = :year") protected abstract suspend fun repaid(year: Int): Int
    @Query("SELECT hijri_year FROM qada_repayment WHERE observed_on = :day") protected abstract suspend fun allocation(day: LocalDate): Int?
    @Insert protected abstract suspend fun insertRepayment(row: QadaRepaymentEntity)
    @Query("DELETE FROM qada_repayment WHERE observed_on = :day AND hijri_year = :year")
    abstract suspend fun removeRepayment(day: LocalDate, year: Int): Int

    @Transaction
    open suspend fun setDebt(debt: QadaDebtEntity): Boolean {
        if (debt.daysOwed < repaid(debt.hijriYear)) return false
        if (debt.daysOwed == 0) deleteDebt(debt.hijriYear) else putDebt(debt)
        return true
    }

    /** A stale editor cannot overwrite a different allocation. A change of date or year is atomic. */
    @Transaction
    open suspend fun saveRepayment(previous: QadaRepaymentEntity?, updated: QadaRepaymentEntity): Boolean {
        if (previous != null && allocation(previous.observedOn) != previous.hijriYear) return false
        if (previous?.observedOn != updated.observedOn && allocation(updated.observedOn) != null) return false
        val limit = owed(updated.hijriYear) ?: return false
        if (previous?.hijriYear != updated.hijriYear && repaid(updated.hijriYear) >= limit) return false
        if (previous != null) removeRepayment(previous.observedOn, previous.hijriYear)
        insertRepayment(updated)
        return true
    }
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge WHERE start_date = :date AND left_at IS NULL ORDER BY id DESC")
    suspend fun joinedStartingOn(date: LocalDate): List<ChallengeEntity>

    @Query("SELECT * FROM challenge ORDER BY start_date DESC, id DESC") fun observeAll(): Flow<List<ChallengeEntity>>
    @Query("SELECT * FROM challenge WHERE id = :id") suspend fun get(id: Long): ChallengeEntity?
    @Insert suspend fun insert(entity: ChallengeEntity): Long
    @Update suspend fun update(entity: ChallengeEntity)
    @Query("UPDATE challenge SET follows_own_plan = :enabled WHERE id = :id AND left_at IS NULL")
    suspend fun setOwnPlan(id: Long, enabled: Boolean)
    @Query("UPDATE challenge SET left_at = :at WHERE id = :id AND left_at IS NULL")
    suspend fun leave(id: Long, at: Instant)
    @Query("DELETE FROM challenge WHERE id = :id") suspend fun delete(id: Long)
}
