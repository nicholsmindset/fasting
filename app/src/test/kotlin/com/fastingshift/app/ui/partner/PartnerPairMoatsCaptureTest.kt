package com.fastingshift.app.ui.partner

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.moatsOutput
import com.fastingshift.app.share.ShareCards
import java.io.ByteArrayOutputStream
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rSG-w320dp-h720dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerPairMoatsCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val days = listOf("2026-12-02", "2026-12-03", "2026-12-05")
    private val member = PartnerMember("Alexandria Santoso", "participant", days)
    private val other = PartnerMember("Muhammad Nur Hakim", "participant", days)
    private val active = PartnerWeek(
        id = "test-week", status = "active", self = member, partner = other,
        startDate = "2026-12-01", endDate = "2026-12-07", inviteCode = null, lastCheer = null,
        canCheckIn = false, canCheer = true, canRenew = false, renewalRequestedByMe = false,
        renewalRequestedByPartner = false, inviteExpiresAt = null, today = "2026-12-05",
    )
    private var week by mutableStateOf(active)
    private var ui by mutableStateOf(PartnerUiState(participantAllowed = true, adultAllowed = true))
    private var sent = ""
    private var pauses = 0

    private fun display() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                FastingShiftTheme(darkTheme = false) {
                    Surface(Modifier.fillMaxSize().testTag("pair-screen")) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WeekProgressContent(week, ui, {}, { sent = it }, {}, { pauses++ })
                        }
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") != null) {
            compose.onNodeWithTag("pair-screen").captureRoboImage(moatsOutput("partner").resolve("$name.png").absolutePath)
        }
    }

    private fun assertTextFits(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow, label)
    }

    @Test fun keepsakeEncodesAsAPortraitPngWithThreeMarksAndOnlyAPeriodAsInput() {
        val res = ApplicationProvider.getApplicationContext<Application>().resources
        val image = ShareCards.renderPartnerKeepsake(res, "1 Dec 2026 – 7 Dec 2026")
        val bytes = ByteArrayOutputStream().use { out ->
            try { check(image.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray() }
            finally { image.recycle() }
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(1080, decoded.width)
            assertEquals(1350, decoded.height)
            assertEquals(Color.rgb(7, 31, 28), decoded.getPixel(0, 0))
            repeat(3) { index -> assertEquals(Color.rgb(131, 218, 184), decoded.getPixel(126 + index * 84, 368)) }
        } finally { decoded.recycle() }
        if (System.getProperty("fastingshift.screenshotDir") != null) {
            moatsOutput("partner").resolve("completed-week-keepsake.png").writeBytes(bytes)
        }
    }

    @Test fun marksAreSpokenTogetherAndKeepsakeWaitsForConfirmedCheckIns() {
        week = active.copy(self = member.copy(days = days.take(2)), canCheckIn = true)
        display()
        compose.onNodeWithTag("partner_self_marks").assertTextContains("Alexandria Santoso (you)").assertTextContains("2 of 3 check-ins")
        compose.onNodeWithTag("partner_other_marks").assertTextContains(other.alias).assertTextContains("3 of 3 check-ins")
        assertTextFits("Alexandria Santoso (you)")
        assertTextFits(other.alias)
        compose.onNodeWithTag("partner_keepsake").assertDoesNotExist()
        capture("participants-double-text")
        compose.runOnIdle { week = active; ui = ui.copy(pendingDays = setOf(days.last())) }
        compose.onNodeWithTag("partner_keepsake").assertDoesNotExist()
        compose.runOnIdle { ui = ui.copy(pendingDays = emptySet()) }
        compose.onNodeWithTag("partner_keepsake").performScrollTo().assertIsDisplayed()
    }

    @Test fun supporterGetsOneProgressRowAndCanChooseAnExactReply() {
        week = active.copy(self = member.copy(role = "supporter", days = emptyList()))
        display()
        compose.onNodeWithTag("partner_self_marks").assertDoesNotExist()
        compose.onNodeWithTag("partner_other_marks").assertTextContains(other.alias).assertTextContains("3 of 3 check-ins")
        compose.onNodeWithTag("partner_checkin").assertDoesNotExist()
        compose.onNodeWithTag("partner_keepsake").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("partner_choose_preset").performScrollTo().performClick()
        compose.onNodeWithText("Thanks for checking in.").performClick()
        val send = compose.onNodeWithTag("partner_cheer").performScrollTo()
        send.assertTextContains("Send “Thanks for checking in.”").assertHeightIsAtLeast(48.dp)
        assertTextFits("Send “Thanks for checking in.”")
        capture("supporter-action-double-text")
        send.performClick()
        compose.runOnIdle { assertEquals("thanks", sent) }
    }

    @Test fun finishedWeekKeepsItsMarksAndWithdrawingARequestNeedsConfirmation() {
        week = active.copy(status = "finished", renewalRequestedByMe = true, canCheer = false)
        display()
        compose.onNodeWithTag("partner_pause").performScrollTo().performClick()
        compose.onNodeWithText("Withdraw your request for another week?").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.runOnIdle { assertEquals(0, pauses) }
        compose.onNodeWithTag("partner_pause").performScrollTo().performClick()
        compose.onNode(hasText("Pause here") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle {
            assertEquals(1, pauses)
            week = week.copy(renewalRequestedByMe = false, canRenew = true)
            ui = ui.copy(pausedWeekId = week.id)
        }
        compose.onNodeWithTag("partner_pause").assertDoesNotExist()
        compose.onNodeWithTag("partner_paused").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("partner_renew").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("partner_self_marks").assertTextContains("3 of 3 check-ins")
    }
}
