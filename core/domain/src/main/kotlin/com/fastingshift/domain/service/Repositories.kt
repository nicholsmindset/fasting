package com.fastingshift.domain.service

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Single source of truth for fasts. Implemented by the Room-backed data layer. */
interface FastRepository {
    fun observeActive(): Flow<Fast?>
    fun observeAll(): Flow<List<Fast>>
    suspend fun getActive(): Fast?
    suspend fun getById(id: Long): Fast?
    suspend fun getAll(): List<Fast>

    /** Inserts a new fast and returns it with its id. */
    suspend fun insert(fast: Fast): Fast
    suspend fun update(fast: Fast)
    suspend fun delete(id: Long)
}

data class ScreeningRecord(
    val answeredAt: Instant,
    val answers: ScreeningAnswers,
    val gateResult: GateResult,
    val clinicianAcknowledged: Boolean,
)

interface ScreeningRepository {
    fun observeLatest(): Flow<ScreeningRecord?>
    suspend fun getLatest(): ScreeningRecord?
    suspend fun save(record: ScreeningRecord)
}

interface StreakFreezeRepository {
    fun observeFrozenDays(): Flow<Set<LocalDate>>
    suspend fun getFrozenDays(): Set<LocalDate>
    suspend fun addFrozenDay(day: LocalDate)
}
