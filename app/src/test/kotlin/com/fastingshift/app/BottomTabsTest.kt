package com.fastingshift.app

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.BottomTabs
import com.fastingshift.app.ui.Routes
import com.fastingshift.app.ui.theme.FastingShiftTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rSG-w320dp-h720dp")
class BottomTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completeTabNamesAreReadableAndSelectableAtDoubleFontSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                var current by remember { mutableStateOf(Routes.TIMER) }
                FastingShiftTheme {
                    Box(Modifier.width(320.dp)) { BottomTabs(current) { current = it } }
                }
            }
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tabs = mapOf(Routes.TIMER to R.string.tab_timer, Routes.HISTORY to R.string.tab_history,
            Routes.LOG to R.string.tab_log, Routes.SETTINGS to R.string.tab_settings)
        for ((route, resource) in tabs) {
            val label = app.getString(resource)
            val tag = "tab_$route"
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .assert(hasText(label)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .assertIsDisplayed().performClick().assertIsSelected()
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
            assertEquals(1, results.single().lineCount, label)
            assertFalse(results.single().hasVisualOverflow, label)
        }
    }
}
