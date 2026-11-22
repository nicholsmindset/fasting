package com.fastingshift.app.ui.ramadan

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.data.repo.QadaRepository
import com.fastingshift.data.repo.QadaYear
import com.fastingshift.domain.model.QadaDebt
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class QadaLedgerViewModel @Inject constructor(
    val ledger: QadaRepository,
    private val clock: Clock,
) : ViewModel() {
    val years = ledger.observeLedger().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
}

@Composable
fun QadaLedgerCard(vm: QadaLedgerViewModel = hiltViewModel()) {
    val snapshot by vm.years.collectAsStateWithLifecycle()
    val years = snapshot ?: return
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Zero selects the new-year form. All saved Hijri years in the form are positive.
    var editingYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var recordingFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var originalDate by rememberSaveable { mutableStateOf<String?>(null) }
    var hasError by rememberSaveable { mutableStateOf(false) }
    var saving by androidx.compose.runtime.remember { mutableStateOf(false) }
    fun dismiss() {
        editingYear = null
        recordingFor = null
        originalDate = null
        hasError = false
    }
    fun write(action: suspend () -> Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                if (action()) dismiss() else hasError = true
            } catch (_: android.database.SQLException) {
                hasError = true
            } finally {
                saving = false
            }
        }
    }
    AppCard {
        Text(stringResource(R.string.qada_ledger), style = MaterialTheme.typography.titleMedium)
        val remaining = years.sumOf { it.remaining }
        if (years.isNotEmpty()) Text(pluralStringResource(R.plurals.qada_remaining, remaining, remaining))
        else Text(stringResource(R.string.qada_intro), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.qada_hide else R.string.qada_manage))
        }
        if (expanded) {
            years.forEach { year ->
                HorizontalDivider()
                Text(stringResource(R.string.qada_year_label, year.debt.hijriYear), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.qada_balance, year.debt.daysOwed, year.repaidDates.size, year.remaining))
                TextButton(onClick = { editingYear = year.debt.hijriYear }) { Text(stringResource(R.string.qada_edit_debt)) }
                if (year.remaining > 0) {
                    TextButton(onClick = { recordingFor = year.debt.hijriYear }) { Text(stringResource(R.string.qada_record)) }
                }
                year.repaidDates.forEach { date ->
                    TextButton(onClick = { originalDate = date.toString(); recordingFor = year.debt.hijriYear }) {
                        Text(stringResource(R.string.qada_edit_day, localDateLabel(date)))
                    }
                }
            }
            TextButton(onClick = { editingYear = 0 }) { Text(stringResource(R.string.qada_add_year)) }
        }
    }
    editingYear?.let { year ->
        key(year) {
            QadaYearDialog(year, years, hasError, saving, ::dismiss) { debt -> write { vm.ledger.setOwed(debt) } }
        }
    }
    recordingFor?.let { year ->
        val original = originalDate?.let(LocalDate::parse)
        key(year, originalDate) {
            QadaDayDialog(
                year, original, vm.today(), years, hasError, saving, ::dismiss,
                onSave = { date, target ->
                    write {
                        when {
                            date > vm.today() -> false
                            original == null -> vm.ledger.record(date, target)
                            else -> vm.ledger.correct(original, year, date, target)
                        }
                    }
                },
                onUndo = { if (original != null) write { vm.ledger.undo(original, year) } },
            )
        }
    }
}

@Composable
private fun QadaYearDialog(
    initialYear: Int, years: List<QadaYear>, hasError: Boolean, saving: Boolean,
    dismiss: () -> Unit, onSave: (QadaDebt) -> Unit,
) {
    val existing = years.find { it.debt.hijriYear == initialYear }
    var yearInput by rememberSaveable { mutableStateOf(existing?.debt?.hijriYear?.toString().orEmpty()) }
    var owedInput by rememberSaveable { mutableStateOf(existing?.debt?.daysOwed?.toString().orEmpty()) }
    val year = yearInput.toIntOrNull()
    val days = owedInput.toIntOrNull()
    val minDays = existing?.repaidDates?.size ?: 1
    val validYear = year != null && year > 0 && (existing != null || years.none { it.debt.hijriYear == year })
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(stringResource(if (initialYear == 0) R.string.qada_add_year else R.string.qada_edit_debt)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (existing == null) {
                    NumberField(yearInput, { yearInput = it }, stringResource(R.string.qada_hijri_year))
                } else {
                    Text(stringResource(R.string.qada_year_label, initialYear))
                }
                NumberField(owedInput, { owedInput = it }, stringResource(R.string.qada_original_days))
                Text(stringResource(R.string.qada_range, minDays), style = MaterialTheme.typography.bodySmall)
                if (hasError) Text(stringResource(R.string.qada_changed))
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && validYear && days != null && days in minDays..30, onClick = {
                if (year != null && days != null) onSave(QadaDebt(year, days))
            }) { Text(stringResource(R.string.qada_save)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text(stringResource(R.string.qada_cancel)) } },
    )
}

@Composable
private fun NumberField(value: String, update: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = update, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QadaDayDialog(
    originalYear: Int, original: LocalDate?, today: LocalDate, years: List<QadaYear>,
    hasError: Boolean, saving: Boolean, dismiss: () -> Unit,
    onSave: (LocalDate, Int) -> Unit, onUndo: () -> Unit,
) {
    val context = LocalContext.current
    var selectedYear by rememberSaveable { mutableStateOf(originalYear) }
    var dateText by rememberSaveable { mutableStateOf((original ?: today).toString()) }
    val date = LocalDate.parse(dateText)
    val duplicate = date != original && years.any { date in it.repaidDates }
    val available = years.filter { it.remaining > 0 || (original != null && it.debt.hijriYear == originalYear) }
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(stringResource(if (original == null) R.string.qada_record else R.string.qada_correct)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.qada_observed))
                TextButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> dateText = LocalDate.of(y, m + 1, d).toString() },
                        date.year, date.monthValue - 1, date.dayOfMonth).apply {
                        datePicker.maxDate = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                    }.show()
                }) { Text(localDateLabel(date)) }
                Text(stringResource(R.string.qada_allocate))
                available.forEach { choice ->
                    val year = choice.debt.hijriYear
                    androidx.compose.material3.FilterChip(
                        selected = year == selectedYear, onClick = { selectedYear = year },
                        label = { Text(stringResource(R.string.qada_year_label, year)) },
                    )
                }
                if (duplicate) Text(stringResource(R.string.qada_duplicate))
                if (hasError) Text(stringResource(R.string.qada_changed))
                if (original != null) TextButton(enabled = !saving, onClick = onUndo) { Text(stringResource(R.string.qada_undo)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && !duplicate && date <= today && available.any { it.debt.hijriYear == selectedYear },
                onClick = { onSave(date, selectedYear) }) { Text(stringResource(R.string.qada_save)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text(stringResource(R.string.qada_cancel)) } },
    )
}

@Composable
private fun localDateLabel(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
}
