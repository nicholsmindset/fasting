package com.fastingshift.app.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.alarm.eligibleSchedules
import com.fastingshift.app.alarm.idleReminders
import com.fastingshift.app.R
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.health.HealthConnectManager
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.app.ui.components.latestKg
import com.fastingshift.app.ui.components.startOfTodayFlow
import com.fastingshift.domain.hydration.WaterGoal
import com.fastingshift.domain.service.StreakFreezeRepository
import com.fastingshift.domain.streak.StreakCalculator
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.billing.Feature
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.ElectrolyteLog
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.program.GuidedPrograms
import com.fastingshift.domain.program.ProgramProgress
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.StartResult
import com.fastingshift.domain.timer.FastTimer
import com.fastingshift.domain.timer.TimerDisplayMode
import com.fastingshift.domain.timer.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProtocolOption(val protocol: Protocol, val enabled: Boolean)

/** The eating window after the last fast, while it is still open. */
data class EatingWindow(val startedAt: Instant, val endsAt: Instant)

data class TimerUiState(
    val active: Fast? = null,
    val timer: TimerState? = null,
    val options: List<ProtocolOption> = emptyList(),
    val selected: Protocol = Presets.W16_8,
    val gate: GateResult? = null,
    val clinicianAcknowledged: Boolean = false,
    val milestones: List<Duration> = emptyList(),
    val maxHours: Int? = null,
    /** Today's guided-program target, if a program is active and the user has Shift+. */
    val programTargetHours: Int? = null,
    /** What the big ring shows; tapping it cycles. */
    val mode: TimerDisplayMode = TimerDisplayMode.ELAPSED,
    val now: Instant = Instant.EPOCH,
    val eatingWindow: EatingWindow? = null,
    /** "I started earlier": the chosen start, or null to start now. */
    val startAt: Instant? = null,
    val hideWeight: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** The "Today" card: next fast, water vs goal, streak. */
    val today: TodaySummary = TodaySummary(),
    val plannedStart: Instant? = null,
    val startReminder: Instant? = null,
)

private data class DailyInputs(
    val waterMl: Double,
    val latestKg: Double?,
    val schedules: List<com.fastingshift.domain.model.Schedule>,
    val frozen: Set<LocalDate>,
)

/** Where to send the user for refeeding guidance (SR-5). */
data class RefeedingNav(val tier: RefeedingTier, val fastId: Long)

