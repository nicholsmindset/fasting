package com.fastingshift.app.ui.onboarding

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.NicholsMark
import java.time.ZoneId
import java.time.ZonedDateTime
import com.fastingshift.app.ui.firstweek.FirstWeekRules
import com.fastingshift.app.ui.firstweek.FirstWindowTimes
import com.fastingshift.app.ui.firstweek.FirstPlanTimeFields
import com.fastingshift.app.ui.safety.ChecklistDialog
import com.fastingshift.app.ui.settings.WelcomeHistoryImport
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.app.ui.design.SwitchGroupRow
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.safety.HelplineList
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.ProtocolType
import com.fastingshift.domain.onboarding.Goal
import com.fastingshift.domain.protocol.Presets
import com.fastingshift.domain.safety.GateReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    if (!ready) return
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.completeNotifications() }
    if (st.step == Step.COMPLETE) {
        LaunchedEffect(vm) { vm.finish(onFinished) }
        return
    }
    // On the first step of a re-run, system back leaves onboarding and returns to Settings.
    val canGoBack = st.step != Step.WELCOME && !(st.rerun && st.step == Step.SCREENING)
    BackHandler(enabled = canGoBack) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            ProgressHeader(stepProgress(st.step, st.rerun), canGoBack, vm::back)
            AnimatedContent(
                targetState = st.step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(tween(320)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(320))
                    val exit = slideOutHorizontally(tween(220)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(220))
                    enter togetherWith exit
                },
                label = "onboardingStep",
                modifier = Modifier.weight(1f),
            ) { step ->
                when (step) {
                    Step.WELCOME -> Welcome(
                        onStart = { vm.openGoal(FirstIntent.START_NOW) }, onPlan = { vm.openGoal() }, onTour = vm::openTour,
                    )
                    Step.INTRO -> Intro(
                        example = FirstWeekRules.nextEvening(vm.now(), ZoneId.systemDefault()),
                        onSetup = { vm.openGoal() },
                    )
                    Step.GOAL -> GoalStep(st, vm)
                    Step.SCREENING -> ScreeningForm(st, vm)
                    Step.RESULT -> Result(st, vm, onFinished)
                    Step.PROTOCOL -> ProtocolPick(st, vm, onFinished)
                    Step.FIRST_PLAN -> FirstPlanStep(st, vm)
                    Step.NOTIFICATIONS -> StepPage(
                        actions = {
                            PrimaryButton(stringResource(R.string.onb_notif_allow), onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.completeNotifications()
                            })
                            PlainButton(stringResource(R.string.onb_not_now), onClick = vm::completeNotifications, modifier = Modifier.fillMaxWidth())
                        },
                    ) {
                        CenteredHero(Icons.Filled.Notifications, R.string.first_plan_saved_title, R.string.first_plan_saved_body)
                    }
                    Step.COMPLETE -> Unit
                }
            }
        }
    }
}

/** Back chevron, "Step n of N" and a thin bar that animates as the user moves through. */
@Composable
private fun ProgressHeader(progress: Pair<Int, Int>?, canGoBack: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onb_back), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (progress != null) {
            val (n, total) = progress
            val fraction by animateFloatAsState(n / total.toFloat(), tween(450), label = "progress")
            val label = stringResource(R.string.onb_step_of, n, total)
            Column(Modifier.weight(1f).padding(end = 16.dp).semantics(mergeDescendants = true) { contentDescription = label }) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

/** A step: scrolling content with the actions pinned at the bottom, like an iOS sheet flow. */
@Composable
private fun StepPage(actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = actions,
        )
    }
}

