package com.eplico.openfit.core

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Accent colours the user can pick. [WALLPAPER] follows the system's dynamic colours (Android 12+). */
enum class AccentColor(val label: String, val seed: Int?) {
    GREEN("Green", 0xFF2E7D32.toInt()),
    TEAL("Teal", 0xFF00796B.toInt()),
    BLUE("Blue", 0xFF1565C0.toInt()),
    PURPLE("Purple", 0xFF6A4FB3.toInt()),
    PINK("Pink", 0xFFC2185B.toInt()),
    RED("Red", 0xFFC62828.toInt()),
    ORANGE("Orange", 0xFFEF6C00.toInt()),
    WALLPAPER("Wallpaper", null),
}

/**
 * Colour roles for one theme, as ARGB ints. Mirrors the Material 3 roles plus five heatmap
 * shades (index 0 = no workout, 4 = busiest).
 */
data class SchemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val outline: Int,
    val outlineVariant: Int,
    val surfaceDim: Int,
    val surfaceBright: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val heatmap: List<Int>,
)

/**
 * Builds Material-style colour schemes from a single seed colour. Tones are CIELAB lightness
 * (0 = black, 100 = white), which is what Material's tone scale is based on; hue and chroma come
 * from the seed, with chroma reduced where a tone can't show it.
 */
object Palette {

    fun scheme(seed: Int, dark: Boolean): SchemeColors {
        val (_, seedChroma, hue) = ColorMath.argbToLch(seed)
        val primary = TonalPalette(hue, seedChroma.coerceIn(40.0, 64.0))
        val secondary = TonalPalette(hue, 18.0)
        val tertiary = TonalPalette((hue + 60.0) % 360.0, 28.0)
        val neutral = TonalPalette(hue, 4.0)
        val neutralVariant = TonalPalette(hue, 9.0)
        val error = TonalPalette(30.0, 70.0)

        return if (!dark) {
            SchemeColors(
                primary = primary.tone(40), onPrimary = primary.tone(100),
                primaryContainer = primary.tone(90), onPrimaryContainer = primary.tone(10),
                inversePrimary = primary.tone(80),
                secondary = secondary.tone(40), onSecondary = secondary.tone(100),
                secondaryContainer = secondary.tone(90), onSecondaryContainer = secondary.tone(10),
                tertiary = tertiary.tone(40), onTertiary = tertiary.tone(100),
                tertiaryContainer = tertiary.tone(90), onTertiaryContainer = tertiary.tone(10),
                error = error.tone(40), onError = error.tone(100),
                errorContainer = error.tone(90), onErrorContainer = error.tone(10),
                background = neutral.tone(98), onBackground = neutral.tone(10),
                surface = neutral.tone(98), onSurface = neutral.tone(10),
                surfaceVariant = neutralVariant.tone(90), onSurfaceVariant = neutralVariant.tone(30),
                inverseSurface = neutral.tone(20), inverseOnSurface = neutral.tone(95),
                outline = neutralVariant.tone(50), outlineVariant = neutralVariant.tone(80),
                surfaceDim = neutral.tone(87), surfaceBright = neutral.tone(98),
                surfaceContainerLowest = neutral.tone(100), surfaceContainerLow = neutral.tone(96),
                surfaceContainer = neutral.tone(94), surfaceContainerHigh = neutral.tone(92),
                surfaceContainerHighest = neutral.tone(90),
                heatmap = listOf(neutralVariant.tone(91), primary.tone(82), primary.tone(67), primary.tone(52), primary.tone(36)),
            )
        } else {
            SchemeColors(
                primary = primary.tone(80), onPrimary = primary.tone(20),
                primaryContainer = primary.tone(30), onPrimaryContainer = primary.tone(90),
                inversePrimary = primary.tone(40),
                secondary = secondary.tone(80), onSecondary = secondary.tone(20),
                secondaryContainer = secondary.tone(30), onSecondaryContainer = secondary.tone(90),
                tertiary = tertiary.tone(80), onTertiary = tertiary.tone(20),
                tertiaryContainer = tertiary.tone(30), onTertiaryContainer = tertiary.tone(90),
                error = error.tone(80), onError = error.tone(20),
                errorContainer = error.tone(30), onErrorContainer = error.tone(90),
                background = neutral.tone(6), onBackground = neutral.tone(90),
                surface = neutral.tone(6), onSurface = neutral.tone(90),
                surfaceVariant = neutralVariant.tone(30), onSurfaceVariant = neutralVariant.tone(80),
                inverseSurface = neutral.tone(90), inverseOnSurface = neutral.tone(20),
                outline = neutralVariant.tone(60), outlineVariant = neutralVariant.tone(30),
                surfaceDim = neutral.tone(6), surfaceBright = neutral.tone(24),
                surfaceContainerLowest = neutral.tone(4), surfaceContainerLow = neutral.tone(10),
                surfaceContainer = neutral.tone(12), surfaceContainerHigh = neutral.tone(17),
                surfaceContainerHighest = neutral.tone(22),
                heatmap = listOf(neutral.tone(17), primary.tone(28), primary.tone(42), primary.tone(60), primary.tone(78)),
            )
        }
    }

