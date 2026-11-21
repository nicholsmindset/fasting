package com.fastingshift.app.ui.timer

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.firstweek.CoachingTipCard
import com.fastingshift.app.ui.firstweek.FastSummarySheet
import com.fastingshift.app.ui.firstweek.FirstFastCard
import com.fastingshift.app.ui.ramadan.RamadanCard
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.components.formatTime
import com.fastingshift.app.ui.components.hoursMinutes
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
import com.fastingshift.domain.timer.toClockString
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TimerRoute(
    onEditFast: (Long) -> Unit,
    onOpenProgram: () -> Unit,
    onOpenCheckIn: (Long) -> Unit,
    onOpenRefeeding: (RefeedingTier, Long) -> Unit,
    onOpenScreening: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
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
)

@Composable
fun TimerScreen(state: TimerUiState, actions: TimerActions) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            // The app scaffold already pads for the status bar; no second inset here.
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val timer = state.timer
            val active = state.active
            RamadanCard()
            if (active != null && timer != null) {
                ExactAlarmBanner()
                TimerRing(active, timer, state.mode, state.now, onToggle = actions.onToggleMode, modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.timer_goal, active.plannedDuration.hoursMinutes()), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.timer_started_at, active.startAt.formatDateTime()), style = MaterialTheme.typography.bodySmall)
                // Ending takes one tap (PRD F2: at most 2).
                Button(onClick = actions.onEnd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_end)) }
                OutlinedButton(onClick = actions.onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_edit)) }
                if (FastPolicy.requiresCheckIns(active.plannedDuration)) {
                    OutlinedButton(onClick = actions.onCheckIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_check_in)) }
                }
                CoachingTipCard(active)
                QuickAddRow(!state.hideWeight, state.weightUnit, actions.onWater, actions.onWeight, actions.onElectrolyte)
                BodyTimelineCard(timer.elapsed)
            } else {
                NotFasting(state, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotFasting(state: TimerUiState, actions: TimerActions) {
    val window = state.eatingWindow
    if (window != null) {
        EatingWindowRing(window, state.now)
    } else {
        Text(stringResource(R.string.timer_not_fasting), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
    }
    when {
        state.gate == GateResult.BLOCK -> {
            // Education-only: no protocols, no start button; support and the body timeline.
            Text(stringResource(R.string.timer_education_only), textAlign = TextAlign.Center)
            HelplineList()
            BodyTimelineCard(null)
            return
        }
        state.gate == null -> {
            Text(stringResource(R.string.timer_needs_screening), textAlign = TextAlign.Center)
            Button(onClick = actions.onOpenScreening, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_finish_screening)) }
            return
        }
        state.gate == GateResult.SOFT && !state.clinicianAcknowledged -> {
            Text(stringResource(R.string.timer_locked_doctor), textAlign = TextAlign.Center)
            Button(onClick = actions.onOpenScreening, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_confirm_doctor)) }
            HelplineList()
            return
        }
    }
    FirstFastCard(onStartNow = actions.onStart)
    state.programTargetHours?.let {
        OutlinedButton(onClick = actions.onOpenProgram, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_program_target, it)) }
    }
    Text(stringResource(R.string.timer_pick_protocol), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        state.options.forEach { option ->
            FilterChip(
                selected = option.protocol == state.selected,
                onClick = { actions.onSelect(option.protocol) },
                enabled = option.enabled,
                label = { Text(option.protocol.name) },
            )
        }
    }
    CustomLengthRow(state, actions.onSelectCustom)
    if (state.options.any { !it.enabled }) {
        Text(stringResource(R.string.timer_locked_soft), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
    StartTimeRow(state, actions.onStartAt)
    val selectedEnabled = state.maxHours != null && state.selected.fastHours <= state.maxHours
    Button(onClick = actions.onStart, enabled = selectedEnabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.timer_start))
    }
    HorizontalDivider()
    QuickAddRow(!state.hideWeight, state.weightUnit, actions.onWater, actions.onWeight, actions.onElectrolyte)
}

@Composable
private fun StartTimeRow(state: TimerUiState, onStartAt: (java.time.Instant?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val at = state.startAt
    if (at == null) {
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.timer_start_earlier)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picking = true }) { Text(stringResource(R.string.timer_starting_at, at.formatDateTime())) }
            TextButton(onClick = { onStartAt(null) }) { Text(stringResource(R.string.timer_start_now)) }
        }
    }
    if (picking) {
        StartEarlierDialog(at, onDismiss = { picking = false }) { onStartAt(it); picking = false }
    }
}

/** While the eating window is open: how long until the next fast, and at what time. */
@Composable
private fun EatingWindowRing(window: EatingWindow, now: java.time.Instant) {
    val total = Duration.between(window.startedAt, window.endsAt).toMillis().coerceAtLeast(1)
    val left = Duration.between(now, window.endsAt).coerceAtLeast(Duration.ZERO)
    val progress = (Duration.between(window.startedAt, now).toMillis().toFloat() / total).coerceIn(0f, 1f)
    val description = stringResource(R.string.eating_a11y, left.hoursMinutes(), window.endsAt.formatTime())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(220.dp),
            strokeWidth = 12.dp,
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text(stringResource(R.string.eating_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.eating_next_in, left.hoursMinutes()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.eating_next_at, window.endsAt.formatTime()), style = MaterialTheme.typography.bodyMedium)
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.timer_custom))
            OutlinedButton(onClick = { onSelectCustom(((hours ?: 16) - 1).coerceIn(1, max)) }) { Text("−", Modifier.semantics { contentDescription = decrease }) }
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
                modifier = Modifier.width(96.dp),
            )
            OutlinedButton(onClick = { onSelectCustom(((hours ?: 16) + 1).coerceIn(1, max)) }) { Text("+", Modifier.semantics { contentDescription = increase }) }
        }
        if (error) Text(stringResource(R.string.timer_custom_range, max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Evidence-graded body timeline (PRD F9). Every claim shows its label. */
@Composable
private fun BodyTimelineCard(elapsed: Duration?) {
    SectionCard(title = stringResource(R.string.timeline_title)) {
        Text(stringResource(R.string.timeline_note), style = MaterialTheme.typography.bodySmall)
        val current = elapsed?.let(BodyTimeline::stageAt)
        BodyTimeline.stages.forEach { stage ->
            val isCurrent = stage == current
            Text(
                "${stage.fromHours}h · ${stage.title}",
                style = if (isCurrent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            stage.claims.forEach { c ->
                Text("[${c.label.display}] ${c.text}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Asks for exact alarms in context, only while a fast is running and only if denied. */
@Composable
private fun ExactAlarmBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    if (alarmManager.canScheduleExactAlarms()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.exact_alarm_banner))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }) { Text(stringResource(R.string.exact_alarm_allow)) }
        }
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
