package com.fastingshift.app.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastingshift.app.ui.design.AppCard
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PlainButton
import com.fastingshift.app.ui.design.PrimaryButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fastingshift.app.R
import com.fastingshift.app.ui.components.SecondaryScaffold
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
    private val clock: java.time.Clock,
) : ViewModel() {
    private val _fast = MutableStateFlow<Fast?>(null)
    val fast: StateFlow<Fast?> = _fast

    fun load(id: Long) {
        if (id <= 0 || _fast.value?.id == id) return
        viewModelScope.launch { _fast.value = fasts.getById(id)?.takeIf { !it.isActive } }
    }

    /** Undo is a quick "oops" right after ending, never a way to resume a fast later. */
    fun canUndo(f: Fast): Boolean =
        f.endedAt?.let { java.time.Duration.between(it, clock.instant()) <= FastService.UNDO_WINDOW } ?: false

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
        fast?.actualDuration?.let {
            Text(stringResource(R.string.refeed_your_fast, it.hoursMinutes()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.refeed_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val tiers = tier?.let { listOf(it) } ?: RefeedingTier.entries
        tiers.forEach { t ->
            AppCard {
                Text(stringResource(tierTitle(t)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(refeedingText(t)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        InsetGroup(header = stringResource(R.string.refeed_tips_title), footer = stringResource(R.string.disclaimer)) {
            listOf(
                Icons.Filled.Favorite to R.string.refeed_tip_water,
                Icons.Filled.Info to R.string.refeed_tip_avoid,
                Icons.Filled.Warning to R.string.refeed_tip_signs,
            ).forEachIndexed { i, (icon, text) ->
                if (i > 0) GroupDivider()
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        PrimaryButton(stringResource(R.string.refeed_done), onClick = onDone)
        if (fast?.let(vm::canUndo) == true) {
            PlainButton(stringResource(R.string.refeed_undo), onClick = { vm.undo(onDone) }, modifier = Modifier.fillMaxWidth())
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
