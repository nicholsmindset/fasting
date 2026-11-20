package com.fastingshift.app.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.data.settings.SettingsRepository
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
    settings: SettingsRepository,
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

    val state: StateFlow<TimerUiState> = combine(
        fasts.observeActive(), screening.observeLatest(), settings.settings, selected, ticker,
    ) { active, record, appSettings, sel, now: Instant ->
        val gate = record?.gateResult
        val ack = record?.clinicianAcknowledged ?: false
        val max = FastPolicy.maxAllowed(gate, ack)
        val milestones = appSettings.reminders.milestones
        TimerUiState(
            active = active,
            timer = active?.let { FastTimer.state(it, now, milestones) },
            options = Presets.all.map { p -> ProtocolOption(p, max != null && Duration.ofHours(p.fastHours.toLong()) <= max) },
            selected = sel,
            gate = gate,
            clinicianAcknowledged = ack,
            milestones = milestones,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUiState())

    fun select(protocol: Protocol) = selected.update { protocol }

    fun start(confirmed: Set<StartRequirement> = emptySet()) {
        viewModelScope.launch {
            when (val result = service.start(selected.value, confirmed)) {
                is StartResult.Started, is StartResult.AlreadyActive -> _events.update { TimerEvents() }
                is StartResult.NeedsConfirmation -> _events.update { it.copy(confirm = result.requirements) }
                is StartResult.Blocked -> _events.update { it.copy(blocked = result.decision.reason) }
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
            val ended = service.endActive() ?: return@launch
            val tier = ended.actualDuration?.let(FastPolicy::refeedingTier)
            _events.update { it.copy(ended = ended, refeeding = tier) }
        }
    }

    fun dismissEvents() = _events.update { TimerEvents() }
}
