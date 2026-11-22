package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.fastingshift.app.ui.history.WeekPlanCard
import com.fastingshift.domain.schedule.WeekChoice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.history.RecapCopy
import com.fastingshift.app.ui.history.RecapItem
import com.fastingshift.app.ui.history.WeeklyRecapCard
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.history.WeeklyRecaps
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The weekly recap card on History, for a seeded Monday–Thursday. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyRecapCardTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = monday.plusDays(3).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()

    /** 16:8 fasts starting Mon–Wed at 20:00; Tuesday's ended early. Water goal met twice. */
    private fun seededItem(): RecapItem {
        val fasts = (0L..2L).map { d ->
            val start = monday.plusDays(d).atTime(20, 0).atZone(zone).toInstant()
            val f = Fast(id = d + 1, startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = "window_16_8")
            f.end(start.plus(Duration.ofHours(if (d == 1L) 12 else 16)))
        }
        val water = listOf(3_000.0, 1_000.0, 2_600.0).mapIndexed { d, ml ->
            WaterLog(at = monday.plusDays(d.toLong()).atTime(12, 0).atZone(zone).toInstant(), value = ml, unit = VolumeUnit.ML)
        }
        val recap = WeeklyRecaps.compute(fasts, water, zone, monday, now, waterGoalMl = 2_500)
        return RecapItem(recap, isLastWeek = false, daysInWeek = RecapCopy.daysInWeek(monday, monday.plusDays(3)))
    }

    @Test fun showsTheSeededWeek() {
        val item = seededItem()
        assertEquals(2, item.recap.completedToPlan)
        assertEquals(2, item.recap.waterGoalDays)
        compose.setContent { FastingShiftTheme(darkTheme = false) { WeeklyRecapCard(item) } }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithTag("recap_this_week").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.recap_this_week)).assertExists()
        compose.onNodeWithText("44h").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.recap_x_of_y, 2, 3)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.recap_x_of_y, 2, 4)).assertExists()
    }

    @Test fun canChooseAWeekOffChangeItBackAndOpenTheSchedule() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        var opened = false
        compose.setContent {
            var choice by remember { mutableStateOf<WeekChoice?>(null) }
            FastingShiftTheme(darkTheme = false) {
                WeekPlanCard(monday, choice, { choice = it }, { opened = true })
            }
        }
        compose.onNodeWithText(ctx.getString(R.string.history_take_break)).performClick()
        compose.onNodeWithText(ctx.getString(R.string.history_break_selected)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.history_keep_plan)).performClick()
        compose.onNodeWithText(ctx.getString(R.string.history_keep_selected)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.history_edit_schedule)).performClick()
        compose.runOnIdle { kotlin.test.assertTrue(opened) }
    }

    @Test fun theMessageNeverRewardsLength() {
        val item = seededItem()
        // Two of four planned days kept: a calm consistency line, whatever the hours.
        assertEquals(R.string.recap_msg_some, RecapCopy.message(item.recap, isLastWeek = false))
    }
}
