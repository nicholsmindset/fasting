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
    /** App preferences (v2+). Absent in v1 backups, which restore with current settings kept. */
    val settings: SettingsRecord? = null,
    /** v4+: Ramadan days owed per Hijri year. */
    @SerialName("qada_debts") val qadaDebts: List<QadaDebtRecord> = emptyList(),
    /** v4+: glucose and ketone readings. */
    val metrics: List<MetricRecord> = emptyList(),
    /** v4+: challenges joined on this device. */
    val challenges: List<ChallengeRecord> = emptyList(),
    @SerialName("qada_repayments") val qadaRepayments: List<QadaRepaymentRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = "fasting-shift-backup"
        const val VERSION = 5
    }
}

/** User preferences carried in a backup so a restore on a new phone feels the same (F8). */
@Serializable data class SettingsRecord(
    @SerialName("onboarding_complete") val onboardingComplete: Boolean = false,
    val goal: String? = null,
    @SerialName("default_protocol") val defaultProtocolId: String? = null,
    @SerialName("hide_weight") val hideWeight: Boolean = false,
    @SerialName("ongoing_notification") val ongoingNotification: Boolean = false,
    @SerialName("fast_end_alert") val fastEndAlert: Boolean = true,
    @SerialName("fast_start_alert") val fastStartAlert: Boolean = true,
    @SerialName("milestone_hours") val milestoneHours: List<Int> = emptyList(),
    @SerialName("forgot_to_end_hours") val forgotToEndHours: Int = 4,
    @SerialName("eating_window_hours") val eatingWindowHours: Int = 8,
    @SerialName("active_program") val activeProgramId: String? = null,
    @SerialName("weight_unit") val weightUnit: String = "KG",
    @SerialName("timer_mode") val timerMode: String? = null,
    @SerialName("water_goal_ml") val waterGoalMl: Int? = null,
    @SerialName("near_goal_alert") val nearGoalAlert: Boolean = false,
    @SerialName("window_closing_alert") val windowClosingAlert: Boolean = false,
    @SerialName("hydration_nudges") val hydrationNudges: Boolean = true,
    val coaching: Boolean = true,
    @SerialName("weekly_recap") val weeklyRecap: Boolean = true,
    @SerialName("ramadan_enabled") val ramadanEnabled: Boolean = false,
    @SerialName("ramadan_city") val ramadanCityId: String? = null,
    @SerialName("ramadan_method") val ramadanMethod: String? = null,
    @SerialName("hijri_offset_days") val hijriOffsetDays: Int = 0,
    @SerialName("sunnah_reminders") val sunnahReminders: Boolean = false,
    @SerialName("sunnah_types") val sunnahTypes: List<String> = emptyList(),
    @SerialName("plan_revisions") val planRevisions: Map<String, Int> = emptyMap(),
    @SerialName("week_choices") val weekChoices: Map<String, String> = emptyMap(),
    @SerialName("day_overrides") val dayOverrides: Map<String, String> = emptyMap(),
    @SerialName("week_reflections") val weekReflections: Map<String, String> = emptyMap(),
)

@Serializable data class FastRecord(
    val id: Long, @SerialName("start_at") val startAt: Long, @SerialName("target_end_at") val targetEndAt: Long,
    @SerialName("ended_at") val endedAt: Long? = null, @SerialName("protocol_id") val protocolId: String,
    val status: String, val note: String? = null,
    /** v3+: optional end-of-fast reflection 1..5. */
    val mood: Int? = null,
    /** v4+: REGULAR, RAMADAN, SUNNAH or QADA. */
    val kind: String = "REGULAR",
)

@Serializable data class QadaRepaymentRecord(@SerialName("observed_on") val observedOn: String, @SerialName("hijri_year") val hijriYear: Int)

@Serializable data class QadaDebtRecord(@SerialName("hijri_year") val hijriYear: Int, @SerialName("days_owed") val daysOwed: Int)

@Serializable data class MetricRecord(
    val id: Long, val at: Long, val type: String, val value: Double, val unit: String, val source: String, val note: String? = null,
)

