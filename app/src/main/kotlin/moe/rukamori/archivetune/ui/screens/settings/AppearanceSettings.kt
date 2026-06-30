/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.ChipSortTypeKey
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.CustomFontNameKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.GridItemSize
import moe.rukamori.archivetune.constants.GridItemsSizeKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.constants.PlayerButtonsStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.QuickPicksDisplayModeKey
import moe.rukamori.archivetune.constants.RandomThemeOnStartupKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.SwipeSensitivityKey
import moe.rukamori.archivetune.constants.SwipeThumbnailKey
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.ThumbnailCornerRadiusSelectorButton
import moe.rukamori.archivetune.ui.player.StyledPlaybackSlider
import moe.rukamori.archivetune.ui.theme.CustomFontLoader
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(navController: NavController) {
    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }
    val (dynamicTheme, onDynamicThemeChange) =
        rememberPreference(
            DynamicThemeKey,
            defaultValue = true,
        )
    val (randomThemeOnStartup, onRandomThemeOnStartupChange) =
        rememberPreference(
            RandomThemeOnStartupKey,
            defaultValue = false,
        )
    val (darkMode, onDarkModeChange) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )
    val (playerDesignStyle, onPlayerDesignStyleChange) =
        rememberEnumPreference(
            PlayerDesignStyleKey,
            defaultValue = PlayerDesignStyle.V4,
        )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) =
        rememberPreference(
            HidePlayerThumbnailKey,
            defaultValue = false,
        )
    val (archiveTuneCanvasEnabled, onArchiveTuneCanvasEnabledChange) =
        rememberPreference(
            ArchiveTuneCanvasKey,
            defaultValue = false,
        )
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) =
        rememberPreference(
            key = ThumbnailCornerRadiusKey,
            defaultValue = 16f, // default dp
        )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) =
        rememberPreference(
            CropThumbnailToSquareKey,
            defaultValue = false,
        )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (miniPlayerBackground, onMiniPlayerBackgroundChange) =
        rememberEnumPreference(
            MiniPlayerBackgroundStyleKey,
            defaultValue = MiniPlayerBackgroundStyle.THEME,
        )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) =
        rememberPreference(
            DisableAnimationsKey,
            defaultValue = defaultDisableAnimations,
        )
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = 48f)
    val (backdropEnabled, onBackdropEnabledChange) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount, onBackdropBlurAmountChange) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (fontPreference, onFontPreferenceChange) =
        rememberEnumPreference(
            FontPreferenceKey,
            defaultValue = AppFontPreference.DEFAULT,
        )
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, defaultValue = "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) =
        rememberEnumPreference(
            PlayerButtonsStyleKey,
            defaultValue = PlayerButtonsStyle.DEFAULT,
        )
    val (sliderStyle, onSliderStyleChange) =
        rememberEnumPreference(
            SliderStyleKey,
            defaultValue = SliderStyle.Standard,
        )
    val (swipeThumbnail, onSwipeThumbnailChange) =
        rememberPreference(
            SwipeThumbnailKey,
            defaultValue = true,
        )
    val (swipeSensitivity, onSwipeSensitivityChange) =
        rememberPreference(
            SwipeSensitivityKey,
            defaultValue = 0.73f,
        )
    val (gridItemSize, onGridItemSizeChange) =
        rememberEnumPreference(
            GridItemsSizeKey,
            defaultValue = GridItemSize.SMALL,
        )

    val (swipeToSong, onSwipeToSongChange) =
        rememberPreference(
            SwipeToSongKey,
            defaultValue = false,
        )

    val (showTagsInLibrary, onShowTagsInLibraryChange) =
        rememberPreference(
            ShowTagsInLibraryKey,
            defaultValue = true,
        )
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) =
        rememberPreference(
            ShowHomeCategoryChipsKey,
            defaultValue = true,
        )
    val (quickPicksDisplayMode, onQuickPicksDisplayModeChange) =
        rememberEnumPreference(
            QuickPicksDisplayModeKey,
            defaultValue = QuickPicksDisplayMode.CARD,
        )

    val customFontPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!CustomFontLoader.isSupportedTtf(context, uri)) {
                Toast.makeText(context, context.getString(R.string.custom_font_invalid), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (customFontUri.isNotBlank() && customFontUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(customFontUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            onCustomFontUriChange(uri.toString())
            onCustomFontNameChange(CustomFontLoader.displayName(context, uri))
            onFontPreferenceChange(AppFontPreference.CUSTOM)
        }
    val pickCustomFont =
        remember(customFontPickerLauncher) {
            {
                customFontPickerLauncher.launch(CustomFontLoader.supportedMimeTypes)
            }
        }
    val onFontPreferenceSelected =
        remember(customFontUri, onFontPreferenceChange, pickCustomFont) {
            { value: AppFontPreference ->
                onFontPreferenceChange(value)
                if (value == AppFontPreference.CUSTOM && customFontUri.isBlank()) {
                    pickCustomFont()
                }
            }
        }

    val availableBackgroundStyles =
        PlayerBackgroundStyle.entries.filter {
            it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
    val isPlayerStyleCustomizationEnabled =
        when (playerDesignStyle) {
            PlayerDesignStyle.V7,
            PlayerDesignStyle.V8,
            PlayerDesignStyle.V9,
            -> false

            else -> true
        }
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val (defaultChip, onDefaultChipChange) =
        rememberEnumPreference(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY,
        )

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(isPlayerStyleCustomizationEnabled, playerBackground) {
        if (!isPlayerStyleCustomizationEnabled && playerBackground != PlayerBackgroundStyle.DEFAULT) {
            onPlayerBackgroundChange(PlayerBackgroundStyle.DEFAULT)
        }
    }

    LaunchedEffect(isPlayerStyleCustomizationEnabled) {
        if (!isPlayerStyleCustomizationEnabled) {
            showSliderOptionDialog = false
        }
    }

    if (showSliderOptionDialog && isPlayerStyleCustomizationEnabled) {
        val sliderStyles =
            remember {
                listOf(
                    SliderStyle.Standard,
                    SliderStyle.Wavy,
                    SliderStyle.Thick,
                    SliderStyle.Circular,
                    SliderStyle.Simple,
                )
            }
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sliderStyles.chunked(3).forEach { styleRow ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        styleRow.forEach { style ->
                            SliderStyleOptionCard(
                                sliderStyle = style,
                                selected = sliderStyle == style,
                                onClick = {
                                    onSliderStyleChange(style)
                                    showSliderOptionDialog = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - styleRow.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.theme)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_dynamic_theme)) },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        checked = dynamicTheme,
                        onCheckedChange = onDynamicThemeChange,
                    )
                }

                item(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.random_theme_on_startup)) },
                        description = stringResource(R.string.random_theme_on_startup_desc),
                        icon = { Icon(painterResource(R.drawable.shuffle), null) },
                        checked = randomThemeOnStartup,
                        onCheckedChange = onRandomThemeOnStartupChange,
                    )
                }

                item(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.color_palette)) },
                        description = stringResource(R.string.customize_theme_colors),
                        icon = { Icon(painterResource(R.drawable.format_paint), null) },
                        onClick = { navController.navigate("settings/appearance/palette_picker") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.app_icon)) },
                        description = stringResource(R.string.app_icon_description),
                        icon = { Icon(painterResource(R.drawable.app_icon_small), null) },
                        onClick = { navController.navigate("settings/appearance/icon") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.dark_theme)) },
                        icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                        selectedValue = darkMode,
                        onValueSelected = onDarkModeChange,
                        valueText = {
                            when (it) {
                                DarkMode.ON -> stringResource(R.string.dark_theme_on)
                                DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                                DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                            }
                        },
                    )
                }

                item(visible = useDarkTheme) {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.pure_black)) },
                        icon = { Icon(painterResource(R.drawable.contrast), null) },
                        checked = pureBlack,
                        onCheckedChange = onPureBlackChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_blur)) },
                        description = stringResource(R.string.disable_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_off), null) },
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_animations)) },
                        description = stringResource(R.string.disable_animations_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = disableAnimations,
                        onCheckedChange = onDisableAnimationsChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.blur_intensity)) },
                        description = stringResource(R.string.blur_intensity_value, blurRadius.roundToInt()),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = !disableBlur,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = blurRadius,
                                onValueChange = onBlurRadiusChange,
                                valueRange = 0f..64f,
                                steps = 63,
                                enabled = !disableBlur,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.album_backdrop)) },
                        description = stringResource(R.string.album_backdrop_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        checked = backdropEnabled,
                        onCheckedChange = onBackdropEnabledChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.backdrop_blur_amount)) },
                        description = stringResource(R.string.backdrop_blur_amount_value, backdropBlurAmount),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = backdropEnabled,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = backdropBlurAmount.toFloat(),
                                onValueChange = { onBackdropBlurAmountChange(it.roundToInt()) },
                                valueRange = 0f..100f,
                                steps = 19,
                                enabled = backdropEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.font_preference)) },
                        description = stringResource(R.string.font_preference_desc),
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        selectedValue = fontPreference,
                        onValueSelected = onFontPreferenceSelected,
                        valueText = {
                            when (it) {
                                AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                                AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                                AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                            }
                        },
                    )
                }

                item(visible = fontPreference == AppFontPreference.CUSTOM) {
                    val customFontDescription =
                        if (customFontName.isNotBlank()) {
                            customFontName
                        } else if (customFontUri.isBlank()) {
                            stringResource(R.string.custom_font_desc)
                        } else {
                            customFontUri
                        }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.custom_font)) },
                        description = customFontDescription,
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        onClick = pickCustomFont,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.player)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.player_design_style)) },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        selectedValue = playerDesignStyle,
                        onValueSelected = onPlayerDesignStyleChange,
                        valueText = {
                            when (it) {
                                PlayerDesignStyle.V1 -> stringResource(R.string.player_design_v1)
                                PlayerDesignStyle.V2 -> stringResource(R.string.player_design_v2)
                                PlayerDesignStyle.V3 -> stringResource(R.string.player_design_v3)
                                PlayerDesignStyle.V4 -> stringResource(R.string.player_design_v4)
                                PlayerDesignStyle.V5 -> stringResource(R.string.player_design_v5)
                                PlayerDesignStyle.V6 -> stringResource(R.string.player_design_v6)
                                PlayerDesignStyle.V7 -> stringResource(R.string.player_design_v7)
                                PlayerDesignStyle.V8 -> stringResource(R.string.player_design_v8)
                                PlayerDesignStyle.V9 -> stringResource(R.string.player_design_v9)
                            }
                        },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.player_background_style)) },
                        description =
                            if (isPlayerStyleCustomizationEnabled) {
                                null
                            } else {
                                stringResource(R.string.player_background_style_v8_v9_desc)
                            },
                        icon = { Icon(painterResource(R.drawable.gradient), null) },
                        selectedValue = playerBackground,
                        onValueSelected = onPlayerBackgroundChange,
                        isEnabled = isPlayerStyleCustomizationEnabled,
                        valueText = {
                            when (it) {
                                PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                                PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                PlayerBackgroundStyle.CUSTOM -> stringResource(R.string.custom)
                                PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                PlayerBackgroundStyle.COLORING -> stringResource(R.string.coloring)
                                PlayerBackgroundStyle.BLUR_GRADIENT -> stringResource(R.string.blur_gradient)
                                PlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
                                PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow Animated"
                            }
                        },
                    )
                }

                item(visible = playerBackground == PlayerBackgroundStyle.CUSTOM) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.customized_background)) },
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        onClick = { navController.navigate("customize_background") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.mini_player_background_style)) },
                        icon = { Icon(painterResource(R.drawable.gradient), null) },
                        selectedValue = miniPlayerBackground,
                        onValueSelected = onMiniPlayerBackgroundChange,
                        valueText = {
                            when (it) {
                                MiniPlayerBackgroundStyle.THEME -> stringResource(R.string.follow_theme)
                                MiniPlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                MiniPlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_player_thumbnail)) },
                        description = stringResource(R.string.hide_player_thumbnail_desc),
                        icon = { Icon(painterResource(R.drawable.hide_image), null) },
                        checked = hidePlayerThumbnail,
                        onCheckedChange = onHidePlayerThumbnailChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.archivetune_canvas)) },
                        description = stringResource(R.string.archivetune_canvas_desc),
                        icon = { Icon(painterResource(R.drawable.motion_photos_on), null) },
                        checked = archiveTuneCanvasEnabled,
                        onCheckedChange = onArchiveTuneCanvasEnabledChange,
                    )
                }

                item {
                    ThumbnailCornerRadiusSelectorButton(
                        onRadiusSelected = {},
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.crop_thumbnail_to_square)) },
                        description = stringResource(R.string.crop_thumbnail_to_square_desc),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        checked = cropThumbnailToSquare,
                        onCheckedChange = onCropThumbnailToSquareChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aod_customize_title)) },
                        description = stringResource(R.string.aod_customize_entry_desc),
                        icon = { Icon(painterResource(R.drawable.bedtime), null) },
                        onClick = { navController.navigate("settings/appearance/aod_customized") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.player_buttons_style)) },
                        description =
                            if (isPlayerStyleCustomizationEnabled) {
                                null
                            } else {
                                stringResource(R.string.player_background_style_v8_v9_desc)
                            },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        selectedValue = playerButtonsStyle,
                        onValueSelected = onPlayerButtonsStyleChange,
                        isEnabled = isPlayerStyleCustomizationEnabled,
                        valueText = {
                            when (it) {
                                PlayerButtonsStyle.DEFAULT -> stringResource(R.string.default_style)
                                PlayerButtonsStyle.SECONDARY -> stringResource(R.string.secondary_color_style)
                            }
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.player_slider_style)) },
                        description = sliderStyleLabel(sliderStyle),
                        icon = { Icon(painterResource(R.drawable.sliders), null) },
                        onClick = {
                            showSliderOptionDialog = true
                        },
                        isEnabled = isPlayerStyleCustomizationEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeThumbnail,
                        onCheckedChange = onSwipeThumbnailChange,
                    )
                }

                item(visible = swipeThumbnail) {
                    var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }

                    if (showSensitivityDialog) {
                        var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }

                        DefaultDialog(
                            onDismiss = {
                                tempSensitivity = swipeSensitivity
                                showSensitivityDialog = false
                            },
                            buttons = {
                                TextButton(
                                    onClick = {
                                        tempSensitivity = 0.73f
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.reset))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                TextButton(
                                    onClick = {
                                        tempSensitivity = swipeSensitivity
                                        showSensitivityDialog = false
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        onSwipeSensitivityChange(tempSensitivity)
                                        showSensitivityDialog = false
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            },
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.swipe_sensitivity),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )

                                Text(
                                    text = stringResource(R.string.sensitivity_percentage, (tempSensitivity * 100).roundToInt()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )

                                Slider(
                                    value = tempSensitivity,
                                    onValueChange = { tempSensitivity = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.swipe_sensitivity)) },
                        description = stringResource(R.string.sensitivity_percentage, (swipeSensitivity * 100).roundToInt()),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { showSensitivityDialog = true },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.misc)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.quick_picks_display_mode)) },
                        icon = { Icon(painterResource(R.drawable.grid_view), null) },
                        selectedValue = quickPicksDisplayMode,
                        onValueSelected = onQuickPicksDisplayModeChange,
                        valueText = {
                            when (it) {
                                QuickPicksDisplayMode.CARD -> stringResource(R.string.quick_picks_display_mode_card)
                                QuickPicksDisplayMode.LIST -> stringResource(R.string.quick_picks_display_mode_list)
                            }
                        },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.default_open_tab)) },
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = defaultOpenTab,
                        onValueSelected = onDefaultOpenTabChange,
                        valueText = {
                            when (it) {
                                NavigationTab.HOME -> stringResource(R.string.home)
                                NavigationTab.SEARCH -> stringResource(R.string.search)
                                NavigationTab.MOODANDGENRES -> stringResource(R.string.mood_and_genres)
                                NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.default_lib_chips)) },
                        icon = { Icon(painterResource(R.drawable.tab), null) },
                        selectedValue = defaultChip,
                        values =
                            listOf(
                                LibraryFilter.LIBRARY,
                                LibraryFilter.PLAYLISTS,
                                LibraryFilter.SONGS,
                                LibraryFilter.ALBUMS,
                                LibraryFilter.ARTISTS,
                            ),
                        valueText = {
                            when (it) {
                                LibraryFilter.SONGS -> stringResource(R.string.songs)
                                LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
                                LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                        onValueSelected = onDefaultChipChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_home_category_chips)) },
                        description = stringResource(R.string.show_home_category_chips_desc),
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        checked = showHomeCategoryChips,
                        onCheckedChange = onShowHomeCategoryChipsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_tags_in_library)) },
                        description = stringResource(R.string.show_tags_in_library_desc),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = showTagsInLibrary,
                        onCheckedChange = onShowTagsInLibraryChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.swipe_song_to_add)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeToSong,
                        onCheckedChange = onSwipeToSongChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderStyleOptionCard(
    sliderStyle: SliderStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember {
        mutableFloatStateOf(0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp),
                ).clickable(onClick = onClick)
                .padding(16.dp),
    ) {
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = sliderValue,
            valueRange = 0f..1f,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {},
            activeColor = MaterialTheme.colorScheme.primary,
            isPlaying = true,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )

        Text(
            text = sliderStyleLabel(sliderStyle),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun sliderStyleLabel(sliderStyle: SliderStyle): String =
    when (sliderStyle) {
        SliderStyle.Standard -> stringResource(R.string.slider_style_standard)
        SliderStyle.Wavy -> stringResource(R.string.slider_style_wavy)
        SliderStyle.Thick -> stringResource(R.string.slider_style_thick)
        SliderStyle.Circular -> stringResource(R.string.slider_style_circular)
        SliderStyle.Simple -> stringResource(R.string.slider_style_simple)
    }

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    MOODANDGENRES,
    LIBRARY,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
