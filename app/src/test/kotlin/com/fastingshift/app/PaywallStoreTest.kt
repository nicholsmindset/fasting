package com.fastingshift.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastingshift.app.billing.PaywallStore
import com.fastingshift.app.billing.PlanOffer
import com.fastingshift.app.billing.PurchaseOutcome
import com.fastingshift.app.billing.RestoreOutcome
import com.fastingshift.app.billing.matchesBillingInterval
import com.fastingshift.app.billing.hasCompleteSchedule
import com.fastingshift.app.billing.transactionOutcome
import com.fastingshift.app.ui.plus.PaywallViewModel
import com.fastingshift.app.ui.plus.Storefront
import com.fastingshift.app.ui.plus.firstPaymentAfterTrial
import com.fastingshift.app.ui.plus.offerTerms
import com.fastingshift.app.ui.plus.phaseTerms
import com.fastingshift.domain.billing.Entitlement
import com.fastingshift.domain.billing.PlanType
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PresentedOfferingContext
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.RecurrenceMode
import com.revenuecat.purchases.models.TestStoreProduct
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
class PaywallStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val res get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Before fun useDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun resetDispatcher() = Dispatchers.resetMain()

    private class FakeStore(override val isConfigured: Boolean = true) : PaywallStore {
        override val entitlement = MutableStateFlow(Entitlement.FREE)
        override val loadedEntitlement = MutableStateFlow<Entitlement?>(null)
        var canRefresh = true
        var refreshPolicy: CacheFetchPolicy? = null
        var remoteEntitlement: Entitlement? = null
        var offers = emptyList<PlanOffer>()
        var offerRequests = 0
        var purchaseRequests = 0
        var restoreRequests = 0
        var purchaseResult = CompletableDeferred<PurchaseOutcome>()
        var restoreResult = RestoreOutcome.EMPTY

        override suspend fun refresh(fetchPolicy: CacheFetchPolicy): Boolean {
            refreshPolicy = fetchPolicy
            if (canRefresh) {
                if (fetchPolicy == CacheFetchPolicy.FETCH_CURRENT) remoteEntitlement?.let { entitlement.value = it }
                loadedEntitlement.value = entitlement.value
            }
            return canRefresh
        }
        override suspend fun offers(): List<PlanOffer> { offerRequests++; return offers }
        override suspend fun purchase(activity: Activity, offer: PlanOffer): PurchaseOutcome {
            purchaseRequests++
            return purchaseResult.await()
        }
        override suspend fun restore(): RestoreOutcome { restoreRequests++; return restoreResult }
    }

    private fun phase(period: String, formatted: String, micros: Long, mode: RecurrenceMode, cycles: Int? = null) =
        PricingPhase(Period.create(period), mode, cycles, Price(formatted, micros, "SGD"))

    private fun annual(): PlanOffer {
        val product = TestStoreProduct(
            id = "fixture", name = "Annual", title = "Annual", description = "",
            price = Price("S$50.00", 50_000_000, "SGD"), period = Period.create("P1Y"),
            freeTrialPricingPhase = phase("P1W", "Free", 0, RecurrenceMode.FINITE_RECURRING, 1),
            introPricePricingPhase = phase("P1M", "S$2.00", 2_000_000, RecurrenceMode.FINITE_RECURRING, 3),
        )
        return requireNotNull(PlanOffer.from(Package("annual", PackageType.ANNUAL, product, PresentedOfferingContext("test"))))
    }

    @Test fun noKeyNeverContactsStoreAndMissingEntitlementCanBeRetried() = runTest(dispatcher) {
        val disabled = FakeStore(false)
        val offline = PaywallViewModel(disabled)
        offline.reload()
        offline.restore()
        runCurrent()
        assertEquals(Storefront.Disabled, offline.storefront)
        assertEquals(0, disabled.offerRequests + disabled.restoreRequests)

        val store = FakeStore().apply { canRefresh = false }
        val vm = PaywallViewModel(store)
        runCurrent()
        assertEquals(Storefront.Failed, vm.storefront)
        assertEquals(0, store.offerRequests)
        store.canRefresh = true
        vm.reload()
        runCurrent()
        assertEquals(Storefront.Empty, vm.storefront)
        store.offers = listOf(annual())
        vm.reload()
        runCurrent()
        assertTrue(vm.storefront is Storefront.Ready)
        store.canRefresh = false
        vm.reload()
        runCurrent()
        assertEquals(Storefront.Failed, vm.storefront)
        vm.purchase(Activity(), store.offers.single())
        runCurrent()
        assertEquals(0, store.purchaseRequests)
    }

    @Test fun paywallRequiresCurrentEligibilityEvenWithACachedFreeEntitlement() = runTest(dispatcher) {
        val offer = annual()
        val store = FakeStore().apply {
            loadedEntitlement.value = Entitlement.FREE
            remoteEntitlement = Entitlement(plan = PlanType.ANNUAL, active = true)
            offers = listOf(offer)
        }
        val vm = PaywallViewModel(store)
        runCurrent()
        assertEquals(CacheFetchPolicy.FETCH_CURRENT, store.refreshPolicy)
        assertTrue(vm.entitlement.value.isShiftPlus)
        assertEquals(0, store.offerRequests)
        vm.purchase(Activity(), offer)
        runCurrent()
        assertEquals(0, store.purchaseRequests)
    }

    @Test fun oneTransactionAtATimeAndCancellationIsQuiet() = runTest(dispatcher) {
        val offer = annual()
        val store = FakeStore().apply { offers = listOf(offer) }
        val vm = PaywallViewModel(store)
        runCurrent()
        val activity = Activity()
        vm.purchase(activity, offer)
        vm.purchase(activity, offer)
        vm.restore()
        runCurrent()
        assertEquals(1, store.purchaseRequests)
        assertEquals(0, store.restoreRequests)
        store.purchaseResult.complete(PurchaseOutcome.CANCELLED)
        runCurrent()
        assertNull(vm.message)
        assertNull(vm.action)
        assertFalse(vm.pendingPayment)
        store.purchaseResult = CompletableDeferred(PurchaseOutcome.PENDING)
        vm.purchase(activity, offer)
        runCurrent()
        assertTrue(vm.pendingPayment)
        assertEquals(R.string.shift_purchase_pending, vm.message)
        vm.purchase(activity, offer)
        runCurrent()
        assertEquals(2, store.purchaseRequests)
    }

    @Test fun emptyFailedAndActiveRestoresHaveDistinctFeedback() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = PaywallViewModel(store)
        runCurrent()
        for ((result, expected) in listOf(
            RestoreOutcome.EMPTY to R.string.shift_restore_empty,
            RestoreOutcome.FAILED to R.string.shift_restore_failed,
            RestoreOutcome.ACTIVE to R.string.settings_purchases_restored,
        )) {
            store.restoreResult = result
            vm.restore()
            runCurrent()
            assertEquals(expected, vm.message)
        }
    }

    @Test fun playOffersKeepTrialIntroRenewalAndNonRenewingTermsDistinct() {
        val offer = annual()
        assertEquals("S$2.00", firstPaymentAfterTrial(offer))
        val lines = offerTerms(offer, Locale.US, res)
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("1 week"))
        assertTrue(lines[1].contains("S$2.00") && lines[1].contains("3 months"))
        assertTrue(lines[2].contains("S$50.00") && lines[2].contains("1 year") && lines[2].contains("Renews"))
        val prepaid = phaseTerms(phase("P1M", "S$4.00", 4_000_000, RecurrenceMode.NON_RECURRING), Locale.US, res)
        assertTrue(prepaid.contains("Does not renew"))
    }

    @Test fun allReadingLanguagesCarryAttributionAndLocalFirstChargeTerms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val offer = annual()
        val firstPayment = requireNotNull(firstPaymentAfterTrial(offer))
        val disclosures = listOf("en", "ms", "id").map { tag ->
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val resources = context.createConfigurationContext(config).resources
            assertTrue(resources.getString(R.string.program_excerpt_credit).contains("Robert L. Nichols"))
            assertTrue(resources.getString(R.string.program_excerpt_credit).contains("Keeping a personal record"))
            assertTrue(resources.getString(R.string.program_excerpt).contains("A useful record"))
            resources.getString(R.string.shift_first_payment, firstPayment).also { assertTrue(it.contains("S$2.00")) }
        }
        assertEquals(3, disclosures.distinct().size)
    }

    @Test fun lifetimeHasOneStoreFormattedPaymentAndNoRenewal() {
        val product = TestStoreProduct(
            id = "fixture_lifetime", name = "Lifetime", title = "Lifetime", description = "",
            price = Price("S$120.00", 120_000_000, "SGD"), period = null, freeTrialPricingPhase = null,
        )
        val offer = requireNotNull(PlanOffer.from(Package("one-time", PackageType.LIFETIME, product, PresentedOfferingContext("test"))))
        assertNull(firstPaymentAfterTrial(offer))
        assertEquals(listOf(res.getString(R.string.shift_price_once, product.price.formatted)), offerTerms(offer, Locale.US, res))
    }

    @Test fun missingTermsCannotCreateAPurchaseOffer() {
        val base = phase("P1Y", "S$50.00", 50_000_000, RecurrenceMode.INFINITE_RECURRING)
        val trial = phase("P1W", "Free", 0, RecurrenceMode.FINITE_RECURRING, 1)
        assertTrue(hasCompleteSchedule(listOf(trial, base), "SGD"))
        assertFalse(hasCompleteSchedule(emptyList(), "SGD"))
        assertFalse(hasCompleteSchedule(listOf(trial), "SGD"))
        assertFalse(hasCompleteSchedule(listOf(base), "USD"))
        assertFalse(hasCompleteSchedule(listOf(phase("P1Y", "", 50_000_000, RecurrenceMode.INFINITE_RECURRING)), "SGD"))
        assertFalse(hasCompleteSchedule(listOf(phase("P1W", "Free", 0, RecurrenceMode.FINITE_RECURRING, 0), base), "SGD"))
        val product = TestStoreProduct(id = "blank-price", name = "Lifetime", title = "Lifetime", description = "",
            price = Price("", 30_000_000, "SGD"), period = null, freeTrialPricingPhase = null)
        assertNull(PlanOffer.from(Package("no-price", PackageType.LIFETIME, product, PresentedOfferingContext("test"))))
    }

    @Test fun recurringPackagesMustMatchTheActualBasePeriod() {
        fun offer(period: String, type: PackageType): PlanOffer? {
            val product = TestStoreProduct(id = "period-fixture", name = "Plan", title = "Plan", description = "",
                price = Price("S$50.00", 50_000_000, "SGD"), period = Period.create(period), freeTrialPricingPhase = null)
            return PlanOffer.from(Package("catalog", type, product, PresentedOfferingContext("test")))
        }
        assertNull(offer("P1M", PackageType.ANNUAL))
        assertNull(offer("P1Y", PackageType.MONTHLY))
        assertNull(offer("P3M", PackageType.MONTHLY))
        assertNull(offer("P52W", PackageType.ANNUAL))
        assertEquals(PlanType.ANNUAL, offer("P12M", PackageType.ANNUAL)?.plan)
        assertEquals(PlanType.MONTHLY, offer("P1M", PackageType.MONTHLY)?.plan)
    }

    @Test fun installmentCommitmentIsDistinctFromBillingInterval() {
        val monthly = Period.create("P1M")
        assertTrue(matchesBillingInterval(PlanType.ANNUAL, monthly, 12))
        assertTrue(matchesBillingInterval(PlanType.MONTHLY, monthly, 6))
        assertFalse(matchesBillingInterval(PlanType.ANNUAL, monthly, 6))
        assertFalse(matchesBillingInterval(PlanType.MONTHLY, monthly, 0))
        assertFalse(matchesBillingInterval(PlanType.ANNUAL, Period.create("P1Y"), 12))
        val payment = phase("P1M", "S$6.00", 6_000_000, RecurrenceMode.INFINITE_RECURRING)
        val marked = phaseTerms(payment, Locale.US, res, installments = true)
        assertTrue(marked.contains("S$6.00") && marked.contains("1 month") && marked.contains("end of the current commitment"))
    }

    @Test fun mixedCatalogExcludesSubscriptionsLabeledLifetimeAndOneTimeProductsLabeledRecurring() {
        val annual = annual().pkg
        val oneTime = TestStoreProduct(id = "one-time", name = "One time", title = "One time", description = "",
            price = Price("S$120.00", 120_000_000, "SGD"), period = null, freeTrialPricingPhase = null)
        val context = PresentedOfferingContext("test")
        val catalog = listOf(
            Package("bad-lifetime", PackageType.LIFETIME, annual.product, context),
            Package("bad-monthly", PackageType.MONTHLY, oneTime, context),
            Package("bad-annual", PackageType.ANNUAL, oneTime, context),
            Package("custom", PackageType.CUSTOM, annual.product, context),
            annual,
            Package("lifetime", PackageType.LIFETIME, oneTime, context),
        )
        val visible = catalog.mapNotNull(PlanOffer::from)
        assertEquals(listOf("annual", "lifetime"), visible.map { it.id })
        assertTrue(offerTerms(visible.first(), Locale.US, res).any { it.contains("Renews") })
        assertEquals(listOf(res.getString(R.string.shift_price_once, oneTime.price.formatted)),
            offerTerms(visible.last(), Locale.US, res))
    }

    @Test fun sdkOutcomesPreserveCancellationPendingAndExistingOwnership() {
        assertEquals(PurchaseOutcome.CANCELLED, transactionOutcome(PurchasesErrorCode.StoreProblemError, userCancelled = true))
        assertEquals(PurchaseOutcome.PENDING, transactionOutcome(PurchasesErrorCode.PaymentPendingError, false))
        assertEquals(PurchaseOutcome.ALREADY_OWNED, transactionOutcome(PurchasesErrorCode.ProductAlreadyPurchasedError, false))
        assertEquals(PurchaseOutcome.FAILED, transactionOutcome(PurchasesErrorCode.NetworkError, false))
    }
}
