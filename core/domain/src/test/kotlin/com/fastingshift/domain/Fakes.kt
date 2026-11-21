package com.fastingshift.domain

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFastRepository : FastRepository {
    private val state = MutableStateFlow<Map<Long, Fast>>(emptyMap())
    private var nextId = 1L

    override fun observeActive(): Flow<Fast?> = state.map { m -> m.values.firstOrNull { it.isActive } }
    override fun observeAll(): Flow<List<Fast>> = state.map { it.values.sortedByDescending(Fast::startAt) }
    override suspend fun getActive(): Fast? = state.value.values.firstOrNull { it.isActive }
    override suspend fun getById(id: Long): Fast? = state.value[id]
    override suspend fun getAll(): List<Fast> = state.value.values.toList()
    override suspend fun insert(fast: Fast): Fast {
        val saved = fast.copy(id = nextId++)
        state.value = state.value + (saved.id to saved)
        return saved
    }
    override suspend fun insertIfNoActive(fast: Fast): Fast? = if (getActive() != null) null else insert(fast)
    override suspend fun update(fast: Fast) { state.value = state.value + (fast.id to fast) }
    override suspend fun delete(id: Long) { state.value = state.value - id }
}

class FakeScreeningRepository(var record: ScreeningRecord? = null) : ScreeningRepository {
    override fun observeLatest(): Flow<ScreeningRecord?> = MutableStateFlow(record)
    override suspend fun getLatest(): ScreeningRecord? = record
    override suspend fun save(record: ScreeningRecord) { this.record = record }
}

/** A settable clock for tests. */
class MutableClock(var now: Instant, private var zoneId: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

fun hours(h: Long): Duration = Duration.ofHours(h)
