package com.fastingshift.domain.ramadan

import com.fastingshift.domain.reminders.Reminder
import com.fastingshift.domain.reminders.ReminderKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A city for prayer times, chosen from a list so no location permission is needed. */
data class City(val id: String, val name: String, val country: String, val latitude: Double, val longitude: Double, val zoneId: String) {
    val zone: ZoneId get() = ZoneId.of(zoneId)
}

/** Offline list, Southeast Asia first (the PRD's Ramadan audience). */
object Cities {
    val all: List<City> = listOf(
        City("sg_singapore", "Singapore", "SG", 1.3521, 103.8198, "Asia/Singapore"),
        City("my_kuala_lumpur", "Kuala Lumpur", "MY", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        City("my_johor_bahru", "Johor Bahru", "MY", 1.4927, 103.7414, "Asia/Kuala_Lumpur"),
        City("my_penang", "George Town", "MY", 5.4141, 100.3288, "Asia/Kuala_Lumpur"),
        City("my_kota_kinabalu", "Kota Kinabalu", "MY", 5.9804, 116.0735, "Asia/Kuching"),
        City("bn_bandar_seri_begawan", "Bandar Seri Begawan", "BN", 4.9031, 114.9398, "Asia/Brunei"),
        City("id_jakarta", "Jakarta", "ID", -6.2088, 106.8456, "Asia/Jakarta"),
        City("id_surabaya", "Surabaya", "ID", -7.2575, 112.7521, "Asia/Jakarta"),
        City("id_bandung", "Bandung", "ID", -6.9175, 107.6191, "Asia/Jakarta"),
        City("id_medan", "Medan", "ID", 3.5952, 98.6722, "Asia/Jakarta"),
        City("id_makassar", "Makassar", "ID", -5.1477, 119.4327, "Asia/Makassar"),
        City("ph_manila", "Manila", "PH", 14.5995, 120.9842, "Asia/Manila"),
        City("ph_cotabato", "Cotabato", "PH", 7.2236, 124.2464, "Asia/Manila"),
        City("th_bangkok", "Bangkok", "TH", 13.7563, 100.5018, "Asia/Bangkok"),
        City("th_pattani", "Pattani", "TH", 6.8695, 101.2501, "Asia/Bangkok"),
        City("ae_dubai", "Dubai", "AE", 25.2048, 55.2708, "Asia/Dubai"),
        City("gb_london", "London", "GB", 51.5074, -0.1278, "Europe/London"),
        City("au_sydney", "Sydney", "AU", -33.8688, 151.2093, "Australia/Sydney"),
    )

    fun byId(id: String?): City? = all.firstOrNull { it.id == id }

    /** Default method for a city's country. */
    fun defaultMethod(city: City): PrayerMethod = when (city.country) {
        "SG" -> PrayerMethod.MUIS
        "MY", "BN" -> PrayerMethod.JAKIM
        "ID" -> PrayerMethod.KEMENAG
        else -> PrayerMethod.MWL
    }
}

/** Where the user is in the Ramadan day. */
sealed interface RamadanPhase {
    val fastingTimeSource: FastingTimeSource

    /** Between Fajr and Maghrib: fasting until [iftar]. */
    data class Fasting(
        val suhoorEnded: Instant,
        val iftar: Instant,
        override val fastingTimeSource: FastingTimeSource = FastingTimeSource.CALCULATED,
    ) : RamadanPhase

    /** After Maghrib or before Fajr: eating allowed until [suhoorEnds]. */
    data class Eating(
        val suhoorEnds: Instant,
        val nextIftar: Instant,
        override val fastingTimeSource: FastingTimeSource = FastingTimeSource.CALCULATED,
    ) : RamadanPhase
}

/**
 * Ramadan mode (PRD Phase 1, free). The daily fast runs from Fajr (end of suhoor) to Maghrib
 * (iftar). No weight-loss framing; the existing safety screening still applies.
 */
object RamadanPlanner {
    val SUHOOR_REMINDER_BEFORE: Duration = Duration.ofMinutes(30)

    fun day(city: City, method: PrayerMethod, date: LocalDate): PrayerDay {
        val calculated = PrayerTimes.compute(date, city.latitude, city.longitude, method)
        if (city.id != "sg_singapore" || method != PrayerMethod.MUIS) return calculated
        val published = SingaporeTimetable[date] ?: return calculated
        return calculated.copy(
            fajr = date.atTime(published.subuh).atZone(city.zone).toInstant(),
            maghrib = date.atTime(published.maghrib).atZone(city.zone).toInstant(),
            fastingTimeSource = FastingTimeSource.MUIS_TIMETABLE,
        )
    }

    /** Returns null when the city has no reliable pair of boundaries for this fast. */
    fun phase(city: City, method: PrayerMethod, now: Instant): RamadanPhase? {
        val today = now.atZone(city.zone).toLocalDate()
        val current = day(city, method, today)
        val sunset = current.maghrib ?: return null
        if (now >= sunset) {
            val next = day(city, method, today.plusDays(1))
            return RamadanPhase.Eating(next.fajr ?: return null, next.maghrib ?: return null, next.fastingTimeSource)
        }

        val dawn = current.fajr ?: return null
        return if (now < dawn) RamadanPhase.Eating(dawn, sunset, current.fastingTimeSource)
        else RamadanPhase.Fasting(dawn, sunset, current.fastingTimeSource)
    }

    /** Next suhoor ("ends in 30 min") and iftar reminders still ahead of [now]. */
    fun reminders(city: City, method: PrayerMethod, now: Instant): List<Reminder> {
        val today = now.atZone(city.zone).toLocalDate()
        val days = listOf(day(city, method, today), day(city, method, today.plusDays(1)))
        val suhoor = days.mapNotNull { it.fajr?.minus(SUHOOR_REMINDER_BEFORE) }.firstOrNull { it.isAfter(now) }
        val iftar = days.mapNotNull { it.maghrib }.firstOrNull { it.isAfter(now) }
        return listOfNotNull(
            suhoor?.let { Reminder(ReminderKind.SUHOOR, it, fastId = 0) },
            iftar?.let { Reminder(ReminderKind.IFTAR, it, fastId = 0) },
        )
    }
}
