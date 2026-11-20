package com.fastingshift.app.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.domain.insights.Insights
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

/**
 * Optional Health Connect mirror (PRD F6). The local database stays the source of truth.
 * Writes WeightRecord and HydrationRecord; reads WeightRecord. Nothing else.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logs: LogRepository,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
    )

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private fun client(): HealthConnectClient? = if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    suspend fun hasAllPermissions(): Boolean =
        client()?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true

    suspend fun writeWeight(log: WeightLog) = runCatching {
        val c = client() ?: return@runCatching
        c.insertRecords(
            listOf(
                WeightRecord(
                    time = log.at,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(log.at),
                    weight = Mass.kilograms(Insights.toKg(log.value, log.unit)),
                    metadata = Metadata.manualEntry(clientRecordId = "weight-${log.id}"),
                ),
            ),
        )
    }

    suspend fun writeWater(log: WaterLog) = runCatching {
        val c = client() ?: return@runCatching
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
                    metadata = Metadata.manualEntry(clientRecordId = "water-${log.id}"),
                ),
            ),
        )
    }

    /**
     * Imports weights recorded by other apps since [since], skipping ones this app wrote.
     * Returns how many were imported.
     */
    suspend fun importWeights(since: Instant): Int {
        val c = client() ?: return 0
        val existing = logs.getWeights().map { it.at }.toSet()
        val records = c.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(since))).records
        var imported = 0
        records.filter { it.metadata.dataOrigin.packageName != context.packageName && it.time !in existing }.forEach {
            logs.addWeight(WeightLog(at = it.time, value = it.weight.inKilograms, unit = WeightUnit.KG, source = RecordSource.HEALTH_CONNECT))
            imported++
        }
        return imported
    }
}
