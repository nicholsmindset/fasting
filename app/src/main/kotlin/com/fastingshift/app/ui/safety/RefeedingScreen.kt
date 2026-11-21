package com.fastingshift.app.ui.safety

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.SecondaryScaffold
import com.fastingshift.app.ui.components.SectionCard
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.safety.RefeedingTier
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RefeedingViewModel @Inject constructor(
    private val fasts: FastRepository,
    private val service: FastService,
) : ViewModel() {
    private val _fast = MutableStateFlow<Fast?>(null)
    val fast: StateFlow<Fast?> = _fast

    fun load(id: Long) {
        if (id <= 0 || _fast.value?.id == id) return
        viewModelScope.launch { _fast.value = fasts.getById(id)?.takeIf { !it.isActive } }
    }

    /** Re-opens an accidentally ended long fast, if nothing has started since. */
    fun undo(onDone: () -> Unit) {
        val f = _fast.value ?: return
        viewModelScope.launch { if (service.undoEnd(f)) onDone() else _fast.value = null }
    }
}

/**
 * Full-screen refeeding guidance (SR-5). Shown after a fast of 48h or more ends, wherever it
 * was ended, and from Settings with every tier. [tier] null means "show all tiers".
 */
@Composable
fun RefeedingRoute(tier: RefeedingTier?, fastId: Long, onDone: () -> Unit, vm: RefeedingViewModel = hiltViewModel()) {
    LaunchedEffect(fastId) { vm.load(fastId) }
    val fast by vm.fast.collectAsStateWithLifecycle()
    SecondaryScaffold(title = stringResource(R.string.refeed_title), onBack = onDone) {
        fast?.actualDuration?.let { Text(stringResource(R.string.refeed_your_fast, it.hoursMinutes()), style = MaterialTheme.typography.titleMedium) }
        Text(stringResource(R.string.refeed_intro))
        val tiers = tier?.let { listOf(it) } ?: RefeedingTier.entries
        tiers.forEach { t ->
            SectionCard(title = stringResource(tierTitle(t))) { Text(stringResource(refeedingText(t))) }
        }
        listOf(R.string.refeed_tip_water, R.string.refeed_tip_avoid, R.string.refeed_tip_signs).forEach { Text("• " + stringResource(it)) }
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refeed_done)) }
        if (fast != null) {
            TextButton(onClick = { vm.undo(onDone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refeed_undo)) }
        }
    }
}

private fun tierTitle(t: RefeedingTier) = when (t) {
    RefeedingTier.STANDARD -> R.string.refeed_tier_standard
    RefeedingTier.EXTENDED -> R.string.refeed_tier_extended
    RefeedingTier.PROLONGED -> R.string.refeed_tier_prolonged
}

fun refeedingText(tier: RefeedingTier): Int = when (tier) {
    RefeedingTier.STANDARD -> R.string.refeed_standard
    RefeedingTier.EXTENDED -> R.string.refeed_extended
    RefeedingTier.PROLONGED -> R.string.refeed_prolonged
}
