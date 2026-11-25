package com.fastingshift.app.ads

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fastingshift.app.BuildConfig
import com.fastingshift.app.R
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.ui.MainActivity
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.domain.billing.Entitlement
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Bound to the activity so a stale activity can never be used to open a consent form. */
internal class AdPrivacySession(private val activity: Activity) {
    private val information by lazy { UserMessagingPlatform.getConsentInformation(activity) }
    private var updated = false
    val privacyOptionsRequired: Boolean
        get() = information.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED

    suspend fun gatherConsent(): Boolean {
        if (!updated) {
            val success = suspendCancellableCoroutine<Boolean> { result ->
                information.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(),
                    { if (result.isActive) result.resume(true) },
                    { if (result.isActive) result.resume(false) })
            }
            if (!success) return false
            val shown = suspendCancellableCoroutine<Boolean> { result ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (result.isActive) result.resume(error == null)
                }
            }
            if (!shown) return false
            updated = true
        }
        return information.canRequestAds()
    }

    fun showPrivacyOptions(onFinished: (Boolean) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            updated = false
            onFinished(error == null)
        }
    }
}

@Composable
internal fun AdPrivacyChoices(onError: () -> Unit) {
    if (!BuildConfig.ADS_ENABLED) return
    val privacy = (LocalActivity.current as? MainActivity)?.adPrivacy ?: return
    if (!privacy.privacyOptionsRequired) return
    var opening by remember { mutableStateOf(false) }
    GroupRow(stringResource(R.string.ad_privacy_choices), showChevron = true, onClick = {
        if (!opening) {
            opening = true
            privacy.showPrivacyOptions { success ->
                opening = false
                if (!success) onError()
            }
        }
    })
    GroupDivider()
}

@Composable
internal fun SettingsBanner(billing: BillingManager) {
    if (!BuildConfig.ADS_ENABLED || !billing.isConfigured) return
    val activity = LocalActivity.current as? MainActivity ?: return
    val owner = LocalLifecycleOwner.current
    val entitlement by billing.loadedEntitlement.collectAsStateWithLifecycle()

    // The navigation entry stops being RESUMED when covered or when the app backgrounds.
    // Losing entitlement also cancels this work and destroys the banner.
    val ready by produceState(false, activity, owner, entitlement) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                if (canRequestSettingsAds(entitlement, billing::confirmFreeForAds, activity.adPrivacy::gatherConsent)) {
                    value = withContext(Dispatchers.IO) {
                        suspendCancellableCoroutine { result ->
                            MobileAds.initialize(activity.applicationContext) {
                                val disabled = MobileAds.putPublisherFirstPartyIdEnabled(false)
                                if (result.isActive) result.resume(disabled)
                            }
                        }
                    }
                }
                awaitCancellation()
            } finally {
                value = false
            }
        }
    }
    if (ready && entitlement == Entitlement.FREE) AdaptiveBanner()
}

@Composable
private fun AdaptiveBanner() {
    val activity = LocalActivity.current ?: return
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt()
        if (width < 320) return@BoxWithConstraints
        key(width) {
            val size = remember(width, activity) {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width)
            }
            var failed by remember { mutableStateOf(false) }
            if (!failed) {
                AndroidView(
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth().height(size.height.dp),
                    factory = { context ->
                        AdView(context).apply {
                            adUnitId = BuildConfig.SETTINGS_BANNER_ID
                            setAdSize(size)
                            adListener = object : AdListener() {
                                override fun onAdFailedToLoad(error: LoadAdError) { failed = true }
                            }
                            // No health data, user IDs, events, keywords or content URLs enter this request.
                            val privacy = Bundle().apply { putString("npa", "1") }
                            loadAd(AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, privacy).build())
                        }
                    },
                    onReset = null,
                    onRelease = { it.destroy() },
                )
            }
        }
    }
}
