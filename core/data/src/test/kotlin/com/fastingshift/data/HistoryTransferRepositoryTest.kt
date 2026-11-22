package com.fastingshift.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.HistoryTransferRepository
import com.fastingshift.data.repo.RoomFastRepository
import com.fastingshift.domain.backup.HistoryBatch
import com.fastingshift.domain.backup.HistorySkip
import com.fastingshift.domain.model.Fast
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HistoryTransferRepositoryTest {
    private val start = Instant.parse("2025-02-01T00:00:00Z")
    private fun fast(hour: Long) = Fast(
        startAt = start.plusSeconds(hour * 3600),
        targetEndAt = start.plusSeconds((hour + 8) * 3600),
        protocolId = "test",
    )

    @Test fun changesDuringPreviewAreRecheckedAndActiveFastSurvivesRetries() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FastingDatabase::class.java)
            .addCallback(FastingDatabase.Constraints).allowMainThreadQueries().build()
        try {
            val repository = HistoryTransferRepository(db)
            val local = RoomFastRepository(db.fastDao())
            val first = fast(0).let { it.end(it.targetEndAt) }
            val second = fast(24).let { it.end(it.targetEndAt) }
            val preview = repository.preview(HistoryBatch(listOf(first, second)))
            assertEquals(0, db.fastDao().getAll().size)
            val active = local.insert(fast(20))
            val result = repository.add(preview)
            assertEquals(listOf(first), result.fasts)
            assertEquals(mapOf(HistorySkip.OVERLAP to 1), result.skipped)
            assertEquals(active.id, db.fastDao().getActive()?.id)
            assertEquals(2, db.fastDao().getAll().size)

            val retry = repository.add(preview)
            assertEquals(0, retry.fasts.size)
            assertEquals(mapOf(HistorySkip.DUPLICATE to 1, HistorySkip.OVERLAP to 1), retry.skipped)
            assertEquals(2, db.fastDao().getAll().size)
        } finally {
            db.close()
        }
    }
}
