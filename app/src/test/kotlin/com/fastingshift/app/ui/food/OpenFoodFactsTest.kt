package com.fastingshift.app.ui.food

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class OpenFoodFactsTest {
    @Test fun validatesAllFourGtinLengthsAndRejectsBadChecksumsAndNonDigits() {
        listOf("96385074", "036000291452", "8888200619046", "00012345600012").forEach { assertTrue(validGtin(it), it) }
        listOf("00000000", "036000291453", "888820061904X", "https://example.org", "123").forEach { assertFalse(validGtin(it), it) }
    }

    @Test fun keepsServingValuesAndHundredValuesSeparateIncludingQualifiedAmountsAndZero() {
        val label = (parseFoodLabel("""
            {"status":1,"product":{"product_name":"Soy drink","serving_size":"250 ml","nutrition_data_per":"100ml",
            "nutriments":{"energy-kcal_serving":165,"sugars_serving":10.5,"energy-kcal_100g":66,"sugars_100g":0,"sugars_modifier":"<"}}}
        """) as LookupResult.Found).label
        assertEquals("250 ml", label.serving)
        assertEquals(165.0, label.perServing.energy?.amount)
        assertEquals(10.5, label.perServing.sugars?.amount)
        assertEquals(66.0, label.perHundred.energy?.amount)
        assertEquals(LabelValue(0.0, "<"), label.perHundred.sugars)
        assertTrue(label.explicitlyPer100ml)
    }

    @Test fun absentAndInvalidValuesAreUnknownAndAnAbsentServingIsNeverCalculated() {
        val label = (parseFoodLabel("""
            {"status":1,"product":{"serving_size":null,"nutriments":{"sugars_100g":7,"energy-kcal_100g":"unknown","sugars_serving":-1}}}
        """) as LookupResult.Found).label
        assertNull(label.serving)
        assertNull(label.name)
        assertNull(label.perHundred.energy)
        assertFalse(label.perServing.available)
        assertFalse(label.explicitlyPer100ml)
        assertEquals(7.0, label.perHundred.sugars?.amount)
        assertEquals(LookupResult.Missing, parseFoodLabel("""{"status":0}"""))
        assertEquals(LookupResult.Unavailable, parseFoodLabel("""{"status":1}"""))
    }

    @Test fun rateLimitsEvenDifferentBarcodesAndRespectsServerCooldown() {
        var now = 1L
        val pacer = LookupPacer { now }
        assertTrue(pacer.take())
        now += 4_099
        assertFalse(pacer.take())
        now += 1
        assertTrue(pacer.take())
        pacer.coolDown(120)
        now += 119_999
        assertFalse(pacer.take())
        now += 1
        assertTrue(pacer.take())
    }
}
