package com.fastingshift.app.partner

import com.fastingshift.app.ui.partner.PartnerLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartnerLinksTest {
    private val token = "0123456789abcdef".repeat(4)

    @Test fun acceptsManualNativeAndBrandedInvitations() {
        listOf(token, PartnerLinks.link(token), "https://fastingshift.com/partner/#$token",
            "fastingshift://partner/#$token").forEach { assertEquals(token, PartnerLinks.token(it)) }
    }

    @Test fun rejectsUnrelatedHostsPathsAndMalformedCodes() {
        listOf("https://fastingshift.com.example/partner/#$token", "https://fastingshift.com@other.com/partner/#$token",
            "https://fastingshift.com/partner/?token=$token", "https://fastingshift.com:8080/partner/#$token",
            "https://fastingshift.com/c/#$token", "http://fastingshift.com/partner/#$token", token + "0",
            token.uppercase(), "fastingshift://partner/path/#$token").forEach { assertNull(PartnerLinks.token(it)) }
    }
}
