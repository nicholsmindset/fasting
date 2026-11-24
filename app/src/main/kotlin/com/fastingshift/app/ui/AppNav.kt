package com.fastingshift.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import java.time.LocalDate
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fastingshift.app.R
import com.fastingshift.app.ui.fast.AddPastFastRoute
import com.fastingshift.app.ui.fast.EditFastRoute
import com.fastingshift.app.ui.history.HistoryRoute
import com.fastingshift.app.ui.log.LogRoute
import com.fastingshift.app.ui.onboarding.OnboardingRoute
import com.fastingshift.app.ui.plus.InsightsRoute
import com.fastingshift.app.ui.plus.PaywallRoute
import com.fastingshift.app.ui.plus.ProgramRoute
import com.fastingshift.app.ui.reliability.ReliabilityRoute
import com.fastingshift.app.ui.safety.CheckInRoute
import com.fastingshift.app.ui.safety.RefeedingRoute
import com.fastingshift.app.ui.safety.StopFastRoute
import com.fastingshift.app.ui.settings.SchedulesRoute
import com.fastingshift.app.ui.settings.SettingsRoute
import com.fastingshift.app.ui.timer.TimerRoute
import com.fastingshift.app.ui.trust.TrustRoute
import com.fastingshift.app.ui.challenge.ChallengeDetailRoute
import com.fastingshift.app.ui.challenge.ChallengeListRoute
import com.fastingshift.app.ui.challenge.CreateChallengeRoute
import com.fastingshift.app.ui.challenge.JoinChallengeRoute
import com.fastingshift.domain.challenge.ChallengeLinks
import com.fastingshift.domain.safety.RefeedingTier

object Routes {
    const val ONBOARDING = "onboarding"
    const val TIMER = "timer"
    const val HISTORY = "history"
    const val LOG = "log"
    const val SETTINGS = "settings"
    const val RELIABILITY = "reliability"
    const val SCHEDULES = "schedules"
    const val PAYWALL = "paywall"
    const val PROGRAM = "program"
    const val INSIGHTS = "insights"
    const val STOP = "stop"
    const val CHECKIN = "checkin/{fastId}"
    const val EDIT_FAST = "fast/{fastId}"
    const val ADD_PAST_FAST = "addfast"
    const val TRUST = "trust"
    const val MUSLIM = "muslim"
    const val CHALLENGES = "challenges"
    const val CHALLENGE_NEW = "challenges/new"
    const val CHALLENGE = "challenge/{challengeId}"
    const val CHALLENGE_JOIN = "challenge/join/{code}"
    fun challenge(id: Long) = "challenge/$id"

    /** Invite codes are base64url, so they are safe in a route; anything else opens as invalid. */
    fun joinChallenge(code: String) = "challenge/join/" + (code.takeIf(ChallengeLinks::looksLikeCode) ?: "invalid")

