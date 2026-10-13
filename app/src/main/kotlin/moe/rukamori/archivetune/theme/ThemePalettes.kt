/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.theme

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import moe.rukamori.archivetune.R

/**
 * A curated four-tone palette.
 *
 * Only the catalogue survives here: the picker screen this type used to live in
 * went away with the legacy ArchiveTune UI.
 */
data class ThemePalette(
    val id: String,
    val nameResId: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
    val onPrimary: Color = if (primary.luminance() > 0.5f) Color.Black else Color.White,
)


object ThemePalettes {
    val Default =
        ThemePalette(
            id = "default",
            nameResId = R.string.palette_default,
            primary = Color(0xFFED5564),
            secondary = Color(0xFFED5564),
            tertiary = Color(0xFFED5564),
            neutral = Color(0xFFED5564),
        )

    val OceanBlue =
        ThemePalette(
            id = "ocean_blue",
            nameResId = R.string.palette_ocean_blue,
            primary = Color(0xFF4A90D9),
            secondary = Color(0xFF4A90D9),
            tertiary = Color(0xFF4A90D9),
            neutral = Color(0xFF4A90D9),
        )

    val ArcticBlue =
        ThemePalette(
            id = "arctic_blue",
            nameResId = R.string.palette_arctic_blue,
            primary = Color(0xFF00BFFF),
            secondary = Color(0xFF00BFFF),
            tertiary = Color(0xFF00BFFF),
            neutral = Color(0xFF00BFFF),
        )

    val MidnightNavy =
        ThemePalette(
            id = "midnight_navy",
            nameResId = R.string.palette_midnight_navy,
            primary = Color(0xFF2C3E50),
            secondary = Color(0xFF2C3E50),
            tertiary = Color(0xFF2C3E50),
            neutral = Color(0xFF2C3E50),
        )

    val SkyBlue =
        ThemePalette(
            id = "sky_blue",
            nameResId = R.string.palette_sky_blue,
            primary = Color(0xFF87CEEB),
            secondary = Color(0xFF87CEEB),
            tertiary = Color(0xFF87CEEB),
            neutral = Color(0xFF87CEEB),
        )

    val CobaltBlue =
        ThemePalette(
            id = "cobalt_blue",
            nameResId = R.string.palette_cobalt_blue,
            primary = Color(0xFF0047AB),
            secondary = Color(0xFF0047AB),
            tertiary = Color(0xFF0047AB),
            neutral = Color(0xFF0047AB),
        )

    val ElectricBlue =
        ThemePalette(
            id = "electric_blue",
            nameResId = R.string.palette_electric_blue,
            primary = Color(0xFF7DF9FF),
            secondary = Color(0xFF7DF9FF),
            tertiary = Color(0xFF7DF9FF),
            neutral = Color(0xFF7DF9FF),
        )

    val EmeraldGreen =
        ThemePalette(
            id = "emerald_green",
            nameResId = R.string.palette_emerald_green,
            primary = Color(0xFF2ECC71),
            secondary = Color(0xFF2ECC71),
            tertiary = Color(0xFF2ECC71),
            neutral = Color(0xFF2ECC71),
        )

    val TealWave =
        ThemePalette(
            id = "teal_wave",
            nameResId = R.string.palette_teal_wave,
            primary = Color(0xFF1ABC9C),
            secondary = Color(0xFF1ABC9C),
            tertiary = Color(0xFF1ABC9C),
            neutral = Color(0xFF1ABC9C),
        )

    val ForestGreen =
        ThemePalette(
            id = "forest_green",
            nameResId = R.string.palette_forest_green,
            primary = Color(0xFF228B22),
            secondary = Color(0xFF228B22),
            tertiary = Color(0xFF228B22),
            neutral = Color(0xFF228B22),
        )

    val SpotifyGreen =
        ThemePalette(
            id = "spotify_green",
            nameResId = R.string.palette_spotify_green,
            primary = Color(0xFF1DB954),
            secondary = Color(0xFF1DB954),
            tertiary = Color(0xFF1DB954),
            neutral = Color(0xFF1DB954),
        )

