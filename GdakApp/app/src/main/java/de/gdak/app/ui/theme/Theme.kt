package de.gdak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.gdak.app.data.settings.ThemeMode

private val LightColors: ColorScheme = lightColorScheme(
    primary = GdakGreen,
    onPrimary = Color.White,
    primaryContainer = GdakGreenContainer,
    onPrimaryContainer = GdakGreenOnContainer,
    secondary = GdakBrown,
    onSecondary = Color.White,
    secondaryContainer = GdakBrownContainer,
    onSecondaryContainer = GdakBrownOnContainer,
    background = GdakCream,
    onBackground = GdakInk,
    surface = GdakCream,
    onSurface = GdakInk,
    onSurfaceVariant = GdakBrown,
    surfaceContainer = GdakCreamContainer,
    surfaceContainerHigh = GdakCreamContainer,
    surfaceContainerHighest = GdakCreamHighest,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = GdakGreenLight,
    onPrimary = Color(0xFF1B3300),
    primaryContainer = GdakGreenDarkContainer,
    onPrimaryContainer = GdakGreenDarkOnContainer,
    secondary = GdakBrownLight,
    onSecondary = Color(0xFF3C2A1C),
    secondaryContainer = GdakBrownDarkContainer,
    onSecondaryContainer = GdakBrownDarkOnContainer,
    background = GdakDarkBackground,
    onBackground = GdakDarkInk,
    surface = GdakDarkBackground,
    onSurface = GdakDarkInk,
    onSurfaceVariant = GdakBrownLight,
    surfaceContainer = GdakDarkSurface,
    surfaceContainerHigh = GdakDarkContainerHigh,
    surfaceContainerHighest = GdakDarkContainerHighest,
)

@Composable
fun GdakTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = GdakTypography,
        content = content,
    )
}
