package com.fastingshift.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.SwitchGroupRow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.settings.PlannerState
import com.fastingshift.app.ui.settings.SettingsOverview
import com.fastingshift.app.ui.settings.SettingsSection
import com.fastingshift.data.settings.AppAppearance
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.billing.Entitlement
import java.time.Instant
import java.time.ZoneOffset
import com.fastingshift.app.ui.settings.ForgotSheet
import com.fastingshift.app.ui.settings.MilestonesSheet
import com.fastingshift.app.ui.settings.WaterGoalDisplay
import com.fastingshift.app.ui.settings.WaterGoalSheet
import com.fastingshift.app.ui.theme.FastingShiftTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SettingsControlsTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    @Config(qualifiers = "w411dp-h822dp")
    fun loadingWeekDoesNotMovePrivacyRow() {
        val planner = mutableStateOf<PlannerState?>(null)
        var opened: SettingsSection? = null
        compose.setContent {
            FastingShiftTheme {
                SettingsOverview(AppAppearance.SYSTEM, planner.value, Entitlement.FREE, {}, {}, { opened = it })
            }
        }
        val privacy = compose.onNodeWithTag(SettingsSection.PRIVACY.overviewTag)
        privacy.performScrollTo().assertIsDisplayed()
        val before = privacy.getUnclippedBoundsInRoot()
        compose.runOnIdle {
            planner.value = PlannerState(emptyList(), AppSettings(), null, Instant.parse("2026-11-25T12:00:00Z"),
                ZoneOffset.UTC, null, false, null)
        }
        assertEquals(before, privacy.getUnclippedBoundsInRoot())
        privacy.performClick()
        compose.runOnIdle { assertEquals(SettingsSection.PRIVACY, opened) }
    }

    @Test fun compactLargeTextKeepsPickerAndSwitchAsSingleLabeledActions() {
        var pickerOpens = 0
        val enabled = mutableStateOf(false)
        val choice = "Follow system appearance"
        val picker = "Appearance"
        val setting = "Show a reminder before the next planned fast"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                FastingShiftTheme(darkTheme = true) {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        GroupRow(picker, value = choice, opensPicker = true, showChevron = true, onClick = { pickerOpens++ })
                        SwitchGroupRow(setting, enabled.value, { enabled.value = it })
                        SecondaryButton("Save changes", onClick = {}, enabled = false)
                    }
                }
            }
        }
        val choiceRow = compose.onNode(role(Role.Button) and hasText(picker) and hasText(choice))
        choiceRow.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, pickerOpens) }
        compose.onAllNodes(role(Role.Switch)).assertCountEquals(1)
        val toggle = compose.onNode(role(Role.Switch) and hasText(setting))
        toggle.performScrollTo().assertHeightIsAtLeast(48.dp).assertIsOff().performClick().assertIsOn()
        compose.onNode(role(Role.Button) and hasText("Save changes")).performScrollTo().assertIsNotEnabled()
    }

    @Test fun eachMilestoneCanBeChangedIndependently() {
        val chosen = mutableStateOf(setOf(12))
        compose.setContent {
            FastingShiftTheme { MilestonesSheet(chosen.value, { hour ->
                chosen.value = if (hour in chosen.value) chosen.value - hour else chosen.value + hour
            }, {}) }
        }
        compose.onAllNodes(role(Role.Checkbox)).assertCountEquals(8)
        val twelve = compose.onNode(hasText(app.getString(R.string.settings_hours_value, 12)) and role(Role.Checkbox))
        val fourteen = compose.onNode(hasText(app.getString(R.string.settings_hours_value, 14)) and role(Role.Checkbox))
        twelve.assertIsOn()
        fourteen.assertIsOff().performClick()
        twelve.assertIsOn()
        fourteen.assertIsOn()
    }

    @Test fun forgotToEndHasOneSelectedDelay() {
        val delay = mutableStateOf(0)
        compose.setContent { FastingShiftTheme { ForgotSheet(delay.value, { delay.value = it }, {}) } }
        val off = compose.onNode(hasText(app.getString(R.string.off)) and role(Role.RadioButton))
        val two = compose.onNode(hasText(app.getString(R.string.settings_after_hours, 2)) and role(Role.RadioButton))
        off.assertIsSelected()
        two.assertIsNotSelected().performClick()
        two.assertIsSelected()
        off.assertIsNotSelected()
    }

    @Test fun customWaterModePreservesTheEffectiveValueAndEnforcesStepperBounds() {
        val ml = mutableStateOf<Int?>(null)
        compose.setContent { FastingShiftTheme { WaterGoalSheet(WaterGoalDisplay.of(ml.value, null), { ml.value = it }, {}) } }
        val auto = compose.onNode(hasText(app.getString(R.string.settings_water_use_auto)) and role(Role.RadioButton))
        val custom = compose.onNode(hasText(app.getString(R.string.settings_water_custom)) and role(Role.RadioButton))
        val goal = app.getString(R.string.settings_water_goal)
        val minus = compose.onNodeWithContentDescription("${app.getString(R.string.a11y_decrease)}, $goal")
        val plus = compose.onNodeWithContentDescription("${app.getString(R.string.a11y_increase)}, $goal")
        auto.assertIsSelected()
        minus.assertDoesNotExist()
        custom.performClick().assertIsSelected()
        val initial = WaterGoalDisplay.of(null, null).ml
        compose.runOnIdle { assertEquals(initial, ml.value) }
        plus.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(initial + WaterGoalDisplay.STEP_ML, ml.value)
            ml.value = WaterGoalDisplay.MAX_ML
        }
        plus.assertIsNotEnabled()
        compose.runOnIdle { ml.value = WaterGoalDisplay.MIN_ML }
        minus.assertIsNotEnabled()
        auto.performClick().assertIsSelected()
        plus.assertDoesNotExist()
    }
}
