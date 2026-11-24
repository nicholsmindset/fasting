package com.fastingshift.app.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.platform.RegionResolver
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.safety.EmergencyNumbers
import com.fastingshift.domain.safety.GateReason
import com.fastingshift.domain.safety.GateResult
import com.fastingshift.domain.safety.ScreeningEvaluator
import com.fastingshift.domain.service.ScreeningRecord
import com.fastingshift.domain.service.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

object RamadanSafety {
    /**
     * Show the card when Ramadan mode is on and the latest screening shows a risk of low blood
     * sugar: glucose-lowering medication (insulin or a sulfonylurea) or type 1 diabetes. Type 1
     * blocks fasting in the app, but someone may still fast Ramadan, so they see it too.
     */
    fun shouldShow(ramadanEnabled: Boolean, screening: ScreeningRecord?): Boolean {
        if (!ramadanEnabled || screening == null) return false
        val reasons = ScreeningEvaluator.evaluate(screening.answers).reasons
        return GateReason.GLUCOSE_LOWERING_MEDICATION in reasons || GateReason.TYPE_1_DIABETES in reasons
    }
}

@HiltViewModel
class RamadanSafetyViewModel @Inject constructor(settings: SettingsRepository, screening: ScreeningRepository) : ViewModel() {
    val visible: StateFlow<Boolean> = combine(settings.settings, screening.observeLatest()) { s, r -> RamadanSafety.shouldShow(s.ramadanEnabled, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

/** Education-only Ramadan safety note for people on glucose-lowering medication. Hidden otherwise. */
@Composable
fun RamadanSafetyCard(vm: RamadanSafetyViewModel = hiltViewModel()) {
    val visible by vm.visible.collectAsStateWithLifecycle()
    if (visible) RamadanSafetyContent()
}

@Composable
fun RamadanSafetyContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val number = remember { EmergencyNumbers.forRegion(RegionResolver.countryCode(context)) }
    AppCard(modifier.testTag("ramadan_safety")) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.ramadan_safety_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.ramadan_safety_clinician), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.ramadan_safety_break_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.ramadan_safety_break_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.ramadan_safety_allowed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            val action = if (number == null) stringResource(R.string.stop_local_emergency)
                else stringResource(R.string.ramadan_safety_emergency, number)
            PlainButton(action, onClick = { dial(context, number) })
        }
    }
}
