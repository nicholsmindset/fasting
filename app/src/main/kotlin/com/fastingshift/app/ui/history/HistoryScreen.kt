package com.fastingshift.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.history.History
import com.fastingshift.domain.history.WeekTotal
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.StreakFreezeRepository
import com.fastingshift.domain.streak.StreakCalculator
import com.fastingshift.domain.streak.StreakResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val month: YearMonth = YearMonth.now(),
    val days: Map<LocalDate, List<Fast>> = emptyMap(),
    val fasts: List<Fast> = emptyList(),
    val weeks: List<WeekTotal> = emptyList(),
    val streak: StreakResult = StreakResult(0, 0),
    /** The missed day a freeze can cover, if any. */
    val freezeableDay: LocalDate? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    fasts: FastRepository,
    private val freezes: StreakFreezeRepository,
    schedules: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HistoryState> = combine(fasts.observeAll(), freezes.observeFrozenDays(), schedules.observeAll(), month) { all, frozen, sch, m ->
        val zone = ZoneId.systemDefault()
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val planned: (LocalDate) -> Boolean = { ScheduleCalculator.isPlannedDay(sch, it) }
        val streak = StreakCalculator.calculate(all, zone, today, frozen, planned)
        val completed = StreakCalculator.completedDays(all, zone)
        val yesterday = today.minusDays(1)
        val freezeable = yesterday.takeIf {
            it !in completed && planned(it) && completed.any { d -> d.isBefore(it) } && StreakCalculator.canFreeze(it, frozen)
        }
        HistoryState(m, History.byDay(all, zone, m), all, History.weeklyTotals(all, zone, now).take(8), streak, freezeable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun shiftMonth(by: Long) = month.update { it.plusMonths(by) }
    fun freeze(day: LocalDate) = viewModelScope.launch { freezes.addFrozenDay(day) }
}

@Composable
fun HistoryRoute(onEditFast: (Long) -> Unit, onOpenInsights: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Streaks count consistency, never length (SR-3). Neutral copy, no badges.
            SectionCard(title = stringResource(R.string.history_streak_title)) {
                Text(pluralStringResource(R.plurals.history_streak, st.streak.current, st.streak.current), style = MaterialTheme.typography.headlineSmall)
                Text(pluralStringResource(R.plurals.history_streak_longest, st.streak.longest, st.streak.longest), style = MaterialTheme.typography.bodySmall)
                st.freezeableDay?.let { day ->
                    Text(stringResource(R.string.history_streak_missed))
                    OutlinedButton(onClick = { vm.freeze(day) }) { Text(stringResource(R.string.history_use_freeze)) }
                }
            }
        }
        item { Calendar(st, vm::shiftMonth) }
        item {
            SectionCard(title = stringResource(R.string.history_weekly)) {
                if (st.weeks.isEmpty()) Text(stringResource(R.string.history_empty))
                st.weeks.forEach { w ->
                    Text(pluralStringResource(R.plurals.history_week_row, w.fasts, w.weekStart.format(weekFormat), w.fastingTime.hoursMinutes(), w.completed, w.fasts))
                }
                TextButton(onClick = onOpenInsights) { Text(stringResource(R.string.history_insights)) }
            }
        }
        items(st.fasts, key = { it.id }) { f -> FastRow(f) { onEditFast(f.id) } }
    }
}

private val weekFormat = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)

@Composable
private fun Calendar(st: HistoryState, onShift: (Long) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val prev = stringResource(R.string.a11y_prev_month)
            val next = stringResource(R.string.a11y_next_month)
            TextButton(onClick = { onShift(-1) }) { Text("‹", Modifier.semantics { contentDescription = prev }) }
            Text(
                "${st.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${st.month.year}",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onShift(1) }) { Text("›", Modifier.semantics { contentDescription = next }) }
        }
        val first = st.month.atDay(1)
        val lead = first.dayOfWeek.value - 1 // Monday first
        val cells = List(lead) { null } + (1..st.month.lengthOfMonth()).map { st.month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day -> DayCell(day, day?.let { st.days[it] }.orEmpty(), Modifier.weight(1f)) }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, fasts: List<Fast>, modifier: Modifier) {
    if (day == null) {
        Box(modifier.aspectRatio(1f))
        return
    }
    val completed = fasts.any { it.status == FastStatus.COMPLETED }
    val any = fasts.isNotEmpty()
    val dayLabel = day.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
    val description = stringResource(
        when {
            completed -> R.string.a11y_day_completed
            any -> R.string.a11y_day_fasted
            else -> R.string.a11y_day_none
        },
        dayLabel,
    )
    Box(modifier.aspectRatio(1f).padding(2.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        val bg = when {
            completed -> MaterialTheme.colorScheme.primary
            any -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
        Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Text(
                day.dayOfMonth.toString(),
                color = if (completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FastRow(f: Fast, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        val length = Duration.between(f.startAt, f.endedAt ?: java.time.Instant.now())
        Text("${Presets.byId(f.protocolId)?.name ?: f.protocolId} · ${length.hoursMinutes()}", style = MaterialTheme.typography.titleSmall)
        Text(f.startAt.formatDateTime(), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(
                when (f.status) {
                    FastStatus.ACTIVE -> R.string.status_active
                    FastStatus.COMPLETED -> R.string.status_completed
                    FastStatus.ENDED_EARLY -> R.string.status_ended
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        f.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