    val MintFresh =
        ThemePalette(
            id = "mint_fresh",
            nameResId = R.string.palette_mint_fresh,
            primary = Color(0xFF98FF98),
            secondary = Color(0xFF98FF98),
            tertiary = Color(0xFF98FF98),
            neutral = Color(0xFF98FF98),
        )

    val OliveGarden =
        ThemePalette(
            id = "olive_garden",
            nameResId = R.string.palette_olive_garden,
            primary = Color(0xFF808000),
            secondary = Color(0xFF808000),
            tertiary = Color(0xFF808000),
            neutral = Color(0xFF808000),
        )

    val SageGreen =
        ThemePalette(
            id = "sage_green",
            nameResId = R.string.palette_sage_green,
            primary = Color(0xFF9CAF88),
            secondary = Color(0xFF9CAF88),
            tertiary = Color(0xFF9CAF88),
            neutral = Color(0xFF9CAF88),
        )

    val SunsetOrange =
        ThemePalette(
            id = "sunset_orange",
            nameResId = R.string.palette_sunset_orange,
            primary = Color(0xFFE67E22),
            secondary = Color(0xFFE67E22),
            tertiary = Color(0xFFE67E22),
            neutral = Color(0xFFE67E22),
        )

    val GoldenHour =
        ThemePalette(
            id = "golden_hour",
            nameResId = R.string.palette_golden_hour,
            primary = Color(0xFFF39C12),
            secondary = Color(0xFFF39C12),
            tertiary = Color(0xFFF39C12),
            neutral = Color(0xFFF39C12),
        )

    val WarmAmber =
        ThemePalette(
            id = "warm_amber",
            nameResId = R.string.palette_warm_amber,
            primary = Color(0xFFFFBF00),
            secondary = Color(0xFFFFBF00),
            tertiary = Color(0xFFFFBF00),
            neutral = Color(0xFFFFBF00),
        )

    val TangerineBlast =
        ThemePalette(
            id = "tangerine_blast",
            nameResId = R.string.palette_tangerine_blast,
            primary = Color(0xFFFF9800),
            secondary = Color(0xFFFF9800),
            tertiary = Color(0xFFFF9800),
            neutral = Color(0xFFFF9800),
        )

    val Peach =
        ThemePalette(
            id = "peach",
            nameResId = R.string.palette_peach,
            primary = Color(0xFFFFDAB9),
            secondary = Color(0xFFFFDAB9),
            tertiary = Color(0xFFFFDAB9),
            neutral = Color(0xFFFFDAB9),
        )

    val Mango =
        ThemePalette(
            id = "mango",
            nameResId = R.string.palette_mango,
            primary = Color(0xFFFF8243),
            secondary = Color(0xFFFF8243),
            tertiary = Color(0xFFFF8243),
            neutral = Color(0xFFFF8243),
        )

    val RoyalPurple =
        ThemePalette(
            id = "royal_purple",
            nameResId = R.string.palette_royal_purple,
            primary = Color(0xFF9B59B6),
            secondary = Color(0xFF9B59B6),
            tertiary = Color(0xFF9B59B6),
            neutral = Color(0xFF9B59B6),
        )

    val LavenderDream =
        ThemePalette(
            id = "lavender_dream",
            nameResId = R.string.palette_lavender_dream,
            primary = Color(0xFFB39DDB),
            secondary = Color(0xFFB39DDB),
            tertiary = Color(0xFFB39DDB),
            neutral = Color(0xFFB39DDB),
        )

    val GrapePurple =
        ThemePalette(
            id = "grape_purple",
            nameResId = R.string.palette_grape_purple,
            primary = Color(0xFF6B5B95),
            secondary = Color(0xFF6B5B95),
            tertiary = Color(0xFF6B5B95),
            neutral = Color(0xFF6B5B95),
        )

    val Violet =
        ThemePalette(
            id = "violet",
            nameResId = R.string.palette_violet,
            primary = Color(0xFFEE82EE),
            secondary = Color(0xFFEE82EE),
            tertiary = Color(0xFFEE82EE),
            neutral = Color(0xFFEE82EE),
        )

