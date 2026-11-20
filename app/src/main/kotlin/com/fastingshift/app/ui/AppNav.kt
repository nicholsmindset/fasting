package com.fastingshift.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fastingshift.app.R
import com.fastingshift.app.ui.fast.EditFastRoute
import com.fastingshift.app.ui.history.HistoryRoute
import com.fastingshift.app.ui.log.LogRoute
import com.fastingshift.app.ui.onboarding.OnboardingRoute
import com.fastingshift.app.ui.plus.InsightsRoute
import com.fastingshift.app.ui.plus.PaywallRoute
import com.fastingshift.app.ui.plus.ProgramRoute
import com.fastingshift.app.ui.reliability.ReliabilityRoute
import com.fastingshift.app.ui.safety.CheckInRoute
import com.fastingshift.app.ui.safety.StopFastRoute
import com.fastingshift.app.ui.settings.SchedulesRoute
import com.fastingshift.app.ui.settings.SettingsRoute
import com.fastingshift.app.ui.timer.TimerRoute

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
    fun checkIn(fastId: Long) = "checkin/$fastId"
    fun editFast(fastId: Long) = "fast/$fastId"
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TIMER, R.string.tab_timer, Icons.Filled.Home),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Filled.DateRange),
    Tab(Routes.LOG, R.string.tab_log, Icons.Filled.Edit),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
)

@Composable
fun AppNav(onboarded: Boolean, deepLink: String?, onDeepLinkHandled: () -> Unit, nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    LaunchedEffect(deepLink, onboarded) {
        if (deepLink != null && onboarded) {
            nav.navigate(deepLink)
            onDeepLinkHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = if (onboarded) Routes.TIMER else Routes.ONBOARDING, modifier = Modifier.padding(padding)) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    onFinished = { nav.navigate(Routes.TIMER) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                    onShowPaywall = { nav.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.TIMER) {
                TimerRoute(
                    onEditFast = { nav.navigate(Routes.editFast(it)) },
                    onOpenProgram = { nav.navigate(Routes.PROGRAM) },
                    onOpenCheckIn = { nav.navigate(Routes.checkIn(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryRoute(onEditFast = { nav.navigate(Routes.editFast(it)) }, onOpenInsights = { nav.navigate(Routes.INSIGHTS) })
            }
            composable(Routes.LOG) { LogRoute() }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onRerunScreening = { nav.navigate(Routes.ONBOARDING) },
                    onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
                    onOpenSchedules = { nav.navigate(Routes.SCHEDULES) },
                    onOpenPaywall = { nav.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.RELIABILITY) { ReliabilityRoute(onBack = { nav.popBackStack() }) }
            composable(Routes.SCHEDULES) { SchedulesRoute(onBack = { nav.popBackStack() }) }
            composable(Routes.PAYWALL) { PaywallRoute(onClose = { nav.popBackStack() }) }
            composable(Routes.PROGRAM) { ProgramRoute(onBack = { nav.popBackStack() }, onUpgrade = { nav.navigate(Routes.PAYWALL) }) }
            composable(Routes.INSIGHTS) { InsightsRoute(onBack = { nav.popBackStack() }, onUpgrade = { nav.navigate(Routes.PAYWALL) }) }
            composable(Routes.STOP) { StopFastRoute(onDone = { nav.popBackStack(Routes.TIMER, inclusive = false) }) }
            composable(Routes.CHECKIN, arguments = listOf(navArgument("fastId") { type = NavType.LongType })) { e ->
                CheckInRoute(
                    fastId = e.arguments?.getLong("fastId") ?: 0L,
                    onRedFlag = { nav.navigate(Routes.STOP) { popUpTo(Routes.TIMER) } },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.EDIT_FAST, arguments = listOf(navArgument("fastId") { type = NavType.LongType })) { e ->
                EditFastRoute(fastId = e.arguments?.getLong("fastId") ?: 0L, onDone = { nav.popBackStack() })
            }
        }
    }
}
