package com.fastingshift.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.alarm.idleReminders
import com.fastingshift.app.alarm.eligibleSchedules
import com.fastingshift.app.ui.onboarding.recommendedProtocol
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.ReminderKind
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
import kotlinx.coroutines.flow.map
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
    val lastFast: Fast?,
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

    fun preview(day: LocalDate, replacement: DayOverride?): DateChangePreview {
        val draft = copy(settings = settings.copy(planHistory = settings.planHistory.withDayOverride(day, replacement)))
        val original = starts(day, original = true).firstOrNull()
        val planned = draft.starts(day).firstOrNull { it.first.id == original?.first?.id }
        val start = planned?.second
        val finish = planned?.let { (schedule, instant) ->
            Presets.byId(schedule.protocolId)?.let { instant.plus(Duration.ofHours(it.fastHours.toLong())) }
        }
        return DateChangePreview(start, finish, replacement?.isSkipped == true,
            previousAlert = if (active) null else nextStartAlert(),
            nextAlert = if (active) null else draft.nextStartAlert())
    }

    fun nextStartAlert(): Instant? = idleReminders(now, zone, settings, eligible, lastFast, firstEvening)
        .firstOrNull { it.kind == ReminderKind.FAST_START }?.triggerAt
}

internal data class DateChangePreview(
    val start: Instant?,
    val finish: Instant?,
    val skipped: Boolean,
    val previousAlert: Instant?,
    val nextAlert: Instant?,
)

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
    private val recentFasts = fasts.observeAll().map { all -> all.any { it.isActive } to all.firstOrNull { !it.isActive } }
    internal val state = combine(repo.observeAll(), settings.settings, screening.observeLatest(), recentFasts, tick) {
        schedules, config, record, recent, now ->
        PlannerState(schedules, config, FastPolicy.maxAllowed(record?.gateResult, record?.clinicianAcknowledged == true),
            now, ZoneId.systemDefault(), repo.firstEvening(), recent.first, recent.second)
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

    internal fun changeDate(day: LocalDate, previous: DayOverride?, override: DayOverride?, done: () -> Unit) = write(done) {
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
        settings.replaceDayOverride(day, previous, override, today)
    }

    internal fun undoDate(day: LocalDate, saved: DayOverride?, previous: DayOverride?) = write({}) {
        settings.replaceDayOverride(day, saved, previous, clock.instant().atZone(ZoneId.systemDefault()).toLocalDate())
    }
}