    val Amethyst =
        ThemePalette(
            id = "amethyst",
            nameResId = R.string.palette_amethyst,
            primary = Color(0xFF9966CC),
            secondary = Color(0xFF9966CC),
            tertiary = Color(0xFF9966CC),
            neutral = Color(0xFF9966CC),
        )

    val UltraViolet =
        ThemePalette(
            id = "ultra_violet",
            nameResId = R.string.palette_ultra_violet,
            primary = Color(0xFF645394),
            secondary = Color(0xFF645394),
            tertiary = Color(0xFF645394),
            neutral = Color(0xFF645394),
        )

    val CherryBlossom =
        ThemePalette(
            id = "cherry_blossom",
            nameResId = R.string.palette_cherry_blossom,
            primary = Color(0xFFFFB7C5),
            secondary = Color(0xFFFFB7C5),
            tertiary = Color(0xFFFFB7C5),
            neutral = Color(0xFFFFB7C5),
        )

    val RoseQuartz =
        ThemePalette(
            id = "rose_quartz",
            nameResId = R.string.palette_rose_quartz,
            primary = Color(0xFFF7CAC9),
            secondary = Color(0xFFF7CAC9),
            tertiary = Color(0xFFF7CAC9),
            neutral = Color(0xFFF7CAC9),
        )

    val MagentaPop =
        ThemePalette(
            id = "magenta_pop",
            nameResId = R.string.palette_magenta_pop,
            primary = Color(0xFFFF00FF),
            secondary = Color(0xFFFF00FF),
            tertiary = Color(0xFFFF00FF),
            neutral = Color(0xFFFF00FF),
        )

    val HotPink =
        ThemePalette(
            id = "hot_pink",
            nameResId = R.string.palette_hot_pink,
            primary = Color(0xFFFF69B4),
            secondary = Color(0xFFFF69B4),
            tertiary = Color(0xFFFF69B4),
            neutral = Color(0xFFFF69B4),
        )

    val Blush =
        ThemePalette(
            id = "blush",
            nameResId = R.string.palette_blush,
            primary = Color(0xFFDE5D83),
            secondary = Color(0xFFDE5D83),
            tertiary = Color(0xFFDE5D83),
            neutral = Color(0xFFDE5D83),
        )

    val Coral =
        ThemePalette(
            id = "coral",
            nameResId = R.string.palette_coral,
            primary = Color(0xFFFF7F50),
            secondary = Color(0xFFFF7F50),
            tertiary = Color(0xFFFF7F50),
            neutral = Color(0xFFFF7F50),
        )

    val Bubblegum =
        ThemePalette(
            id = "bubblegum",
            nameResId = R.string.palette_bubblegum,
            primary = Color(0xFFFFC1CC),
            secondary = Color(0xFFFFC1CC),
            tertiary = Color(0xFFFFC1CC),
            neutral = Color(0xFFFFC1CC),
        )

    val CrimsonRed =
        ThemePalette(
            id = "crimson_red",
            nameResId = R.string.palette_crimson_red,
            primary = Color(0xFFDC143C),
            secondary = Color(0xFFDC143C),
            tertiary = Color(0xFFDC143C),
            neutral = Color(0xFFDC143C),
        )

    val YouTubeRed =
        ThemePalette(
            id = "youtube_red",
            nameResId = R.string.palette_youtube_red,
            primary = Color(0xFFFF0000),
            secondary = Color(0xFFFF0000),
            tertiary = Color(0xFFFF0000),
            neutral = Color(0xFFFF0000),
        )

    val WineRed =
        ThemePalette(
            id = "wine_red",
            nameResId = R.string.palette_wine_red,
            primary = Color(0xFF722F37),
            secondary = Color(0xFF722F37),
            tertiary = Color(0xFF722F37),
            neutral = Color(0xFF722F37),
        )

    val RubyRed =
        ThemePalette(
            id = "ruby_red",
            nameResId = R.string.palette_ruby_red,
            primary = Color(0xFFE0115F),
            secondary = Color(0xFFE0115F),
            tertiary = Color(0xFFE0115F),
            neutral = Color(0xFFE0115F),
        )

    val Scarlet =
        ThemePalette(
            id = "scarlet",
            nameResId = R.string.palette_scarlet,
            primary = Color(0xFFFF2400),
            secondary = Color(0xFFFF2400),
            tertiary = Color(0xFFFF2400),
            neutral = Color(0xFFFF2400),
        )

