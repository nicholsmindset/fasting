package com.fastingshift.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fastingshift.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Alarms are cleared on reboot, and wall-clock alarms may be wrong after a time or time-zone
 * change. Because the fast is stored as UTC instants, rebuilding the plan from the database
 * is all that is needed.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: ReminderCoordinator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                coordinator.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
