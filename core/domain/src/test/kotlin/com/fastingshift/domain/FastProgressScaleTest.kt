package com.fastingshift.domain

import com.fastingshift.domain.timeline.BodyTimeline
import com.fastingshift.domain.timeline.FastProgressScale
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastProgressScaleTest {
    @Test fun allLandmarksAreTakenFromTheBodyTimelineAndClippedToThePersonalTarget() {
        for (hours in listOf(1, 12, 16, 18, 20, 24, 36, 48, 72, 120)) {
            val plan = Duration.ofHours(hours.toLong())
            val scale = FastProgressScale.forFast(plan, plan)
            val expected = BodyTimeline.stages.map { it.fromHours * 60 }.filter { it > 0 && it < plan.toMinutes() }
            assertEquals(expected, scale.transitionMinutes)
            assertEquals(expected, scale.segmentMinutes.runningFold(0, Int::plus).drop(1).dropLast(1))
            assertEquals(plan.toMinutes().toInt(), scale.segmentMinutes.sum())
            assertTrue(scale.segmentMinutes.all { it > 0 })
        }
    }

    @Test fun boundsElapsedAndRespectsTargetsThatAreNotAWholeHour() {
        val plan = Duration.ofMinutes(750)
        assertEquals(listOf(720, 30), FastProgressScale.forFast(plan, plan).segmentMinutes)
        assertEquals(0, FastProgressScale.forFast(plan, Duration.ofSeconds(-80)).progress)
        assertEquals(724, FastProgressScale.forFast(plan, Duration.ofMinutes(724)).progress)
        assertEquals(750, FastProgressScale.forFast(plan, plan.plusHours(1)).progress)
    }
}
