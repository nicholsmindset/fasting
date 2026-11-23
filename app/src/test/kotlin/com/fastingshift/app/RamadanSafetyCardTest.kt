package com.fastingshift.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.safety.RamadanSafety
import com.fastingshift.app.ui.safety.RamadanSafetyContent
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import com.fastingshift.domain.safety.ScreeningAnswers
import com.fastingshift.domain.service.ScreeningRecord
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Ramadan safety card shows for glucose-lowering medication or type 1 diabetes in Ramadan mode. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class RamadanSafetyCardTest {
    @get:Rule val compose = createComposeRule()

    private fun record(vararg meds: Medication, gate: GateResult = GateResult.SOFT) =
        ScreeningRecord(Instant.EPOCH, ScreeningAnswers(45, false, false, false, meds.toSet(), 170.0, 75.0), gate, true)

    @Test fun visibility() {
        assertTrue(RamadanSafety.shouldShow(true, record(Medication.INSULIN)))
        assertTrue(RamadanSafety.shouldShow(true, record(Medication.SULFONYLUREA, Medication.GLP1)))
        assertFalse(RamadanSafety.shouldShow(false, record(Medication.INSULIN)))
        assertFalse(RamadanSafety.shouldShow(true, record(Medication.GLP1)))
        assertFalse(RamadanSafety.shouldShow(true, record()))
        assertFalse(RamadanSafety.shouldShow(true, null))
        // Highest risk: blocked from fasting in the app, but they may still fast Ramadan.
        assertTrue(RamadanSafety.shouldShow(true, record(Medication.INSULIN, gate = GateResult.BLOCK)))
        val type1 = ScreeningRecord(Instant.EPOCH, ScreeningAnswers(30, false, false, true, emptySet(), 170.0, 70.0), GateResult.BLOCK, false)
        assertTrue(RamadanSafety.shouldShow(true, type1))
    }

    @Config(qualifiers = "en-rSG")
    @Test fun contentNamesTheSymptomsAndAnEmergencyNumber() {
        compose.setContent { FastingShiftTheme(darkTheme = false) { RamadanSafetyContent() } }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.onNodeWithText(ctx.getString(R.string.ramadan_safety_break_body)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.ramadan_safety_clinician)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.ramadan_safety_emergency, "995")).assertExists()
    }

    @Config(qualifiers = "en-rBR")
    @Test fun unsupportedCountryShowsLocalEmergencyGuidance() {
        compose.setContent { FastingShiftTheme(darkTheme = false) { RamadanSafetyContent() } }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.onNodeWithText(ctx.getString(R.string.stop_local_emergency)).assertExists()
    }
}
