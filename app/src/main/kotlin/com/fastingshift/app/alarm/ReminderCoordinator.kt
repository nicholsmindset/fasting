package com.fastingshift.app.alarm

import android.content.Context
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.billing.BillingReminderScheduler
import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.widget.WidgetUpdater
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.coaching.CoachingPlanner
import com.fastingshift.domain.history.WeeklyRecaps
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.RamadanPlanner
import java.time.Instant
import com.fastingshift.domain.schedule.ScheduleCalculator
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.service.ScreeningRecord
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
                listOf(active, s, sch, eligibleToStart(scr))
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
        schedules.refreshPlanHistory()
        val appSettings = settings.current()
        val active = fasts.getActive()
        scheduler.apply(planAt(now, appSettings, active, fasts.getAll()))

        if (active != null && appSettings.ongoingNotification) Notifications.showOngoing(context, active)
        else Notifications.cancelOngoing(context)
        if (active == null) Notifications.cancelFastNotifications(context)

        // Alarms don't survive a reboot: restore the billing reminder too (no-op until loaded).
        billingReminders.apply(billing.loadedEntitlement.value, appSettings.billingRemindedFor, now)

        WidgetUpdater.updateAll(context)
    }

    /** Evaluate the same rules as scheduling, including settings changed since the alarm was armed. */
    suspend fun isCurrent(reminder: Reminder, active: Fast?): Boolean {
        // Android alarms retain millisecond precision and may arrive late in Doze. Evaluate
        // at their scheduled time so a normal delivery delay does not discard a valid alarm.
        val beforeTrigger = reminder.triggerAt.minusMillis(1)
        val plan = planAt(beforeTrigger, settings.current(), active, fasts.getAll())
        return plan.any { matchesBroadcast(it, reminder) }
    }

    private suspend fun planAt(now: Instant, appSettings: AppSettings, active: Fast?, all: List<Fast>): List<Reminder> {
        val reminderSettings = appSettings.reminders
        val mayStart = eligibleToStart(screening.getLatest())
        val plan: List<Reminder> = if (active != null) {
            val coaching = if (appSettings.coachingEnabled &&
                CoachingPlanner.isCoaching(all.count { it.startAt.isBefore(active.startAt) }, appSettings.onboardedAt?.let(Instant::ofEpochMilli), now)
            ) CoachingPlanner.reminders(active, now) else emptyList()
            ReminderPlanner.planForActiveFast(active, reminderSettings, now) + coaching
        } else if (!mayStart) {
            // Unscreened or education-only users are never nudged to start a fast (SR-1).
            emptyList()
        } else {
            val last = all.firstOrNull { !it.isActive }
            val eat = last?.let { Presets.byId(it.protocolId)?.eatHours ?: appSettings.eatingWindowHours }
            val afterWindow = last?.let { ReminderPlanner.planNextStart(it, eat, reminderSettings, now) }
            val closing = last?.let { ReminderPlanner.planWindowClosing(it, eat, reminderSettings, now) }
            val scheduled = if (reminderSettings.fastStartEnabled) {
                ScheduleCalculator.nextStart(schedules.getAll(), ZoneId.systemDefault(), now, appSettings.planHistory.breakWeeks)
                    ?.let { (_, at) -> Reminder(ReminderKind.FAST_START, at, fastId = 0) }
            } else null
            val zone = ZoneId.systemDefault()
            fun outsideBreak(reminder: Reminder): Boolean =
                !appSettings.planHistory.isBreak(reminder.triggerAt.atZone(zone).toLocalDate())
            listOfNotNull(
                listOfNotNull(afterWindow, scheduled).filter(::outsideBreak).minByOrNull { it.triggerAt },
                closing?.takeIf(::outsideBreak),
            )
        }
        // Ramadan mode: suhoor and iftar reminders for the chosen city (only for people who may fast).
        val city = Cities.byId(appSettings.ramadanCityId)
        val ramadan = if (appSettings.ramadanEnabled && city != null && mayStart) {
            RamadanPlanner.reminders(city, appSettings.ramadanMethod ?: Cities.defaultMethod(city), now)
        } else emptyList()
        val recap = if (appSettings.weeklyRecapEnabled && all.isNotEmpty()) {
            listOf(Reminder(ReminderKind.WEEKLY_RECAP, WeeklyRecaps.nextRecapAt(now, ZoneId.systemDefault()), fastId = 0))
        } else emptyList()
        return plan + ramadan + recap
    }

    /** Fetches customer info (cached when offline) and re-arms the billing reminder. */
    suspend fun rearmBilling() {
        billing.refresh()
        billingReminders.apply(billing.loadedEntitlement.value, settings.current().billingRemindedFor, clock.instant())
    }
}

internal fun eligibleToStart(record: ScreeningRecord?): Boolean =
    record != null && FastPolicy.maxAllowed(record.gateResult, record.clinicianAcknowledged) != null

internal fun matchesBroadcast(planned: Reminder, fired: Reminder): Boolean =
    planned.kind == fired.kind && planned.index == fired.index && planned.fastId == fired.fastId &&
        planned.triggerAt.toEpochMilli() == fired.triggerAt.toEpochMilli()
