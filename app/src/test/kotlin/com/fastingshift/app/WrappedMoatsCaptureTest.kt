package com.fastingshift.app

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.history.AnnualStory
import com.fastingshift.app.ui.history.SundayMini
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.history.AnnualRecap
import com.fastingshift.domain.history.SundayRecap
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rSG-w411dp-h822dp-xxhdpi")
class WrappedMoatsCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val demo = AnnualRecap(2026, LocalDate.of(2026,12,6), 18, Duration.ofHours(16), "16:8", false, true, 4, 12, 20)

    private fun export(action: String, opened: () -> Int, expected: Int, name: String): Int {
        compose.onNodeWithText(action).performClick()
        compose.waitUntil(12_000) { opened() == expected }
        val chooser = requireNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        @Suppress("DEPRECATION") val uri = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("image/png", send.type)
        assertEquals("content", uri.scheme)
        val bytes = requireNotNull(app.contentResolver.openInputStream(uri)).use { it.readBytes() }
        val image = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertTrue(image.width > 100 && image.height > 100)
        if (System.getProperty("fastingshift.screenshotDir") != null) {
            moatsOutput("wrapped").resolve("$name.png").writeBytes(bytes)
        }
        return bytes.contentHashCode()
    }

    @Test fun annualSharesEachSelectedSlideAsItsOwnPng() {
        var views = 0
        var opens = 0
        compose.setContent { FastingShiftTheme {
            AnnualStory(demo, { views++ }, { opens++ }, {})
        } }
        val hashes = mutableSetOf<Int>()
        for (index in 1..7) {
            compose.onNodeWithText("Slide $index of 7").assertExists()
            hashes += export("Share this slide", { opens }, index, "annual-$index")
            if (index < 7) {
                compose.onNodeWithText("Next").performClick()
                compose.waitForIdle()
            }
        }
        assertEquals(1, views)
        assertEquals(7, hashes.size)
    }

    @Test fun sundaySharesOnlyItsWeeklyArtwork() {
        var opens = 0
        compose.setContent { FastingShiftTheme {
            SundayMini(SundayRecap(LocalDate.of(2026,11,30), 2, Duration.ofHours(12), Duration.ofHours(13)), {}, { opens++ })
        } }
        compose.onNodeWithText("shorter than last week.", substring = true).assertExists()
        export("Share weekly recap", { opens }, 1, "sunday")
    }
}
