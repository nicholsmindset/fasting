package com.fastingshift.app.ui.theme

import androidx.annotation.StyleRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import com.fastingshift.app.R

/** Native pickers need an explicit dialog theme when the app overrides the device appearance. */
@StyleRes
@Composable
internal fun pickerDialogTheme(): Int =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) R.style.FastPickerDark else R.style.FastPickerLight
