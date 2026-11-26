package com.fastingshift.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.HistoryTransferRepository
import com.fastingshift.domain.backup.HistoryBatch
import com.fastingshift.domain.backup.HistoryCsv
import com.fastingshift.domain.backup.HistoryImport
import com.fastingshift.domain.backup.HistoryFileException
import com.fastingshift.domain.backup.HistorySkip
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class HistoryTooLarge : IOException()

private fun InputStream.readHistoryFile(): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        if (out.size() + count > HistoryCsv.MAX_BYTES) throw HistoryTooLarge()
        out.write(buffer, 0, count)
    }
    return out.toByteArray().decodeToString(throwOnInvalidSequence = true)
}

sealed interface TransferState {
    data object Idle : TransferState
    data object Busy : TransferState
    data class Preview(val batch: HistoryBatch) : TransferState
    data class Imported(val batch: HistoryBatch) : TransferState
    data class Message(val resource: Int) : TransferState
}

@HiltViewModel
class HistoryTransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    db: FastingDatabase,
    private val clock: Clock,
) : ViewModel() {
    private val repo = HistoryTransferRepository(db)
    var state by mutableStateOf<TransferState>(TransferState.Idle)
        private set

    fun dismiss() { if (state != TransferState.Busy) state = TransferState.Idle }

    fun open(uri: Uri) = runTransfer {
        val contents = context.contentResolver.openInputStream(uri)?.use { it.readHistoryFile() } ?: throw IOException()
        TransferState.Preview(repo.preview(HistoryImport.parse(contents, clock.instant())))
    }

    fun confirm() {
        val batch = (state as? TransferState.Preview)?.batch ?: return
        runTransfer { TransferState.Imported(repo.add(batch)) }
    }

    fun export(uri: Uri) = runTransfer {
        val csv = repo.exportCsv()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: throw IOException()
        TransferState.Message(R.string.history_transfer_exported)
    }

    private fun runTransfer(block: suspend () -> TransferState) {
        if (state == TransferState.Busy) return
        state = TransferState.Busy
        viewModelScope.launch {
            state = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HistoryTooLarge) {
                TransferState.Message(R.string.history_transfer_too_large)
            } catch (e: HistoryFileException) {
                TransferState.Message(R.string.history_transfer_format)
            } catch (e: Exception) {
                TransferState.Message(R.string.history_transfer_error)
            }
        }
    }
}

@Composable
internal fun HistoryTransferRows(vm: HistoryTransferViewModel = hiltViewModel()) {
    HistoryImportRow(vm, stringResource(R.string.history_transfer_import))
    GroupDivider()
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::export) }
    GroupRow(
        stringResource(R.string.history_transfer_export), subtitle = stringResource(R.string.history_transfer_privacy),
        showChevron = true, onClick = { exportFile.launch("fasting-shift-history.csv") },
    )
    TransferDialog(vm)
}

@Composable
internal fun WelcomeHistoryImport(vm: HistoryTransferViewModel = hiltViewModel()) {
    InsetGroup {
        HistoryImportRow(vm, stringResource(R.string.welcome_import_history))
    }
    TransferDialog(vm)
}

@Composable
private fun HistoryImportRow(vm: HistoryTransferViewModel, title: String) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
    GroupRow(
        title = title,
        subtitle = stringResource(R.string.history_transfer_hint),
        showChevron = true,
        onClick = {
            picker.launch(arrayOf("application/json", "text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
        },
    )
}

@Composable
private fun TransferDialog(vm: HistoryTransferViewModel) {
    val state = vm.state
    when (state) {
        TransferState.Idle -> Unit
        TransferState.Busy -> AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { CircularProgressIndicator() })
        is TransferState.Message -> AlertDialog(
            onDismissRequest = vm::dismiss,
            text = { Text(stringResource(state.resource)) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(android.R.string.ok)) } },
        )
        else -> {
            val preview = state is TransferState.Preview
            val batch = when (state) {
                is TransferState.Preview -> state.batch
                is TransferState.Imported -> state.batch
                else -> return
            }
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text(stringResource(if (preview) R.string.history_transfer_preview else R.string.history_transfer_done)) },
                text = { HistoryTransferDetails(batch, preview) },
                confirmButton = {
                    if (preview && batch.fasts.isNotEmpty()) {
                        TextButton(onClick = vm::confirm) { Text(stringResource(R.string.history_transfer_add)) }
                    } else TextButton(onClick = vm::dismiss) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    if (preview && batch.fasts.isNotEmpty()) TextButton(onClick = vm::dismiss) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun HistoryTransferDetails(batch: HistoryBatch, preview: Boolean) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val count = batch.fasts.size
        Text(pluralStringResource(if (preview) R.plurals.history_transfer_ready else R.plurals.history_transfer_added, count, count))
        if (count > 0) {
            val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
            Text(stringResource(R.string.history_transfer_range, dates.format(batch.fasts.first().startAt), dates.format(batch.fasts.last().endedAt)))
        }
        if (preview && count > 0) {
            val starts = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
            for (fast in batch.fasts.take(3)) {
                Text(stringResource(
                    R.string.history_transfer_sample, starts.format(fast.startAt),
                    requireNotNull(fast.actualDuration).hoursMinutes(), fast.plannedDuration.hoursMinutes(),
                ))
            }
        }
        for ((reason, number) in batch.skipped) {
            val resource = when (reason) {
                HistorySkip.DUPLICATE -> R.plurals.history_transfer_duplicates
                HistorySkip.OVERLAP -> R.plurals.history_transfer_overlaps
                HistorySkip.NO_GOAL -> R.plurals.history_transfer_no_goal
                HistorySkip.LEGACY_ZERO -> R.plurals.history_transfer_legacy
                HistorySkip.UNFINISHED -> R.plurals.history_transfer_unfinished
                HistorySkip.INVALID -> R.plurals.history_transfer_invalid
            }
            Text(pluralStringResource(resource, number, number))
        }
    }
}
