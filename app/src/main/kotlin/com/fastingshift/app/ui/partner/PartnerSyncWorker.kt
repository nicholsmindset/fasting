package com.fastingshift.app.ui.partner

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PartnerWorkerEntryPoint {
    fun partnerWeekRepository(): PartnerWeekRepository
}

class PartnerSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, PartnerWorkerEntryPoint::class.java)
            .partnerWeekRepository()
        return try {
            repo.syncPending()
            Result.success()
        } catch (failure: PartnerRequestException) {
            if (failure.retryable) Result.retry() else Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        internal fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PartnerSyncWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            ).build()
            WorkManager.getInstance(context).enqueueUniqueWork("partner-week-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
