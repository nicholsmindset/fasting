package com.fastingshift.data.repo

import com.fastingshift.data.db.ElectrolyteLogEntity
import com.fastingshift.data.db.LogDao
import com.fastingshift.data.db.ProgramProgressDao
import com.fastingshift.data.db.ProgramProgressEntity
import androidx.room.withTransaction
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.db.ScheduleEntity
import com.fastingshift.data.db.SymptomCheckinDao
import com.fastingshift.data.db.SymptomCheckinEntity
import com.fastingshift.data.db.WaterLogEntity
import com.fastingshift.data.db.WeightLogEntity
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.safety.SymptomCheck
import com.fastingshift.data.settings.FIRST_PLAN_SCHEDULE_ID
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.schedule.FirstPlanOffer
import com.fastingshift.domain.schedule.PlanHistory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LogRepository(private val dao: LogDao) {
    fun observeWeights(): Flow<List<WeightLog>> = dao.observeWeights().map { l -> l.map { WeightLog(it.id, it.at, it.value, it.unit, it.source) } }
    fun observeWaterSince(from: Instant): Flow<List<WaterLog>> =
        dao.observeWaterSince(from.toEpochMilli()).map { l -> l.map { WaterLog(it.id, it.at, it.value, it.unit, it.source) } }
    fun observeElectrolytesSince(from: Instant): Flow<List<ElectrolyteLog>> =
        dao.observeElectrolytesSince(from.toEpochMilli()).map { l -> l.map { ElectrolyteLog(it.id, it.at, it.electrolyte, it.value, it.source) } }

    suspend fun getWeights(): List<WeightLog> = dao.getWeights().map { WeightLog(it.id, it.at, it.value, it.unit, it.source) }

    suspend fun addWeight(log: WeightLog): Long = dao.insertWeight(WeightLogEntity(0, log.at, log.value, log.unit, log.source))
    suspend fun addWater(log: WaterLog): Long = dao.insertWater(WaterLogEntity(0, log.at, log.value, log.unit, log.source))
    suspend fun addElectrolyte(log: ElectrolyteLog): Long =
        dao.insertElectrolyte(ElectrolyteLogEntity(0, log.at, log.electrolyte, log.milligrams, log.source))

    suspend fun getWeight(id: Long): WeightLog? = dao.getWeight(id)?.let { WeightLog(it.id, it.at, it.value, it.unit, it.source) }
    suspend fun getWater(id: Long): WaterLog? = dao.getWater(id)?.let { WaterLog(it.id, it.at, it.value, it.unit, it.source) }

    suspend fun deleteWeight(id: Long) = dao.deleteWeight(id)
    suspend fun deleteWater(id: Long) = dao.deleteWater(id)
    suspend fun deleteElectrolyte(id: Long) = dao.deleteElectrolyte(id)
}

class ScheduleRepository(
    private val db: FastingDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val dao = db.scheduleDao()
    private val changes = Mutex()

    fun observeAll(): Flow<List<Schedule>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getAll(): List<Schedule> = dao.getAll().map { it.toDomain() }
    suspend fun save(schedule: Schedule): Long = changes.withLock {
        val id = upsert(schedule)
        recordCurrentPlan()
        id
    }

    fun firstEvening(): Pair<Long, LocalDate>? = settings.firstEvening()

    /** Re-check history, screening and conflicting routines inside the same transaction as the edit. */
    suspend fun saveFirstEvening(
        schedule: Schedule,
        date: LocalDate,
        check: suspend (List<Schedule>, PlanHistory) -> FirstPlanOffer,
    ): FirstPlanOffer = changes.withLock {
        db.withTransaction {
            val previous = firstEvening()
            val routines = getAll().filter { it.id != previous?.first }
            when (val offer = check(routines, settings.current().planHistory)) {
                FirstPlanOffer.AVAILABLE -> {
                    val id = previous?.first ?: FIRST_PLAN_SCHEDULE_ID
                    upsert(schedule.copy(id = id))
                    settings.rememberFirstEvening(id, date)
                    offer
                }
                FirstPlanOffer.COVERED -> {
                    // When an edit lands on a regular routine, retire only the old one-off.
                    if (previous != null) {
                        dao.delete(previous.first)
                    }
                    offer
                }
                FirstPlanOffer.UNAVAILABLE -> offer
            }
        }.also { offer ->
            if (offer == FirstPlanOffer.COVERED) settings.clearFirstEvening()
            if (offer != FirstPlanOffer.UNAVAILABLE) recordCurrentPlan()
        }
    }

    suspend fun cancelFirstEvening(expected: Pair<Long, LocalDate>? = null) = changes.withLock {
        val current = firstEvening() ?: return@withLock
        if (expected != null && current != expected) return@withLock
        dao.delete(current.first)
        settings.clearFirstEvening()
    }

    private suspend fun upsert(s: Schedule) = dao.upsert(
        ScheduleEntity(s.id, s.protocolId, s.days, s.startTime.hour * 60 + s.startTime.minute, s.enabled),
    )

    suspend fun delete(id: Long) = changes.withLock {
        dao.delete(id)
        if (settings.firstEvening()?.first == id) settings.clearFirstEvening()
        recordCurrentPlan()
    }

    /** Establish a baseline on upgrade or restore, without inferring earlier schedules. */
    suspend fun refreshPlanHistory() = changes.withLock { recordCurrentPlan() }

    private suspend fun recordCurrentPlan() {
        val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
        val temporary = firstEvening()?.first
        settings.recordSchedule(today, PlanHistory.mask(getAll().filter { it.id != temporary }))
    }

    private fun ScheduleEntity.toDomain() = Schedule(id, protocolId, days, LocalTime.of(startMinute / 60, startMinute % 60), enabled)
}

data class SymptomCheckin(val id: Long, val fastId: Long, val at: Instant, val answers: Set<Symptom>, val redFlag: Boolean)

class SymptomCheckinRepository(private val dao: SymptomCheckinDao) {
    /** Saves a check-in and returns whether it contained a red flag (SR-4). */
    suspend fun record(fastId: Long, at: Instant, answers: Set<Symptom>): Boolean {
        val red = SymptomCheck.hasRedFlag(answers)
        dao.insert(SymptomCheckinEntity(0, fastId, at, answers, red))
        return red
    }

    fun observeForFast(fastId: Long): Flow<List<SymptomCheckin>> =
        dao.observeForFast(fastId).map { l -> l.map { SymptomCheckin(it.id, it.fastId, it.at, it.answers, it.redFlag) } }
}

class ProgramRepository(private val dao: ProgramProgressDao) {
    fun observeCompleted(programId: String): Flow<Map<Int, Instant>> =
        dao.observe(programId).map { l -> l.associate { it.day to it.completedAt } }

    suspend fun completeDay(programId: String, day: Int, at: Instant) = dao.insert(ProgramProgressEntity(programId, day, at))
}
