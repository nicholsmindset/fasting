package com.fastingshift.domain.ramadan

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** The twelve Hijri months, in order ([number] is 1-based). */
enum class HijriMonth {
    MUHARRAM,
    SAFAR,
    RABI_AL_AWWAL,
    RABI_AL_THANI,
    JUMADA_AL_ULA,
    JUMADA_AL_AKHIRAH,
    RAJAB,
    SHABAN,
    RAMADAN,
    SHAWWAL,
    DHUL_QADAH,
    DHUL_HIJJAH,
    ;

    val number: Int get() = ordinal + 1

    companion object {
        fun of(number: Int): HijriMonth = entries[number - 1]
    }
}

/** A day of the Hijri calendar. [lengthOfMonth] is 29 or 30. */
data class HijriDate(val year: Int, val month: HijriMonth, val day: Int, val lengthOfMonth: Int) {
    val isRamadan: Boolean get() = month == HijriMonth.RAMADAN
}

/**
 * Hijri dates from the JDK's Umm al-Qura calendar ([HijrahChronology]), shifted by the user's
 * offset. Umm al-Qura is a calculated calendar; local moon sighting (e.g. MUIS, JAKIM,
 * Kemenag) often starts a month a day earlier or later, which is what the -2..+2 offset is for.
 * Every function returns null for dates outside the chronology's supported range
 * (about 1300–1600 AH, i.e. 1882–2174 CE) instead of throwing.
 */
object Hijri {
    const val MIN_OFFSET = -2
    const val MAX_OFFSET = 2

    /**
     * The Hijri date for [date]. [offsetDays] moves the Hijri calendar relative to Umm al-Qura:
     * +1 means the local Hijri date is one day ahead (the month started a day earlier).
     */
    fun of(date: LocalDate, offsetDays: Int = 0): HijriDate? = try {
        val h = HijrahDate.from(date.plusDays(offsetDays.coerceIn(MIN_OFFSET, MAX_OFFSET).toLong()))
        HijriDate(
            year = h.get(ChronoField.YEAR),
            month = HijriMonth.of(h.get(ChronoField.MONTH_OF_YEAR)),
            day = h.get(ChronoField.DAY_OF_MONTH),
            lengthOfMonth = h.lengthOfMonth(),
        )
    } catch (e: DateTimeException) {
        null
    }

    /** The Gregorian date of a Hijri day, or null if it doesn't exist or is out of range. */
    fun toGregorian(year: Int, month: HijriMonth, day: Int, offsetDays: Int = 0): LocalDate? = try {
        LocalDate.from(HijrahChronology.INSTANCE.date(year, month.number, day))
            .minusDays(offsetDays.coerceIn(MIN_OFFSET, MAX_OFFSET).toLong())
    } catch (e: DateTimeException) {
        null
    }

    fun isRamadan(date: LocalDate, offsetDays: Int = 0): Boolean = of(date, offsetDays)?.isRamadan == true

    /** Day of the Hijri month (1–30), or null when out of range. */
    fun dayOfMonth(date: LocalDate, offsetDays: Int = 0): Int? = of(date, offsetDays)?.day
}
