package com.fastingshift.data.repo

import com.fastingshift.data.db.FastEntity
import com.fastingshift.data.db.ScreeningEntity
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord

internal fun FastEntity.toDomain() = Fast(id, startAt, targetEndAt, endedAt, protocolId, status, note, mood)

internal fun Fast.toEntity() = FastEntity(id, startAt, targetEndAt, endedAt, protocolId, status, note, mood)

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