    val Charcoal =
        ThemePalette(
            id = "charcoal",
            nameResId = R.string.palette_charcoal,
            primary = Color(0xFF36454F),
            secondary = Color(0xFF36454F),
            tertiary = Color(0xFF36454F),
            neutral = Color(0xFF36454F),
        )

    val Silver =
        ThemePalette(
            id = "silver",
            nameResId = R.string.palette_silver,
            primary = Color(0xFFC0C0C0),
            secondary = Color(0xFFC0C0C0),
            tertiary = Color(0xFFC0C0C0),
            neutral = Color(0xFFC0C0C0),
        )

    val Slate =
        ThemePalette(
            id = "slate",
            nameResId = R.string.palette_slate,
            primary = Color(0xFF708090),
            secondary = Color(0xFF708090),
            tertiary = Color(0xFF708090),
            neutral = Color(0xFF708090),
        )

    val Graphite =
        ThemePalette(
            id = "graphite",
            nameResId = R.string.palette_graphite,
            primary = Color(0xFF474747),
            secondary = Color(0xFF474747),
            tertiary = Color(0xFF474747),
            neutral = Color(0xFF474747),
        )

    val Terracotta =
        ThemePalette(
            id = "terracotta",
            nameResId = R.string.palette_terracotta,
            primary = Color(0xFFE2725B),
            secondary = Color(0xFFE2725B),
            tertiary = Color(0xFFE2725B),
            neutral = Color(0xFFE2725B),
        )

    val Coffee =
        ThemePalette(
            id = "coffee",
            nameResId = R.string.palette_coffee,
            primary = Color(0xFF6F4E37),
            secondary = Color(0xFF6F4E37),
            tertiary = Color(0xFF6F4E37),
            neutral = Color(0xFF6F4E37),
        )

    val Mocha =
        ThemePalette(
            id = "mocha",
            nameResId = R.string.palette_mocha,
            primary = Color(0xFF967969),
            secondary = Color(0xFF967969),
            tertiary = Color(0xFF967969),
            neutral = Color(0xFF967969),
        )

    val Sand =
        ThemePalette(
            id = "sand",
            nameResId = R.string.palette_sand,
            primary = Color(0xFFC2B280),
            secondary = Color(0xFFC2B280),
            tertiary = Color(0xFFC2B280),
            neutral = Color(0xFFC2B280),
        )

    val Clay =
        ThemePalette(
            id = "clay",
            nameResId = R.string.palette_clay,
            primary = Color(0xFFB66A50),
            secondary = Color(0xFFB66A50),
            tertiary = Color(0xFFB66A50),
            neutral = Color(0xFFB66A50),
        )

    val PastelPink =
        ThemePalette(
            id = "pastel_pink",
            nameResId = R.string.palette_pastel_pink,
            primary = Color(0xFFFFD1DC),
            secondary = Color(0xFFFFD1DC),
            tertiary = Color(0xFFFFD1DC),
            neutral = Color(0xFFFFD1DC),
        )

    val PastelBlue =
        ThemePalette(
            id = "pastel_blue",
            nameResId = R.string.palette_pastel_blue,
            primary = Color(0xFFAEC6CF),
            secondary = Color(0xFFAEC6CF),
            tertiary = Color(0xFFAEC6CF),
            neutral = Color(0xFFAEC6CF),
        )

    val PastelGreen =
        ThemePalette(
            id = "pastel_green",
            nameResId = R.string.palette_pastel_green,
            primary = Color(0xFF77DD77),
            secondary = Color(0xFF77DD77),
            tertiary = Color(0xFF77DD77),
            neutral = Color(0xFF77DD77),
        )

    val PastelYellow =
        ThemePalette(
            id = "pastel_yellow",
            nameResId = R.string.palette_pastel_yellow,
            primary = Color(0xFFFDFD96),
            secondary = Color(0xFFFDFD96),
            tertiary = Color(0xFFFDFD96),
            neutral = Color(0xFFFDFD96),
        )

