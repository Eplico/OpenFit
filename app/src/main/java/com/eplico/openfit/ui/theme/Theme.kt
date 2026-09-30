package com.eplico.openfit.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.eplico.openfit.core.AccentColor
import com.eplico.openfit.core.Palette
import com.eplico.openfit.core.SchemeColors
import com.eplico.openfit.core.Trophy

/** GitHub-style contribution shades, index 0 = no workout, 4 = busiest. */
data class HeatmapColors(val levels: List<Color>)

val LocalHeatmapColors = staticCompositionLocalOf {
    HeatmapColors(Palette.scheme(AccentColor.GREEN.seed!!, dark = false).heatmap.map(::Color))
}

/** Colours for the trophy badges beside record sets. */
data class TrophyColors(private val picked: Map<Trophy, Int> = emptyMap()) {
    operator fun get(trophy: Trophy): Color = Color(picked[trophy] ?: trophy.defaultColor)
}

val LocalTrophyColors = staticCompositionLocalOf { TrophyColors() }

/** True when the wallpaper-based ("Material You") colours are available on this device. */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
val supportsWallpaperColors: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun OpenFitTheme(
    accent: AccentColor = AccentColor.GREEN,
    trophyColors: Map<Trophy, Int> = emptyMap(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val (colorScheme, heatmap) = remember(accent, darkTheme) {
        if (accent == AccentColor.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            scheme to Palette.heatmap(scheme.primary.toArgb(), darkTheme)
        } else {
            val seed = accent.seed ?: AccentColor.GREEN.seed!!
            val generated = Palette.scheme(seed, darkTheme)
            generated.toColorScheme(darkTheme) to generated.heatmap
        }
    }
    CompositionLocalProvider(
        LocalHeatmapColors provides HeatmapColors(heatmap.map(::Color)),
        LocalTrophyColors provides TrophyColors(trophyColors),
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

private fun SchemeColors.toColorScheme(dark: Boolean): ColorScheme {
    fun c(argb: Int) = Color(argb)
    return if (dark) {
        darkColorScheme(
            primary = c(primary), onPrimary = c(onPrimary),
            primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary), onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary), onTertiary = c(onTertiary),
            tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(background), onBackground = c(onBackground),
            surface = c(surface), onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
            surfaceTint = c(primary),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
            error = c(error), onError = c(onError),
            errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
            outline = c(outline), outlineVariant = c(outlineVariant),
            surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim),
            surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest), surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest),
        )
    } else {
        lightColorScheme(
            primary = c(primary), onPrimary = c(onPrimary),
            primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary), onSecondary = c(onSecondary),
            secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary), onTertiary = c(onTertiary),
            tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(background), onBackground = c(onBackground),
            surface = c(surface), onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
            surfaceTint = c(primary),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
            error = c(error), onError = c(onError),
            errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
            outline = c(outline), outlineVariant = c(outlineVariant),
            surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim),
            surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest), surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest),
        )
    }
}
