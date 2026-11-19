/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import java.io.IOException
import kotlin.math.roundToInt
import androidx.compose.material3.IconButton as MaterialIconButton

private const val ThemeNameLimit = 48
private const val ImportErrorExcerptLimit = 1200

private enum class SeedRole(
    val labelRes: Int,
) {
    PRIMARY(R.string.theme_seed_primary),
    SECONDARY(R.string.theme_seed_secondary),
    TERTIARY(R.string.theme_seed_tertiary),
    NEUTRAL(R.string.theme_seed_neutral),
}

private enum class ColorEditMode { HSV, RGB }

private enum class CreatorExit { UP, MAIN }

private enum class PaletteFamily(
    val labelRes: Int,
    val paletteIds: Set<String>,
) {
    ALL(R.string.palette_family_all, emptySet()),
    BLUE(
        R.string.palette_family_blue,
        setOf("ocean_blue", "arctic_blue", "midnight_navy", "sky_blue", "cobalt_blue", "electric_blue"),
    ),
    GREEN(
        R.string.palette_family_green,
        setOf("emerald_green", "teal_wave", "forest_green", "spotify_green", "mint_fresh", "olive_garden", "sage_green"),
    ),
    WARM(
        R.string.palette_family_warm,
        setOf("sunset_orange", "golden_hour", "warm_amber", "tangerine_blast", "peach", "mango"),
    ),
    PURPLE(
        R.string.palette_family_purple,
        setOf("royal_purple", "lavender_dream", "grape_purple", "violet", "amethyst", "ultra_violet"),
    ),
    PINK(
        R.string.palette_family_pink,
        setOf("cherry_blossom", "rose_quartz", "magenta_pop", "hot_pink", "blush", "coral", "bubblegum"),
    ),
    RED(
        R.string.palette_family_red,
        setOf("crimson_red", "youtube_red", "wine_red", "ruby_red", "scarlet"),
    ),
    NEUTRAL(
        R.string.palette_family_neutral,
        setOf("charcoal", "silver", "slate", "graphite"),
    ),
    EARTH(
        R.string.palette_family_earth,
        setOf("terracotta", "coffee", "mocha", "sand", "clay"),
    ),
    PASTEL(
        R.string.palette_family_pastel,
        setOf("pastel_pink", "pastel_blue", "pastel_green", "pastel_yellow", "pastel_purple"),
    ),
    NEON(
        R.string.palette_family_neon,
        setOf("neon_green", "neon_pink", "neon_blue", "neon_orange", "cyberpunk", "synthwave"),
    ),
    MOOD(
        R.string.palette_family_mood,
        setOf("ocean", "forest", "autumn", "winter", "spring", "summer", "twilight", "aurora", "candy", "rainbow"),
    ),
    ;

    fun includes(palette: ThemePalette): Boolean = this == ALL || palette.id in paletteIds
}

private data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColor(): Color =
        Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(hue.coerceIn(0f, 359.99f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
            ),
        )
}

private fun Color.toHsv(): Hsv {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return Hsv(out[0], out[1], out[2])
}

