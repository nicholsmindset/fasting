package com.fastingshift.app.ui.firstweek

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupRow
import java.time.temporal.ChronoUnit

/** Opens an editable copy in the user's calendar. Fasting Shift never reads or writes the calendar. */
internal fun openPlanCalendar(context: Context, plan: FirstPlanWindow): Boolean {
    val end = plan.start.plus(plan.protocol.fastHours.toLong(), ChronoUnit.HOURS)
    val draft = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, context.getString(R.string.first_plan_calendar_event_title))
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, plan.start.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
        putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
    }
    return try {
        context.startActivity(draft)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

@Composable
internal fun AddPlanToCalendar(plan: FirstPlanWindow) {
    val context = LocalContext.current
    var unavailable by remember(plan) { mutableStateOf(false) }
    GroupRow(
        title = stringResource(R.string.first_plan_add_to_calendar),
        subtitle = stringResource(R.string.first_plan_calendar_copy),
        titleColor = MaterialTheme.colorScheme.primary,
        onClick = { unavailable = !openPlanCalendar(context, plan) },
    )
    if (unavailable) {
        Text(
            stringResource(R.string.first_plan_calendar_unavailable),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
