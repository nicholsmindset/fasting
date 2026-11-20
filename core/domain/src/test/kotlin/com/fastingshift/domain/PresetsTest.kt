package com.fastingshift.domain

import com.fastingshift.domain.protocol.Presets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PresetsTest {
    @Test fun `ids are unique and resolvable`() {
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        Presets.all.forEach { assertEquals(it, Presets.byId(it.id)) }
    }

    @Test fun `windows add up to a day`() {
        Presets.all.filter { it.eatHours != null }.forEach { assertEquals(24, it.fastHours + it.eatHours!!) }
    }

    @Test fun `custom range`() {
        assertEquals(120, Presets.custom(120).fastHours)
        assertEquals(30, Presets.byId("custom_30")!!.fastHours)
        assertNull(Presets.byId("custom_500"))
        assertFailsWith<IllegalArgumentException> { Presets.custom(0) }
    }
}
