package com.fastingshift.app.ui.firstweek

import com.fastingshift.app.share.ShareCards
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.notifications.Notifications
import com.fastingshift.app.ui.components.rememberWeekFormat
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupHeader
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.onboarding.HeroIcon
import com.fastingshift.app.ui.onboarding.TextFieldGroupRow
import com.fastingshift.domain.coaching.CoachingTip
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/*
 * First-week journey. These are the stable entry points the Timer screen calls; each decides on
 * its own (through its view model) whether to show anything.
 */

/** One-off reminder before the first fast. The Timer provides the start action. */
@Composable
fun FirstFastCard(
    now: Instant,
    offer: EveningOffer,
    remindersAvailable: Boolean,
    onOpenReminders: () -> Unit,
    vm: FirstFastViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val visible = st != null && (st?.planned != null || offer == EveningOffer.AVAILABLE ||
        (offer == EveningOffer.COVERED && !remindersAvailable))
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val ui = st ?: return@AnimatedVisibility
        val format = rememberWeekFormat()
        AppCard(Modifier.animateContentSize(), padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            Text(stringResource(R.string.first_fast_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val plan = ui.planned
            if (plan != null) {
                Text(stringResource(R.string.first_fast_one_off, format.date.format(plan.date)), style = MaterialTheme.typography.bodyMedium)
                PlainButton(stringResource(R.string.first_fast_cancel_plan), onClick = vm::cancelPlan)
            }
            if (!remindersAvailable) {
                Text(stringResource(R.string.week_alert_off), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(stringResource(R.string.settings_reminders), onClick = onOpenReminders)
            } else if (plan == null && offer == EveningOffer.AVAILABLE) {
                val zone = ZoneId.systemDefault()
                val tonight = FirstWeekRules.nextEvening(now, zone).toLocalDate() == now.atZone(zone).toLocalDate()
                SecondaryButton(
                    stringResource(if (tonight) R.string.first_fast_tonight else R.string.first_fast_tomorrow,
                        format.time.format(FirstWeekRules.EVENING_START)),
                    onClick = vm::planTonight,
                )
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

/**
 * Calm end-of-fast summary (duration vs plan, water during the fast, "how do you feel?" and a
 * note), shown right after a fast ends from the Timer. [onUndo] re-opens the fast (only offered
 * inside the undo window); [onDismiss] closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastSummarySheet(fast: Fast, onUndo: () -> Unit, onDismiss: () -> Unit, vm: FastSummaryViewModel = hiltViewModel()) {
    LaunchedEffect(fast.id) { vm.bind(fast) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val close: (() -> Unit) -> Unit = { then -> scope.launch { sheet.hide() }.invokeOnCompletion { then() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val completed = fast.status == FastStatus.COMPLETED
            val actual = fast.actualDuration ?: java.time.Duration.ZERO
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroIcon(if (completed) Icons.Filled.CheckCircle else Icons.Filled.Favorite, size = 56.dp)
                Text(
                    stringResource(if (completed) R.string.summary_title else R.string.summary_title_early),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                Text(shortLength(actual), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (completed) {
                        stringResource(R.string.summary_goal_met, fast.plannedDuration.hoursMinutes())
                    } else {
                        stringResource(R.string.summary_goal_early, fast.plannedDuration.hoursMinutes(), shortLength(actual))
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
            InsetGroup {
                GroupRow(
                    stringResource(R.string.summary_water),
                    value = if (ui.waterMl >= 1_000) stringResource(R.string.summary_water_l, ui.waterMl / 1000.0) else stringResource(R.string.summary_water_ml, ui.waterMl),
                )
            }
            Column {
                GroupHeader(stringResource(R.string.summary_mood_title))
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    moodLabels.forEachIndexed { i, label ->
                        val value = i + 1
                        val selected = mood == value
                        val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "mood")
                        val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "moodText")
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.tick(); vm.setMood(value) }),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1) }
                    }
                }
            }
            InsetGroup {
                TextFieldGroupRow(stringResource(R.string.summary_note), note, vm::setNote, keyboardType = KeyboardType.Text, singleLine = false)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(stringResource(R.string.save), onClick = { vm.save { close(onDismiss) } }, haptic = true)
                val shareContext = LocalContext.current
                PlainButton(stringResource(R.string.share), onClick = { ShareCards.shareFast(shareContext, fast) })
                AnimatedVisibility(ui.canUndo) {
                    PlainButton(stringResource(R.string.summary_undo), onClick = { onUndo(); close(onDismiss) })
                }
            }
        }
    }
}

/** "13h 40m", or "25m" for a fast under an hour (never a bare "0h"). */
@Composable
private fun shortLength(d: java.time.Duration): String =
    if (d.toHours() == 0L) com.fastingshift.app.ui.components.minutesLabel(d.toMinutes()) else d.hoursMinutes()
