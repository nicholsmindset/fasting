package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.app.ui.trust.TrustScreen
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Trust center: the promises are shown, export is one tap, and delete always asks first. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class TrustScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun s(id: Int) = ApplicationProvider.getApplicationContext<android.app.Application>().getString(id)

    private var exports = 0
    private var deletes = 0

    private fun show() = compose.setContent {
        FastingShiftTheme(darkTheme = false) {
            TrustScreen(onBack = {}, onExport = { exports++ }, onDeleteConfirmed = { deletes++ })
        }
    }

    @Test fun showsWhatStaysAndWhatLeaves() {
        show()
        listOf(
            R.string.trust_on_device_title,
            R.string.trust_encrypted_title,
            R.string.trust_no_account_title,
            R.string.trust_no_tracking_title,
            R.string.trust_leaves_purchases_title,
            R.string.trust_leaves_hc_title,
            R.string.trust_leaves_files_title,
        ).forEach { compose.onNodeWithText(s(it)).performScrollTo().assertExists() }
    }

    @Test fun exportIsOneTap() {
        show()
        compose.onNodeWithText(s(R.string.trust_export)).performScrollTo().performClick()
        assertEquals(1, exports)
    }

    @Test fun deleteAsksForConfirmationAndCanBeCancelled() {
        show()
        compose.onNodeWithTag("trust_delete").performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.trust_delete_confirm_title)).assertExists()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        compose.onNodeWithText(s(R.string.trust_delete_confirm_title)).assertDoesNotExist()
        assertEquals(0, deletes)

        compose.onNodeWithTag("trust_delete").performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.trust_delete_confirm)).performClick()
        assertEquals(1, deletes)
    }
}
