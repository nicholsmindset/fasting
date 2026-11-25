package com.fastingshift.app.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.clip
import com.fastingshift.app.ui.components.ProgressRing
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.components.latestKg
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.LargeTitle
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.app.ui.timer.Today
import com.fastingshift.app.alarm.eligibleSchedules
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.domain.model.Schedule
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.service.ScreeningRepository
import java.time.Instant
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.history.WeeklyRecaps
import com.fastingshift.domain.history.ScheduleFit
import java.time.DayOfWeek
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.minutesLabel
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.components.hoursLabel
import com.fastingshift.domain.schedule.WeekChoice
import com.fastingshift.domain.history.History
import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.history.Period
import com.fastingshift.domain.history.PeriodSummary
import com.fastingshift.domain.history.RecentFasts
import com.fastingshift.domain.history.WeekSegment
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.protocol.Presets
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

data class WeekRow(val weekStart: LocalDate, val segments: List<WeekSegment>)

data class CalendarPlan(
    val history: PlanHistory = PlanHistory(),
    val schedules: List<Schedule> = emptyList(),
    val firstEvening: Pair<Long, LocalDate>? = null,
)

data class HistoryState(
    val period: Period = Period.WEEK,
    val summary: PeriodSummary? = null,
    val recent: RecentFasts = RecentFasts(emptyList(), null),
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Duration = Duration.ZERO,
    val weeks: List<WeekRow> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val fasts: List<Fast> = emptyList(),
    val streak: StreakResult = StreakResult(0, 0),
    /** The missed day a freeze can cover, if any. */
    val freezeableDay: LocalDate? = null,
    /** This week so far, then last week on Mondays and Tuesdays. */
    val recaps: List<RecapItem> = emptyList(),
    val trackedFrom: LocalDate? = null,
    val nextWeekChoice: WeekChoice? = null,
    val now: Instant = Instant.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val calendarPlan: CalendarPlan = CalendarPlan(),
    val wrappedEnabled: Boolean = false,
    val frozenDays: Set<LocalDate> = emptySet(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    fasts: FastRepository,
    private val freezes: StreakFreezeRepository,
    logs: LogRepository,
    schedules: ScheduleRepository,
    screening: ScreeningRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now(clock.withZone(ZoneId.systemDefault())))
    private val period = MutableStateFlow(Period.WEEK)
    private val view = combine(month, period) { m, p -> m to p }
    private val minuteClock = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    /** Water since the start of last week, and the water goal, for the weekly recaps. */
    private val hydration = run {
        val lastWeek = History.weekStart(LocalDate.now(clock.withZone(ZoneId.systemDefault()))).minusWeeks(1)
        val from = lastWeek.atStartOfDay(ZoneId.systemDefault()).toInstant()
        combine(logs.observeWaterSince(from), logs.observeWeights(), settings.settings) { water, weights, s ->
            water to Today.waterGoalMl(s.waterGoalMl, weights.latestKg())
        }
    }

    private val plans = combine(settings.settings, schedules.observeAll(), screening.observeLatest()) { settings, rows, record ->
        val max = FastPolicy.maxAllowed(record?.gateResult, record?.clinicianAcknowledged == true)
        val plan = CalendarPlan(settings.planHistory, eligibleSchedules(rows, max), schedules.firstEvening())
        Triple(plan, settings.weekReflections, settings.wrappedEnabled)
    }

    /** Null until loaded, so an empty history never flashes before the fasts arrive. */
    val state: StateFlow<HistoryState?> = combine(fasts.observeAll(), freezes.observeFrozenDays(), plans, view, combine(hydration, minuteClock) { hydration, now -> hydration to now }) { all, frozen, (calendarPlan, reflections, wrappedEnabled), (m, p), timedHydration ->
        val (hydrationValues, now) = timedHydration
        val (water, goal) = hydrationValues
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val history = calendarPlan.history
        val planned = history::isPlanned
        val completed = StreakCalculator.completedDays(all, zone)
        val yesterday = today.minusDays(1)
        // A day whose fast is still running isn't missed yet, so it's never offered for a freeze.
        val running = all.filter { it.isActive }.map { it.startAt.atZone(zone).toLocalDate() }.toSet()
        val freezeable = yesterday.takeIf {
            it !in completed && it !in running && planned(it) && completed.any { d -> d.isBefore(it) && planned(d) } && StreakCalculator.canFreeze(it, frozen, today)
        }
        val firstRow = History.weekStart(m.atDay(1))
        val rows = generateSequence(firstRow) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(m.atEndOfMonth()) }
            .map { WeekRow(it, HistoryStats.weekSegments(all, zone, it, now)) }
            .toList()
        HistoryState(
            period = p,
            now = now,
            zone = zone,
            calendarPlan = calendarPlan,
            wrappedEnabled = wrappedEnabled,
            frozenDays = frozen,
            trackedFrom = history.trackedFrom,
            nextWeekChoice = history.choices[History.weekStart(today).plusWeeks(1)],
            summary = HistoryStats.periodSummary(all, zone, p, today, now, planned),
            recent = HistoryStats.recentFasts(all),
            month = m,
            monthTotal = HistoryStats.monthTotal(all, zone, m, now),
            weeks = rows,
            today = today,
            fasts = all,
            streak = StreakCalculator.calculate(all, zone, today, frozen, planned),
            freezeableDay = freezeable,
            recaps = buildList {
                val thisWeek = WeeklyRecaps.weekFor(today)
                // Early in the week, last week's full picture leads; this week is just starting.
                if (today.dayOfWeek == DayOfWeek.MONDAY || today.dayOfWeek == DayOfWeek.TUESDAY) {
                    val last = thisWeek.minusWeeks(1)
                    add(RecapItem(WeeklyRecaps.compute(all, water, zone, last, now, goal, planned), true, 7, reflections[last]))
                }
                add(RecapItem(WeeklyRecaps.compute(all, water, zone, thisWeek, now, goal, planned), false, RecapCopy.daysInWeek(thisWeek, today), reflections[thisWeek]))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun chooseWeek(week: LocalDate, choice: WeekChoice) = viewModelScope.launch {
        settings.setNextWeekChoice(week, choice, LocalDate.now(clock.withZone(ZoneId.systemDefault())))
    }

    fun reflect(week: LocalDate, fit: ScheduleFit) = viewModelScope.launch {
        settings.setWeekReflection(week, fit, LocalDate.now(clock.withZone(ZoneId.systemDefault())))
    }

    fun recordRecap(annual: Boolean, period: Long, shared: Boolean) = viewModelScope.launch {
        settings.recordRecap(annual, period, shared)
    }

    fun shiftMonth(by: Long) = month.update { it.plusMonths(by) }
    fun setPeriod(p: Period) = period.update { p }
    fun freeze(day: LocalDate) = viewModelScope.launch {
        freezes.addFrozenDay(day, clock.instant().atZone(ZoneId.systemDefault()).toLocalDate())
    }
}

@Composable
fun HistoryRoute(onEditFast: (Long) -> Unit, onOpenInsights: () -> Unit, onAddPastFast: (LocalDate?) -> Unit, onOpenSchedules: () -> Unit, onOpenChallenges: () -> Unit = {}, vm: HistoryViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val st = loaded
    if (st == null) {
        LoadingBox(Modifier.padding(top = 120.dp))
        return
    }
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var wrappedOpen by rememberSaveable { mutableStateOf(false) }
    val annualAvailable = st.wrappedEnabled && remember(st.fasts, st.zone, st.today) {
        com.fastingshift.domain.history.FastingWrapped.hasStory(st.fasts, st.zone, st.now)
    }
    if (annualAvailable && wrappedOpen) {
        val annual = remember(st.fasts, st.frozenDays, st.calendarPlan.history, st.zone, st.today) {
            com.fastingshift.domain.history.FastingWrapped.annual(st.fasts, st.zone, st.now, st.frozenDays, st.calendarPlan.history::isPlanned)
        }
        if (annual != null) {
            AnnualStory(annual, onSeen = { vm.recordRecap(true, annual.year.toLong(), false) },
                onShared = { vm.recordRecap(true, annual.year.toLong(), true) }, onClose = { wrappedOpen = false })
        }
    }
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    if (recordsOpen) {
        FastRecordsSheet(st.fasts, st.now, onEditFast, onClose = { recordsOpen = false })
    }
    selectedEpochDay?.let { epochDay ->
        val day = LocalDate.ofEpochDay(epochDay)
        HistoryDaySheet(day, st, onEditFast, onAdd = { onAddPastFast(day) }, onClose = { selectedEpochDay = null })
    }
    LazyColumn(
        Modifier.testTag("history-list").fillMaxSize().padding(horizontal = Spacing.screen),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        section {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val title = stringResource(R.string.history_title)
                val add = stringResource(R.string.add_past_button)
                if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column {
                        LargeTitle(title, Modifier.semantics { heading() })
                        TextButton(onClick = { onAddPastFast(null) }) { Text(add) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LargeTitle(title, Modifier.weight(1f).semantics { heading() })
                        TextButton(onClick = { onAddPastFast(null) }) { Text(add) }
                    }
                }
            }
        }
        section { st.summary?.let { PeriodOverview(st.period, it, st.trackedFrom, vm::setPeriod) } }
        section {
            // Streaks count consistency, never length (SR-3). Neutral copy, no badges.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.history_streak_title), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium)
                        Text(pluralStringResource(R.plurals.history_streak_longest, st.streak.longest, st.streak.longest),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(pluralStringResource(R.plurals.history_streak, st.streak.current, st.streak.current),
                        Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers))
                }
                com.fastingshift.app.ui.revival.RevivalHistory()
                st.freezeableDay?.let { day ->
                    Text(stringResource(R.string.history_streak_missed), style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton(stringResource(R.string.history_use_freeze), onClick = { vm.freeze(day) })
                }
            }
        }
        section(key = "history-recent") {
            Column {
                RecentFastChart(st.recent, onEditFast)
                TextButton(onClick = { recordsOpen = true }) { Text(stringResource(R.string.history_view_records)) }
            }
        }
        section(key = "history-calendar") {
            CalendarCard(st, vm::shiftMonth, onEditFast) { selectedEpochDay = it.toEpochDay() }
        }
        section {
            InsetGroup {
                GroupRow(stringResource(R.string.history_insights), showChevron = true, onClick = onOpenInsights)
                GroupDivider()
                GroupRow(stringResource(R.string.history_challenges), showChevron = true, onClick = onOpenChallenges)
            }
        }
        if (annualAvailable) section(key = "annual-wrapped") {
            WrappedEntry(com.fastingshift.domain.history.FastingWrapped.latestYear(st.today)) { wrappedOpen = true }
        }
        if (st.today.dayOfWeek == DayOfWeek.SUNDAY) section(key = "sunday-mini") {
            val mini = remember(st.fasts, st.zone, st.today) { com.fastingshift.domain.history.FastingWrapped.sunday(st.fasts, st.zone, st.now) }
            if (mini != null) SundayMini(mini,
                onSeen = { vm.recordRecap(false, mini.weekStart.toEpochDay(), false) },
                onShared = { vm.recordRecap(false, mini.weekStart.toEpochDay(), true) })
        }
        val review = st.recaps.firstOrNull { !it.recap.isEmpty || it.recap.plannedDays > 0 } ?: st.recaps.lastOrNull()
        st.recaps.forEach { item ->
            section(key = "recap-${item.recap.weekStart}") {
                WeeklyRecapCard(item, showShare = st.today.dayOfWeek != DayOfWeek.SUNDAY, onReflect = if (item == review || item.fit != null) {
                    { fit -> vm.reflect(item.recap.weekStart, fit) }
                } else null)
            }
        }
        section {
            val nextWeek = History.weekStart(st.today).plusWeeks(1)
            WeekPlanCard(nextWeek, st.nextWeekChoice, { vm.chooseWeek(nextWeek, it) }, onOpenSchedules)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastRecordsSheet(fasts: List<Fast>, now: Instant, onEditFast: (Long) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_all_fasts), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text(stringResource(R.string.settings_done)) }
            }
            LazyColumn(Modifier.testTag("history-records-list"), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                if (fasts.isEmpty()) item { Text(stringResource(R.string.history_empty)) }
                itemsIndexed(fasts, key = { _, f -> f.id }) { i, f ->
                    FastRow(f, now, first = i == 0, last = i == fasts.lastIndex) { onEditFast(f.id) }
                }
            }
        }
    }
}

