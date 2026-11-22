package com.fastingshift.app.billing

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.fastingshift.app.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitGetProducts
import com.revenuecat.purchases.models.StoreProduct
import com.fastingshift.app.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlanOffer(val plan: PlanType, val price: String, internal val pkg: Package) {
    private val freePhase get() = pkg.product.defaultOption?.freePhase
    val hasTrial get() = freePhase != null
    val trialPeriod: java.time.Period? get() = freePhase?.let { phase ->
        runCatching {
            java.time.Period.parse(phase.billingPeriod.iso8601)
                .multipliedBy((phase.billingCycleCount ?: 1).coerceAtLeast(1))
        }.getOrNull()
    }
}

/**
 * Shift+ billing through RevenueCat over Play Billing (PRD F10). No account, no backend: the
 * anonymous RevenueCat app user id is enough. If no API key is configured, billing is off and
 * the app behaves as free with every free feature intact.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _entitlement = MutableStateFlow(Entitlement.FREE)
    val entitlement: StateFlow<Entitlement> = _entitlement

    /**
     * The entitlement from real RevenueCat customer info, or null until the first info arrives.
     * [entitlement] starts at FREE before anything is known; work that must not act on that
     * placeholder (billing reminders) reads this instead.
     */
    private val _loadedEntitlement = MutableStateFlow<Entitlement?>(null)
    val loadedEntitlement: StateFlow<Entitlement?> = _loadedEntitlement

    private val _managementUrl = MutableStateFlow<Uri?>(null)
    val managementUrl: StateFlow<Uri?> = _managementUrl

    val isConfigured: Boolean get() = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    fun configure() {
        if (!isConfigured) return
        Purchases.configure(PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY).build())
        Purchases.sharedInstance.updatedCustomerInfoListener = object : com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener {
            override fun onReceived(customerInfo: CustomerInfo) = publish(customerInfo)
        }
    }

    suspend fun refresh() {
        if (!isConfigured) return
        runCatching { Purchases.sharedInstance.awaitCustomerInfo() }.onSuccess(::publish)
    }

    suspend fun offers(): List<PlanOffer> {
        if (!isConfigured) return emptyList()
        val current = runCatching { Purchases.sharedInstance.awaitOfferings().current }.getOrNull() ?: return emptyList()
        rememberPeriods(current.availablePackages.map { it.product })
        return current.availablePackages.mapNotNull { pkg ->
            val plan = when (pkg.packageType) {
                PackageType.ANNUAL -> PlanType.ANNUAL
                PackageType.MONTHLY -> PlanType.MONTHLY
                PackageType.LIFETIME -> PlanType.LIFETIME
                else -> return@mapNotNull null
            }
            PlanOffer(plan, pkg.product.price.formatted, pkg)
        }.sortedBy { it.plan.ordinal }
    }

    /** Returns null on success, or a user-facing reason. A user cancelling is not an error. */
    suspend fun purchase(activity: Activity, offer: PlanOffer): String? = try {
        val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, offer.pkg).build())
        publish(result.customerInfo)
        null
    } catch (e: com.revenuecat.purchases.PurchasesTransactionException) {
        if (e.userCancelled) null else e.message
    } catch (e: com.revenuecat.purchases.PurchasesException) {
        e.message
    }

    suspend fun restore(): String? = try {
        publish(Purchases.sharedInstance.awaitRestore())
        null
    } catch (e: com.revenuecat.purchases.PurchasesException) {
        e.message
    }

    /** Subscription period (ISO 8601) by store product id, from offerings and product lookups. */
    private val periods = ConcurrentHashMap<String, String>()
    private val lookedUp = ConcurrentHashMap.newKeySet<String>()

    private fun rememberPeriods(products: List<StoreProduct>) {
        products.forEach { p -> p.period?.iso8601?.let { periods[p.id] = it } }
    }

    /** The plan comes from the product's period; fetch it once, then publish again. */
    private fun lookUpPeriod(productId: String, info: CustomerInfo) {
        if (!lookedUp.add(productId)) return
        scope.launch {
            runCatching { Purchases.sharedInstance.awaitGetProducts(listOf(productId), ProductType.SUBS) }
                .onSuccess {
                    rememberPeriods(it)
                    publish(info)
                }
        }
    }

    private fun publish(info: CustomerInfo) {
        _managementUrl.value = info.managementURL
        val e = info.entitlements[ENTITLEMENT_ID]
        _entitlement.value = if (e == null || !e.isActive) {
            Entitlement.FREE
        } else {
            val key = PlanMapper.productKey(e.productIdentifier, e.productPlanIdentifier)
            val period = periods[key]
            if (period == null && e.expirationDate != null) lookUpPeriod(e.productIdentifier, info)
            val plan = PlanMapper.plan(e.expirationDate?.toInstant(), period, e.productIdentifier, e.productPlanIdentifier)
            Entitlement(
                plan = plan,
                active = true,
                inTrial = e.periodType == PeriodType.TRIAL,
                expiresAt = e.expirationDate?.toInstant(),
                willRenew = e.willRenew,
                fromBookCode = PlanMapper.isBookCode(e.productIdentifier, e.productPlanIdentifier),
            )
        }
        _loadedEntitlement.value = _entitlement.value
    }

    companion object {
        /** RevenueCat entitlement identifier. Must match the RevenueCat dashboard. */
        const val ENTITLEMENT_ID = "shift_plus"

        /** Play subscriptions page: one-tap cancel (paywall rule 4). */
        fun playSubscriptionsUri(packageName: String): Uri =
            Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")

        /**
         * Book codes are Play promo/offer codes (3 months free), set up in Play Console. Redeeming
         * goes through Play, so the app needs no backend.
         */
        fun redeemUri(code: String): Uri = Uri.parse("https://play.google.com/redeem?code=" + Uri.encode(code.trim()))
    }
}
