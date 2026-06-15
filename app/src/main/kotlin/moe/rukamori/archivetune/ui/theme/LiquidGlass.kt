/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState

/**
 * Composition local that provides the HazeState for backdrop blur effects.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Composition local that indicates whether the Liquid Glass UI mode is active.
 * Read this in any composable that wants to apply additional glass-specific polish.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/**
 * Colour and brush constants for the Liquid Glass UI mode.
 *
 * Dark mode glass  → deep indigo-tinted frosted surfaces with white specular highlights.
 * Light mode glass → white-frosted surfaces with bright specular highlights.
 *
 * All colours are intentionally semi-transparent so that the root background gradient
 * set in [moe.rukamori.archivetune.MainActivity] bleeds through, creating the depth
 * illusion that defines the glass aesthetic.
 */
object LiquidGlassDefaults {

    // ── Surface tokens ────────────────────────────────────────────────────────

    /** Base colour for M3 `surface` when liquid glass is active. */
    fun surfaceColor(isDark: Boolean): Color = if (isDark) {
        Color(0xFF12121F).copy(alpha = 0.20f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }

    /** Colour for elevated surface container tokens. */
    fun surfaceContainerColor(isDark: Boolean): Color = if (isDark) {
        Color(0xFF1A1A2E).copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    /** Lower-elevation container — slightly more transparent. */
    fun surfaceContainerLowColor(isDark: Boolean): Color = if (isDark) {
        Color(0xFF0F0F1C).copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    /** Higher-elevation container — slightly more opaque. */
    fun surfaceContainerHighColor(isDark: Boolean): Color = if (isDark) {
        Color(0xFF1E1E30).copy(alpha = 0.25f)
    } else {
        Color.White.copy(alpha = 0.20f)
    }

    /** Lowest-elevation container. */
    fun surfaceContainerLowestColor(isDark: Boolean): Color = if (isDark) {
        Color(0xFF0A0A14).copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.06f)
    }

    /** Highest-elevation container. */
    fun surfaceContainerHighestColor(isDark: Boolean): Color = if (isDark) {
        Color(0xFF22223A).copy(alpha = 0.30f)
    } else {
        Color.White.copy(alpha = 0.28f)
    }

    // ── Decorative brushes ────────────────────────────────────────────────────

    /**
     * Specular (top-edge) highlight brush painted over a glass surface.
     * Mimics light reflecting off the top rim of a glass pane.
     */
    fun specularBrush(isDark: Boolean): Brush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.02f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.82f),
                Color.White.copy(alpha = 0.30f),
                Color.Transparent,
            )
        },
        // Only the top ~30 % of the surface carries the highlight
        startY = 0f,
        endY = Float.POSITIVE_INFINITY,
    )

    /**
     * Border brush that fades from a bright top edge down to near-invisible.
     * Applied as a 1 dp border around glass surfaces.
     */
    fun borderBrush(isDark: Boolean): Brush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.06f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.90f),
                Color.White.copy(alpha = 0.28f),
            )
        },
    )

    /** Glass backdrop tint color for Haze blurs - provides optimal frosting and readability. */
    fun glassBackdropTint(isDark: Boolean): Color = if (isDark) {
        Color(0xFF1A1A2E).copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.40f)
    }

    /**
     * Diagonal gradient used as the app-root background when Liquid Glass is active.
     * The gradient uses the current theme seed colour so it adapts to album art tinting.
     *
     * @param isDark     Whether dark mode is currently active.
     * @param themeColor The current accent/seed colour of the app theme.
     */
    fun rootBackgroundBrush(isDark: Boolean, themeColor: Color): Brush {
        return if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF050508),
                    androidx.compose.ui.graphics.lerp(Color(0xFF050508), themeColor, 0.22f),
                    Color(0xFF08080F),
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF0F4FF),
                    androidx.compose.ui.graphics.lerp(Color.White, themeColor, 0.10f),
                    Color(0xFFEBF0FA),
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            )
        }
    }
}