    val PastelPurple =
        ThemePalette(
            id = "pastel_purple",
            nameResId = R.string.palette_pastel_purple,
            primary = Color(0xFFB19CD9),
            secondary = Color(0xFFB19CD9),
            tertiary = Color(0xFFB19CD9),
            neutral = Color(0xFFB19CD9),
        )

    val NeonGreen =
        ThemePalette(
            id = "neon_green",
            nameResId = R.string.palette_neon_green,
            primary = Color(0xFF39FF14),
            secondary = Color(0xFF39FF14),
            tertiary = Color(0xFF39FF14),
            neutral = Color(0xFF39FF14),
        )

    val NeonPink =
        ThemePalette(
            id = "neon_pink",
            nameResId = R.string.palette_neon_pink,
            primary = Color(0xFFFF10F0),
            secondary = Color(0xFFFF10F0),
            tertiary = Color(0xFFFF10F0),
            neutral = Color(0xFFFF10F0),
        )

    val NeonBlue =
        ThemePalette(
            id = "neon_blue",
            nameResId = R.string.palette_neon_blue,
            primary = Color(0xFF00F5FF),
            secondary = Color(0xFF00F5FF),
            tertiary = Color(0xFF00F5FF),
            neutral = Color(0xFF00F5FF),
        )

    val NeonOrange =
        ThemePalette(
            id = "neon_orange",
            nameResId = R.string.palette_neon_orange,
            primary = Color(0xFFFF5F1F),
            secondary = Color(0xFFFF5F1F),
            tertiary = Color(0xFFFF5F1F),
            neutral = Color(0xFFFF5F1F),
        )

    val Cyberpunk =
        ThemePalette(
            id = "cyberpunk",
            nameResId = R.string.palette_cyberpunk,
            primary = Color(0xFFFF00FF),
            secondary = Color(0xFFFF00FF),
            tertiary = Color(0xFFFF00FF),
            neutral = Color(0xFFFF00FF),
        )

    val Synthwave =
        ThemePalette(
            id = "synthwave",
            nameResId = R.string.palette_synthwave,
            primary = Color(0xFFFF6EC7),
            secondary = Color(0xFFFF6EC7),
            tertiary = Color(0xFFFF6EC7),
            neutral = Color(0xFFFF6EC7),
        )

    val Ocean =
        ThemePalette(
            id = "ocean",
            nameResId = R.string.palette_ocean,
            primary = Color(0xFF006994),
            secondary = Color(0xFF006994),
            tertiary = Color(0xFF006994),
            neutral = Color(0xFF006994),
        )

    val Forest =
        ThemePalette(
            id = "forest",
            nameResId = R.string.palette_forest,
            primary = Color(0xFF0B3D0B),
            secondary = Color(0xFF0B3D0B),
            tertiary = Color(0xFF0B3D0B),
            neutral = Color(0xFF0B3D0B),
        )

    val Autumn =
        ThemePalette(
            id = "autumn",
            nameResId = R.string.palette_autumn,
            primary = Color(0xFFD2691E),
            secondary = Color(0xFFD2691E),
            tertiary = Color(0xFFD2691E),
            neutral = Color(0xFFD2691E),
        )

    val Winter =
        ThemePalette(
            id = "winter",
            nameResId = R.string.palette_winter,
            primary = Color(0xFFADD8E6),
            secondary = Color(0xFFADD8E6),
            tertiary = Color(0xFFADD8E6),
            neutral = Color(0xFFADD8E6),
        )

    val Spring =
        ThemePalette(
            id = "spring",
            nameResId = R.string.palette_spring,
            primary = Color(0xFF98FB98),
            secondary = Color(0xFF98FB98),
            tertiary = Color(0xFF98FB98),
            neutral = Color(0xFF98FB98),
        )

    val Summer =
        ThemePalette(
            id = "summer",
            nameResId = R.string.palette_summer,
            primary = Color(0xFFFFD700),
            secondary = Color(0xFFFFD700),
            tertiary = Color(0xFFFFD700),
            neutral = Color(0xFFFFD700),
        )

    val Twilight =
        ThemePalette(
            id = "twilight",
            nameResId = R.string.palette_twilight,
            primary = Color(0xFF4B0082),
            secondary = Color(0xFF4B0082),
            tertiary = Color(0xFF4B0082),
            neutral = Color(0xFF4B0082),
        )

