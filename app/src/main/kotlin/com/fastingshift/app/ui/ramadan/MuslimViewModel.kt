package com.fastingshift.app.ui.ramadan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.City
import com.fastingshift.domain.ramadan.Hijri
import com.fastingshift.domain.ramadan.HijriDate
import com.fastingshift.domain.ramadan.NoFastReason
import com.fastingshift.domain.ramadan.RamadanPlanner
import com.fastingshift.domain.ramadan.Sunnah
import com.fastingshift.domain.ramadan.SunnahDay
import com.fastingshift.domain.ramadan.SunnahType
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.StartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Today's fast, if one can be started from the hub: until maghrib in the chosen city. */
sealed interface TodayFast {
    /** No Ramadan city chosen, so maghrib is unknown. */
    data object NeedsCity : TodayFast

    /** A fast is already running; the Timer shows it. */
    data class Active(val fast: Fast) : TodayFast

    /** Maghrib has passed, or is less than an hour away. */
    data class TooLate(val maghrib: Instant, val zone: ZoneId) : TodayFast

    /** Maghrib can't be calculated for this city today; use a local timetable. */
    data object TimesUnavailable : TodayFast

    /** A fast can start now and end at [maghrib]. */
    data class Ready(val maghrib: Instant, val zone: ZoneId, val length: Duration) : TodayFast
}

data class MuslimUiState(
    val today: LocalDate,
    val hijri: HijriDate?,
    val offsetDays: Int,
    /** Sunnah fasts that fall today (empty on excluded days). */
    val todayTypes: Set<SunnahType>,
    val noFast: NoFastReason?,
    /** Sunnah days in the next [UPCOMING_DAYS] days, today included. */
    val upcoming: List<SunnahDay>,
    val city: City?,
    val todayFast: TodayFast,
) {
    /** Kinds of fast the hub offers to start today. */
    val startKinds: List<FastKind>
        get() = allowedKinds(noFast, todayTypes)

    companion object {
        const val UPCOMING_DAYS = 30

        internal fun allowedKinds(noFast: NoFastReason?, todayTypes: Set<SunnahType>): List<FastKind> =
            when (noFast) {
                NoFastReason.RAMADAN -> listOf(FastKind.RAMADAN)
                null -> listOfNotNull(FastKind.SUNNAH.takeIf { todayTypes.isNotEmpty() }, FastKind.QADA)
                else -> emptyList()
            }
    }
}

/** One-off dialogs after a start attempt. */
data class MuslimEvents(
    val confirm: Set<StartRequirement>? = null,
    val blocked: BlockReason? = null,
    val invalidStart: Boolean = false,
    val needsCity: Boolean = false,
    /** A fast just started: go to the Timer. */
    val started: Boolean = false,
)

/** The Muslim fasting hub: Hijri date, Sunnah days and starting a fast until maghrib. */
@HiltViewModel
class MuslimViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val fasts: FastRepository,
    private val service: FastService,
    private val clock: Clock,
) : ViewModel() {
    private val minutes = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            delay(60_000 - now.toEpochMilli() % 60_000)
        }
    }

    private val _events = MutableStateFlow(MuslimEvents())
    val events: StateFlow<MuslimEvents> = _events
    private var pendingKind: FastKind? = null

    val state: StateFlow<MuslimUiState?> = combine(settings.settings, fasts.observeAll(), minutes) { s, all, now ->
        val city = Cities.byId(s.ramadanCityId)
        val today = now.atZone(city?.zone ?: ZoneId.systemDefault()).toLocalDate()
        val offset = s.hijriOffsetDays
        val hijri = Hijri.of(today, offset)
        val active = all.firstOrNull { it.isActive }
        val todayFast = when {
            active != null -> TodayFast.Active(active)
            city == null -> TodayFast.NeedsCity
            else -> {
                val method = s.ramadanMethod ?: Cities.defaultMethod(city)
                val maghrib = RamadanPlanner.day(city, method, today).maghrib
                val length = maghrib?.let { Duration.between(now, it) }
                when {
                    maghrib == null || length == null -> TodayFast.TimesUnavailable
                    length < FastPolicy.MIN_FAST -> TodayFast.TooLate(maghrib, city.zone)
                    else -> TodayFast.Ready(maghrib, city.zone, length)
                }
            }
        }
        MuslimUiState(
            today = today,
            hijri = hijri,
            offsetDays = offset,
            todayTypes = Sunnah.typesFor(today, offset),
            noFast = Sunnah.noFastReason(today, offset),
            upcoming = Sunnah.upcoming(today, MuslimUiState.UPCOMING_DAYS, offset),
            city = city,
            todayFast = todayFast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun chooseCity(city: City) = viewModelScope.launch {
        settings.setRamadanCity(city.id, Cities.defaultMethod(city))
        _events.update { it.copy(needsCity = false) }
    }

    /** Starts a [kind] fast from now until today's maghrib, through [FastService] so every safety rule applies. */
    fun start(kind: FastKind, confirmed: Set<StartRequirement> = emptySet()) = viewModelScope.launch {
        val s = settings.current()
        val city = Cities.byId(s.ramadanCityId)
        if (city == null) {
            _events.update { it.copy(needsCity = true) }
            return@launch
        }
        val now = clock.instant()
        val method = s.ramadanMethod ?: Cities.defaultMethod(city)
        val today = now.atZone(city.zone).toLocalDate()
        val offset = s.hijriOffsetDays
        val allowed = MuslimUiState.allowedKinds(Sunnah.noFastReason(today, offset), Sunnah.typesFor(today, offset))
        if (kind !in allowed) {
            _events.update { it.copy(invalidStart = true) }
            return@launch
        }
        val maghrib = RamadanPlanner.day(city, method, today).maghrib
        if (maghrib == null) {
            _events.update { it.copy(invalidStart = true) }
            return@launch
        }
        val minutes = Duration.between(now, maghrib).toMinutes()
        if (minutes < FastPolicy.MIN_FAST.toMinutes()) {
            _events.update { it.copy(invalidStart = true) }
            return@launch
        }
        val hours = ((minutes + 59) / 60).toInt().coerceIn(Presets.CUSTOM_MIN_HOURS, Presets.CUSTOM_MAX_HOURS)
        pendingKind = kind
        when (val result = service.start(Presets.custom(hours), confirmed, now, kind = kind, targetEndAt = maghrib)) {
            is StartResult.Started, is StartResult.AlreadyActive -> _events.update { MuslimEvents(started = true) }
            is StartResult.NeedsConfirmation -> _events.update { it.copy(confirm = result.requirements) }
            is StartResult.Blocked -> _events.update { it.copy(blocked = result.decision.reason) }
            StartResult.InvalidStartTime -> _events.update { it.copy(invalidStart = true) }
        }
    }

    fun confirmAndStart() {
        val shown = _events.value.confirm ?: return
        val kind = pendingKind ?: return
        _events.update { it.copy(confirm = null) }
        start(kind, shown)
    }

    fun consumeStarted() = _events.update { it.copy(started = false) }
    fun dismissDialogs() = _events.update { it.copy(confirm = null, blocked = null, invalidStart = false, needsCity = false) }
}
