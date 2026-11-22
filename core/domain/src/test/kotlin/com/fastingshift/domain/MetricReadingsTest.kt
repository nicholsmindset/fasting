package com.fastingshift.domain

import com.fastingshift.domain.insights.MetricReadings
import com.fastingshift.domain.model.MetricLog
import com.fastingshift.domain.model.MetricType
import com.fastingshift.domain.model.MetricUnit
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetricReadingsTest {
    private val at = Instant.parse("2026-11-20T08:00:00Z")
    private fun glucose(v: Double, u: MetricUnit) = MetricLog(at = at, type = MetricType.GLUCOSE, value = v, unit = u)
    private fun ketone(v: Double) = MetricLog(at = at, type = MetricType.KETONE, value = v, unit = MetricUnit.MMOL_L)

    @Test fun `glucose ranges per unit`() {
        assertTrue(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MG_DL, 20.0))
        assertTrue(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MG_DL, 600.0))
        assertFalse(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MG_DL, 19.9))
        assertFalse(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MG_DL, 601.0))
        assertTrue(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MMOL_L, 1.1))
        assertTrue(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MMOL_L, 33.3))
        assertFalse(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MMOL_L, 1.0))
        assertFalse(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MMOL_L, 90.0)) // mg/dL typed in mmol/L
        assertFalse(MetricReadings.isValid(MetricType.GLUCOSE, MetricUnit.MMOL_L, Double.NaN))
    }

    @Test fun `ketones only in mmol per L up to 10`() {
        assertTrue(MetricReadings.isValid(MetricType.KETONE, MetricUnit.MMOL_L, 0.3))
        assertTrue(MetricReadings.isValid(MetricType.KETONE, MetricUnit.MMOL_L, 10.0))
        assertFalse(MetricReadings.isValid(MetricType.KETONE, MetricUnit.MMOL_L, 10.1))
        assertFalse(MetricReadings.isValid(MetricType.KETONE, MetricUnit.MMOL_L, 0.0))
        assertFalse(MetricReadings.isValid(MetricType.KETONE, MetricUnit.MG_DL, 1.0))
        assertNull(MetricReadings.range(MetricType.KETONE, MetricUnit.MG_DL))
    }

    @Test fun `parse accepts comma decimals and rejects junk`() {
        assertEquals(5.4, MetricReadings.parse("5,4", MetricType.GLUCOSE, MetricUnit.MMOL_L))
        assertEquals(98.0, MetricReadings.parse(" 98 ", MetricType.GLUCOSE, MetricUnit.MG_DL))
        assertNull(MetricReadings.parse("", MetricType.GLUCOSE, MetricUnit.MG_DL))
        assertNull(MetricReadings.parse("abc", MetricType.KETONE, MetricUnit.MMOL_L))
        assertNull(MetricReadings.parse("12", MetricType.KETONE, MetricUnit.MMOL_L))
    }

    @Test fun `low glucose is under 70 mg per dL or 3_9 mmol per L`() {
        assertTrue(MetricReadings.isLowGlucose(glucose(69.0, MetricUnit.MG_DL)))
        assertFalse(MetricReadings.isLowGlucose(glucose(70.0, MetricUnit.MG_DL)))
        assertTrue(MetricReadings.isLowGlucose(glucose(3.8, MetricUnit.MMOL_L)))
        assertFalse(MetricReadings.isLowGlucose(glucose(3.9, MetricUnit.MMOL_L)))
        assertFalse(MetricReadings.isLowGlucose(ketone(0.2)))
    }

    @Test fun `high ketones from 3 mmol per L`() {
        assertTrue(MetricReadings.isHighKetone(ketone(3.0)))
        assertFalse(MetricReadings.isHighKetone(ketone(2.9)))
        assertFalse(MetricReadings.isHighKetone(glucose(5.0, MetricUnit.MMOL_L)))
    }

    @Test fun `values convert to the display unit`() {
        assertEquals(90.0, MetricReadings.valueIn(glucose(90.0, MetricUnit.MG_DL), MetricUnit.MG_DL), 1e-9)
        assertEquals(5.0, MetricReadings.valueIn(glucose(90.08, MetricUnit.MG_DL), MetricUnit.MMOL_L), 1e-9)
        assertEquals(90.08, MetricReadings.valueIn(glucose(5.0, MetricUnit.MMOL_L), MetricUnit.MG_DL), 1e-9)
        assertEquals(1.2, MetricReadings.valueIn(ketone(1.2), MetricUnit.MG_DL), 1e-9) // ketones stay mmol/L
    }

    @Test fun `default glucose unit follows the latest reading then the country`() {
        assertEquals(MetricUnit.MG_DL, MetricReadings.defaultGlucoseUnit(null, "US"))
        assertEquals(MetricUnit.MMOL_L, MetricReadings.defaultGlucoseUnit(null, "MY"))
        assertEquals(MetricUnit.MMOL_L, MetricReadings.defaultGlucoseUnit(null, null))
        assertEquals(MetricUnit.MMOL_L, MetricReadings.defaultGlucoseUnit(glucose(5.0, MetricUnit.MMOL_L), "US"))
        assertEquals(MetricUnit.MG_DL, MetricReadings.defaultGlucoseUnit(glucose(90.0, MetricUnit.MG_DL), "SG"))
        assertEquals(MetricUnit.MMOL_L, MetricReadings.defaultGlucoseUnit(ketone(1.0), "SG"))
    }
}
