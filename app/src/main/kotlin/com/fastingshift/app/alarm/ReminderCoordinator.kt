package com.fastingshift.app.alarm

import com.fastingshift.app.di.ApplicationScope
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderPlanner
import com.fastingshift.domain.service.FastRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns database state into scheduled alarms. Called whenever the active fast or reminder
 * settings change, and from [RescheduleReceiver] on boot, time change, time-zone change and
 * exact-alarm permission change.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val fasts: FastRepository,
    private val settings: SettingsRepository,
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
            combine(fasts.observeActive(), settings.settings) { active, s -> active to s.reminders }
                .distinctUntilChanged()
                .collect { reschedule() }
        }
    }

    suspend fun reschedule() = mutex.withLock {
        val now = clock.instant()
        val reminderSettings = settings.current().reminders
        val active = fasts.getActive()
        val plan: List<Reminder> = if (active != null) {
            ReminderPlanner.planForActiveFast(active, reminderSettings, now)
        } else {
            val last = fasts.getAll().firstOrNull { !it.isActive }
            listOfNotNull(
                last?.let { ReminderPlanner.planNextStart(it, Presets.byId(it.protocolId)?.eatHours, reminderSettings, now) },
            )
        }
        scheduler.apply(plan)
    }
}