@Composable
private fun StepTitle(title: Int, body: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (body != null) Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenteredHero(icon: ImageVector, title: Int, body: Int?, container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroIcon(icon, container = container, tint = tint)
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onPlan: () -> Unit, onTour: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepTitle(R.string.onb_welcome_title, R.string.onb_welcome_body)
        InsetGroup {
            GroupRow(stringResource(R.string.onb_intent_now), subtitle = stringResource(R.string.onb_intent_now_detail),
                showChevron = true, onClick = onStart)
            GroupDivider()
            GroupRow(stringResource(R.string.onb_get_started), subtitle = stringResource(R.string.onb_intent_later_detail),
                showChevron = true, onClick = onPlan)
            GroupDivider()
            GroupRow(stringResource(R.string.onb_open_tour), subtitle = stringResource(R.string.onb_intent_explore_detail),
                showChevron = true, onClick = onTour)
        }
        WelcomeHistoryImport()
        Box {
            WelcomeArt()
            NicholsMark(Modifier.align(Alignment.TopStart).padding(12.dp).size(36.dp), compact = true)
        }
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class IntroPage(val icon: ImageVector, val title: Int, val body: Int)

private val introPages = listOf(
    IntroPage(Icons.Filled.PlayArrow, R.string.onb_intro_timer_title, R.string.onb_intro_timer_body),
    IntroPage(Icons.Filled.Favorite, R.string.onb_intro_safety_title, R.string.onb_intro_safety_body),
    IntroPage(Icons.Filled.Lock, R.string.onb_intro_privacy_title, R.string.onb_intro_privacy_body),
)

/** "How Fasting Shift works": three swipeable pages with dots, Next and Skip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(example: ZonedDateTime, onSetup: () -> Unit) {
    val pager = rememberPagerState { introPages.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val last = pager.currentPage == introPages.lastIndex
    Column(Modifier.fillMaxSize()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.onb_intro_heading), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() },
            )
            if (!last) PlainButton(stringResource(R.string.onb_skip), onClick = onSetup)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val page = introPages[i]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                if (i != 0) HeroIcon(page.icon, size = 112.dp)
                Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (i == 0) {
                    InsetGroup(header = stringResource(R.string.onb_sample_window)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FirstWindowTimes(example, Presets.W12_12)
                        }
                    }
                }
            }
        }
        PageDots(pager.currentPage, introPages.size, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        Box(Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = 16.dp)) {
            PrimaryButton(
                stringResource(if (last) R.string.onb_skip else R.string.onb_next),
                onClick = {
                    if (last) {
                        onSetup()
                    } else {
                        haptics.tick()
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
            )
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.onb_intro_page, current + 1, count)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun GoalStep(st: OnboardingState, vm: OnboardingViewModel) {
    StepPage(actions = { PrimaryButton(stringResource(R.string.onb_next), onClick = vm::continueFromGoal, enabled = st.goal != null) }) {
        StepTitle(R.string.onb_goal_title, R.string.onb_goal_body)
        InsetGroup {
            Goal.entries.forEachIndexed { i, g ->
                if (i > 0) GroupDivider()
                SelectGroupRow(stringResource(goalLabel(g)), selected = st.goal == g, onClick = { vm.setGoal(g) })
            }
        }
    }
}

@Composable
private fun ScreeningForm(st: OnboardingState, vm: OnboardingViewModel) {
    val f = st.form
    val required = stringResource(R.string.onb_field_hint)
    StepPage(actions = {
        if (st.formError) {
            f.invalidNumber?.let { number ->
                val label = when (number) {
                    ScreeningNumber.AGE -> R.string.onb_age
                    ScreeningNumber.HEIGHT -> R.string.onb_height
                    ScreeningNumber.WEIGHT -> R.string.onb_weight
                }
                Text(stringResource(R.string.onb_form_error, stringResource(label), number.bounds.first, number.bounds.last),
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PrimaryButton(stringResource(R.string.onb_next), onClick = vm::submitScreening)
    }) {
        StepTitle(R.string.onb_screen_title, R.string.onb_screen_body)
        InsetGroup(header = stringResource(R.string.onb_screen_about)) {
            TextFieldGroupRow(stringResource(R.string.onb_age), f.age, { v -> vm.updateForm { it.copy(age = v) } }, placeholder = required)
            GroupDivider()
            TextFieldGroupRow(stringResource(R.string.onb_height), f.heightCm, { v -> vm.updateForm { it.copy(heightCm = v) } }, placeholder = required)
            GroupDivider()
            TextFieldGroupRow(stringResource(R.string.onb_weight), f.weightKg, { v -> vm.updateForm { it.copy(weightKg = v) } }, placeholder = required)
        }
        InsetGroup(header = stringResource(R.string.onb_screen_health)) {
            SwitchGroupRow(stringResource(R.string.onb_pregnant), f.pregnant, { v -> vm.updateForm { it.copy(pregnant = v) } })
            GroupDivider()
            SwitchGroupRow(stringResource(R.string.onb_ed), f.eatingDisorder, { v -> vm.updateForm { it.copy(eatingDisorder = v) } })
            GroupDivider()
            SwitchGroupRow(stringResource(R.string.onb_t1d), f.type1, { v -> vm.updateForm { it.copy(type1 = v) } })
        }
        InsetGroup(header = stringResource(R.string.onb_meds_title), footer = stringResource(R.string.onb_screen_privacy)) {
            Medication.entries.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                CheckGroupRow(stringResource(medicationLabel(m)), m in f.medications, { on ->
                    vm.updateForm { it.copy(medications = if (on) it.medications + m else it.medications - m) }
                })
            }
        }
    }
}

@Composable
private fun Result(st: OnboardingState, vm: OnboardingViewModel, onFinished: () -> Unit) {
    val outcome = st.outcome ?: return
    when (outcome.result) {
        GateResult.OK -> StepPage(actions = { PrimaryButton(stringResource(R.string.onb_next), onClick = { vm.confirmResult(onFinished) }, haptic = true) }) {
            CenteredHero(Icons.Filled.CheckCircle, R.string.onb_ok_title, R.string.onb_ok_body)
        }
        GateResult.SOFT -> StepPage(actions = { PrimaryButton(stringResource(R.string.onb_next), onClick = { vm.confirmResult(onFinished) }) }) {
            CenteredHero(
                Icons.Filled.Info, R.string.onb_soft_title, R.string.onb_soft_body,
                container = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.tertiary,
            )
            InsetGroup {
                outcome.reasons.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    GroupRow(stringResource(reasonLabel(r)))
                }
            }
            InsetGroup {
                CheckGroupRow(stringResource(R.string.onb_soft_ack), st.clinicianAck, vm::setClinicianAck)
            }
        }
        GateResult.BLOCK -> StepPage(actions = { PrimaryButton(stringResource(R.string.onb_continue), onClick = { vm.confirmResult(onFinished) }) }) {
            // Non-diagnostic, non-shaming: explain, offer support, keep the door open.
            CenteredHero(Icons.Filled.Favorite, R.string.onb_block_title, R.string.onb_block_body)
            HelplineList()
        }
    }
}

@Composable
private fun ProtocolPick(st: OnboardingState, vm: OnboardingViewModel, onFinished: () -> Unit) {
    if (st.protocols.isEmpty()) {
        StepPage(actions = { PrimaryButton(stringResource(R.string.onb_continue), onClick = { vm.finish(onFinished) }) }) {
            StepTitle(R.string.onb_protocol_title)
            Text(stringResource(R.string.timer_needs_clinician), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val daily = st.protocols.filter { it.type == ProtocolType.WINDOW }
    val longer = st.protocols - daily.toSet()
    val recommended = recommendedProtocol(st.goal).takeIf { st.goal != null }
    var showLonger by rememberSaveable { mutableStateOf(st.selected in longer) }
    StepPage(actions = { PrimaryButton(stringResource(R.string.onb_next), onClick = { vm.confirmProtocol(onFinished) }, haptic = true) }) {
        StepTitle(R.string.onb_protocol_title, R.string.onb_protocol_body)
        if (st.goal == Goal.LONG_FASTS) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.onb_protocol_long_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (daily.isNotEmpty()) {
            InsetGroup(header = stringResource(R.string.onb_protocol_daily)) {
                ProtocolRows(daily, st.selected, recommended, vm::select)
            }
        }
        if (longer.isNotEmpty()) {
            Column(Modifier.animateContentSize()) {
                if (showLonger) {
                    InsetGroup(header = stringResource(R.string.onb_protocol_longer)) {
                        ProtocolRows(longer, st.selected, recommended, vm::select)
                    }
                } else {
                    InsetGroup {
                        GroupRow(
                            stringResource(R.string.onb_protocol_show_more), titleColor = MaterialTheme.colorScheme.primary,
                            showChevron = true, onClick = { showLonger = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstPlanStep(st: OnboardingState, vm: OnboardingViewModel) {
    val now by vm.minuteClock.collectAsStateWithLifecycle(initialValue = vm.now())
    val context = LocalContext.current
    // Let the whole page scroll with large type or a short landscape window.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val immediate = st.intent == FirstIntent.START_NOW
        val expired = st.firstStart?.let { it.toInstant() <= now || it.zone.rules != ZoneId.systemDefault().rules } == true
        if (immediate) {
            StepTitle(R.string.first_plan_now_title, R.string.first_plan_now_body)
            FirstWindowTimes(now.atZone(ZoneId.systemDefault()), st.selected)
        } else {
            StepTitle(R.string.first_plan_title, R.string.first_plan_body)
            FirstPlanTimeFields(now = now, protocol = st.selected, start = st.firstStart, onPick = vm::pickFirstStart)
            if (expired) Text(stringResource(R.string.first_plan_expired), style = MaterialTheme.typography.bodyMedium)
        }
        st.planError?.let { message ->
            Text(
                stringResource(message), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (immediate) {
            PrimaryButton(stringResource(R.string.first_plan_start), onClick = { vm.startFirstFast() }, enabled = !st.busy)
        } else {
            PrimaryButton(
                stringResource(R.string.first_plan_schedule),
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    vm.saveFirstPlan(offerNotifications = needsPermission)
                },
                enabled = !st.busy && st.firstStart != null && !expired,
            )
            if (expired) SecondaryButton(stringResource(R.string.onb_intent_now), onClick = vm::reviewStartNow, enabled = !st.busy)
        }
        PlainButton(stringResource(R.string.first_plan_leave_open), onClick = vm::leaveFirstPlanOpen, modifier = Modifier.fillMaxWidth())
    }
    st.checklist?.let { requirements ->
        ChecklistDialog(requirements, onConfirm = { vm.startFirstFast(confirmChecklist = true) }, onDismiss = vm::dismissFirstChecklist)
    }
}

@Composable
private fun ProtocolRows(list: List<Protocol>, selected: Protocol, recommended: Protocol?, onSelect: (Protocol) -> Unit) {
    list.forEachIndexed { i, p ->
        if (i > 0) GroupDivider()
        SelectGroupRow(
            title = protocolDescription(p),
            selected = p == selected,
            onClick = { onSelect(p) },
            badge = if (p == recommended) stringResource(R.string.onb_protocol_recommended) else null,
        )
    }
}

/** "16:8 · fast 16h, eat within 8h" and similar one-line descriptions. */
@Composable
fun protocolDescription(p: Protocol): String = when {
    p.id == Presets.OMAD.id -> stringResource(R.string.protocol_desc_omad, p.name)
    p.id == Presets.FIVE_TWO.id -> stringResource(R.string.protocol_desc_five_two, p.name)
    p.id == Presets.ADF.id -> stringResource(R.string.protocol_desc_adf, p.name)
    p.type == ProtocolType.WINDOW && p.eatHours != null -> stringResource(R.string.protocol_desc_window, p.name, p.fastHours, p.eatHours ?: 0)
    else -> stringResource(R.string.protocol_desc_duration, p.name, p.fastHours)
}

private fun goalLabel(g: Goal) = when (g) {
    Goal.CONSISTENCY -> R.string.goal_consistency
    Goal.WEIGHT -> R.string.goal_weight
    Goal.METABOLIC_HEALTH -> R.string.goal_metabolic
    Goal.LONG_FASTS -> R.string.goal_long
}

private fun medicationLabel(m: Medication) = when (m) {
    Medication.INSULIN -> R.string.med_insulin
    Medication.SULFONYLUREA -> R.string.med_sulfonylurea
    Medication.SGLT2_INHIBITOR -> R.string.med_sglt2
    Medication.DIURETIC -> R.string.med_diuretic
    Medication.BLOOD_PRESSURE -> R.string.med_bp
    Medication.GLP1 -> R.string.med_glp1
}

private fun reasonLabel(r: GateReason) = when (r) {
    GateReason.GLUCOSE_LOWERING_MEDICATION -> R.string.reason_glucose
    GateReason.SGLT2_INHIBITOR -> R.string.reason_sglt2
    GateReason.BLOOD_PRESSURE_OR_DIURETIC -> R.string.reason_bp
    GateReason.GLP1 -> R.string.reason_glp1
    else -> R.string.reason_other
}
