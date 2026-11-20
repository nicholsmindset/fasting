package com.fastingshift.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fastingshift.app.R
import com.fastingshift.app.alarm.AlarmReceiver
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.Duration

object Notifications {
    const val CHANNEL_FAST_ALERTS = "fast_alerts"
    const val CHANNEL_CHECK_INS = "check_ins"
    const val CHANNEL_REMINDERS = "reminders"

    private const val ID_FAST = 1001
    private const val ID_CHECK_IN = 1002
    private const val ID_START = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FAST_ALERTS, context.getString(R.string.channel_fast_alerts), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_fast_alerts_desc) },
                NotificationChannel(CHANNEL_CHECK_INS, context.getString(R.string.channel_check_ins), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_check_ins_desc) },
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_reminders_desc) },
            ),
        )
    }

    fun showForReminder(context: Context, reminder: Reminder, fast: Fast) {
        val (channel, id, title, body) = when (reminder.kind) {
            ReminderKind.FAST_END -> Content(
                CHANNEL_FAST_ALERTS, ID_FAST,
                context.getString(R.string.notif_fast_end_title),
                context.getString(R.string.notif_fast_end_body, fast.plannedDuration.toHoursLabel()),
            )
            ReminderKind.MILESTONE -> Content(
                CHANNEL_FAST_ALERTS, ID_FAST,
                context.getString(R.string.notif_milestone_title, Duration.between(fast.startAt, reminder.triggerAt).toHoursLabel()),
                context.getString(R.string.notif_milestone_body),
            )
            ReminderKind.FORGOT_TO_END -> Content(
                CHANNEL_FAST_ALERTS, ID_FAST,
                context.getString(R.string.notif_forgot_title),
                context.getString(R.string.notif_forgot_body),
            )
            ReminderKind.SYMPTOM_CHECK_IN -> Content(
                CHANNEL_CHECK_INS, ID_CHECK_IN,
                context.getString(R.string.notif_check_in_title),
                context.getString(R.string.notif_check_in_body),
            )
            ReminderKind.FAST_START -> return showStartReminder(context)
        }
        val builder = base(context, channel, title, body)
        if (reminder.kind != ReminderKind.SYMPTOM_CHECK_IN) {
            builder.addAction(0, context.getString(R.string.action_end_fast), endFastIntent(context))
        }
        notify(context, id, builder)
    }

    fun showStartReminder(context: Context) {
        notify(
            context, ID_START,
            base(context, CHANNEL_REMINDERS, context.getString(R.string.notif_start_title), context.getString(R.string.notif_start_body)),
        )
    }

    fun cancelFastNotifications(context: Context) {
        NotificationManagerCompat.from(context).apply {
            cancel(ID_FAST)
            cancel(ID_CHECK_IN)
        }
    }

    private data class Content(val channel: String, val id: Int, val title: String, val body: String)

    private fun base(context: Context, channel: String, title: String, body: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_fast)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun endFastIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_END_FAST),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun Duration.toHoursLabel(): String {
        val h = toHours()
        val m = toMinutes() % 60
        return if (m == 0L) "${h}h" else "${h}h ${m}m"
    }
}
