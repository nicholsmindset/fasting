package com.fastingshift.app.ui.revival

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.moatsOutput
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.data.db.RevivalEventEntity
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rSG-w411dp-h822dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RevivalMoatsCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val event = RevivalEventEntity("january-2027", Instant.parse("2026-12-31T16:00:00Z").toEpochMilli(),
        Instant.parse("2027-01-14T16:00:00Z").toEpochMilli(), "Asia/Singapore", "days_on_plan", 3)

    private fun capture(name: String) {
        if (System.getProperty("fastingshift.screenshotDir") == null) return
        compose.onNodeWithTag("revival-capture").captureRoboImage(moatsOutput("en").resolve("revival-$name.png").absolutePath)
    }

    @Test fun joiningAndOpeningTheCelebrationAreExplicitActions() {
        val state = mutableStateOf(RevivalState(event = event, best = 42))
        var joined: String? = null
        compose.setContent {
            FastingShiftTheme(darkTheme = false) {
                Surface(Modifier.fillMaxWidth().testTag("revival-capture"), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.padding(16.dp)) { RevivalCardContent(state.value, { joined = it }, {}) }
                }
            }
        }
        capture("invitation")
        compose.onNodeWithText("Join this event").performClick()
        assertEquals(event.id, joined)
        compose.runOnIdle { state.value = state.value.copy(event = event.copy(joined = event.starts), marks = 2) }
        compose.onNodeWithText("2 of 3 planned days", substring = true).assertExists()
        capture("progress")
        compose.runOnIdle { state.value = state.value.copy(event = event.copy(joined = event.starts, restored = event.ends-1, restoredDays = 42)) }
        compose.onNodeWithText("Welcome back.").assertDoesNotExist()
        capture("earned")
        compose.onNodeWithText("See my revival").performClick()
        compose.onNodeWithText("Welcome back.").assertExists()
        if (System.getProperty("fastingshift.screenshotDir") != null) {
            compose.onNodeWithTag("revival-achievement").captureRoboImage(moatsOutput("en").resolve("revival-celebration.png").absolutePath)
        }
    }
}
