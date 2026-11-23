package com.fastingshift.app.ui.timer

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.startReminderNotificationsEnabled
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.ProgressRing
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupHeader
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.firstweek.CoachingTipCard
import com.fastingshift.app.ui.firstweek.FastSummarySheet
import com.fastingshift.app.ui.firstweek.FirstFastCard
import com.fastingshift.app.ui.ramadan.RamadanCard
import com.fastingshift.app.ui.safety.RamadanSafetyCard
import com.fastingshift.app.ui.safety.ChecklistDialog
import com.fastingshift.app.ui.safety.HelplineList
import com.fastingshift.domain.model.Electrolyte
import com.fastingshift.domain.model.Protocol
import com.fastingshift.domain.model.WeightUnit
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.timeline.BodyTimeline
import com.fastingshift.domain.timeline.EvidenceLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimerRoute(
    onEditFast: (Long) -> Unit,
    onOpenProgram: () -> Unit,
    onOpenCheckIn: (Long) -> Unit,
    onOpenRefeeding: (RefeedingTier, Long) -> Unit,
    onOpenScreening: () -> Unit,
    onOpenMuslimHub: () -> Unit = {},
    viewModel: TimerViewModel = hiltViewModel(),
    onOpenWeek: (LocalDate?) -> Unit = {},
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Notification permission is requested in context: when the user starts a fast.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val onStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start()
    }

    Box(Modifier.fillMaxSize()) {
        val state = loaded
        if (state == null) {
            LoadingBox(Modifier.padding(top = 120.dp))
        } else {
            TimerScreen(
                state = state,
                actions = TimerActions(
                    onSelect = viewModel::select, onSelectCustom = viewModel::selectCustom, onStart = onStart, onEnd = viewModel::end,
                    onEdit = { state.active?.let { onEditFast(it.id) } }, onCheckIn = { state.active?.let { onOpenCheckIn(it.id) } },
                    onOpenProgram = onOpenProgram, onToggleMode = viewModel::cycleMode, onOpenScreening = onOpenScreening,
                    onStartAt = viewModel::setStartAt, onWater = { viewModel.addWater() },
                    onWeight = { v, u -> viewModel.addWeight(v, u) }, onElectrolyte = { e, mg -> viewModel.addElectrolyte(e, mg) },
                    onTodayWater = { viewModel.addWaterQuietly() },
                    onOpenMuslimHub = onOpenMuslimHub,
                    onOpenWeek = onOpenWeek,
                ),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // Ending is one tap; Undo stays on screen for 10 seconds in case it was a mis-tap.
    // Leaving the Timer drops a pending Undo, so it can't reappear later.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.consumeEnded() } }
    events.ended?.let { ended ->
        // A calm summary with "how do you feel?"; Undo lives inside it during the undo window.
        FastSummarySheet(ended, onUndo = { viewModel.undoEnd(ended) }, onDismiss = viewModel::consumeEnded)
    }
    events.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbar.showSnackbar(context.getString(msg))
            viewModel.consumeMessage()
        }
    }
    events.refeeding?.let { nav ->
        LaunchedEffect(nav) {
            viewModel.consumeRefeeding()
            onOpenRefeeding(nav.tier, nav.fastId)
        }
    }
    events.confirm?.let { reqs ->
        ChecklistDialog(reqs, onConfirm = viewModel::confirmAndStart, onDismiss = viewModel::dismissDialogs)
    }
    events.blocked?.let { reason ->
        InfoDialog(text = stringResource(blockedMessage(reason)), onDismiss = viewModel::dismissDialogs)
    }
    if (events.invalidStart) {
        InfoDialog(text = stringResource(R.string.timer_invalid_start), onDismiss = viewModel::dismissDialogs)
    }
}

/** Everything the Timer screen can ask for. */
class TimerActions(
    val onSelect: (Protocol) -> Unit = {},
    val onSelectCustom: (Int) -> Unit = {},
    val onStart: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onCheckIn: () -> Unit = {},
    val onOpenProgram: () -> Unit = {},
    val onToggleMode: () -> Unit = {},
    val onOpenScreening: () -> Unit = {},
    val onStartAt: (java.time.Instant?) -> Unit = {},
    val onWater: () -> Unit = {},
    val onWeight: (Double, WeightUnit) -> Unit = { _, _ -> },
    val onElectrolyte: (Electrolyte, Double) -> Unit = { _, _ -> },
    /** +250 ml from the Today card's water ring. */
    val onTodayWater: () -> Unit = onWater,
    /** Sunnah & qada hub, from the Ramadan card. */
    val onOpenMuslimHub: () -> Unit = {},
    val onOpenWeek: (LocalDate?) -> Unit = {},
)

