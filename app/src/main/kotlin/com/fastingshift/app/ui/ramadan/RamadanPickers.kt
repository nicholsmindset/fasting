package com.fastingshift.app.ui.ramadan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fastingshift.app.R
import com.fastingshift.app.ui.design.GroupDivider
import com.fastingshift.app.ui.design.InsetGroup
import com.fastingshift.app.ui.design.rememberHaptics
import com.fastingshift.domain.ramadan.Cities
import com.fastingshift.domain.ramadan.City
import com.fastingshift.domain.ramadan.PrayerMethod

/**
 * A bottom sheet with an iOS-style title bar ("Done" on the right) on the grouped background.
 * [fullHeight] opens straight to full height (for long, searchable lists).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedSheet(
    title: String,
    onDismiss: () -> Unit,
    fullHeight: Boolean = false,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().then(if (fullHeight) Modifier.fillMaxHeight() else Modifier).navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
            }
            Column(
                Modifier.fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** A single focus target: the row owns selection, and the marker only displays it. */
@Composable
internal fun RadioChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { haptics.tick(); onClick() })
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** Full-height, searchable list of [Cities.all] grouped by country. */
@Composable
fun CityPickerSheet(selectedId: String?, onPick: (City) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(query) { filterCities(Cities.all, query) }
    GroupedSheet(title = stringResource(R.string.ramadan_city), onDismiss = onDismiss, fullHeight = true, scrollable = false) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.ramadan_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.ramadan_no_cities, query.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
            items(groups, key = { it.first }) { (country, cities) ->
                InsetGroup(header = country) {
                    cities.forEachIndexed { i, city ->
                        if (i > 0) GroupDivider()
                        RadioChoiceRow(city.name, selected = city.id == selectedId, onClick = { onPick(city) })
                    }
                }
            }
        }
    }
}

@Composable
fun methodDescription(method: PrayerMethod): String = stringResource(
    when (method) {
        PrayerMethod.MUIS -> R.string.ramadan_method_muis
        PrayerMethod.JAKIM -> R.string.ramadan_method_jakim
        PrayerMethod.KEMENAG -> R.string.ramadan_method_kemenag
        PrayerMethod.MWL -> R.string.ramadan_method_mwl
    },
)

/** MUIS / JAKIM / Kemenag / MWL, with the city's usual method marked. */
@Composable
fun MethodPickerSheet(selected: PrayerMethod, city: City?, onPick: (PrayerMethod) -> Unit, onDismiss: () -> Unit) {
    GroupedSheet(title = stringResource(R.string.ramadan_method), onDismiss = onDismiss) {
        val usual = city?.let(Cities::defaultMethod)
        InsetGroup(modifier = Modifier.selectableGroup(), footer = stringResource(R.string.ramadan_method_footer)) {
            PrayerMethod.entries.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                val subtitle = methodDescription(m) +
                    if (m == usual && city != null) " · " + stringResource(R.string.ramadan_method_recommended, RamadanText.countryName(city.country)) else ""
                RadioChoiceRow(RamadanText.methodName(m), selected = m == selected, onClick = { onPick(m) }, subtitle = subtitle)
            }
        }
    }
}
