package com.fastingshift.domain.program

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProgramDay(
    val day: Int,
    val title: String,
    val lesson: String,
    val targetHours: Int? = null,
    val chapter: String? = null,
)

data class Program(
    val id: String,
    val days: List<ProgramDay>,
    val expectedDays: Int,
    val approvedForRelease: Boolean = false,
) {
    val isPublished: Boolean get() = approvedForRelease && expectedDays > 0 &&
        days.size == expectedDays && days.withIndex().all { (index, item) ->
            item.day == index + 1 && item.title.isNotBlank() && item.lesson.isNotBlank()
        }
}

object GuidedPrograms {
    const val FASTING_SHIFT_21 = "fasting_shift_21"

    // Retain the ID for existing progress and backups until the complete program is released.
    val fastingShift21 = Program(id = FASTING_SHIFT_21, days = emptyList(), expectedDays = 21)

    val all = listOf(fastingShift21).filter { it.isPublished }
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Progress through a program. */
object ProgramProgress {
    /**
     * The day the user is on: the first day not yet completed. Days unlock one per calendar day
     * after the previous one was completed, so the plan cannot be rushed.
     */
    fun currentDay(program: Program, completed: Map<Int, Instant>, zone: ZoneId, today: LocalDate): ProgramDay? {
        if (!program.isPublished) return null
        val next = program.days.firstOrNull { it.day !in completed } ?: return null
        val prev = completed[next.day - 1] ?: return next
        return if (prev.atZone(zone).toLocalDate().isBefore(today)) next else null
    }

    fun isFinished(program: Program, completed: Map<Int, Instant>): Boolean = program.isPublished && program.days.all { it.day in completed }
}
