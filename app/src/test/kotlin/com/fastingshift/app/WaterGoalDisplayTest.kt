package com.fastingshift.app

import com.fastingshift.app.ui.settings.WaterGoalDisplay
import com.fastingshift.app.ui.settings.formatLiters
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterGoalDisplayTest {
    @Test fun automaticGoalComesFromWeight() {
        val d = WaterGoalDisplay.of(customMl = null, weightKg = 80.0)
        assertTrue(d.automatic)
        assertEquals(2_800, d.ml)
        assertEquals("2.8", formatLiters(d.ml, Locale.US))
    }

    @Test fun automaticWithoutWeightFallsBackToTwoLitres() {
        val d = WaterGoalDisplay.of(null, null)
        assertTrue(d.automatic)
        assertEquals(2_000, d.ml)
        assertEquals("2.0", formatLiters(d.ml, Locale.US))
    }

    @Test fun customGoalWinsButStillKnowsTheAutomaticOne() {
        val d = WaterGoalDisplay.of(customMl = 2_250, weightKg = 80.0)
        assertFalse(d.automatic)
        assertEquals(2_250, d.ml)
        assertEquals(2_800, d.automaticMl)
        assertEquals("2.25", formatLiters(d.ml, Locale.US))
    }

    @Test fun litresUseTheLocaleDecimalSeparator() {
        assertEquals("2,8", formatLiters(2_800, Locale.GERMANY))
    }

    @Test fun stepperMovesIn250mlAndStaysInBounds() {
        assertEquals(3_050, WaterGoalDisplay.stepped(2_800, 1))
        assertEquals(500, WaterGoalDisplay.stepped(600, -1))
        assertEquals(6_000, WaterGoalDisplay.stepped(5_900, 1))
    }
}
