package com.fastingshift.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.challenge.challengeInviteIntent
import com.fastingshift.domain.challenge.ChallengeInvite
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.challenge.InviteResult
import com.fastingshift.domain.model.ChallengeType
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "ms")
class ChallengeInviteShareTest {
    @Test fun inviteContainsOnlyLocalizedTextAndTheCompleteJoinableLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = "Seminggu bersama"
        val invite = ChallengeInvite(ChallengeType.DAILY_PROTOCOL, "window_16_8", LocalDate.of(2026, 11, 23), 7, title)
        val link = ChallengeLinks.link(invite)
        val send = challengeInviteIntent(context, title, link)

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras?.keySet())
        assertNull(send.clipData)
        val text = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertEquals(context.getString(R.string.share_challenge_text, title, link), text)
        assertEquals(InviteResult.Valid(invite), ChallengeLinks.parse(text.substringAfterLast(' ')))
    }
}
