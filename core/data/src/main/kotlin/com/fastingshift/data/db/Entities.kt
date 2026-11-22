package com.fastingshift.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.model.RecordSource
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.Symptom
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "fast", indices = [Index("start_at"), Index("ended_at")])
data class FastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_at") val startAt: Instant,
    @ColumnInfo(name = "target_end_at") val targetEndAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
    @ColumnInfo(name = "protocol_id") val protocolId: String,
    val status: FastStatus,
    val note: String?,
    /** Optional reflection 1..5 recorded when the fast ended (schema v2). */
    @ColumnInfo(defaultValue = "NULL") val mood: Int? = null,
    /** REGULAR / RAMADAN / SUNNAH / QADA (schema v3). */
    @ColumnInfo(defaultValue = "REGULAR") val kind: FastKind = FastKind.REGULAR,
)

/** Custom protocols. Built-in presets live in code (Presets) and are not stored. */
@Entity(tableName = "protocol")
data class ProtocolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: ProtocolType,
    @ColumnInfo(name = "fast_hours") val fastHours: Int,
    @ColumnInfo(name = "eat_hours") val eatHours: Int?,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
)

@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "protocol_id") val protocolId: String,
    val days: Set<DayOfWeek>,
    /** Minutes after local midnight. */
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    val enabled: Boolean,
)

@Entity(tableName = "log_weight", indices = [Index("at")])
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val value: Double,
    val unit: WeightUnit,
    val source: RecordSource,
)

@Entity(tableName = "log_water", indices = [Index("at")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val value: Double,
    val unit: VolumeUnit,
    val source: RecordSource,
)

/** Electrolyte intake; value is in milligrams. */
@Entity(tableName = "log_electrolyte", indices = [Index("at")])
data class ElectrolyteLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val electrolyte: Electrolyte,
    val value: Double,
    val source: RecordSource,
)

@Entity(
    tableName = "symptom_checkin",
    foreignKeys = [ForeignKey(entity = FastEntity::class, parentColumns = ["id"], childColumns = ["fast_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("fast_id")],
)
data class SymptomCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "fast_id") val fastId: Long,
    val at: Instant,
    val answers: Set<Symptom>,
    @ColumnInfo(name = "red_flag") val redFlag: Boolean,
)

/** Every screening run is kept; the latest row is the one in force. */
@Entity(tableName = "screening", indices = [Index("answered_at")])
data class ScreeningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "answered_at") val answeredAt: Instant,
    @ColumnInfo(name = "age_years") val ageYears: Int,
    @ColumnInfo(name = "pregnant_or_breastfeeding") val pregnantOrBreastfeeding: Boolean,
    @ColumnInfo(name = "eating_disorder_history") val eatingDisorderHistory: Boolean,
    @ColumnInfo(name = "type1_diabetes") val type1Diabetes: Boolean,
    val medications: Set<Medication>,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "gate_result") val gateResult: GateResult,
    @ColumnInfo(name = "clinician_acknowledged") val clinicianAcknowledged: Boolean,
)

@Entity(tableName = "program_progress", primaryKeys = ["program_id", "day"])
data class ProgramProgressEntity(
    @ColumnInfo(name = "program_id") val programId: String,
    val day: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Instant,
)

@Entity(tableName = "streak_freeze")
data class StreakFreezeEntity(
    @PrimaryKey val day: LocalDate,
)

/** Glucose and ketone readings (schema v3). */
@Entity(tableName = "log_metric", indices = [Index("at"), Index("type")])
data class MetricLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val type: MetricType,
    val value: Double,
    val unit: MetricUnit,
    val source: RecordSource,
    val note: String?,
)

@Entity(
    tableName = "qada_repayment",
    foreignKeys = [ForeignKey(
        entity = QadaDebtEntity::class,
        parentColumns = ["hijri_year"],
        childColumns = ["hijri_year"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("hijri_year")],
)
data class QadaRepaymentEntity(
    @PrimaryKey @ColumnInfo(name = "observed_on") val observedOn: LocalDate,
    @ColumnInfo(name = "hijri_year") val hijriYear: Int,
)

/** Ramadan days owed per Hijri year (schema v3). */
@Entity(tableName = "qada_debt")
data class QadaDebtEntity(
    @PrimaryKey @ColumnInfo(name = "hijri_year") val hijriYear: Int,
    @ColumnInfo(name = "days_owed") val daysOwed: Int,
)

/** Challenges joined on this device (schema v3). */
@Entity(tableName = "challenge")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ChallengeType,
    @ColumnInfo(name = "protocol_id") val protocolId: String?,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "length_days") val lengthDays: Int,
    val name: String?,
    @ColumnInfo(name = "joined_at") val joinedAt: Instant,
    @ColumnInfo(name = "left_at") val leftAt: Instant?,
)
