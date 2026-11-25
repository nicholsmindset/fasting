package com.fastingshift.domain.schedule

import com.fastingshift.domain.history.History
import com.fastingshift.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.TreeMap

enum class WeekChoice { KEEP, BREAK }

/** A dated change to the recurrence. A null time means the whole date is skipped. */
data class DayOverride(val startTime: LocalTime? = null) {
    val isSkipped: Boolean get() = startTime == null
    fun toRecord(): String = startTime?.toString() ?: "skip"

    companion object {
        fun fromRecord(raw: String): DayOverride? =
            if (raw == "skip") DayOverride() else runCatching { DayOverride(LocalTime.parse(raw)) }.getOrNull()
    }
}

/** Local civil dates match the calendar and schedule editor even across DST changes. */
data class PlanHistory(
    val revisions: Map<LocalDate, Int> = emptyMap(),
    val choices: Map<LocalDate, WeekChoice> = emptyMap(),
    val dayOverrides: Map<LocalDate, DayOverride> = emptyMap(),
) {
    private val masks = TreeMap(revisions)
    val trackedFrom: LocalDate? get() = masks.firstEntry()?.key
    val breakWeeks: Set<LocalDate> = choices.filterValues { it == WeekChoice.BREAK }.keys

    fun withDayOverride(day: LocalDate, override: DayOverride?): PlanHistory =
        copy(dayOverrides = if (override == null) dayOverrides - day else dayOverrides + (day to override))

    fun isBreak(day: LocalDate): Boolean = History.weekStart(day) in breakWeeks

    /** Unknown history has no denominator. A week off also has no planned days. */
    fun isPlanned(day: LocalDate): Boolean = isRoutineDay(day) && dayOverrides[day]?.isSkipped != true

    /** This date was in the recurring plan before a possible one-day move or skip. */
    fun isRoutineDay(day: LocalDate): Boolean {
        val mask = masks.floorEntry(day)?.value ?: return false
        return !isBreak(day) && mask and bit(day.dayOfWeek) != 0
    }

    fun record(day: LocalDate, mask: Int): PlanHistory {
        require(mask in 1..EVERY_DAY)
        if (masks.floorEntry(day)?.value == mask) return this
        val next = TreeMap(masks)
        next[day] = mask
        // Multiple edits on the same day may restore the previous week's plan.
        if (next.lowerEntry(day)?.value == mask) next.remove(day)
        return copy(revisions = next)
    }

    companion object {
        const val EVERY_DAY = 127
        private fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        fun mask(schedules: List<Schedule>): Int {
            val days = schedules.filter { it.enabled }.flatMap { it.days }.toSet()
            return if (days.isEmpty()) EVERY_DAY else days.fold(0) { mask, day -> mask or bit(day) }
        }

        fun fromRecords(
            revisions: Map<String, Int>,
            choices: Map<String, String>,
            dayOverrides: Map<String, String> = emptyMap(),
        ): PlanHistory {
            fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
            return PlanHistory(
                revisions.mapNotNull { (key, mask) ->
                    date(key)?.takeIf { mask in 1..EVERY_DAY }?.let { it to mask }
                }.toMap(),
                choices.mapNotNull { (key, value) ->
                    val day = date(key)?.takeIf { it.dayOfWeek == DayOfWeek.MONDAY } ?: return@mapNotNull null
                    WeekChoice.entries.find { it.name == value }?.let { day to it }
                }.toMap(),
                dayOverrides.mapNotNull { (key, raw) ->
                    val day = date(key) ?: return@mapNotNull null
                    DayOverride.fromRecord(raw)?.let { day to it }
                }.toMap(),
            )
        }
    }
}
