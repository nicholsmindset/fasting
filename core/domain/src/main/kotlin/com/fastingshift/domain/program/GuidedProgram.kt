package com.fastingshift.domain.program

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProgramDay(
    val day: Int,
    val title: String,
    val lesson: String,
    /** Suggested fasting target for the day, in hours. */
    val targetHours: Int,
    /** Book chapter reference; null until the chapter mapping is decided (PRD open question). */
    val chapter: String? = null,
)

data class Program(val id: String, val name: String, val days: List<ProgramDay>)

/** Shift+ guided program (PRD F10). */
object GuidedPrograms {
    const val FASTING_SHIFT_21 = "fasting_shift_21"

    /**
     * The 21-day plan. Gradual ramp, capped at 18h so the program never pushes beyond what the
     * safety gates allow for every user. LESSON TEXT IS PLACEHOLDER pending the chapter mapping.
     */
    val fastingShift21 = Program(
        id = FASTING_SHIFT_21,
        name = "The Fasting Shift: 21 days",
        days = (1..21).map { d ->
            val target = when (d) {
                in 1..3 -> 12
                in 4..7 -> 14
                in 8..14 -> 16
                else -> if (d % 7 == 0) 18 else 16
            }
            ProgramDay(day = d, title = "Day $d", lesson = "Lesson $d — to be written from The Fasting Shift.", targetHours = target)
        },
    )

    val all = listOf(fastingShift21)
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Progress through a program. */
object ProgramProgress {
    /**
     * The day the user is on: the first day not yet completed. Days unlock one per calendar day
     * after the previous one was completed, so the plan cannot be rushed.
     */
    fun currentDay(program: Program, completed: Map<Int, Instant>, zone: ZoneId, today: LocalDate): ProgramDay? {
        val next = program.days.firstOrNull { it.day !in completed } ?: return null
        val prev = completed[next.day - 1] ?: return next
        return if (prev.atZone(zone).toLocalDate().isBefore(today)) next else null
    }

    fun isFinished(program: Program, completed: Map<Int, Instant>): Boolean = program.days.all { it.day in completed }
}
