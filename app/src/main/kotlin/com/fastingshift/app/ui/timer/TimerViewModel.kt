package com.fastingshift.app.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.billing.Feature
import com.fastingshift.domain.program.GuidedPrograms
import com.fastingshift.domain.program.ProgramProgress
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.protocol.Presets
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
import com.fastingshift.domain.timer.TimerState
import com.fastingshift.domain.timer.TimerDisplayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProtocolOption(val protocol: Protocol, val enabled: Boolean)

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
)

/** One-off dialogs and messages. */
data class TimerEvents(
    val confirm: Set<StartRequirement>? = null,
    val blocked: BlockReason? = null,
    val ended: Fast? = null,
    val refeeding: RefeedingTier? = null,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    fasts: FastRepository,
    screening: ScreeningRepository,
    private val settings: SettingsRepository,
    programs: ProgramRepository,
    billing: BillingManager,
    private val service: FastService,
    private val clock: Clock,
) : ViewModel() {

    private val selected = MutableStateFlow(Presets.W16_8)
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

    private val base = combine(
        fasts.observeActive(), screening.observeLatest(), settings.settings, selected, ticker,
    ) { active, record, appSettings, sel, now: Instant ->
        val gate = record?.gateResult
        val ack = record?.clinicianAcknowledged ?: false
        val max = FastPolicy.maxAllowed(gate, ack)
        val milestones = appSettings.reminders.milestones
        TimerUiState(
            maxHours = max?.toHours()?.toInt(),
            active = active,
            timer = active?.let { FastTimer.state(it, now, milestones) },
            options = Presets.all.map { p -> ProtocolOption(p, max != null && Duration.ofHours(p.fastHours.toLong()) <= max) },
            selected = sel,
            gate = gate,
            clinicianAcknowledged = ack,
            milestones = milestones,
            mode = appSettings.timerMode,
            now = now,
        )
    }

    val state: StateFlow<TimerUiState> = combine(base, programTarget) { s, target -> s.copy(programTargetHours = target) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    /** Tapping the ring cycles the view; the choice is remembered across launches. */
    fun cycleMode() {
        val next = state.value.mode.next()
        viewModelScope.launch { settings.setTimerMode(next) }
    }

    fun select(protocol: Protocol) = selected.update { protocol }

    /** Custom fast of 1–120h; the policy still decides whether it can start. */
    fun selectCustom(hours: Int) = selected.update { Presets.custom(hours.coerceIn(Presets.CUSTOM_MIN_HOURS, Presets.CUSTOM_MAX_HOURS)) }

    fun start(confirmed: Set<StartRequirement> = emptySet()) {
        viewModelScope.launch {
            when (val result = service.start(selected.value, confirmed)) {
                is StartResult.Started, is StartResult.AlreadyActive -> _events.update { TimerEvents() }
                is StartResult.NeedsConfirmation -> _events.update { it.copy(confirm = result.requirements) }
                is StartResult.Blocked -> _events.update { it.copy(blocked = result.decision.reason) }
                StartResult.InvalidStartTime -> _events.update { TimerEvents() }
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
            _events.update { it.copy(ended = result.fast, refeeding = result.refeeding) }
        }
    }

    fun dismissEvents() = _events.update { TimerEvents() }
}
