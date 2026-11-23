package com.fastingshift.app.ui.ramadan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.LoadingBox
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.safety.ChecklistDialog
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.ramadan.NoFastReason
import com.fastingshift.domain.safety.BlockReason

/**
 * "Muslim fasting": today's Hijri date, today's fast (until maghrib), the next Sunnah days
 * and qada progress. A calendar and tracking aid; no religious rulings.
 */
@Composable
fun MuslimHubRoute(onBack: () -> Unit, onOpenTimer: () -> Unit, vm: MuslimViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    SecondaryScaffold(title = stringResource(R.string.muslim_hub_title), onBack = onBack) {
        val s = state
        if (s == null) {
            LoadingBox()
        } else {
            HijriCard(s)
            TodayGroup(s, kinds = s.startKinds, onStart = vm::start, onOpenTimer = onOpenTimer)
            UpcomingGroup(s)
            QadaLedgerCard()
        }
    }
    StartFastDialogs(vm, onOpenTimer)
}

@Composable
private fun HijriCard(s: MuslimUiState) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Crescent(size = 18.dp)
                Text(stringResource(R.string.muslim_today), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            val h = s.hijri
            Text(
                if (h != null) hijriText(h) else stringResource(R.string.muslim_hijri_unavailable),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(MuslimText.gregorianFull(s.today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.offsetDays != 0) {
                Text(
                    stringResource(R.string.muslim_hijri_adjusted, offsetText(s.offsetDays)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.muslim_hijri_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Today's status and "Start … fast" rows for [kinds]. Shared with the qada screen. */
@Composable
fun TodayGroup(s: MuslimUiState, kinds: List<FastKind>, onStart: (FastKind) -> Unit, onOpenTimer: () -> Unit, header: String? = stringResource(R.string.muslim_today)) {
    InsetGroup(header = header) {
        var first = true
        fun divider(): Boolean = (!first).also { first = false }
        if (s.todayTypes.isNotEmpty()) {
            divider()
            GroupRow(sunnahTypesText(s.todayTypes), subtitle = stringResource(R.string.muslim_today_sunnah))
        }
        noFastText(s.noFast)?.let { text ->
            if (divider()) GroupDivider()
            GroupRow(text)
        }
        if (kinds.isNotEmpty() || s.todayFast is TodayFast.Active) {
            when (val t = s.todayFast) {
                TodayFast.NeedsCity -> {
                    if (divider()) GroupDivider()
                    // Tapping a start row asks for a city first; the picker opens from the dialog flow.
                    kinds.forEachIndexed { i, kind ->
                        if (i > 0) GroupDivider()
                        GroupRow(
                            stringResource(startLabel(kind)),
                            subtitle = stringResource(R.string.muslim_choose_city_body),
                            titleColor = MaterialTheme.colorScheme.primary,
                            onClick = { onStart(kind) },
                        )
                    }
                }
                is TodayFast.Active -> {
                    if (divider()) GroupDivider()
                    GroupRow(
                        stringResource(R.string.muslim_fast_active),
                        subtitle = stringResource(R.string.muslim_fast_active_body),
                        showChevron = true,
                        onClick = onOpenTimer,
                    )
                }
                TodayFast.TimesUnavailable -> {
                    if (divider()) GroupDivider()
                    GroupRow(stringResource(R.string.ramadan_local_timetable))
                }
                is TodayFast.TooLate -> {
                    if (divider()) GroupDivider()
                    GroupRow(stringResource(R.string.muslim_too_late, clockTime(t.maghrib, t.zone)))
                }
                is TodayFast.Ready -> kinds.forEach { kind ->
                    if (divider()) GroupDivider()
                    GroupRow(
                        stringResource(startLabel(kind)),
                        subtitle = stringResource(R.string.muslim_until_maghrib, clockTime(t.maghrib, t.zone), t.length.hoursMinutes()),
                        titleColor = MaterialTheme.colorScheme.primary,
                        onClick = { onStart(kind) },
                    )
                }
            }
        }
        if (first) GroupRow(stringResource(R.string.muslim_today_none))
    }
}

@Composable
private fun noFastText(reason: NoFastReason?): String? = when (reason) {
    NoFastReason.RAMADAN -> stringResource(R.string.muslim_no_fast_ramadan)
    NoFastReason.EID_AL_FITR -> stringResource(R.string.muslim_no_fast_eid_fitr)
    NoFastReason.EID_AL_ADHA -> stringResource(R.string.muslim_no_fast_eid_adha)
    NoFastReason.TASHREEQ -> stringResource(R.string.muslim_no_fast_tashreeq)
    null -> null
}

private fun startLabel(kind: FastKind): Int = when (kind) {
    FastKind.SUNNAH -> R.string.muslim_start_sunnah
    FastKind.QADA -> R.string.muslim_start_qada
    FastKind.RAMADAN -> R.string.muslim_start_ramadan
    FastKind.REGULAR -> R.string.muslim_start_qada
}

@Composable
private fun UpcomingGroup(s: MuslimUiState) {
    InsetGroup(header = stringResource(R.string.muslim_upcoming), footer = stringResource(R.string.muslim_upcoming_footer)) {
        if (s.upcoming.isEmpty()) {
            GroupRow(stringResource(if (s.noFast == NoFastReason.RAMADAN) R.string.muslim_no_fast_ramadan else R.string.muslim_upcoming_none))
        }
        s.upcoming.forEachIndexed { i, day ->
            if (i > 0) GroupDivider()
            val dateLabel = when (day.date) {
                s.today -> stringResource(R.string.muslim_today)
                s.today.plusDays(1) -> stringResource(R.string.muslim_tomorrow)
                else -> MuslimText.gregorian(day.date)
            }
            GroupRow(sunnahTypesText(day.types), subtitle = hijriText(day.hijri), value = dateLabel)
        }
    }
}

@Composable
fun StartFastDialogs(vm: MuslimViewModel, onOpenTimer: () -> Unit) {
    val events by vm.events.collectAsStateWithLifecycle()
    if (events.started) {
        LaunchedEffect(Unit) {
            vm.consumeStarted()
            onOpenTimer()
        }
    }
    events.confirm?.let { reqs -> ChecklistDialog(reqs, onConfirm = vm::confirmAndStart, onDismiss = vm::dismissDialogs) }
    events.blocked?.let { reason ->
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            text = { Text(stringResource(blockedMessage(reason))) },
            confirmButton = { TextButton(onClick = { vm.dismissDialogs(); onOpenTimer() }) { Text(stringResource(R.string.muslim_open_timer)) } },
            dismissButton = { TextButton(onClick = vm::dismissDialogs) { Text(stringResource(R.string.ok)) } },
        )
    }
    if (events.invalidStart) {
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            text = { Text(stringResource(R.string.muslim_invalid_start)) },
            confirmButton = { TextButton(onClick = vm::dismissDialogs) { Text(stringResource(R.string.ok)) } },
        )
    }
    if (events.needsCity) {
        CityPickerSheet(selectedId = null, onPick = vm::chooseCity, onDismiss = vm::dismissDialogs)
    }
}

private fun blockedMessage(reason: BlockReason): Int = when (reason) {
    BlockReason.EDUCATION_ONLY -> R.string.timer_education_only
    BlockReason.NEEDS_CLINICIAN_ACKNOWLEDGEMENT -> R.string.timer_locked_doctor
    BlockReason.NEEDS_SCREENING -> R.string.timer_needs_screening
    BlockReason.EXCEEDS_GATED_LIMIT, BlockReason.OUT_OF_RANGE -> R.string.timer_locked_soft
}
