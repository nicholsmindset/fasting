package com.fastingshift.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.repo.ChallengeRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.model.ChallengeType
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Opening an invite link (https://fastingshift.com/c/...) shows the challenge and joins it locally. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class ChallengeJoinTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var challenges: ChallengeRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
            settings.setOnboardingComplete(true)
        }
    }

    /** DataStore is a process-wide singleton under Robolectric: leave it as the next test expects. */
    @After fun tearDown() {
        scenario?.close()
        runBlocking { settings.setOnboardingComplete(false) }
    }

    private fun s(id: Int, vararg args: Any) = ApplicationProvider.getApplicationContext<android.app.Application>().getString(id, *args)

    private fun waitFor(text: String) = try {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        println("DBG>> " + compose.onRoot().printToString().lines().joinToString("\nDBG>> "))
        throw e
    }

    private fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @Test fun deepLinkJoinsTheChallenge() {
        val start = LocalDate.now().plusDays(1)
        val invite = ChallengeInvite(ChallengeType.SUNNAH_MON_THU, null, start, 28, "Family")
        open(ChallengeLinks.link(invite))

        waitFor("Family")
        compose.onNodeWithText(s(R.string.challenge_join)).performScrollTo().performClick()

        // Joining opens the challenge's progress.
        waitFor(s(R.string.challenge_leave))
        val joined = runBlocking { challenges.observeAll().first() }
        assertEquals(1, joined.size)
        assertEquals(ChallengeInvite.of(joined.single()), invite)
    }

    @Test fun freshInstallInviteCanBeJoinedAfterFinishingScreening() {
        runBlocking { settings.setOnboardingComplete(false) }
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", LocalDate.now().plusDays(1), 7, "Welcome challenge")
        open(ChallengeLinks.link(invite))

        fun tap(label: Int) {
            waitFor(s(label))
            val node = compose.onNodeWithText(s(label))
            runCatching { node.performScrollTo() }
            node.performClick()
        }
        fun enter(label: Int, value: String) {
            compose.onNode(hasSetTextAction() and hasAnyDescendant(hasText(s(label))), useUnmergedTree = true)
                .performScrollTo().performTextReplacement(value)
        }

        tap(R.string.onb_get_started)
        tap(R.string.onb_skip)
        tap(R.string.goal_consistency)
        tap(R.string.onb_next)
        waitFor(s(R.string.onb_screen_title))
        enter(R.string.onb_age, "40")
        enter(R.string.onb_height, "175")
        enter(R.string.onb_weight, "70")
        tap(R.string.onb_next)
        waitFor(s(R.string.onb_ok_title))
        tap(R.string.onb_next)
        waitFor(s(R.string.onb_protocol_title))
        tap(R.string.onb_next)
        tap(R.string.onb_not_now)

        // A briefly visible Join screen is insufficient: completion used to pop it back to Timer.
        waitFor("Welcome challenge")
        compose.waitForIdle()
        tap(R.string.challenge_join)
        waitFor(s(R.string.challenge_leave))
        assertEquals(invite, ChallengeInvite.of(runBlocking { challenges.observeAll().first().single() }))
    }

    @Test fun pilotInvitePreviewsBeforeJoiningAndKeepsThePrivatePlanChoice() {
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", LocalDate.now().plusDays(1), 7, "Walking group")
        open(ChallengeLinks.link(invite, pilot = true))
        waitFor("Walking group")
        compose.onNodeWithText(s(R.string.challenge_join)).assertExists()
        assertEquals(0, runBlocking { challenges.observeAll().first() }.size)
        compose.onNodeWithText(s(R.string.friend_own_plan)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.challenge_join)).performScrollTo().performClick()
        // The invite preview explains personal progress too. Wait for the detail route itself.
        waitFor(s(R.string.challenge_leave))
        compose.waitForIdle()
        compose.onNodeWithText(s(R.string.friend_personal_count)).assertExists()
        val enrollment = runBlocking { challenges.observeAll().first().single() }
        assertEquals(true, enrollment.followsOwnPlan)
        assertEquals(invite, ChallengeInvite.of(enrollment))
        runBlocking {
            assertEquals(enrollment.id, challenges.join(invite.toChallenge(Instant.now())))
            assertEquals(true, challenges.observeAll().first().single().followsOwnPlan)
        }
        compose.onNodeWithText(s(R.string.friend_own_plan)).performScrollTo().performClick()
        compose.waitUntil(10_000) { !runBlocking { challenges.observeAll().first().single().followsOwnPlan } }
    }

    @Test fun changedScreeningClosesTheInviteAndBlocksAStaleEnrollment() {
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", LocalDate.now().plusDays(1), 7, "Friends")
        open(ChallengeLinks.link(invite))
        waitFor("Friends")

        val soft = runBlocking {
            val prior = requireNotNull(screening.getLatest())
            prior.copy(
                answeredAt = prior.answeredAt.plusSeconds(1),
                answers = prior.answers.copy(medications = setOf(Medication.GLP1)),
                gateResult = GateResult.SOFT,
                clinicianAcknowledged = false,
            ).also { screening.save(it) }
        }
        waitFor(s(R.string.challenges_not_allowed))
        compose.onNodeWithText(s(R.string.challenge_join)).assertDoesNotExist()
        val pending = invite.toChallenge(Instant.now())
        runBlocking {
            assertNull(challenges.join(pending))
            assertEquals(0, challenges.observeAll().first().size)
            screening.save(soft.copy(answeredAt = soft.answeredAt.plusSeconds(1), clinicianAcknowledged = true))
        }
        waitFor("Friends")
        compose.onNodeWithText(s(R.string.challenge_join)).performScrollTo().performClick()
        waitFor(s(R.string.challenge_leave))
        assertEquals(invite, ChallengeInvite.of(runBlocking { challenges.observeAll().first().single() }))
    }

    @Test fun concurrentEnrollmentsReuseOneRecordAndALeftChallengeCanBeRejoined() = runBlocking {
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", LocalDate.now(), 7, "Friends")
        val challenge = invite.toChallenge(Instant.now())
        val ids = List(2) { async { challenges.join(challenge) } }.awaitAll()
        val original = challenges.observeAll().first().single()
        assertEquals(listOf(original.id, original.id), ids)
        challenges.update(original.copy(leftAt = Instant.now()))
        val rejoined = challenges.join(challenge)
        val records = challenges.observeAll().first()
        assertEquals(2, records.size)
        assertEquals(rejoined, records.single { it.leftAt == null }.id)
    }

    @Test fun malformedLinkIsRejectedCalmly() {
        open("https://fastingshift.com/c/not*a*code")
        waitFor(s(R.string.challenge_join_invalid))
        assertEquals(0, runBlocking { challenges.observeAll().first() }.size)
    }
}
