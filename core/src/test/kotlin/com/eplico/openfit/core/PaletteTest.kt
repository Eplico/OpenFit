package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PaletteTest {

    @Test
    fun extremesAreBlackAndWhite() {
        val palette = TonalPalette(140.0, 50.0)
        assertEquals(0xFF000000.toInt(), palette.tone(0))
        assertEquals(0xFFFFFFFF.toInt(), palette.tone(100))
    }

    @Test
    fun tonesKeepTheirLightnessAndHue() {
        val seed = AccentColor.BLUE.seed!!
        val seedHue = ColorMath.argbToLch(seed).h
        val palette = TonalPalette(seedHue, 48.0)
        for (tone in listOf(10, 30, 40, 50, 80, 90)) {
            val lch = ColorMath.argbToLch(palette.tone(tone))
            assertEquals("lightness of tone $tone", tone.toDouble(), lch.l, 1.0)
            if (lch.c > 10) assertTrue("hue of tone $tone drifted to ${lch.h}", hueDistance(seedHue, lch.h) < 8.0)
        }
    }

    @Test
    fun everyAccentGivesReadableSchemes() {
        for (accent in AccentColor.entries.filter { it.seed != null }) {
            for (dark in listOf(false, true)) {
                val s = Palette.scheme(accent.seed!!, dark)
                val label = "${accent.label} ${if (dark) "dark" else "light"}"
                assertTrue("$label primary", ColorMath.contrast(s.primary, s.onPrimary) >= 4.5)
                assertTrue("$label container", ColorMath.contrast(s.primaryContainer, s.onPrimaryContainer) >= 4.5)
                assertTrue("$label surface", ColorMath.contrast(s.surface, s.onSurface) >= 7.0)
                assertTrue("$label variant", ColorMath.contrast(s.surfaceVariant, s.onSurfaceVariant) >= 4.5)
                assertTrue("$label error", ColorMath.contrast(s.error, s.onError) >= 4.5)
                assertEquals(5, s.heatmap.size)
            }
        }
    }

    @Test
    fun heatmapGetsMoreProminentWithEachLevel() {
        val seed = AccentColor.GREEN.seed!!
        val light = Palette.scheme(seed, dark = false)
        val lightLevels = light.heatmap.drop(1).map(ColorMath::luminance)
        assertEquals(lightLevels.sortedDescending(), lightLevels) // darker = busier on a light background
        val dark = Palette.scheme(seed, dark = true)
        val darkLevels = dark.heatmap.drop(1).map(ColorMath::luminance)
        assertEquals(darkLevels.sorted(), darkLevels) // brighter = busier on a dark background
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180) 360 - d else d
    }
}
