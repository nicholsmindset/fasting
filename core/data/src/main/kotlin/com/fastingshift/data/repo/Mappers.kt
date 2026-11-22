package com.fastingshift.data.repo

import com.fastingshift.data.db.ChallengeEntity
import com.fastingshift.data.db.FastEntity
import com.fastingshift.data.db.MetricLogEntity
import com.fastingshift.data.db.QadaDebtEntity
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.QadaDebt
import com.fastingshift.data.db.ScreeningEntity
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord

internal fun FastEntity.toDomain() = Fast(id, startAt, targetEndAt, endedAt, protocolId, status, note, mood, kind)

internal fun Fast.toEntity() = FastEntity(id, startAt, targetEndAt, endedAt, protocolId, status, note, mood, kind)

internal fun ScreeningEntity.toDomain() = ScreeningRecord(
    answeredAt = answeredAt,
    answers = ScreeningAnswers(ageYears, pregnantOrBreastfeeding, eatingDisorderHistory, type1Diabetes, medications, heightCm, weightKg),
    gateResult = gateResult,
    clinicianAcknowledged = clinicianAcknowledged,
)

internal fun ScreeningRecord.toEntity() = ScreeningEntity(
    answeredAt = answeredAt,
    ageYears = answers.ageYears,
    pregnantOrBreastfeeding = answers.pregnantOrBreastfeeding,
    eatingDisorderHistory = answers.eatingDisorderHistory,
    type1Diabetes = answers.type1Diabetes,
    medications = answers.medications,
    heightCm = answers.heightCm,
    weightKg = answers.weightKg,
    gateResult = gateResult,
    clinicianAcknowledged = clinicianAcknowledged,
)

internal fun MetricLogEntity.toDomain() = MetricLog(id, at, type, value, unit, source, note)
internal fun MetricLog.toEntity() = MetricLogEntity(id, at, type, value, unit, source, note)
internal fun QadaDebtEntity.toDomain() = QadaDebt(hijriYear, daysOwed)
internal fun QadaDebt.toEntity() = QadaDebtEntity(hijriYear, daysOwed)
internal fun ChallengeEntity.toDomain() = Challenge(id, type, protocolId, startDate, lengthDays, name, joinedAt, leftAt)
internal fun Challenge.toEntity() = ChallengeEntity(id, type, protocolId, startDate, lengthDays, name, joinedAt, leftAt)
