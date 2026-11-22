package com.fastingshift.app.ui.history

import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.share.ShareCards
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import com.fastingshift.domain.schedule.WeekChoice
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.domain.history.WeeklyRecap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** A week to recap on History: the current week so far, or last week (shown Mon–Tue). */
data class RecapItem(val recap: WeeklyRecap, val isLastWeek: Boolean, val daysInWeek: Int)

object RecapCopy {
    /**
     * One calm line. It speaks to consistency (planned days kept), never to fast length, so a
     * longer fast is never praised.
     */
    fun message(recap: WeeklyRecap, isLastWeek: Boolean): Int = when {
        recap.isEmpty -> if (isLastWeek) R.string.recap_msg_empty_last else R.string.recap_msg_empty
        recap.plannedDays > 0 && recap.completedDays >= recap.plannedDays -> R.string.recap_msg_all
        recap.plannedDays == 0 -> R.string.history_no_planned_days
        recap.completedDays > 0 -> R.string.recap_msg_some
        else -> R.string.recap_msg_none
    }

    /** Days of the week that count: all 7 for a past week, up to today for this one. */
    fun daysInWeek(weekStart: LocalDate, today: LocalDate): Int =
        (ChronoUnit.DAYS.between(weekStart, today) + 1).toInt().coerceIn(1, 7)
}

@Composable
fun WeeklyRecapCard(item: RecapItem, modifier: Modifier = Modifier) {
    val r = item.recap
    val range = remember(r.weekStart) {
        val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        "${f.format(r.weekStart)} – ${f.format(r.weekStart.plusDays(6))}"
    }
    AppCard(modifier.testTag(if (item.isLastWeek) "recap_last_week" else "recap_this_week")) {
        Column {
            Text(
                stringResource(if (item.isLastWeek) R.string.recap_last_week else R.string.recap_this_week),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth()) {
            RecapStat(stringResource(R.string.recap_hours), totalLabel(r.fastingTime), Modifier.weight(1f))
            RecapStat(stringResource(R.string.recap_to_plan), stringResource(R.string.recap_x_of_y, r.completedToPlan, r.fastsStarted), Modifier.weight(1f))
            RecapStat(stringResource(R.string.recap_water_days), stringResource(R.string.recap_x_of_y, r.waterGoalDays, item.daysInWeek), Modifier.weight(1f))
        }
        Text(
            stringResource(RecapCopy.message(r, item.isLastWeek), r.completedDays, r.plannedDays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!r.isEmpty && r.fastsStarted > 0) {
            val context = LocalContext.current
            PlainButton(stringResource(R.string.share), onClick = { ShareCards.shareWeek(context, r) })
        }
    }
}

@Composable
fun WeekPlanCard(week: LocalDate, choice: WeekChoice?, onChoice: (WeekChoice) -> Unit, onEdit: () -> Unit) {
    val date = week.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    AppCard {
        Text(
            stringResource(R.string.history_next_week, date),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val message = when (choice) {
            WeekChoice.BREAK -> R.string.history_break_selected
            WeekChoice.KEEP -> R.string.history_keep_selected
            null -> R.string.history_next_week_hint
        }
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChoice(WeekChoice.KEEP) }, enabled = choice != WeekChoice.KEEP) {
            Text(stringResource(R.string.history_keep_plan))
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.history_edit_schedule)) }
        TextButton(onClick = { onChoice(WeekChoice.BREAK) }, enabled = choice != WeekChoice.BREAK) {
            Text(stringResource(R.string.history_take_break))
        }
    }
}

@Composable
private fun RecapStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Top) {
        Text(value, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
    }
}
