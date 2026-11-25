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
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import com.fastingshift.domain.model.QadaDebt
import com.fastingshift.domain.backup.ChallengeRecord
import com.fastingshift.domain.backup.MetricRecord
import com.fastingshift.data.db.QadaRepaymentEntity
import com.fastingshift.domain.backup.QadaRepaymentRecord
import com.fastingshift.domain.backup.QadaDebtRecord
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.model.RecordSource
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.safety.SymptomCheck
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.safety.ScreeningEvaluator
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.StartDecision
import com.fastingshift.data.db.RevivalEventEntity
import com.fastingshift.domain.backup.RevivalRecord
import java.time.ZoneId
import java.time.Duration
import com.fastingshift.data.settings.SettingsRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Builds a full [Backup] from the database and restores one (PRD F8). */
class BackupRepository(private val db: FastingDatabase, private val settings: SettingsRepository? = null) {

    suspend fun export(now: Instant): Backup {
        val b = db.backupDao()
        val ledger = db.qadaDao().ledgerSnapshot()
        val firstEveningId = settings?.firstEvening()?.first
        return Backup(
            exportedAt = now.toEpochMilli(),
            fasts = db.fastDao().getAll().map {
                FastRecord(it.id, it.startAt.ms, it.targetEndAt.ms, it.endedAt?.ms, it.protocolId, it.status.name, it.note, it.mood, it.kind.name)
            },
            protocols = db.protocolDao().getAll().map { ProtocolRecord(it.id, it.name, it.type.name, it.fastHours, it.eatHours, it.isCustom) },
            schedules = db.scheduleDao().getAll().filterNot { it.id == firstEveningId }.map { ScheduleRecord(it.id, it.protocolId, it.days.map { d -> d.name }.sorted(), it.startMinute, it.enabled) },
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
            settings = settings?.toRecord(),
            qadaDebts = ledger.map { QadaDebtRecord(it.debt.hijriYear, it.debt.daysOwed) },
            qadaRepayments = ledger.flatMap { it.repayments }.sortedBy { it.observedOn }
                .map { QadaRepaymentRecord(it.observedOn.toString(), it.hijriYear) },
            metrics = b.metrics().map { MetricRecord(it.id, it.at.ms, it.type.name, it.value, it.unit.name, it.source.name, it.note) },
            revivalEvents = db.revivalDao().all().filter { it.joined != null || it.dismissed || it.notified }.map {
                RevivalRecord(it.id, it.starts, it.ends, it.zone, it.target, it.threshold, it.joined, it.baselineBest,
                    it.restored, it.awardDay, it.restoredDays, it.dismissed, it.notified)
            },
            challenges = b.challenges().map {
                ChallengeRecord(it.id, it.type.name, it.protocolId, it.startDate.toString(), it.lengthDays, it.name, it.joinedAt.ms, it.leftAt?.ms, it.followsOwnPlan)
            },
        )
    }

