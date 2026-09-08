package org.omashare.app.ui

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppTheme(val label: String) {
    Mocha("Catppuccin Mocha"),
    Monokai("Monokai"),
    ;

    companion object {
        fun fromId(id: String): AppTheme =
            entries.find { it.name == id } ?: Mocha
    }
}

data class OmasharePalette(
    val background: Color,
    val surface: Color,
    val overlay: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
)

val MochaPalette = OmasharePalette(
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF313244),
    overlay = Color(0xFF45475A),
    text = Color(0xFFCDD6F4),
    muted = Color(0xFF6C7086),
    accent = Color(0xFF89B4FA),
    green = Color(0xFFA6E3A1),
    red = Color(0xFFF38BA8),
    yellow = Color(0xFFF9E2AF),
)

val MonokaiPalette = OmasharePalette(
    background = Color(0xFF272822),
    surface = Color(0xFF3E3D32),
    overlay = Color(0xFF49483E),
    text = Color(0xFFF8F8F2),
    muted = Color(0xFF75715E),
    accent = Color(0xFF66D9EF),
    green = Color(0xFFA6E22E),
    red = Color(0xFFF92672),
    yellow = Color(0xFFE6DB74),
)

fun AppTheme.palette(): OmasharePalette = when (this) {
    AppTheme.Mocha -> MochaPalette
    AppTheme.Monokai -> MonokaiPalette
}

val LocalOmasharePalette = compositionLocalOf { MochaPalette }

val MochaBackground: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.background

val MochaSurface: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.surface

val MochaOverlay: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.overlay

val MochaText: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.text

val MochaMuted: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.muted

val MochaAccent: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.accent

val MochaGreen: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.green

val MochaRed: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.red

val MochaYellow: Color
    @androidx.compose.runtime.Composable get() = LocalOmasharePalette.current.yellow

object ThemePreferences {
    private const val PREFS = "omashare_prefs"
    private const val KEY_THEME = "theme"

    fun load(context: Context): AppTheme {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, AppTheme.Mocha.name)
            ?: AppTheme.Mocha.name
        return AppTheme.fromId(id)
    }

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }
}
