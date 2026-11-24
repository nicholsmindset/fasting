package com.fastingshift.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.SwitchGroupRow
import com.fastingshift.app.ui.onboarding.CheckGroupRow
import com.fastingshift.app.ui.ramadan.GroupedSheet
import com.fastingshift.app.ui.ramadan.RadioChoiceRow
import com.fastingshift.data.settings.AppSettings
import com.fastingshift.domain.ramadan.SunnahType

@Composable
internal fun SunnahSettingsGroup(s: AppSettings, vm: SettingsViewModel, onOpenHub: () -> Unit = {}) {
    var choosingDays by rememberSaveable { mutableStateOf(false) }
    var choosingOffset by rememberSaveable { mutableStateOf(false) }
    InsetGroup(
        header = stringResource(R.string.sunnah_title),
        footer = stringResource(R.string.sunnah_when),
    ) {
        SwitchGroupRow(stringResource(R.string.sunnah_enable), s.sunnahReminders, { enabled ->
            if (enabled && s.sunnahTypes.isEmpty()) choosingDays = true
            else vm.set { setSunnahReminders(enabled) }
        })
        if (s.sunnahReminders) {
            GroupDivider()
            GroupRow(
                stringResource(R.string.sunnah_days),
                subtitle = s.sunnahTypes.sortedBy { it.ordinal }.map { stringResource(typeTitle(it)) }.joinToString(" · "),
                showChevron = true, opensPicker = true, onClick = { choosingDays = true },
            )
        }
        GroupDivider()
        GroupRow(stringResource(R.string.sunnah_offset), value = offsetLabel(s.hijriOffsetDays),
            showChevron = true, opensPicker = true, onClick = { choosingOffset = true })
        GroupDivider()
        GroupRow(stringResource(R.string.muslim_hub_entry), showChevron = true, onClick = onOpenHub)
    }
    if (choosingDays) {
        var draft by rememberSaveable { mutableStateOf(ArrayList(s.sunnahTypes.map { it.name })) }
        AlertDialog(
            onDismissRequest = { choosingDays = false },
            title = { Text(stringResource(R.string.sunnah_days)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SunnahType.entries.forEach { type ->
                        CheckGroupRow(stringResource(typeTitle(type)), type.name in draft, { enabled ->
                            draft = ArrayList(if (enabled) draft + type.name else draft - type.name)
                        })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = draft.isNotEmpty(), onClick = {
                    val chosen = draft.map(SunnahType::valueOf).toSet()
                    vm.set {
                        setSunnahTypes(chosen)
                        setSunnahReminders(true)
                    }
                    choosingDays = false
                }) { Text(stringResource(R.string.qada_save)) }
            },
            dismissButton = {
                TextButton(onClick = { choosingDays = false }) { Text(stringResource(R.string.qada_cancel)) }
            },
        )
    }
    if (choosingOffset) {
        GroupedSheet(
            title = stringResource(R.string.sunnah_offset),
            onDismiss = { choosingOffset = false },
            dismissLabel = R.string.qada_cancel,
        ) {
            InsetGroup(modifier = Modifier.selectableGroup(), footer = stringResource(R.string.sunnah_offset_hint)) {
                for (days in -2..2) {
                    if (days > -2) GroupDivider()
                    RadioChoiceRow(offsetLabel(days), selected = days == s.hijriOffsetDays, onClick = {
                        vm.set { setHijriOffsetDays(days) }
                        choosingOffset = false
                    })
                }
            }
        }
    }
}

private fun typeTitle(type: SunnahType): Int = when (type) {
    SunnahType.MON_THU -> R.string.sunnah_mon_thu
    SunnahType.AYYAMUL_BIDH -> R.string.sunnah_white
    SunnahType.ARAFAH -> R.string.sunnah_arafah
    SunnahType.ASHURA -> R.string.sunnah_ashura
    SunnahType.SHAWWAL -> R.string.sunnah_shawwal
}

@Composable
private fun offsetLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.sunnah_offset_none)
    else -> stringResource(R.string.sunnah_offset_value, days)
}
