package com.fastingshift.app

import com.fastingshift.app.ui.timer.Today
import kotlin.test.assertEquals
import org.junit.Test

/** The Today card chooses between an explicit and a weight-based water goal. */
class TodayTest {
    @Test fun anExplicitGoalWinsOverTheWeightDefault() {
        assertEquals(3_100, Today.waterGoalMl(3_100, 80.0))
    }

    @Test fun withoutAGoalTheDefaultComesFromTheLatestWeight() {
        assertEquals(2_800, Today.waterGoalMl(null, 80.0))
        assertEquals(2_000, Today.waterGoalMl(null, null))
    }
}
