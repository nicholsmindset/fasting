package com.fastingshift.app.ui.timer

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.components.startReminderNotificationsEnabled
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.TabularNumbers
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.NicholsMark
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.partner.PartnerTimerEntry
import com.fastingshift.app.ui.firstweek.CoachingTipCard
import com.fastingshift.app.ui.firstweek.FastSummarySheet
import com.fastingshift.app.ui.firstweek.FirstFastCard
import com.fastingshift.app.ui.ramadan.GroupedSheet
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
import com.fastingshift.domain.timer.TimerDisplayMode
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
    onOpenReminders: () -> Unit = {},
    onOpenPartner: (() -> Unit)? = null,
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        val state = loaded
        if (state == null) {
            LoadingBox(Modifier.padding(top = 120.dp))
        } else {
            TimerScreen(
                state = state,
                revival = { com.fastingshift.app.ui.revival.RevivalCard() },
                actions = TimerActions(
                    onSelect = viewModel::select, onSelectCustom = viewModel::selectCustom, onStart = { viewModel.start() }, onEnd = viewModel::end,
                    onEdit = { state.active?.let { onEditFast(it.id) } }, onCheckIn = { state.active?.let { onOpenCheckIn(it.id) } },
                    onOpenProgram = onOpenProgram, onSetMode = viewModel::setMode, onOpenScreening = onOpenScreening,
                    onStartAt = viewModel::setStartAt, onWater = { viewModel.addWater() },
                    onWeight = { v, u -> viewModel.addWeight(v, u) }, onElectrolyte = { e, mg -> viewModel.addElectrolyte(e, mg) },
                    onOpenMuslimHub = onOpenMuslimHub,
                    onOpenWeek = onOpenWeek,
                    onOpenReminders = onOpenReminders,
                    onOpenPartner = onOpenPartner,
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
    val onSetMode: (TimerDisplayMode) -> Unit = {},
    val onOpenScreening: () -> Unit = {},
    val onStartAt: (java.time.Instant?) -> Unit = {},
    val onWater: () -> Unit = {},
    val onWeight: (Double, WeightUnit) -> Unit = { _, _ -> },
    val onElectrolyte: (Electrolyte, Double) -> Unit = { _, _ -> },
    /** Sunnah & qada hub, from the Ramadan card. */
    val onOpenMuslimHub: () -> Unit = {},
    val onOpenWeek: (LocalDate?) -> Unit = {},
    val onOpenReminders: () -> Unit = {},
    val onOpenPartner: (() -> Unit)? = null,
)

@Composable
fun TimerScreen(state: TimerUiState, actions: TimerActions, revival: @Composable () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val timer = state.timer
                val active = state.active
                TimerHeader(state)
                RamadanSafetyCard()
                AnimatedContent(
                    targetState = active != null && timer != null,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "timerState",
                ) { fasting ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (fasting && active != null && timer != null) {
                            TimerRing(active, timer, state.mode, state.now, onSetMode = actions.onSetMode)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                FactColumn(stringResource(R.string.daily_started), startLabel(active.startAt, state.now), Modifier.weight(1f))
                                FactColumn(stringResource(R.string.daily_goal), active.plannedDuration.hoursMinutes(), Modifier.weight(1f))
                            }
                            Column(Modifier.fillMaxWidth()) {
                                PrimaryButton(stringResource(R.string.timer_end), onClick = actions.onEnd, haptic = true)
                                FlowActions {
                                    TextButton(onClick = actions.onEdit) { Text(stringResource(R.string.timer_edit)) }
                                    if (FastPolicy.requiresCheckIns(active.plannedDuration)) {
                                        TextButton(onClick = actions.onCheckIn) { Text(stringResource(R.string.timer_check_in)) }
                                    }
                                }
                            }
                            actions.onOpenPartner?.let { PartnerTimerEntry(it) }
                            revival()
                            ExactAlarmBanner()
                            RamadanCard(onOpenHub = actions.onOpenMuslimHub)
                            CoachingTipCard(active)
                            TodayCard(state.today, actions.onWater)
                            QuickAddRow(!state.hideWeight, state.weightUnit, actions.onWeight, actions.onElectrolyte)
                            BodyTimelineCard(timer.elapsed)
                        } else {
                            NotFasting(state, actions, revival)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowActions(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

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
        else -> stringResource(R.string.timer_idle_title)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NicholsMark(Modifier.size(26.dp), compact = true)
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
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
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
    }
}

@Composable
private fun NotFasting(state: TimerUiState, actions: TimerActions, revival: @Composable () -> Unit) {
    // Place any required screening action before the routine or time controls.
    when {
        state.gate == GateResult.BLOCK -> {
            // Education-only: no protocols, no start button; support and the body timeline.
            AppCard { Text(stringResource(R.string.timer_education_only), style = MaterialTheme.typography.bodyLarge) }
            RamadanCard(onOpenHub = actions.onOpenMuslimHub)
            HelplineList()
            BodyTimelineCard(null)
            return
        }
        state.gate == null -> {
            AppCard { Text(stringResource(R.string.timer_needs_screening), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.timer_finish_screening), onClick = actions.onOpenScreening)
            RamadanCard(onOpenHub = actions.onOpenMuslimHub)
            return
        }
        state.gate == GateResult.SOFT && !state.clinicianAcknowledged -> {
            AppCard { Text(stringResource(R.string.timer_locked_doctor), style = MaterialTheme.typography.bodyLarge) }
            PrimaryButton(stringResource(R.string.timer_confirm_doctor), onClick = actions.onOpenScreening)
            RamadanCard(onOpenHub = actions.onOpenMuslimHub)
            HelplineList()
            return
        }
    }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val window = state.eatingWindow
    if (window != null) {
        EatingWindowReadout(window, state.now)
    }
    val selectedEnabled = state.maxHours != null && state.selected.fastHours <= state.maxHours
    SelectedFast(state.selected, onChoose = { choosing = true })
    if (selectedEnabled) {
        val format = rememberWeekFormat()
        Text(stringResource(R.string.plan_target_time, format.dateTime.format(state.selectedTargetEnd.atZone(ZoneId.systemDefault()))),
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
    }
    Column(Modifier.fillMaxWidth()) {
        PrimaryButton(stringResource(R.string.timer_start), onClick = actions.onStart, enabled = selectedEnabled, haptic = true)
        StartTimeRow(state, actions.onStartAt)
    }
    actions.onOpenPartner?.let { PartnerTimerEntry(it) }
    revival()
    val alertsAvailable = startReminderNotificationsEnabled()
    if (state.maxHours != null) WeekStartPreview(state, actions.onOpenWeek, alertsAvailable)
    RamadanCard(onOpenHub = actions.onOpenMuslimHub)
    FirstFastCard(state.now, state.eveningOffer, state.startRemindersEnabled && alertsAvailable, actions.onOpenReminders)
    state.programTargetHours?.let {
        SecondaryButton(stringResource(R.string.timer_program_target, it), onClick = actions.onOpenProgram)
    }
    if (choosing) ProtocolPicker(state, actions) { choosing = false }
    TodayCard(state.today, actions.onWater)
    QuickAddRow(!state.hideWeight, state.weightUnit, actions.onWeight, actions.onElectrolyte)
}

@Composable
private fun WeekStartPreview(state: TimerUiState, onOpenWeek: (LocalDate?) -> Unit, notificationsEnabled: Boolean) {
    val format = rememberWeekFormat()
    val planned = state.plannedStart
    val reminder = state.startReminder
    val zone = ZoneId.systemDefault()
    val day = planned?.atZone(zone)?.toLocalDate()
    val today = state.now.atZone(zone).toLocalDate()
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        state.earlierPlannedStartToday?.atZone(zone)?.let { earlier ->
            TextButton(
                onClick = { onOpenWeek(earlier.toLocalDate()) },
                modifier = Modifier.testTag("timer-move-missed-start"),
            ) { Text(stringResource(R.string.week_timer_move_earlier_today, format.time.format(earlier))) }
        }
        if (planned != null) {
            Text(
                stringResource(R.string.week_timer_planned, format.dateTime.format(planned.atZone(zone))),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp),
            )
        }
        val reminderText = when {
            reminder != null && reminder == planned && notificationsEnabled -> stringResource(R.string.week_timer_reminder_on)
            reminder != null && notificationsEnabled -> stringResource(R.string.week_timer_reminder, format.dateTime.format(reminder.atZone(zone)))
            planned != null || reminder != null -> stringResource(R.string.week_timer_reminder_off)
            else -> null
        }
        reminderText?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val action = when (day) {
            null -> R.string.week_timer_plan
            today -> R.string.week_timer_change_today
            else -> R.string.week_timer_change
        }
        TextButton(onClick = { onOpenWeek(day) }) { Text(stringResource(action)) }
    }
}

/** Presets and the custom length are available together when the user changes the plan. */
@Composable
private fun ProtocolPicker(state: TimerUiState, actions: TimerActions, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    GroupedSheet(stringResource(R.string.timer_pick_protocol), onDismiss = onDismiss) {
        CustomLengthRow(state, actions.onSelectCustom)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            state.options.forEach { option ->
                val p = option.protocol
                val selected = p.id == state.selected.id
                val detail = p.eatHours?.let {
                    stringResource(R.string.daily_protocol_fast, p.fastHours) + " · " + stringResource(R.string.daily_protocol_eat, it)
                } ?: stringResource(R.string.daily_protocol_fast, p.fastHours)
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected, enabled = option.enabled, role = Role.RadioButton) {
                            haptics.tick()
                            actions.onSelect(p)
                            onDismiss()
                        }
                        .heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = option.enabled)
                    Column {
                        Text(p.name, style = MaterialTheme.typography.titleMedium,
                            color = if (option.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (state.options.any { !it.enabled }) {
            Text(stringResource(R.string.timer_locked_soft), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedFast(selected: Protocol, onChoose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.week_timer_selected), modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onChoose) { Text(stringResource(R.string.timer_pick_protocol)) }
        }
        Text(selected.name, style = MaterialTheme.typography.displayMedium.merge(TabularNumbers), color = MaterialTheme.colorScheme.onSurface)
        val eating = selected.eatHours
        val fastingLabel = stringResource(R.string.daily_protocol_fast, selected.fastHours)
        if (eating != null && eating > 0) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(selected.fastHours.toFloat()).height(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Box(Modifier.weight(eating.toFloat()).height(6.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
            Text(fastingLabel + " · " + stringResource(R.string.daily_protocol_eat, eating), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(fastingLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartTimeRow(state: TimerUiState, onStartAt: (java.time.Instant?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val at = state.startAt
    FlowActions {
        if (at == null) {
            TextButton(onClick = { picking = true }) { Text(stringResource(R.string.timer_start_earlier)) }
        } else {
            val start = rememberWeekFormat().dateTime.format(at.atZone(ZoneId.systemDefault()))
            TextButton(onClick = { picking = true }) { Text(stringResource(R.string.timer_starting_at, start)) }
            TextButton(onClick = { onStartAt(null) }) { Text(stringResource(R.string.timer_start_now)) }
        }
    }
    if (picking) {
        StartEarlierDialog(at, state.now, onDismiss = { picking = false }, onPick = { onStartAt(it); picking = false })
    }
}

/** While the eating window is open: the remaining time is independent of the planned or rescheduled fasting start. */
@Composable
private fun EatingWindowReadout(window: EatingWindow, now: Instant) {
    val left = Duration.between(now, window.endsAt).coerceAtLeast(Duration.ZERO)
    val description = stringResource(R.string.eating_a11y, left.hoursMinutes(), window.endsAt.formatTime())
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Text(stringResource(R.string.eating_next_in, left.hoursMinutes()), style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers))
        Text(stringResource(R.string.eating_next_at, window.endsAt.formatTime()), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.timer_custom), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−", decrease) { onSelectCustom(((hours ?: 16) - 1).coerceIn(1, max)) }
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter(Char::isDigit).take(3)
                    text.toIntOrNull()?.takeIf { it in 1..max }?.let(onSelectCustom)
                },
                singleLine = true, isError = error, label = { Text(stringResource(R.string.timer_custom_hours)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f),
            )
            StepButton("+", increase) { onSelectCustom(((hours ?: 16) + 1).coerceIn(1, max)) }
        }
        if (error) Text(stringResource(R.string.timer_custom_range, max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StepButton(glyph: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
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
