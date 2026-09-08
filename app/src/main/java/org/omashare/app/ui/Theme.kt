package org.omashare.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MochaBackground = Color(0xFF1E1E2E)
val MochaSurface = Color(0xFF313244)
val MochaOverlay = Color(0xFF45475A)
val MochaText = Color(0xFFCDD6F4)
val MochaMuted = Color(0xFF6C7086)
val MochaAccent = Color(0xFF89B4FA)
val MochaGreen = Color(0xFFA6E3A1)
val MochaRed = Color(0xFFF38BA8)
val MochaYellow = Color(0xFFF9E2AF)

private val OmashareColors: ColorScheme = darkColorScheme(
    primary = MochaAccent,
    onPrimary = MochaBackground,
    secondary = MochaGreen,
    onSecondary = MochaBackground,
    tertiary = MochaYellow,
    onTertiary = MochaBackground,
    background = MochaBackground,
    onBackground = MochaText,
    surface = MochaSurface,
    onSurface = MochaText,
    surfaceVariant = MochaOverlay,
    onSurfaceVariant = MochaMuted,
    error = MochaRed,
    onError = MochaBackground,
    outline = MochaOverlay,
    outlineVariant = MochaOverlay,
)

private val OmashareTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = MochaText,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        color = MochaText,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        color = MochaText,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = MochaText,
    ),
)

@Composable
fun OmashareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmashareColors,
        typography = OmashareTypography,
        content = content,
    )
}
