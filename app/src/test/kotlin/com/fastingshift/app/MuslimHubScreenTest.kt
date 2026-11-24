package com.fastingshift.app

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.data.repo.QadaRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.model.QadaDebt
import com.fastingshift.domain.ramadan.Hijri
import com.fastingshift.domain.ramadan.HijriMonth
import com.fastingshift.domain.ramadan.PrayerMethod
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Settings → Muslim fasting hub → Qada, on the real app. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class MuslimHubScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var screening: ScreeningRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var qada: QadaRepository
    @Inject lateinit var fasts: FastRepository

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            screening.save(ScreeningRecord(Instant.now(), ScreeningAnswers(40, false, false, false, emptySet(), 175.0, 70.0), GateResult.OK, false))
            settings.setRamadanCity("sg_singapore", PrayerMethod.MUIS)
            settings.setOnboardingComplete(true)
        }
    }

    /** DataStore is a process-wide singleton under Robolectric: leave it as the next test expects. */
    @After fun tearDown() {
        runBlocking {
            settings.setOnboardingComplete(false)
            settings.setHijriOffsetDays(0)
            settings.setSunnahReminders(false)
            settings.setSunnahTypes(emptySet())
        }
    }

    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun waitFor(text: String, substring: Boolean = false) = try {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        println("DBG>> " + compose.onRoot().printToString().lines().joinToString("\nDBG>> "))
        throw e
    }

    private fun openHubFromSettings() {
        waitFor(s(R.string.tab_settings))
        compose.onNodeWithText(s(R.string.tab_settings)).performClick()
        compose.onNodeWithTag("settings_open_fasting").performScrollTo().performClick()
        waitFor(s(R.string.muslim_hub_entry))
        compose.onNodeWithText(s(R.string.muslim_hub_entry)).performScrollTo().performClick()
        waitFor(s(R.string.muslim_upcoming))
    }

    @Test fun hubShowsTodaysHijriDateAndQadaLedger() {
        val today = Hijri.of(LocalDate.now())!!
        openHubFromSettings()
        // Today's Hijri date, in the app's language.
        val month = s(com.fastingshift.app.ui.ramadan.MuslimText.monthName(today.month))
        waitFor(s(R.string.hijri_date, today.day, month, today.year))
        // Your qada ledger is part of the hub.
        waitFor(s(R.string.qada_ledger))
    }

    @Test fun historyLabelsQadaFasts() {
        runBlocking {
            val start = Instant.now().minus(Duration.ofHours(14))
            fasts.insert(Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(12)), protocolId = "custom_12", kind = FastKind.QADA).end(start.plus(Duration.ofHours(12))))
        }
        waitFor(s(R.string.tab_history))
        compose.onNodeWithText(s(R.string.tab_history)).performClick()
        waitFor(s(R.string.history_title))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(s(R.string.fast_kind_qada)))
        waitFor(s(R.string.fast_kind_qada))
        assertEquals(1, compose.onAllNodesWithText(s(R.string.fast_kind_qada)).fetchSemanticsNodes().size)
    }
}
