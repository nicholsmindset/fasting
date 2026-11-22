package com.fastingshift.domain.ramadan

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Calculation methods used across Southeast Asia (sun angles below the horizon for Fajr/Isha,
 * plus a small safety margin, ihtiyati, added to each time). Values follow the published
 * conventions; verify against the official timetable before launch.
 */
enum class PrayerMethod(val fajrAngle: Double, val ishaAngle: Double, val ihtiyatiMinutes: Long) {
    /** Majlis Ugama Islam Singapura. */
    MUIS(20.0, 18.0, 2),

    /** JAKIM (Malaysia). */
    JAKIM(20.0, 18.0, 2),

    /** Kemenag (Indonesia). */
    KEMENAG(20.0, 18.0, 2),

    /** Muslim World League (general fallback). */
    MWL(18.0, 17.0, 0),
}

/** Source for the two fasting boundaries, Fajr and Maghrib. */
enum class FastingTimeSource {
    CALCULATED,
    MUIS_TIMETABLE,
}

data class PrayerDay(
    val date: LocalDate,
    /** End of suhoor / start of the fast. */
    val fajr: Instant,
    val sunrise: Instant,
    val dhuhr: Instant,
    /** Iftar / end of the fast. */
    val maghrib: Instant,
    val isha: Instant,
    val fastingTimeSource: FastingTimeSource = FastingTimeSource.CALCULATED,
)

/**
 * Prayer times from the sun's position (standard astronomical method: solar declination and
 * equation of time from the Julian day, hour angles for each sun altitude). Works offline.
 */
object PrayerTimes {
    /** Times for [date] at a place; returned as instants (format them in the place's zone). */
    fun compute(date: LocalDate, latitude: Double, longitude: Double, method: PrayerMethod): PrayerDay {
        val jd = julianDay(date) - longitude / (15 * 24)
        // Iterate once: compute at approximate times, then refine with the sun at those times.
        var times = doubleArrayOf(5.0, 6.0, 12.0, 18.0, 18.0) // fajr, sunrise, dhuhr, maghrib, isha (hours)
        repeat(2) {
            times = doubleArrayOf(
                sunAngleTime(jd, times[0] / 24, method.fajrAngle, latitude, before = true),
                sunAngleTime(jd, times[1] / 24, RISE_SET_ANGLE, latitude, before = true),
                midDay(jd, times[2] / 24),
                sunAngleTime(jd, times[3] / 24, RISE_SET_ANGLE, latitude, before = false),
                sunAngleTime(jd, times[4] / 24, method.ishaAngle, latitude, before = false),
            )
        }
        fun toInstant(hoursUtcMeanNoon: Double, extraMinutes: Long): Instant {
            // Times above are local mean solar times relative to longitude; convert to UTC hours.
            val utcHours = hoursUtcMeanNoon - longitude / 15
            val seconds = Math.round(utcHours * 3600)
            return date.atStartOfDay(ZoneId.of("UTC")).toInstant().plusSeconds(seconds).plusSeconds(extraMinutes * 60)
        }
        val ih = method.ihtiyatiMinutes
        return PrayerDay(
            date = date,
            fajr = toInstant(times[0], ih),
            sunrise = toInstant(times[1], -ih),
            dhuhr = toInstant(times[2], ih),
            maghrib = toInstant(times[3], ih),
            isha = toInstant(times[4], ih),
        )
    }

    /** Sun altitude at sunrise/sunset: refraction plus the sun's radius. */
    private const val RISE_SET_ANGLE = 0.833

    private fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }

    /** Declination (degrees) and equation of time (hours) for Julian day [jd]. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15
        val eqt = q / 15 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun midDay(jd: Double, dayFraction: Double): Double {
        val eqt = sunPosition(jd + dayFraction).second
        return fixHour(12 - eqt)
    }

    private fun sunAngleTime(jd: Double, dayFraction: Double, angle: Double, lat: Double, before: Boolean): Double {
        val decl = sunPosition(jd + dayFraction).first
        val noon = midDay(jd, dayFraction)
        val cosT = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        // Polar day/night: no such sun angle; fall back to noon +/- 6h (not a concern in SE Asia).
        val t = if (abs(cosT) > 1) 6.0 else darccos(cosT) / 15
        return noon + if (before) -t else t
    }

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)
}
