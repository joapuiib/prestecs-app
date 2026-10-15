package com.fpmislata.prestecs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Accent colours from the backend's API docs, so the app matches the web.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FBF),
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF0B1A33),
)

// No dynamic colour: loan states are colour-coded and must look the same on
// every device.
@Composable
fun PrestecsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/**
 * Text colour for successful results (scan accepted, saved). Bootstrap greens
 * as on the web, picked to reach 4.5:1 contrast on the surface: the web's
 * #198754 is too light on white and too dark on the dark theme.
 */
val ColorScheme.success: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF4AC26B) else Color(0xFF146C43)

/** Background of a row that was just added: the web's `table-success` flash. */
val ColorScheme.successContainer: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF1E4D33) else Color(0xFFD1E7DD)
