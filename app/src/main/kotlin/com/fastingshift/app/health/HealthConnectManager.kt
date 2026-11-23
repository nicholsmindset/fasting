package com.fastingshift.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.insights.Insights
import com.fastingshift.domain.insights.RestingHeartRateSample
import com.fastingshift.domain.insights.SleepSession
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.RecordSource
import com.fastingshift.domain.model.VolumeUnit
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.math.roundToLong

/** A weight read from Health Connect, reduced to what de-duplication needs. */
data class HealthWeight(val at: Instant, val kg: Double, val packageName: String)

/** Pure import rules, kept apart from the client so they can be unit-tested. */
object WeightImport {
    /**
     * Weights to import: not written by this app, and not already logged with the same time
     * and value (to 10 g), including duplicates within the batch itself.
     */
    fun select(records: List<HealthWeight>, existing: List<WeightLog>, ownPackage: String): List<HealthWeight> {
        val seen = existing.mapTo(HashSet()) { key(it.at, Insights.toKg(it.value, it.unit)) }
        return records.filter { it.packageName != ownPackage && seen.add(key(it.at, it.kg)) }
    }

    private fun key(at: Instant, kg: Double) = at.toEpochMilli() to (kg * 100).roundToLong()
}

/**
 * Optional Health Connect mirror (PRD F6). The local database stays the source of truth.
 * Writes WeightRecord, HydrationRecord and BloodGlucoseRecord; reads WeightRecord, and (for
 * Shift+ body signals, read-only) SleepSessionRecord and RestingHeartRateRecord. Ketones are
 * local only: Health Connect has no ketone type. Permissions are requested incrementally: the
 * core [permissions] on sync opt-in, [glucosePermissions] and [bodySignalPermissions] where
 * those features are used. Every call is
 * guarded: Health Connect can throw SecurityException (permission revoked) or
 * RemoteException/IllegalStateException (provider restarting or updating) at any time.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logs: LogRepository,
    private val settings: SettingsRepository,
    private val metrics: com.fastingshift.data.repo.MetricRepository,
) {
    private val retries by lazy {
        HealthWriteRetries(context.getSharedPreferences("health_connect_retries", Context.MODE_PRIVATE))
    }
    val pendingWriteCount get() = retries.pendingCount

    /**
     * Health Connect treats a repeated clientRecordId as an update. Room ids restart at 1 after
     * "Delete all data" or a key reset, so ids carry a random per-install tag: new readings never
     * overwrite records written before. The tag lives in app prefs, which both resets clear.
     */
    private val installTag by lazy {
        val prefs = context.getSharedPreferences(ID_PREFS, Context.MODE_PRIVATE)
        prefs.getString("tag", null) ?: java.util.UUID.randomUUID().toString().take(8).also { tag -> prefs.edit { putString("tag", tag) } }
    }

    private fun clientId(type: String, id: Long) = "$type-$installTag-$id"

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
    )

    /** Asked for from the glucose log, on top of [permissions]. */
    val glucosePermissions: Set<String> = setOf(HealthPermission.getWritePermission(BloodGlucoseRecord::class))

    /** Read-only, asked for from Shift+ insights ("Body signals"). */
    val bodySignalPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    private fun sdkStatus(): Int = runCatching { HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE) }
        .getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect is installed but must be updated before the app can use it. */
    fun needsProviderUpdate(): Boolean = sdkStatus() == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    /** Opens Health Connect's Play Store page so the user can install or update it. */
    fun providerUpdateIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URI))
        .setPackage("com.android.vending")
        .putExtra("overlay", true)
        .putExtra("callerId", context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private fun client(): HealthConnectClient? =
        if (isAvailable()) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun hasAllPermissions(): Boolean = guarded(false) {
        client()?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
    }

    /** Whether every permission in [wanted] is granted; false if Health Connect is unavailable. */
    suspend fun hasPermissions(wanted: Set<String>): Boolean = guarded(false) {
        client()?.permissionController?.getGrantedPermissions()?.containsAll(wanted) == true
    }

    suspend fun writeWeight(log: WeightLog): Result<Unit> = retries.write(RetryKey.forWeight(log)) {
        if (settings.current().healthConnectSync) insertWeight(log) else Result.success(Unit)
    }

    private suspend fun insertWeight(log: WeightLog): Result<Unit> = guardedResult {
        val c = checkNotNull(client()) { "Health Connect is unavailable" }
        c.insertRecords(
            listOf(
                WeightRecord(
                    time = log.at,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(log.at),
                    weight = Mass.kilograms(Insights.toKg(log.value, log.unit)),
                    metadata = Metadata.manualEntry(clientRecordId = clientId("weight", log.id)),
                ),
            ),
        )
    }

    suspend fun writeWater(log: WaterLog): Result<Unit> = retries.write(RetryKey.forWater(log)) {
        if (settings.current().healthConnectSync) insertWater(log) else Result.success(Unit)
    }

    private suspend fun insertWater(log: WaterLog): Result<Unit> = guardedResult {
        val c = checkNotNull(client()) { "Health Connect is unavailable" }
        val ml = if (log.unit == VolumeUnit.ML) log.value else log.value * 29.5735
        val offset = ZoneId.systemDefault().rules.getOffset(log.at)
        c.insertRecords(
            listOf(
                HydrationRecord(
                    startTime = log.at,
                    startZoneOffset = offset,
                    endTime = log.at.plus(Duration.ofMinutes(1)),
                    endZoneOffset = offset,
                    volume = Volume.milliliters(ml),
                    metadata = Metadata.manualEntry(clientRecordId = clientId("water", log.id)),
                ),
            ),
        )
    }

    suspend fun retryPendingWrites() = retries.retry(enabled = { settings.current().healthConnectSync }) { key ->
        val weight = logs.getWeight(key.rowId)
        if (weight != null && RetryKey.forWeight(weight) == key) return@retry insertWeight(weight)
        val water = logs.getWater(key.rowId)
        if (water != null && RetryKey.forWater(water) == key) return@retry insertWater(water)
        val glucose = metrics.get(key.rowId)?.takeIf { it.type == MetricType.GLUCOSE }
        if (glucose != null && RetryKey.forGlucose(glucose) == key) {
            return@retry insertGlucose(glucose)
        }
        // Deleted, or a different row now occupies the ID after restoring a backup.
        Result.success(Unit)
    }

    suspend fun forgetWeight(log: WeightLog) = retries.forget(RetryKey.forWeight(log))
    suspend fun forgetWater(log: WaterLog) = retries.forget(RetryKey.forWater(log))
    suspend fun forgetGlucose(log: MetricLog) = retries.forget(RetryKey.forGlucose(log))

    /**
     * Writes a glucose reading (never a ketone reading: Health Connect has no ketone type) when
     * sync is on and glucose access was granted. A failed write is queued for retry like weight.
     */
    suspend fun writeGlucose(log: MetricLog): Result<Unit> {
        require(log.type == MetricType.GLUCOSE) { "only glucose readings go to Health Connect" }
        return retries.write(RetryKey.forGlucose(log)) {
            if (settings.current().healthConnectSync) insertGlucose(log) else Result.success(Unit)
        }
    }

    private suspend fun insertGlucose(log: MetricLog): Result<Unit> {
        val c = client() ?: return Result.failure(IllegalStateException("Health Connect is unavailable"))
        return writeGlucoseWhenGranted({ c.permissionController.getGrantedPermissions().containsAll(glucosePermissions) }) {
            c.insertRecords(
                listOf(
                    BloodGlucoseRecord(
                        time = log.at,
                        zoneOffset = ZoneId.systemDefault().rules.getOffset(log.at),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("glucose", log.id)),
                        level = BloodGlucose.millimolesPerLiter(log.mmolPerL),
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN,
                        relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL,
                    ),
                ),
            )
        }
    }

    /** Removes a glucose reading this app wrote, when the user deletes it here. */
    suspend fun deleteGlucose(id: Long): Result<Unit> = guardedResult {
        val c = client() ?: return@guardedResult
        c.deleteRecords(BloodGlucoseRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientId("glucose", id)))
    }

    /** Sleep sessions since [since], from any app; empty if unavailable or not granted. */
    suspend fun readSleep(since: Instant): List<SleepSession> = guarded(emptyList()) {
        val c = client() ?: return@guarded emptyList()
        readAll(c, SleepSessionRecord::class, since).filter { it.endTime.isAfter(it.startTime) }.map { SleepSession(it.startTime, it.endTime) }
    }

    /** Resting heart rate samples since [since]; empty if unavailable or not granted. */
    suspend fun readRestingHeartRate(since: Instant): List<RestingHeartRateSample> = guarded(emptyList()) {
        val c = client() ?: return@guarded emptyList()
        readAll(c, RestingHeartRateRecord::class, since).map { RestingHeartRateSample(it.time, it.beatsPerMinute.toDouble()) }
    }

    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, since: Instant): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = c.readRecords(ReadRecordsRequest(type, TimeRangeFilter.after(since), pageSize = PAGE_SIZE, pageToken = pageToken))
            records += response.records
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null && ++pages < MAX_PAGES)
        return records
    }

    /**
     * Imports weights recorded by other apps since [since], skipping ones this app wrote and
     * ones already logged (same time and value). Reads every page. Returns how many were
     * imported; 0 if Health Connect is unavailable or refuses.
     */
    suspend fun importWeights(since: Instant): Int = guarded(0) {
        val c = client() ?: return@guarded 0
        val records = mutableListOf<HealthWeight>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = c.readRecords(
                ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(since), pageSize = PAGE_SIZE, pageToken = pageToken),
            )
            response.records.mapTo(records) { HealthWeight(it.time, it.weight.inKilograms, it.metadata.dataOrigin.packageName) }
            pageToken = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (pageToken != null && ++pages < MAX_PAGES)
        val toImport = WeightImport.select(records, logs.getWeights(), context.packageName)
        toImport.forEach {
            logs.addWeight(WeightLog(at = it.at, value = it.kg, unit = WeightUnit.KG, source = RecordSource.HEALTH_CONNECT))
        }
        toImport.size
    }

    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // SecurityException, RemoteException, IllegalStateException, IOException...
        fallback
    }

    companion object {
        /** Preferences file holding the per-install record-id tag. */
        const val ID_PREFS = "health_connect_ids"
        private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val PLAY_STORE_URI = "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        private const val PAGE_SIZE = 1000
        private const val MAX_PAGES = 50
    }
}

/** Only a successful permission lookup can omit a glucose write. Provider errors stay retryable. */
internal suspend fun writeGlucoseWhenGranted(hasPermission: suspend () -> Boolean, insert: suspend () -> Unit): Result<Unit> =
    guardedResult { if (hasPermission()) insert() }

private suspend fun guardedResult(block: suspend () -> Unit): Result<Unit> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
