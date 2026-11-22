package com.fastingshift.data.repo

import androidx.room.withTransaction
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.domain.backup.HistoryBatch
import com.fastingshift.domain.backup.HistoryCsv
import com.fastingshift.domain.backup.against

class HistoryTransferRepository(private val db: FastingDatabase) {
    private suspend fun all() = db.fastDao().getAll().map { it.toDomain() }

    suspend fun exportCsv(): String = HistoryCsv.export(all())

    suspend fun preview(batch: HistoryBatch): HistoryBatch = batch.against(all())

    /** Recheck only approved rows inside the same transaction as their inserts. */
    suspend fun add(preview: HistoryBatch): HistoryBatch = db.withTransaction {
        preview.against(all()).also { result ->
            result.fasts.forEach { db.fastDao().insert(it.copy(id = 0).toEntity()) }
        }
    }
}
