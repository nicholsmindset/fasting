package com.fastingshift.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.history.Period
import com.fastingshift.domain.history.PeriodSummary
import com.fastingshift.domain.history.RecentFasts
import com.fastingshift.domain.history.WeekSegment
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

data class WeekRow(val weekStart: LocalDate, val segments: List<WeekSegment>)

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
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    fasts: FastRepository,
    private val freezes: StreakFreezeRepository,
    schedules: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val period = MutableStateFlow(Period.WEEK)
    private val view = combine(month, period) { m, p -> m to p }

    val state: StateFlow<HistoryState> = combine(fasts.observeAll(), freezes.observeFrozenDays(), schedules.observeAll(), view) { all, frozen, sch, (m, p) ->
        val zone = ZoneId.systemDefault()
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val planned: (LocalDate) -> Boolean = { ScheduleCalculator.isPlannedDay(sch, it) }
        val completed = StreakCalculator.completedDays(all, zone)
        val yesterday = today.minusDays(1)
        val freezeable = yesterday.takeIf {
            it !in completed && planned(it) && completed.any { d -> d.isBefore(it) } && StreakCalculator.canFreeze(it, frozen)
        }
        val firstRow = History.weekStart(m.atDay(1))
        val rows = generateSequence(firstRow) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(m.atEndOfMonth()) }
            .map { WeekRow(it, HistoryStats.weekSegments(all, zone, it, now)) }
            .toList()
        HistoryState(
            period = p,
            summary = HistoryStats.periodSummary(all, zone, p, today, now, planned),
            recent = HistoryStats.recentFasts(all),
            month = m,
            monthTotal = HistoryStats.monthTotal(all, zone, m, now),
            weeks = rows,
            today = today,
            fasts = all,
            streak = StreakCalculator.calculate(all, zone, today, frozen, planned),
            freezeableDay = freezeable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun shiftMonth(by: Long) = month.update { it.plusMonths(by) }
    fun setPeriod(p: Period) = period.update { p }
    fun freeze(day: LocalDate) = viewModelScope.launch { freezes.addFrozenDay(day) }
}

@Composable
fun HistoryRoute(onEditFast: (Long) -> Unit, onOpenInsights: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { st.summary?.let { SummaryCard(st.period, it, vm::setPeriod) } }
        item { RecentFastsCard(st.recent, onEditFast) }
        item { CalendarCard(st, vm::shiftMonth, onEditFast) }
        item {
            // Streaks count consistency, never length (SR-3). Neutral copy, no badges.
            SectionCard(title = stringResource(R.string.history_streak_title)) {
                Text(pluralStringResource(R.plurals.history_streak, st.streak.current, st.streak.current), style = MaterialTheme.typography.headlineSmall)
                Text(pluralStringResource(R.plurals.history_streak_longest, st.streak.longest, st.streak.longest), style = MaterialTheme.typography.bodySmall)
                st.freezeableDay?.let { day ->
                    Text(stringResource(R.string.history_streak_missed))
                    OutlinedButton(onClick = { vm.freeze(day) }) { Text(stringResource(R.string.history_use_freeze)) }
                }
                TextButton(onClick = onOpenInsights) { Text(stringResource(R.string.history_insights)) }
            }
        }
        items(st.fasts, key = { it.id }) { f -> FastRow(f) { onEditFast(f.id) } }
    }
}

// ---------- Summary ring ----------

@Composable
private fun SummaryCard(period: Period, s: PeriodSummary, onPeriod: (Period) -> Unit) {
    SectionCard {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Period.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = p == period,
                    onClick = { onPeriod(p) },
                    shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                ) { Text(stringResource(periodLabel(p))) }
            }
        }
        val fastedLabel = stringResource(fastedIn(period))
        val ringA11y = stringResource(R.string.history_ring_a11y, s.totalFasting.hoursMinutes(), fastedLabel, s.completedDays, s.plannedDays)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = ringA11y },
            contentAlignment = Alignment.Center,
        ) {
            ConsistencyRing(s.consistency, Modifier.size(200.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(totalLabel(s.totalFasting), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                Text(fastedLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.history_stat_fasts), s.fasts.toString(), Modifier.weight(1f))
            Stat(stringResource(R.string.history_stat_average), s.average?.hoursMinutes() ?: "—", Modifier.weight(1f))
            Stat(stringResource(R.string.history_stat_planned_days), "${s.completedDays}/${s.plannedDays}", Modifier.weight(1f))
        }
    }
}

