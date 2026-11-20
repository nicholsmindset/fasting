package com.fastingshift.data.repo

import androidx.room.withTransaction
import com.fastingshift.data.db.ElectrolyteLogEntity
import com.fastingshift.data.db.FastEntity
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.db.ProgramProgressEntity
import com.fastingshift.data.db.ProtocolEntity
import com.fastingshift.data.db.ScheduleEntity
import com.fastingshift.data.db.ScreeningEntity
import com.fastingshift.data.db.StreakFreezeEntity
import com.fastingshift.data.db.SymptomCheckinEntity
import com.fastingshift.data.db.WaterLogEntity
import com.fastingshift.data.db.WeightLogEntity
import com.fastingshift.domain.backup.Backup
import com.fastingshift.domain.backup.BackupFormatException
import com.fastingshift.domain.backup.ElectrolyteRecord
import com.fastingshift.domain.backup.FastRecord
import com.fastingshift.domain.backup.ProgramProgressRecord
import com.fastingshift.domain.backup.ProtocolRecord
import com.fastingshift.domain.backup.ScheduleRecord
import com.fastingshift.domain.backup.ScreeningRecordDto
import com.fastingshift.domain.backup.SymptomCheckinRecord
import com.fastingshift.domain.backup.WaterRecord
import com.fastingshift.domain.backup.WeightRecord
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.model.RecordSource
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.Symptom
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Builds a full [Backup] from the database and restores one (PRD F8). */
class BackupRepository(private val db: FastingDatabase) {

    suspend fun export(now: Instant): Backup {
        val b = db.backupDao()
        return Backup(
            exportedAt = now.toEpochMilli(),
            fasts = db.fastDao().getAll().map {
                FastRecord(it.id, it.startAt.ms, it.targetEndAt.ms, it.endedAt?.ms, it.protocolId, it.status.name, it.note)
            },
            protocols = db.protocolDao().getAll().map { ProtocolRecord(it.id, it.name, it.type.name, it.fastHours, it.eatHours, it.isCustom) },
            schedules = db.scheduleDao().getAll().map { ScheduleRecord(it.id, it.protocolId, it.days.map { d -> d.name }.sorted(), it.startMinute, it.enabled) },
            weights = b.weights().map { WeightRecord(it.id, it.at.ms, it.value, it.unit.name, it.source.name) },
            water = b.water().map { WaterRecord(it.id, it.at.ms, it.value, it.unit.name, it.source.name) },
            electrolytes = b.electrolytes().map { ElectrolyteRecord(it.id, it.at.ms, it.electrolyte.name, it.value, it.source.name) },
            symptomCheckins = b.checkins().map { SymptomCheckinRecord(it.id, it.fastId, it.at.ms, it.answers.map { s -> s.name }.sorted(), it.redFlag) },
            screenings = b.screenings().map {
                ScreeningRecordDto(
                    it.answeredAt.ms, it.ageYears, it.pregnantOrBreastfeeding, it.eatingDisorderHistory, it.type1Diabetes,
                    it.medications.map { m -> m.name }.sorted(), it.heightCm, it.weightKg, it.gateResult.name, it.clinicianAcknowledged,
                )
            },
            programProgress = db.programProgressDao().getAll().map { ProgramProgressRecord(it.programId, it.day, it.completedAt.ms) },
            streakFreezes = db.streakFreezeDao().getAll().map { it.day.toString() }.sorted(),
        )
    }

    /**
     * Replaces all data with [backup] in one transaction. Mapping errors (unknown enum names,
     * bad dates) abort before anything is deleted.
     */
    suspend fun restore(backup: Backup) {
        val mapped = try {
            Mapped(backup)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("The backup contains values this version cannot read.", e)
        } catch (e: java.time.DateTimeException) {
            throw BackupFormatException("The backup contains an invalid date.", e)
        }
        db.withTransaction {
            val b = db.backupDao()
            b.clearCheckins(); b.clearFasts(); b.clearProtocols(); b.clearSchedules(); b.clearWeights()
            b.clearWater(); b.clearElectrolytes(); b.clearScreenings(); b.clearProgress(); b.clearFreezes()
            b.insertFasts(mapped.fasts)
            b.insertProtocols(mapped.protocols)
            b.insertSchedules(mapped.schedules)
            b.insertWeights(mapped.weights)
            b.insertWater(mapped.water)
            b.insertElectrolytes(mapped.electrolytes)
            b.insertCheckins(mapped.checkins)
            b.insertScreenings(mapped.screenings)
            b.insertProgress(mapped.progress)
            b.insertFreezes(mapped.freezes)
        }
    }

    private class Mapped(b: Backup) {
        val fasts = b.fasts.map { FastEntity(it.id, it.startAt.inst, it.targetEndAt.inst, it.endedAt?.inst, it.protocolId, FastStatus.valueOf(it.status), it.note) }
        val protocols = b.protocols.map { ProtocolEntity(it.id, it.name, ProtocolType.valueOf(it.type), it.fastHours, it.eatHours, it.isCustom) }
        val schedules = b.schedules.map { ScheduleEntity(it.id, it.protocolId, it.days.map(DayOfWeek::valueOf).toSet(), it.startMinute, it.enabled) }
        val weights = b.weights.map { WeightLogEntity(it.id, it.at.inst, it.value, WeightUnit.valueOf(it.unit), RecordSource.valueOf(it.source)) }
        val water = b.water.map { WaterLogEntity(it.id, it.at.inst, it.value, VolumeUnit.valueOf(it.unit), RecordSource.valueOf(it.source)) }
        val electrolytes = b.electrolytes.map { ElectrolyteLogEntity(it.id, it.at.inst, Electrolyte.valueOf(it.electrolyte), it.milligrams, RecordSource.valueOf(it.source)) }
        val checkins = b.symptomCheckins.map { SymptomCheckinEntity(it.id, it.fastId, it.at.inst, it.answers.map(Symptom::valueOf).toSet(), it.redFlag) }
        val screenings = b.screenings.map {
            ScreeningEntity(
                0, it.answeredAt.inst, it.ageYears, it.pregnantOrBreastfeeding, it.eatingDisorderHistory, it.type1Diabetes,
                it.medications.map(Medication::valueOf).toSet(), it.heightCm, it.weightKg, GateResult.valueOf(it.gateResult), it.clinicianAcknowledged,
            )
        }
        val progress = b.programProgress.map { ProgramProgressEntity(it.programId, it.day, it.completedAt.inst) }
        val freezes = b.streakFreezes.map { StreakFreezeEntity(LocalDate.parse(it)) }
    }
}

private val Instant.ms: Long get() = toEpochMilli()
private val Long.inst: Instant get() = Instant.ofEpochMilli(this)
