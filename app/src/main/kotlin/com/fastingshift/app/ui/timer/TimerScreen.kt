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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fastingshift.app.ui.components.hoursMinutes
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import androidx.compose.foundation.layout.Row
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.app.ui.components.formatDateTime
import com.fastingshift.app.ui.safety.HelplineList
import com.fastingshift.domain.safety.BlockReason
import com.fastingshift.domain.safety.FastPolicy
import com.fastingshift.domain.timeline.BodyTimeline
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.safety.StartRequirement
import com.fastingshift.domain.timer.toClockString

@Composable
fun TimerRoute(
    onEditFast: (Long) -> Unit,
    onOpenProgram: () -> Unit,
    onOpenCheckIn: (Long) -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    // Notification permission is requested in context: when the user starts a fast.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val onStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start()
    }

    TimerScreen(
        state = state, onSelect = viewModel::select, onSelectCustom = viewModel::selectCustom, onStart = onStart, onEnd = viewModel::end,
        onEdit = { state.active?.let { onEditFast(it.id) } }, onCheckIn = { state.active?.let { onOpenCheckIn(it.id) } },
        onOpenProgram = onOpenProgram,
    )

    events.confirm?.let { reqs ->
        ChecklistDialog(reqs, onConfirm = viewModel::confirmAndStart, onDismiss = viewModel::dismissEvents)
    }
    events.blocked?.let { reason ->
        InfoDialog(text = stringResource(blockedMessage(reason)), onDismiss = viewModel::dismissEvents)
    }
    events.ended?.let { ended ->
        val duration = ended.actualDuration?.toClockString() ?: ""
        val tier = events.refeeding
        InfoDialog(
            title = if (tier != null) stringResource(R.string.refeed_title) else null,
            text = stringResource(R.string.timer_ended, duration) + (tier?.let { "\n\n" + stringResource(refeedingText(it)) } ?: ""),
            onDismiss = viewModel::dismissEvents,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    state: TimerUiState,
    onSelect: (com.fastingshift.domain.model.Protocol) -> Unit,
    onSelectCustom: (Int) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
    onOpenProgram: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val timer = state.timer
            val active = state.active
            if (active != null && timer != null) {
                ExactAlarmBanner()
                val ringDescription = stringResource(
                    R.string.a11y_timer,
                    timer.elapsed.hoursMinutes(),
                    active.plannedDuration.hoursMinutes(),
                    (timer.progress * 100).toInt(),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(top = 24.dp).clearAndSetSemantics {
                        contentDescription = ringDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(timer.progress, 0f..1f)
                    },
                ) {
                    CircularProgressIndicator(
                        progress = { timer.progress },
                        modifier = Modifier.size(260.dp),
                        strokeWidth = 14.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.timer_elapsed), style = MaterialTheme.typography.labelLarge)
                        Text(timer.elapsed.toClockString(), style = MaterialTheme.typography.displaySmall)
                        Text(
                            if (timer.isTargetReached) stringResource(R.string.timer_goal_reached, timer.pastTarget.toClockString())
                            else stringResource(R.string.timer_remaining, timer.remaining.toClockString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(stringResource(R.string.timer_goal, "${active.plannedDuration.toHours()}h"))
                Text(stringResource(R.string.timer_started_at, active.startAt.formatDateTime()), style = MaterialTheme.typography.bodySmall)
                // Ending takes one tap (PRD F2: at most 2).
                Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_end)) }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_edit)) }
                if (FastPolicy.requiresCheckIns(active.plannedDuration)) {
                    OutlinedButton(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_check_in)) }
                }
                BodyTimelineCard(timer.elapsed)
            } else {
                Text(stringResource(R.string.timer_not_fasting), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
                if (state.gate == GateResult.BLOCK) {
                    Text(stringResource(R.string.timer_education_only), textAlign = TextAlign.Center)
                    HelplineList()
                    BodyTimelineCard(null)
                    return@Column
                }
                state.programTargetHours?.let {
                    OutlinedButton(onClick = onOpenProgram, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.timer_program_target, it)) }
                }
                Text(stringResource(R.string.timer_pick_protocol), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.options.forEach { option ->
                        FilterChip(
                            selected = option.protocol == state.selected,
                            onClick = { onSelect(option.protocol) },
                            enabled = option.enabled,
                            label = { Text(option.protocol.name) },
                        )
                    }
                }
                CustomLengthRow(state, onSelectCustom)
                if (state.options.any { !it.enabled }) {
                    val msg = if (state.gate == GateResult.SOFT) R.string.timer_locked_soft else R.string.timer_locked_screening
                    Text(stringResource(msg), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                val selectedEnabled = state.maxHours != null && state.selected.fastHours <= state.maxHours
                Button(onClick = onStart, enabled = selectedEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.timer_start))
                }
            }
        }
    }
}

@Composable
private fun CustomLengthRow(state: TimerUiState, onSelectCustom: (Int) -> Unit) {
    val max = state.maxHours ?: return
    val hours = if (state.selected.isCustom) state.selected.fastHours else null
    val decrease = stringResource(R.string.a11y_decrease)
    val increase = stringResource(R.string.a11y_increase)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.timer_custom))
        OutlinedButton(onClick = { onSelectCustom(((hours ?: 16) - 1).coerceAtLeast(1)) }) { Text("−", Modifier.semantics { contentDescription = decrease }) }
        Text(hours?.let { "${it}h" } ?: "—")
        OutlinedButton(onClick = { onSelectCustom(((hours ?: 16) + 1).coerceAtMost(max)) }) { Text("+", Modifier.semantics { contentDescription = increase }) }
    }
}

/** Evidence-graded body timeline (PRD F9). Every claim shows its label. */
@Composable
private fun BodyTimelineCard(elapsed: java.time.Duration?) {
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
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                )
            }) { Text(stringResource(R.string.exact_alarm_allow)) }
        }
    }
}

@Composable
private fun ChecklistDialog(requirements: Set<StartRequirement>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checklist_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (StartRequirement.EXTENDED_FAST_CHECKLIST in requirements) {
                    listOf(
                        R.string.checklist_hydration, R.string.checklist_electrolytes, R.string.checklist_medications,
                        R.string.checklist_contact, R.string.checklist_redflags,
                    ).forEach { Text("• " + stringResource(it)) }
                }
                if (StartRequirement.OVER_72H_ACKNOWLEDGEMENT in requirements) {
                    Text(stringResource(R.string.checklist_over72), style = MaterialTheme.typography.bodyMedium)
                }
                if (StartRequirement.MEDICAL_DISCLAIMER in requirements) {
                    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.checklist_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
    BlockReason.NEEDS_CLINICIAN_ACKNOWLEDGEMENT -> R.string.timer_needs_clinician
    BlockReason.EXCEEDS_GATED_LIMIT, BlockReason.OUT_OF_RANGE -> R.string.timer_locked_screening
}

private fun refeedingText(tier: RefeedingTier): Int = when (tier) {
    RefeedingTier.STANDARD -> R.string.refeed_standard
    RefeedingTier.EXTENDED -> R.string.refeed_extended
    RefeedingTier.PROLONGED -> R.string.refeed_prolonged
}
