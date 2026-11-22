package com.fastingshift.domain.insights

import com.fastingshift.domain.history.History
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One sleep session (e.g. read from Health Connect), reduced to its times. */
data class SleepSession(val start: Instant, val end: Instant) {
    init {
        require(end.isAfter(start)) { "a sleep session must end after it starts" }
    }

    val duration: Duration get() = Duration.between(start, end)
}

/** One resting heart rate sample, in beats per minute. */
data class RestingHeartRateSample(val at: Instant, val bpm: Double)

/** Average sleep on nights after a fasting day vs other nights. */
data class SleepComparison(
    val fastingNights: Int,
    val otherNights: Int,
    val afterFastingDay: Duration,
    val otherwise: Duration,
) {
    val difference: Duration get() = afterFastingDay.minus(otherwise)
}

data class WeeklyRestingHeartRate(val weekStart: LocalDate, val averageBpm: Double, val fastingWeek: Boolean)

/** Resting heart rate, averaged per week, in fasting weeks vs other weeks. */
data class RestingHeartRateComparison(
    val fastingWeeks: Int,
    val otherWeeks: Int,
    val fastingAverageBpm: Double,
    val otherAverageBpm: Double,
    /** Every week with samples, oldest first: the trend. */
    val weekly: List<WeeklyRestingHeartRate>,
) {
    val difference: Double get() = fastingAverageBpm - otherAverageBpm
}

/** Average glucose (mmol/L) for readings taken during a fast vs outside one. */
data class GlucoseComparison(
    val duringFastReadings: Int,
    val outsideReadings: Int,
    val duringFastMmolPerL: Double,
    val outsideMmolPerL: Double,
) {
    val differenceMmolPerL: Double get() = duringFastMmolPerL - outsideMmolPerL
}

/** Each part is null when there is too little data to say anything honest. */
data class BodySignalsReport(
    val sleep: SleepComparison? = null,
    val restingHeartRate: RestingHeartRateComparison? = null,
    val glucose: GlucoseComparison? = null,
) {
    val isEmpty: Boolean get() = sleep == null && restingHeartRate == null && glucose == null
}

/** Count, average, minimum and maximum of one kind of reading over a period. */
data class MetricSummary(
    val type: MetricType,
    /** Glucose in the unit asked for; ketones always in mmol/L. */
    val unit: MetricUnit,
    val count: Int,
    val average: Double,
    val min: Double,
    val max: Double,
)

/**
 * Body signals next to fasting (Shift+ insights). Descriptive only, and always a correlation,
 * not a cause: sleep, heart rate and glucose change for many reasons. Every comparison needs
 * a minimum number of samples on each side and is null otherwise, so the app shows nothing
 * rather than noise.
 */
object BodySignals {
    /** Nights needed on each side (after a fasting day / other nights). */
    const val MIN_NIGHTS = 5

    /** Weeks with resting heart rate needed on each side. */
    const val MIN_WEEKS = 3

    /** Glucose readings needed on each side (during a fast / outside). */
    const val MIN_GLUCOSE_READINGS = 5

    /** A week with at least this many fasting days is a fasting week. */
    const val FASTING_DAYS_PER_WEEK = 2

    /** A fasting day is a day on which a fast of at least this long ended. */
    val MIN_FAST: Duration = Duration.ofHours(12)

    /** Shorter sleep is treated as a nap and left out. */
    val MIN_NIGHT_SLEEP: Duration = Duration.ofHours(3)

    /** Resting heart rates outside this range are treated as sensor errors. */
    val PLAUSIBLE_BPM = 25.0..220.0

    /** Days (in [zone]) on which a fast of at least [MIN_FAST] ended. Active fasts don't count. */
    fun fastingDays(fasts: List<Fast>, zone: ZoneId): Set<LocalDate> =
        fasts.filter { f -> f.actualDuration?.let { it >= MIN_FAST } == true }
            .mapTo(HashSet()) { it.endedAt!!.atZone(zone).toLocalDate() }

    /**
     * The evening a sleep session belongs to: sleep starting before noon counts for the
     * previous day, so 23:00 and 01:00 starts are the same night.
     */
    fun nightOf(session: SleepSession, zone: ZoneId): LocalDate = session.start.minus(Duration.ofHours(12)).atZone(zone).toLocalDate()

    /** Total sleep per night, merging overlapping sessions (two devices) and leaving out naps. */
    fun sleepPerNight(sleep: List<SleepSession>, zone: ZoneId): Map<LocalDate, Duration> =
        sleep.groupBy { nightOf(it, zone) }
            .mapValues { (_, sessions) -> merged(sessions) }
            .filterValues { it >= MIN_NIGHT_SLEEP }

