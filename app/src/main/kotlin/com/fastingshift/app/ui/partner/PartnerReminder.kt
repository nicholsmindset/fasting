package com.fastingshift.app.ui.partner

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fastingshift.app.R
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.components.openAppNotificationSettings
import com.fastingshift.app.ui.components.startReminderNotificationsEnabled
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/** The next occurrence of the chosen local time, accounting for local clock changes. */
internal fun nextPartnerReminder(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val next = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (next.isAfter(now)) next else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

@Composable
internal fun PartnerReminderRow(weekId: String, enabledForWeek: Boolean) {
    if (!enabledForWeek) return
    val context = LocalContext.current
    val work = remember(context) { WorkManager.getInstance(context) }
    val name = "partner-check-in-reminder-$weekId"
    val schedule = remember(work, name) { work.getWorkInfosForUniqueWorkFlow(name) }
    val requests by schedule.collectAsStateWithLifecycle(initialValue = emptyList<WorkInfo>())
    val at = requests.firstOrNull { !it.state.isFinished }?.tags?.firstNotNullOfOrNull {
        it.removePrefix("at:").toLongOrNull()
    }
    val allowed = startReminderNotificationsEnabled()
    Column {
        at?.let {
            val display = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .format(Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
            Text(stringResource(R.string.partner_remind_scheduled, display))
        }
        TextButton(onClick = {
            if (!allowed) {
                openAppNotificationSettings(context)
            } else {
                val initial = at?.let { timestamp ->
                    Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                } ?: LocalTime.of(18, 0)
                TimePickerDialog(context, { _, hour, minute ->
                    val now = ZonedDateTime.now()
                    val target = nextPartnerReminder(now, LocalTime.of(hour, minute))
                    val epoch = target.toInstant().toEpochMilli()
                    val request = OneTimeWorkRequestBuilder<PartnerReminderWorker>()
                        .setInitialDelay(Duration.between(now, target).toMillis(), TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf("week" to weekId, "at" to epoch))
                        .addTag("at:$epoch").build()
                    work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
                }, initial.hour, initial.minute, DateFormat.is24HourFormat(context)).show()
            }
        }) {
            Text(stringResource(if (!allowed) R.string.partner_remind_settings else if (at == null)
                R.string.partner_remind_set else R.string.partner_remind_change))
        }
        if (at != null) TextButton(onClick = { work.cancelUniqueWork(name) }) {
            Text(stringResource(R.string.partner_remind_cancel))
        }
    }
}

class PartnerReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("week") ?: return Result.success()
        val due = Instant.ofEpochMilli(inputData.getLong("at", 0))
        if (Instant.now().isAfter(due.plus(Duration.ofHours(1)))) return Result.success()
        val repo = EntryPointAccessors.fromApplication(applicationContext, PartnerWorkerEntryPoint::class.java)
            .partnerWeekRepository()
        val week = repo.cachedWeek()
        if (week?.id == id && week.canCheckIn) Notifications.showPartnerReminder(applicationContext)
        return Result.success()
    }
}