/** One-off dialogs and messages. */
data class TimerEvents(
    val confirm: Set<StartRequirement>? = null,
    val blocked: BlockReason? = null,
    /** A fast just ended from this screen: offer Undo. */
    val ended: Fast? = null,
    val refeeding: RefeedingNav? = null,
    val invalidStart: Boolean = false,
    val message: Int? = null,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val fasts: FastRepository,
    screening: ScreeningRepository,
    private val settings: SettingsRepository,
    programs: ProgramRepository,
    billing: BillingManager,
    private val logs: LogRepository,
    private val health: HealthConnectManager,
    private val service: FastService,
    private val clock: Clock,
    schedules: ScheduleRepository,
    freezes: StreakFreezeRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Protocol?>(null)
    private val startAt = MutableStateFlow<Instant?>(null)
    private val _events = MutableStateFlow(TimerEvents())
    val events: StateFlow<TimerEvents> = _events

    /** Ticks once a second. Elapsed time is always derived from the stored start instant. */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(1_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val programTarget = settings.settings.flatMapLatest { s ->
        val program = s.activeProgramId?.let(GuidedPrograms::byId) ?: return@flatMapLatest flowOf<Int?>(null)
        combine(programs.observeCompleted(program.id), billing.entitlement) { done, ent ->
            if (!Feature.GUIDED_PROGRAM.isAvailable(ent)) null
            else ProgramProgress.currentDay(program, done, ZoneId.systemDefault(), LocalDate.now())?.targetHours
        }
    }

    private val allFasts = fasts.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val waterToday = startOfTodayFlow(clock).flatMapLatest { from -> logs.observeWaterSince(from) }

    /** Inputs of the Today card that don't tick every second. */
    private val daily = combine(waterToday, logs.observeWeights(), schedules.observeAll(), freezes.observeFrozenDays()) { w, weights, sch, frozen ->
        DailyInputs(w.sumOf(WaterGoal::toMl), weights.latestKg(), sch, frozen)
    }

    private val choices = combine(selected, startAt) { sel, at -> sel to at }

    private val base = combine(
        fasts.observeActive(), screening.observeLatest(), settings.settings, choices, ticker,
    ) { active, record, appSettings, (sel, at), now: Instant ->
        val gate = record?.gateResult
        val ack = record?.clinicianAcknowledged ?: false
        val max = FastPolicy.maxAllowed(gate, ack)
        val milestones = appSettings.reminders.milestones
        val chosen = sel ?: appSettings.defaultProtocolId?.let(Presets::byId) ?: Presets.W16_8
        TimerUiState(
            maxHours = max?.toHours()?.toInt(),
            active = active,
            timer = active?.let { FastTimer.state(it, now, milestones) },
            options = Presets.all.map { p -> ProtocolOption(p, max != null && Duration.ofHours(p.fastHours.toLong()) <= max) },
            selected = chosen,
            gate = gate,
            clinicianAcknowledged = ack,
            milestones = milestones,
            mode = appSettings.timerMode,
            now = now,
            startAt = at,
            hideWeight = appSettings.hideWeight,
            weightUnit = appSettings.weightUnit,
        ) to appSettings.eatingWindowHours
    }

    /** Null until the first values load, so nothing flashes (no "Ready when you are" mid-fast). */
    val state: StateFlow<TimerUiState?> = combine(base, programTarget, allFasts, daily, settings.settings) { (s, eatHours), target, all, d, appSettings ->
        val last = all.filter { !it.isActive }.maxByOrNull { it.endedAt!! }
        val window = if (s.active == null && last != null) {
            val ended = last.endedAt!!
            val hours = Presets.byId(last.protocolId)?.eatHours ?: eatHours
            val end = ended.plus(Duration.ofHours(hours.toLong()))
            EatingWindow(ended, end).takeIf { end.isAfter(s.now) }
        } else {
            null
        }
        val zone = ZoneId.systemDefault()
        val todayDate = s.now.atZone(zone).toLocalDate()
        val eligible = eligibleSchedules(d.schedules, s.maxHours?.let { Duration.ofHours(it.toLong()) })
        val plan = appSettings.planHistory
        val firstEvening = settings.firstEvening()
        val plannedStart = if (s.active == null) {
            ScheduleCalculator.nextStart(eligible, zone, s.now, plan.breakWeeks, plan.dayOverrides, firstEvening)?.second
        } else null
        // A start can be shown even when the user has muted its notification.
        val candidate = if (s.active == null && s.maxHours != null) {
            val withStart = appSettings.copy(reminders = appSettings.reminders.copy(fastStartEnabled = true))
            idleReminders(s.now, zone, withStart, eligible, last, firstEvening)
                .firstOrNull { it.kind == ReminderKind.FAST_START }?.triggerAt
        } else null
        val today = TodaySummary(
            next = when {
                s.active != null -> NextFast.FastingNow
                candidate != null -> NextFast.At(candidate)
                else -> NextFast.None
            },
            waterMl = d.waterMl,
            waterGoalMl = Today.waterGoalMl(appSettings.waterGoalMl, d.latestKg),
            streak = StreakCalculator.calculate(all, zone, todayDate, d.frozen, appSettings.planHistory::isPlanned).current,
        )
        s.copy(
            programTargetHours = target, eatingWindow = window, today = today,
            plannedStart = plannedStart, startReminder = candidate.takeIf { appSettings.reminders.fastStartEnabled },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // A long fast ended elsewhere (widget, notification): show its refeeding guidance once.
        viewModelScope.launch {
            settings.settings.map { it.pendingRefeedingFastId }.distinctUntilChanged().filterNotNull().collect { id ->
                settings.setPendingRefeeding(null)
                val tier = fasts.getById(id)?.actualDuration?.let(FastPolicy::refeedingTier)
                if (tier != null) _events.update { it.copy(refeeding = RefeedingNav(tier, id)) }
            }
        }
    }

    fun setMode(mode: TimerDisplayMode) {
        viewModelScope.launch { settings.setTimerMode(mode) }
    }

    fun select(protocol: Protocol) = selected.update { protocol }

    /** Custom fast of 1–120h; the policy still decides whether it can start. */
    fun selectCustom(hours: Int) = selected.update { Presets.custom(hours.coerceIn(Presets.CUSTOM_MIN_HOURS, Presets.CUSTOM_MAX_HOURS)) }

    /** "Started earlier?": null starts now. */
    fun setStartAt(at: Instant?) = startAt.update { at }

    fun start(confirmed: Set<StartRequirement> = emptySet()) {
        val protocol = state.value?.selected ?: return
        viewModelScope.launch {
            when (val result = service.start(protocol, confirmed, startAt.value ?: clock.instant())) {
                is StartResult.Started, is StartResult.AlreadyActive -> {
                    startAt.value = null
                    _events.update { TimerEvents() }
                }
                is StartResult.NeedsConfirmation -> _events.update { it.copy(confirm = result.requirements) }
                is StartResult.Blocked -> _events.update { it.copy(blocked = result.decision.reason) }
                StartResult.InvalidStartTime -> _events.update { it.copy(invalidStart = true) }
            }
        }
    }

    /** Confirms the checklist that was shown and starts the fast. */
    fun confirmAndStart() {
        val shown = _events.value.confirm ?: return
        _events.update { it.copy(confirm = null) }
        start(confirmed = shown)
    }

    fun end() {
        viewModelScope.launch {
            val result = service.endActive() ?: return@launch
            val tier = result.refeeding
            _events.update {
                if (tier != null) it.copy(refeeding = RefeedingNav(tier, result.fast.id)) else it.copy(ended = result.fast)
            }
        }
    }

    fun undoEnd(fast: Fast) {
        _events.update { it.copy(ended = null) }
        viewModelScope.launch {
            if (!service.undoEnd(fast)) _events.update { it.copy(message = R.string.timer_undo_failed) }
        }
    }

    fun addWater() = viewModelScope.launch {
        val log = WaterLog(at = clock.instant(), value = 250.0, unit = VolumeUnit.ML)
        val id = logs.addWater(log)
        if (settings.current().healthConnectSync) health.writeWater(log.copy(id = id))
        _events.update { it.copy(message = R.string.quick_water_added) }
    }

    /** Water from the Today card: no snackbar, the ring itself shows the change. */
    fun addWaterQuietly() = viewModelScope.launch {
        val log = WaterLog(at = clock.instant(), value = 250.0, unit = VolumeUnit.ML)
        val id = logs.addWater(log)
        if (settings.current().healthConnectSync) health.writeWater(log.copy(id = id))
    }

    fun addWeight(value: Double, unit: WeightUnit) = viewModelScope.launch {
        settings.setWeightUnit(unit)
        val log = WeightLog(at = clock.instant(), value = value, unit = unit)
        val id = logs.addWeight(log)
        if (settings.current().healthConnectSync) health.writeWeight(log.copy(id = id))
        _events.update { it.copy(message = R.string.quick_weight_added) }
    }

    fun addElectrolyte(e: Electrolyte, mg: Double) = viewModelScope.launch {
        logs.addElectrolyte(ElectrolyteLog(at = clock.instant(), electrolyte = e, milligrams = mg))
        _events.update { it.copy(message = R.string.quick_electrolyte_added) }
    }

    fun consumeEnded() = _events.update { it.copy(ended = null) }
    fun consumeRefeeding() = _events.update { it.copy(refeeding = null) }
    fun consumeMessage() = _events.update { it.copy(message = null) }
    fun dismissDialogs() = _events.update { it.copy(confirm = null, blocked = null, invalidStart = false) }
}