@Serializable data class ChallengeRecord(
    val id: Long, val type: String, @SerialName("protocol_id") val protocolId: String? = null,
    @SerialName("start_date") val startDate: String, @SerialName("length_days") val lengthDays: Int,
    val name: String? = null, @SerialName("joined_at") val joinedAt: Long, @SerialName("left_at") val leftAt: Long? = null,
    @SerialName("follows_own_plan") val followsOwnPlan: Boolean = false,
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
    private fun <T> unique(what: String, ids: List<T>) {
        if (ids.size != ids.toSet().size) throw BackupFormatException("Backup has duplicate $what entries.")
    }

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
        if (backup.version < 1) throw BackupFormatException("This file is not a Fasting Shift backup.")
        if (backup.version > Backup.VERSION) throw BackupFormatException("This backup is from a newer version of the app. Please update first.")
        backup.fasts.forEach {
            if (it.targetEndAt <= it.startAt) throw BackupFormatException("Fast ${it.id} has an invalid time range.")
            if (it.endedAt != null && it.endedAt < it.startAt) throw BackupFormatException("Fast ${it.id} ends before it starts.")
        }
        if (backup.fasts.count { it.endedAt == null } > 1) throw BackupFormatException("Backup has more than one active fast.")
        unique("fast", backup.fasts.map { it.id })
        unique("weight", backup.weights.map { it.id })
        unique("water", backup.water.map { it.id })
        unique("electrolyte", backup.electrolytes.map { it.id })
        unique("check-in", backup.symptomCheckins.map { it.id })
        unique("schedule", backup.schedules.map { it.id })
        unique("protocol", backup.protocols.map { it.id })
        unique("streak freeze", backup.streakFreezes)
        unique("program day", backup.programProgress.map { it.programId to it.day })
        unique("qada repayment date", backup.qadaRepayments.map { it.observedOn })
        unique("qada year", backup.qadaDebts.map { it.hijriYear })
        unique("metric", backup.metrics.map { it.id })
        unique("challenge", backup.challenges.map { it.id })
        backup.schedules.forEach {
            if (it.startMinute !in 0 until 24 * 60) throw BackupFormatException("Schedule ${it.id} has an invalid start time.")
        }
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
            listOf("id", "start_at", "target_end_at", "ended_at", "protocol_id", "status", "note", "mood", "kind"),
            b.fasts.map { listOf(it.id, iso(it.startAt), iso(it.targetEndAt), it.endedAt?.let(::iso), it.protocolId, it.status, it.note, it.mood, it.kind) },
        ),
        "qada_debts.csv" to table(
            listOf("hijri_year", "days_owed"),
            b.qadaDebts.map { listOf(it.hijriYear, it.daysOwed) },
        ),
        "qada_repayments.csv" to table(
            listOf("observed_on", "hijri_year"),
            b.qadaRepayments.map { listOf(it.observedOn, it.hijriYear) },
        ),
        "metrics.csv" to table(
            listOf("id", "at", "type", "value", "unit", "source", "note"),
            b.metrics.map { listOf(it.id, iso(it.at), it.type, it.value, it.unit, it.source, it.note) },
        ),
        "weight.csv" to table(listOf("id", "at", "value", "unit", "source"), b.weights.map { listOf(it.id, iso(it.at), it.value, it.unit, it.source) }),
        "water.csv" to table(listOf("id", "at", "value", "unit", "source"), b.water.map { listOf(it.id, iso(it.at), it.value, it.unit, it.source) }),
        "electrolytes.csv" to table(
            listOf("id", "at", "electrolyte", "milligrams", "source"),
            b.electrolytes.map { listOf(it.id, iso(it.at), it.electrolyte, it.milligrams, it.source) },
        ),
        "protocols.csv" to table(
            listOf("id", "name", "type", "fast_hours", "eat_hours", "is_custom"),
            b.protocols.map { listOf(it.id, it.name, it.type, it.fastHours, it.eatHours, it.isCustom) },
        ),
        "schedules.csv" to table(
            listOf("id", "protocol_id", "days", "start_minute", "enabled"),
            b.schedules.map { listOf(it.id, it.protocolId, it.days.joinToString(";"), it.startMinute, it.enabled) },
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

    fun escape(value: String): String {
        // Spreadsheets run cells that start with these as formulas; prefix them so a note can't.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r" && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun iso(millis: Long) = java.time.Instant.ofEpochMilli(millis).toString()
}
