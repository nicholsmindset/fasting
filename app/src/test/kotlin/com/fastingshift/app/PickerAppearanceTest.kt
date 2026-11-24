package com.fastingshift.app

import android.app.Application
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Looper
import android.util.TypedValue
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.fast.pickDateTime
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.app.ui.theme.pickerDialogTheme
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PickerAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "notnight")
    fun darkPickersOnLightDevice() = checkPickers(dark = true)

    @Test @Config(qualifiers = "night")
    fun lightPickersOnDarkDevice() = checkPickers(dark = false)

    private fun checkPickers(dark: Boolean) {
        var expectedAccent = 0
        compose.setContent {
            FastingShiftTheme(darkTheme = dark) {
                val context = LocalContext.current
                val theme = pickerDialogTheme()
                val accent = MaterialTheme.colorScheme.primary.toArgb()
                Button(onClick = {
                    expectedAccent = accent
                    pickDateTime(context, theme, Instant.parse("2026-11-20T11:00:00Z")) {}
                }) { Text("Choose time") }
            }
        }
        compose.onNodeWithText("Choose time").performClick()
        compose.runOnIdle {
            val datePicker = ShadowDialog.getLatestDialog() as DatePickerDialog
            assertDialogAccent(datePicker, expectedAccent)
            datePicker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        shadowOf(Looper.getMainLooper()).idle()
        compose.runOnIdle {
            val timePicker = ShadowDialog.getLatestDialog() as TimePickerDialog
            assertDialogAccent(timePicker, expectedAccent)
            timePicker.dismiss()
        }
    }

    private fun assertDialogAccent(dialog: Dialog, expected: Int) {
        val value = TypedValue()
        assertTrue(dialog.context.theme.resolveAttribute(android.R.attr.colorAccent, value, true))
        assertEquals(expected, value.data)
    }
}