@Composable
fun TimerScreen(state: TimerUiState, actions: TimerActions) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            // The app scaffold already pads for the status bar; no second inset here.
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val timer = state.timer
            val active = state.active
            TimerHeader(state)
            RamadanCard(onOpenHub = actions.onOpenMuslimHub)
            RamadanSafetyCard()
            // A gentle cross-fade between fasting and not fasting.
            AnimatedContent(
                targetState = active != null && timer != null,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "timerState",
            ) { fasting ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (fasting && active != null && timer != null) {
                        ExactAlarmBanner()
                        AppCard(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 18.dp)) {
                            TimerRing(active, timer, state.mode, state.now, onToggle = actions.onToggleMode, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                                FactColumn(stringResource(R.string.daily_started), startLabel(active.startAt, state.now), Modifier.weight(1f))
                                FactColumn(stringResource(R.string.daily_goal), active.plannedDuration.hoursMinutes(), Modifier.weight(1f))
                            }
                            // Ending takes one tap (PRD F2: at most 2).
                            PrimaryButton(stringResource(R.string.timer_end), onClick = actions.onEnd, haptic = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton(stringResource(R.string.timer_edit), onClick = actions.onEdit, modifier = Modifier.weight(1f))
                                if (FastPolicy.requiresCheckIns(active.plannedDuration)) {
                                    SecondaryButton(stringResource(R.string.timer_check_in), onClick = actions.onCheckIn, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        CoachingTipCard(active)
                        TodayCard(state.today, actions.onTodayWater)
                        QuickAddRow(!state.hideWeight, state.weightUnit, actions.onWater, actions.onWeight, actions.onElectrolyte)
                        BodyTimelineCard(timer.elapsed)
                    } else {
                        NotFasting(state, actions)
                    }
                }
            }
        }
    }
}

/** Compact header: today's date, a state title and the current streak. */
/**
 * The locale's full date pattern without the year: "EEEE, MMMM d" (en-US), "EEEE d MMMM" (en-GB),
 * "M月d日EEEE" (zh), "EEEE, d MMMM" (ms/in). CJK patterns write the year as "y年"/"y년", so the
 * year marker goes too.
 */
internal fun fullDateWithoutYearPattern(locale: Locale): String {
    val full = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        java.time.format.FormatStyle.FULL, null, java.time.chrono.IsoChronology.INSTANCE, locale,
    )
    return full
        .replace(Regex("y+\\s*[年년]\\s*"), "")
        .replace(Regex("[^A-Za-z'\\u4e00-\\u9fff]*y+[^A-Za-z'\\u4e00-\\u9fff]*$"), "")
        .replace(Regex("[,\\s]*y+[,.\\s]*"), " ")
        .trim()
}

