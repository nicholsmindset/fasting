package com.fastingshift.app.ui.trust

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.export.ExportManager
import com.fastingshift.app.trust.DataWiper
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TrustViewModel @Inject constructor(
    private val export: ExportManager,
    private val wiper: DataWiper,
) : ViewModel() {
    val message = MutableStateFlow<Int?>(null)
    val deleting = MutableStateFlow(false)

    fun exportZip(uri: Uri) = viewModelScope.launch {
        message.value = runCatching { export.writeExportZip(uri) }.fold({ R.string.settings_exported }, { R.string.settings_file_error })
    }

    fun deleteEverything() {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            try {
                wiper.wipeAndRestart()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = R.string.wipe_retry_error
            } finally {
                deleting.value = false
            }
        }
    }
}

/** One plain-language promise, checked against the code (see TrustScreen KDoc). */
private data class Promise(val title: Int, val body: Int)

/*
 * Every line here must stay true. Checked against the code when written:
 * - no account: there is no sign-in anywhere; RevenueCat uses an anonymous app user ID
 * - storage: Room + SQLCipher (FastingDatabase.createEncrypted), passphrase wrapped by an
 *   AES-GCM key in the Android Keystore (DatabaseKeyProvider); Auto Backup is off
 * - network: billing and free-tier advertising use separate services. The app supplies
 *   neither with fasting, screening, religious-observance or Health Connect records.
 * - Health Connect: only after the user turns it on and grants permissions (Settings)
 * - files: export/backup via the system file picker; report/cards via the share sheet only
 */
private val promises = listOf(
    Promise(R.string.trust_on_device_title, R.string.trust_on_device_body),
    Promise(R.string.trust_encrypted_title, R.string.trust_encrypted_body),
    Promise(R.string.trust_no_account_title, R.string.trust_no_account_body),
    Promise(R.string.trust_free_ads_title, R.string.trust_free_ads_body),
)

private val leaves = listOf(
    Promise(R.string.trust_leaves_ads_title, R.string.trust_leaves_ads_body),
    Promise(R.string.trust_leaves_purchases_title, R.string.trust_leaves_purchases_body),
    Promise(R.string.trust_leaves_hc_title, R.string.trust_leaves_hc_body),
    Promise(R.string.trust_leaves_files_title, R.string.trust_leaves_files_body),
    Promise(R.string.trust_leaves_links_title, R.string.trust_leaves_links_body),
)

@Composable
fun TrustRoute(onBack: () -> Unit, vm: TrustViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportZip) }

    msg?.let { m ->
        LaunchedEffect(m) {
            snackbar.showSnackbar(context.getString(m))
            vm.message.value = null
        }
    }
    TrustScreen(
        onBack = onBack,
        snackbar = snackbar,
        deleting = deleting,
        onExport = { exportLauncher.launch("fasting-shift-export.zip") },
        onDeleteConfirmed = vm::deleteEverything,
    )
}

@Composable
fun TrustScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    snackbar: SnackbarHostState? = null,
    deleting: Boolean = false,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = deleting) {}
    SecondaryScaffold(title = stringResource(R.string.trust_title), onBack = { if (!deleting) onBack() }, snackbarHostState = snackbar) {
        Text(stringResource(R.string.trust_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppCard {
            promises.forEach { p -> PromiseRow(p) }
        }
        Text(
            stringResource(R.string.trust_leaves_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() },
        )
        AppCard {
            leaves.forEach { p -> PromiseRow(p, check = false) }
        }
        InsetGroup(header = stringResource(R.string.trust_your_data), footer = stringResource(R.string.trust_delete_footer)) {
            GroupRow(
                stringResource(R.string.trust_export),
                subtitle = stringResource(R.string.trust_export_subtitle),
                titleColor = MaterialTheme.colorScheme.primary,
                onClick = { if (!deleting) onExport() },
            )
            GroupDivider()
            GroupRow(
                stringResource(if (deleting) R.string.trust_deleting else R.string.trust_delete),
                titleColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("trust_delete"),
                onClick = { if (!deleting) confirm = true },
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.trust_delete_confirm_title)) },
            text = { Text(stringResource(R.string.trust_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDeleteConfirmed() }) {
                    Text(stringResource(R.string.trust_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PromiseRow(p: Promise, check: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        if (check) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(p.title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(p.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
