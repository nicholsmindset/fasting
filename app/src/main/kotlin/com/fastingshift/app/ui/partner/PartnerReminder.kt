package com.fastingshift.app.ui.partner

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.fastingshift.app.ui.design.SwitchGroupRow
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

private fun plannedReminderTag(id: String, date: String) = "partner-plan:$id:$date"

internal fun cancelPartnerPlanReminder(context: Context, id: String, date: String) {
    WorkManager.getInstance(context).cancelAllWorkByTag(plannedReminderTag(id, date))
}

@Composable
internal fun PartnerReminderRow(week: PartnerWeek, plannedDays: Set<String>) {
    val context = LocalContext.current
    val work = remember(context) { WorkManager.getInstance(context) }
    val name = "partner-check-in-reminder-${week.id}"
    val schedule = remember(work, name) { work.getWorkInfosForUniqueWorkFlow(name) }
    val requests by schedule.collectAsStateWithLifecycle(initialValue = emptyList<WorkInfo>())
    val tags = requests.firstOrNull { !it.state.isFinished }?.tags.orEmpty()
    val at = tags.firstNotNullOfOrNull { it.removePrefix("at:").toLongOrNull() }
    val allowed = startReminderNotificationsEnabled()
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    var editing by rememberSaveable(week.id) { mutableStateOf(false) }
    var time by rememberSaveable(week.id) { mutableStateOf("18:00") }
    var silence by rememberSaveable(week.id) { mutableStateOf(false) }
    var start by rememberSaveable(week.id) { mutableStateOf("22:00") }
    var end by rememberSaveable(week.id) { mutableStateOf("08:00") }
    Column {
        at?.let {
            Text(stringResource(R.string.partner_remind_scheduled, format.format(Instant.ofEpochMilli(it).atZone(week.zone))))
        }
        TextButton(onClick = {
            if (!allowed) openAppNotificationSettings(context) else {
                time = tags.firstOrNull { it.startsWith("time:") }?.substringAfter(':')
                    ?: at?.let { Instant.ofEpochMilli(it).atZone(week.zone).toLocalTime().toString() } ?: "18:00"
                start = tags.firstOrNull { it.startsWith("quietStart:") }?.substringAfter(':') ?: "22:00"
                end = tags.firstOrNull { it.startsWith("quietEnd:") }?.substringAfter(':') ?: "08:00"
                silence = tags.any { it.startsWith("quietStart:") }
                editing = true
            }
        }) {
            Text(stringResource(if (!allowed) R.string.partner_remind_settings else if (at == null)
                R.string.partner_remind_set else R.string.partner_remind_change))
        }
        if (at != null) TextButton(onClick = { work.cancelUniqueWork(name) }) {
            Text(stringResource(R.string.partner_remind_cancel))
        }
    }
    if (!editing) return
    val quiet = if (silence) PartnerQuietHours(LocalTime.parse(start), LocalTime.parse(end)) else null
    fun target(now: ZonedDateTime) = partnerReminderTarget(now, LocalTime.parse(time),
        LocalDate.parse(week.startDate), LocalDate.parse(week.endDate), plannedDays, week.self.days.toSet(), quiet)
    val next = target(ZonedDateTime.now(week.zone))
    AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.partner_remind_set)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReminderTimeField(R.string.partner_plan_reminder_time, time) { time = it }
                SwitchGroupRow(stringResource(R.string.partner_quiet_hours), silence, { silence = it })
                if (silence) {
                    ReminderTimeField(R.string.partner_quiet_from, start) { start = it }
                    ReminderTimeField(R.string.partner_quiet_until, end) { end = it }
                }
                Text(when {
                    next != null -> stringResource(R.string.partner_plan_reminder_preview, format.format(next), week.zone.id)
                    quiet?.contains(LocalTime.parse(time)) == true -> stringResource(R.string.partner_quiet_conflict)
                    else -> stringResource(R.string.partner_plan_no_reminder)
                })
            }
        }, confirmButton = {
            TextButton(enabled = allowed && next != null, onClick = {
                val now = ZonedDateTime.now(week.zone)
                val due = target(now)
                if (due != null) {
                    val epoch = due.toInstant().toEpochMilli()
                    val day = due.toLocalDate().toString().takeIf { plannedDays.isNotEmpty() }
                    val request = OneTimeWorkRequestBuilder<PartnerReminderWorker>()
                        .setInitialDelay(Duration.between(now, due).toMillis(), TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf("week" to week.id, "at" to epoch, "planDay" to day,
                            "quietStart" to quiet?.start?.toString(), "quietEnd" to quiet?.end?.toString()))
                        .addTag("at:$epoch").addTag("time:$time")
                    if (day != null) request.addTag(plannedReminderTag(week.id, day))
                    if (quiet != null) request.addTag("quietStart:$start").addTag("quietEnd:$end")
                    work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request.build())
                    editing = false
                }
            }) { Text(stringResource(R.string.partner_plan_save)) }
        }, dismissButton = {
            TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
        })
}

@Composable
private fun ReminderTimeField(label: Int, value: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val time = LocalTime.parse(value)
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    val format = DateTimeFormatter.ofPattern(pattern, locale)
    TextButton(onClick = {
        TimePickerDialog(context, { _, hour, minute -> onPick(LocalTime.of(hour, minute).toString()) },
            time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
    }) { Text(stringResource(label, time.format(format))) }
}

class PartnerReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("week") ?: return Result.success()
        val due = Instant.ofEpochMilli(inputData.getLong("at", 0))
        val now = Instant.now()
        val entry = EntryPointAccessors.fromApplication(applicationContext, PartnerWorkerEntryPoint::class.java)
        if (!entry.screeningRepository().getLatest().partnerEligibility().participant) return Result.success()
        val repo = entry.partnerWeekRepository()
        val week = repo.cachedWeek() ?: return Result.success()
        val planned = inputData.getString("planDay")
        val start = inputData.getString("quietStart")
        val end = inputData.getString("quietEnd")
        val quiet = if (start != null && end != null) PartnerQuietHours(LocalTime.parse(start), LocalTime.parse(end)) else null
        val local = now.atZone(week.zone)
        if (week.id == id && week.canCheckIn && (planned == null || planned in repo.snapshot.value.plannedDays) &&
            canDeliverPartnerReminder(due.atZone(week.zone), local, quiet)) {
            Notifications.showPartnerReminder(applicationContext)
        }
        return Result.success()
    }
}
