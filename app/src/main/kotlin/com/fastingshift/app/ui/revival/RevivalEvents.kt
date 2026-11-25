package com.fastingshift.app.ui.revival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.db.RevivalEventEntity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.revival.Revival
import com.fastingshift.domain.revival.RevivalCalendar
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.StreakFreezeRepository
import com.fastingshift.domain.streak.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun ScreeningRecord?.eligible() = this != null && answers.ageYears >= 18 &&
    FastPolicy.maxAllowed(gateResult, clinicianAcknowledged) != null

internal data class RevivalState(
    val event: RevivalEventEntity? = null, val marks: Int = 0, val best: Int = 0,
    val award: RevivalEventEntity? = null, val currentRevived: Int? = null,
    val joinedCount: Int = 0, val completedCount: Int = 0,
)

private data class Personal(val fasts: List<Fast>, val frozen: Set<LocalDate>, val plan: PlanHistory,
                            val offset: Int, val eligible: Boolean)

@Singleton
class RevivalEvents @Inject constructor(
    db: FastingDatabase, private val fasts: FastRepository, private val freezes: StreakFreezeRepository,
    private val settings: SettingsRepository, private val screening: ScreeningRepository, private val clock: Clock,
) {
    private val dao = db.revivalDao()

    private suspend fun seed(now: Instant) {
        val zone = ZoneId.systemDefault()
        val year = now.atZone(zone).year
        val events = (year..year+1).flatMap { RevivalCalendar.windows(it, zone) }.map {
            RevivalEventEntity(it.id, it.start.toEpochMilli(), it.endExclusive.toEpochMilli(), it.zone.id, it.target, it.requiredDays)
        }
        dao.seed(events)
    }

    private fun ticks() = flow {
        var previousYear: Int? = null
        while (true) {
            val now = clock.instant()
            val year = now.atZone(ZoneId.systemDefault()).year
            if (year != previousYear) { seed(now); previousYear = year }
            emit(now)
            delay(60_000)
        }
    }

    private val personal = combine(fasts.observeAll(), freezes.observeFrozenDays(), settings.settings, screening.observeLatest()) {
        all, frozen, s, gate -> Personal(all, frozen, s.planHistory, s.hijriOffsetDays, gate.eligible())
    }

    internal fun observe(): Flow<RevivalState> = combine(dao.observe(), personal, ticks()) { events, person, now ->
        if (!person.eligible) return@combine RevivalState()
        val supported = events.filter { it.target == Revival.DAYS_ON_PLAN && it.threshold > 0 }
        val visible = supported.firstOrNull { now.toEpochMilli() in it.starts until it.ends && !it.dismissed }
        var visibleMarks = 0
        for (event in supported.filter { it.joined != null && it.restored == null }) {
            val window = event.window()
            val marks = Revival.marks(window, Instant.ofEpochMilli(requireNotNull(event.joined)),
                person.fasts, now, person.offset, person.plan::isPlanned)
            if (event.id == visible?.id) visibleMarks = marks.size
            if (marks.size == event.threshold) {
                val last = marks.last()
                val ended = requireNotNull(last.endedAt)
                val days = maxOf(event.baselineBest ?: 0, event.threshold)
                dao.finish(event.id, ended.toEpochMilli(), ended.atZone(window.zone).toLocalDate().toEpochDay(), days)
            }
        }
        val award = supported.filter { it.restored != null }.maxByOrNull { requireNotNull(it.restored) }
        val zone = (visible ?: award)?.window()?.zone ?: ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val best = if (visible != null && visible.joined == null) {
            StreakCalculator.calculate(person.fasts, zone, today, person.frozen, person.plan::isPlanned).longest
        } else visible?.baselineBest ?: 0
        val revived = award?.let {
            val awardZone = it.window().zone
            Revival.currentRevived(requireNotNull(it.restoredDays), LocalDate.ofEpochDay(requireNotNull(it.awardDay)),
                person.fasts, person.frozen, now.atZone(awardZone).toLocalDate(), awardZone, person.plan::isPlanned)
        }
        RevivalState(visible, if (visible?.restored != null) visible.threshold else visibleMarks, best,
            award, revived, supported.count { it.joined != null }, supported.count { it.restored != null })
    }.flowOn(Dispatchers.Default)

    internal suspend fun join(id: String) {
        val now = clock.instant()
        val event = dao.all().firstOrNull { it.id == id && it.target == Revival.DAYS_ON_PLAN } ?: return
        val person = personal.first()
        if (!person.eligible) return
        val zone = event.window().zone
        val best = StreakCalculator.calculate(person.fasts, zone, now.atZone(zone).toLocalDate(), person.frozen, person.plan::isPlanned).longest
        dao.join(id, now.toEpochMilli(), best)
    }

    internal suspend fun dismiss(id: String) = dao.dismiss(id)

    /** A missed delivery is never replayed on a later day. Only users who once had a streak qualify. */
    internal suspend fun claimStartAlert(expectedId: String): Boolean {
        val now = clock.instant()
        seed(now)
        val event = dao.all().firstOrNull {
            val zone = ZoneId.of(it.zone)
            it.id == expectedId && now.atZone(zone).toLocalDate() == Instant.ofEpochMilli(it.starts).atZone(zone).toLocalDate() &&
                now.toEpochMilli() in it.starts until it.ends && !it.notified && !it.dismissed && it.joined == null &&
                it.target == Revival.DAYS_ON_PLAN
        } ?: return false
        val p = personal.first()
        if (!p.eligible) return false
        val zone = ZoneId.of(event.zone)
        val today = now.atZone(zone).toLocalDate()
        if (!RevivalCalendar.eligibleDay(today, p.offset)) return false
        val streak = StreakCalculator.calculate(p.fasts, zone, today, p.frozen, p.plan::isPlanned)
        return streak.current == 0 && streak.longest > 0 && dao.claimNotification(event.id) == 1
    }
}

@HiltViewModel
class RevivalViewModel @Inject constructor(private val events: RevivalEvents) : ViewModel() {
    internal val state = events.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RevivalState())
    fun join(id: String) { viewModelScope.launch { events.join(id) } }
    fun dismiss(id: String) { viewModelScope.launch { events.dismiss(id) } }
}
