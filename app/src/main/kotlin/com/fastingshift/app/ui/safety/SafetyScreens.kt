package com.fastingshift.app.ui.safety

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.platform.RegionResolver
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.Spacing
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.app.ui.onboarding.CheckGroupRow
import com.fastingshift.app.ui.onboarding.HeroIcon
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.domain.safety.EmergencyNumbers
import com.fastingshift.domain.safety.Helplines
import com.fastingshift.domain.safety.Symptom
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val checkins: SymptomCheckinRepository,
    private val service: FastService,
    private val clock: Clock,
    private val settings: com.fastingshift.data.settings.SettingsRepository,
) : ViewModel() {
    fun submit(fastId: Long, answers: Set<Symptom>, onRedFlag: () -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            if (checkins.record(fastId, clock.instant(), answers)) onRedFlag() else onDone()
        }
    }

    fun endFast(then: () -> Unit) {
        viewModelScope.launch {
            val result = service.endActive()
            // A long fast stopped for a red flag still needs refeeding guidance (SR-5); the
            // Timer shows it as soon as the user is back.
            if (result?.refeeding != null) settings.setPendingRefeeding(result.fast.id)
            then()
        }
    }
}

/** The user's region for emergency numbers and helplines: SIM, then network, then locale. Works offline. */
private fun emergencyRegion(context: Context): String? = RegionResolver.countryCode(context)

/** Opens the dialler with [number]. Never crashes on devices without a phone app. */
fun dial(context: Context, number: String) {
    val digits = number.filter { it.isDigit() || it == '+' }
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.dial_unavailable, number), Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, context.getString(R.string.dial_unavailable, number), Toast.LENGTH_LONG).show()
    }
}

private val symptomSetSaver = listSaver<Set<Symptom>, String>(
    save = { set -> set.map { it.name } },
    restore = { names -> names.mapNotNull { n -> Symptom.entries.firstOrNull { it.name == n } }.toSet() },
)

/** 8-hourly symptom check-in during fasts of 24h or more (PRD F7). */
@Composable
fun CheckInRoute(fastId: Long, onRedFlag: () -> Unit, onDone: () -> Unit, vm: SafetyViewModel = hiltViewModel()) {
    var selected by rememberSaveable(stateSaver = symptomSetSaver) { mutableStateOf(emptySet<Symptom>()) }
    SecondaryScaffold(title = stringResource(R.string.checkin_title), onBack = onDone) {
        Text(stringResource(R.string.checkin_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InsetGroup(header = stringResource(R.string.checkin_group), footer = stringResource(R.string.checkin_footer)) {
            Symptom.entries.forEachIndexed { i, s ->
                if (i > 0) GroupDivider()
                CheckGroupRow(stringResource(symptomLabel(s)), s in selected, { on -> selected = if (on) selected + s else selected - s })
            }
        }
        AnimatedContent(selected.isEmpty(), label = "checkinButton") { none ->
            PrimaryButton(
                stringResource(if (none) R.string.checkin_feel_fine else R.string.checkin_submit),
                onClick = { vm.submit(fastId, selected, onRedFlag, onDone) },
                haptic = true,
            )
        }
    }
}

/**
 * Full-screen "End your fast and seek medical care" (SR-4). Works offline: no network calls,
 * the emergency number comes from the SIM or network country, and is always shown in large
 * text so it can be dialled by hand if the dialler can't open.
 */
@Composable
fun StopFastRoute(onDone: () -> Unit, vm: SafetyViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val number = remember { EmergencyNumbers.forRegion(emergencyRegion(context)) }
    val haptics = rememberHaptics()
    BackHandler { vm.endFast(onDone) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HeroIcon(Icons.Filled.Warning, size = 64.dp, container = MaterialTheme.colorScheme.error, tint = MaterialTheme.colorScheme.onError)
            Text(
                stringResource(R.string.stop_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.stop_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.stop_number_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(number, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { haptics.confirm(); dial(context, number) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stop_call, number), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Button(
                onClick = { vm.endFast(onDone) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.stop_end_fast), style = MaterialTheme.typography.labelLarge) }
            Text(stringResource(R.string.stop_reassure), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.stop_after), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** Support lines for the user's region, Singapore by default (SR-6). Tapping opens the dialler. */
@Composable
fun HelplineList() {
    val context = LocalContext.current
    val lines = remember { Helplines.forRegion(emergencyRegion(context)) }
    InsetGroup(header = stringResource(R.string.helplines_title)) {
        lines.forEachIndexed { i, h ->
            if (i > 0) GroupDivider()
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { dial(context, h.phone) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(h.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(h.phone, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
