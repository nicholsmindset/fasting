package com.fastingshift.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var billing: BillingManager

    private var deepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink = intent?.getStringExtra(Notifications.EXTRA_ROUTE)
        lifecycleScope.launch { billing.refresh() }
        setContent {
            FastingShiftTheme {
                // null until settings load, so onboarding never flashes for returning users.
                val onboarded by settings.settings.map<_, Boolean?> { it.onboardingComplete }
                    .collectAsStateWithLifecycle(initialValue = null)
                onboarded?.let { AppNav(onboarded = it, deepLink = deepLink, onDeepLinkHandled = { deepLink = null }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_ROUTE)?.let { deepLink = it }
    }
}
