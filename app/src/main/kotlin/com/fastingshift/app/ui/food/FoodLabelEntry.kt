package com.fastingshift.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fastingshift.app.R
import androidx.compose.ui.res.stringResource
import com.fastingshift.app.ui.design.GroupRow
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.PrimaryButton
import com.fastingshift.app.ui.design.SecondaryButton
import com.fastingshift.app.ui.ramadan.GroupedSheet
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FoodLookupViewModel(private val fetch: (String) -> LookupResult) : ViewModel() {
    constructor() : this(OpenFoodFacts::lookup)

    var code by mutableStateOf("")
        private set
    var result by mutableStateOf<LookupResult?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private var request: Job? = null
    private var lastLabel: Pair<String, LookupResult.Found>? = null

    fun edit(text: String) {
        request?.cancel()
        loading = false
        code = text.trim().take(32)
        result = null
    }

    fun lookup() {
        val gtin = code
        if (loading || !validGtin(gtin)) return
        lastLabel?.takeIf { it.first == gtin }?.let { result = it.second; return }
        result = null
        loading = true
        request = viewModelScope.launch {
            val response = withContext(Dispatchers.IO) { fetch(gtin) }
            if (response is LookupResult.Found) lastLabel = gtin to response
            result = response
            loading = false
        }
    }
}

@Composable
internal fun FoodLabelEntry(vm: FoodLookupViewModel = viewModel()) {
    var open by rememberSaveable { mutableStateOf(false) }
    InsetGroup {
        GroupRow(
            title = stringResource(R.string.food_label),
            subtitle = stringResource(R.string.food_label_summary),
            showChevron = true, onClick = { open = true },
        )
    }
    if (!open) return
    val context = LocalContext.current
    var scannerError by rememberSaveable { mutableStateOf(false) }
    GroupedSheet(stringResource(R.string.food_label), onDismiss = { vm.edit(""); open = false }) {
        Text(stringResource(R.string.food_lookup_privacy), style = MaterialTheme.typography.bodyMedium)
        SecondaryButton(stringResource(R.string.food_scan), onClick = {
            scannerError = false
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A, Barcode.FORMAT_ITF)
                .build()
            try {
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { barcode ->
                        barcode.rawValue?.let(vm::edit) ?: run { scannerError = true }
                    }
                    .addOnFailureListener { scannerError = true }
            } catch (_: IllegalStateException) {
                scannerError = true
            }
        })
        if (scannerError) StatusText(stringResource(R.string.food_scan_unavailable))
        val invalid = !validGtin(vm.code) && (vm.code.length in setOf(8, 12, 13) || vm.code.length >= 14)
        OutlinedTextField(
            value = vm.code,
            onValueChange = { scannerError = false; vm.edit(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.food_barcode)) },
            supportingText = { Text(stringResource(if (invalid) R.string.food_check_digits else R.string.food_barcode_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = invalid,
        )
        PrimaryButton(stringResource(if (vm.loading) R.string.food_searching else R.string.food_lookup),
            onClick = vm::lookup, enabled = validGtin(vm.code) && !vm.loading)
        when (val result = vm.result) {
            is LookupResult.Found -> NutritionLabel(result.label, vm.code)
            LookupResult.Missing -> StatusText(stringResource(R.string.food_unknown))
            LookupResult.SlowDown -> StatusText(stringResource(R.string.food_rate_limit))
            LookupResult.Unavailable -> StatusText(stringResource(R.string.food_offline))
            null -> Unit
        }
    }
}

@Composable
private fun StatusText(message: String) {
    Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun NutritionLabel(label: FoodLabel, code: String) {
    val uriHandler = LocalUriHandler.current
    var linkUnavailable by rememberSaveable(code) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.name ?: stringResource(R.string.food_unnamed), style = MaterialTheme.typography.titleLarge)
        label.serving?.let { Text(stringResource(R.string.food_serving_size, it)) }
        if (label.perServing.available) Amounts(stringResource(R.string.food_per_serving), label.perServing)
        if (label.perHundred.available) {
            Amounts(stringResource(if (label.explicitlyPer100ml) R.string.food_per_100ml else R.string.food_per_100), label.perHundred)
        }
        if (!label.perServing.available && !label.perHundred.available) StatusText(stringResource(R.string.food_no_nutrition))
        Text(stringResource(R.string.food_attribution), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            try { uriHandler.openUri("https://world.openfoodfacts.org/product/$code") }
            catch (_: IllegalArgumentException) { linkUnavailable = true }
        }) { Text(stringResource(R.string.food_source)) }
        if (linkUnavailable) StatusText(stringResource(R.string.food_no_browser))
    }
}

@Composable
private fun Amounts(basis: String, values: LabelAmounts) {
    val format = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = 3 }
    fun formatted(value: LabelValue) = value.modifier + format.format(value.amount)
    InsetGroup(header = basis) {
        GroupRow(
            stringResource(R.string.food_energy),
            value = values.energy?.let { stringResource(R.string.food_kcal, formatted(it)) } ?: stringResource(R.string.food_not_listed),
        )
        GroupRow(
            stringResource(R.string.food_sugars),
            value = values.sugars?.let { stringResource(R.string.food_grams, formatted(it)) } ?: stringResource(R.string.food_not_listed),
        )
    }
}
