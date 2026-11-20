package com.fastingshift.data.db

import androidx.room.TypeConverter
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.Symptom
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Instants are stored as epoch milliseconds (UTC); sets of enums as comma-separated names. */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun medicationsToString(value: Set<Medication>): String = value.joinNames()
    @TypeConverter fun stringToMedications(value: String): Set<Medication> = value.parseNames(Medication::valueOf)

    @TypeConverter fun symptomsToString(value: Set<Symptom>): String = value.joinNames()
    @TypeConverter fun stringToSymptoms(value: String): Set<Symptom> = value.parseNames(Symptom::valueOf)

    @TypeConverter fun daysToString(value: Set<DayOfWeek>): String = value.joinNames()
    @TypeConverter fun stringToDays(value: String): Set<DayOfWeek> = value.parseNames(DayOfWeek::valueOf)

    private fun Set<Enum<*>>.joinNames(): String = map { it.name }.sorted().joinToString(",")

    private fun <T> String.parseNames(parse: (String) -> T): Set<T> =
        split(',').filter { it.isNotBlank() }.map(parse).toSet()
}
