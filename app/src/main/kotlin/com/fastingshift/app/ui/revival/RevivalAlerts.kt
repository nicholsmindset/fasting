package com.fastingshift.app.ui.revival

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.components.appNotificationsEnabled
import com.fastingshift.domain.revival.RevivalCalendar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RevivalEntryPoint { fun revivalEvents(): RevivalEvents }

internal object RevivalAlerts {
    private const val TAG = "streak-revival-alert"
    private fun prefs(c: Context) = c.getSharedPreferences("seasonal_events", Context.MODE_PRIVATE)
    fun enabled(c: Context): Boolean = prefs(c).getBoolean("notify", false)
    fun setEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("notify", enabled).apply()
        if (enabled) scheduleNext(c, Instant.now()) else WorkManager.getInstance(c).cancelAllWorkByTag(TAG)
    }

    fun scheduleNext(context: Context, now: Instant) {
        if (!enabled(context)) return
        val zone = ZoneId.systemDefault()
        val year = now.atZone(zone).year
        val next = (year..year + 1).flatMap { RevivalCalendar.windows(it, zone) }.map {
            it.id to it.start.atZone(zone).toLocalDate().atTime(10, 0).atZone(zone).toInstant()
        }.filter { it.second > now }.minByOrNull { it.second } ?: return
        val request = OneTimeWorkRequestBuilder<RevivalAlertWorker>()
            .setInputData(workDataOf("event" to next.first))
            .setInitialDelay(Duration.between(now, next.second).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG-${next.first}", ExistingWorkPolicy.KEEP, request)
    }
}

class RevivalAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!RevivalAlerts.enabled(context)) return Result.success()
        val event = inputData.getString("event") ?: return Result.success()
        RevivalAlerts.scheduleNext(context, Instant.now())
        val channelOn = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(Notifications.CHANNEL_REMINDERS)?.importance != NotificationManager.IMPORTANCE_NONE
        if (!appNotificationsEnabled(context) || !channelOn) return Result.success()
        val events = EntryPointAccessors.fromApplication(context, RevivalEntryPoint::class.java).revivalEvents()
        if (events.claimStartAlert(event) && RevivalAlerts.enabled(context)) Notifications.showRevival(context)
        return Result.success()
    }
}
