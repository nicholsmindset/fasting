package com.fastingshift.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fasting Shift's premium look: calm, iOS-inspired. Grouped light-grey backgrounds with white
 * cards (pure black with elevated greys in dark), one teal accent, generous rounded corners,
 * a large-title type scale and hairline separators. Every Material role is set so nothing falls
 * back to default purple. No wallpaper colours: the app looks the same on every phone.
 */
private val Teal = Color(0xFF1F6F62)
private val TealDark = Color(0xFF5FD3BD)

private val LightColors = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F2EC), onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF55615E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6ECEA), onSecondaryContainer = Color(0xFF111A18),
    tertiary = Color(0xFFB0661F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6CF), onTertiaryContainer = Color(0xFF2E1500),
    // Grouped background (like iOS systemGroupedBackground) with white cards on top.
    background = Color(0xFFF2F3F5), onBackground = Color(0xFF111312),
    surface = Color(0xFFF2F3F5), onSurface = Color(0xFF111312),
    surfaceVariant = Color(0xFFE5E7EA), onSurfaceVariant = Color(0xFF6B6F73),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF7F8F9),
    outline = Color(0xFFC6C8CC), outlineVariant = Color(0xFFE1E3E6),
    inverseSurface = Color(0xFF2C2E30), inverseOnSurface = Color(0xFFF2F3F5), inversePrimary = TealDark,
    error = Color(0xFFD93A2B), errorContainer = Color(0xFFFFE3DF), onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = TealDark, onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF12443B), onPrimaryContainer = Color(0xFFC4F5EA),
    secondary = Color(0xFFB4BEBB), onSecondary = Color(0xFF1C2523),
    secondaryContainer = Color(0xFF2C2F31), onSecondaryContainer = Color(0xFFE2E7E5),
    tertiary = Color(0xFFFFB878), onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF5C3A12), onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color.Black, onBackground = Color(0xFFF2F3F5),
    surface = Color.Black, onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF9A9EA3),
    surfaceTint = Color.Transparent,
    // Elevated greys like iOS secondarySystemGroupedBackground.
    surfaceContainerLowest = Color(0xFF0E0E0F), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    outline = Color(0xFF48484A), outlineVariant = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F3F5), inverseOnSurface = Color(0xFF1C1C1E), inversePrimary = Teal,
    error = Color(0xFFFF6B5E), errorContainer = Color(0xFF5C1A14), onErrorContainer = Color(0xFFFFDAD5),
)

/** iOS-like scale: bold large titles, 17sp body, semibold headings, slightly tight tracking. */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 41.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp, letterSpacing = 0.1.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FastingShiftTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
