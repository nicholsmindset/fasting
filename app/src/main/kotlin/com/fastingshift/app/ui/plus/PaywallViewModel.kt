package com.fastingshift.app.ui.plus

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.billing.PaywallStore
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.billing.PlanOffer
import com.fastingshift.app.billing.PurchaseOutcome
import com.fastingshift.app.billing.RestoreOutcome
import com.revenuecat.purchases.PurchasesException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

internal sealed interface Storefront {
    data object Disabled : Storefront
    data object Loading : Storefront
    data object Empty : Storefront
    data object Failed : Storefront
    data class Ready(val offers: List<PlanOffer>) : Storefront
}

internal enum class StoreAction { PURCHASE, RESTORE }

@HiltViewModel
class PaywallViewModel internal constructor(private val billing: PaywallStore) : ViewModel() {
    @Inject constructor(billing: BillingManager) : this(billing as PaywallStore)

    val entitlement = billing.entitlement
    val configured get() = billing.isConfigured
    internal var storefront by mutableStateOf<Storefront>(Storefront.Disabled)
        private set
    internal var action by mutableStateOf<StoreAction?>(null)
        private set
    @get:StringRes var message by mutableStateOf<Int?>(null)
        private set
    var pendingPayment by mutableStateOf(false)
        private set

    init { reload() }

    fun reload() {
        if (!configured || storefront == Storefront.Loading || action != null) return
        storefront = Storefront.Loading
        viewModelScope.launch {
            storefront = try {
                // An active subscriber must not be offered the same subscription while we are
                // still waiting for their first entitlement check.
                if (!billing.refresh() || billing.loadedEntitlement.value == null) {
                    Storefront.Failed
                } else {
                    val offers = billing.offers()
                    if (offers.isEmpty()) Storefront.Empty else Storefront.Ready(offers)
                }
            } catch (_: PurchasesException) {
                Storefront.Failed
            }
        }
    }

    fun purchase(activity: Activity, offer: PlanOffer) {
        if (action != null || pendingPayment || billing.entitlement.value.isShiftPlus) return
        val current = storefront as? Storefront.Ready ?: return
        if (offer !in current.offers) return
        action = StoreAction.PURCHASE
        message = null
        viewModelScope.launch {
            try {
                val result = billing.purchase(activity, offer)
                pendingPayment = result in setOf(PurchaseOutcome.PENDING, PurchaseOutcome.UNCONFIRMED, PurchaseOutcome.ALREADY_OWNED)
                message = when (result) {
                    PurchaseOutcome.ACTIVE -> R.string.shift_purchase_active
                    PurchaseOutcome.CANCELLED -> null
                    PurchaseOutcome.PENDING -> R.string.shift_purchase_pending
                    PurchaseOutcome.UNCONFIRMED -> R.string.shift_purchase_unconfirmed
                    PurchaseOutcome.ALREADY_OWNED -> R.string.shift_already_owned
                    PurchaseOutcome.FAILED -> R.string.shift_purchase_failed
                    PurchaseOutcome.BUSY -> R.string.shift_transaction_busy
                }
            } finally {
                action = null
            }
        }
    }

    fun restore() {
        if (!configured || action != null) return
        action = StoreAction.RESTORE
        message = null
        viewModelScope.launch {
            try {
                message = when (billing.restore()) {
                    RestoreOutcome.ACTIVE -> R.string.settings_purchases_restored
                    RestoreOutcome.EMPTY -> R.string.shift_restore_empty
                    RestoreOutcome.FAILED -> R.string.shift_restore_failed
                    RestoreOutcome.BUSY -> R.string.shift_transaction_busy
                }
            } finally {
                action = null
            }
        }
    }
}
