package com.fastingshift.app.ui.design

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.fastingshift.app.R

/** Use the supplied favicon for small placements and the full symbol on larger surfaces. */
@Composable
internal fun NicholsMark(modifier: Modifier, compact: Boolean = false) {
    val resource = when {
        compact -> R.drawable.nichols_n_badge
        MaterialTheme.colorScheme.background.luminance() < 0.5f -> R.drawable.nichols_n_mark_reversed
        else -> R.drawable.nichols_n_mark
    }
    Image(painterResource(resource), contentDescription = null, modifier = modifier)
}
