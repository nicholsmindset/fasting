package com.fastingshift.app.ui.firstweek

import com.fastingshift.app.share.ShareCards
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupHeader
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.partner.PartnerReflectionInvite
import com.fastingshift.app.ui.onboarding.HeroIcon
import com.fastingshift.app.ui.onboarding.TextFieldGroupRow
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import java.time.Instant
import com.fastingshift.domain.model.Protocol
import com.fastingshift.app.ui.ramadan.GroupedSheet
import androidx.compose.ui.platform.testTag
import java.time.ZoneId
import kotlinx.coroutines.launch

/*
 * First-week journey. These are the stable entry points the Timer screen calls; each decides on
 * its own (through its view model) whether to show anything.
 */

/** Editing a dated plan never starts the timer and does not require notifications. */
@Composable
fun FirstFastCard(
    now: Instant,
    selected: Protocol,
    plan: FirstPlanWindow?,
    remindersAvailable: Boolean,
    onOpenReminders: () -> Unit,
    vm: FirstFastViewModel = hiltViewModel(),
    onStartNow: (() -> Unit)? = null,
) {
    val result by vm.result.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    val zone = ZoneId.systemDefault()
    val busy = result == FirstPlanEditResult.BUSY
    val savedStart = plan?.start?.atZone(zone)
    val openEditor = {
        draftEpoch = savedStart?.toEpochSecond()
        vm.clearResult()
        editing = true
    }
    LaunchedEffect(result) {
        if (result == FirstPlanEditResult.SAVED) {
            editing = false
            vm.clearResult()
        }
    }
    AppCard(
        modifier = if (plan == null) Modifier else Modifier.testTag("first-plan-saved"),
    ) {
        Text(stringResource(if (plan == null) R.string.first_fast_title else R.string.first_plan_home_title),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (plan != null && savedStart != null) FirstWindowTimes(savedStart, plan.protocol)
        if (plan == null) {
            SecondaryButton(stringResource(R.string.first_plan_choose_time), onClick = openEditor, enabled = !busy)
        } else {
            val upcoming = plan.start > now
            if (!upcoming) Text(stringResource(R.string.first_plan_time_passed), style = MaterialTheme.typography.bodyMedium)
            if (upcoming || onStartNow == null) {
                PrimaryButton(stringResource(R.string.first_plan_edit_time), onClick = openEditor, enabled = !busy)
                onStartNow?.let { SecondaryButton(stringResource(R.string.first_plan_start), it) }
            } else {
                PrimaryButton(stringResource(R.string.first_plan_start), onStartNow, haptic = true)
                SecondaryButton(stringResource(R.string.first_plan_edit_time), openEditor, enabled = !busy)
            }
            if (upcoming) {
                Text(stringResource(if (remindersAvailable) R.string.week_timer_reminder_on else R.string.week_timer_reminder_off),
                    style = MaterialTheme.typography.bodyMedium)
                if (!remindersAvailable) PlainButton(stringResource(R.string.settings_reminders), onClick = onOpenReminders)
                AddPlanToCalendar(plan)
            }
        }
    }
    if (editing) {
        val draft = draftEpoch?.let { Instant.ofEpochSecond(it).atZone(zone) }
        GroupedSheet(stringResource(if (plan == null) R.string.first_fast_title else R.string.first_plan_edit_time),
            onDismiss = { if (!busy) editing = false }, dismissLabel = R.string.cancel) {
            FirstPlanTimeFields(now, selected, draft, onPick = { picked ->
                draftEpoch = picked.toEpochSecond()
                vm.clearResult()
            })
            val problem = when (result) {
                FirstPlanEditResult.COVERED -> R.string.first_plan_covered
                FirstPlanEditResult.UNAVAILABLE -> R.string.first_plan_unavailable
                FirstPlanEditResult.FAILED -> R.string.first_plan_save_failed
                else -> null
            }
            if (problem != null) Text(stringResource(problem), color = MaterialTheme.colorScheme.error)
            PrimaryButton(stringResource(R.string.first_plan_save_window),
                onClick = { if (draft != null) vm.save(selected, draft) },
                enabled = !busy && draft != null && draft.toInstant() > now)
            if (plan != null) TextButton(onClick = vm::cancelPlan, enabled = !busy) {
                Text(stringResource(R.string.first_fast_cancel_plan))
            }
        }
    }
}

/** The current coaching tip during one of the user's first fasts (nothing otherwise). */
@Composable
fun CoachingTipCard(fast: Fast, vm: CoachingViewModel = hiltViewModel()) {
    LaunchedEffect(fast) { vm.bind(fast) }
    val tip by vm.tip.collectAsStateWithLifecycle()
    var confirmHide by rememberSaveable { mutableStateOf(false) }
    AnimatedVisibility(tip != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(tip, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { t ->
                if (t == null) return@AnimatedContent
                Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(end = 10.dp)) {
                        Icon(tipIcon(t), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.coach_card_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(Notifications.coachingTitle(t)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(stringResource(Notifications.coachingBody(t)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    PlainButton(stringResource(R.string.coach_hide), onClick = { confirmHide = true }, modifier = Modifier.align(Alignment.End))
                }
            }
        }
    }
    if (confirmHide) {
        AlertDialog(
            onDismissRequest = { confirmHide = false },
            title = { Text(stringResource(R.string.coach_hide_title)) },
            text = { Text(stringResource(R.string.coach_hide_body)) },
            confirmButton = {
                TextButton(onClick = { confirmHide = false; vm.hideTips() }) { Text(stringResource(R.string.coach_hide_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmHide = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun tipIcon(t: CoachingTip) = when (t) {
    CoachingTip.HUNGER_WAVES -> Icons.Filled.Info
    CoachingTip.HYDRATE -> Icons.Filled.Favorite
    CoachingTip.ELECTROLYTES -> Icons.Filled.Info
    CoachingTip.BREAK_GENTLY -> Icons.Filled.ThumbUp
}

private val moodLabels = listOf(R.string.mood_1, R.string.mood_2, R.string.mood_3, R.string.mood_4, R.string.mood_5)

/** The timer is already saved. Reflection and corrections are optional. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FastSummarySheet(
    fast: Fast,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    vm: FastSummaryViewModel = hiltViewModel(),
    onEdit: (() -> Unit)? = null,
    undoFailed: Boolean = false,
    onOpenPartner: (() -> Unit)? = null,
) {
    LaunchedEffect(fast.id, fast.endedAt) { vm.bind(fast) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val saveFailed by vm.saveFailed.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val edited = mood != fast.mood || note != fast.note.orEmpty()
    val close: (() -> Unit) -> Unit = { then -> scope.launch { sheet.hide(); then() } }
    val finish: (() -> Unit) -> Unit = { then ->
        if (edited) vm.save { close(then) } else close(then)
    }
    val format = rememberWeekFormat().dateTime
    val zone = ZoneId.systemDefault()
    val completed = fast.status == FastStatus.COMPLETED
    var confirmDiscard by rememberSaveable(fast.id) { mutableStateOf(false) }
    LaunchedEffect(confirmDiscard) { if (confirmDiscard) sheet.show() }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.today_discard_title)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val safeToShare = completed && ui.fastId == fast.id && ui.noRedFlags
    ModalBottomSheet(onDismissRequest = { if (edited) confirmDiscard = true else onDismiss() }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroIcon(Icons.Filled.CheckCircle, size = 48.dp)
                Text(stringResource(if (completed) R.string.summary_title else R.string.summary_title_early),
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                fast.actualDuration?.let { Text(shortLength(it), style = MaterialTheme.typography.titleLarge) }
            }
            InsetGroup {
                GroupRow(stringResource(R.string.daily_started), subtitle = format.format(fast.startAt.atZone(zone)),
                    modifier = Modifier.testTag("summary-start"))
                fast.endedAt?.let { end ->
                    GroupDivider()
                    GroupRow(stringResource(R.string.edit_end), subtitle = format.format(end.atZone(zone)),
                        modifier = Modifier.testTag("summary-end"))
                }
                onEdit?.let {
                    GroupDivider()
                    GroupRow(stringResource(R.string.timer_edit), showChevron = true, onClick = { finish(it) })
                }
            }
            if (undoFailed) Text(stringResource(R.string.timer_undo_failed), color = MaterialTheme.colorScheme.error)
            if (ui.fastId == fast.id && ui.canUndo && !undoFailed) {
                PlainButton(stringResource(R.string.summary_undo), onClick = onUndo)
            }
            if (!edited) PrimaryButton(stringResource(R.string.today_back), onClick = { finish(onDismiss) })
            Column {
                GroupHeader(stringResource(R.string.summary_mood_title))
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    moodLabels.forEachIndexed { i, label ->
                        val value = i + 1
                        val selected = mood == value
                        val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "mood")
                        val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "moodText")
                        Box(
                            Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.tick(); vm.setMood(value) })
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = fg) }
                    }
                }
            }
            InsetGroup {
                TextFieldGroupRow(stringResource(R.string.summary_note), note, vm::setNote, keyboardType = KeyboardType.Text, singleLine = false)
                if (ui.fastId == fast.id && ui.waterMl > 0) {
                    GroupDivider()
                    GroupRow(stringResource(R.string.summary_water), value = if (ui.waterMl >= 1_000) {
                        stringResource(R.string.summary_water_l, ui.waterMl / 1000.0)
                    } else stringResource(R.string.summary_water_ml, ui.waterMl))
                }
            }
            if (saveFailed) {
                Text(stringResource(R.string.today_reflection_failed), color = MaterialTheme.colorScheme.error)
                PlainButton(stringResource(R.string.today_discard), onClick = onDismiss)
            }
            if (edited) PrimaryButton(stringResource(R.string.save), onClick = { finish(onDismiss) }, haptic = true)
            if (safeToShare) {
                if ((mood ?: 0) >= 4 && onOpenPartner != null) PartnerReflectionInvite(onOpen = { finish(onOpenPartner) })
                val context = LocalContext.current
                PlainButton(stringResource(R.string.share), onClick = { ShareCards.shareFast(context, fast) })
            }
        }
    }
}

/** "13h 40m", or "25m" for a fast under an hour (never a bare "0h"). */
@Composable
private fun shortLength(d: java.time.Duration): String =
    if (d.toHours() == 0L) com.fastingshift.app.ui.components.minutesLabel(d.toMinutes()) else d.hoursMinutes()
