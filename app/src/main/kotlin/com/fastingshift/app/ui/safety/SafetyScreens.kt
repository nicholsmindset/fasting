package com.fastingshift.app.ui.safety

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.domain.safety.EmergencyNumbers
import com.fastingshift.domain.safety.Helplines
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val checkins: SymptomCheckinRepository,
    private val service: FastService,
    private val clock: Clock,
) : ViewModel() {
    fun submit(fastId: Long, answers: Set<Symptom>, onRedFlag: () -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            if (checkins.record(fastId, clock.instant(), answers)) onRedFlag() else onDone()
        }
    }

    fun endFast(then: () -> Unit) {
        viewModelScope.launch {
            service.endActive()
            then()
        }
    }
}

/** 8-hourly symptom check-in during fasts of 24h or more (PRD F7). */
@Composable
fun CheckInRoute(fastId: Long, onRedFlag: () -> Unit, onDone: () -> Unit, vm: SafetyViewModel = hiltViewModel()) {
    var selected by remember { mutableStateOf(emptySet<Symptom>()) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.checkin_body))
            Symptom.entries.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s in selected, onCheckedChange = { on -> selected = if (on) selected + s else selected - s })
                    Text(stringResource(symptomLabel(s)))
                }
            }
            Button(onClick = { vm.submit(fastId, selected, onRedFlag, onDone) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (selected.isEmpty()) R.string.checkin_feel_fine else R.string.checkin_submit))
            }
        }
    }
}

/**
 * Full-screen "End your fast and seek medical care" (SR-4). Works offline: no network calls,
 * the emergency number is resolved from the device region.
 */
@Composable
fun StopFastRoute(onDone: () -> Unit, vm: SafetyViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val number = EmergencyNumbers.forRegion(Locale.getDefault().country)
    BackHandler { vm.endFast(onDone) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.stop_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.stop_body))
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.stop_call, number)) }
            OutlinedButton(onClick = { vm.endFast(onDone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_end_fast)) }
            Text(stringResource(R.string.stop_after), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Support lines, Singapore first (SR-6). Tapping opens the dialler. */
@Composable
fun HelplineList() {
    val context = LocalContext.current
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp).fillMaxWidth()) {
        Text(stringResource(R.string.helplines_title), style = MaterialTheme.typography.titleSmall)
        Helplines.forRegion(Locale.getDefault().country).forEach { h ->
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + h.phone.filter { it.isDigit() || it == '+' }))) }) {
                Text("${h.name}: ${h.phone} (${h.note})")
            }
        }
    }
}

fun symptomLabel(s: Symptom): Int = when (s) {
    Symptom.FAINTING -> R.string.sym_fainting
    Symptom.CHEST_PAIN -> R.string.sym_chest_pain
    Symptom.PALPITATIONS -> R.string.sym_palpitations
    Symptom.CONFUSION -> R.string.sym_confusion
    Symptom.SEVERE_WEAKNESS -> R.string.sym_severe_weakness
    Symptom.LIGHTHEADED -> R.string.sym_lightheaded
    Symptom.HEADACHE -> R.string.sym_headache
    Symptom.NAUSEA -> R.string.sym_nausea
    Symptom.MUSCLE_CRAMPS -> R.string.sym_cramps
    Symptom.FATIGUE -> R.string.sym_fatigue
    Symptom.HUNGER -> R.string.sym_hunger
    Symptom.IRRITABILITY -> R.string.sym_irritability
}