/** The ring fills with consistency (planned days completed), never with hours: longer isn't better. */
@Composable
private fun ConsistencyRing(progress: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        if (progress > 0f) {
            drawArc(fill, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

/** "76h" for the ring; minutes only matter for short totals. */
private fun totalLabel(d: Duration): String = if (d.toHours() >= 10) "${d.toHours()}h" else d.hoursMinutes()

// ---------- Recent fasts chart ----------

/** Day and month in the order the user's locale expects (19/11 or 11/19). */
private val shortDate: DateTimeFormatter
    get() {
        val locale = Locale.getDefault()
        val full = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, locale,
        )
        // Drop the year and the separator next to it: "M/d/yy" -> "M/d", "dd.MM.yy" -> "dd.MM".
        val dayMonth = full.replace(Regex("[^dM]*y+[^dM]*"), "").ifBlank { "d/M" }
        return DateTimeFormatter.ofPattern(dayMonth, locale)
    }

@Composable
private fun RecentFastsCard(recent: RecentFasts, onEditFast: (Long) -> Unit) {
    SectionCard(title = stringResource(R.string.history_recent)) {
        if (recent.bars.isEmpty()) {
            Text(stringResource(R.string.history_empty))
            return@SectionCard
        }
        recent.average?.let {
            Text(stringResource(R.string.history_average, it.hoursMinutes(), recent.bars.size), style = MaterialTheme.typography.headlineSmall)
        }
        val zone = ZoneId.systemDefault()
        val maxHours = maxOf(recent.bars.maxOf { it.length.toMinutes() } / 60f, 16f)
        val barArea = 120.dp
        val maxBar = 100.dp
        val done = MaterialTheme.colorScheme.primary
        val early = MaterialTheme.colorScheme.outline
        Box(Modifier.fillMaxWidth().height(barArea)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                recent.bars.forEach { bar ->
                    val hours = bar.length.toMinutes() / 60f
                    val desc = stringResource(
                        R.string.a11y_fast_bar,
                        bar.length.hoursMinutes(),
                        stringResource(if (bar.completed) R.string.status_completed else R.string.status_ended),
                    )
                    Column(
                        Modifier.fillMaxHeight().clickable { onEditFast(bar.fast.id) }.semantics { contentDescription = desc },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text("${bar.length.toHours()}h", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier.width(14.dp).height(maxBar * (hours / maxHours).coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                .background(if (bar.completed) done else early),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            recent.bars.forEach { Text(it.fast.startAt.atZone(zone).format(shortDate), style = MaterialTheme.typography.labelSmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(done, stringResource(R.string.status_completed))
            Legend(early, stringResource(R.string.status_ended))
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- Calendar with fasts drawn across days ----------

@Composable
private fun CalendarCard(st: HistoryState, onShift: (Long) -> Unit, onEditFast: (Long) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val prev = stringResource(R.string.a11y_prev_month)
            val next = stringResource(R.string.a11y_next_month)
            TextButton(onClick = { onShift(-1) }) { Text("‹", Modifier.semantics { contentDescription = prev }) }
            Text(
                "${st.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${st.month.year}",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            Text(
                totalLabel(st.monthTotal),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { onShift(1) }) { Text("›", Modifier.semantics { contentDescription = next }) }
        }
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        st.weeks.forEach { row -> WeekRowView(row, st.month, st.today, onEditFast) }
    }
}

private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

@Composable
private fun WeekRowView(row: WeekRow, month: YearMonth, today: LocalDate, onEditFast: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (0L until 7L).map { row.weekStart.plusDays(it) }.forEach { day ->
                val inMonth = YearMonth.from(day) == month
                val isToday = day == today
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (inMonth) {
                        val label = day.format(longDate)
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clearAndSetSemantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp)) {
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
        seg.fast.isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        seg.fast.status == FastStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val onColor = if (seg.fast.status == FastStatus.COMPLETED || seg.fast.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface
    val desc = stringResource(R.string.a11y_fast_bar, seg.totalLength.hoursMinutes(), stringResource(statusLabel(seg.fast.status)))
    Box(
        Modifier.offset(x = dayWidth * seg.startDay).width(width).fillMaxHeight().padding(horizontal = 1.dp)
            .clip(shape).background(color)
            .clickable { onEditFast(seg.fast.id) }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (seg.showLabel && width >= 20.dp) {
            // Narrow pieces (a fast split across two week rows) get a smaller label.
            val style = if (width >= 30.dp) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
            Text("${seg.totalLength.toHours()}h", style = style, fontWeight = FontWeight.SemiBold, color = onColor, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun FastRow(f: Fast, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        val length = Duration.between(f.startAt, f.endedAt ?: java.time.Instant.now())
        Text("${Presets.byId(f.protocolId)?.name ?: f.protocolId} · ${length.hoursMinutes()}", style = MaterialTheme.typography.titleSmall)
        Text(f.startAt.formatDateTime(), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(statusLabel(f.status)), style = MaterialTheme.typography.bodySmall)
        f.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun statusLabel(s: FastStatus) = when (s) {
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
