package com.fastingshift.app

import com.fastingshift.app.billing.PlanMapper
import com.fastingshift.domain.billing.PlanType
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PlanMapperTest {
    private val exp = Instant.parse("2027-01-01T00:00:00Z")

    @Test fun noExpiryIsLifetime() {
        assertEquals(PlanType.LIFETIME, PlanMapper.plan(null, "P1Y", "shift_plus", "annual"))
    }

    @Test fun periodDecidesOverIds() {
        assertEquals(PlanType.ANNUAL, PlanMapper.plan(exp, "P1Y", "shift_plus", "base"))
        assertEquals(PlanType.ANNUAL, PlanMapper.plan(exp, "P12M", "shift_plus", null))
        assertEquals(PlanType.MONTHLY, PlanMapper.plan(exp, "P1M", "shift_plus_annual_promo", null))
    }

    @Test fun idFallbackWhenPeriodUnknown() {
        assertEquals(PlanType.ANNUAL, PlanMapper.plan(exp, null, "shift_plus", "yearly"))
        assertEquals(PlanType.ANNUAL, PlanMapper.plan(exp, "garbage", "shift_plus_annual", null))
        assertEquals(PlanType.MONTHLY, PlanMapper.plan(exp, null, "shift_plus", "monthly"))
    }

    @Test fun bookCodeOnlyFromProduct() {
        assertTrue(PlanMapper.isBookCode("shift_plus_book", null))
        assertFalse(PlanMapper.isBookCode("shift_plus", "prepaid-3m"))
    }

    @Test fun productKeys() {
        assertEquals("sub:annual", PlanMapper.productKey("sub", "annual"))
        assertEquals("sub:annual", PlanMapper.productKey("sub:annual", "annual"))
        assertEquals("lifetime", PlanMapper.productKey("lifetime", null))
    }
}