    /** Heatmap shades built around an arbitrary colour (used with wallpaper colours). */
    fun heatmap(seed: Int, dark: Boolean): List<Int> = scheme(seed, dark).heatmap
}

/** Every tone of one hue at (up to) one chroma. */
class TonalPalette(private val hue: Double, private val chroma: Double) {
    private val cache = HashMap<Int, Int>()

    fun tone(tone: Int): Int = cache.getOrPut(tone) { ColorMath.lchToArgbInGamut(tone.toDouble(), chroma, hue) }
}

/** sRGB ⇄ CIELAB / LCh conversions (D65 white point). */
object ColorMath {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val EPSILON = 6.0 / 29.0

    data class Lch(val l: Double, val c: Double, val h: Double)

    /** "#RRGGBB" for an opaque ARGB colour. */
    fun toHex(argb: Int): String = "#%06X".format(java.util.Locale.ROOT, argb and 0xFFFFFF)

    /** Parses "#RRGGBB" / "RRGGBB" (or the short "#RGB") into an opaque ARGB colour, or null. */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        val full = when {
            digits.length == 3 -> digits.map { "$it$it" }.joinToString("")
            digits.length == 6 -> digits
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000.toInt() or full.toInt(16)
    }

    fun argbToLch(argb: Int): Lch {
        val r = toLinear((argb shr 16) and 0xFF)
        val g = toLinear((argb shr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        val fx = f(x / XN)
        val fy = f(y / YN)
        val fz = f(z / ZN)
        val l = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        val hue = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360.0 else it }
        return Lch(l, hypot(a, bb), hue)
    }

    /** The colour at lightness [l] and [hue] with the most chroma (up to [chroma]) sRGB can show. */
    fun lchToArgbInGamut(l: Double, chroma: Double, hue: Double): Int {
        if (l <= 0.0) return 0xFF000000.toInt()
        if (l >= 100.0) return 0xFFFFFFFF.toInt()
        lchToArgb(l, chroma, hue)?.let { return it }
        var low = 0.0
        var high = chroma
        repeat(24) {
            val mid = (low + high) / 2
            if (lchToArgb(l, mid, hue) != null) low = mid else high = mid
        }
        return lchToArgb(l, low, hue) ?: grey(l)
    }

    /** Null when the colour is outside sRGB. */
    fun lchToArgb(l: Double, c: Double, h: Double): Int? {
        val a = c * cos(Math.toRadians(h))
        val b = c * sin(Math.toRadians(h))
        val fy = (l + 16.0) / 116.0
        val x = XN * fInverse(fy + a / 500.0)
        val y = YN * fInverse(fy)
        val z = ZN * fInverse(fy - b / 200.0)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val tolerance = 1e-4
        if (listOf(r, g, bl).any { it < -tolerance || it > 1 + tolerance }) return null
        return argb(fromLinear(r), fromLinear(g), fromLinear(bl))
    }

    /** WCAG relative luminance, 0..1. */
    fun luminance(argb: Int): Double {
        val r = toLinear((argb shr 16) and 0xFF)
        val g = toLinear((argb shr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG contrast ratio between two colours, 1..21. */
    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun grey(l: Double): Int {
        val y = YN * fInverse((l + 16.0) / 116.0)
        val v = fromLinear(y)
        return argb(v, v, v)
    }

    private fun f(t: Double) = if (t > EPSILON.pow(3)) cbrt(t) else t / (3 * EPSILON * EPSILON) + 4.0 / 29.0

    private fun fInverse(t: Double) = if (t > EPSILON) t * t * t else 3 * EPSILON * EPSILON * (t - 4.0 / 29.0)

    private fun toLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun fromLinear(value: Double): Int {
        val c = value.coerceIn(0.0, 1.0)
        val encoded = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (encoded * 255.0).roundToInt().coerceIn(0, 255)
    }

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
