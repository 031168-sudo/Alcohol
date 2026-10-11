package com.example.alcohol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Пастельная зелёная палитра. Все роли заданы явно: Material 3 по умолчанию
 * подставляет фиолетовые оттенки в surfaceContainer* (карточки, диалоги, поля).
 * Светлая и тёмная версии следуют системной теме.
 */
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
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0EEEF),
    onTertiaryContainer = Color(0xFF0B3536),
    background = Color(0xFFE6F2E9),
    onBackground = Color(0xFF1A2A1F),
    surface = Color(0xFFFAFDFA),
    onSurface = Color(0xFF1A2A1F),
    surfaceVariant = Color(0xFFDCE8DF),
    onSurfaceVariant = Color(0xFF414E45),
    surfaceDim = Color(0xFFD5E3D9),
    surfaceBright = Color(0xFFFAFDFA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F8F3),
    surfaceContainer = Color(0xFFEAF5ED),
    surfaceContainerHigh = Color(0xFFE2EFE6),
    surfaceContainerHighest = Color(0xFFDAE8DE),
    outline = Color(0xFF717D74),
    outlineVariant = Color(0xFFC2D3C7),
    inverseSurface = Color(0xFF2C3A31),
    inverseOnSurface = Color(0xFFE6F2EA),
    inversePrimary = Color(0xFFA6D6B3),
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
    onTertiary = Color(0xFF123536),
    tertiaryContainer = Color(0xFF264B4D),
    onTertiaryContainer = Color(0xFFD0EEEF),
    background = Color(0xFF0F1712),
    onBackground = Color(0xFFDDE8E0),
    surface = Color(0xFF141E18),
    onSurface = Color(0xFFDDE8E0),
    surfaceVariant = Color(0xFF3F4B43),
    onSurfaceVariant = Color(0xFFC1CCC4),
    surfaceDim = Color(0xFF0C130F),
    surfaceBright = Color(0xFF353D37),
    surfaceContainerLowest = Color(0xFF0A100C),
    surfaceContainerLow = Color(0xFF17211B),
    surfaceContainer = Color(0xFF1B261F),
    surfaceContainerHigh = Color(0xFF232E27),
    surfaceContainerHighest = Color(0xFF2E3932),
    outline = Color(0xFF8B978F),
    outlineVariant = Color(0xFF414E45),
    inverseSurface = Color(0xFFDDE8E0),
    inverseOnSurface = Color(0xFF2A342D),
    inversePrimary = Color(0xFF4E8A62),
)

@Composable
fun AlcoholTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
