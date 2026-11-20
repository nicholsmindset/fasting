package com.fastingshift.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Full, versioned export of every user record (PRD F8). Times are epoch milliseconds (UTC);
 * enums are stored by name. The same document is the backup file, so restore is lossless.
 */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    val fasts: List<FastRecord> = emptyList(),
    val protocols: List<ProtocolRecord> = emptyList(),
    val schedules: List<ScheduleRecord> = emptyList(),
    val weights: List<WeightRecord> = emptyList(),
    val water: List<WaterRecord> = emptyList(),
    val electrolytes: List<ElectrolyteRecord> = emptyList(),
    @SerialName("symptom_checkins") val symptomCheckins: List<SymptomCheckinRecord> = emptyList(),
    val screenings: List<ScreeningRecordDto> = emptyList(),
    @SerialName("program_progress") val programProgress: List<ProgramProgressRecord> = emptyList(),
    @SerialName("streak_freezes") val streakFreezes: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "fasting-shift-backup"
        const val VERSION = 1
    }
}

@Serializable data class FastRecord(
    val id: Long, @SerialName("start_at") val startAt: Long, @SerialName("target_end_at") val targetEndAt: Long,
    @SerialName("ended_at") val endedAt: Long? = null, @SerialName("protocol_id") val protocolId: String,
    val status: String, val note: String? = null,
)

@Serializable data class ProtocolRecord(
    val id: String, val name: String, val type: String, @SerialName("fast_hours") val fastHours: Int,
    @SerialName("eat_hours") val eatHours: Int? = null, @SerialName("is_custom") val isCustom: Boolean,
)

@Serializable data class ScheduleRecord(
    val id: Long, @SerialName("protocol_id") val protocolId: String, val days: List<String>,
    @SerialName("start_minute") val startMinute: Int, val enabled: Boolean,
)

@Serializable data class WeightRecord(val id: Long, val at: Long, val value: Double, val unit: String, val source: String)
@Serializable data class WaterRecord(val id: Long, val at: Long, val value: Double, val unit: String, val source: String)
@Serializable data class ElectrolyteRecord(val id: Long, val at: Long, val electrolyte: String, val milligrams: Double, val source: String)

@Serializable data class SymptomCheckinRecord(
    val id: Long, @SerialName("fast_id") val fastId: Long, val at: Long, val answers: List<String>,
    @SerialName("red_flag") val redFlag: Boolean,
)

@Serializable data class ScreeningRecordDto(
    @SerialName("answered_at") val answeredAt: Long, @SerialName("age_years") val ageYears: Int,
    @SerialName("pregnant_or_breastfeeding") val pregnantOrBreastfeeding: Boolean,
    @SerialName("eating_disorder_history") val eatingDisorderHistory: Boolean,
    @SerialName("type1_diabetes") val type1Diabetes: Boolean, val medications: List<String>,
    @SerialName("height_cm") val heightCm: Double, @SerialName("weight_kg") val weightKg: Double,
    @SerialName("gate_result") val gateResult: String, @SerialName("clinician_acknowledged") val clinicianAcknowledged: Boolean,
)

@Serializable data class ProgramProgressRecord(
    @SerialName("program_id") val programId: String, val day: Int, @SerialName("completed_at") val completedAt: Long,
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Parses and validates a backup. Throws [BackupFormatException] for anything unusable. */
    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("This file is not a Fasting Shift backup.", e)
        }
        if (backup.format != Backup.FORMAT) throw BackupFormatException("This file is not a Fasting Shift backup.")
        if (backup.version > Backup.VERSION) throw BackupFormatException("This backup is from a newer version of the app. Please update first.")
        backup.fasts.forEach {
            if (it.targetEndAt <= it.startAt) throw BackupFormatException("Fast ${it.id} has an invalid time range.")
            if (it.endedAt != null && it.endedAt < it.startAt) throw BackupFormatException("Fast ${it.id} ends before it starts.")
        }
        if (backup.fasts.count { it.endedAt == null } > 1) throw BackupFormatException("Backup has more than one active fast.")
        val fastIds = backup.fasts.map { it.id }.toSet()
        backup.symptomCheckins.forEach {
            if (it.fastId !in fastIds) throw BackupFormatException("Check-in ${it.id} refers to a missing fast.")
        }
        return backup
    }
}

/** CSV export: one file per table, RFC 4180 quoting. */
object CsvExport {
    fun files(b: Backup): Map<String, String> = mapOf(
        "fasts.csv" to table(
            listOf("id", "start_at", "target_end_at", "ended_at", "protocol_id", "status", "note"),
            b.fasts.map { listOf(it.id, iso(it.startAt), iso(it.targetEndAt), it.endedAt?.let(::iso), it.protocolId, it.status, it.note) },
        ),
        "weight.csv" to table(listOf("id", "at", "value", "unit", "source"), b.weights.map { listOf(it.id, iso(it.at), it.value, it.unit, it.source) }),
        "water.csv" to table(listOf("id", "at", "value", "unit", "source"), b.water.map { listOf(it.id, iso(it.at), it.value, it.unit, it.source) }),
        "electrolytes.csv" to table(
            listOf("id", "at", "electrolyte", "milligrams", "source"),
            b.electrolytes.map { listOf(it.id, iso(it.at), it.electrolyte, it.milligrams, it.source) },
        ),
        "symptom_checkins.csv" to table(
            listOf("id", "fast_id", "at", "answers", "red_flag"),
            b.symptomCheckins.map { listOf(it.id, it.fastId, iso(it.at), it.answers.joinToString(";"), it.redFlag) },
        ),
    )

    fun table(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(",") { escape(it) })
        rows.forEach { row -> appendLine(row.joinToString(",") { escape(it?.toString() ?: "") }) }
    }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun iso(millis: Long) = java.time.Instant.ofEpochMilli(millis).toString()
}
