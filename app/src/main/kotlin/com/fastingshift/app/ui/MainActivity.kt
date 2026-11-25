package com.fastingshift.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fastingshift.app.ads.AdPrivacySession
import com.fastingshift.app.alarm.ReminderCoordinator
import com.fastingshift.app.billing.BillingManager
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.recovery.KeyLostScreen
import com.fastingshift.app.ui.partner.PartnerLinks
import com.fastingshift.app.ui.theme.FastingShiftTheme
import com.fastingshift.data.db.DatabaseKeyProvider
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.challenge.ChallengeLinks
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Neither of these touches the database, so they are safe while its key is lost.
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var billing: BillingManager

    // Opens the database: only ever used after the key check, off the main thread.
    @Inject lateinit var coordinator: Lazy<ReminderCoordinator>

    internal val adPrivacy by lazy { AdPrivacySession(this) }

    private var deepLink by mutableStateOf<String?>(null)

    /** null until checked off the main thread; nothing database-backed is shown before. */
    private var keyStatus by mutableStateOf<DatabaseKeyProvider.Status?>(null)
    private var resetting by mutableStateOf(false)
    private var resetFailed by mutableStateOf(false)

    private val displaySettings by lazy {
        settings.settings.map { it.onboardingComplete to it.appearance }.distinctUntilChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Restore only unhandled routes. AppNav clears this as soon as it navigates.
        if (savedInstanceState == null) consumeRoute(intent)
        else deepLink = savedInstanceState.getString(STATE_PENDING_ROUTE)
        lifecycleScope.launch { keyStatus = withContext(Dispatchers.IO) { FastingDatabase.keyStatus(applicationContext) } }
        lifecycleScope.launch { billing.refresh() }
        setContent {
            // Load both values before drawing the first screen on a fresh process.
            val display by displaySettings.collectAsStateWithLifecycle(initialValue = null)
            val (onboarded, appearance) = display ?: return@setContent
            val darkTheme = appearance.usesDarkPalette(isSystemInDarkTheme())
            LaunchedEffect(darkTheme) {
                val bars = if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            FastingShiftTheme(darkTheme = darkTheme) {
                when (keyStatus) {
                    null -> Unit
                    DatabaseKeyProvider.Status.LOST -> KeyLostScreen(resetting = resetting, onStartFresh = ::startFresh, resetFailed = resetFailed)
                    else -> AppNav(onboarded = onboarded, deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        deepLink?.let { outState.putString(STATE_PENDING_ROUTE, it) }
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

    private fun isNotificationRoute(route: String): Boolean = route == Routes.partner() || route == "history" || route == "settings" || Regex("checkin/\\d{1,18}").matches(route)

    /** Reads the notification route once, then removes it so it can't open the screen again. */
    private fun consumeRoute(intent: Intent?) {
        if (intent == null) return
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        // Any app can start this activity: accept only the routes our own notifications send.
        if (!fromHistory) intent.getStringExtra(Notifications.EXTRA_ROUTE)?.takeIf(::isNotificationRoute)?.let { deepLink = it }
        intent.removeExtra(Notifications.EXTRA_ROUTE)
        // Challenge invite links (https://fastingshift.com/c/...): opened once, never from Recents.
        if (!fromHistory && intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                when {
                    PartnerLinks.isPartnerUrl(url) -> deepLink = Routes.partner(PartnerLinks.token(url) ?: "invalid")
                    url.contains("/c/") -> deepLink = Routes.joinChallenge(ChallengeLinks.codeFrom(url) ?: "")
                    else -> Unit
                }
            }
            intent.data = null
        }
    }

    /** Deletes the unreadable database and its key, then restarts the process cleanly. */
    private fun startFresh() {
        if (resetting) return
        resetting = true
        resetFailed = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FastingDatabase.resetEncrypted(applicationContext)
                    // Row ids restart: new Health Connect ids must not overwrite earlier records.
                    val removed = applicationContext.deleteSharedPreferences(com.fastingshift.app.health.HealthConnectManager.ID_PREFS)
                    check(removed) { "Could not reset Health Connect record IDs" }
                }
                // The safety screening was in the lost database: ask the questions again (SR-1).
                settings.setOnboardingComplete(false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                resetting = false
                resetFailed = true
                return@launch
            }
            startActivity(
                Intent(applicationContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            // Singletons may hold a failed database; a new process starts from a clean graph.
            Runtime.getRuntime().exit(0)
        }
    }
    private companion object { const val STATE_PENDING_ROUTE = "pending_route" }
}
