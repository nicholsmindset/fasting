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
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.coaching.CoachingTip
import java.time.Duration

object Notifications {
    const val CHANNEL_FAST_ALERTS = "fast_alerts"
    const val CHANNEL_CHECK_INS = "check_ins"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_ONGOING = "ongoing_fast"
    const val CHANNEL_ACCOUNT = "account"

    /** Intent extra naming the screen to open (see AppNav routes). */
    const val EXTRA_ROUTE = "route"

    private const val ID_FAST = 1001
    private const val ID_CHECK_IN = 1002
    private const val ID_START = 1003
    private const val ID_ONGOING = 1004
    private const val ID_BILLING = 1005
    private const val ID_HYDRATION = 1007
    private const val ID_COACHING = 1008
    private const val ID_RECAP = 1009
    private const val ID_RAMADAN = 1010

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
                NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_ongoing_desc); setShowBadge(false) },
                NotificationChannel(CHANNEL_ACCOUNT, context.getString(R.string.channel_account), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_account_desc) },
            ),
        )
    }

    fun showForReminder(context: Context, reminder: Reminder, fast: Fast) {
        val (channel, id, title, body) = when (reminder.kind) {
            ReminderKind.FAST_END -> Content(
                CHANNEL_FAST_ALERTS, ID_FAST,
                context.getString(R.string.notif_fast_end_title),
                context.getString(R.string.notif_fast_end_body, fast.plannedDuration.hoursMinutes(context.resources)),
            )
            ReminderKind.MILESTONE -> Content(
                CHANNEL_FAST_ALERTS, ID_FAST,
                context.getString(R.string.notif_milestone_title, Duration.between(fast.startAt, reminder.triggerAt).hoursMinutes(context.resources)),
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
            ReminderKind.NEAR_GOAL -> Content(
                CHANNEL_FAST_ALERTS, ID_FAST,
                context.getString(R.string.notif_near_goal_title),
                context.getString(R.string.notif_near_goal_body, formatTime(fast.targetEndAt)),
            )
            ReminderKind.HYDRATION -> Content(
                CHANNEL_REMINDERS, ID_HYDRATION,
                context.getString(R.string.notif_hydration_title),
                context.getString(R.string.notif_hydration_body),
            )
            ReminderKind.COACHING -> {
                val tip = CoachingTip.entries.getOrNull(reminder.index) ?: return
                Content(CHANNEL_REMINDERS, ID_COACHING, context.getString(coachingTitle(tip)), context.getString(coachingBody(tip)))
            }
            ReminderKind.FAST_START, ReminderKind.EATING_WINDOW_CLOSING, ReminderKind.WEEKLY_RECAP,
            ReminderKind.SUHOOR, ReminderKind.IFTAR, ReminderKind.SUNNAH_FAST -> return showGeneral(context, reminder)
        }
        val route = if (reminder.kind == ReminderKind.SYMPTOM_CHECK_IN) "checkin/${fast.id}" else null
        val builder = base(context, channel, title, body, route)
        if (reminder.kind in setOf(ReminderKind.FAST_END, ReminderKind.MILESTONE, ReminderKind.FORGOT_TO_END, ReminderKind.NEAR_GOAL)) {
            builder.addAction(0, context.getString(R.string.action_end_fast), endFastIntent(context))
        }
        notify(context, id, builder)
    }

    /** Reminders that don't belong to a running fast: next start, window closing, recap, Ramadan. */
    fun showGeneral(context: Context, reminder: Reminder) {
        val (channel, id, title, body) = when (reminder.kind) {
            // My Week reminders have no originating fast. Eating-window reminders do.
            ReminderKind.FAST_START -> Content(
                CHANNEL_REMINDERS, ID_START,
                context.getString(if (reminder.fastId == 0L) R.string.notif_scheduled_start_title else R.string.notif_start_title),
                context.getString(R.string.notif_start_body),
            )
            ReminderKind.EATING_WINDOW_CLOSING -> Content(
                CHANNEL_REMINDERS, ID_START,
                context.getString(R.string.notif_window_closing_title),
                context.getString(R.string.notif_window_closing_body),
            )
            ReminderKind.WEEKLY_RECAP -> Content(
                CHANNEL_REMINDERS, ID_RECAP,
                context.getString(R.string.notif_recap_title),
                context.getString(R.string.notif_recap_body),
            )
            ReminderKind.SUHOOR -> Content(
                CHANNEL_REMINDERS, ID_RAMADAN,
                context.getString(R.string.notif_suhoor_title),
                context.getString(R.string.notif_suhoor_body, formatTime(reminder.triggerAt.plus(com.fastingshift.domain.ramadan.RamadanPlanner.SUHOOR_REMINDER_BEFORE))),
            )
            ReminderKind.IFTAR -> Content(
                CHANNEL_REMINDERS, ID_RAMADAN,
                context.getString(R.string.notif_iftar_title),
                context.getString(R.string.notif_iftar_body),
            )
            ReminderKind.SUNNAH_FAST -> Content(
                CHANNEL_REMINDERS, ID_RAMADAN,
                context.getString(R.string.notif_sunnah_title),
                context.getString(R.string.notif_sunnah_body),
            )
            else -> return
        }
        val route = if (reminder.kind == ReminderKind.WEEKLY_RECAP) "history" else null
        notify(context, id, base(context, channel, title, body, route))
    }

    fun coachingTitle(tip: CoachingTip): Int = when (tip) {
        CoachingTip.HUNGER_WAVES -> R.string.coach_hunger_title
        CoachingTip.HYDRATE -> R.string.coach_hydrate_title
        CoachingTip.ELECTROLYTES -> R.string.coach_electrolytes_title
        CoachingTip.BREAK_GENTLY -> R.string.coach_break_title
    }

    fun coachingBody(tip: CoachingTip): Int = when (tip) {
        CoachingTip.HUNGER_WAVES -> R.string.coach_hunger_body
        CoachingTip.HYDRATE -> R.string.coach_hydrate_body
        CoachingTip.ELECTROLYTES -> R.string.coach_electrolytes_body
        CoachingTip.BREAK_GENTLY -> R.string.coach_break_body
    }

    private fun formatTime(i: java.time.Instant): String =
        java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(i.atZone(java.time.ZoneId.systemDefault()))

    /** Optional ongoing notification with a live chronometer (PRD F3). */
    fun showOngoing(context: Context, fast: Fast) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_fast)
            .setContentTitle(context.getString(R.string.notif_ongoing_title))
            .setContentText(context.getString(R.string.notif_ongoing_body, fast.plannedDuration.hoursMinutes(context.resources)))
            .setUsesChronometer(true)
            .setWhen(fast.startAt.toEpochMilli())
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(context, null))
            .addAction(0, context.getString(R.string.action_end_fast), endFastIntent(context))
        notify(context, ID_ONGOING, builder)
    }

    fun cancelOngoing(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ONGOING)

    /** Paywall rule 3: plain reminders before a trial ends or an annual plan renews. */
    fun showBillingReminder(context: Context, trial: Boolean, date: String) {
        val title = context.getString(if (trial) R.string.notif_trial_title else R.string.notif_renewal_title)
        val body = context.getString(if (trial) R.string.notif_trial_body else R.string.notif_renewal_body, date)
        notify(context, ID_BILLING, base(context, CHANNEL_ACCOUNT, title, body, "settings"))
    }

    fun showPartnerReminder(context: Context) {
        val title = context.getString(R.string.partner_remind_notification)
        val body = context.getString(R.string.partner_remind_note)
        notify(context, 1011, base(context, CHANNEL_REMINDERS, title, body, "partner"))
    }

    fun cancelFastNotifications(context: Context) {
        NotificationManagerCompat.from(context).apply {
            cancel(ID_FAST)
            cancel(ID_CHECK_IN)
        }
    }

    private data class Content(val channel: String, val id: Int, val title: String, val body: String)

    private fun base(context: Context, channel: String, title: String, body: String, route: String? = null) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_fast)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context, route))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun openAppIntent(context: Context, route: String?): PendingIntent = PendingIntent.getActivity(
        context, route?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (route != null) putExtra(EXTRA_ROUTE, route) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun endFastIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_END_FAST),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

}
