package com.fastingshift.app.ui.partner

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PartnerWeekRepositoryTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("partner-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    private val calls = mutableListOf<JSONObject>()
    private var background = 0
    private var service: (JSONObject) -> JSONObject = { error("Unexpected partner service request") }

    private fun repository(instant: String = "2026-11-25T15:58:00Z"): PartnerWeekRepository =
        PartnerWeekRepository(
            prefs,
            PartnerTransport { calls += it; service(it) },
            Clock.fixed(Instant.parse(instant), zone), { zone }, { background++ },
        )

    private fun member(alias: String, role: String, days: List<String>) = JSONObject()
        .put("alias", alias).put("role", role).put("days", JSONArray(days)).put("renewed", false)

    private fun sample(status: String = "active", role: String = "participant", dates: List<String> = emptyList()) =
        JSONObject().put("week", JSONObject().apply {
            put("id", "55d97ee8-33b6-4d2e-a944-998f8d985339")
            put("status", status)
            put("self", "host")
            put("host", member("Host", role, dates))
            put("guest", if (status == "pending") JSONObject.NULL else member("Friend", "participant", emptyList()))
            put("expiresAt", "2026-11-30T00:00:00Z")
            put("startDay", if (status == "pending") JSONObject.NULL else "2026-11-23")
            put("endDay", if (status == "pending") JSONObject.NULL else "2026-11-29")
            put("cheers", JSONArray())
        })

    @Test fun ageAndFastingEligibilityAreCheckedIndependently() {
        fun record(age: Int, gate: GateResult) = ScreeningRecord(
            Instant.EPOCH, ScreeningAnswers(age, false, false, false, emptySet(), 172.0, 73.0), gate, false,
        )
        assertEquals(PartnerEligibility(false, false), null.partnerEligibility())
        assertEquals(PartnerEligibility(false, false), record(17, GateResult.OK).partnerEligibility())
        assertEquals(PartnerEligibility(true, false), record(18, GateResult.BLOCK).partnerEligibility())
        assertEquals(PartnerEligibility(true, true), record(18, GateResult.OK).partnerEligibility())
    }

    @Test fun revokedParticipationDropsUnsentCheckInButPermitsQueuedLeave() = runBlocking {
        val repo = repository()
        service = { sample() }
        repo.create("Host")
        service = { throw IOException("offline") }
        assertFailsWith<IOException> { repo.checkIn() }
        val requestsBefore = calls.size
        repo.syncPending(participationAllowed = false)
        assertEquals(requestsBefore, calls.size)
        assertTrue(repo.snapshot.value.pendingDays.isEmpty())
        assertFailsWith<IOException> { repo.leave() }
        service = { JSONObject().put("week", JSONObject.NULL) }
        repo.syncPending(participationAllowed = false)
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun neverEnrolledReadsAndRefreshGenerateNoIdentityOrTraffic() = runBlocking {
        val repo = repository()
        repo.load()
        repo.refresh()
        repo.syncPending()
        assertNull(repo.cachedWeek())
        assertTrue(calls.isEmpty())
        assertTrue(prefs.all.isEmpty())
        assertEquals(0, background)
    }

    @Test fun lostCreateResponseRetriesWithSameDeviceAndInvite() = runBlocking {
        val repo = repository()
        service = { throw IOException("lost response") }
        assertFailsWith<IOException> { repo.create("Host") }
        service = { sample("pending") }
        repo.create("Host")
        val original = calls.first()
        val retry = calls.last()
        assertEquals(original.getString("deviceKey"), retry.getString("deviceKey"))
        assertEquals(original.getString("inviteToken"), retry.getString("inviteToken"))
        assertTrue(original.getString("inviteToken").matches(Regex("[a-f0-9]{64}")))
        assertEquals(original.getString("inviteToken"), repo.cachedWeek()?.inviteCode)
    }

    @Test fun offlineMarkSurvivesRestartAndMidnightWithoutDuplication() = runBlocking {
        val repo = repository()
        service = { sample() }
        repo.create("Host")
        service = { throw IOException("offline") }
        assertFailsWith<IOException> { repo.checkIn() }
        repo.checkIn()
        assertEquals(listOf("2026-11-25"), repo.cachedWeek()?.self?.days)
        assertFalse(repo.cachedWeek()!!.canCheckIn)
        assertEquals(1, background)

        val restored = repository("2026-11-25T16:05:00Z")
        assertEquals("2026-11-26", restored.cachedWeek()?.today)
        assertEquals(setOf("2026-11-25"), restored.snapshot.value.pendingDays)
        service = { request ->
            assertEquals("check_in", request.getString("action"))
            assertEquals("2026-11-25", request.getString("day"))
            sample(dates = listOf("2026-11-25"))
        }
        restored.syncPending()
        assertTrue(restored.snapshot.value.pendingDays.isEmpty())
        assertEquals(listOf("2026-11-25"), restored.cachedWeek()?.self?.days)
        assertTrue(restored.cachedWeek()!!.canCheckIn)
    }

    @Test fun supporterAndThreeMarksCannotAddMarks() = runBlocking {
        val repo = repository()
        service = { sample(role = "supporter") }
        repo.create("Host")
        assertTrue(repo.cachedWeek()!!.canCheer)
        repo.checkIn()
        assertEquals(1, calls.size)
        service = { sample(dates = listOf("2026-11-22", "2026-11-23", "2026-11-24")) }
        repo.refresh()
        repo.checkIn()
        assertEquals(2, calls.size)
    }

    @Test fun leaveDropsOfflineMarksImmediatelyAndCompletesAfterReconnection() = runBlocking {
        val repo = repository()
        service = { sample() }
        repo.create("Host")
        service = { throw IOException("offline") }
        assertFailsWith<IOException> { repo.checkIn() }
        assertFailsWith<IOException> { repo.leave() }
        assertTrue(repo.snapshot.value.leaving)
        assertNull(repo.cachedWeek())
        assertTrue(repo.snapshot.value.pendingDays.isEmpty())
        val actionsBeforeReconnect = calls.size
        val restored = repository()
        service = { JSONObject().put("week", JSONObject.NULL) }
        restored.syncPending()
        assertEquals(listOf("leave"), calls.drop(actionsBeforeReconnect).map { it.getString("action") })
        assertTrue(prefs.all.isEmpty())
        assertFalse(restored.snapshot.value.leaving)
    }

    @Test fun partnerWhoAlreadyLeftCannotReceiveAnOfflineMark() = runBlocking {
        val repo = repository()
        service = { sample() }
        repo.create("Host")
        service = { throw IOException("offline") }
        assertFailsWith<IOException> { repo.checkIn() }
        service = { req ->
            when (req.getString("action")) {
                "get" -> JSONObject().put("week", JSONObject.NULL)
                else -> throw PartnerRequestException(410)
            }
        }
        assertFailsWith<PartnerRequestException> { repo.syncPending() }
        assertNull(repo.cachedWeek())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun invitationPreviewHasNoDeviceKeyAndDoesNotOptIn() = runBlocking {
        val repo = repository()
        service = { JSONObject().put("alias", "Friend").put("expiresAt", "2026-11-30T00:00:00Z") }
        val invite = "0".repeat(64)
        assertEquals("Friend", repo.preview(invite).hostAlias)
        assertFalse(calls.single().has("deviceKey"))
        assertTrue(prefs.all.isEmpty())
        assertFailsWith<IllegalArgumentException> { repo.preview("not-a-token") }
        assertEquals(1, calls.size)
    }

    @Test fun localEndOfWeekWaitsForOtherTimeZoneBeforeRenewal() = runBlocking {
        val repo = repository("2026-11-30T01:00:00Z")
        service = { sample("active") }
        repo.create("Host")
        val recap = assertNotNull(repo.cachedWeek())
        assertEquals("finished", recap.status)
        assertFalse(recap.canRenew)
        repo.renew()
        assertEquals(1, calls.size)
        service = { sample("finished") }
        repo.refresh()
        assertTrue(repo.cachedWeek()!!.canRenew)
    }

    @Test fun onlyIncomingReactionsDisplayAndRenewNeedsASecondVote() = runBlocking {
        val repo = repository()
        service = { sample("finished").also { payload ->
            val week = payload.getJSONObject("week")
            week.getJSONObject("host").put("renewed", true)
            week.getJSONArray("cheers")
                .put(JSONObject().put("from", "guest").put("day", "2026-11-25").put("kind", "here"))
                .put(JSONObject().put("from", "host").put("day", "2026-11-25").put("kind", "thanks"))
        } }
        repo.create("Host")
        val week = assertNotNull(repo.cachedWeek())
        assertEquals("here", week.lastCheer)
        assertTrue(week.renewalRequestedByMe)
        assertFalse(week.canRenew)
        repo.renew()
        assertEquals(1, calls.size)
    }
}
