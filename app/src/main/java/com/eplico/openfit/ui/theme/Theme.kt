package com.eplico.openfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F5A8),
    onPrimaryContainer = Color(0xFF002107),
    secondary = Color(0xFF516350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8D0),
    onSecondaryContainer = Color(0xFF0F1F10),
    tertiary = Color(0xFF39656B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF1),
    onTertiaryContainer = Color(0xFF001F23),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE5EAE3),
    surfaceContainerHighest = Color(0xFFDFE4DD),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF89D88E),
    onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF00531D),
    onPrimaryContainer = Color(0xFFA4F5A8),
    secondary = Color(0xFFB8CCB5),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD4E8D0),
    tertiary = Color(0xFFA1CED5),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF1F4D53),
    onTertiaryContainer = Color(0xFFBCEBF1),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDFE4DD),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
)

/** GitHub-style contribution greens, index 0 = no workout, 4 = busiest. */
data class HeatmapColors(val levels: List<Color>)

private val LightHeatmap = HeatmapColors(
    listOf(Color(0xFFE3E8E1), Color(0xFF9BE9A8), Color(0xFF40C463), Color(0xFF30A14E), Color(0xFF216E39)),
)

private val DarkHeatmap = HeatmapColors(
    listOf(Color(0xFF232A25), Color(0xFF0E4429), Color(0xFF006D32), Color(0xFF26A641), Color(0xFF39D353)),
)

val LocalHeatmapColors = staticCompositionLocalOf { LightHeatmap }

@Composable
fun OpenFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHeatmapColors provides if (darkTheme) DarkHeatmap else LightHeatmap) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
