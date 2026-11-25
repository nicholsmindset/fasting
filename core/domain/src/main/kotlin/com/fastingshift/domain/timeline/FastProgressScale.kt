package com.fastingshift.domain.timeline

import java.time.Duration

/** Minute-scale geometry of a single planned fast, using the same landmarks as the Timer timeline. */
data class FastProgressScale(val segmentMinutes: List<Int>, val transitionMinutes: List<Int>, val progress: Int) {
    companion object {
        fun forFast(planned: Duration, elapsed: Duration): FastProgressScale {
            val total = planned.toMinutes().coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            val transitions = BodyTimeline.stages.map { it.fromHours * 60 }.filter { it in 1 until total }
            val edges = listOf(0) + transitions + total
            return FastProgressScale(edges.zipWithNext { start, end -> end - start }, transitions,
                elapsed.toMinutes().coerceIn(0L, total.toLong()).toInt())
        }
    }
}
