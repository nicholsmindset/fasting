package com.fastingshift.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Receives fired alarms and the "End fast" notification action. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var service: FastService
    @Inject lateinit var coordinator: ReminderCoordinator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REMINDER -> onReminder(context, intent)
                    ACTION_BILLING_REMINDER -> {
                        val expires = java.time.Instant.ofEpochMilli(intent.getLongExtra(EXTRA_EXPIRES_AT, 0))
                        val date = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                            .format(expires.atZone(java.time.ZoneId.systemDefault()))
                        Notifications.showBillingReminder(context, intent.getBooleanExtra(EXTRA_TRIAL, false), date)
                    }
                    ACTION_END_FAST -> {
                        service.endActive()
                        Notifications.cancelFastNotifications(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onReminder(context: Context, intent: Intent) {
        val reminder = AndroidAlarmGateway.parse(intent) ?: return
        val active = fasts.getActive()
        when (reminder.kind) {
            ReminderKind.FAST_START -> if (active == null) Notifications.showStartReminder(context)
            else -> {
                // Ignore stale alarms for a fast that has ended or been replaced.
                if (active == null || active.id != reminder.fastId) {
                    coordinator.reschedule()
                    return
                }
                Notifications.showForReminder(context, reminder, active)
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.fastingshift.app.action.REMINDER"
        const val ACTION_END_FAST = "com.fastingshift.app.action.END_FAST"
        const val ACTION_BILLING_REMINDER = "com.fastingshift.app.action.BILLING_REMINDER"
        const val EXTRA_TRIAL = "trial"
        const val EXTRA_EXPIRES_AT = "expires_at"
        const val EXTRA_KIND = "kind"
        const val EXTRA_FAST_ID = "fast_id"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TRIGGER_AT = "trigger_at"
    }
}
