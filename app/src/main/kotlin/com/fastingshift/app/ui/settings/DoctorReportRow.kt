package com.fastingshift.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.export.DoctorReportExporter
import com.fastingshift.app.export.ReportPeriod
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.SegmentedControl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DoctorReportViewModel @Inject constructor(private val exporter: DoctorReportExporter) : ViewModel() {
    val busy = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    fun share(context: Context, period: ReportPeriod) {
        if (busy.value) return
        busy.value = true
        failed.value = false
        viewModelScope.launch {
            runCatching { exporter.share(context, period) }.onFailure { failed.value = true }
            busy.value = false
        }
    }
}

/** Settings → "Report for your doctor": pick 30 or 90 days, then share a PDF made on the phone. */
@Composable
fun DoctorReportGroup(vm: DoctorReportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableIntStateOf(0) }

    InsetGroup(
        header = stringResource(R.string.report_group),
        footer = stringResource(if (failed) R.string.report_failed else R.string.report_footer_note),
    ) {
        GroupRow(
            stringResource(R.string.report_row),
            subtitle = if (busy) stringResource(R.string.report_making) else stringResource(R.string.report_row_subtitle),
            showChevron = true,
            onClick = { if (!busy) open = true },
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.report_row)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.report_dialog_body), style = MaterialTheme.typography.bodyMedium)
                    SegmentedControl(
                        options = listOf(stringResource(R.string.report_days_30), stringResource(R.string.report_days_90)),
                        selected = period,
                        onSelect = { period = it },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    vm.share(context, if (period == 0) ReportPeriod.DAYS_30 else ReportPeriod.DAYS_90)
                }) { Text(stringResource(R.string.report_create)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
