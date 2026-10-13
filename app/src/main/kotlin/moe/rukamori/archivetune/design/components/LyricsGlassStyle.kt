/*
 * ArchiveTune (2026)
 * ﾂｩ Rukamori 窶・github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import moe.rukamori.archivetune.R

@Immutable
data class LyricsGlassStyle(
    @StringRes val labelRes: Int,
    val surfaceTint: Color,
    val surfaceAlpha: Float,
    val textColor: Color,
    val secondaryTextColor: Color,
    val overlayColor: Color,
    val overlayAlpha: Float,
    val isDark: Boolean,
    val backgroundDimAlpha: Float = 0.3f,
) {
    companion object {
        val FrostedDark =
            LyricsGlassStyle(
                labelRes = R.string.lyrics_share_style_frosted_dark,
                surfaceTint = Color.Black,
                surfaceAlpha = 0.35f,
                textColor = Color.White,
                secondaryTextColor = Color.White.copy(alpha = 0.7f),
                overlayColor = Color.Black,
                overlayAlpha = 0.25f,
                isDark = true,
                backgroundDimAlpha = 0.35f,
            )

        val FrostedLight =
            LyricsGlassStyle(
                labelRes = R.string.lyrics_share_style_frosted_light,
                surfaceTint = Color.White,
                surfaceAlpha = 0.45f,
                textColor = Color(0xFF1A1A1A),
                secondaryTextColor = Color(0xFF1A1A1A).copy(alpha = 0.65f),
                overlayColor = Color.White,
                overlayAlpha = 0.35f,
                isDark = false,
                backgroundDimAlpha = 0.15f,
            )

        val ClearGlass =
            LyricsGlassStyle(
                labelRes = R.string.lyrics_share_style_clear_glass,
                surfaceTint = Color.White,
                surfaceAlpha = 0.15f,
                textColor = Color.White,
                secondaryTextColor = Color.White.copy(alpha = 0.75f),
                overlayColor = Color.White,
                overlayAlpha = 0.08f,
                isDark = true,
                backgroundDimAlpha = 0.2f,
            )

        val DeepBlur =
            LyricsGlassStyle(
                labelRes = R.string.lyrics_share_style_deep_blur,
                surfaceTint = Color(0xFF0A0A14),
                surfaceAlpha = 0.55f,
                textColor = Color.White,
                secondaryTextColor = Color.White.copy(alpha = 0.6f),
                overlayColor = Color(0xFF0A0A14),
                overlayAlpha = 0.4f,
                isDark = true,
                backgroundDimAlpha = 0.5f,
            )

        val VividGlow =
            LyricsGlassStyle(
                labelRes = R.string.lyrics_share_style_vivid_glow,
                surfaceTint = Color(0xFFFF6B9D),
                surfaceAlpha = 0.2f,
                textColor = Color.White,
                secondaryTextColor = Color.White.copy(alpha = 0.8f),
                overlayColor = Color(0xFFFF6B9D),
                overlayAlpha = 0.12f,
                isDark = true,
                backgroundDimAlpha = 0.25f,
            )

        val allPresets = listOf(FrostedDark, FrostedLight, ClearGlass, DeepBlur, VividGlow)

        fun fromPalette(palette: Palette): LyricsGlassStyle {
            val vibrantSwatch =
                palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.darkVibrantSwatch
                    ?: palette.mutedSwatch

            val dominantSwatch = palette.dominantSwatch

            val tintColor = vibrantSwatch?.let { Color(it.rgb) } ?: Color(0xFF6366F1)
            val bgDominant = dominantSwatch?.let { Color(it.rgb) } ?: Color.Black

            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(bgDominant.toArgb(), hsv)
            val isDarkBackground = hsv[2] < 0.5f

            return LyricsGlassStyle(
                labelRes = R.string.lyrics_share_style_album_tint,
                surfaceTint = tintColor.copy(alpha = 0.6f),
                surfaceAlpha = if (isDarkBackground) 0.25f else 0.3f,
                textColor = if (isDarkBackground) Color.White else Color(0xFF1A1A1A),
                secondaryTextColor = if (isDarkBackground) Color.White.copy(alpha = 0.7f) else Color(0xFF1A1A1A).copy(alpha = 0.65f),
                overlayColor = tintColor.copy(alpha = 0.3f),
                overlayAlpha = 0.15f,
                isDark = isDarkBackground,
                backgroundDimAlpha = if (isDarkBackground) 0.3f else 0.15f,
            )
        }
    }
}