package com.fastingshift.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.model.ChallengeType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class PendingInviteTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject lateinit var settings: SettingsRepository

    @Test fun rotationKeepsInviteUntilHandledAndDoesNotReplayItAfterBack() {
        hilt.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun waitFor(resource: Int) {
            val text = context.getString(resource)
            compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        }
        val invite = ChallengeInvite(ChallengeType.SUNNAH_MON_THU, null, LocalDate.of(2026, 11, 23), 7, null)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ChallengeLinks.link(invite)), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runBlocking { settings.setOnboardingComplete(false) }
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                waitFor(R.string.onb_get_started)
                scenario.recreate()
                waitFor(R.string.onb_get_started)
                runBlocking { settings.setOnboardingComplete(true) }
                waitFor(R.string.challenge_join_title)
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                waitFor(R.string.tab_timer)
                scenario.recreate()
                waitFor(R.string.tab_timer)
                compose.waitForIdle()
                check(compose.onAllNodesWithText(context.getString(R.string.challenge_join_title)).fetchSemanticsNodes().isEmpty())
            }
        } finally {
            runBlocking { settings.setOnboardingComplete(false) }
        }
    }
}