    /**
     * Replaces all data with [backup] in one transaction. Mapping errors (unknown enum names,
     * bad dates) abort before anything is deleted.
     */
    suspend fun restore(backup: Backup, restoredAt: Instant = Instant.now()) {
        val mapped = try {
            Mapped(backup, restoredAt)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("The backup contains values this version cannot read.", e)
        } catch (e: IllegalStateException) {
            throw BackupFormatException("The backup contains values this version cannot read.", e)
        } catch (e: java.time.DateTimeException) {
            throw BackupFormatException("The backup contains an invalid date.", e)
        }
        db.withTransaction {
            val b = db.backupDao()
            b.clearCheckins(); b.clearFasts(); b.clearProtocols(); b.clearSchedules(); b.clearWeights()
            b.clearWater(); b.clearElectrolytes(); b.clearScreenings(); b.clearProgress(); b.clearFreezes()
            b.clearMetrics(); b.clearRepayments(); b.clearQada(); b.clearChallenges()
            b.insertFasts(mapped.fasts)
            b.insertProtocols(mapped.protocols)
            b.insertSchedules(mapped.schedules)
            b.insertWeights(mapped.weights)
            b.insertWater(mapped.water)
            b.insertElectrolytes(mapped.electrolytes)
            b.insertCheckins(mapped.checkins)
            b.insertScreenings(mapped.screeningEntities)
            b.insertProgress(mapped.progress)
            b.insertFreezes(mapped.freezes)
            b.insertMetrics(mapped.metrics)
            b.insertQada(mapped.qada)
            b.insertRepayments(mapped.repayments)
            b.insertChallenges(mapped.challenges)
            db.revivalDao().clear()
            db.revivalDao().seed(mapped.revivals)
        }
        settings?.clearFirstEvening()
        backup.settings?.let { settings?.restore(it) }
        if (backup.settings == null) settings?.clearPlanHistory()
        // Fast ids may now point at different fasts: never show a stale refeeding prompt.
        settings?.setPendingRefeeding(null)
    }

    /**
     * Every row is rebuilt through the domain model first, so anything the app could not read
     * back (an ACTIVE fast with an end time, an impossible age, a bad schedule time) aborts the
     * restore here instead of crashing the app later. Safety results are recomputed, never
     * trusted from the file: the screening gate (SR-1/SR-2) and check-in red flags (SR-4).
     */
    private class Mapped(b: Backup, restoredAt: Instant) {
        val screenings = b.screenings.map {
            val answers = ScreeningAnswers(
                it.ageYears, it.pregnantOrBreastfeeding, it.eatingDisorderHistory, it.type1Diabetes,
                it.medications.map(Medication::valueOf).toSet(), it.heightCm, it.weightKg,
            )
            val gate = ScreeningEvaluator.evaluate(answers).result
            // A clinician acknowledgement only carries over if the gate it was given for still applies.
            val ack = it.clinicianAcknowledged && gate == GateResult.SOFT && GateResult.valueOf(it.gateResult) == GateResult.SOFT
            ScreeningRecord(it.answeredAt.inst, answers, gate, ack)
        }

        /**
         * An active fast comes back only if it is still allowed: not older than the longest
         * fast at either export or restore time, and permitted by the restored screening. Otherwise it is ended
         * (at its goal, or at the export time if earlier) so a restore can't resume an unsafe fast.
         */
        val fasts = b.fasts.map {
            val f = Fast(it.id, it.startAt.inst, it.targetEndAt.inst, it.endedAt?.inst, it.protocolId, FastStatus.valueOf(it.status), it.note, it.mood, FastKind.valueOf(it.kind))
            if (!f.isActive) return@map f.toEntity()
            val latest = screenings.maxByOrNull { s -> s.answeredAt }
            val decision = FastPolicy.evaluateStart(latest?.gateResult, latest?.clinicianAcknowledged ?: false, f.plannedDuration)
            val exportedAt = b.exportedAt.inst
            require(!f.startAt.isAfter(restoredAt)) { "Active fast starts after the restore time." }
            val tooOld = Duration.between(f.startAt, maxOf(exportedAt, restoredAt)) > FastPolicy.MAX_FAST
            if (decision is StartDecision.Allowed && !tooOld) return@map f.toEntity()
            val endAt = maxOf(f.startAt, minOf(f.targetEndAt, exportedAt, restoredAt))
            f.end(endAt).toEntity()
        }

