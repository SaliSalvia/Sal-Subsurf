package com.salsalvia.subsurf.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    inversePrimary = Teal300,

    secondary = Sage600,
    onSecondary = Color.White,
    secondaryContainer = Sage200,
    onSecondaryContainer = Color(0xFF172418),

    tertiary = Ochre700,
    onTertiary = Color.White,
    tertiaryContainer = Ochre100,
    onTertiaryContainer = Color(0xFF2B1E00),

    background = Mist50,
    onBackground = Ink900,
    surface = Paper,
    onSurface = Ink900,
    surfaceVariant = Mist200,
    onSurfaceVariant = Slate600,
    surfaceTint = Teal600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFCFA),
    surfaceContainer = Mist100,
    surfaceContainerHigh = Mist200,
    surfaceContainerHighest = Mist300,
    inverseSurface = Ink700,
    inverseOnSurface = Mist100,

    outline = Mist500,
    outlineVariant = Mist300,

    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Teal300,
    onPrimary = Teal900,
    primaryContainer = Teal700,
    onPrimaryContainer = Teal100,
    inversePrimary = Teal600,

    secondary = Sage300,
    onSecondary = Color(0xFF1B291C),
    secondaryContainer = Color(0xFF33462F),
    onSecondaryContainer = Sage200,

    tertiary = Ochre300,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF584200),
    onTertiaryContainer = Ochre100,

    background = Night900,
    onBackground = Night100,
    surface = Night850,
    onSurface = Night100,
    surfaceVariant = Night700,
    onSurfaceVariant = Night200,
    surfaceTint = Teal300,
    surfaceContainerLowest = Night950,
    surfaceContainerLow = Night900,
    surfaceContainer = Night800,
    surfaceContainerHigh = Night750,
    surfaceContainerHighest = Night700,
    inverseSurface = Night100,
    inverseOnSurface = Night800,

    outline = Night600,
    outlineVariant = Night700,

    error = ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Generous, softly rounded corners — reads as a modern consumer product rather than a tool. */
val SubsurfShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** Extra design tokens that are not part of [MaterialTheme] (spacing / elevation scale). */
object Dims {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp

    /** Height of the floating sheet handle area. */
    val handle = 26.dp
    val minTouch = 48.dp
}

@Composable
fun SubsurfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge: the system bars stay transparent and their icons flip with the theme.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = SubsurfTypography,
        shapes = SubsurfShapes,
        content = content,
    )
}
