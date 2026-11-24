package com.fastingshift.app.ui.plus

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.domain.history.ScheduleFit
import com.fastingshift.domain.insights.PlanCoverage
import com.fastingshift.domain.insights.ReviewWeek
import com.fastingshift.domain.insights.WeeklyConsistency
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.ramadan.GroupedSheet
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.domain.insights.ScheduleReview
import com.fastingshift.domain.schedule.DayOverride
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Fixed fictional records; the preview never reads a person's history, schedule or reflections. */
internal val reviewExample = listOf(
    ReviewWeek(WeeklyConsistency(LocalDate.of(2026, 6, 22), 3, 4), PlanCoverage.PLANNED,
        changes = mapOf(LocalDate.of(2026, 6, 23) to DayOverride(LocalTime.of(21, 30)),
            LocalDate.of(2026, 6, 26) to DayOverride()), reflection = ScheduleFit.PARTLY),
    ReviewWeek(WeeklyConsistency(LocalDate.of(2026, 6, 15), 0, 0), PlanCoverage.WEEK_OFF),
    ReviewWeek(WeeklyConsistency(LocalDate.of(2026, 6, 8), 4, 4), PlanCoverage.PLANNED,
        changes = mapOf(LocalDate.of(2026, 6, 9) to DayOverride(LocalTime.of(21, 0))), reflection = ScheduleFit.YES),
    ReviewWeek(WeeklyConsistency(LocalDate.of(2026, 6, 1), 2, 3), PlanCoverage.PLANNED),
)

@Composable
internal fun ScheduleReviewSection(weeks: List<ReviewWeek>, example: Boolean, onOpenWeek: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val full = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMd"), locale) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val repeated = remember(weeks) { ScheduleReview.repeatedChanges(weeks) }
    var selectedDay by rememberSaveable(example) { mutableStateOf<DayOfWeek?>(null) }
    selectedDay?.takeIf { it in repeated }?.let { weekday ->
        val format = rememberWeekFormat()
        GroupedSheet(weekday.getDisplayName(TextStyle.FULL, locale), onDismiss = { selectedDay = null }) {
            Text(stringResource(if (example) R.string.review_example_label else R.string.review_period), color = secondary)
            weeks.flatMap { it.changes.entries }.filter { it.key.dayOfWeek == weekday }
                .sortedByDescending { it.key }.forEach { (day, change) ->
                    val detail = change.startTime?.let { stringResource(R.string.plan_moved_to, format.time.format(it)) }
                        ?: stringResource(R.string.week_skipped)
                    GroupRow(day.format(full), modifier = Modifier.semantics(mergeDescendants = true) {}, value = detail)
                }
        }
    }
    Column(Modifier.fillMaxWidth().testTag(if (example) "schedule-review-example" else "schedule-review-personal"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.review_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(if (example) R.string.review_example_label else R.string.review_period),
            style = MaterialTheme.typography.bodyMedium, color = if (example) MaterialTheme.colorScheme.primary else secondary)
        val completed = weeks.sumOf { it.consistency.completedDays }
        val planned = weeks.sumOf { it.consistency.plannedDays }
        AppCard {
            if (planned > 0) {
                Text(pluralStringResource(R.plurals.review_days_done, planned, completed, planned),
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers))
            } else {
                val message = if (weeks.all { it.coverage == PlanCoverage.BEFORE_TRACKING }) R.string.plan_review_new
                    else R.string.plan_review_empty
                Text(stringResource(message), style = MaterialTheme.typography.titleMedium)
            }
            if (repeated.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.plan_repeat_heading), Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = secondary)
                    repeated.forEach { (weekday, count) ->
                        GroupRow(stringResource(R.string.plan_repeat_count, weekday.getDisplayName(TextStyle.FULL, locale), count),
                            showChevron = true, onClick = { selectedDay = weekday })
                    }
                }
            }
            SecondaryButton(stringResource(R.string.review_open_week), onClick = onOpenWeek)
        }
        weeks.forEach { week ->
            val counts = week.consistency
            val monday = counts.weekStart
            val sunday = monday.plusDays(6)
            val range = stringResource(R.string.history_chart_range,
                monday.format(if (monday.year == sunday.year) date else full), sunday.format(full))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(range, style = MaterialTheme.typography.titleMedium)
                val result = when (week.coverage) {
                    PlanCoverage.PLANNED -> pluralStringResource(R.plurals.review_days_done, counts.plannedDays, counts.completedDays, counts.plannedDays)
                    PlanCoverage.WEEK_OFF -> stringResource(R.string.history_day_break)
                    PlanCoverage.BEFORE_TRACKING -> stringResource(R.string.review_untracked)
                    PlanCoverage.NO_PLANNED_DAYS -> stringResource(R.string.history_no_planned_days)
                }
                Text(result, style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers))
                week.trackedFrom?.let { Text(stringResource(R.string.history_tracking_since, it.format(full)), style = MaterialTheme.typography.bodySmall, color = secondary) }
                val changes = listOfNotNull(
                    if (week.movedDates > 0) stringResource(R.string.review_moved, week.movedDates) else null,
                    if (week.skippedDates > 0) stringResource(R.string.review_skipped, week.skippedDates) else null,
                )
                if (changes.isNotEmpty()) Text(changes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = secondary)
                week.reflection?.let { fit ->
                    val answer = when (fit) {
                        ScheduleFit.YES -> R.string.recap_fit_yes
                        ScheduleFit.PARTLY -> R.string.recap_fit_partly
                        ScheduleFit.NO -> R.string.recap_fit_no
                        ScheduleFit.SKIPPED -> null
                    }
                    if (answer != null) Text(stringResource(R.string.review_fit, stringResource(answer)), style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
        }
    }
}
