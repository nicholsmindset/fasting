package com.fastingshift.app

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], application = Application::class)
class StartNotificationTest {
    @Test fun startNotificationNamesTheTriggerAndKeepsOneNotificationSlot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Notifications.createChannels(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        val planned = Reminder(ReminderKind.FAST_START, Instant.EPOCH, fastId = 0L)
        Notifications.showGeneral(app, planned)
        val scheduled = manager.activeNotifications.single()
        assertEquals(app.getString(R.string.notif_scheduled_start_title), scheduled.notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(Notifications.CHANNEL_REMINDERS, scheduled.notification.channelId)

        Notifications.showGeneral(app, planned.copy(fastId = 42L))
        val afterEatingWindow = manager.activeNotifications.single()
        assertEquals(scheduled.id, afterEatingWindow.id)
        assertEquals(app.getString(R.string.notif_start_title), afterEatingWindow.notification.extras.getCharSequence(Notification.EXTRA_TITLE))
    }
}
