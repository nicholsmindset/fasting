package com.fastingshift.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.firstweek.FirstPlanWindow
import com.fastingshift.app.ui.firstweek.openPlanCalendar
import com.fastingshift.domain.protocol.Presets
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
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "ms")
class FirstPlanCalendarTest {
    private val plan = FirstPlanWindow(Instant.parse("2026-11-26T21:30:00+08:00"), Presets.W14_10)

    private fun contextLaunching(launch: (Intent) -> Unit): Context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) = launch(intent)
        }

    @Test fun calendarDraftContainsOnlyTheDatedWindowAndANeutralTitle() {
        var started: Intent? = null
        val context = contextLaunching { started = it }
        assertTrue(openPlanCalendar(context, plan))
        val draft = assertNotNull(started)
        assertEquals(Intent.ACTION_INSERT, draft.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, draft.data)
        assertEquals(
            setOf(CalendarContract.Events.TITLE, CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                CalendarContract.EXTRA_EVENT_END_TIME, CalendarContract.Events.AVAILABILITY),
            draft.extras?.keySet(),
        )
        assertEquals("Masa peribadi", draft.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(plan.start.toEpochMilli(), draft.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(
            Instant.parse("2026-11-27T11:30:00+08:00").toEpochMilli(),
            draft.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1),
        )
        assertEquals(CalendarContract.Events.AVAILABILITY_FREE, draft.getIntExtra(CalendarContract.Events.AVAILABILITY, -1))
        assertNull(draft.clipData)
    }

    @Test fun absentOrBlockedCalendarEditorIsRecoverable() {
        assertFalse(openPlanCalendar(contextLaunching { throw ActivityNotFoundException() }, plan))
        assertFalse(openPlanCalendar(contextLaunching { throw SecurityException() }, plan))
    }
}
