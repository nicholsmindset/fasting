package com.fastingshift.app.billing

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.fastingshift.app.BuildConfig
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitGetProducts
import com.revenuecat.purchases.models.StoreProduct
import com.fastingshift.app.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
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

class PlanOffer private constructor(val plan: PlanType, internal val pkg: Package) {
    val id: String get() = pkg.identifier
    val price: String get() = pkg.product.price.formatted
    val phases = pkg.product.defaultOption?.pricingPhases.orEmpty()
    val installments = pkg.product.defaultOption?.installmentsInfo

    companion object {
        /** An offering can be edited independently of its Play product and billing terms. */
        internal fun from(pkg: Package): PlanOffer? {
            val plan = when (pkg.product.type) {
                ProductType.INAPP -> PlanType.LIFETIME.takeIf { pkg.packageType == PackageType.LIFETIME }
                ProductType.SUBS -> when (pkg.packageType) {
                    PackageType.MONTHLY -> PlanType.MONTHLY
                    PackageType.ANNUAL -> PlanType.ANNUAL
                    else -> null
                }
                else -> null
            } ?: return null
            return PlanOffer(plan, pkg)
        }
    }
}

enum class PurchaseOutcome { ACTIVE, CANCELLED, PENDING, UNCONFIRMED, ALREADY_OWNED, FAILED, BUSY }
enum class RestoreOutcome { ACTIVE, EMPTY, FAILED, BUSY }

internal fun transactionOutcome(code: PurchasesErrorCode, userCancelled: Boolean): PurchaseOutcome = when {
    userCancelled || code == PurchasesErrorCode.PurchaseCancelledError -> PurchaseOutcome.CANCELLED
    code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.PENDING
    code == PurchasesErrorCode.OperationAlreadyInProgressError -> PurchaseOutcome.BUSY
    code == PurchasesErrorCode.ProductAlreadyPurchasedError -> PurchaseOutcome.ALREADY_OWNED
    else -> PurchaseOutcome.FAILED
}

interface PaywallStore {
    val entitlement: StateFlow<Entitlement>
    val loadedEntitlement: StateFlow<Entitlement?>
    val isConfigured: Boolean
    suspend fun refresh(): Boolean
    suspend fun offers(): List<PlanOffer>
    suspend fun purchase(activity: Activity, offer: PlanOffer): PurchaseOutcome
    suspend fun restore(): RestoreOutcome
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
) : PaywallStore {
    private val _entitlement = MutableStateFlow(Entitlement.FREE)
    override val entitlement: StateFlow<Entitlement> = _entitlement

    /**
     * The entitlement from real RevenueCat customer info, or null until the first info arrives.
     * [entitlement] starts at FREE before anything is known; work that must not act on that
     * placeholder (billing reminders) reads this instead.
     */
    private val _loadedEntitlement = MutableStateFlow<Entitlement?>(null)
    override val loadedEntitlement: StateFlow<Entitlement?> = _loadedEntitlement

    private val _managementUrl = MutableStateFlow<Uri?>(null)
    val managementUrl: StateFlow<Uri?> = _managementUrl

    override val isConfigured: Boolean get() = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    fun configure() {
        if (!isConfigured) return
        Purchases.configure(PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY).build())
        Purchases.sharedInstance.updatedCustomerInfoListener = object : com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener {
            override fun onReceived(customerInfo: CustomerInfo) = publish(customerInfo)
        }
    }

    override suspend fun refresh(): Boolean {
        if (!isConfigured) return false
        return try {
            publish(Purchases.sharedInstance.awaitCustomerInfo())
            true
        } catch (_: PurchasesException) {
            false
        }
    }

    /** A cached free result can predate a purchase on another device. Ads require a fresh result. */
    suspend fun confirmFreeForAds(): Boolean {
        if (!isConfigured) return false
        return try {
            val info = Purchases.sharedInstance.awaitCustomerInfo(CacheFetchPolicy.FETCH_CURRENT)
            publish(info)
            info.entitlements[ENTITLEMENT_ID]?.isActive != true && loadedEntitlement.value == Entitlement.FREE
        } catch (_: PurchasesException) {
            false
        }
    }

    override suspend fun offers(): List<PlanOffer> {
        if (!isConfigured) return emptyList()
        val current = Purchases.sharedInstance.awaitOfferings().current ?: return emptyList()
        rememberPeriods(current.availablePackages.map { it.product })
        return current.availablePackages.mapNotNull(PlanOffer::from).sortedBy { it.plan.ordinal }
    }

    // Both entry points share a guard, including restores launched from Settings.
    private val transaction = Mutex()

    override suspend fun purchase(activity: Activity, offer: PlanOffer): PurchaseOutcome {
        if (!isConfigured) return PurchaseOutcome.FAILED
        if (!transaction.tryLock()) return PurchaseOutcome.BUSY
        return try {
            val info = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, offer.pkg).build()).customerInfo
            publish(info)
            if (info.entitlements[ENTITLEMENT_ID]?.isActive == true) PurchaseOutcome.ACTIVE else PurchaseOutcome.UNCONFIRMED
        } catch (ex: PurchasesTransactionException) {
            transactionOutcome(ex.code, ex.userCancelled)
        } catch (ex: PurchasesException) {
            transactionOutcome(ex.code, false)
        } finally {
            transaction.unlock()
        }
    }

    override suspend fun restore(): RestoreOutcome {
        if (!isConfigured) return RestoreOutcome.FAILED
        if (!transaction.tryLock()) return RestoreOutcome.BUSY
        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            publish(info)
            if (info.entitlements[ENTITLEMENT_ID]?.isActive == true) RestoreOutcome.ACTIVE else RestoreOutcome.EMPTY
        } catch (_: PurchasesException) {
            RestoreOutcome.FAILED
        } finally {
            transaction.unlock()
        }
    }

    /** Subscription period (ISO 8601) by store product id, from offerings and product lookups. */
    private val periods = ConcurrentHashMap<String, String>()
    private val lookedUp = ConcurrentHashMap.newKeySet<String>()

    private fun rememberPeriods(products: List<StoreProduct>) {
        products.forEach { p -> p.period?.iso8601?.let { periods[p.id] = it } }
    }

    /** The plan comes from the product's period; fetch it once, then publish again. */
    private fun lookUpPeriod(productId: String) {
        if (!lookedUp.add(productId)) return
        scope.launch {
            runCatching { Purchases.sharedInstance.awaitGetProducts(listOf(productId), ProductType.SUBS) }
                .onSuccess {
                    rememberPeriods(it)
                    synchronized(this@BillingManager) { latestInfo?.let(::publish) }
                }.onFailure { lookedUp.remove(productId) }
        }
    }

    private var latestInfo: CustomerInfo? = null

    @Synchronized
    private fun publish(info: CustomerInfo) {
        // A slow refresh must not overwrite a purchase or restore that completed in the meantime.
        if (latestInfo?.requestDate?.after(info.requestDate) == true) return
        latestInfo = info
        _managementUrl.value = info.managementURL
        val e = info.entitlements[ENTITLEMENT_ID]
        _entitlement.value = if (e == null || !e.isActive) {
            Entitlement.FREE
        } else {
            val key = PlanMapper.productKey(e.productIdentifier, e.productPlanIdentifier)
            val period = periods[key]
            if (period == null && e.expirationDate != null) lookUpPeriod(e.productIdentifier)
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
