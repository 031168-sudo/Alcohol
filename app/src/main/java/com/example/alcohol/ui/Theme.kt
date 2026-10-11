package com.example.alcohol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Пастельная зелёная палитра. Светлая и тёмная версии следуют системной теме. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4E8A62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBD6),
    onPrimaryContainer = Color(0xFF0C3A1E),
    secondary = Color(0xFF5F8B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDEFE3),
    onSecondaryContainer = Color(0xFF13331F),
    tertiary = Color(0xFF4D8A8C),
    tertiaryContainer = Color(0xFFD0EEEF),
    onTertiaryContainer = Color(0xFF0B3536),
    background = Color(0xFFF5FAF6),
    onBackground = Color(0xFF1A2A1F),
    surface = Color(0xFFF5FAF6),
    onSurface = Color(0xFF1A2A1F),
    surfaceVariant = Color(0xFFDCE8DF),
    onSurfaceVariant = Color(0xFF414E45),
    outline = Color(0xFF717D74),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6D6B3),
    onPrimary = Color(0xFF0E3A20),
    primaryContainer = Color(0xFF285238),
    onPrimaryContainer = Color(0xFFCDEBD6),
    secondary = Color(0xFFB7D0BE),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF3A4D3F),
    onSecondaryContainer = Color(0xFFDDEFE3),
    tertiary = Color(0xFF9FCFD1),
    background = Color(0xFF0F1712),
    onBackground = Color(0xFFDDE8E0),
    surface = Color(0xFF0F1712),
    onSurface = Color(0xFFDDE8E0),
    surfaceVariant = Color(0xFF3F4B43),
    onSurfaceVariant = Color(0xFFC1CCC4),
    outline = Color(0xFF8B978F),
)

@Composable
fun AlcoholTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
