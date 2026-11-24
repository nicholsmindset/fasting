package com.fastingshift.app

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.app.ui.timer.StartEarlierDialog
import com.fastingshift.app.ui.timer.TimerUiState
import com.fastingshift.domain.protocol.Presets
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class EarlierStartTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun show(initial: Instant?, now: Instant, zone: String, onPick: (Instant) -> Unit) {
        compose.setContent {
            FastingShiftTheme {
                StartEarlierDialog(initial, now, {}, onPick, ZoneId.of(zone))
            }
        }
    }

    @Test fun justAfterMidnightDefaultsToYesterdayAndRejectsTheFutureClockTimeToday() {
        val now = Instant.parse("2026-11-24T16:30:00Z")
        var selected: Instant? = null
        show(null, now, "Asia/Singapore") { selected = it }
        val confirm = compose.onNodeWithText(app.getString(R.string.ok))
        confirm.performClick()
        compose.runOnIdle { assertEquals(now.minusSeconds(3600), selected) }
        compose.onNodeWithText(app.getString(R.string.today)).performClick()
        confirm.assertIsNotEnabled()
    }

    @Test fun targetPreviewUsesElapsedHoursAcrossTheSpringClockChange() {
        val zone = ZoneId.of("America/New_York")
        val previousEvening = Instant.parse("2026-03-08T01:00:00Z")
        val state = TimerUiState(selected = Presets.W16_8, now = previousEvening.plusSeconds(7200))
        assertEquals(15, state.selectedTargetEnd.atZone(zone).hour)
        assertEquals(13, state.copy(startAt = previousEvening).selectedTargetEnd.atZone(zone).hour)
        assertEquals(11, state.copy(startAt = previousEvening, selected = Presets.W14_10).selectedTargetEnd.atZone(zone).hour)
    }

    @Test fun reopeningTheLaterOccurrenceOfAFallBackTimeKeepsItsInstant() {
        val secondOccurrence = Instant.parse("2026-11-01T06:30:00Z")
        var selected: Instant? = null
        show(secondOccurrence, secondOccurrence.plusSeconds(3600), "America/New_York") { selected = it }
        compose.onNodeWithText(app.getString(R.string.ok)).performClick()
        compose.runOnIdle { assertEquals(secondOccurrence, selected) }
    }
}