    val Aurora =
        ThemePalette(
            id = "aurora",
            nameResId = R.string.palette_aurora,
            primary = Color(0xFF00FF7F),
            secondary = Color(0xFF00FF7F),
            tertiary = Color(0xFF00FF7F),
            neutral = Color(0xFF00FF7F),
        )

    val Candy =
        ThemePalette(
            id = "candy",
            nameResId = R.string.palette_candy,
            primary = Color(0xFFFF69B4),
            secondary = Color(0xFFFF69B4),
            tertiary = Color(0xFFFF69B4),
            neutral = Color(0xFFFF69B4),
        )

    val Rainbow =
        ThemePalette(
            id = "rainbow",
            nameResId = R.string.palette_rainbow,
            primary = Color(0xFFFF0000),
            secondary = Color(0xFFFF0000),
            tertiary = Color(0xFFFF0000),
            neutral = Color(0xFFFF0000),
        )

    val allPalettes: List<ThemePalette> =
        listOf(
            Default,
            OceanBlue,
            ArcticBlue,
            MidnightNavy,
            SkyBlue,
            CobaltBlue,
            ElectricBlue,
            EmeraldGreen,
            TealWave,
            ForestGreen,
            SpotifyGreen,
            MintFresh,
            OliveGarden,
            SageGreen,
            SunsetOrange,
            GoldenHour,
            WarmAmber,
            TangerineBlast,
            Peach,
            Mango,
            RoyalPurple,
            LavenderDream,
            GrapePurple,
            Violet,
            Amethyst,
            UltraViolet,
            CherryBlossom,
            RoseQuartz,
            MagentaPop,
            HotPink,
            Blush,
            Coral,
            Bubblegum,
            CrimsonRed,
            YouTubeRed,
            WineRed,
            RubyRed,
            Scarlet,
            Charcoal,
            Silver,
            Slate,
            Graphite,
            Terracotta,
            Coffee,
            Mocha,
            Sand,
            Clay,
            PastelPink,
            PastelBlue,
            PastelGreen,
            PastelYellow,
            PastelPurple,
            NeonGreen,
            NeonPink,
            NeonBlue,
            NeonOrange,
            Cyberpunk,
            Synthwave,
            Ocean,
            Forest,
            Autumn,
            Winter,
            Spring,
            Summer,
            Twilight,
            Aurora,
            Candy,
            Rainbow,
        )

    fun findByPrimaryColor(colorHex: String): ThemePalette? = allPalettes.find { it.primary.toHexString() == colorHex }

    fun findById(id: String): ThemePalette? = allPalettes.find { it.id == id }

    fun getRandomPalette(): ThemePalette = allPalettes.random()

    fun generateRandomPalette(): ThemePalette {
        val random = java.util.Random()
        val primaryHue = random.nextFloat() * 360f
        val primarySaturation = 0.5f + random.nextFloat() * 0.4f
        val primaryLightness = 0.4f + random.nextFloat() * 0.25f
        val primary = hctToColor(primaryHue, primarySaturation, primaryLightness)
        val secondaryHue = (primaryHue + 30f + random.nextFloat() * 60f) % 360f
        val secondary = hctToColor(secondaryHue, primarySaturation * 0.9f, primaryLightness * 1.1f)
        val tertiaryHue = (primaryHue - 30f - random.nextFloat() * 60f + 360f) % 360f
        val tertiary = hctToColor(tertiaryHue, primarySaturation * 0.8f, primaryLightness * 0.95f)
        val neutralHue = (primaryHue + random.nextFloat() * 20f - 10f) % 360f
        val neutral = hctToColor(neutralHue, 0.1f, primaryLightness * 0.8f)
        return ThemePalette(
            id = "random_" + System.currentTimeMillis(),
            nameResId = R.string.palette_custom,
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            neutral = neutral,
        )
    }

    private fun hctToColor(
        hue: Float,
        saturation: Float,
        lightness: Float,
    ): Color {
        val hsv = floatArrayOf(hue, saturation, lightness)
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }
}
