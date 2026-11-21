package com.fastingshift.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastingshift.app.R
import com.fastingshift.app.ui.safety.HelplineList
import com.fastingshift.domain.onboarding.Goal
import com.fastingshift.domain.safety.GateReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.Medication

@Composable
fun OnboardingRoute(onFinished: () -> Unit, onShowPaywall: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.next() }
    // On the first step of a re-run, system back leaves onboarding and returns to Settings.
    BackHandler(enabled = st.step != Step.WELCOME && !(st.rerun && st.step == Step.SCREENING)) { vm.back() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (st.step) {
                Step.WELCOME -> {
                    Title(R.string.onb_welcome_title)
                    Text(stringResource(R.string.onb_welcome_body))
                    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
                    Primary(R.string.onb_get_started) { vm.next() }
                }
                Step.GOAL -> {
                    Title(R.string.onb_goal_title)
                    Goal.entries.forEach { g ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().selectable(selected = st.goal == g, role = Role.RadioButton, onClick = { vm.setGoal(g) }),
                        ) {
                            RadioButton(selected = st.goal == g, onClick = null)
                            Text(stringResource(goalLabel(g)))
                        }
                    }
                    Primary(R.string.onb_next, enabled = st.goal != null) { vm.next() }
                }
                Step.SCREENING -> ScreeningForm(st, vm)
                Step.RESULT -> Result(st, vm, onFinished)
                Step.PROTOCOL -> ProtocolPick(st, vm, onFinished)
                Step.NOTIFICATIONS -> {
                    Title(R.string.onb_notif_title)
                    Text(stringResource(R.string.onb_notif_body))
                    Primary(R.string.onb_notif_allow) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.next()
                    }
                    OutlinedButton(onClick = vm::next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_not_now)) }
                }
                Step.OFFER -> {
                    // Paywall rule 1: "Continue free" is exactly as visible as "Start trial".
                    Title(R.string.onb_offer_title)
                    Text(stringResource(R.string.onb_offer_body))
                    Text(stringResource(R.string.onb_offer_free_note), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.finish { onFinished(); onShowPaywall() } }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.onb_offer_trial))
                        }
                        Button(onClick = { vm.finish(onFinished) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.onb_offer_free))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreeningForm(st: OnboardingState, vm: OnboardingViewModel) {
    val f = st.form
    Title(R.string.onb_screen_title)
    Text(stringResource(R.string.onb_screen_body))
    NumberField(f.age, R.string.onb_age) { v -> vm.updateForm { it.copy(age = v) } }
    Check(f.pregnant, R.string.onb_pregnant) { v -> vm.updateForm { it.copy(pregnant = v) } }
    Check(f.eatingDisorder, R.string.onb_ed) { v -> vm.updateForm { it.copy(eatingDisorder = v) } }
    Check(f.type1, R.string.onb_t1d) { v -> vm.updateForm { it.copy(type1 = v) } }
    Text(stringResource(R.string.onb_meds_title), style = MaterialTheme.typography.titleSmall)
    Medication.entries.forEach { m ->
        Check(m in f.medications, medicationLabel(m)) { on ->
            vm.updateForm { it.copy(medications = if (on) it.medications + m else it.medications - m) }
        }
    }
    NumberField(f.heightCm, R.string.onb_height) { v -> vm.updateForm { it.copy(heightCm = v) } }
    NumberField(f.weightKg, R.string.onb_weight) { v -> vm.updateForm { it.copy(weightKg = v) } }
    if (st.formError) Text(stringResource(R.string.onb_form_error), color = MaterialTheme.colorScheme.error)
    Text(stringResource(R.string.onb_screen_privacy), style = MaterialTheme.typography.bodySmall)
    Primary(R.string.onb_next) { vm.submitScreening() }
}

@Composable
private fun Result(st: OnboardingState, vm: OnboardingViewModel, onFinished: () -> Unit) {
    val outcome = st.outcome ?: return
    when (outcome.result) {
        GateResult.OK -> {
            Title(R.string.onb_ok_title)
            Text(stringResource(R.string.onb_ok_body))
            Primary(R.string.onb_next) { vm.confirmResult(onFinished) }
        }
        GateResult.SOFT -> {
            Title(R.string.onb_soft_title)
            Text(stringResource(R.string.onb_soft_body))
            outcome.reasons.forEach { Text("• " + stringResource(reasonLabel(it))) }
            Check(st.clinicianAck, R.string.onb_soft_ack) { vm.setClinicianAck(it) }
            Primary(R.string.onb_next) { vm.confirmResult(onFinished) }
        }
        GateResult.BLOCK -> {
            // Non-diagnostic, non-shaming: explain, offer support, keep the door open.
            Title(R.string.onb_block_title)
            Text(stringResource(R.string.onb_block_body))
            HelplineList()
            Primary(R.string.onb_continue) { vm.confirmResult(onFinished) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProtocolPick(st: OnboardingState, vm: OnboardingViewModel, onFinished: () -> Unit) {
    Title(R.string.onb_protocol_title)
    if (st.protocols.isEmpty()) {
        Text(stringResource(R.string.timer_needs_clinician))
        Primary(R.string.onb_continue) { vm.finish(onFinished) }
        return
    }
    Text(stringResource(R.string.onb_protocol_body))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        st.protocols.forEach { p -> FilterChip(selected = p == st.selected, onClick = { vm.select(p) }, label = { Text(p.name) }) }
    }
    Primary(R.string.onb_next) { vm.confirmProtocol(onFinished) }
}

@Composable private fun Title(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.headlineSmall)

@Composable
private fun Primary(res: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(res)) }
}

@Composable
private fun Check(checked: Boolean, label: Int, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(label))
    }
}

@Composable
private fun NumberField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}

private fun goalLabel(g: Goal) = when (g) {
    Goal.CONSISTENCY -> R.string.goal_consistency
    Goal.WEIGHT -> R.string.goal_weight
    Goal.METABOLIC_HEALTH -> R.string.goal_metabolic
    Goal.LONG_FASTS -> R.string.goal_long
}

private fun medicationLabel(m: Medication) = when (m) {
    Medication.INSULIN -> R.string.med_insulin
    Medication.SULFONYLUREA -> R.string.med_sulfonylurea
    Medication.SGLT2_INHIBITOR -> R.string.med_sglt2
    Medication.DIURETIC -> R.string.med_diuretic
    Medication.BLOOD_PRESSURE -> R.string.med_bp
    Medication.GLP1 -> R.string.med_glp1
}

private fun reasonLabel(r: GateReason) = when (r) {
    GateReason.GLUCOSE_LOWERING_MEDICATION -> R.string.reason_glucose
    GateReason.SGLT2_INHIBITOR -> R.string.reason_sglt2
    GateReason.BLOOD_PRESSURE_OR_DIURETIC -> R.string.reason_bp
    GateReason.GLP1 -> R.string.reason_glp1
    else -> R.string.reason_other
}