    /** Average sleep on nights after a fasting day vs other nights; null below [MIN_NIGHTS] each. */
    fun sleepAfterFasting(fasts: List<Fast>, sleep: List<SleepSession>, zone: ZoneId): SleepComparison? {
        val days = fastingDays(fasts, zone)
        val (after, other) = sleepPerNight(sleep, zone).entries.partition { it.key in days }
        if (after.size < MIN_NIGHTS || other.size < MIN_NIGHTS) return null
        return SleepComparison(after.size, other.size, average(after.map { it.value }), average(other.map { it.value }))
    }

    /** Weekly resting heart rate in fasting weeks vs others; null below [MIN_WEEKS] each. */
    fun restingHeartRateByWeek(fasts: List<Fast>, samples: List<RestingHeartRateSample>, zone: ZoneId): RestingHeartRateComparison? {
        val days = fastingDays(fasts, zone)
        val weekly = samples.filter { it.bpm in PLAUSIBLE_BPM }
            .groupBy { History.weekStart(it.at.atZone(zone).toLocalDate()) }
            .map { (week, list) ->
                val fastingDaysInWeek = (0L until 7L).count { week.plusDays(it) in days }
                WeeklyRestingHeartRate(week, list.map { it.bpm }.average(), fastingDaysInWeek >= FASTING_DAYS_PER_WEEK)
            }
            .sortedBy { it.weekStart }
        val (fasting, other) = weekly.partition { it.fastingWeek }
        if (fasting.size < MIN_WEEKS || other.size < MIN_WEEKS) return null
        return RestingHeartRateComparison(
            fasting.size, other.size, fasting.map { it.averageBpm }.average(), other.map { it.averageBpm }.average(), weekly,
        )
    }

    /**
     * Average glucose during a fast (between its start and its end, or [now] if it is still
     * running) vs outside any fast; null below [MIN_GLUCOSE_READINGS] each.
     */
    fun glucoseDuringFasts(fasts: List<Fast>, readings: List<MetricLog>, now: Instant): GlucoseComparison? {
        val glucose = readings.filter { it.type == MetricType.GLUCOSE }
        val (during, outside) = glucose.partition { r -> fasts.any { f -> !r.at.isBefore(f.startAt) && !r.at.isAfter(f.endedAt ?: now) } }
        if (during.size < MIN_GLUCOSE_READINGS || outside.size < MIN_GLUCOSE_READINGS) return null
        return GlucoseComparison(during.size, outside.size, during.map { it.mmolPerL }.average(), outside.map { it.mmolPerL }.average())
    }

    fun report(
        fasts: List<Fast>,
        sleep: List<SleepSession>,
        restingHeartRate: List<RestingHeartRateSample>,
        readings: List<MetricLog>,
        zone: ZoneId,
        now: Instant,
    ): BodySignalsReport = BodySignalsReport(
        sleep = sleepAfterFasting(fasts, sleep, zone),
        restingHeartRate = restingHeartRateByWeek(fasts, restingHeartRate, zone),
        glucose = glucoseDuringFasts(fasts, readings, now),
    )

    /**
     * Summary of glucose and ketone readings taken in [from, to) — for the doctor report.
     * Glucose is given in [glucoseUnit], ketones in mmol/L. Types with no readings are left
     * out; the list is ordered glucose, then ketones.
     */
    fun summarise(readings: List<MetricLog>, from: Instant, to: Instant, glucoseUnit: MetricUnit = MetricUnit.MMOL_L): List<MetricSummary> {
        val inPeriod = readings.filter { !it.at.isBefore(from) && it.at.isBefore(to) }
        return MetricType.entries.mapNotNull { type ->
            val unit = if (type == MetricType.KETONE) MetricUnit.MMOL_L else glucoseUnit
            val values = inPeriod.filter { it.type == type }.map { MetricReadings.valueIn(it, unit) }
            if (values.isEmpty()) null else MetricSummary(type, unit, values.size, values.average(), values.min(), values.max())
        }
    }

    private fun merged(sessions: List<SleepSession>): Duration {
        var total = Duration.ZERO
        var curStart: Instant? = null
        var curEnd: Instant? = null
        for (s in sessions.sortedBy { it.start }) {
            if (curEnd == null || s.start.isAfter(curEnd)) {
                if (curStart != null) total = total.plus(Duration.between(curStart, curEnd))
                curStart = s.start
                curEnd = s.end
            } else if (s.end.isAfter(curEnd)) {
                curEnd = s.end
            }
        }
        if (curStart != null) total = total.plus(Duration.between(curStart, curEnd))
        return total
    }

    private fun average(durations: List<Duration>): Duration =
        durations.fold(Duration.ZERO, Duration::plus).dividedBy(durations.size.toLong())
}
