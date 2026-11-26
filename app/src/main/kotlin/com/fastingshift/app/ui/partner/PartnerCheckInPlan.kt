package com.fastingshift.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PartnerCheckInPlan(week: PartnerWeek, plannedDays: Set<String>, enabled: Boolean, onSave: (Set<String>) -> Unit) {
    val start = week.startDate?.let(LocalDate::parse) ?: return
    val end = week.endDate?.let(LocalDate::parse) ?: return
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    var editing by rememberSaveable(week.id) { mutableStateOf(false) }
    var draft by rememberSaveable(week.id) { mutableStateOf(arrayListOf<String>()) }
    val summary = if (plannedDays.isEmpty()) stringResource(R.string.partner_plan_empty)
        else plannedDays.sorted().joinToString(" · ") { LocalDate.parse(it).format(dateFormat) }
    InsetGroup {
        GroupRow(stringResource(R.string.partner_plan_title), Modifier.testTag("partner_plan_open"),
            subtitle = summary, showChevron = true, onClick = if (enabled) ({
                draft = ArrayList(plannedDays)
                editing = true
            }) else null)
    }
    if (editing) {
        AlertDialog(onDismissRequest = { editing = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }, title = { Text(stringResource(R.string.partner_plan_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.partner_plan_help), style = MaterialTheme.typography.bodyMedium)
                    for (index in 0L..6L) {
                        val date = start.plusDays(index)
                        if (date > end) break
                        val key = date.toString()
                        val selected = key in draft
                        val available = selected || ((key >= week.today || key in plannedDays) && draft.size < 3)
                        Row(Modifier.fillMaxWidth().testTag("partner_plan_day_$index")
                            .toggleable(value = selected, enabled = available, role = Role.Checkbox) {
                                draft = ArrayList(if (it) draft + key else draft - key)
                            }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(selected, onCheckedChange = null, enabled = available)
                            Text(date.format(dateFormat), color = if (available) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { onSave(draft.toSet()); editing = false }, enabled = enabled,
                    modifier = Modifier.testTag("partner_plan_save")) { Text(stringResource(R.string.partner_plan_save)) }
            }, dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
            })
    }
}
