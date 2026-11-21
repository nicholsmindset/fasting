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
import com.fastingshift.domain.reminders.ReminderSettings
import com.fastingshift.domain.timer.TimerDisplayMode
import java.time.Duration
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
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences. Health data never lives here; it is in the encrypted database. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

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
            ramadanEnabled = p[RAMADAN] ?: false,
            ramadanCityId = p[RAMADAN_CITY],
            ramadanMethod = p[RAMADAN_METHOD]?.let { runCatching { PrayerMethod.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun current(): AppSettings = settings.first()

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
    suspend fun setOnboardedAt(millis: Long) = store.edit { it[ONBOARDED_AT] = millis }
    suspend fun setRamadan(enabled: Boolean) = store.edit { it[RAMADAN] = enabled }
    suspend fun setRamadanCity(cityId: String, method: PrayerMethod) = store.edit {
        it[RAMADAN_CITY] = cityId
        it[RAMADAN_METHOD] = method.name
    }

    suspend fun setBillingRemindedFor(expiresAtMillis: Long) = store.edit { it[BILLING_REMINDED] = expiresAtMillis }

    /** Preferences for a backup (F8). Device-specific items (Health Connect, billing) are left out. */
    suspend fun toRecord(): SettingsRecord = current().let { s ->
        SettingsRecord(
            onboardingComplete = s.onboardingComplete,
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
        )
    }

    suspend fun restore(r: SettingsRecord) = store.edit {
        it[ONBOARDED] = r.onboardingComplete
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
        val WEEKLY_RECAP = booleanPreferencesKey("weekly_recap")
        val ONBOARDED_AT = longPreferencesKey("onboarded_at")
        val RAMADAN = booleanPreferencesKey("ramadan_enabled")
        val RAMADAN_CITY = stringPreferencesKey("ramadan_city")
        val RAMADAN_METHOD = stringPreferencesKey("ramadan_method")
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
