package com.fastingshift.app.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.domain.safety.StartRequirement

/**
 * The pre-fast safety checklist (PRD F7, SR-3, SR-8), used when starting a fast and when an
 * edit lengthens one. Fasts over 72 hours need a separate, explicit tick before confirming.
 */
@Composable
fun ChecklistDialog(requirements: Set<StartRequirement>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val needs72h = StartRequirement.OVER_72H_ACKNOWLEDGEMENT in requirements
    var acknowledged72h by rememberSaveable { mutableStateOf(false) }
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
                if (needs72h) {
                    Text(stringResource(R.string.checklist_over72), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = acknowledged72h, role = Role.Checkbox, onValueChange = { acknowledged72h = it },
                        ),
                    ) {
                        Checkbox(checked = acknowledged72h, onCheckedChange = null)
                        Text(stringResource(R.string.checklist_over72_ack), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (StartRequirement.MEDICAL_DISCLAIMER in requirements) {
                    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !needs72h || acknowledged72h) { Text(stringResource(R.string.checklist_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
