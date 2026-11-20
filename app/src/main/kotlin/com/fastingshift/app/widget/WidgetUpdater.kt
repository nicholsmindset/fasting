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
import com.fastingshift.app.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps widgets fresh (PRD F4: never more than 1 minute stale while the screen is on).
 *
 * - Immediately on any start/end/edit (called from the app's DB collector and widget actions).
 * - Every minute while a fast is active, using a non-wakeup RTC alarm: it fires while the
 *   device is awake and waits while asleep, so it costs nothing with the screen off.
 * - A 15-minute WorkManager job as a safety net.
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
        val ep = EntryPointAccessors.fromApplication(context, AppEntryPoint::class.java)
        if (ep.fastRepository().getActive() == null || !hasWidgets(context)) {
            am.cancel(pi)
            return
        }
        val next = System.currentTimeMillis() + 60_000
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExact(AlarmManager.RTC, next, pi) else am.setWindow(AlarmManager.RTC, next, 30_000, pi)
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetUpdater.updateAll(context)
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
