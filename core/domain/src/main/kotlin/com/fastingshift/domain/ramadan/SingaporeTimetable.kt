package com.fastingshift.domain.ramadan

import java.time.LocalDate
import java.time.LocalTime

/** Local clock times (Asia/Singapore) as printed in the annual MUIS prayer timetables. */
internal object SingaporeTimetable {
    data class FastingTimes(val subuh: LocalTime, val maghrib: LocalTime)

    // MUIS publishes the annual tables at https://www.muis.gov.sg/resources/islamic-calendar/
    // Only the Subuh and Maghrib columns are transcribed, in 24-hour time.
    // The earlier Imsak column in the Ramadan supplement is a separate time.
    private val timesByDate by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/com/fastingshift/domain/ramadan/muis-singapore.csv"))
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).associate { line ->
                val (date, subuh, maghrib) = line.split(',')
                LocalDate.parse(date) to FastingTimes(LocalTime.parse(subuh), LocalTime.parse(maghrib))
            }
        }
    }

    operator fun get(date: LocalDate): FastingTimes? = timesByDate[date]
}
