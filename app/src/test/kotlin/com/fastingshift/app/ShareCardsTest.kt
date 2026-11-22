package com.fastingshift.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.share.ShareCard
import com.fastingshift.app.share.ShareCards
import com.fastingshift.domain.history.WeeklyRecap
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Share cards: 1080×1350 images that carry only what is printed on them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardsTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val zone = ZoneId.of("Asia/Singapore")
    private val start = Instant.parse("2026-11-20T12:00:00Z")

    private fun fast(protocol: String, kind: FastKind = FastKind.REGULAR) =
        Fast(startAt = start, targetEndAt = start.plus(Duration.ofHours(16)), protocolId = protocol, kind = kind, note = "private", mood = 2)
            .end(start.plus(Duration.ofHours(16)).plus(Duration.ofMinutes(20)))

    @Test fun fastCardShowsLengthProtocolAndDateOnly() {
        val card = ShareCards.forFast(app.resources, fast("window_16_8"), zone)
        assertEquals(ShareCard.FinishedFast(Duration.ofMinutes(16 * 60 + 20), "16:8 fast", LocalDate.of(2026, 11, 21)), card)
    }

    @Test fun religiousFastsUseTheirKind() {
        assertEquals("Sunnah", ShareCards.forFast(app.resources, fast("window_16_8", FastKind.SUNNAH), zone).label)
        assertEquals("Fast", ShareCards.forFast(app.resources, fast("custom_20"), zone).label)
    }

    @Test fun weekCard() {
        val recap = WeeklyRecap(LocalDate.of(2026, 11, 16), Duration.ofHours(62), 4, 3, 7, 3, 2, 5)
        assertEquals(ShareCard.Week(LocalDate.of(2026, 11, 16), Duration.ofHours(62), 4), ShareCards.forWeek(recap))
    }

    @Test fun rendersAtTheShareSize() {
        val cards = listOf(
            ShareCards.forFast(app.resources, fast("window_16_8"), zone),
            ShareCard.Week(LocalDate.of(2026, 11, 16), Duration.ofHours(62), 4),
            ShareCard.ChallengeProgress("Family Mon/Thu", 5, 8, "https://fastingshift.com/c/MXxNfHwyMDI2MTEyM3wyOHxGYW1pbHk"),
        )
        cards.forEach { c ->
            val bmp = ShareCards.render(app.resources, c)
            assertEquals(ShareCards.WIDTH, bmp.width)
            assertEquals(ShareCards.HEIGHT, bmp.height)
        }
    }
}
