package com.fastingshift.data.repo

import com.fastingshift.data.db.FastDao
import com.fastingshift.data.db.ScreeningDao
import com.fastingshift.data.db.StreakFreezeDao
import com.fastingshift.data.db.StreakFreezeEntity
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.StreakFreezeRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomFastRepository(private val dao: FastDao) : FastRepository {
    override fun observeActive(): Flow<Fast?> = dao.observeActive().map { it?.toDomain() }.distinctUntilChanged()
    override fun observeAll(): Flow<List<Fast>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getActive(): Fast? = dao.getActive()?.toDomain()
    override suspend fun getById(id: Long): Fast? = dao.getById(id)?.toDomain()
    override suspend fun getAll(): List<Fast> = dao.getAll().map { it.toDomain() }
    override suspend fun insert(fast: Fast): Fast {
        val id = dao.insert(fast.toEntity().copy(id = 0))
        return fast.copy(id = id)
    }
    override suspend fun update(fast: Fast) = dao.update(fast.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomScreeningRepository(private val dao: ScreeningDao) : ScreeningRepository {
    override fun observeLatest(): Flow<ScreeningRecord?> = dao.observeLatest().map { it?.toDomain() }
    override suspend fun getLatest(): ScreeningRecord? = dao.getLatest()?.toDomain()
    override suspend fun save(record: ScreeningRecord) { dao.insert(record.toEntity()) }
}

class RoomStreakFreezeRepository(private val dao: StreakFreezeDao) : StreakFreezeRepository {
    override fun observeFrozenDays(): Flow<Set<LocalDate>> = dao.observeAll().map { list -> list.map { it.day }.toSet() }
    override suspend fun getFrozenDays(): Set<LocalDate> = dao.getAll().map { it.day }.toSet()
    override suspend fun addFrozenDay(day: LocalDate) = dao.insert(StreakFreezeEntity(day))
}
