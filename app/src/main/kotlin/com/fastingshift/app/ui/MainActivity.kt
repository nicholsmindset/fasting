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
import com.fastingshift.app.alarm.ReminderCoordinator
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.recovery.KeyLostScreen
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.challenge.ChallengeLinks
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Neither of these touches the database, so they are safe while its key is lost.
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var billing: BillingManager

    // Opens the database: only ever used after the key check, off the main thread.
    @Inject lateinit var coordinator: Lazy<ReminderCoordinator>

    private var deepLink by mutableStateOf<String?>(null)

    /** null until checked off the main thread; nothing database-backed is shown before. */
    private var keyStatus by mutableStateOf<DatabaseKeyProvider.Status?>(null)
    private var resetting by mutableStateOf(false)

    /** null until settings load, so onboarding never flashes for returning users. */
    private val onboardedFlow by lazy { settings.settings.map<_, Boolean?> { it.onboardingComplete } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity, or one relaunched from Recents, must not replay the route.
        if (savedInstanceState == null) consumeRoute(intent) else intent?.removeExtra(Notifications.EXTRA_ROUTE)
        lifecycleScope.launch { keyStatus = withContext(Dispatchers.IO) { FastingDatabase.keyStatus(applicationContext) } }
        lifecycleScope.launch { billing.refresh() }
        setContent {
            FastingShiftTheme {
                when (keyStatus) {
                    null -> Unit
                    DatabaseKeyProvider.Status.LOST -> KeyLostScreen(resetting = resetting, onStartFresh = ::startFresh)
                    else -> {
                        val onboarded by onboardedFlow.collectAsStateWithLifecycle(initialValue = null)
                        onboarded?.let { AppNav(onboarded = it, deepLink = deepLink, onDeepLinkHandled = { deepLink = null }) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up exact-alarm or notification permission changes made in system settings.
        lifecycleScope.launch(Dispatchers.Default) {
            val status = keyStatus ?: FastingDatabase.keyStatus(applicationContext)
            if (status != DatabaseKeyProvider.Status.LOST) runCatching { coordinator.get().reschedule() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRoute(intent)
    }

    /** Reads the notification route once, then removes it so it can't open the screen again. */
    private fun consumeRoute(intent: Intent?) {
        if (intent == null) return
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (!fromHistory) intent.getStringExtra(Notifications.EXTRA_ROUTE)?.let { deepLink = it }
        intent.removeExtra(Notifications.EXTRA_ROUTE)
        // Challenge invite links (https://fastingshift.com/c/...): opened once, never from Recents.
        if (!fromHistory && intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.takeIf { it.contains("/c/") }?.let { url ->
                deepLink = Routes.joinChallenge(ChallengeLinks.codeFrom(url) ?: "")
            }
            intent.data = null
        }
    }

    /** Deletes the unreadable database and its key, then restarts the process cleanly. */
    private fun startFresh() {
        if (resetting) return
        resetting = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { FastingDatabase.resetEncrypted(applicationContext) }
            // The safety screening was in the lost database: ask the questions again (SR-1).
            settings.setOnboardingComplete(false)
            startActivity(
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            // Singletons may hold a failed database; a new process starts from a clean graph.
            Runtime.getRuntime().exit(0)
        }
    }
}
