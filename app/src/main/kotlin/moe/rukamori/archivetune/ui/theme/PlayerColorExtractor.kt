/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Player color extraction system for generating gradients from album artwork
 *
 * This system analyzes album artwork and extracts vibrant, dominant colors
 * to create visually appealing gradients for the music player interface.
 */
object PlayerColorExtractor {
    /**
     * Extracts colors from a palette and creates a gradient
     *
     * @param palette The color palette extracted from album artwork
     * @param fallbackColor Fallback color to use if extraction fails
     * @return List of colors for gradient (primary, darker variant, black)
     */
    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> =
        withContext(Dispatchers.Default) {
            val allSwatches =
                listOfNotNull(
                    palette.vibrantSwatch,
                    palette.lightVibrantSwatch,
                    palette.darkVibrantSwatch,
                    palette.dominantSwatch,
                    palette.mutedSwatch,
                    palette.darkMutedSwatch,
                    palette.lightMutedSwatch,
                ).distinctBy { it.rgb }

            val rankedSwatches = allSwatches.sortedByDescending { calculateColorWeight(it) }

            val availableColors = mutableListOf<Color>()

            fun addIfUnique(
                color: Color,
                saturationFactor: Float,
            ) {
                if (!isSimilarToAny(color, availableColors)) {
                    availableColors.add(enhanceColorVividness(color, saturationFactor))
                }
            }

            for (swatch in rankedSwatches) {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                val satFactor = if (hsv[1] > 0.3f) 1.25f else 1.05f
                addIfUnique(Color(swatch.rgb), satFactor)
                if (availableColors.size >= 6) break
            }

            val totalPopulation = allSwatches.sumOf { it.population }.coerceAtLeast(1)
            val weightedExtractedSaturation =
                allSwatches
                    .sumOf { swatch ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                        (hsv[1] * swatch.population).toDouble()
                    }.toFloat() / totalPopulation.toFloat()

            val dominantColor = availableColors.firstOrNull() ?: Color(fallbackColor)
            val isGreyscaleImage = weightedExtractedSaturation < 0.22f || isNearGray(dominantColor)

            if (isGreyscaleImage) {
                availableColors.clear()
                val baseBrightness =
                    allSwatches.maxByOrNull { it.population }?.let { swatch ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                        hsv[2]
                    } ?: 0.10f
                val greyStops =
                    floatArrayOf(
                        (baseBrightness * 1.2f).coerceIn(0.06f, 0.40f),
                        (baseBrightness * 0.9f).coerceIn(0.04f, 0.28f),
                        (baseBrightness * 0.6f).coerceIn(0.02f, 0.16f),
                        (baseBrightness * 1.4f).coerceIn(0.08f, 0.44f),
                        (baseBrightness * 0.7f).coerceIn(0.03f, 0.20f),
                        (baseBrightness * 0.5f).coerceIn(0.01f, 0.12f),
                    )
                while (availableColors.size < 6) {
                    val v = greyStops[availableColors.size % greyStops.size]
                    availableColors.add(Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, v))))
                }
                return@withContext availableColors
            }

            val fallbackSeed =
                Color(fallbackColor).takeUnless { isNearGray(it) }
                    ?: palette.dominantSwatch?.let { Color(it.rgb) }?.takeUnless { isNearGray(it) }
                    ?: Color.DarkGray

            val seed = availableColors.firstOrNull() ?: fallbackSeed
            val targets = listOf(25f, -25f, 55f, -55f, 120f, -120f, 180f, 150f, -150f)
            val valueTargets = floatArrayOf(0.82f, 0.74f, 0.68f, 0.6f, 0.86f, 0.7f)

            run {
                val baseCandidates = (availableColors.toList() + seed).distinct()
                var baseIndex = 0
                var targetIndex = 0
                while (availableColors.size < 6) {
                    val baseColor = baseCandidates[baseIndex % baseCandidates.size]
                    val hueShiftDegrees = targets[targetIndex % targets.size]
                    val valueTarget = valueTargets[availableColors.size % valueTargets.size]
                    val derived =
                        tuneColorForMesh(
                            hueShift(baseColor, hueShiftDegrees),
                            saturationMin = 0.62f,
                            saturationBoost = 1.08f,
                            valueTarget = valueTarget,
                            valueMin = 0.38f,
                            valueMax = 0.9f,
                        )
                    if (!isSimilarToAny(derived, availableColors)) {
                        availableColors.add(derived)
                    }
                    baseIndex++
                    targetIndex++
                    if (baseIndex > 40) break
                }
            }

            if (availableColors.isEmpty()) {
                availableColors.add(tuneColorForMesh(fallbackSeed, 0.62f, 1.08f, 0.75f, 0.38f, 0.9f))
            }

            return@withContext availableColors
        }

    private fun enhanceColorVividness(
        color: Color,
        saturationFactor: Float = 1.4f,
    ): Color {
        val argb = color.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv[1] = (hsv[1] * saturationFactor).coerceAtMost(1.0f)
        hsv[2] = (hsv[2] * 1.02f).coerceIn(0.32f, 0.88f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun calculateColorWeight(swatch: Palette.Swatch?): Float {
        if (swatch == null) return 0f
        val population = swatch.population.toFloat()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        val saturation = hsv[1]
        val brightness = hsv[2]
        val vibrancyBonus = if (saturation > 0.3f && brightness in 0.2f..0.9f) 1.3f else 1.0f
        return population * vibrancyBonus
    }

    /**
     * Checks if two colors are similar (to avoid using nearly identical colors)
     */
    private fun isSimilarColor(
        color1: Color?,
        color2: Color?,
    ): Boolean {
        if (color1 == null || color2 == null) return false
        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        android.graphics.Color.colorToHSV(color1.toArgb(), hsv1)
        android.graphics.Color.colorToHSV(color2.toArgb(), hsv2)

        val hueDiffRaw = kotlin.math.abs(hsv1[0] - hsv2[0])
        val hueDiff = kotlin.math.min(hueDiffRaw, 360f - hueDiffRaw)
        val satDiff = kotlin.math.abs(hsv1[1] - hsv2[1])
        val valueDiff = kotlin.math.abs(hsv1[2] - hsv2[2])
        if (hueDiff < 12f && satDiff < 0.12f && valueDiff < 0.12f) return true

        val threshold = 28
        val r1 = (color1.red * 255).toInt()
        val g1 = (color1.green * 255).toInt()
        val b1 = (color1.blue * 255).toInt()
        val r2 = (color2.red * 255).toInt()
        val g2 = (color2.green * 255).toInt()
        val b2 = (color2.blue * 255).toInt()

        return kotlin.math.abs(r1 - r2) < threshold &&
            kotlin.math.abs(g1 - g2) < threshold &&
            kotlin.math.abs(b1 - b2) < threshold
    }

    private fun isSimilarToAny(
        color: Color,
        colors: List<Color>,
    ): Boolean = colors.any { isSimilarColor(color, it) }

    private fun hueShift(
        color: Color,
        degrees: Float,
    ): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun tuneColorForMesh(
        color: Color,
        saturationMin: Float,
        saturationBoost: Float,
        valueTarget: Float,
        valueMin: Float,
        valueMax: Float,
    ): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv[1] = (kotlin.math.max(hsv[1], saturationMin) * saturationBoost).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.85f + valueTarget * 0.15f).coerceIn(valueMin, valueMax)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun isNearGray(color: Color): Boolean {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[1] < 0.15f || hsv[2] < 0.08f
    }

    /**
     * Configuration constants for color extraction
     */
    object Config {
        const val MAX_COLOR_COUNT = 32
        const val BITMAP_AREA = 8000
        const val IMAGE_SIZE = 128
    }
}
