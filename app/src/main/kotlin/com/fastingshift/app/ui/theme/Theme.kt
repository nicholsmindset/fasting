package com.fastingshift.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fasting Shift brand palette: deep teal with neutral green-grey surfaces. Every role is set
 * so nothing falls back to Material's default purple. Dynamic (wallpaper) colour is not used,
 * so the app looks the same on every phone and matches the store screenshots.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E46), onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E8DC), onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E1), onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF8A5A2B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0), onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF7FAF8), onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF7FAF8), onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4946),
    surfaceTint = Color(0xFF1F4E46),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4F2),
    surfaceContainer = Color(0xFFEBEFED), surfaceContainerHigh = Color(0xFFE5E9E7),
    surfaceContainerHighest = Color(0xFFE0E3E1),
    outline = Color(0xFF6F7976), outlineVariant = Color(0xFFBEC9C5),
    inverseSurface = Color(0xFF2D3130), inverseOnSurface = Color(0xFFEEF2EF), inversePrimary = Color(0xFF8FCFC2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCFC2), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1F4E46), onPrimaryContainer = Color(0xFFB8E8DC),
    secondary = Color(0xFFB1CCC5), onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46), onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFFFB878), onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C4316), onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF0F1513), onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1513), onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946), onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceTint = Color(0xFF8FCFC2),
    surfaceContainerLowest = Color(0xFF0A0F0E), surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4946),
    inverseSurface = Color(0xFFDEE4E1), inverseOnSurface = Color(0xFF2D3130), inversePrimary = Color(0xFF1F4E46),
)

@Composable
fun FastingShiftTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
