package com.fastingshift.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test fun malformedLinkIsRejectedCalmly() {
        open("https://fastingshift.com/c/not*a*code")
        waitFor(s(R.string.challenge_join_invalid))
        assertEquals(0, runBlocking { challenges.observeAll().first() }.size)
    }
}
