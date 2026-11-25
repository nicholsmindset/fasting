package com.fastingshift.app

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.notifications.LiveFastUpdate
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.domain.model.Fast
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class LiveFastUpdateTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val start = Instant.parse("2026-11-25T00:00:00Z")
    private val fast = Fast(7, start, start.plusSeconds(16 * 3600L), protocolId = "window_16_8")
    private val now = start.plusSeconds((12 * 60 + 4) * 60L)

    @Test fun android16RequestsPromotionWithAllDocumentedCharacteristicsAndKeepsEndAction() {
        Notifications.createChannels(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertTrue(manager.getNotificationChannel(Notifications.CHANNEL_ONGOING).importance > NotificationManager.IMPORTANCE_MIN)
        val n = Notifications.ongoingBuilder(app, fast, now, tracking = true, promoted = true).build()
        // Robolectric 4.16 uses the original Android 16 image. Its legacy predicate
        // requires colorization, which the current Android Live Update specification forbids.
        assertTrue(NotificationCompat.isRequestPromotedOngoing(n))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals("android.app.Notification\$ProgressStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals("16:8 fast · 12h 04m", n.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("12:04", NotificationCompat.getShortCriticalText(n))
        assertNotNull(n.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNotNull(n.actions.single().actionIntent)
        assertNotNull(n.deleteIntent)
        assertNull(n.contentView)
    }

    @Test fun optOutKeepsProgressInTheDrawerAndDismissalLastsForThisFastOnly() {
        val n = Notifications.ongoingBuilder(app, fast, now, tracking = true, promoted = false).build()
        assertFalse(NotificationCompat.isRequestPromotedOngoing(n))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        LiveFastUpdate.rememberDismissal(app, fast.id)
        assertTrue(LiveFastUpdate.dismissed(app, fast.id))
        assertFalse(LiveFastUpdate.dismissed(app, fast.id + 1))
        LiveFastUpdate.clearDismissal(app)
        assertFalse(LiveFastUpdate.dismissed(app, fast.id))
    }

    @Test @Config(sdk = [35]) fun android15RetainsThePlainOngoingNotification() {
        val n = Notifications.ongoingBuilder(app, fast, now, tracking = true, promoted = true).build()
        assertEquals(app.getString(R.string.notif_ongoing_title), n.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNull(n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNull(n.deleteIntent)
    }

    @Test fun ticksUseOriginalStartAndStopAtTheConfiguredTarget() {
        val end = start.plusSeconds(23 * 60)
        assertEquals(start.plusSeconds(15 * 60), LiveFastUpdate.nextUpdate(start, end, start.plusSeconds(120)))
        assertEquals(end, LiveFastUpdate.nextUpdate(start, end, start.plusSeconds(15 * 60)))
        assertNull(LiveFastUpdate.nextUpdate(start, end, end))
    }
}
