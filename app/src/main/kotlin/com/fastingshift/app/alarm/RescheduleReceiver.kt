package com.fastingshift.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Alarms are cleared on reboot, and wall-clock alarms may be wrong after a time or time-zone
 * change. Because the fast is stored as UTC instants, rebuilding the plan from the database
 * is all that is needed. The billing reminder is re-armed here too.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    // Lazy: injection runs on the main thread, and the coordinator opens the database.
    @Inject lateinit var coordinator: Lazy<ReminderCoordinator>
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(AlarmReceiver.RECEIVER_TIMEOUT_MS) {
                    if (FastingDatabase.keyStatus(context) == DatabaseKeyProvider.Status.LOST) return@withTimeoutOrNull
                    val c = coordinator.get()
                    c.reschedule()
                    c.rearmBilling()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
