package com.nexusdigital.rivamusic.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun buildDarkScheme() = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF1D0C0B),
    primaryContainer = Color(0xFF4A231C),
    onPrimaryContainer = Color(0xFFFFD9D2),
    secondary = Coral,
    onSecondary = Color(0xFF2B0A0F),
    secondaryContainer = Color(0xFF4A1F1A),
    onSecondaryContainer = Color(0xFFFFE1D9),
    tertiary = Sky,
    onTertiary = Color(0xFF2B0A0A),
    tertiaryContainer = Color(0xFF4A1F1F),
    onTertiaryContainer = Color(0xFFFFE5E1),
    background = Ink,
    onBackground = TextPrimary,
    surface = InkSoft,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextSecondary,
    outline = PanelBorder,
    outlineVariant = Color(0xFF2A1715),
    error = Danger,
    onError = Color(0xFF2A0505),
)

private fun buildLightScheme() = lightColorScheme(
    primary = Lime,
    onPrimary = Color(0xFFFFF6F2),
    primaryContainer = Color(0xFFFFD9D2),
    onPrimaryContainer = Color(0xFF3A140E),
    secondary = Coral,
    onSecondary = Color(0xFFFFF6F2),
    secondaryContainer = Color(0xFFFFE1D9),
    onSecondaryContainer = Color(0xFF3A140E),
    tertiary = Sky,
    onTertiary = Color(0xFF3A140E),
    tertiaryContainer = Color(0xFFFFE5E1),
    onTertiaryContainer = Color(0xFF3A140E),
    background = Ink,
    onBackground = TextPrimary,
    surface = InkSoft,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextSecondary,
    outline = PanelBorder,
    outlineVariant = Color(0xFFE5D3CD),
    error = Danger,
    onError = Color(0xFFFFF6F2),
)

@Composable
fun RivaMusicTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    RivaIsDark = darkTheme
    MaterialTheme(
        colorScheme = if (darkTheme) buildDarkScheme() else buildLightScheme(),
        typography = Typography,
        content = content,
    )
}
