package com.fastingshift.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.alarm.eligibleSchedules
import com.fastingshift.app.ui.onboarding.recommendedProtocol
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal val weekdays = DayOfWeek.entries.take(5).toSet()

internal data class PlannerState(
    val schedules: List<Schedule>,
    val settings: AppSettings,
    val maxDuration: Duration?,
    val now: Instant,
    val zone: ZoneId,
    val firstEvening: Pair<Long, LocalDate>?,
    val active: Boolean,
) {
    val today: LocalDate = now.atZone(zone).toLocalDate()
    val routines = schedules.filter { it.id != firstEvening?.first }
    val eligible = eligibleSchedules(schedules, maxDuration)
    val dailyPlan: Protocol? = listOfNotNull(
        settings.defaultProtocolId?.let(Presets::byId), recommendedProtocol(settings.goal), Presets.W12_12,
    ).firstOrNull { it.type == ProtocolType.WINDOW && allows(it.id) }

    fun allows(id: String): Boolean {
        val duration = Presets.byId(id)?.let { Duration.ofHours(it.fastHours.toLong()) } ?: return false
        return maxDuration != null && duration <= maxDuration
    }

    fun starts(day: LocalDate, original: Boolean = false): List<Pair<Schedule, Instant>> =
        ScheduleCalculator.startsOn(eligible, day, zone, settings.planHistory.breakWeeks,
            if (original) emptyMap() else settings.planHistory.dayOverrides, firstEvening)

    fun next() = ScheduleCalculator.nextStart(eligible, zone, now, settings.planHistory.breakWeeks,
        settings.planHistory.dayOverrides, firstEvening)
}

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val repo: ScheduleRepository,
    private val settings: SettingsRepository,
    private val screening: ScreeningRepository,
    fasts: FastRepository,
    private val clock: Clock,
) : ViewModel() {
    private val tick = flow {
        while (true) {
            emit(clock.instant())
            delay(30_000)
        }
    }
    internal val state = combine(repo.observeAll(), settings.settings, screening.observeLatest(), fasts.observeActive(), tick) {
        schedules, config, record, active, now ->
        PlannerState(schedules, config, FastPolicy.maxAllowed(record?.gateResult, record?.clinicianAcknowledged == true),
            now, ZoneId.systemDefault(), repo.firstEvening(), active != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    internal val busy = MutableStateFlow(false)
    internal val failed = MutableStateFlow(false)

    private fun write(done: () -> Unit, action: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                action()
                done()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed.value = true
            } finally {
                busy.value = false
            }
        }
    }

    internal fun save(plan: Schedule, done: () -> Unit) = write(done) {
        val record = screening.getLatest()
        val max = FastPolicy.maxAllowed(record?.gateResult, record?.clinicianAcknowledged == true)
        require(plan.days.isNotEmpty() && eligibleSchedules(listOf(plan.copy(enabled = true)), max).isNotEmpty())
        require(plan.id == 0L || (plan.id != repo.firstEvening()?.first && repo.getAll().any { it.id == plan.id }))
        repo.save(plan)
    }

    internal fun delete(id: Long, done: () -> Unit) = write(done) {
        require(id != repo.firstEvening()?.first)
        repo.delete(id)
    }

    internal fun changeDate(day: LocalDate, override: DayOverride?, done: () -> Unit) = write(done) {
        val zone = ZoneId.systemDefault()
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        require(!day.isBefore(today))
        if (override != null) {
            val record = screening.getLatest()
            val max = FastPolicy.maxAllowed(record?.gateResult, record?.clinicianAcknowledged == true)
            val config = settings.current()
            val base = ScheduleCalculator.startsOn(eligibleSchedules(repo.getAll(), max), day, zone,
                config.planHistory.breakWeeks, emptyMap(), repo.firstEvening())
            require(base.isNotEmpty())
            override.startTime?.let { require(day.atTime(it).atZone(zone).toInstant().isAfter(now)) }
        }
        settings.setDayOverride(day, override, today)
    }
}
