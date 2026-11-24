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

// The launcher mark uses the original emerald. The lightened shade keeps the
// same actions legible on the dark reading surface.
private val Emerald = Color(0xFF007A56)
private val LightEmerald = Color(0xFF83DAB8)

private val LightColors = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EDE3), onPrimaryContainer = Color(0xFF104A35),
    secondary = Color(0xFF495E53), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8DF), onSecondaryContainer = Color(0xFF263A30),
    tertiary = Color(0xFF915012), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E4D2), onTertiaryContainer = Color(0xFF472A12),
    background = Color(0xFFF7F6F0), onBackground = Color(0xFF18281F),
    surface = Color(0xFFF7F6F0), onSurface = Color(0xFF18281F),
    surfaceVariant = Color(0xFFE8EAE1), onSurfaceVariant = Color(0xFF526057),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFEFA), surfaceContainerLow = Color(0xFFFAFAF4),
    surfaceContainer = Color(0xFFFFFEFA), surfaceContainerHigh = Color(0xFFF2F2EA),
    surfaceContainerHighest = Color(0xFFE8EAE1),
    outline = Color(0xFF778378), outlineVariant = Color(0xFFD5DACF),
    inverseSurface = Color(0xFF28382F), inverseOnSurface = Color(0xFFF5F5ED), inversePrimary = LightEmerald,
    error = Color(0xFFB3352B), onError = Color.White,
    errorContainer = Color(0xFFFFE3DF), onErrorContainer = Color(0xFF470C08),
)

private val DarkColors = darkColorScheme(
    primary = LightEmerald, onPrimary = Color(0xFF053728),
    primaryContainer = Color(0xFF24473A), onPrimaryContainer = Color(0xFFC3EFDB),
    secondary = Color(0xFFBACABE), onSecondary = Color(0xFF14291E),
    secondaryContainer = Color(0xFF2A3B30), onSecondaryContainer = Color(0xFFDFEBE1),
    tertiary = Color(0xFFEBBA8C), onTertiary = Color(0xFF46270B),
    tertiaryContainer = Color(0xFF4C3826), onTertiaryContainer = Color(0xFFFFDFC1),
    background = Color(0xFF111814), onBackground = Color(0xFFF1F2E9),
    surface = Color(0xFF111814), onSurface = Color(0xFFF1F2E9),
    surfaceVariant = Color(0xFF2B352E), onSurfaceVariant = Color(0xFFB0BDB2),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF141D17), surfaceContainerLow = Color(0xFF172019),
    surfaceContainer = Color(0xFF1C261F), surfaceContainerHigh = Color(0xFF263229),
    surfaceContainerHighest = Color(0xFF2D3A31),
    outline = Color(0xFF859486), outlineVariant = Color(0xFF364339),
    inverseSurface = Color(0xFFE7ECE1), inverseOnSurface = Color(0xFF1A241D), inversePrimary = Emerald,
    error = Color(0xFFFFA599), onError = Color(0xFF561D16),
    errorContainer = Color(0xFF542A24), onErrorContainer = Color(0xFFFFDAD5),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.2).sp, lineHeight = 43.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.7).sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.1.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
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
