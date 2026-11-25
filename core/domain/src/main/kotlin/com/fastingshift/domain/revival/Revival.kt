package com.fastingshift.domain.revival

import com.fastingshift.domain.challenge.Hijri
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.streak.StreakCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RevivalWindow(
    val id: String, val start: Instant, val endExclusive: Instant, val zone: ZoneId,
    val target: String = Revival.DAYS_ON_PLAN, val requiredDays: Int = 3,
)

/** A small, separately sourced calendar. Prayer times have no Hijri date column. */
object RevivalCalendar {
    private data class EidDates(val fitri: LocalDate, val adha: LocalDate)
    private val published by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/com/fastingshift/domain/ramadan/muis-key-dates.csv"))
        stream.bufferedReader().useLines { lines -> lines.drop(1).associate {
            val (year, fitri, adha) = it.split(',')
            year.toInt() to EidDates(LocalDate.parse(fitri), LocalDate.parse(adha))
        } }
    }

    fun windows(year: Int, zone: ZoneId): List<RevivalWindow> {
        fun window(id: String, start: LocalDate) = RevivalWindow(id,
            start.atStartOfDay(zone).toInstant(), start.plusDays(14).atStartOfDay(zone).toInstant(), zone)
        return buildList {
            add(window("january-$year", LocalDate.of(year, 1, 1)))
            published[year]?.let { add(window("post-eid-$year", it.fitri.plusDays(1))) }
        }
    }

    fun eligibleDay(date: LocalDate, hijriOffset: Int): Boolean {
        val dates = published[date.year]
        if (dates != null && (date == dates.fitri || date in dates.adha..dates.adha.plusDays(3))) return false
        return !Hijri.isForbiddenFastDay(date, hijriOffset)
    }
}

object Revival {
    const val DAYS_ON_PLAN = "days_on_plan"

    /** Provides the third qualifying completion time even when the app next opens after the event. */
    fun marks(window: RevivalWindow, joinedAt: Instant, fasts: List<Fast>, now: Instant,
              hijriOffset: Int, planned: (LocalDate) -> Boolean): List<Fast> {
        if (window.target != DAYS_ON_PLAN || window.requiredDays <= 0) return emptyList()
        return fasts.asSequence().filter { fast ->
            val ended = fast.endedAt ?: return@filter false
            val day = fast.startAt.atZone(window.zone).toLocalDate()
            fast.status == FastStatus.COMPLETED && fast.startAt >= maxOf(joinedAt, window.start) &&
                ended <= now && ended < window.endExclusive &&
                fast.actualDuration!! >= FastPolicy.MIN_FAST && planned(day) && RevivalCalendar.eligibleDay(day, hijriOffset)
        }.sortedBy { it.endedAt }.distinctBy { it.startAt.atZone(window.zone).toLocalDate() }
            .take(window.requiredDays).toList()
    }

    /** A revived count has an explicit origin. Historical fasts and raw best streak are never rewritten. */
    fun currentRevived(restoredDays: Int, awardDay: LocalDate, fasts: List<Fast>, frozen: Set<LocalDate>,
                       today: LocalDate, zone: ZoneId, planned: (LocalDate) -> Boolean): Int? {
        if (today < awardDay) return null
        val completed = StreakCalculator.completedDays(fasts, zone)
        val active = fasts.filter { it.isActive }.map { it.startAt.atZone(zone).toLocalDate() }.toSet()
        var total = restoredDays
        var day = awardDay.plusDays(1)
        while (day <= today) {
            if (planned(day)) {
                if (day in completed) total++
                else if (day < today && day !in frozen && day !in active) return null
            }
            day = day.plusDays(1)
        }
        return total
    }
}
