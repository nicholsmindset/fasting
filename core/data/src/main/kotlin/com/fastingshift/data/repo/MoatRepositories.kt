package com.fastingshift.data.repo

import com.fastingshift.data.db.ChallengeDao
import com.fastingshift.data.db.MetricDao
import com.fastingshift.data.db.QadaDao
import com.fastingshift.domain.model.Challenge
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.QadaDebt
import com.fastingshift.data.db.QadaRepaymentEntity
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Glucose and ketone readings. */
class MetricRepository(private val dao: MetricDao) {
    fun observeAll(): Flow<List<MetricLog>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun since(from: Instant): List<MetricLog> = dao.since(from.toEpochMilli()).map { it.toDomain() }
    suspend fun get(id: Long): MetricLog? = dao.get(id)?.toDomain()
    suspend fun add(log: MetricLog): Long = dao.insert(log.copy(id = 0).toEntity())
    suspend fun delete(id: Long) = dao.delete(id)
}

data class QadaYear(val debt: QadaDebt, val repaidDates: List<LocalDate>) {
    val remaining: Int get() = debt.daysOwed - repaidDates.size
}

/** Ramadan days owed, per Hijri year. Setting 0 days removes the year. */
class QadaRepository(private val dao: QadaDao) {
    fun observeDebts(): Flow<List<QadaDebt>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun getDebts(): List<QadaDebt> = dao.getAll().map { it.toDomain() }
    suspend fun setOwed(debt: QadaDebt): Boolean = dao.setDebt(debt.toEntity())

    fun observeLedger(): Flow<List<QadaYear>> = dao.observeLedger().map { rows ->
        rows.map { QadaYear(it.debt.toDomain(), it.repayments.map { day -> day.observedOn }.sortedDescending()) }
    }

    suspend fun record(day: LocalDate, year: Int): Boolean = dao.saveRepayment(null, QadaRepaymentEntity(day, year))

    suspend fun correct(oldDay: LocalDate, oldYear: Int, day: LocalDate, year: Int): Boolean =
        dao.saveRepayment(QadaRepaymentEntity(oldDay, oldYear), QadaRepaymentEntity(day, year))

    suspend fun undo(day: LocalDate, year: Int): Boolean = dao.removeRepayment(day, year) == 1
}

/** Challenges joined on this device. */
class ChallengeRepository(private val dao: ChallengeDao) {
    fun observeAll(): Flow<List<Challenge>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun get(id: Long): Challenge? = dao.get(id)?.toDomain()
    suspend fun join(challenge: Challenge): Long = dao.insert(challenge.copy(id = 0).toEntity())
    suspend fun update(challenge: Challenge) = dao.update(challenge.toEntity())
    suspend fun delete(id: Long) = dao.delete(id)
}
