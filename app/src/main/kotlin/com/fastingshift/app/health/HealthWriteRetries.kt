package com.fastingshift.app.health

import android.content.SharedPreferences
import com.fastingshift.domain.model.WaterLog
import com.fastingshift.domain.model.WeightLog
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local row ID and an opaque fingerprint. Measurements and timestamps stay in the encrypted database. */
internal data class RetryKey(val rowId: Long, val fingerprint: String) {
    fun encode(): String = "$rowId:$fingerprint"

    companion object {
        fun forWeight(log: WeightLog) = create(log.id, "weight", log.at, log.value, log.unit.name, log.source.name)
        fun forWater(log: WaterLog) = create(log.id, "water", log.at, log.value, log.unit.name, log.source.name)

        fun decode(encoded: String): RetryKey? {
            val id = encoded.substringBefore(':').toLongOrNull() ?: return null
            val digest = encoded.substringAfter(':', "")
            return if (id > 0 && digest.length == 43) RetryKey(id, digest) else null
        }

        private fun create(id: Long, type: String, at: Instant, value: Double, unit: String, source: String): RetryKey {
            // Room stores timestamps in milliseconds. A retry must identify the same row after restore.
            val bytes = "$type|$id|${at.toEpochMilli()}|${value.toBits()}|$unit|$source".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return RetryKey(id, Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
        }
    }
}

/** A user-initiated retry never inserts another local log. Successful and deleted rows leave the queue. */
internal class HealthWriteRetries(private val prefs: SharedPreferences) {
    private val mutex = Mutex()
    private val pending = prefs.getStringSet(PENDING, emptySet()).orEmpty().mapNotNull(RetryKey::decode).toMutableSet()
    private val count = MutableStateFlow(pending.size)
    val pendingCount = count.asStateFlow()

    suspend fun write(key: RetryKey, attempt: suspend () -> Result<Unit>): Result<Unit> = mutex.withLock {
        val result = attempt()
        if (result.isFailure) {
            if (pending.add(key)) save()
        } else if (pending.remove(key)) save()
        result
    }

    suspend fun retry(enabled: suspend () -> Boolean, attempt: suspend (RetryKey) -> Result<Unit>) = mutex.withLock {
        for (key in pending.toList()) {
            if (!enabled()) break
            if (attempt(key).isSuccess && pending.remove(key)) save()
        }
    }

    suspend fun forget(key: RetryKey) = mutex.withLock {
        if (pending.remove(key)) save()
    }

    private fun save() {
        prefs.edit().putStringSet(PENDING, pending.mapTo(mutableSetOf()) { it.encode() }).apply()
        count.value = pending.size
    }

    private companion object { const val PENDING = "pending" }
}
