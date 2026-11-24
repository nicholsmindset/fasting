package com.fastingshift.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.fast.canAddPastFastEndingOn
import com.fastingshift.domain.history.HistoryStats
import com.fastingshift.domain.schedule.ScheduleCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDaySheet(day: LocalDate, st: HistoryState, onEditFast: (Long) -> Unit, onAdd: () -> Unit, onClose: () -> Unit) {
    val detail = remember(st.fasts, st.zone, day, st.now) { HistoryStats.dayDetail(st.fasts, st.zone, day, st.now) }
    val format = rememberWeekFormat()
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(format.locale)
    val time = format.time
    fun endpoint(instant: Instant): String {
        val local = instant.atZone(st.zone)
        return if (local.toLocalDate() == day) time.format(local) else format.dateTime.format(local)
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().testTag("history-day-sheet").verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date.format(day), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                if (day <= st.today) {
                    Text(stringResource(R.string.history_day_total, detail.totalFasting.hoursMinutes()), style = MaterialTheme.typography.titleMedium)
                }
            }
            DayPlan(day, st)
            if (day <= st.today) {
                if (detail.entries.isEmpty()) {
                    Text(stringResource(R.string.history_day_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val editLabel = stringResource(R.string.edit_title)
                    InsetGroup(header = stringResource(R.string.history_day_recorded)) {
                        detail.entries.forEachIndexed { index, entry ->
                            if (index > 0) GroupDivider()
                            val f = entry.fast
                            val end = f.endedAt?.let(::endpoint) ?: stringResource(R.string.history_day_ongoing)
                            val summary = stringResource(R.string.history_day_slice, entry.duration.hoursMinutes(), stringResource(statusLabel(f.status)))
                            GroupRow(
                                title = stringResource(R.string.history_day_interval, endpoint(f.startAt), end),
                                subtitle = summary, showChevron = true, onClick = { onEditFast(f.id) },
                                modifier = Modifier.testTag("history-day-fast-${f.id}").semantics { onClick(label = editLabel, action = null) },
                            )
                        }
                    }
                }
                if (canAddPastFastEndingOn(day, st.zone, st.now)) {
                    SecondaryButton(stringResource(R.string.add_past_button), onClick = onAdd, modifier = Modifier.testTag("history-day-add"))
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.settings_done)) }
        }
    }
}

@Composable
private fun DayPlan(day: LocalDate, st: HistoryState) {
    val plan = st.calendarPlan
    val history = plan.history
    val info = when {
        history.isBreak(day) -> stringResource(R.string.history_day_break)
        day < st.today -> when {
            history.trackedFrom?.let { day >= it } != true -> stringResource(R.string.history_day_unknown)
            history.dayOverrides[day]?.isSkipped == true -> stringResource(R.string.week_skipped)
            history.isPlanned(day) -> stringResource(R.string.history_day_planned)
            else -> stringResource(R.string.week_rest)
        }
        else -> {
            val times = ScheduleCalculator.startsOn(plan.schedules, day, st.zone, history.breakWeeks, history.dayOverrides, plan.firstEvening)
            val format = rememberWeekFormat().time
            when {
                times.isNotEmpty() -> stringResource(R.string.history_day_starts, times.joinToString(" · ") { format.format(it.second.atZone(st.zone)) })
                history.dayOverrides[day]?.isSkipped == true &&
                    ScheduleCalculator.startsOn(plan.schedules, day, st.zone, history.breakWeeks, firstEvening = plan.firstEvening).isNotEmpty() ->
                    stringResource(R.string.week_skipped)
                else -> stringResource(R.string.week_rest)
            }
        }
    }
    InsetGroup(header = stringResource(R.string.history_day_plan)) { GroupRow(info) }
}
