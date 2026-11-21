package com.fastingshift.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fastingshift.app.billing.BillingReminderDecision
import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Receives fired alarms and the "End fast" notification action. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    // Lazy: injection runs on the main thread, and these open the encrypted database.
    @Inject lateinit var fasts: Lazy<FastRepository>
    @Inject lateinit var service: Lazy<FastService>
    @Inject lateinit var coordinator: Lazy<ReminderCoordinator>
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                // goAsync gives about 10 seconds; never let a slow database or Keystore hang it.
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    if (intent.action == ACTION_BILLING_REMINDER) {
                        onBillingReminder(context, intent)
                        return@withTimeoutOrNull
                    }
                    // The database can't be read after its key was lost; MainActivity explains.
                    if (FastingDatabase.keyStatus(context) == DatabaseKeyProvider.Status.LOST) return@withTimeoutOrNull
                    when (intent.action) {
                        ACTION_REMINDER -> onReminder(context, intent)
                        ACTION_END_FAST -> onEndFast(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Shown once per expiry date; the stored date stops every later re-arm for it. */
    private suspend fun onBillingReminder(context: Context, intent: Intent) {
        val expiresMillis = intent.getLongExtra(EXTRA_EXPIRES_AT, 0)
        if (!BillingReminderDecision.shouldNotify(expiresMillis, settings.current().billingRemindedFor)) return
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(Instant.ofEpochMilli(expiresMillis).atZone(ZoneId.systemDefault()))
        Notifications.showBillingReminder(context, intent.getBooleanExtra(EXTRA_TRIAL, false), date)
        settings.setBillingRemindedFor(expiresMillis)
    }

    /**
     * Ends the fast from the notification. Rescheduling clears its alarms and the ongoing
     * notification and refreshes widgets; long fasts leave refeeding guidance for the Timer.
     */
    private suspend fun onEndFast(context: Context) {
        val result = service.get().endActive()
        if (result?.refeeding != null) settings.setPendingRefeeding(result.fast.id)
        Notifications.cancelFastNotifications(context)
        coordinator.get().reschedule()
    }

    private suspend fun onReminder(context: Context, intent: Intent) {
        val reminder = AndroidAlarmGateway.parse(intent)
        if (reminder != null) {
            val active = fasts.get().getActive()
            when (reminder.kind) {
                ReminderKind.FAST_START, ReminderKind.EATING_WINDOW_CLOSING -> if (active == null) Notifications.showGeneral(context, reminder)
                // Not tied to a fast: Ramadan times and the weekly recap (only once there is history).
                ReminderKind.SUHOOR, ReminderKind.IFTAR -> Notifications.showGeneral(context, reminder)
                ReminderKind.WEEKLY_RECAP -> if (fasts.get().getAll().isNotEmpty()) Notifications.showGeneral(context, reminder)
                // Stale alarms for a fast that has ended or been replaced are ignored.
                else -> if (active != null && active.id == reminder.fastId) Notifications.showForReminder(context, reminder, active)
            }
        }
        // Always re-plan after a reminder fires: it arms the next scheduled start and rolls
        // the 8-hourly safety check-ins forward (the planner keeps a rolling horizon).
        coordinator.get().reschedule()
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

        /** Receiver work limit, under the ~10 s goAsync allows. */
        const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}
