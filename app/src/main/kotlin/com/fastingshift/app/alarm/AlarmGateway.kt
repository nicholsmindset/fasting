package com.fastingshift.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.Duration
import java.time.Instant

/** Thin seam over AlarmManager so scheduling logic can be unit-tested on the JVM. */
interface AlarmGateway {
    fun canScheduleExact(): Boolean
    fun setExact(reminder: Reminder)
    fun setWindowed(reminder: Reminder, window: Duration)
    fun cancel(requestCode: Int)
}

class AndroidAlarmGateway(
    private val context: Context,
    private val alarmManager: AlarmManager,
) : AlarmGateway {

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun setExact(reminder: Reminder) {
        // setExactAndAllowWhileIdle fires in Doze; guarded by canScheduleExact() above.
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt.toEpochMilli(), pendingIntent(reminder))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call: fall back to a windowed alarm.
            setWindowed(reminder, AlarmScheduler.FALLBACK_WINDOW)
        }
    }

    override fun setWindowed(reminder: Reminder, window: Duration) {
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, reminder.triggerAt.toEpochMilli(), window.toMillis(), pendingIntent(reminder))
    }

    override fun cancel(requestCode: Int) {
        val existing = PendingIntent.getBroadcast(
            context, requestCode, baseIntent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun baseIntent() = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_REMINDER)

    private fun pendingIntent(reminder: Reminder): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.requestCode,
        baseIntent()
            .putExtra(AlarmReceiver.EXTRA_KIND, reminder.kind.name)
            .putExtra(AlarmReceiver.EXTRA_FAST_ID, reminder.fastId)
            .putExtra(AlarmReceiver.EXTRA_INDEX, reminder.index)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, reminder.triggerAt.toEpochMilli()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        fun parse(intent: Intent): Reminder? {
            val kind = intent.getStringExtra(AlarmReceiver.EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return null
            return Reminder(
                kind = kind,
                triggerAt = Instant.ofEpochMilli(intent.getLongExtra(AlarmReceiver.EXTRA_TRIGGER_AT, 0)),
                fastId = intent.getLongExtra(AlarmReceiver.EXTRA_FAST_ID, -1),
                index = intent.getIntExtra(AlarmReceiver.EXTRA_INDEX, 0),
            )
        }
    }
}
