package org.omashare.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OmashareCardShape = RoundedCornerShape(8.dp)

private fun OmasharePalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = background,
    secondary = green,
    onSecondary = background,
    tertiary = yellow,
    onTertiary = background,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = overlay,
    onSurfaceVariant = muted,
    error = red,
    onError = background,
    outline = overlay,
    outlineVariant = overlay,
)

private fun omashareTypography(text: Color): Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = text,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        color = text,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        color = text,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = text,
    ),
)

@Composable
fun OmashareTheme(
    theme: AppTheme = AppTheme.Mocha,
    content: @Composable () -> Unit,
) {
    val palette = theme.palette()
    CompositionLocalProvider(LocalOmasharePalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = omashareTypography(palette.text),
            content = content,
        )
    }
}
