package com.fastingshift.app.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.onboarding.CheckGroupRow
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
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.checklist_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (StartRequirement.EXTENDED_FAST_CHECKLIST in requirements) {
                    listOf(
                        R.string.checklist_hydration, R.string.checklist_electrolytes, R.string.checklist_medications,
                        R.string.checklist_contact, R.string.checklist_redflags,
                    ).forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 1.dp))
                            Text(stringResource(item), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (needs72h) {
                    Text(stringResource(R.string.checklist_over72), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.background) {
                        CheckGroupRow(stringResource(R.string.checklist_over72_ack), acknowledged72h, { acknowledged72h = it })
                    }
                }
                if (StartRequirement.MEDICAL_DISCLAIMER in requirements) {
                    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !needs72h || acknowledged72h) {
                Text(stringResource(R.string.checklist_confirm), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
