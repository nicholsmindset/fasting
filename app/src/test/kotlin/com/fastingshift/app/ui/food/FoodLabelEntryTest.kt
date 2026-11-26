package com.fastingshift.app.ui.food

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.ui.theme.FastingShiftTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class FoodLabelEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lookupRequiresConfirmationAndReturnsCachedLabelAfterAnUnknownProduct() {
        val requests = CopyOnWriteArrayList<String>()
        val soy = "8888200619046"
        val water = "8888200717216"
        val vm = FoodLookupViewModel { code ->
            requests.add(code)
            if (code == soy) LookupResult.Found(FoodLabel("Soy drink", null, LabelAmounts(null, null),
                LabelAmounts(null, LabelValue(4.2, "")), false))
            else LookupResult.Missing
        }
        compose.setContent { FastingShiftTheme { FoodLabelEntry(vm) } }
        compose.onNodeWithText("Food label").performClick()
        compose.onNodeWithText("Look up").assertIsNotEnabled()
        val field = compose.onNode(hasSetTextAction())
        field.performScrollTo().performTextInput(soy)
        compose.runOnIdle { assertEquals(emptyList<String>(), requests.toList()) }
        compose.onNodeWithText("Look up").performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Soy drink").assertExists()
        compose.onNodeWithText("4.2 g").assertExists()
        compose.onNodeWithText("Not listed").assertExists()
        field.performScrollTo().performTextReplacement(water)
        compose.onNodeWithText("Look up").performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Open Food Facts has no entry", substring = true).assertExists()
        field.performScrollTo().performTextReplacement(soy)
        compose.onNodeWithText("Look up").performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Soy drink").assertExists()
        assertEquals(listOf(soy, water), requests.toList())
    }
}
