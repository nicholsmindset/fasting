package com.fastingshift.app.ui.plus

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.fastingshift.app.R
import com.fastingshift.app.billing.PlanOffer
import com.fastingshift.domain.billing.PlanType
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.RecurrenceMode
import java.util.Locale

/** Display the same default Play option that PurchaseParams selects for the package. */
internal fun offerTerms(offer: PlanOffer, locale: Locale, resources: Resources): List<String> = buildList {
    if (offer.plan == PlanType.LIFETIME) {
        add(resources.getString(R.string.shift_price_once, offer.price))
        return@buildList
    }
    if (offer.phases.isEmpty()) add(resources.getString(R.string.shift_price_confirm, offer.price))
    offer.phases.forEach { add(phaseTerms(it, locale, resources)) }
    offer.installments?.let { installments ->
        add(resources.getString(R.string.shift_commitment, installments.commitmentPaymentsCount))
        if (installments.renewalCommitmentPaymentsCount > 0) {
            add(resources.getString(R.string.shift_renewal_commitment, installments.renewalCommitmentPaymentsCount))
        }
    }
}

internal fun phaseTerms(phase: PricingPhase, locale: Locale, resources: Resources): String {
    val billingPeriod = phase.billingPeriod
    val measureUnit = when (billingPeriod.unit) {
        Period.Unit.DAY -> MeasureUnit.DAY
        Period.Unit.WEEK -> MeasureUnit.WEEK
        Period.Unit.MONTH -> MeasureUnit.MONTH
        Period.Unit.YEAR -> MeasureUnit.YEAR
        Period.Unit.UNKNOWN -> return resources.getString(R.string.shift_price_confirm, phase.price.formatted)
    }
    val formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
    fun duration(cycles: Int) = formatter.formatMeasures(Measure(billingPeriod.value.toLong() * cycles, measureUnit))
    val cycles = (phase.billingCycleCount ?: 1).coerceAtLeast(1)
    val price = phase.price.formatted
    return when {
        phase.price.amountMicros == 0L -> resources.getString(R.string.program_trial_duration, duration(cycles))
        phase.recurrenceMode == RecurrenceMode.FINITE_RECURRING && cycles > 1 ->
            resources.getString(R.string.shift_intro_recurring, price, duration(1), duration(cycles))
        phase.recurrenceMode == RecurrenceMode.FINITE_RECURRING ->
            resources.getString(R.string.shift_price_period, price, duration(1))
        phase.recurrenceMode == RecurrenceMode.INFINITE_RECURRING ->
            resources.getString(R.string.shift_recurring, price, duration(1))
        phase.recurrenceMode == RecurrenceMode.NON_RECURRING ->
            resources.getString(R.string.shift_prepaid, price, duration(1))
        else -> resources.getString(R.string.shift_price_confirm, price)
    }
}