    /** Refeeding guidance. No tier shows every tier (from Settings); fastId offers Undo. */
    const val REFEEDING = "refeeding?tier={tier}&fastId={fastId}"
    fun checkIn(fastId: Long) = "checkin/$fastId"
    fun editFast(fastId: Long) = "fast/$fastId"
    fun addPastFast(day: LocalDate?) = if (day == null) ADD_PAST_FAST else "$ADD_PAST_FAST?date=$day"
    fun refeeding(tier: RefeedingTier?, fastId: Long? = null) =
        "refeeding?tier=${tier?.name ?: ""}&fastId=${fastId ?: -1L}"
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TIMER, R.string.tab_timer, Icons.Filled.Home),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Filled.DateRange),
    Tab(Routes.LOG, R.string.tab_log, Icons.Filled.Edit),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppNav(onboarded: Boolean, deepLink: String?, onDeepLinkHandled: () -> Unit, nav: NavHostController = rememberNavController()) {
    // Changing the graph when screening completes would reset its back stack during navigation.
    val initialRoute = rememberSaveable { if (onboarded) Routes.TIMER else Routes.ONBOARDING }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    LaunchedEffect(onboarded, deepLink, entry) {
        if (!onboarded || entry == null) return@LaunchedEffect
        // Establish Timer as the root before opening an invite, including after process recreation.
        if (nav.currentDestination?.route == Routes.ONBOARDING && nav.previousBackStackEntry == null) {
            nav.navigate(Routes.TIMER) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
        }
        if (deepLink != null) {
            // A malformed route must never crash the app.
            runCatching { nav.navigate(deepLink) }
            onDeepLinkHandled()
        }
    }

    val back: () -> Unit = { nav.popBackStack() }
    val toTimer: () -> Unit = { if (!nav.popBackStack(Routes.TIMER, inclusive = false)) nav.navigate(Routes.TIMER) }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == current }) {
                // iOS-style tab bar: translucent, hairline on top, accent colour for the selected tab.
                Column {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f), tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("tab_${tab.route}"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.TIMER) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                }
            }
        },
    ) { padding ->
        // Consume the insets the scaffold padded for, so screens don't add the status bar twice.
        NavHost(
            nav,
            startDestination = initialRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.ONBOARDING) { screeningEntry ->
                OnboardingRoute(
                    onFinished = {
                        // The persistence callback can arrive after an invite has already opened.
                        if (nav.currentBackStackEntry === screeningEntry) {
                            // Re-running the screening from Settings returns there; first run opens the Timer.
                            if (nav.previousBackStackEntry != null) {
                                nav.popBackStack()
                            } else {
                                nav.navigate(Routes.TIMER) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            }
                        }
                    },
                    onShowPaywall = { nav.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.TIMER) {
                TimerRoute(
                    onEditFast = { nav.navigate(Routes.editFast(it)) },
                    onOpenProgram = { nav.navigate(Routes.PROGRAM) },
                    onOpenCheckIn = { nav.navigate(Routes.checkIn(it)) },
                    onOpenRefeeding = { tier, id -> nav.navigate(Routes.refeeding(tier, id)) { launchSingleTop = true } },
                    onOpenScreening = { nav.navigate(Routes.ONBOARDING) },
                    onOpenWeek = { day -> nav.navigate(if (day == null) Routes.SCHEDULES else "${Routes.SCHEDULES}?date=$day") },
                    onOpenMuslimHub = { nav.navigate(Routes.MUSLIM) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryRoute(
                    onEditFast = { nav.navigate(Routes.editFast(it)) },
                    onOpenInsights = { nav.navigate(Routes.INSIGHTS) },
                    onOpenSchedules = { nav.navigate(Routes.SCHEDULES) },
                    onAddPastFast = { day -> nav.navigate(Routes.addPastFast(day)) },
                    onOpenChallenges = { nav.navigate(Routes.CHALLENGES) },
                )
            }
            composable(Routes.LOG) { LogRoute() }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onRerunScreening = { nav.navigate(Routes.ONBOARDING) },
                    onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
                    onOpenSchedules = { nav.navigate(Routes.SCHEDULES) },
                    onOpenPaywall = { nav.navigate(Routes.PAYWALL) },
                    onOpenRefeeding = { nav.navigate(Routes.refeeding(null)) },
                    onOpenTrust = { nav.navigate(Routes.TRUST) },
                    onOpenMuslimHub = { nav.navigate(Routes.MUSLIM) },
                )
            }
            composable(Routes.RELIABILITY) { ReliabilityRoute(onBack = back) }
            composable(Routes.TRUST) { TrustRoute(onBack = back) }
            composable(Routes.CHALLENGES) {
                ChallengeListRoute(
                    onBack = back,
                    onOpen = { nav.navigate(Routes.challenge(it)) },
                    onNew = { nav.navigate(Routes.CHALLENGE_NEW) },
                    onJoinCode = { nav.navigate(Routes.joinChallenge(it)) },
                )
            }
            composable(Routes.CHALLENGE_NEW) {
                CreateChallengeRoute(onBack = back, onCreated = { id ->
                    nav.navigate(Routes.challenge(id)) { popUpTo(Routes.CHALLENGE_NEW) { inclusive = true } }
                })
            }
            composable(Routes.CHALLENGE, arguments = listOf(navArgument("challengeId") { type = NavType.LongType })) {
                ChallengeDetailRoute(onBack = back)
            }
            composable(Routes.CHALLENGE_JOIN, arguments = listOf(navArgument("code") { type = NavType.StringType })) {
                JoinChallengeRoute(onBack = back, onJoined = { id ->
                    nav.navigate(Routes.challenge(id)) { popUpTo(Routes.CHALLENGE_JOIN) { inclusive = true } }
                })
            }
            composable(
                "${Routes.SCHEDULES}?date={date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { destination ->
                val day = destination.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                SchedulesRoute(onBack = back, initialDate = day)
            }
            composable(Routes.PAYWALL) { PaywallRoute(onClose = back, onOpenProgram = { nav.navigate(Routes.PROGRAM) { launchSingleTop = true } }) }
            composable(Routes.PROGRAM) { ProgramRoute(onBack = back, onUpgrade = { nav.navigate(Routes.PAYWALL) }) }
            composable(Routes.INSIGHTS) { InsightsRoute(onBack = back, onUpgrade = { nav.navigate(Routes.PAYWALL) }) }
            composable(Routes.STOP) { StopFastRoute(onDone = { nav.popBackStack(Routes.TIMER, inclusive = false) }) }
            composable(Routes.CHECKIN, arguments = listOf(navArgument("fastId") { type = NavType.LongType })) { e ->
                CheckInRoute(
                    fastId = e.arguments?.getLong("fastId") ?: 0L,
                    onRedFlag = { nav.navigate(Routes.STOP) { popUpTo(Routes.TIMER) } },
                    onDone = back,
                )
            }
            composable(Routes.EDIT_FAST, arguments = listOf(navArgument("fastId") { type = NavType.LongType })) { e ->
                EditFastRoute(fastId = e.arguments?.getLong("fastId") ?: 0L, onDone = back)
            }
            composable(Routes.MUSLIM) {
                com.fastingshift.app.ui.ramadan.MuslimHubRoute(onBack = back, onOpenTimer = toTimer)
            }
            composable(
                "${Routes.ADD_PAST_FAST}?date={date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { AddPastFastRoute(onDone = back) }
            composable(
                Routes.REFEEDING,
                arguments = listOf(
                    navArgument("tier") { type = NavType.StringType; defaultValue = "" },
                    navArgument("fastId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                val tier = e.arguments?.getString("tier")?.let { t -> RefeedingTier.entries.firstOrNull { it.name == t } }
                RefeedingRoute(tier = tier, fastId = e.arguments?.getLong("fastId") ?: -1L, onDone = back)
            }
        }
    }
}
