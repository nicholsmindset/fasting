package com.fastingshift.app.billing

import com.fastingshift.domain.billing.PlanType
import java.time.Instant
import java.time.Period

/** Maps an active RevenueCat entitlement to a Shift+ plan. Pure, so it can be unit-tested. */
object PlanMapper {
    /**
     * @param expiresAt null for a non-expiring (lifetime) purchase.
     * @param periodIso the active product's subscription period (ISO 8601, e.g. "P1Y", "P1M"),
     *   if known.
     * @param productId the store product id; with [basePlanId], a fallback when the period
     *   isn't known yet.
     */
    fun plan(expiresAt: Instant?, periodIso: String?, productId: String, basePlanId: String?): PlanType {
        if (expiresAt == null) return PlanType.LIFETIME
        periodIso?.let { iso ->
            val months = runCatching { Period.parse(iso).toTotalMonths() }.getOrNull()
            if (months != null) return if (months >= 12) PlanType.ANNUAL else PlanType.MONTHLY
        }
        val ids = "$productId ${basePlanId.orEmpty()}".lowercase()
        return if ("annual" in ids || "year" in ids || "p1y" in ids) PlanType.ANNUAL else PlanType.MONTHLY
    }

    /**
     * Book codes are Play promo codes on a dedicated product. RevenueCat's PREPAID period type
     * means a prepaid base plan, not a book code, so only the product id decides.
     */
    fun isBookCode(productId: String, basePlanId: String?): Boolean =
        "book" in productId.lowercase() || "book" in basePlanId.orEmpty().lowercase()

    /** Keys a product's period is stored under: Play ids are "subscription:basePlan". */
    fun productKey(productId: String, basePlanId: String?): String =
        if (basePlanId.isNullOrEmpty() || productId.contains(':')) productId else "$productId:$basePlanId"
}
