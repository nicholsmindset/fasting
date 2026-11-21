package com.fastingshift.app.alarm

import android.content.Context
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.billing.BillingReminderScheduler
import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.widget.WidgetUpdater
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.service.FastRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns database state into everything that lives outside the app: alarms, the ongoing
 * notification, widgets and billing reminders. Runs whenever the active fast, settings or
 * schedules change, and from [RescheduleReceiver] on boot, time change, time-zone change and
 * exact-alarm permission change.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fasts: FastRepository,
    private val settings: SettingsRepository,
    private val schedules: ScheduleRepository,
    private val billing: BillingManager,
    private val billingReminders: BillingReminderScheduler,
    gateway: AlarmGateway,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val scheduler = AlarmScheduler(gateway)
    private val mutex = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(fasts.observeActive(), settings.settings, schedules.observeAll()) { active, s, sch -> Triple(active, s, sch) }
                .distinctUntilChanged()
                .collect { reschedule() }
        }
        scope.launch {
            // Only real customer info: the FREE placeholder at process start must not re-arm.
            combine(billing.loadedEntitlement, settings.settings.map { it.billingRemindedFor }) { e, reminded -> e to reminded }
                .distinctUntilChanged()
                .collect { (e, reminded) -> billingReminders.apply(e, reminded, clock.instant()) }
        }
    }

    suspend fun reschedule() = mutex.withLock {
        val now = clock.instant()
        val appSettings = settings.current()
        val reminderSettings = appSettings.reminders
        val active = fasts.getActive()
        val plan: List<Reminder> = if (active != null) {
            ReminderPlanner.planForActiveFast(active, reminderSettings, now)
        } else {
            val last = fasts.getAll().firstOrNull { !it.isActive }
            val afterWindow = last?.let {
                val eat = Presets.byId(it.protocolId)?.eatHours ?: appSettings.eatingWindowHours
                ReminderPlanner.planNextStart(it, eat, reminderSettings, now)
            }
            val scheduled = if (reminderSettings.fastStartEnabled) {
                ScheduleCalculator.nextStart(schedules.getAll(), ZoneId.systemDefault(), now)
                    ?.let { (_, at) -> Reminder(ReminderKind.FAST_START, at, fastId = 0) }
            } else null
            listOfNotNull(listOfNotNull(afterWindow, scheduled).minByOrNull { it.triggerAt })
        }
        scheduler.apply(plan)

        if (active != null && appSettings.ongoingNotification) Notifications.showOngoing(context, active)
        else Notifications.cancelOngoing(context)
        if (active == null) Notifications.cancelFastNotifications(context)

        // Alarms don't survive a reboot: restore the billing reminder too (no-op until loaded).
        billingReminders.apply(billing.loadedEntitlement.value, appSettings.billingRemindedFor, now)

        WidgetUpdater.updateAll(context)
    }

    /** Fetches customer info (cached when offline) and re-arms the billing reminder. */
    suspend fun rearmBilling() {
        billing.refresh()
        billingReminders.apply(billing.loadedEntitlement.value, settings.current().billingRemindedFor, clock.instant())
    }
}
