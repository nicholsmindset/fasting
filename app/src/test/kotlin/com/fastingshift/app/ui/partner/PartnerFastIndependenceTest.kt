package com.fastingshift.app.ui.partner

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.R
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.FastStatus
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class PartnerFastIndependenceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private val pair = ActivePair()
    @BindValue @JvmField val host: PartnerWeekRepository = memberRepository("partner_week", "host")
    private val guest = memberRepository("guest_test_partner_week", "guest")

    @Inject lateinit var fasts: FastRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var screening: ScreeningRepository

    private fun memberRepository(name: String, member: String): PartnerWeekRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        check(prefs.edit().clear().putString("device", member).putString("zone", "UTC").commit())
        val clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC)
        return PartnerWeekRepository(prefs, pair, clock, { ZoneOffset.UTC }, {})
    }

    private fun waitForText(id: Int): String = compose.activity.getString(id).also { text ->
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun endingEarlyKeepsTheEarnedMarkOnBothDevicesAndSendsNoFastDetails() {
        hilt.inject()
        val original = runBlocking { settings.toRecord() }
        try {
            runBlocking {
                screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(),
                    175.0, 70.0), GateResult.OK, false))
                settings.setOnboardingComplete(true)
                host.refresh()
                guest.refresh()
            }
            compose.onNodeWithText(waitForText(R.string.timer_start)).performClick()
            val endButton = waitForText(R.string.timer_end)
            val active = assertNotNull(runBlocking { fasts.getActive() })

            runBlocking {
                assertEquals(emptyList(), host.cachedWeek()?.self?.days)
                host.savePlannedDays("pair-week", setOf("2026-11-27", "2026-11-29"))
                host.checkIn()
                guest.refresh()
            }
            val guestBefore = assertNotNull(runBlocking { guest.cachedWeek() })
            assertEquals(listOf("2026-11-26"), guestBefore.partner?.days)
            assertEquals(guestBefore.partner, runBlocking { host.cachedWeek()?.self })

            compose.onNodeWithText(endButton).performClick()
            waitForText(R.string.timer_start)
            runBlocking {
                assertNull(fasts.getActive())
                assertEquals(FastStatus.ENDED_EARLY, fasts.getById(active.id)?.status)
                // Read the host cache before refresh so lost local marks cannot be masked by the server.
                assertEquals(guestBefore.partner, host.cachedWeek()?.self)
                host.refresh()
                guest.refresh()
                assertEquals(guestBefore.partner, host.cachedWeek()?.self)
                assertEquals(guestBefore, guest.cachedWeek())
                assertEquals(setOf("2026-11-27", "2026-11-29"), host.snapshot.value.plannedDays)
                assertEquals(emptySet(), guest.snapshot.value.plannedDays)
            }
            assertEquals(1, pair.checkIns)
        } finally {
            runBlocking { settings.restore(original) }
        }
    }

    /** Two already-enrolled people. Only local-day check-ins can change their shared record. */
    private class ActivePair : PartnerTransport {
        private val hostDays = mutableSetOf<String>()
        var checkIns = 0
            private set

        @Synchronized override fun send(body: JSONObject): JSONObject {
            val viewer = body.getString("deviceKey")
            check(viewer == "host" || viewer == "guest")
            when (val action = body.getString("action")) {
                "get" -> assertEquals(setOf("action", "deviceKey"), body.keys().asSequence().toSet())
                "check_in" -> {
                    assertEquals(setOf("action", "deviceKey", "weekId", "day"), body.keys().asSequence().toSet())
                    assertEquals("host", viewer)
                    assertEquals("pair-week", body.getString("weekId"))
                    hostDays.add(body.getString("day"))
                    checkIns++
                }
                else -> error("Unexpected shared action: $action")
            }
            fun member(alias: String, days: Collection<String>) = JSONObject()
                .put("alias", alias).put("role", "participant").put("days", JSONArray(days)).put("renewed", false)
            val week = JSONObject().put("id", "pair-week").put("status", "active").put("self", viewer)
                .put("startDay", "2026-11-23").put("endDay", "2026-11-29")
                .put("host", member("Robert", hostDays)).put("guest", member("Maya", listOf("2026-11-25")))
                .put("cheers", JSONArray())
            return JSONObject().put("week", week)
        }
    }
}
