package com.fastingshift.app

import com.fastingshift.app.health.HealthWeight
import com.fastingshift.app.health.WeightImport
import com.fastingshift.domain.model.WeightLog
import com.fastingshift.domain.model.WeightUnit
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

class WeightImportTest {
    private val t = Instant.parse("2026-11-20T07:00:00Z")
    private val own = "com.fastingshift.app"

    @Test fun skipsOwnRecords() {
        val r = listOf(HealthWeight(t, 70.0, own), HealthWeight(t.plusSeconds(60), 71.0, "com.scale"))
        assertEquals(listOf(r[1]), WeightImport.select(r, emptyList(), own))
    }

    @Test fun skipsSameTimeAndValueButKeepsDifferentValueAtSameTime() {
        val existing = listOf(WeightLog(at = t, value = 70.0, unit = WeightUnit.KG))
        val r = listOf(HealthWeight(t, 70.001, "com.scale"), HealthWeight(t, 72.5, "com.other"))
        assertEquals(listOf(r[1]), WeightImport.select(r, existing, own))
    }

    @Test fun matchesPoundsLogsByValueInKg() {
        val existing = listOf(WeightLog(at = t, value = 154.0, unit = WeightUnit.LB))
        val kg = 154.0 * 0.45359237
        assertEquals(emptyList(), WeightImport.select(listOf(HealthWeight(t, kg, "com.scale")), existing, own))
    }

    @Test fun deDuplicatesWithinTheBatch() {
        val r = listOf(HealthWeight(t, 70.0, "com.a"), HealthWeight(t, 70.0, "com.b"))
        assertEquals(1, WeightImport.select(r, emptyList(), own).size)
    }
}
