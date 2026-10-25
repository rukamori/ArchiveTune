/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.svg.DynamicSVGImage
import moe.rukamori.archivetune.ui.svg.PALETTE
import moe.rukamori.archivetune.ui.svg.SVGString
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.ui.theme.palette.TonalPalettes
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

private enum class SeedRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
}

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

private fun Color.toHexString(): String {
    val red = (this.red * 255).toInt()
    val green = (this.green * 255).toInt()
    val blue = (this.blue * 255).toInt()
    return String.format("#%02X%02X%02X", red, green, blue)
}

private fun ThemePalette.toSeedPalette(): ThemeSeedPalette =
    ThemeSeedPalette(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        neutral = neutral,
    )

private fun ThemeSeedPalette.toThemePalette(): ThemePalette =
    ThemePalette(
        id = "custom_seed",
        nameResId = R.string.palette_custom,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        neutral = neutral,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePickerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (customThemeColor, onCustomThemeColorChange) =
        rememberPreference(
            CustomThemeColorKey,
            defaultValue = ThemePalettes.Default.id,
        )

    val selectedPalette =
        remember(customThemeColor) {
            val custom = ThemeSeedPaletteCodec.decodeFromPreference(customThemeColor)?.toThemePalette()
            custom
                ?: ThemePalettes.findById(customThemeColor)
                ?: ThemePalettes.findByPrimaryColor(customThemeColor)
                ?: ThemePalettes.Default
        }

    val selectedSeedPalette = remember(selectedPalette) { selectedPalette.toSeedPalette() }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                .orEmpty()
                        }.getOrNull().orEmpty()
                    }
                val imported = ThemeSeedPaletteCodec.decodeFromJson(text)
                if (imported != null) {
                    val name = ThemeSeedPaletteCodec.extractNameFromJsonOrNull(text)
                    onCustomThemeColorChange(ThemeSeedPaletteCodec.encodeForPreference(imported, name))
                    Toast.makeText(context, context.getString(R.string.theme_import_success), Toast.LENGTH_SHORT).show()
                    navController.navigate("settings/appearance/theme_creator")
                } else {
                    Toast.makeText(context, context.getString(R.string.theme_import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.color_palette)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier.windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.custom_theme)) },
                    icon = { Icon(painter = painterResource(R.drawable.palette), contentDescription = null) },
                    onClick = { navController.navigate("settings/appearance/theme_creator") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.import_theme)) },
                    icon = { Icon(painter = painterResource(R.drawable.restore), contentDescription = null) },
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            SimpleThemePreview(
                palette = selectedSeedPalette,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            ColorPaletteSelector(
                palettes = ThemePalettes.allPalettes,
                selectedPalette = selectedPalette,
                onPaletteSelected = { palette -> onCustomThemeColorChange(palette.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCreatorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (customThemeValue, setCustomThemeValue) =
        rememberPreference(
            key = CustomThemeColorKey,
            defaultValue = ThemePalettes.Default.id,
        )
    val (_, setDynamicThemeEnabled) =
        rememberPreference(
            key = DynamicThemeKey,
            defaultValue = true,
        )

    val seedFromPrefs =
        remember(customThemeValue) {
            ThemeSeedPaletteCodec.decodeFromPreference(customThemeValue)
                ?: ThemePalettes.findById(customThemeValue)?.toSeedPalette()
                ?: ThemePalettes.Default.toSeedPalette()
        }

    var themeName by rememberSaveable(customThemeValue) {
        mutableStateOf(ThemeSeedPaletteCodec.extractNameFromPreference(customThemeValue) ?: "")
    }

    var primary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.primary) }
    var secondary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.secondary) }
    var tertiary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.tertiary) }
    var neutral by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.neutral) }

    val currentPalette =
        ThemeSeedPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            neutral = neutral,
        )

    var activeRole by rememberSaveable { mutableStateOf(SeedRole.PRIMARY) }
    var showImportErrorDialog by rememberSaveable { mutableStateOf(false) }
    var importErrorText by rememberSaveable { mutableStateOf("") }

    fun applyThemeToPrefs() {
        setDynamicThemeEnabled(false)
        setCustomThemeValue(ThemeSeedPaletteCodec.encodeForPreference(currentPalette, themeName.takeIf { it.isNotBlank() }))
        Toast.makeText(context, context.getString(R.string.theme_applied), Toast.LENGTH_SHORT).show()
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val payload = ThemeSeedPaletteCodec.encodeAsJson(currentPalette, themeName.takeIf { it.isNotBlank() })
                val ok =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(payload.toByteArray(Charsets.UTF_8))
                                out.flush()
                            } ?: error("No output stream")
                        }.isSuccess
                    }
                if (ok) {
                    Toast.makeText(context, context.getString(R.string.theme_export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.theme_export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                .orEmpty()
                        }.getOrNull().orEmpty()
                    }
                val importedPalette = ThemeSeedPaletteCodec.decodeFromJson(text)
                if (importedPalette != null) {
                    val name = ThemeSeedPaletteCodec.extractNameFromJsonOrNull(text)
                    setDynamicThemeEnabled(false)
                    setCustomThemeValue(ThemeSeedPaletteCodec.encodeForPreference(importedPalette, name))
                    Toast.makeText(context, context.getString(R.string.theme_import_success), Toast.LENGTH_SHORT).show()
                } else {
                    importErrorText = text.take(1200)
                    showImportErrorDialog = true
                }
            }
        }

    if (showImportErrorDialog) {
        AlertDialog(
            onDismissRequest = { showImportErrorDialog = false },
            confirmButton = {
                TextButton(onClick = { showImportErrorDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            title = { Text(text = stringResource(R.string.theme_import_failed_title)) },
            text = {
                Text(
                    text = if (importErrorText.isBlank()) stringResource(R.string.theme_import_failed) else importErrorText,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.theme_creator_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            primary = ThemePalettes.Default.primary
                            secondary = ThemePalettes.Default.secondary
                            tertiary = ThemePalettes.Default.tertiary
                            neutral = ThemePalettes.Default.neutral
                            themeName = ""
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(R.string.reset))
                    }
                    TextButton(onClick = { applyThemeToPrefs() }, shapes = ButtonDefaults.shapes()) {
                        Text(text = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ).padding(horizontal = 16.dp),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.import_theme)) },
                    icon = { Icon(painter = painterResource(R.drawable.restore), contentDescription = null) },
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        val safeName =
                            themeName
                                .trim()
                                .ifBlank { "ArchiveTune Theme" }
                                .replace(Regex("[^a-zA-Z0-9 _\\-]"), "_")
                                .take(64)
                        exportLauncher.launch("$safeName.json")
                    },
                    text = { Text(stringResource(R.string.export_theme)) },
                    icon = { Icon(painter = painterResource(R.drawable.share), contentDescription = null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState()),
        ) {
            SimpleThemePreview(
                palette = currentPalette,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            Card(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.theme_meta_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = themeName,
                        onValueChange = { themeName = it.take(48) },
                        label = { Text(stringResource(R.string.theme_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { applyThemeToPrefs() },
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(R.string.theme_apply_button))
                    }
                }
            }

            SeedRolePicker(
                activeRole = activeRole,
                onRoleChange = { activeRole = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val activeColor by remember(activeRole, primary, secondary, tertiary, neutral) {
                derivedStateOf {
                    when (activeRole) {
                        SeedRole.PRIMARY -> primary
                        SeedRole.SECONDARY -> secondary
                        SeedRole.TERTIARY -> tertiary
                        SeedRole.NEUTRAL -> neutral
                    }
                }
            }

            SeedColorEditor(
                role = activeRole,
                color = activeColor,
                onColorChange = { newColor ->
                    when (activeRole) {
                        SeedRole.PRIMARY -> primary = newColor
                        SeedRole.SECONDARY -> secondary = newColor
                        SeedRole.TERTIARY -> tertiary = newColor
                        SeedRole.NEUTRAL -> neutral = newColor
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SimpleThemePreview(
    palette: ThemeSeedPalette,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val tonalPalettes =
        remember(palette) {
            TonalPalettes.fromSeedColors(
                primarySeed = palette.primary,
                secondarySeed = palette.secondary,
                tertiarySeed = palette.tertiary,
                neutralSeed = palette.neutral,
            )
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1.38f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        DynamicSVGImage(
            svgImageString = SVGString.PALETTE,
            tonalPalettes = tonalPalettes,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ColorPaletteSelector(
    palettes: List<ThemePalette>,
    selectedPalette: ThemePalette,
    onPaletteSelected: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = palettes.indexOf(selectedPalette)
    val totalDots = (palettes.size + 3) / 4

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex / 4 }
    }

    var stableCurrentPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(currentPage) {
        kotlinx.coroutines.delay(50)
        stableCurrentPage = currentPage
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(index = selectedIndex, scrollOffset = -100)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(palettes, key = { it.id }) { palette ->
                SelectableMiniPalette(
                    palette = palette,
                    isSelected = palette.id == selectedPalette.id,
                    onClick = { onPaletteSelected(palette) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CarouselDotsIndicator(
            totalDots = totalDots,
            currentPage = stableCurrentPage,
            selectedColor = selectedPalette.primary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun SelectableMiniPalette(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "miniPaletteScale",
    )

    Surface(
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(16.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
    ) {
        Surface(
            modifier =
                Modifier
                    .focusable()
                    .clickable(onClick = onClick)
                    .padding(16.dp)
                    .size(48.dp),
            shape = CircleShape,
            color = palette.primary,
        ) {
            Box {
                Surface(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .offset((-24).dp, 24.dp),
                    color = palette.tertiary,
                ) {}
                Surface(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .offset(24.dp, 24.dp),
                    color = palette.secondary,
                ) {}
                AnimatedVisibility(
                    visible = isSelected,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                    exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .padding(8.dp)
                                .size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselDotsIndicator(
    totalDots: Int,
    currentPage: Int,
    selectedColor: Color,
    modifier: Modifier = Modifier,
) {
    val fixedDotContainerSize = 10.dp

    Row(
        modifier = modifier.height(fixedDotContainerSize),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalDots) { index ->
            val isSelected = index == currentPage

            val dotSize by animateDpAsState(
                targetValue = if (isSelected) 8.dp else 4.dp,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "dotSize",
            )

            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 2.dp)
                        .size(fixedDotContainerSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    selectedColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                            ),
                )
            }
        }
    }
}

@Composable
private fun SeedRolePicker(
    activeRole: SeedRole,
    onRoleChange: (SeedRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.theme_seed_colors), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeedChip(label = stringResource(R.string.theme_seed_primary), selected = activeRole == SeedRole.PRIMARY, onClick = {
                    onRoleChange(SeedRole.PRIMARY)
                }, modifier = Modifier.weight(1f))
                SeedChip(label = stringResource(R.string.theme_seed_secondary), selected = activeRole == SeedRole.SECONDARY, onClick = {
                    onRoleChange(SeedRole.SECONDARY)
                }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeedChip(label = stringResource(R.string.theme_seed_tertiary), selected = activeRole == SeedRole.TERTIARY, onClick = {
                    onRoleChange(SeedRole.TERTIARY)
                }, modifier = Modifier.weight(1f))
                SeedChip(label = stringResource(R.string.theme_seed_neutral), selected = activeRole == SeedRole.NEUTRAL, onClick = {
                    onRoleChange(SeedRole.NEUTRAL)
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SeedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(999.dp))
                .focusable()
                .clickable(onClick = onClick),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (selected) 6.dp else 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeedColorEditor(
    role: SeedRole,
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val roleLabel =
        when (role) {
            SeedRole.PRIMARY -> stringResource(R.string.theme_seed_primary)
            SeedRole.SECONDARY -> stringResource(R.string.theme_seed_secondary)
            SeedRole.TERTIARY -> stringResource(R.string.theme_seed_tertiary)
            SeedRole.NEUTRAL -> stringResource(R.string.theme_seed_neutral)
        }

    val r0 = ((color.toArgb() shr 16) and 0xFF)
    val g0 = ((color.toArgb() shr 8) and 0xFF)
    val b0 = (color.toArgb() and 0xFF)

    var r by rememberSaveable(role.name) { mutableStateOf(r0) }
    var g by rememberSaveable(role.name) { mutableStateOf(g0) }
    var b by rememberSaveable(role.name) { mutableStateOf(b0) }

    LaunchedEffect(role, color.toArgb()) {
        val argb = color.toArgb()
        r = (argb shr 16) and 0xFF
        g = (argb shr 8) and 0xFF
        b = argb and 0xFF
    }

    val hex = remember(color.toArgb()) { String.format("#%08X", color.toArgb()) }
    var hexInput by rememberSaveable(role.name) { mutableStateOf(hex) }
    var hexError by rememberSaveable(role.name) { mutableStateOf(false) }

    LaunchedEffect(hex) {
        if (!hexError) hexInput = hex
    }

    fun commitRgb() {
        hexError = false
        onColorChange(Color((0xFF shl 24) or (r shl 16) or (g shl 8) or b))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = stringResource(R.string.theme_editor_title, roleLabel), style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(54.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.theme_editor_hex),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = hex, style = MaterialTheme.typography.titleSmall)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        modifier =
                            Modifier
                                .clickable {
                                    clipboard.setText(AnnotatedString(hex))
                                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painter = painterResource(R.drawable.link), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(R.string.copy), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = { next ->
                    hexInput = next.take(12)
                    val parsed =
                        runCatching {
                            val normalized = hexInput.trim().let { if (it.startsWith("#")) it else "#$it" }
                            Color(android.graphics.Color.parseColor(normalized))
                        }.getOrNull()
                    if (parsed != null) {
                        hexError = false
                        onColorChange(parsed)
                    } else {
                        hexError = true
                    }
                },
                label = { Text(stringResource(R.string.theme_editor_hex_input)) },
                isError = hexError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            RgbSlider(label = "R", value = r, color = Color(0xFFE53935), onValueChange = {
                r = it
                commitRgb()
            })
            RgbSlider(label = "G", value = g, color = Color(0xFF43A047), onValueChange = {
                g = it
                commitRgb()
            })
            RgbSlider(label = "B", value = b, color = Color(0xFF1E88E5), onValueChange = {
                b = it
                commitRgb()
            })

            PresetSwatches(current = color, onPick = onColorChange)
        }
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.18f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors =
                SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.weight(1f),
        )
        Text(text = value.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun PresetSwatches(
    current: Color,
    onPick: (Color) -> Unit,
) {
    val swatches =
        remember {
            ThemePalettes.allPalettes
                .map { it.primary }
                .distinctBy { it.toArgb() }
                .take(18)
        }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.theme_presets_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { c ->
                val selected = c.toArgb() == current.toArgb()
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .shadow(if (selected) 8.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ).clickable { onPick(c) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PalettePickerScreenPreview() {
    MaterialTheme {
        PalettePickerScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectableMiniPalettePreview() {
    MaterialTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            SelectableMiniPalette(palette = ThemePalettes.Default, isSelected = true, onClick = {})
            SelectableMiniPalette(palette = ThemePalettes.OceanBlue, isSelected = false, onClick = {})
            SelectableMiniPalette(palette = ThemePalettes.EmeraldGreen, isSelected = false, onClick = {})
        }
    }
}
