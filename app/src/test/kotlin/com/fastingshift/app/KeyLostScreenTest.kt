package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.recovery.KeyLostScreen
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class KeyLostScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun s(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test fun startFreshNeedsConfirmation() {
        var resets = 0
        compose.setContent { KeyLostScreen(resetting = false, onStartFresh = { resets++ }) }
        compose.onNodeWithText(s(R.string.recovery_backup_note)).assertExists()
        compose.onNodeWithText(s(R.string.recovery_start_fresh)).performClick()
        assertEquals(0, resets)
        compose.onNodeWithText(s(R.string.recovery_confirm_title)).assertExists()
        compose.onAllNodesWithText(s(R.string.recovery_start_fresh))[1].performClick()
        assertEquals(1, resets)
    }
}
