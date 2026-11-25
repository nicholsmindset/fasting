package com.fastingshift.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.fastingshift.domain.revival.RevivalWindow
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** Each row is both an independently configurable event and its local participation record. */
@Entity(tableName = "revival_event")
data class RevivalEventEntity(
    @PrimaryKey val id: String, val starts: Long, val ends: Long, val zone: String,
    val target: String, val threshold: Int, val joined: Long? = null, val baselineBest: Int? = null,
    val restored: Long? = null, val awardDay: Long? = null, val restoredDays: Int? = null,
    val dismissed: Boolean = false, val notified: Boolean = false,
) {
    fun window() = RevivalWindow(id, Instant.ofEpochMilli(starts), Instant.ofEpochMilli(ends), ZoneId.of(zone), target, threshold)
}

@Dao
interface RevivalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seed(events: List<RevivalEventEntity>)
    @Query("SELECT * FROM revival_event ORDER BY starts DESC") fun observe(): Flow<List<RevivalEventEntity>>
    @Query("SELECT * FROM revival_event ORDER BY starts DESC") suspend fun all(): List<RevivalEventEntity>
    @Query("UPDATE revival_event SET joined=:at, baselineBest=:best WHERE id=:id AND joined IS NULL AND dismissed=0 AND starts<=:at AND ends>:at")
    suspend fun join(id: String, at: Long, best: Int): Int
    @Query("UPDATE revival_event SET dismissed=1 WHERE id=:id AND restored IS NULL") suspend fun dismiss(id: String)
    @Query("UPDATE revival_event SET restored=:at, awardDay=:day, restoredDays=:days WHERE id=:id AND joined IS NOT NULL AND restored IS NULL AND dismissed=0")
    suspend fun finish(id: String, at: Long, day: Long, days: Int): Int
    @Query("UPDATE revival_event SET notified=1 WHERE id=:id AND notified=0 AND joined IS NULL AND dismissed=0")
    suspend fun claimNotification(id: String): Int
    @Query("DELETE FROM revival_event") suspend fun clear()
}
