package com.fastingshift.app.billing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fastingshift.app.alarm.AlarmReceiver
import com.fastingshift.domain.billing.Entitlement
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules the trial-ending and annual-renewal reminders (paywall rule 3). */
@Singleton
class BillingReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** [entitlement] is null until real customer info has loaded; then nothing changes. */
    fun apply(entitlement: Entitlement?, remindedFor: Long?, now: Instant) =
        apply(BillingReminderDecision.decide(entitlement, remindedFor, now))

    fun apply(action: BillingReminderAction) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_BILLING_REMINDER)
        when (action) {
            BillingReminderAction.Keep -> Unit
            BillingReminderAction.Cancel ->
                PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                    ?.let { alarmManager.cancel(it); it.cancel() }
            is BillingReminderAction.Schedule -> {
                intent.putExtra(AlarmReceiver.EXTRA_TRIAL, action.trial).putExtra(AlarmReceiver.EXTRA_EXPIRES_AT, action.expiresAtMillis)
                val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                // Not time-critical: a one-hour window is fine and needs no exact-alarm permission.
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, action.at.toEpochMilli(), 60 * 60 * 1000L, pi)
            }
        }
    }

    private companion object {
        const val REQUEST_CODE = 8001
    }
}