@Composable
private fun TimerHeader(state: TimerUiState) {
    val zone = ZoneId.systemDefault()
    val date = remember(state.now.atZone(zone).toLocalDate()) {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern(fullDateWithoutYearPattern(locale), locale).format(state.now.atZone(zone))
    }
    val title = when {
        state.active != null -> stringResource(R.string.daily_header_fasting)
        state.eatingWindow != null -> stringResource(R.string.eating_title)
        else -> stringResource(R.string.daily_header_ready)
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(date.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        }
        if (state.today.streak > 0) {
            Text(
                pluralStringResource(R.plurals.daily_streak_pill, state.today.streak, state.today.streak),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** "10:17 AM" today, "Yesterday, 8:00 PM", or "Thu 8:00 PM" for older starts. */
@Composable
private fun startLabel(start: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val day = start.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (day) {
        today -> start.formatTime()
        today.minusDays(1) -> stringResource(R.string.daily_yesterday_at, start.formatTime())
        else -> DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(start.atZone(zone)) + " " + start.formatTime()
    }
}

@Composable
private fun FactColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NotFasting(state: TimerUiState, actions: TimerActions) {
    val window = state.eatingWindow
    AppCard(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 22.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (window != null) EatingWindowRing(window, state.now) else ReadyRing(state.selected)
        }
        if (state.maxHours != null) {
            WeekStartPreview(state, actions.onOpenWeek)
        }
    }
    when {
        state.gate == GateResult.BLOCK -> {
            // Education-only: no protocols, no start button; support and the body timeline.
            AppCard { Text(stringResource(R.string.timer_education_only), style = MaterialTheme.typography.bodyLarge) }
            HelplineList()
            BodyTimelineCard(null)
            return
        }
        state.gate == null -> {
            AppCard { Text(stringResource(R.string.timer_needs_screening), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.timer_finish_screening), onClick = actions.onOpenScreening)
            return
        }
        state.gate == GateResult.SOFT && !state.clinicianAcknowledged -> {
            AppCard { Text(stringResource(R.string.timer_locked_doctor), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.timer_confirm_doctor), onClick = actions.onOpenScreening)
            HelplineList()
            return
        }
    }
    FirstFastCard(onStartNow = actions.onStart)
    state.programTargetHours?.let {
        SecondaryButton(stringResource(R.string.timer_program_target, it), onClick = actions.onOpenProgram)
    }
    ProtocolPicker(state, actions)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val selectedEnabled = state.maxHours != null && state.selected.fastHours <= state.maxHours
        PrimaryButton(stringResource(R.string.timer_start), onClick = actions.onStart, enabled = selectedEnabled, haptic = true)
        StartTimeRow(state, actions.onStartAt)
    }
    TodayCard(state.today, actions.onTodayWater)
    QuickAddRow(!state.hideWeight, state.weightUnit, actions.onWater, actions.onWeight, actions.onElectrolyte)
}

@Composable
private fun WeekStartPreview(state: TimerUiState, onOpenWeek: (LocalDate?) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val planned = state.plannedStart
    val reminder = state.startReminder
    val notificationsEnabled = startReminderNotificationsEnabled()
    val zone = ZoneId.systemDefault()
    val day = planned?.atZone(zone)?.toLocalDate()
    val today = state.now.atZone(zone).toLocalDate()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (planned != null) {
            Text(
                stringResource(R.string.week_timer_planned, planned.formatDateTime(locale)),
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
            )
        }
        val reminderText = when {
            reminder != null && reminder == planned && notificationsEnabled -> stringResource(R.string.week_timer_reminder_on)
            reminder != null && notificationsEnabled -> stringResource(R.string.week_timer_reminder, reminder.formatDateTime(locale))
            planned != null || reminder != null -> stringResource(R.string.week_timer_reminder_off)
            else -> null
        }
        reminderText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val action = when (day) {
            null -> R.string.week_timer_plan
            today -> R.string.week_timer_change_today
            else -> R.string.week_timer_change
        }
        PlainButton(stringResource(action), onClick = { onOpenWeek(day) })
    }
}

/** Horizontally scrolling protocol cards, iOS style, then a custom-length stepper. */
@Composable
private fun ProtocolPicker(state: TimerUiState, actions: TimerActions) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        GroupHeader(stringResource(R.string.timer_pick_protocol), Modifier.semantics { heading() })
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Let the cards scroll edge to edge past the screen padding.
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(state.options, key = { it.protocol.id }) { option ->
                val selected = option.protocol.id == state.selected.id
                ProtocolCard(option, selected) { haptics.tick(); actions.onSelect(option.protocol) }
            }
        }
        CustomLengthRow(state, actions.onSelectCustom)
        if (state.options.any { !it.enabled }) {
            Text(
                stringResource(R.string.timer_locked_soft),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun ProtocolCard(option: ProtocolOption, selected: Boolean, onClick: () -> Unit) {
    val p = option.protocol
    val shape = RoundedCornerShape(18.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val sub = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.width(104.dp).heightIn(min = 84.dp).clip(shape).background(bg)
            .then(if (selected) Modifier else Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape))
            .selectable(selected = selected, enabled = option.enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (option.enabled) 1f else 0.4f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(p.name, style = MaterialTheme.typography.titleLarge, color = fg, maxLines = 1)
        Text(stringResource(R.string.daily_protocol_fast, p.fastHours), style = MaterialTheme.typography.bodySmall, color = sub)
        p.eatHours?.let { Text(stringResource(R.string.daily_protocol_eat, it), style = MaterialTheme.typography.bodySmall, color = sub) }
    }
}

/** Ready state ring: the chosen protocol, at rest. */
@Composable
private fun ReadyRing(selected: Protocol) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(232.dp)) {
        ProgressRing(0f, Modifier.fillMaxSize(), stroke = 16.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.week_timer_selected), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(selected.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, maxLines = 1)
            Text(stringResource(R.string.daily_protocol_fast, selected.fastHours), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartTimeRow(state: TimerUiState, onStartAt: (java.time.Instant?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val at = state.startAt
    if (at == null) {
        PlainButton(stringResource(R.string.timer_start_earlier), onClick = { picking = true })
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainButton(stringResource(R.string.timer_starting_at, at.formatDateTime()), onClick = { picking = true })
            PlainButton(stringResource(R.string.timer_start_now), onClick = { onStartAt(null) })
        }
    }
    if (picking) {
        StartEarlierDialog(at, onDismiss = { picking = false }) { onStartAt(it); picking = false }
    }
}

/** While the eating window is open: the remaining time is independent of the planned or rescheduled fasting start. */
@Composable
private fun EatingWindowRing(window: EatingWindow, now: java.time.Instant) {
    val total = Duration.between(window.startedAt, window.endsAt).toMillis().coerceAtLeast(1)
    val left = Duration.between(now, window.endsAt).coerceAtLeast(Duration.ZERO)
    val progress = (Duration.between(window.startedAt, now).toMillis().toFloat() / total).coerceIn(0f, 1f)
    val description = stringResource(R.string.eating_a11y, left.hoursMinutes(), window.endsAt.formatTime())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(232.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        ProgressRing(progress, Modifier.fillMaxSize(), stroke = 16.dp, color = MaterialTheme.colorScheme.tertiary)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text(stringResource(R.string.eating_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.eating_next_in, left.hoursMinutes()), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.eating_next_at, window.endsAt.formatTime()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Custom length: type the hours, or nudge them with − and +. */
@Composable
private fun CustomLengthRow(state: TimerUiState, onSelectCustom: (Int) -> Unit) {
    val max = state.maxHours ?: return
    val hours = if (state.selected.isCustom) state.selected.fastHours else null
    var text by rememberSaveable(hours) { mutableStateOf(hours?.toString().orEmpty()) }
    val decrease = stringResource(R.string.a11y_decrease)
    val increase = stringResource(R.string.a11y_increase)
    val typed = text.toIntOrNull()
    val error = text.isNotEmpty() && (typed == null || typed !in 1..max)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.timer_custom), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                StepButton("−", decrease) { onSelectCustom(((hours ?: 16) - 1).coerceIn(1, max)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter(Char::isDigit).take(3)
                        text.toIntOrNull()?.takeIf { it in 1..max }?.let(onSelectCustom)
                    },
                    singleLine = true,
                    isError = error,
                    label = { Text(stringResource(R.string.timer_custom_hours)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.width(84.dp),
                )
                StepButton("+", increase) { onSelectCustom(((hours ?: 16) + 1).coerceIn(1, max)) }
            }
            if (error) Text(stringResource(R.string.timer_custom_range, max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Evidence-graded body timeline (PRD F9). Every claim shows its label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyTimelineCard(elapsed: Duration?) {
    AppCard {
        Text(stringResource(R.string.timeline_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.timeline_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val current = elapsed?.let(BodyTimeline::stageAt)
        BodyTimeline.stages.forEach { stage ->
            val isCurrent = stage == current
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape)
                        .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${com.fastingshift.app.ui.components.hoursLabel(stage.fromHours.toLong())} · ${stage.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    stage.claims.forEach { c ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EvidenceChip(c.label)
                            Text(c.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** The evidence grade as a small coloured pill; the text itself carries the meaning. */
@Composable
private fun EvidenceChip(label: EvidenceLabel) {
    val (bg, fg) = when (label) {
        EvidenceLabel.ESTABLISHED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        EvidenceLabel.EMERGING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        EvidenceLabel.ANIMAL_ONLY, EvidenceLabel.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label.display,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Asks for exact alarms in context, only while a fast is running and only if denied. */
@Composable
private fun ExactAlarmBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    if (alarmManager.canScheduleExactAlarms()) return
    AppCard {
        Text(stringResource(R.string.exact_alarm_banner), style = MaterialTheme.typography.bodyMedium)
        SecondaryButton(stringResource(R.string.exact_alarm_allow), onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        })
    }
}

@Composable
private fun InfoDialog(text: String, onDismiss: () -> Unit, title: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = if (title != null) { { Text(title) } } else null,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

private fun blockedMessage(reason: BlockReason): Int = when (reason) {
    BlockReason.EDUCATION_ONLY -> R.string.timer_education_only
    BlockReason.NEEDS_CLINICIAN_ACKNOWLEDGEMENT -> R.string.timer_locked_doctor
    BlockReason.NEEDS_SCREENING -> R.string.timer_needs_screening
    BlockReason.EXCEEDS_GATED_LIMIT, BlockReason.OUT_OF_RANGE -> R.string.timer_locked_soft
}
