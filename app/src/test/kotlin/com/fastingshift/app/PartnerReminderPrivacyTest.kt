package com.fastingshift.app

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.MainActivity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en")
class PartnerReminderPrivacyTest {
    @Test fun permissionControlsDeliveryAndTheReminderContainsNoPartnerOrFastDetails() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        Notifications.createChannels(app)
        shadowOf(manager).setNotificationsEnabled(true)

        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.showPartnerReminder(app)
        assertTrue(manager.activeNotifications.isEmpty())

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.showPartnerReminder(app)
        val delivered = manager.activeNotifications.single()
        val notification = delivered.notification
        assertEquals(Notifications.CHANNEL_REMINDERS, notification.channelId)
        assertEquals("Your check-in reminder", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val expectedBody = "You asked for a reminder to check in. A rest day counts too."
        assertEquals(expectedBody, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(expectedBody, notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())

        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals("partner", intent.getStringExtra(Notifications.EXTRA_ROUTE))
        assertEquals(setOf(Notifications.EXTRA_ROUTE), intent.extras?.keySet())
        assertNull(intent.data)

        Notifications.showPartnerReminder(app)
        assertEquals(delivered.id, manager.activeNotifications.single().id)
        manager.cancelAll()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.showPartnerReminder(app)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
