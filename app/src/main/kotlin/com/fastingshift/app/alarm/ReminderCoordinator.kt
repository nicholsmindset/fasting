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
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
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
    private val screening: ScreeningRepository,
    private val billing: BillingManager,
    private val billingReminders: BillingReminderScheduler,
    gateway: AlarmGateway,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val scheduler = AlarmScheduler(gateway)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    /** Starts following the database. Call off the main thread: it opens the database. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(fasts.observeActive(), settings.settings, schedules.observeAll(), screening.observeLatest()) { active, s, sch, scr ->
                listOf(active, s, sch, scr?.gateResult)
            }
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
        } else if (!mayStartFasts()) {
            // Unscreened or education-only users are never nudged to start a fast (SR-1).
            emptyList()
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

    private suspend fun mayStartFasts(): Boolean {
        val gate = screening.getLatest()?.gateResult
        return gate != null && gate != GateResult.BLOCK
    }

    /** Fetches customer info (cached when offline) and re-arms the billing reminder. */
    suspend fun rearmBilling() {
        billing.refresh()
        billingReminders.apply(billing.loadedEntitlement.value, settings.current().billingRemindedFor, clock.instant())
    }
}