        val protocols = b.protocols.map {
            require(it.fastHours in 1..120) { "bad protocol length" }
            ProtocolEntity(it.id, it.name, ProtocolType.valueOf(it.type), it.fastHours, it.eatHours, it.isCustom)
        }
        val schedules = b.schedules.map {
            require(it.startMinute in 0 until 24 * 60) { "bad schedule time" }
            ScheduleEntity(it.id, it.protocolId, it.days.map(DayOfWeek::valueOf).toSet(), it.startMinute, it.enabled)
        }
        val weights = b.weights.map { WeightLogEntity(it.id, it.at.inst, positive(it.value), WeightUnit.valueOf(it.unit), RecordSource.valueOf(it.source)) }
        val water = b.water.map { WaterLogEntity(it.id, it.at.inst, positive(it.value), VolumeUnit.valueOf(it.unit), RecordSource.valueOf(it.source)) }
        val electrolytes = b.electrolytes.map { ElectrolyteLogEntity(it.id, it.at.inst, Electrolyte.valueOf(it.electrolyte), positive(it.milligrams), RecordSource.valueOf(it.source)) }
        val checkins = b.symptomCheckins.map {
            val answers = it.answers.map(Symptom::valueOf).toSet()
            SymptomCheckinEntity(it.id, it.fastId, it.at.inst, answers, SymptomCheck.hasRedFlag(answers))
        }
        val screeningEntities = screenings.map { it.toEntity() }
        val progress = b.programProgress.map {
            require(it.day in 1..366) { "bad program day" }
            ProgramProgressEntity(it.programId, it.day, it.completedAt.inst)
        }
        val freezes = b.streakFreezes.map { StreakFreezeEntity(LocalDate.parse(it)) }
        val metrics = b.metrics.map {
            MetricLog(it.id, it.at.inst, MetricType.valueOf(it.type), it.value, MetricUnit.valueOf(it.unit), RecordSource.valueOf(it.source), it.note).toEntity()
        }
        val qada = b.qadaDebts.filter { it.daysOwed > 0 }.map { QadaDebt(it.hijriYear, it.daysOwed).toEntity() }
        val repayments = b.qadaRepayments.map { QadaRepaymentEntity(LocalDate.parse(it.observedOn), it.hijriYear) }
        init {
            val debtsByYear = qada.associateBy { it.hijriYear }
            require(debtsByYear.size == qada.size)
            require(repayments.distinctBy { it.observedOn }.size == repayments.size)
            repayments.groupingBy { it.hijriYear }.eachCount().forEach { (year, count) ->
                require(count <= (debtsByYear[year]?.daysOwed ?: 0)) { "Qada repayments exceed the recorded days for the year." }
            }
        }

        val revivals = b.revivalEvents.map {
            require(it.id.isNotBlank() && it.target.isNotBlank() && it.starts < it.ends && it.threshold in 1..366)
            ZoneId.of(it.zone)
            require((it.joined == null) == (it.baselineBest == null))
            require(it.joined == null || (requireNotNull(it.joined) in it.starts until it.ends && (it.baselineBest ?: -1) >= 0))
            require((it.restored == null) == (it.awardDay == null) && (it.restored == null) == (it.restoredDays == null))
            if (it.restored != null) {
                require(it.joined != null && requireNotNull(it.restored) in requireNotNull(it.joined) until it.ends && (it.restoredDays ?: 0) >= it.threshold)
                require(LocalDate.ofEpochDay(requireNotNull(it.awardDay)) == requireNotNull(it.restored).inst.atZone(ZoneId.of(it.zone)).toLocalDate())
            }
            RevivalEventEntity(it.id, it.starts, it.ends, it.zone, it.target, it.threshold, it.joined, it.baselineBest,
                it.restored, it.awardDay, it.restoredDays, it.dismissed, it.notified)
        }
        val challenges = b.challenges.map {
            Challenge(it.id, ChallengeType.valueOf(it.type), it.protocolId, LocalDate.parse(it.startDate), it.lengthDays, it.name, it.joinedAt.inst, it.leftAt?.inst, it.followsOwnPlan).toEntity()
        }

        private fun positive(v: Double): Double {
            require(v.isFinite() && v > 0) { "values must be positive" }
            return v
        }
    }
}

private val Instant.ms: Long get() = toEpochMilli()
private val Long.inst: Instant get() = Instant.ofEpochMilli(this)
