package com.fastingshift.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fastingshift.app.alarm.AlarmReceiver
import com.fastingshift.app.di.AppEntryPoint
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps widgets fresh (PRD F4: never more than 1 minute stale while the screen is on).
 *
 * - Elapsed time is a RemoteViews Chronometer, ticked by the launcher itself.
 * - Immediately on any start/end/edit (called from the coordinator and widget actions).
 * - Every minute while a fast is active, for the ring and remaining time, using a non-wakeup
 *   exact RTC alarm: it fires while the device is awake and waits while asleep. Only when exact
 *   alarms are allowed; a windowed alarm would be stretched to 10 minutes on Android 12+.
 * - A 15-minute WorkManager job as a safety net, enqueued only while a widget is placed.
 */
object WidgetUpdater {
    private const val REQUEST_TICK = 9001
    private const val WORK_NAME = "widget_refresh"

    suspend fun updateAll(context: Context) {
        RingWidget().updateAll(context)
        DetailWidget().updateAll(context)
        scheduleTick(context)
    }

    suspend fun hasWidgets(context: Context): Boolean {
        val m = GlanceAppWidgetManager(context)
        return m.getGlanceIds(RingWidget::class.java).isNotEmpty() || m.getGlanceIds(DetailWidget::class.java).isNotEmpty()
    }

    private suspend fun scheduleTick(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_TICK, Intent(context, WidgetTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (!exact || !hasWidgets(context) || !hasActiveFast(context)) {
            am.cancel(pi)
            return
        }
        try {
            am.setExact(AlarmManager.RTC, System.currentTimeMillis() + 60_000, pi)
        } catch (_: SecurityException) {
            // Exact-alarm permission revoked between the check and the call.
            am.cancel(pi)
        }
    }

    private suspend fun hasActiveFast(context: Context): Boolean {
        if (FastingDatabase.keyStatus(context) == DatabaseKeyProvider.Status.LOST) return false
        return EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java).fastRepository().getActive() != null
    }

    /** Called when the first widget of either kind is placed. */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    /** Called when the last widget of both kinds is removed. */
    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(AlarmReceiver.RECEIVER_TIMEOUT_MS) { WidgetUpdater.updateAll(context) }
            } finally {
                pending.finish()
            }
        }
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }
}
