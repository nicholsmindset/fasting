package com.fastingshift.app.ui.partner

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.google.zxing.qrcode.decoder.Decoder
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PartnerInviteQrTest {
    @get:Rule val compose = createComposeRule()
    private val token = "81e3".repeat(16)
    private val expires = Instant.now().plusSeconds(3600)
    private val waiting = PartnerWeek(
        id = "new-week", status = "pending", self = PartnerMember("Host", "participant", emptyList()),
        partner = null, startDate = null, endDate = null, inviteCode = token, lastCheer = null,
        canCheckIn = false, canCheer = false, canRenew = false, renewalRequestedByMe = false,
        renewalRequestedByPartner = false, inviteExpiresAt = expires.toString(), today = "2026-11-26",
    )

    @Test fun qrDecodesToTheExistingPartnerInviteIncludingItsFragment() {
        val expected = requireNotNull(waiting.shareableInviteLink(expires.minusMillis(1)))
        val matrix = partnerQrModules(expected)
        val rect = requireNotNull(matrix.enclosingRectangle)
        assertEquals(4, rect[0])
        assertEquals(4, rect[1])
        val code = Array(rect[3]) { y -> BooleanArray(rect[2]) { x -> matrix[x + rect[0], y + rect[1]] } }
        val decoded = Decoder().decode(code).text
        assertEquals(PartnerLinks.link(token), decoded)
        assertEquals(token, PartnerLinks.token(decoded))
        assertNull(waiting.shareableInviteLink(expires))
        assertNull(waiting.copy(status = "active").shareableInviteLink(Instant.now()))
        assertNull(waiting.copy(status = "left").shareableInviteLink(Instant.now()))
        assertNull(waiting.copy(inviteCode = "broken").shareableInviteLink(Instant.now()))
    }

    @Test fun hostCanShowQrAndStaleInvitationsRemoveAllSharingActions() {
        var week by mutableStateOf(waiting)
        compose.setContent {
            FastingShiftTheme(darkTheme = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { WaitingWeek(week) }
            }
        }
        compose.onNodeWithTag("partner_share").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("partner_copy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("partner_show_qr").performScrollTo().performClick()
        compose.onNodeWithTag("partner_qr_image").assertIsDisplayed()
        compose.runOnIdle { week = week.copy(inviteExpiresAt = Instant.EPOCH.toString()) }
        compose.onNodeWithTag("partner_qr_image").assertDoesNotExist()
        compose.onNodeWithTag("partner_show_qr").assertDoesNotExist()
        compose.onNodeWithTag("partner_share").assertDoesNotExist()
        compose.onNodeWithTag("partner_copy").assertDoesNotExist()
    }
}
