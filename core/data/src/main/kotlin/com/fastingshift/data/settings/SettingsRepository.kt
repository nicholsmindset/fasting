package com.fastingshift.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastingshift.domain.backup.SettingsRecord
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.onboarding.Goal
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.ramadan.SunnahType
import com.fastingshift.domain.reminders.ReminderSettings
import com.fastingshift.domain.timer.TimerDisplayMode
import com.fastingshift.domain.history.History
import com.fastingshift.domain.schedule.DayOverride
import com.fastingshift.domain.schedule.PlanHistory
import com.fastingshift.domain.schedule.WeekChoice
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val reminders: ReminderSettings = ReminderSettings(),
    /** Removes weight from every screen (SR-6). */
    val hideWeight: Boolean = false,
    /** Optional ongoing notification with a live chronometer during a fast. */
    val ongoingNotification: Boolean = false,
    val defaultProtocolId: String? = null,
    val onboardingComplete: Boolean = false,
    val goal: Goal? = null,
    /** Eating window after duration fasts, used for the next-start reminder. */
    val eatingWindowHours: Int = 8,
    val healthConnectSync: Boolean = false,
    /** Active guided program, if the user started one. */
    val activeProgramId: String? = null,
    /** What the big timer shows; tapping it cycles. */
    val timerMode: TimerDisplayMode = TimerDisplayMode.ELAPSED,
    /** A long fast ended outside the Timer screen whose refeeding guidance hasn't been shown. */
    val pendingRefeedingFastId: Long? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** Expiry date already reminded about, so a billing reminder fires once per period. */
    val billingRemindedFor: Long? = null,
    /** Daily water goal in ml; null = automatic from weight ([WaterGoal.defaultMl]). */
    val waterGoalMl: Int? = null,
    /** First-week coaching tips (in-app and notifications). */
    val coachingEnabled: Boolean = true,
    /** Sunday evening weekly recap notification. */
    val weeklyRecapEnabled: Boolean = true,
    /** When onboarding finished; coaching runs in the first two weeks after it. */
    val onboardedAt: Long? = null,
    /** Ramadan mode (PRD Phase 1): daily fast from Fajr to Maghrib for the chosen city. */
    val ramadanEnabled: Boolean = false,
    val ramadanCityId: String? = null,
    val ramadanMethod: PrayerMethod? = null,
    /** Days added to the calculated (Umm al-Qura) Hijri date for local moon sighting, −2..+2. */
    val hijriOffsetDays: Int = 0,
    /** Evening-before reminders for the Sunnah fasts in [sunnahTypes]. */
    val sunnahReminders: Boolean = false,
    val sunnahTypes: Set<SunnahType> = emptySet(),
    val planHistory: PlanHistory = PlanHistory(),
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences. Health data never lives here; it is in the encrypted database. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore
    // Preserve the one-off reminder stored by earlier versions.
    private val firstWeek = context.applicationContext.getSharedPreferences("first_week", Context.MODE_PRIVATE)

    fun firstEvening(): Pair<Long, LocalDate>? {
        val id = firstWeek.getLong("planned_schedule_id", -1L).takeIf { it > 0L } ?: return null
        val day = firstWeek.getString("planned_date", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return id to day
    }

    fun rememberFirstEvening(id: Long, day: LocalDate) {
        firstWeek.edit().putLong("planned_schedule_id", id).putString("planned_date", day.toString()).apply()
    }

    fun clearFirstEvening() {
        firstWeek.edit().remove("planned_schedule_id").remove("planned_date").apply()
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            reminders = ReminderSettings(
                fastEndEnabled = p[FAST_END] ?: true,
                milestones = (p[MILESTONES] ?: emptySet()).mapNotNull { it.toLongOrNull() }.sorted().map(Duration::ofHours),
                forgotToEndAfter = (p[FORGOT_HOURS] ?: DEFAULT_FORGOT_HOURS).takeIf { it > 0 }?.let { Duration.ofHours(it.toLong()) },
                fastStartEnabled = p[FAST_START] ?: true,
                nearGoalEnabled = p[NEAR_GOAL] ?: false,
                windowClosingEnabled = p[WINDOW_CLOSING] ?: false,
                hydrationEnabled = p[HYDRATION] ?: true,
            ),
            hideWeight = p[HIDE_WEIGHT] ?: false,
            ongoingNotification = p[ONGOING] ?: false,
            defaultProtocolId = p[DEFAULT_PROTOCOL],
            onboardingComplete = p[ONBOARDED] ?: false,
            goal = p[GOAL]?.let { runCatching { Goal.valueOf(it) }.getOrNull() },
            eatingWindowHours = p[EATING_WINDOW] ?: 8,
            healthConnectSync = p[HEALTH_CONNECT] ?: false,
            activeProgramId = p[ACTIVE_PROGRAM],
            timerMode = p[TIMER_MODE]?.let { runCatching { TimerDisplayMode.valueOf(it) }.getOrNull() } ?: TimerDisplayMode.ELAPSED,
            pendingRefeedingFastId = p[PENDING_REFEED],
            weightUnit = p[WEIGHT_UNIT]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
            billingRemindedFor = p[BILLING_REMINDED],
            waterGoalMl = p[WATER_GOAL],
            coachingEnabled = p[COACHING] ?: true,
            weeklyRecapEnabled = p[WEEKLY_RECAP] ?: true,
            onboardedAt = p[ONBOARDED_AT],
            planHistory = p.planHistory(),
            ramadanEnabled = p[RAMADAN] ?: false,
            ramadanCityId = p[RAMADAN_CITY],
            ramadanMethod = p[RAMADAN_METHOD]?.let { runCatching { PrayerMethod.valueOf(it) }.getOrNull() },
            hijriOffsetDays = (p[HIJRI_OFFSET] ?: 0).coerceIn(-2, 2),
            sunnahReminders = p[SUNNAH_REMINDERS] ?: false,
            sunnahTypes = (p[SUNNAH_TYPES] ?: emptySet()).mapNotNull { runCatching { SunnahType.valueOf(it) }.getOrNull() }.toSet(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Removes every preference ("Delete all data"), through the live store so nothing writes it back. */
    suspend fun clearAll() = store.edit { it.clear() }

    suspend fun setFastEndReminder(enabled: Boolean) = store.edit { it[FAST_END] = enabled }
    suspend fun setFastStartReminder(enabled: Boolean) = store.edit { it[FAST_START] = enabled }
    suspend fun setMilestoneHours(hours: Set<Int>) = store.edit { it[MILESTONES] = hours.map(Int::toString).toSet() }

    /** 0 disables the "forgot to end your fast?" nudge. */
    suspend fun setForgotToEndHours(hours: Int) = store.edit { it[FORGOT_HOURS] = hours.coerceAtLeast(0) }
    suspend fun setHideWeight(hide: Boolean) = store.edit { it[HIDE_WEIGHT] = hide }
    suspend fun setOngoingNotification(enabled: Boolean) = store.edit { it[ONGOING] = enabled }
    suspend fun setDefaultProtocol(id: String) = store.edit { it[DEFAULT_PROTOCOL] = id }

    suspend fun setOnboardingComplete(done: Boolean) = store.edit { it[ONBOARDED] = done }
    suspend fun setGoal(goal: Goal) = store.edit { it[GOAL] = goal.name }
    suspend fun setEatingWindowHours(hours: Int) = store.edit { it[EATING_WINDOW] = hours.coerceIn(1, 23) }
    suspend fun setHealthConnectSync(enabled: Boolean) = store.edit { it[HEALTH_CONNECT] = enabled }
    suspend fun setActiveProgram(id: String?) = store.edit {
        if (id == null) {
            it.remove(ACTIVE_PROGRAM)
        } else {
            it[ACTIVE_PROGRAM] = id
        }
    }

    suspend fun setTimerMode(mode: TimerDisplayMode) = store.edit { it[TIMER_MODE] = mode.name }
    suspend fun setWeightUnit(unit: WeightUnit) = store.edit { it[WEIGHT_UNIT] = unit.name }
    suspend fun setPendingRefeeding(fastId: Long?) = store.edit {
        if (fastId == null) {
            it.remove(PENDING_REFEED)
        } else {
            it[PENDING_REFEED] = fastId
        }
    }
    suspend fun setNearGoalReminder(enabled: Boolean) = store.edit { it[NEAR_GOAL] = enabled }
    suspend fun setWindowClosingReminder(enabled: Boolean) = store.edit { it[WINDOW_CLOSING] = enabled }
    suspend fun setHydrationNudges(enabled: Boolean) = store.edit { it[HYDRATION] = enabled }

    /** null resets to the automatic goal. */
    suspend fun setWaterGoalMl(ml: Int?) = store.edit {
        if (ml == null) it.remove(WATER_GOAL) else it[WATER_GOAL] = ml.coerceIn(500, 6_000)
    }
    suspend fun setCoaching(enabled: Boolean) = store.edit { it[COACHING] = enabled }
    suspend fun setWeeklyRecap(enabled: Boolean) = store.edit { it[WEEKLY_RECAP] = enabled }
    suspend fun setOnboardedAt(millis: Long) = store.edit { p ->
        p[ONBOARDED_AT] = millis
        // New installs have the default daily plan until they first edit their schedules.
        if (p[PLAN_REVISIONS].isNullOrEmpty()) {
            val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            p[PLAN_REVISIONS] = setOf("${day}=${PlanHistory.EVERY_DAY}")
        }
    }

    suspend fun recordSchedule(day: LocalDate, mask: Int) = store.edit { p ->
        if (p[ONBOARDED] == true) {
            val current = p.planHistory()
            val updated = current.record(day, mask)
            if (updated !== current) p[PLAN_REVISIONS] = updated.revisions.entries.map { "${it.key}=${it.value}" }.toSet()
        }
    }

    /** The recap acts on the upcoming Monday. Stale screens cannot rewrite past decisions. */
    suspend fun setNextWeekChoice(week: LocalDate, choice: WeekChoice, today: LocalDate) = store.edit { p ->
        if (week == History.weekStart(today).plusWeeks(1)) {
            val choices = p.planHistory().choices + (week to choice)
            p[WEEK_CHOICES] = choices.entries.map { "${it.key}=${it.value.name}" }.toSet()
        }
    }

    /** A null override restores the recurring plan. Past civil dates are read-only. */
    suspend fun setDayOverride(day: LocalDate, override: DayOverride?, today: LocalDate) = store.edit { p ->
        if (day >= today) {
            val overrides = p.planHistory().dayOverrides.toMutableMap()
            if (override == null) overrides.remove(day) else overrides[day] = override
            p[DAY_OVERRIDES] = overrides.entries.map { "${it.key}=${it.value.toRecord()}" }.toSet()
        }
    }

    suspend fun clearPlanHistory() = store.edit {
        it.remove(PLAN_REVISIONS)
        it.remove(WEEK_CHOICES)
        it.remove(DAY_OVERRIDES)
        Unit
    }

    private fun Preferences.planHistory(): PlanHistory {
        val revisions = this[PLAN_REVISIONS].orEmpty().mapNotNull { entry ->
            entry.substringAfter('=').toIntOrNull()?.let { entry.substringBefore('=') to it }
        }.toMap()
        val choices = this[WEEK_CHOICES].orEmpty().associate { it.substringBefore('=') to it.substringAfter('=') }
        val dayOverrides = this[DAY_OVERRIDES].orEmpty().associate { it.substringBefore('=') to it.substringAfter('=') }
        return PlanHistory.fromRecords(revisions, choices, dayOverrides)
    }
    suspend fun setRamadan(enabled: Boolean) = store.edit { it[RAMADAN] = enabled }
    suspend fun setRamadanCity(cityId: String, method: PrayerMethod) = store.edit {
        it[RAMADAN_CITY] = cityId
        it[RAMADAN_METHOD] = method.name
    }

    suspend fun setHijriOffsetDays(days: Int) = store.edit { it[HIJRI_OFFSET] = days.coerceIn(-2, 2) }
    suspend fun setSunnahReminders(enabled: Boolean) = store.edit { it[SUNNAH_REMINDERS] = enabled }
    suspend fun setSunnahTypes(types: Set<SunnahType>) = store.edit { it[SUNNAH_TYPES] = types.map { t -> t.name }.toSet() }

    suspend fun setBillingRemindedFor(expiresAtMillis: Long) = store.edit { it[BILLING_REMINDED] = expiresAtMillis }

    /** Preferences for a backup (F8). Device-specific items (Health Connect, billing) are left out. */
    suspend fun toRecord(): SettingsRecord = current().let { s ->
        SettingsRecord(
            onboardingComplete = s.onboardingComplete,
            planRevisions = s.planHistory.revisions.mapKeys { it.key.toString() },
            dayOverrides = s.planHistory.dayOverrides.entries.associate { it.key.toString() to it.value.toRecord() },
            weekChoices = s.planHistory.choices.entries.associate { it.key.toString() to it.value.name },
            goal = s.goal?.name,
            defaultProtocolId = s.defaultProtocolId,
            hideWeight = s.hideWeight,
            ongoingNotification = s.ongoingNotification,
            fastEndAlert = s.reminders.fastEndEnabled,
            fastStartAlert = s.reminders.fastStartEnabled,
            milestoneHours = s.reminders.milestones.map { it.toHours().toInt() },
            forgotToEndHours = s.reminders.forgotToEndAfter?.toHours()?.toInt() ?: 0,
            eatingWindowHours = s.eatingWindowHours,
            activeProgramId = s.activeProgramId,
            weightUnit = s.weightUnit.name,
            timerMode = s.timerMode.name,
            waterGoalMl = s.waterGoalMl,
            nearGoalAlert = s.reminders.nearGoalEnabled,
            windowClosingAlert = s.reminders.windowClosingEnabled,
            hydrationNudges = s.reminders.hydrationEnabled,
            coaching = s.coachingEnabled,
            weeklyRecap = s.weeklyRecapEnabled,
            ramadanEnabled = s.ramadanEnabled,
            ramadanCityId = s.ramadanCityId,
            ramadanMethod = s.ramadanMethod?.name,
            hijriOffsetDays = s.hijriOffsetDays,
            sunnahReminders = s.sunnahReminders,
            sunnahTypes = s.sunnahTypes.map { it.name }.sorted(),
        )
    }

    suspend fun restore(r: SettingsRecord) = store.edit {
        it[ONBOARDED] = r.onboardingComplete
        val history = PlanHistory.fromRecords(r.planRevisions, r.weekChoices, r.dayOverrides)
        it[PLAN_REVISIONS] = history.revisions.entries.map { entry -> "${entry.key}=${entry.value}" }.toSet()
        it[DAY_OVERRIDES] = history.dayOverrides.entries.map { entry -> "${entry.key}=${entry.value.toRecord()}" }.toSet()
        it[WEEK_CHOICES] = history.choices.entries.map { entry -> "${entry.key}=${entry.value.name}" }.toSet()
        r.goal?.takeIf { g -> Goal.entries.any { e -> e.name == g } }?.let { g -> it[GOAL] = g } ?: it.remove(GOAL)
        r.defaultProtocolId?.let { d -> it[DEFAULT_PROTOCOL] = d } ?: it.remove(DEFAULT_PROTOCOL)
        // Restoring an older backup must never reveal weight someone has chosen to hide (SR-6).
        it[HIDE_WEIGHT] = (it[HIDE_WEIGHT] ?: false) || r.hideWeight
        it[ONGOING] = r.ongoingNotification
        it[FAST_END] = r.fastEndAlert
        it[FAST_START] = r.fastStartAlert
        it[MILESTONES] = r.milestoneHours.filter { h -> h in 1..120 }.map(Int::toString).toSet()
        it[FORGOT_HOURS] = r.forgotToEndHours.coerceIn(0, 24)
        it[EATING_WINDOW] = r.eatingWindowHours.coerceIn(1, 23)
        r.activeProgramId?.let { a -> it[ACTIVE_PROGRAM] = a } ?: it.remove(ACTIVE_PROGRAM)
        if (WeightUnit.entries.any { e -> e.name == r.weightUnit }) it[WEIGHT_UNIT] = r.weightUnit
        r.waterGoalMl?.let { w -> it[WATER_GOAL] = w.coerceIn(500, 6_000) }
        it[NEAR_GOAL] = r.nearGoalAlert
        it[WINDOW_CLOSING] = r.windowClosingAlert
        it[HYDRATION] = r.hydrationNudges
        it[COACHING] = r.coaching
        it[WEEKLY_RECAP] = r.weeklyRecap
        it[RAMADAN] = r.ramadanEnabled
        r.ramadanCityId?.let { c -> it[RAMADAN_CITY] = c }
        r.ramadanMethod?.takeIf { m -> PrayerMethod.entries.any { e -> e.name == m } }?.let { m -> it[RAMADAN_METHOD] = m }
        it[HIJRI_OFFSET] = r.hijriOffsetDays.coerceIn(-2, 2)
        it[SUNNAH_REMINDERS] = r.sunnahReminders
        it[SUNNAH_TYPES] = r.sunnahTypes.filter { t -> SunnahType.entries.any { e -> e.name == t } }.toSet()
        r.timerMode?.takeIf { m -> TimerDisplayMode.entries.any { e -> e.name == m } }?.let { m -> it[TIMER_MODE] = m }
    }

    private companion object {
        const val DEFAULT_FORGOT_HOURS = 4
        val TIMER_MODE = stringPreferencesKey("timer_mode")
        val PENDING_REFEED = longPreferencesKey("pending_refeeding_fast")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val BILLING_REMINDED = longPreferencesKey("billing_reminded_for")
        val WATER_GOAL = intPreferencesKey("water_goal_ml")
        val NEAR_GOAL = booleanPreferencesKey("reminder_near_goal")
        val WINDOW_CLOSING = booleanPreferencesKey("reminder_window_closing")
        val HYDRATION = booleanPreferencesKey("reminder_hydration")
        val COACHING = booleanPreferencesKey("coaching")
        val PLAN_REVISIONS = stringSetPreferencesKey("plan_revisions")
        val WEEK_CHOICES = stringSetPreferencesKey("week_choices")
        val DAY_OVERRIDES = stringSetPreferencesKey("day_overrides")
        val WEEKLY_RECAP = booleanPreferencesKey("weekly_recap")
        val ONBOARDED_AT = longPreferencesKey("onboarded_at")
        val RAMADAN = booleanPreferencesKey("ramadan_enabled")
        val RAMADAN_CITY = stringPreferencesKey("ramadan_city")
        val RAMADAN_METHOD = stringPreferencesKey("ramadan_method")
        val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
        val SUNNAH_REMINDERS = booleanPreferencesKey("sunnah_reminders")
        val SUNNAH_TYPES = stringSetPreferencesKey("sunnah_types")
        val FAST_END = booleanPreferencesKey("reminder_fast_end")
        val FAST_START = booleanPreferencesKey("reminder_fast_start")
        val MILESTONES = stringSetPreferencesKey("reminder_milestone_hours")
        val FORGOT_HOURS = intPreferencesKey("reminder_forgot_hours")
        val HIDE_WEIGHT = booleanPreferencesKey("hide_weight")
        val ONGOING = booleanPreferencesKey("ongoing_notification")
        val DEFAULT_PROTOCOL = stringPreferencesKey("default_protocol")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val GOAL = stringPreferencesKey("goal")
        val EATING_WINDOW = intPreferencesKey("eating_window_hours")
        val HEALTH_CONNECT = booleanPreferencesKey("health_connect_sync")
        val ACTIVE_PROGRAM = stringPreferencesKey("active_program")
    }
}
