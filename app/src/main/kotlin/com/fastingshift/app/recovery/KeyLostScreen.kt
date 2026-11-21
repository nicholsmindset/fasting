package com.fastingshift.app.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R

/**
 * Shown instead of the app when the database key in the Android Keystore is gone (SR-9): the
 * encrypted data can't be read by anyone, so the only way on is a fresh start, optionally
 * followed by restoring an exported backup file.
 */
@Composable
fun KeyLostScreen(resetting: Boolean, onStartFresh: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.recovery_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.recovery_body))
            Text(stringResource(R.string.recovery_fresh_body))
            Text(stringResource(R.string.recovery_backup_note), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { confirm = true }, enabled = !resetting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recovery_start_fresh))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.recovery_confirm_title)) },
            text = { Text(stringResource(R.string.recovery_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onStartFresh() }) { Text(stringResource(R.string.recovery_start_fresh)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.recovery_cancel)) } },
        )
    }
}