private fun Color.rotateHue(degrees: Float): Color {
    val hsv = toHsv()
    return hsv.copy(hue = ((hsv.hue + degrees) % 360f + 360f) % 360f).toColor()
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun parseSeedHex(input: String): Color? {
    val digits = input.trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    if (!digits.all { it.isHexDigit() }) return null
    val rgb = digits.toLong(16) and 0xFFFFFFL
    return Color(0xFF000000L or rgb)
}

private fun ThemeSeedPalette.colorFor(role: SeedRole): Color =
    when (role) {
        SeedRole.PRIMARY -> primary
        SeedRole.SECONDARY -> secondary
        SeedRole.TERTIARY -> tertiary
        SeedRole.NEUTRAL -> neutral
    }

private suspend fun Context.readThemeText(uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

private suspend fun Context.writeThemeText(
    uri: Uri,
    payload: String,
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            } != null
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

private fun SnackbarHostState.announce(
    scope: kotlinx.coroutines.CoroutineScope,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    scope.launch {
        currentSnackbarData?.dismiss()
        val result = showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
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
    val snackbarHostState = remember { SnackbarHostState() }
    val animationsDisabled = LocalAnimationsDisabled.current
    val (customThemeColor, onCustomThemeColorChange) =
        rememberPreference(
            CustomThemeColorKey,
            defaultValue = ThemePalettes.Default.id,
        )
    val (dynamicThemeEnabled, setDynamicThemeEnabled) =
        rememberPreference(
            key = DynamicThemeKey,
            defaultValue = true,
        )

    val customSeed =remember(customThemeColor) { ThemeSeedPaletteCodec.decodeFromPreference(customThemeColor) }
    val customName =
        remember(customThemeColor) {
            ThemeSeedPaletteCodec.extractNameFromPreference(customThemeColor)?.takeIf { it.isNotBlank() }
        }
    val selectedPalette =
        remember(customThemeColor, customSeed) {
            customSeed?.toThemePalette()
                ?: ThemePalettes.findById(customThemeColor)
                ?: ThemePalettes.findByPrimaryColor(customThemeColor)
                ?: ThemePalettes.Default
        }
    val isCustomActive = customSeed != null
    val selectedSeeds = remember(selectedPalette) { selectedPalette.toSeedPalette() }

    val appIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var previewDarkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val previewDark = previewDarkOverride ?: appIsDark
    val previewScheme = rememberSeedColorScheme(selectedSeeds, previewDark)

    var family by rememberSaveable { mutableStateOf(PaletteFamily.ALL) }
    val visiblePalettes = remember(family) { ThemePalettes.allPalettes.filter(family::includes) }

    val appliedMessage = stringResource(R.string.theme_applied)
    val undoLabel = stringResource(R.string.undo)
    val importFailedMessage = stringResource(R.string.theme_import_failed)
    val untitledName = stringResource(R.string.theme_untitled)
    val currentName = if (isCustomActive) customName ?: untitledName else stringResource(selectedPalette.nameResId)

    fun applyPalette(palette: ThemePalette) {
        if (!isCustomActive && !dynamicThemeEnabled && palette.id == selectedPalette.id) return
        val previousValue = customThemeColor
        setDynamicThemeEnabled(false)
        onCustomThemeColorChange(palette.id)
        if (isCustomActive) {
            snackbarHostState.announce(scope, appliedMessage, undoLabel) { onCustomThemeColorChange(previousValue) }
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = context.readThemeText(uri)
                val imported = text?.let(ThemeSeedPaletteCodec::decodeFromJson)
                if (text != null && imported != null) {
                    val name = ThemeSeedPaletteCodec.extractNameFromJsonOrNull(text)
                    setDynamicThemeEnabled(false)
                    onCustomThemeColorChange(ThemeSeedPaletteCodec.encodeForPreference(imported, name))
                    Toast.makeText(context, context.getString(R.string.theme_import_success), Toast.LENGTH_SHORT).show()
                    navController.navigate("settings/appearance/theme_creator")
                } else {
                    snackbarHostState.announce(scope, importFailedMessage)
                }
            }
        }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val bottomInset = playerInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.color_palette),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = currentName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            PaletteToolbar(
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onShuffle = {
                    val candidates = ThemePalettes.allPalettes.filter { isCustomActive || it.id != selectedPalette.id }
                    applyPalette(candidates.random())
                },
                onCreate = { navController.navigate("settings/appearance/theme_creator") },
                modifier =
                    Modifier.windowInsetsPadding(
                        playerInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }, contentType = "hero") {
                PaletteHero(
                    name = currentName,
                    seeds = selectedSeeds,
                    selectionKey = customThemeColor,
                    isCustom = isCustomActive,
                    inUse = !dynamicThemeEnabled,
                    scheme = previewScheme,
                    previewDark = previewDark,
                    onPreviewDarkChange = { previewDarkOverride = it },
                )
            }
            item(key = "custom", span = { GridItemSpan(maxLineSpan) }, contentType = "custom") {
                CustomThemeEntry(
                    customSeed = customSeed,
                    customName = customName ?: untitledName,
                    inUse = !dynamicThemeEnabled,
                    onClick = { navController.navigate("settings/appearance/theme_creator") },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "catalog", span = { GridItemSpan(maxLineSpan) }, contentType = "catalog") {
                PaletteCatalogHeader(
                    count = visiblePalettes.size,
                    family = family,
                    onFamilyChange = { family = it },
                )
            }
            items(visiblePalettes, key = { it.id }, contentType = { "palette" }) { palette ->
                PaletteTile(
                    palette = palette,
                    selected = !isCustomActive && palette.id == selectedPalette.id,
                    onClick = { applyPalette(palette) },
                    modifier = if (animationsDisabled) Modifier else Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun PaletteHero(
    name: String,
    seeds: ThemeSeedPalette,
    selectionKey: String,
    isCustom: Boolean,
    inUse: Boolean,
    scheme: ColorScheme,
    previewDark: Boolean,
    onPreviewDarkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(selectionKey) { pulse = !pulse }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MorphSwatch(
                    color = seeds.primary,
                    morphed = pulse,
                    size = 72.dp,
                    restPolygon = MaterialShapes.Cookie9Sided,
                    morphPolygon = MaterialShapes.Cookie12Sided,
                    turnDegrees = 60f,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (inUse) {
                        Text(
                            text = stringResource(R.string.palette_in_use),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AnimatedContent(
                        targetState = name,
                        transitionSpec = {
                            (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec()))
                                .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec()))
                                .using(SizeTransform(clip = false))
                        },
                        label = "paletteName",
                    ) { target ->
                        Text(
                            text = target,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text =
                            if (isCustom) {
                                listOf(seeds.primary, seeds.secondary, seeds.tertiary, seeds.neutral)
                                    .joinToString(separator = "  ") { it.toHexRgb() }
                            } else {
                                seeds.primary.toHexRgb()
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            PreviewHeaderRow(
                previewDark = previewDark,
                onPreviewDarkChange = onPreviewDarkChange,
            )
            ThemeStage(scheme = scheme)
            TonalStrip(seed = seeds.primary)
        }
    }
}

@Composable
private fun PreviewHeaderRow(
    previewDark: Boolean,
    onPreviewDarkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ConnectedChoiceRow(
                options = listOf(stringResource(R.string.theme_preview_light), stringResource(R.string.theme_preview_dark)),
                selectedIndex = if (previewDark) 1 else 0,
                onSelect = { onPreviewDarkChange(it == 1) },
                leadingIcons = listOf(null, R.drawable.dark_mode),
            )
        }
        badge()
    }
}

@Composable
private fun CustomThemeEntry(
    customSeed: ThemeSeedPalette?,
    customName: String,
    inUse: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val active = customSeed != null
    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "customContainer",
    )
    val content = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (customSeed != null) {
                SeedQuad(seeds = customSeed, size = 52.dp)
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(MaterialShapes.Flower.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.palette),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.custom_theme),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        when {
                            !active -> stringResource(R.string.custom_theme_cta_desc)
                            inUse -> stringResource(R.string.custom_theme_in_use, customName)
                            else -> customName
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SeedQuad(
    seeds: ThemeSeedPalette,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .size(size)
                .clip(MaterialShapes.Cookie4Sided.toShape()),
    ) {
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(seeds.primary))
            Box(Modifier.weight(1f).fillMaxHeight().background(seeds.secondary))
        }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(seeds.tertiary))
            Box(Modifier.weight(1f).fillMaxHeight().background(seeds.neutral))
        }
    }
}

@Composable
private fun PaletteCatalogHeader(
    count: Int,
    family: PaletteFamily,
    onFamilyChange: (PaletteFamily) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.palette_catalog_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(
                targetState = count,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "paletteCount",
            ) { value ->
                Text(
                    text = pluralStringResource(R.plurals.palette_count, value, value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PaletteFamily.entries, key = { it.name }) { option ->
                val selected = option == family
                FilterChip(
                    selected = selected,
                    onClick = { onFamilyChange(option) },
                    label = { Text(stringResource(option.labelRes)) },
                    leadingIcon =
                        if (selected) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun PaletteTile(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "tileScale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContainer",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileLabel",
    )

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(RoundedCornerShape(24.dp))
                .background(container)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MorphSwatch(
            color = palette.primary,
            morphed = selected,
            size = 56.dp,
        ) {
            SwatchCheck(visible = selected, tint = palette.primary.readableContent())
        }
        Text(
            text = stringResource(palette.nameResId),
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwatchCheck(
    visible: Boolean,
    tint: Color,
    size: Dp = 24.dp,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun PaletteToolbar(
    onImport: () -> Unit,
    onShuffle: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onCreate) {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = stringResource(R.string.custom_theme),
                )
            }
        },
    ) {
        MaterialIconButton(onClick = onImport) {
            Icon(
                painter = painterResource(R.drawable.restore),
                contentDescription = stringResource(R.string.import_theme),
            )
        }
        MaterialIconButton(onClick = onShuffle) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = stringResource(R.string.shuffle),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCreatorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val animationsDisabled = LocalAnimationsDisabled.current

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
    val savedName =
        remember(customThemeValue) {
            ThemeSeedPaletteCodec.extractNameFromPreference(customThemeValue).orEmpty()
        }

    var themeName by rememberSaveable(customThemeValue) { mutableStateOf(savedName) }
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
    val isDirty = !currentPalette.sameSeedsAs(seedFromPrefs) || themeName.trim() != savedName

    var activeRole by rememberSaveable { mutableStateOf(SeedRole.PRIMARY) }
    var previewDarkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val previewDark = previewDarkOverride ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    val previewScheme = rememberSeedColorScheme(currentPalette, previewDark)
    var importErrorText by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExit by rememberSaveable { mutableStateOf<CreatorExit?>(null) }

    val appliedMessage = stringResource(R.string.theme_applied)
    val importSuccessMessage = stringResource(R.string.theme_import_success)
    val exportSuccessMessage = stringResource(R.string.theme_export_success)
    val exportFailedMessage = stringResource(R.string.theme_export_failed)
    val copiedMessage = stringResource(R.string.copied)
    val resetMessage = stringResource(R.string.theme_reset_done)
    val undoLabel = stringResource(R.string.undo)
    val untitledName = stringResource(R.string.theme_untitled)

    fun setRoleColor(
        role: SeedRole,
        color: Color,
    ) {
        when (role) {
            SeedRole.PRIMARY -> primary = color
            SeedRole.SECONDARY -> secondary = color
            SeedRole.TERTIARY -> tertiary = color
            SeedRole.NEUTRAL -> neutral = color
        }
    }

    fun restorePalette(
        palette: ThemeSeedPalette,
        name: String,
    ) {
        primary = palette.primary
        secondary = palette.secondary
        tertiary = palette.tertiary
        neutral = palette.neutral
        themeName = name
    }

    fun applyTheme() {
        setDynamicThemeEnabled(false)
        setCustomThemeValue(
            ThemeSeedPaletteCodec.encodeForPreference(currentPalette, themeName.trim().takeIf { it.isNotBlank() }),
        )
        snackbarHostState.announce(scope, appliedMessage)
    }

    fun resetToDefault() {
        val snapshot = currentPalette
        val snapshotName = themeName
        restorePalette(ThemePalettes.Default.toSeedPalette(), "")
        snackbarHostState.announce(scope, resetMessage, undoLabel) { restorePalette(snapshot, snapshotName) }
    }

    fun performExit(target: CreatorExit) {
        pendingExit = null
        when (target) {
            CreatorExit.UP -> navController.navigateUp()
            CreatorExit.MAIN -> navController.backToMain()
        }
    }

    fun requestExit(target: CreatorExit) {
        if (isDirty) pendingExit = target else performExit(target)
    }

    fun copyHex(
        role: SeedRole,
        hex: String,
    ) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(context.getString(role.labelRes), hex)))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbarHostState.announce(scope, copiedMessage)
            }
        }
    }

    BackHandler(enabled = isDirty) { pendingExit = CreatorExit.UP }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val payload = ThemeSeedPaletteCodec.encodeAsJson(currentPalette, themeName.trim().takeIf { it.isNotBlank() })
            scope.launch {
                val ok = context.writeThemeText(uri, payload)
                snackbarHostState.announce(scope, if (ok) exportSuccessMessage else exportFailedMessage)
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = context.readThemeText(uri)
                val importedPalette = text?.let(ThemeSeedPaletteCodec::decodeFromJson)
                if (text != null && importedPalette != null) {
                    val name = ThemeSeedPaletteCodec.extractNameFromJsonOrNull(text)
                    setDynamicThemeEnabled(false)
                    setCustomThemeValue(ThemeSeedPaletteCodec.encodeForPreference(importedPalette, name))
                    snackbarHostState.announce(scope, importSuccessMessage)
                } else {
                    importErrorText = text.orEmpty().take(ImportErrorExcerptLimit)
                }
            }
        }

    importErrorText?.let { excerpt ->
        AlertDialog(
            onDismissRequest = { importErrorText = null },
            icon = { Icon(painter = painterResource(R.drawable.info), contentDescription = null) },
            title = { Text(text = stringResource(R.string.theme_import_failed_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.theme_import_failed_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (excerpt.isNotBlank()) {
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importErrorText = null }, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    pendingExit?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingExit = null },
            icon = { Icon(painter = painterResource(R.drawable.palette), contentDescription = null) },
            title = { Text(text = stringResource(R.string.theme_discard_title)) },
            text = { Text(text = stringResource(R.string.theme_discard_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        applyTheme()
                        performExit(target)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.theme_apply_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { performExit(target) }, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.theme_discard_confirm))
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val bottomInset = playerInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.theme_creator_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = themeName.trim().ifBlank { untitledName },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { requestExit(CreatorExit.UP) },
                        onLongClick = { requestExit(CreatorExit.MAIN) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            CreatorToolbar(
                onReset = ::resetToDefault,
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onExport = {
                    val safeName =
                        themeName
                            .trim()
                            .ifBlank { "ArchiveTune Theme" }
                            .replace(Regex("[^a-zA-Z0-9 _\\-]"), "_")
                            .take(64)
                    exportLauncher.launch("$safeName.json")
                },
                onApply = ::applyTheme,
                modifier =
                    Modifier.windowInsetsPadding(
                        playerInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero", contentType = "hero") {
                CreatorHero(
                    scheme = previewScheme,
                    toneSeed = currentPalette.colorFor(activeRole),
                    isDirty = isDirty,
                    previewDark = previewDark,
                    onPreviewDarkChange = { previewDarkOverride = it },
                )
            }
            item(key = "roles", contentType = "roles") {
                RoleBoard(
                    palette = currentPalette,
                    activeRole = activeRole,
                    onRoleChange = { activeRole = it },
                )
            }
            item(key = "editor", contentType = "editor") {
                SeedColorStudio(
                    role = activeRole,
                    color = currentPalette.colorFor(activeRole),
                    onColorChange = { setRoleColor(activeRole, it) },
                    onCopyHex = { copyHex(activeRole, it) },
                )
            }
            if (activeRole != SeedRole.PRIMARY) {
                item(key = "harmony", contentType = "harmony") {
                    HarmonyRail(
                        primary = primary,
                        current = currentPalette.colorFor(activeRole),
                        onPick = { setRoleColor(activeRole, it) },
                        modifier = if (animationsDisabled) Modifier else Modifier.animateItem(),
                    )
                }
            }
            item(key = "presets", contentType = "presets") {
                PresetRail(
                    current = currentPalette.colorFor(activeRole),
                    onPick = { setRoleColor(activeRole, it) },
                    modifier = if (animationsDisabled) Modifier else Modifier.animateItem(),
                )
            }
            item(key = "details", contentType = "details") {
                ThemeDetailsCard(
                    name = themeName,
                    onNameChange = { themeName = it },
                    modifier = if (animationsDisabled) Modifier else Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun CreatorHero(
    scheme: ColorScheme,
    toneSeed: Color,
    isDirty: Boolean,
    previewDark: Boolean,
    onPreviewDarkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewHeaderRow(
                previewDark = previewDark,
                onPreviewDarkChange = onPreviewDarkChange,
                badge = {
                    AnimatedVisibility(
                        visible = isDirty,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                    ) {
                        Text(
                            text = stringResource(R.string.theme_unsaved_changes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                },
            )
            ThemeStage(scheme = scheme)
            TonalStrip(seed = toneSeed)
        }
    }
}

@Composable
private fun StudioSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun RoleBoard(
    palette: ThemeSeedPalette,
    activeRole: SeedRole,
    onRoleChange: (SeedRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioSection(
        title = stringResource(R.string.theme_seed_colors),
        subtitle = stringResource(R.string.theme_seed_colors_desc),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeedRole.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { seedRole ->
                        RoleTile(
                            seedRole = seedRole,
                            color = palette.colorFor(seedRole),
                            selected = seedRole == activeRole,
                            onClick = { onRoleChange(seedRole) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleTile(
    seedRole: SeedRole,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "roleContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "roleContent",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 18.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "roleCorner",
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MorphSwatch(
                color = color,
                morphed = selected,
                size = 40.dp,
                morphPolygon = MaterialShapes.Cookie6Sided,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(seedRole.labelRes),
                    style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = color.toHexRgb(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SeedColorStudio(
    role: SeedRole,
    color: Color,
    onColorChange: (Color) -> Unit,
    onCopyHex: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val focusManager = LocalFocusManager.current
    val roleLabel = stringResource(role.labelRes)
    val hex = color.toHexRgb()

    var mode by rememberSaveable { mutableStateOf(ColorEditMode.HSV) }
    var hsv by remember(role) { mutableStateOf(color.toHsv()) }
    LaunchedEffect(color) {
        if (hsv.toColor().toArgb() != color.toArgb()) hsv = color.toHsv()
    }

    var hexInput by rememberSaveable(role) { mutableStateOf(hex) }
    var hexFocused by remember { mutableStateOf(false) }
    LaunchedEffect(hex) {
        if (!hexFocused) hexInput = hex
    }
    val hexDigits = hexInput.trim().removePrefix("#")
    val hexError = hexDigits.any { !it.isHexDigit() } || (hexDigits.length >= 6 && parseSeedHex(hexDigits) == null)

    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(role) { pulse = !pulse }

    fun updateHsv(next: Hsv) {
        hsv = next
        onColorChange(next.toColor())
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MorphSwatch(
                    color = color,
                    morphed = pulse,
                    size = 64.dp,
                    restPolygon = MaterialShapes.Cookie6Sided,
                    morphPolygon = MaterialShapes.SoftBurst,
                    turnDegrees = 30f,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.theme_editor_title, roleLabel),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hex,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                FilledTonalIconButton(
                    onClick = { onCopyHex(hex) },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = stringResource(R.string.copy),
                    )
                }
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = { next ->
                    val trimmed = next.take(9)
                    hexInput = trimmed
                    parseSeedHex(trimmed)?.let(onColorChange)
                },
                label = { Text(stringResource(R.string.theme_editor_hex_input)) },
                isError = hexError,
                supportingText =
                    if (hexError) {
                        { Text(stringResource(R.string.theme_hex_invalid)) }
                    } else {
                        null
                    },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            hexFocused = state.isFocused
                            if (!state.isFocused) hexInput = hex
                        },
            )

            ConnectedChoiceRow(
                options = listOf(stringResource(R.string.theme_editor_mode_hsv), stringResource(R.string.theme_editor_mode_rgb)),
                selectedIndex = mode.ordinal,
                onSelect = { mode = ColorEditMode.entries[it] },
            )

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                        .using(SizeTransform(clip = false))
                },
                label = "editorMode",
            ) { target ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (target) {
                        ColorEditMode.HSV -> HsvChannels(hsv = hsv, onChange = ::updateHsv)
                        ColorEditMode.RGB -> RgbChannels(color = color, onColorChange = onColorChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun HsvChannels(
    hsv: Hsv,
    onChange: (Hsv) -> Unit,
) {
    val hueStops = remember(hsv.saturation, hsv.value) { (0..6).map { Hsv(it * 60f, hsv.saturation, hsv.value).toColor() } }
    val thumb = hsv.toColor()
    ColorChannelSlider(
        label = stringResource(R.string.theme_channel_hue),
        valueText = stringResource(R.string.theme_channel_degrees, hsv.hue.roundToInt()),
        value = hsv.hue,
        valueRange = 0f..360f,
        trackColors = hueStops,
        thumbColor = thumb,
        onValueChange = { onChange(hsv.copy(hue = it)) },
    )
    ColorChannelSlider(
        label = stringResource(R.string.theme_channel_saturation),
        valueText = stringResource(R.string.theme_channel_percent, (hsv.saturation * 100).roundToInt()),
        value = hsv.saturation,
        valueRange = 0f..1f,
        trackColors = listOf(hsv.copy(saturation = 0f).toColor(), hsv.copy(saturation = 1f).toColor()),
        thumbColor = thumb,
        onValueChange = { onChange(hsv.copy(saturation = it)) },
    )
    ColorChannelSlider(
        label = stringResource(R.string.brightness),
        valueText = stringResource(R.string.theme_channel_percent, (hsv.value * 100).roundToInt()),
        value = hsv.value,
        valueRange = 0f..1f,
        trackColors = listOf(hsv.copy(value = 0f).toColor(), hsv.copy(value = 1f).toColor()),
        thumbColor = thumb,
        onValueChange = { onChange(hsv.copy(value = it)) },
    )
}

@Composable
private fun RgbChannels(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val argb = color.toArgb()
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF

    fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Color = Color((0xFF shl 24) or (r shl 16) or (g shl 8) or b)

    ColorChannelSlider(
        label = stringResource(R.string.theme_channel_red),
        valueText = red.toString(),
        value = red.toFloat(),
        valueRange = 0f..255f,
        trackColors = listOf(rgb(0, green, blue), rgb(255, green, blue)),
        thumbColor = color,
        onValueChange = { onColorChange(rgb(it.roundToInt().coerceIn(0, 255), green, blue)) },
    )
    ColorChannelSlider(
        label = stringResource(R.string.theme_channel_green),
        valueText = green.toString(),
        value = green.toFloat(),
        valueRange = 0f..255f,
        trackColors = listOf(rgb(red, 0, blue), rgb(red, 255, blue)),
        thumbColor = color,
        onValueChange = { onColorChange(rgb(red, it.roundToInt().coerceIn(0, 255), blue)) },
    )
    ColorChannelSlider(
        label = stringResource(R.string.theme_channel_blue),
        valueText = blue.toString(),
        value = blue.toFloat(),
        valueRange = 0f..255f,
        trackColors = listOf(rgb(red, green, 0), rgb(red, green, 255)),
        thumbColor = color,
        onValueChange = { onColorChange(rgb(red, green, it.roundToInt().coerceIn(0, 255))) },
    )
}

@Composable
private fun HarmonyRail(
    primary: Color,
    current: Color,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        remember(primary.toArgb()) {
            listOf(
                R.string.theme_harmony_match to primary,
                R.string.theme_harmony_analogous to primary.rotateHue(30f),
                R.string.theme_harmony_triadic to primary.rotateHue(120f),
                R.string.theme_harmony_split to primary.rotateHue(150f),
                R.string.theme_harmony_complementary to primary.rotateHue(180f),
            )
        }
    StudioSection(
        title = stringResource(R.string.theme_harmony_title),
        subtitle = stringResource(R.string.theme_harmony_desc),
        modifier = modifier,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(options, key = { it.first }) { (labelRes, swatch) ->
                SwatchChoice(
                    color = swatch,
                    label = stringResource(labelRes),
                    showLabel = true,
                    selected = swatch.toArgb() == current.toArgb(),
                    onClick = { onPick(swatch) },
                    morphPolygon = MaterialShapes.Sunny,
                )
            }
        }
    }
}

@Composable
private fun PresetRail(
    current: Color,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = remember { ThemePalettes.allPalettes.distinctBy { it.primary.toArgb() } }
    StudioSection(
        title = stringResource(R.string.theme_presets_title),
        subtitle = stringResource(R.string.theme_presets_desc),
        modifier = modifier,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(presets, key = { it.id }) { palette ->
                SwatchChoice(
                    color = palette.primary,
                    label = stringResource(palette.nameResId),
                    showLabel = false,
                    selected = palette.primary.toArgb() == current.toArgb(),
                    onClick = { onPick(palette.primary) },
                    size = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun SwatchChoice(
    color: Color,
    label: String,
    showLabel: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    morphPolygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    Column(
        modifier =
            modifier
                .width(if (showLabel) 88.dp else size + 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = label })
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MorphSwatch(
            color = color,
            morphed = selected,
            size = size,
            morphPolygon = morphPolygon,
        ) {
            SwatchCheck(visible = selected, tint = color.readableContent(), size = 20.dp)
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThemeDetailsCard(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    StudioSection(
        title = stringResource(R.string.theme_meta_title),
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it.take(ThemeNameLimit)) },
            label = { Text(stringResource(R.string.theme_name_optional)) },
            supportingText = {
                Text(
                    text = stringResource(R.string.theme_name_counter, name.length, ThemeNameLimit),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
        )
    }
}

@Composable
private fun CreatorToolbar(
    onReset: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onApply) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = stringResource(R.string.theme_apply_button),
                )
            }
        },
    ) {
        MaterialIconButton(onClick = onReset) {
            Icon(
                painter = painterResource(R.drawable.replay),
                contentDescription = stringResource(R.string.reset),
            )
        }
        MaterialIconButton(onClick = onImport) {
            Icon(
                painter = painterResource(R.drawable.restore),
                contentDescription = stringResource(R.string.import_theme),
            )
        }
        MaterialIconButton(onClick = onExport) {
            Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = stringResource(R.string.export_theme),
            )
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
