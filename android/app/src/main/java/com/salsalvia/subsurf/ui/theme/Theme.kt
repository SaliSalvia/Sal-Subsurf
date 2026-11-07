package com.salsalvia.subsurf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF146356)
private val TealDark = Color(0xFF0C4A40)
private val TealLight = Color(0xFF63A99C)
private val Earth = Color(0xFF8A5A2B)
private val Moss = Color(0xFF546E3C)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = Color(0xFF06231E),
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7C9),
    onSecondaryContainer = Color(0xFF1B2410),
    tertiary = Earth,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3DFC9),
    onTertiaryContainer = Color(0xFF2E1A05),
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF191C1B),
    surface = Color.White,
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE0E7E3),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF06231E),
    primaryContainer = Teal,
    onPrimaryContainer = Color(0xFFCDEDE4),
    secondary = Color(0xFFB5CBA0),
    onSecondary = Color(0xFF22301A),
    tertiary = Color(0xFFE0BE99),
    onTertiary = Color(0xFF2E1A05),
    background = Color(0xFF121413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF181B1A),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun SubsurfTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