// ---------- Summary ring ----------

@Composable
private fun PeriodOverview(period: Period, s: PeriodSummary, trackedFrom: LocalDate?, onPeriod: (Period) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val fastedLabel = stringResource(fastedIn(period))
    val summaryDescription = if (s.plannedDays > 0) {
        pluralStringResource(R.plurals.history_ring_a11y, s.plannedDays, s.totalFasting.hoursMinutes(), fastedLabel, s.completedDays, s.plannedDays)
    } else stringResource(R.string.history_ring_no_planned, s.totalFasting.hoursMinutes(), fastedLabel)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val haptics = rememberHaptics()
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            Period.entries.forEach { option ->
                val selected = option == period
                Column(
                    Modifier.weight(1f).selectable(selected, role = Role.Tab, onClick = { haptics.tick(); onPeriod(option) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(periodLabel(option)), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = 2.dp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        // The duration is an absolute measure. Only the planned-days ratio belongs in a progress ring.
        BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summaryDescription }) {
            val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.25f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PeriodTotal(s.totalFasting, fastedLabel)
                    PlannedDayRatio(s)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PeriodTotal(s.totalFasting, fastedLabel, Modifier.weight(1f))
                    Box(Modifier.weight(1f)) { PlannedDayRatio(s) }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metrics = listOf(
                stringResource(R.string.history_stat_fasts) to s.fasts.toString(),
                stringResource(R.string.history_stat_average) to (s.average?.hoursMinutes() ?: "—"),
                stringResource(R.string.status_completed) to s.completed.toString(),
            )
            if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.25f) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    metrics.forEach { (label, value) -> Stat(label, value, Modifier.fillMaxWidth()) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.forEach { (label, value) -> Stat(label, value, Modifier.weight(1f)) }
                }
            }
        }
        if (trackedFrom != null && trackedFrom > s.range.start) {
            Text(stringResource(R.string.history_tracking_since, trackedFrom.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PeriodTotal(total: Duration, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(totalLabel(total), style = MaterialTheme.typography.displayMedium.merge(TabularNumbers))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlannedDayRatio(summary: PeriodSummary) {
    if (summary.plannedDays == 0) {
        Text(stringResource(R.string.history_no_planned_days), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val fontScale = LocalDensity.current.fontScale
    // At large type the fraction moves beside the ring so it can wrap with the caption.
    val fraction = "${summary.completedDays}/${summary.plannedDays}"
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            val diameter = if (fontScale > 1.25f) 40.dp else if (summary.plannedDays >= 100) 72.dp else 60.dp
            ProgressRing(summary.consistency, Modifier.size(diameter), stroke = 4.dp)
            if (fontScale <= 1.25f) Text(fraction, style = MaterialTheme.typography.labelMedium.merge(TabularNumbers))
        }
        Column {
            if (fontScale > 1.25f) Text(fraction, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
            Text(stringResource(R.string.history_stat_planned_days),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Gap below each section of the History list (fast rows sit flush, as one inset group). */
private fun LazyListScope.section(key: String? = null, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.padding(bottom = 16.dp)) { content() } }
}

/** Compact duration for the period headline, month total, and recap. */
@Composable
internal fun totalLabel(d: Duration): String = if (d.toHours() >= 10) hoursLabel(d.toHours()) else d.hoursMinutes()

// ---------- Recent fasts chart ----------

/** Day and month in the order the user's locale expects (19/11 or 11/19). */
private fun shortDateFormat(locale: Locale): DateTimeFormatter {
    val full = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, locale,
    )
    // Drop the year and the separator next to it: "M/d/yy" -> "M/d", "dd.MM.yy" -> "dd.MM".
    val dayMonth = full.replace(Regex("[^dM]*y+[^dM]*"), "").ifBlank { "d/M" }
    return DateTimeFormatter.ofPattern(dayMonth, locale)
}

@Composable
private fun RecentFastChart(recent: RecentFasts, onEditFast: (Long) -> Unit) {
    val palette = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = palette.outlineVariant)
        Text(stringResource(R.string.history_recent), Modifier.padding(top = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge)
        if (recent.bars.isEmpty()) {
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            return@Column
        }
        val zone = ZoneId.systemDefault()
        val locale = LocalConfiguration.current.locales[0]
        val shortDate = remember(locale) { shortDateFormat(locale) }
        val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
        val first = recent.bars.first().fast.startAt.atZone(zone).format(dateFormat)
        val last = recent.bars.last().fast.startAt.atZone(zone).format(dateFormat)
        Text(stringResource(R.string.history_chart_range, first, last), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        recent.average?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp, 2.dp).drawBehind {
                    drawLine(palette.onSurfaceVariant, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                })
                Text(stringResource(R.string.history_chart_average, it.hoursMinutes()), style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
            }
        }
        val maxMinutes = maxOf(recent.bars.maxOf { it.length.toMinutes() }, 16L * 60)
        val maxBar = 100.dp
        val barLabelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
        val plotHeight = maxBar + barLabelHeight + 8.dp
        val averageFraction = (recent.average?.toMinutes() ?: 0L).toFloat() / maxMinutes
        val editLabel = stringResource(R.string.edit_title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columnWidth = maxOf(maxWidth / recent.bars.size, 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
            // Start at the latest record. Dates and bars scroll together on smaller screens.
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
                Row(Modifier.width(columnWidth * recent.bars.size).drawBehind {
                    val baseline = plotHeight.toPx()
                    drawLine(palette.outline, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                    val y = baseline - maxBar.toPx() * averageFraction
                    drawLine(palette.onSurfaceVariant, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                }) {
                    recent.bars.forEach { bar ->
                        val date = bar.fast.startAt.atZone(zone)
                        val status = stringResource(if (bar.completed) R.string.status_completed else R.string.status_ended)
                        val desc = bar.fast.startAt.formatDateTime(locale) + ", " + stringResource(R.string.a11y_fast_bar, bar.length.hoursMinutes(), status)
                        val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                        val barStyle = if (bar.completed) Modifier.background(palette.primary, shape)
                            else Modifier.background(palette.background, shape).border(1.5.dp, palette.onSurfaceVariant, shape)
                        Column(Modifier.width(columnWidth).clickable(role = Role.Button, onClickLabel = editLabel) { onEditFast(bar.fast.id) }
                            .semantics { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Column(Modifier.fillMaxWidth().height(plotHeight).clearAndSetSemantics {},
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                val value = if (bar.length.toHours() == 0L) minutesLabel(bar.length.toMinutes()) else hoursLabel(bar.length.toHours())
                                Text(value, Modifier.background(palette.background).padding(horizontal = 2.dp),
                                    style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), maxLines = 1)
                                Box(Modifier.width(19.dp).height(maxBar * (bar.length.toMinutes().toFloat() / maxMinutes)).then(barStyle))
                            }
                            Text(date.format(shortDate), Modifier.padding(vertical = 6.dp).clearAndSetSemantics {},
                                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = palette.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(true, stringResource(R.string.status_completed), Modifier.weight(1f, fill = false))
            Legend(false, stringResource(R.string.status_ended), Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun Legend(completed: Boolean, label: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shape = RoundedCornerShape(2.dp)
        val mark = if (completed) Modifier.background(MaterialTheme.colorScheme.primary, shape)
            else Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
        Box(Modifier.size(11.dp).then(mark))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Calendar with fasts drawn across days ----------

@Composable
internal fun CalendarCard(st: HistoryState, onShift: (Long) -> Unit, onEditFast: (Long) -> Unit, onDay: (LocalDate) -> Unit = {}) {
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${st.month.month.getDisplayName(TextStyle.FULL, locale)} ${st.month.year}",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.report_total) + " · " + totalLabel(st.monthTotal),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val prev = stringResource(R.string.a11y_prev_month)
            val next = stringResource(R.string.a11y_next_month)
            IconButton(onClick = { onShift(-1) }, modifier = Modifier.semantics { contentDescription = prev }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.rotate(180f))
            }
            IconButton(onClick = { onShift(1) }, modifier = Modifier.semantics { contentDescription = next }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        st.weeks.forEach { row -> WeekRowView(row, st.month, st.today, dayFormat, onEditFast, onDay) }
    }
}

@Composable
private fun WeekRowView(row: WeekRow, month: YearMonth, today: LocalDate, dayFormat: DateTimeFormatter, onEditFast: (Long) -> Unit, onDay: (LocalDate) -> Unit) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
    val daySize = maxOf(32.dp, labelHeight + 4.dp)
    val laneHeight = maxOf(26.dp, labelHeight + 4.dp)
    Box(Modifier.fillMaxWidth().height(daySize + laneHeight + 4.dp).drawBehind {
        drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx())
    }) {
        // The cells fill the entire row, including empty space in the bar lane.
        Row(Modifier.fillMaxSize()) {
            repeat(7) { offset ->
                val day = row.weekStart.plusDays(offset.toLong())
                if (YearMonth.from(day) != month) {
                    Spacer(Modifier.weight(1f))
                } else {
                    val label = day.format(dayFormat)
                    val isToday = day == today
                    Box(
                        Modifier.weight(1f).fillMaxHeight().testTag("history-day-$day")
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.history_day_open)) { onDay(day) }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            Modifier.size(daySize).clip(CircleShape)
                                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clearAndSetSemantics {},
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(laneHeight)) {
            val dayWidth = maxWidth / 7
            row.segments.forEach { seg -> SegmentBar(seg, dayWidth, onEditFast) }
        }
    }
}

@Composable
private fun SegmentBar(seg: WeekSegment, dayWidth: androidx.compose.ui.unit.Dp, onEditFast: (Long) -> Unit) {
    val width = dayWidth * (seg.endDay - seg.startDay)
    val r = 13.dp
    val shape = RoundedCornerShape(
        topStart = if (seg.continuesBefore) 0.dp else r, bottomStart = if (seg.continuesBefore) 0.dp else r,
        topEnd = if (seg.continuesAfter || seg.fast.isActive) 0.dp else r, bottomEnd = if (seg.continuesAfter || seg.fast.isActive) 0.dp else r,
    )
    val color = when {
        seg.fast.isActive -> MaterialTheme.colorScheme.primaryContainer
        seg.fast.status == FastStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val onColor = when (seg.fast.status) {
        FastStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        FastStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimary
        FastStatus.ENDED_EARLY -> MaterialTheme.colorScheme.onSecondary
    }
    val desc = stringResource(R.string.a11y_fast_bar, seg.totalLength.hoursMinutes(), stringResource(statusLabel(seg.fast.status)))
    Box(
        Modifier.offset(x = dayWidth * seg.startDay).width(width).fillMaxHeight().padding(horizontal = 1.dp)
            .clip(shape).then(
                if (seg.fast.isActive && !seg.continuesAfter) Modifier.background(
                    Brush.horizontalGradient(0f to color, 0.65f to color, 1f to color.copy(alpha = 0.1f)),
                ) else Modifier.background(color),
            )
            .then(if (seg.fast.isActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable { onEditFast(seg.fast.id) }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (seg.showLabel && width >= 20.dp) {
            // Narrow pieces (a fast split across two week rows) get a smaller label.
            val style = if (width >= 30.dp) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
            Text(hoursLabel(seg.totalLength.toHours()), style = style, fontWeight = FontWeight.SemiBold, color = onColor, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun FastRow(f: Fast, now: Instant, first: Boolean, last: Boolean, onClick: () -> Unit) {
    // One row of the "All fasts" inset group: rounded only at the group's top and bottom.
    val r = 22.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    val length = Duration.between(f.startAt, f.endedAt ?: now)
    val status = stringResource(statusLabel(f.status))
    val editLabel = stringResource(R.string.edit_title)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (!first) GroupDivider()
            GroupRow(
                title = f.startAt.formatDateTime(LocalConfiguration.current.locales[0]),
                modifier = Modifier.semantics { this.onClick(label = editLabel, action = null) },
                subtitle = listOfNotNull(Presets.byId(f.protocolId)?.name ?: f.protocolId, length.hoursMinutes(), status, f.note).joinToString(" · "),
                showChevron = true,
                onClick = onClick,
                leading = {
                    val color = when (f.status) {
                        FastStatus.ACTIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        FastStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        FastStatus.ENDED_EARLY -> MaterialTheme.colorScheme.secondary
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                },
            ) {
                com.fastingshift.app.ui.ramadan.FastKindChip(f.kind)
            }
        }
    }
}

internal fun statusLabel(s: FastStatus) = when (s) {
    FastStatus.ACTIVE -> R.string.status_active
    FastStatus.COMPLETED -> R.string.status_completed
    FastStatus.ENDED_EARLY -> R.string.status_ended
}

private fun periodLabel(p: Period) = when (p) {
    Period.WEEK -> R.string.history_period_week
    Period.MONTH -> R.string.history_period_month
    Period.YEAR -> R.string.history_period_year
}

private fun fastedIn(p: Period) = when (p) {
    Period.WEEK -> R.string.history_fasted_week
    Period.MONTH -> R.string.history_fasted_month
    Period.YEAR -> R.string.history_fasted_year
}
