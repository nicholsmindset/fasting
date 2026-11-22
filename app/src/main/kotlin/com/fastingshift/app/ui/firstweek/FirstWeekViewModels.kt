package com.fastingshift.app.ui.firstweek

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.hydration.WaterGoal
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Emits now, then every minute: tips and the undo window follow the clock. */
internal fun minuteTicker(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(60_000)
    }
}

data class FirstFastUi(val protocol: Protocol, val planned: PlannedFirstFast?)

@HiltViewModel
class FirstFastViewModel @Inject constructor(
    fasts: FastRepository,
    screening: ScreeningRepository,
    settings: SettingsRepository,
    private val planner: FirstFastPlanner,
) : ViewModel() {
    private val planned = MutableStateFlow(planner.planned())

    /** Null hides the card. */
    val state: StateFlow<FirstFastUi?> = combine(fasts.observeAll(), screening.observeLatest(), settings.settings, planned) { all, scr, s, plan ->
        val protocol = s.defaultProtocolId?.let(Presets::byId) ?: Presets.W16_8
        if (FirstWeekRules.showFirstFastCard(all.isNotEmpty(), scr?.gateResult, scr?.clinicianAcknowledged ?: false, protocol)) {
            FirstFastUi(protocol, plan)
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            planner.cleanUp()
            planned.value = planner.planned()
        }
    }

    fun planTonight() {
        val protocol = state.value?.protocol ?: return
        viewModelScope.launch { planned.value = planner.planTonight(protocol) }
    }

    fun cancelPlan() {
        viewModelScope.launch {
            planner.cancel()
            planned.value = null
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CoachingViewModel @Inject constructor(
    fasts: FastRepository,
    private val settings: SettingsRepository,
    planner: FirstFastPlanner,
    clock: Clock,
) : ViewModel() {
    private val fast = MutableStateFlow<Fast?>(null)

    val tip: StateFlow<CoachingTip?> = combine(fast.filterNotNull(), fasts.observeAll(), settings.settings, minuteTicker(clock)) { f, all, s, now ->
        FirstWeekRules.coachingTip(s.coachingEnabled, FirstWeekRules.fastsStartedBefore(all, f), s.onboardedAt?.let(Instant::ofEpochMilli), f, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // A fast is running, so a one-off "start tonight" reminder has done its job.
        viewModelScope.launch { planner.cleanUp() }
    }

    fun bind(f: Fast) = fast.update { f }

    fun hideTips() {
        viewModelScope.launch { settings.setCoaching(false) }
    }
}

data class SummaryUi(val waterMl: Int = 0, val canUndo: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FastSummaryViewModel @Inject constructor(
    logs: LogRepository,
    private val service: FastService,
    clock: Clock,
) : ViewModel() {
    private val fast = MutableStateFlow<Fast?>(null)
    val mood = MutableStateFlow<Int?>(null)
    val note = MutableStateFlow("")

    val ui: StateFlow<SummaryUi> = fast.filterNotNull().flatMapLatest { f ->
        val end = f.endedAt ?: clock.instant()
        combine(logs.observeWaterSince(f.startAt), minuteTicker(clock)) { water, now ->
            SummaryUi(WaterGoal.totalBetween(water, f.startAt, end).toInt(), FirstWeekRules.canUndo(f, now))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUi())

    fun bind(f: Fast) {
        if (fast.value?.id == f.id) return
        fast.value = f
        mood.value = f.mood
        note.value = f.note.orEmpty()
    }

    fun setMood(m: Int?) = mood.update { if (it == m) null else m }
    fun setNote(n: String) = note.update { n.take(500) }

    fun save(onDone: () -> Unit) {
        val f = fast.value ?: return onDone()
        viewModelScope.launch {
            service.saveReflection(f.id, mood.value, note.value)
            onDone()
        }
    }
}
