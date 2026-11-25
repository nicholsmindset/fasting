package com.fastingshift.app.ui.partner

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class PartnerSnapshot(
    val week: PartnerWeek? = null,
    val pendingDays: Set<String> = emptySet(),
    val leaving: Boolean = false,
)

/** Only opt-in social data is stored here. Fast history, screening and timers are never read. */
@Singleton
class PartnerWeekRepository internal constructor(
    private val prefs: SharedPreferences,
    private val transport: PartnerTransport,
    private val clock: Clock,
    private val localZone: () -> ZoneId,
    private val scheduleSync: () -> Unit,
) {
    @Inject constructor(@ApplicationContext context: Context, clock: Clock) : this(
        context.getSharedPreferences("partner_week", Context.MODE_PRIVATE),
        HttpPartnerTransport(), clock, ZoneId::systemDefault, { PartnerSyncWorker.enqueue(context) },
    )

    private val lock = Mutex()
    private val current = MutableStateFlow(PartnerSnapshot())
    internal val snapshot = current.asStateFlow()

    private val key get() = prefs.getString("device", null)
    private val token get() = prefs.getString("invite", null)
    private val days get() = prefs.getStringSet("days", emptySet()).orEmpty().toSet()
    private val rawWeek get() = prefs.getString("week", null)?.let(::JSONObject)
    private val leavingId get() = prefs.getString("leaving", null)
    private val today get() = LocalDate.now(clock.withZone(ZoneId.of(prefs.getString("zone", null) ?: localZone().id)))

    private fun publish() {
        current.value = PartnerSnapshot(rawWeek?.let { projectPartnerWeek(it, today, token, days) }, days, leavingId != null)
    }

    private fun save(change: SharedPreferences.Editor.() -> Unit) {
        if (!prefs.edit().apply(change).commit()) throw IOException("Could not save partner week on this device")
        publish()
    }

    private suspend fun <T> serialized(action: () -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { action() } }

    internal suspend fun cachedWeek(): PartnerWeek? = serialized {
        publish()
        current.value.week
    }

    internal suspend fun load() = serialized {
        publish()
        if (leavingId != null || days.isNotEmpty()) scheduleSync()
    }

    private fun request(action: String, vararg fields: Pair<String, String>): JSONObject {
        val body = JSONObject().put("action", action)
        if (action != "preview") body.put("deviceKey", requireNotNull(key))
        for ((name, value) in fields) body.put(name, value)
        return transport.send(body)
    }

    private fun accept(response: JSONObject, acknowledgedDay: String? = null) {
        if (!response.has("week")) throw IOException("Missing partner week in response")
        if (!response.isNull("week") && response.optJSONObject("week") == null) {
            throw IOException("Unexpected partner week in response")
        }
        val next = response.optJSONObject("week")
        if (next == null) {
            // A lost create response can still be retried with the original idempotency token.
            if (rawWeek != null || leavingId != null) save { clear() }
            return
        }
        // Validate before replacing the last usable offline copy.
        projectPartnerWeek(next, today, token, emptySet())
        val sameWeek = rawWeek?.getString("id") == next.getString("id")
        val remaining = if (sameWeek) days - listOfNotNull(acknowledgedDay).toSet() else emptySet()
        save {
            putString("week", next.toString())
            putStringSet("days", remaining)
            if (next.getString("status") != "pending") remove("invite")
        }
    }

    internal suspend fun preview(code: String): PartnerInvitePreview = serialized {
        validateToken(code)
        val response = request("preview", "inviteToken" to code)
        PartnerInvitePreview(response.getString("alias"), code, response.optionalString("expiresAt"))
    }

    private fun prepareMembership(alias: String) {
        val name = alias.trim()
        require(name.codePointCount(0, name.length) in 1..32 &&
            name.none { it.isISOControl() || it == '\u061c' || it in '\u200e'..'\u200f' ||
                it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }) {
            INVALID_ALIAS
        }
        if (leavingId != null) flushLeave()
        if (rawWeek != null) throw PartnerRequestException(409)
        if (key == null) save {
            putString("device", secret())
            putString("zone", localZone().id)
        }
    }

    internal suspend fun create(alias: String) = serialized {
        prepareMembership(alias)
        if (token == null) save { putString("invite", secret()) }
        accept(request("create", "alias" to alias.trim(), "zone" to prefs.getString("zone", null)!!, "inviteToken" to token!!))
    }

    internal suspend fun join(code: String, alias: String, supporter: Boolean) = serialized {
        validateToken(code)
        prepareMembership(alias)
        val response = try {
            request("join", "inviteToken" to code, "alias" to alias.trim(), "zone" to prefs.getString("zone", null)!!,
                "role" to if (supporter) "supporter" else "participant")
        } catch (e: PartnerRequestException) {
            // The first join may have succeeded on the server before the response was lost.
            if (e.status == 410 || e.status == 409) {
                val existing = request("get")
                if (existing.optJSONObject("week") != null) accept(existing) else throw e
                return@serialized
            }
            throw e
        }
        accept(response)
    }

    internal suspend fun checkIn() = serialized {
        publish()
        val week = current.value.week ?: return@serialized
        if (!week.canCheckIn) return@serialized
        // Commit the intended local day before any I/O. Both an immediate request and its
        // background retry use this exact week and day, even after midnight or process death.
        save { putStringSet("days", days + week.today) }
        scheduleSync()
        flushDays()
    }

    private fun flushDays() {
        val id = rawWeek?.getString("id") ?: return
        var rejected: PartnerRequestException? = null
        for (day in days.sorted()) {
            try {
                accept(request("check_in", "weekId" to id, "day" to day), acknowledgedDay = day)
            } catch (e: PartnerRequestException) {
                if (e.retryable) throw e
                if (e.status !in setOf(403, 404, 409, 410, 422)) throw e
                save { putStringSet("days", days - day) }
                // Dropped marks (e.g. partner left) must never be presented as synced.
                accept(request("get"))
                rejected = e
            }
            if (rawWeek?.getString("id") != id) break
        }
        rejected?.let { throw it }
    }

    internal suspend fun cheer(kind: String) = serialized {
        require(kind in partnerReactions)
        publish()
        val week = current.value.week ?: return@serialized
        if (week.canCheer) accept(request("cheer", "weekId" to week.id, "day" to week.today, "kind" to kind))
    }

    internal suspend fun renew() = serialized {
        publish()
        val week = current.value.week ?: return@serialized
        if (week.canRenew) accept(request("renew", "weekId" to week.id))
    }

    internal suspend fun leave() = serialized {
        val id = rawWeek?.getString("id") ?: leavingId ?: return@serialized
        save {
            remove("week")
            remove("invite")
            remove("days")
            putString("leaving", id)
        }
        scheduleSync()
        flushLeave()
    }

    private fun flushLeave() {
        val id = leavingId ?: return
        try {
            request("leave", "weekId" to id)
        } catch (e: PartnerRequestException) {
            if (e.status !in setOf(403, 404, 409, 410)) throw e
            // If renewal and leave crossed in flight, refresh the accessible relationship.
            // A replacement week with a new ID still belongs to this same device.
            val active = request("get").optJSONObject("week")
            if (active != null) {
                val currentId = active.getString("id")
                if (currentId == id) throw e
                save { putString("leaving", currentId) }
                request("leave", "weekId" to currentId)
            }
        }
        save { clear() }
    }

    internal suspend fun refresh(participationAllowed: Boolean = true) = serialized {
        publish()
        if (key == null) return@serialized
        if (leavingId != null) flushLeave() else {
            syncDays(participationAllowed)
            if (key != null) accept(request("get"))
        }
    }

    internal suspend fun syncPending(participationAllowed: Boolean = true) = serialized {
        if (leavingId != null) flushLeave() else syncDays(participationAllowed)
    }

    private fun syncDays(participationAllowed: Boolean) {
        if (participationAllowed) flushDays()
        else if (days.isNotEmpty()) save { remove("days") }
    }

    private fun validateToken(code: String) {
        require(code.matches(Regex("[a-f0-9]{64}"))) { "This partner invitation is invalid." }
    }

    internal companion object { const val INVALID_ALIAS = "invalid_alias" }

    private fun secret(): String {
        val random = ByteArray(32).also(SecureRandom()::nextBytes)
        return random.joinToString("") { "%02x".format(it) }
    }
}
