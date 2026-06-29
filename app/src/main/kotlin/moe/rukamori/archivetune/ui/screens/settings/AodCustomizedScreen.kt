/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodAccentStyle
import moe.rukamori.archivetune.constants.AodAccentStyleKey
import moe.rukamori.archivetune.constants.AodAmbientIntensityKey
import moe.rukamori.archivetune.constants.AodArtworkGlowKey
import moe.rukamori.archivetune.constants.AodBackgroundStyle
import moe.rukamori.archivetune.constants.AodBackgroundStyleKey
import moe.rukamori.archivetune.constants.AodContentPosition
import moe.rukamori.archivetune.constants.AodContentPositionKey
import moe.rukamori.archivetune.constants.AodControlSizeKey
import moe.rukamori.archivetune.constants.AodControlStyle
import moe.rukamori.archivetune.constants.AodControlStyleKey
import moe.rukamori.archivetune.constants.AodHorizontalPaddingKey
import moe.rukamori.archivetune.constants.AodShowAlbumKey
import moe.rukamori.archivetune.constants.AodShowArtistKey
import moe.rukamori.archivetune.constants.AodShowControlsKey
import moe.rukamori.archivetune.constants.AodShowExitButtonKey
import moe.rukamori.archivetune.constants.AodShowProgressKey
import moe.rukamori.archivetune.constants.AodShowThumbnailKey
import moe.rukamori.archivetune.constants.AodShowTimeLabelsKey
import moe.rukamori.archivetune.constants.AodTextAlignment
import moe.rukamori.archivetune.constants.AodTextAlignmentKey
import moe.rukamori.archivetune.constants.AodThumbnailShape
import moe.rukamori.archivetune.constants.AodThumbnailShapeKey
import moe.rukamori.archivetune.constants.AodThumbnailShapeRotationKey
import moe.rukamori.archivetune.constants.AodThumbnailSizeKey
import moe.rukamori.archivetune.constants.AodTitleMaxLinesKey
import moe.rukamori.archivetune.constants.AodVerticalSpacingKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.supportsArtworkGlowShadow
import moe.rukamori.archivetune.ui.utils.toComposeShape
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

@Immutable
private data class AodPreviewSettings(
    val thumbnailShape: AodThumbnailShape,
    val thumbnailSize: Float,
    val thumbnailShapeRotation: Int,
    val thumbnailCornerRadius: Float,
    val showThumbnail: Boolean,
    val showArtist: Boolean,
    val showAlbum: Boolean,
    val showProgress: Boolean,
    val showTimeLabels: Boolean,
    val showControls: Boolean,
    val showExitButton: Boolean,
    val artworkGlow: Boolean,
    val backgroundStyle: AodBackgroundStyle,
    val accentStyle: AodAccentStyle,
    val contentPosition: AodContentPosition,
    val textAlignment: AodTextAlignment,
    val controlStyle: AodControlStyle,
    val controlSize: Float,
    val horizontalPadding: Float,
    val verticalSpacing: Float,
    val titleMaxLines: Int,
    val ambientIntensity: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AodCustomizedScreen(navController: NavController) {
    val scrollBehavior = appBarScrollBehavior()
    val (thumbnailShape, onThumbnailShapeChange) =
        rememberEnumPreference(
            AodThumbnailShapeKey,
            defaultValue = AodThumbnailShape.ROUNDED,
        )
    val (thumbnailSize, onThumbnailSizeChange) = rememberPreference(AodThumbnailSizeKey, defaultValue = 260f)
    val (thumbnailShapeRotation, onThumbnailShapeRotationChange) = rememberPreference(AodThumbnailShapeRotationKey, defaultValue = 0)
    val (thumbnailCornerRadius) = rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 16f)
    val (showThumbnail, onShowThumbnailChange) = rememberPreference(AodShowThumbnailKey, defaultValue = true)
    val (showArtist, onShowArtistChange) = rememberPreference(AodShowArtistKey, defaultValue = true)
    val (showAlbum, onShowAlbumChange) = rememberPreference(AodShowAlbumKey, defaultValue = false)
    val (showProgress, onShowProgressChange) = rememberPreference(AodShowProgressKey, defaultValue = true)
    val (showTimeLabels, onShowTimeLabelsChange) = rememberPreference(AodShowTimeLabelsKey, defaultValue = true)
    val (showControls, onShowControlsChange) = rememberPreference(AodShowControlsKey, defaultValue = true)
    val (showExitButton, onShowExitButtonChange) = rememberPreference(AodShowExitButtonKey, defaultValue = true)
    val (artworkGlow, onArtworkGlowChange) = rememberPreference(AodArtworkGlowKey, defaultValue = true)
    val (backgroundStyle, onBackgroundStyleChange) =
        rememberEnumPreference(
            AodBackgroundStyleKey,
            defaultValue = AodBackgroundStyle.PURE_BLACK,
        )
    val (accentStyle, onAccentStyleChange) =
        rememberEnumPreference(
            AodAccentStyleKey,
            defaultValue = AodAccentStyle.MONOCHROME,
        )
    val (contentPosition, onContentPositionChange) =
        rememberEnumPreference(
            AodContentPositionKey,
            defaultValue = AodContentPosition.CENTER,
        )
    val (textAlignment, onTextAlignmentChange) =
        rememberEnumPreference(
            AodTextAlignmentKey,
            defaultValue = AodTextAlignment.CENTER,
        )
    val (controlStyle, onControlStyleChange) =
        rememberEnumPreference(
            AodControlStyleKey,
            defaultValue = AodControlStyle.FILLED,
        )
    val (controlSize, onControlSizeChange) = rememberPreference(AodControlSizeKey, defaultValue = 64f)
    val (horizontalPadding, onHorizontalPaddingChange) = rememberPreference(AodHorizontalPaddingKey, defaultValue = 40f)
    val (verticalSpacing, onVerticalSpacingChange) = rememberPreference(AodVerticalSpacingKey, defaultValue = 20f)
    val (titleMaxLines, onTitleMaxLinesChange) = rememberPreference(AodTitleMaxLinesKey, defaultValue = 1)
    val (ambientIntensity, onAmbientIntensityChange) = rememberPreference(AodAmbientIntensityKey, defaultValue = 0.18f)

    val previewSettings =
        remember(
            thumbnailShape,
            thumbnailSize,
            thumbnailShapeRotation,
            thumbnailCornerRadius,
            showThumbnail,
            showArtist,
            showAlbum,
            showProgress,
            showTimeLabels,
            showControls,
            showExitButton,
            artworkGlow,
            backgroundStyle,
            accentStyle,
            contentPosition,
            textAlignment,
            controlStyle,
            controlSize,
            horizontalPadding,
            verticalSpacing,
            titleMaxLines,
            ambientIntensity,
        ) {
            AodPreviewSettings(
                thumbnailShape = thumbnailShape,
                thumbnailSize = thumbnailSize,
                thumbnailShapeRotation = thumbnailShapeRotation,
                thumbnailCornerRadius = thumbnailCornerRadius,
                showThumbnail = showThumbnail,
                showArtist = showArtist,
                showAlbum = showAlbum,
                showProgress = showProgress,
                showTimeLabels = showTimeLabels,
                showControls = showControls,
                showExitButton = showExitButton,
                artworkGlow = artworkGlow,
                backgroundStyle = backgroundStyle,
                accentStyle = accentStyle,
                contentPosition = contentPosition,
                textAlignment = textAlignment,
                controlStyle = controlStyle,
                controlSize = controlSize,
                horizontalPadding = horizontalPadding,
                verticalSpacing = verticalSpacing,
                titleMaxLines = titleMaxLines,
                ambientIntensity = ambientIntensity,
            )
        }

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
                        text = stringResource(R.string.aod_customize_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = {
                    Text(
                        text = stringResource(R.string.aod_customize_subtitle),
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
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(
                key = "aod_preview",
                contentType = "preview",
            ) {
                AodPreviewCard(
                    settings = previewSettings,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            item(
                key = "aod_visibility",
                contentType = "preference_group",
            ) {
                PreferenceGroup(title = stringResource(R.string.aod_customize_visibility)) {
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_thumbnail)) },
                            icon = { Icon(painterResource(R.drawable.image), null) },
                            checked = showThumbnail,
                            onCheckedChange = onShowThumbnailChange,
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_artist)) },
                            icon = { Icon(painterResource(R.drawable.artist), null) },
                            checked = showArtist,
                            onCheckedChange = onShowArtistChange,
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_album)) },
                            icon = { Icon(painterResource(R.drawable.album), null) },
                            checked = showAlbum,
                            onCheckedChange = onShowAlbumChange,
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_progress)) },
                            icon = { Icon(painterResource(R.drawable.sliders), null) },
                            checked = showProgress,
                            onCheckedChange = onShowProgressChange,
                        )
                    }
                    item(visible = showProgress) {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_time_labels)) },
                            icon = { Icon(painterResource(R.drawable.timer), null) },
                            checked = showTimeLabels,
                            onCheckedChange = onShowTimeLabelsChange,
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_controls)) },
                            icon = { Icon(painterResource(R.drawable.buttons), null) },
                            checked = showControls,
                            onCheckedChange = onShowControlsChange,
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_show_exit_button)) },
                            icon = { Icon(painterResource(R.drawable.close), null) },
                            checked = showExitButton,
                            onCheckedChange = onShowExitButtonChange,
                        )
                    }
                }
            }

            item(
                key = "aod_thumbnail",
                contentType = "shape_picker",
            ) {
                AodShapePicker(
                    selectedShape = thumbnailShape,
                    cornerRadius = thumbnailCornerRadius,
                    shapeRotation = thumbnailShapeRotation,
                    onShapeSelected = onThumbnailShapeChange,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            item(
                key = "aod_layout",
                contentType = "preference_group",
            ) {
                PreferenceGroup(title = stringResource(R.string.aod_customize_layout)) {
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.aod_customize_background_style)) },
                            icon = { Icon(painterResource(R.drawable.gradient), null) },
                            selectedValue = backgroundStyle,
                            valueText = { it.label() },
                            onValueSelected = onBackgroundStyleChange,
                        )
                    }
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.aod_customize_accent_style)) },
                            icon = { Icon(painterResource(R.drawable.palette), null) },
                            selectedValue = accentStyle,
                            valueText = { it.label() },
                            onValueSelected = onAccentStyleChange,
                        )
                    }
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.aod_customize_content_position)) },
                            icon = { Icon(painterResource(R.drawable.format_align_center), null) },
                            selectedValue = contentPosition,
                            valueText = { it.label() },
                            onValueSelected = onContentPositionChange,
                        )
                    }
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.aod_customize_text_alignment)) },
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                            selectedValue = textAlignment,
                            valueText = { it.label() },
                            onValueSelected = onTextAlignmentChange,
                        )
                    }
                    item {
                        AodIntSliderPreference(
                            title = stringResource(R.string.aod_customize_title_max_lines),
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                            value = titleMaxLines,
                            valueRange = 1..3,
                            onValueChange = onTitleMaxLinesChange,
                        )
                    }
                }
            }

            item(
                key = "aod_scale",
                contentType = "preference_group",
            ) {
                PreferenceGroup(title = stringResource(R.string.aod_customize_scale_and_spacing)) {
                    item {
                        AodSliderPreference(
                            title = stringResource(R.string.aod_customize_thumbnail_size),
                            icon = { Icon(painterResource(R.drawable.image), null) },
                            value = thumbnailSize,
                            valueRange = 160f..340f,
                            steps = 35,
                            valueLabel = { stringResource(R.string.aod_customize_dp_value, it.roundToInt()) },
                            onValueChange = onThumbnailSizeChange,
                        )
                    }
                    item {
                        AodIntSliderPreference(
                            title = stringResource(R.string.aod_customize_shape_rotation),
                            icon = { Icon(painterResource(R.drawable.replay), null) },
                            value = thumbnailShapeRotation,
                            valueRange = 0..315,
                            steps = 7,
                            valueLabel = { stringResource(R.string.aod_customize_degree_value, it) },
                            onValueChange = onThumbnailShapeRotationChange,
                        )
                    }
                    item {
                        AodSliderPreference(
                            title = stringResource(R.string.aod_customize_horizontal_padding),
                            icon = { Icon(painterResource(R.drawable.format_align_center), null) },
                            value = horizontalPadding,
                            valueRange = 16f..72f,
                            steps = 13,
                            valueLabel = { stringResource(R.string.aod_customize_dp_value, it.roundToInt()) },
                            onValueChange = onHorizontalPaddingChange,
                        )
                    }
                    item {
                        AodSliderPreference(
                            title = stringResource(R.string.aod_customize_vertical_spacing),
                            icon = { Icon(painterResource(R.drawable.drag_handle), null) },
                            value = verticalSpacing,
                            valueRange = 8f..36f,
                            steps = 13,
                            valueLabel = { stringResource(R.string.aod_customize_dp_value, it.roundToInt()) },
                            onValueChange = onVerticalSpacingChange,
                        )
                    }
                    item {
                        AodSliderPreference(
                            title = stringResource(R.string.aod_customize_ambient_intensity),
                            icon = { Icon(painterResource(R.drawable.blur_on), null) },
                            value = ambientIntensity,
                            valueRange = 0f..1f,
                            steps = 20,
                            valueLabel = { stringResource(R.string.aod_customize_percent_value, (it * 100f).roundToInt()) },
                            onValueChange = onAmbientIntensityChange,
                        )
                    }
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.aod_customize_artwork_glow)) },
                            icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                            checked = artworkGlow,
                            onCheckedChange = onArtworkGlowChange,
                        )
                    }
                }
            }

            item(
                key = "aod_controls",
                contentType = "preference_group",
            ) {
                PreferenceGroup(title = stringResource(R.string.aod_customize_controls)) {
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.aod_customize_control_style)) },
                            icon = { Icon(painterResource(R.drawable.buttons), null) },
                            selectedValue = controlStyle,
                            valueText = { it.label() },
                            onValueSelected = onControlStyleChange,
                            isEnabled = showControls,
                        )
                    }
                    item {
                        AodSliderPreference(
                            title = stringResource(R.string.aod_customize_control_size),
                            icon = { Icon(painterResource(R.drawable.buttons), null) },
                            value = controlSize,
                            valueRange = 52f..84f,
                            steps = 15,
                            valueLabel = { stringResource(R.string.aod_customize_dp_value, it.roundToInt()) },
                            onValueChange = onControlSizeChange,
                            isEnabled = showControls,
                        )
                    }
                }
            }

            item(
                key = "aod_bottom_space",
                contentType = "spacer",
            ) {
                Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding))
            }
        }
    }
}

@Composable
private fun AodPreviewCard(
    settings: AodPreviewSettings,
    modifier: Modifier = Modifier,
) {
    val accentColor =
        when (settings.accentStyle) {
            AodAccentStyle.MONOCHROME -> Color.White
            AodAccentStyle.THEME -> MaterialTheme.colorScheme.primary
        }
    val supportsArtworkGlowShadow = settings.thumbnailShape.supportsArtworkGlowShadow()
    val thumbnailShape =
        settings.thumbnailShape.toComposeShape(
            cornerRadius = settings.thumbnailCornerRadius,
            startAngle = settings.thumbnailShapeRotation,
        )
    val textAlign = settings.textAlignment.toTextAlign()
    val horizontalAlignment = settings.textAlignment.toHorizontalAlignment()
    val contentAlignment = settings.contentPosition.toBoxAlignment()
    val previewArtworkSize = (settings.thumbnailSize * 0.46f).coerceIn(82f, 156f).dp
    val previewControlSize = (settings.controlSize * 0.58f).coerceIn(30f, 50f).dp
    val previewHorizontalPadding = (settings.horizontalPadding * 0.55f).coerceIn(12f, 36f).dp
    val previewSpacing = (settings.verticalSpacing * 0.48f).coerceIn(6f, 18f).dp

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.aod_customize_preview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .aspectRatio(0.64f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .aodPreviewBackground(
                            style = settings.backgroundStyle,
                            accentColor = accentColor,
                            ambientIntensity = settings.ambientIntensity,
                        ).border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.extraLarge,
                        ),
            ) {
                if (settings.showExitButton) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(20.dp),
                    )
                }

                Column(
                    horizontalAlignment = horizontalAlignment,
                    verticalArrangement = Arrangement.spacedBy(previewSpacing),
                    modifier =
                        Modifier
                            .align(contentAlignment)
                            .fillMaxWidth()
                            .padding(horizontal = previewHorizontalPadding)
                            .padding(vertical = 28.dp),
                ) {
                    if (settings.showThumbnail) {
                        PreviewArtwork(
                            shape = thumbnailShape,
                            accentColor = accentColor,
                            showGlow = settings.artworkGlow && supportsArtworkGlowShadow,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(previewArtworkSize),
                        )
                    }

                    Column(
                        horizontalAlignment = horizontalAlignment,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.aod_customize_sample_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = settings.titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (settings.showArtist) {
                            Text(
                                text = stringResource(R.string.aod_customize_sample_artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (settings.showAlbum) {
                            Text(
                                text = stringResource(R.string.aod_customize_sample_album),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.52f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (settings.showProgress) {
                        PreviewProgress(
                            accentColor = accentColor,
                            showTimeLabels = settings.showTimeLabels,
                        )
                    }

                    if (settings.showControls) {
                        PreviewControls(
                            accentColor = accentColor,
                            controlStyle = settings.controlStyle,
                            controlSize = previewControlSize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewArtwork(
    shape: Shape,
    accentColor: Color,
    showGlow: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .then(
                    if (showGlow) {
                        Modifier.shadow(24.dp, shape = shape, clip = false, ambientColor = accentColor, spotColor = accentColor)
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.92f),
                                Color(0xFF141414),
                            ),
                    ),
                ),
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.78f),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
        )
    }
}

@Composable
private fun PreviewProgress(
    accentColor: Color,
    showTimeLabels: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.46f)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(accentColor),
            )
        }
        if (showTimeLabels) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.aod_customize_sample_elapsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                Text(
                    text = stringResource(R.string.aod_customize_sample_duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
        }
    }
}

@Composable
private fun PreviewControls(
    accentColor: Color,
    controlStyle: AodControlStyle,
    controlSize: androidx.compose.ui.unit.Dp,
) {
    val centerContainer =
        when (controlStyle) {
            AodControlStyle.FILLED -> Color.White
            AodControlStyle.TONAL -> accentColor.copy(alpha = 0.22f)
            AodControlStyle.MINIMAL -> Color.Transparent
        }
    val centerContent =
        when (controlStyle) {
            AodControlStyle.FILLED -> Color.Black

            AodControlStyle.TONAL,
            AodControlStyle.MINIMAL,
            -> Color.White
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewControlIcon(
            iconRes = R.drawable.skip_previous,
            size = controlSize * 0.78f,
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.82f),
        )
        PreviewControlIcon(
            iconRes = R.drawable.pause,
            size = controlSize,
            containerColor = centerContainer,
            contentColor = centerContent,
            borderColor = if (controlStyle == AodControlStyle.MINIMAL) Color.White.copy(alpha = 0.32f) else Color.Transparent,
        )
        PreviewControlIcon(
            iconRes = R.drawable.skip_next,
            size = controlSize * 0.78f,
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun PreviewControlIcon(
    iconRes: Int,
    size: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color = Color.Transparent,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(containerColor)
                .border(1.dp, borderColor, MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
private fun AodShapePicker(
    selectedShape: AodThumbnailShape,
    cornerRadius: Float,
    shapeRotation: Int,
    onShapeSelected: (AodThumbnailShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = remember { AodThumbnailShape.entries.toList() }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.aod_customize_thumbnail_shape),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.aod_customize_thumbnail_shape_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                shapes.forEach { shape ->
                    AodShapeOption(
                        shape = shape,
                        selected = shape == selectedShape,
                        cornerRadius = cornerRadius,
                        shapeRotation = shapeRotation,
                        onClick = { onShapeSelected(shape) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AodShapeOption(
    shape: AodThumbnailShape,
    selected: Boolean,
    cornerRadius: Float,
    shapeRotation: Int,
    onClick: () -> Unit,
) {
    val composeShape = shape.toComposeShape(cornerRadius = cornerRadius, startAngle = shapeRotation)
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier =
            Modifier
                .width(102.dp)
                .heightIn(min = 112.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = composeShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                text = shape.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AodSliderPreference(
    title: String,
    icon: @Composable () -> Unit,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: @Composable (Float) -> String,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) {
        sliderValue = value
    }

    PreferenceEntry(
        title = { Text(title) },
        description = valueLabel(sliderValue),
        icon = icon,
        isEnabled = isEnabled,
        content = {
            Spacer(modifier = Modifier.height(10.dp))
            Slider(
                value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue.coerceIn(valueRange.start, valueRange.endInclusive)) },
                valueRange = valueRange,
                steps = steps,
                enabled = isEnabled,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        thumbColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun AodIntSliderPreference(
    title: String,
    icon: @Composable () -> Unit,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    steps: Int = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
    valueLabel: @Composable (Int) -> String = { it.toString() },
    isEnabled: Boolean = true,
) {
    var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) {
        sliderValue = value.toFloat()
    }
    val roundedValue = sliderValue.roundToInt().coerceIn(valueRange.first, valueRange.last)

    PreferenceEntry(
        title = { Text(title) },
        description = valueLabel(roundedValue),
        icon = icon,
        isEnabled = isEnabled,
        content = {
            Spacer(modifier = Modifier.height(10.dp))
            Slider(
                value = sliderValue.coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(roundedValue) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = steps,
                enabled = isEnabled,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        thumbColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun Modifier.aodPreviewBackground(
    style: AodBackgroundStyle,
    accentColor: Color,
    ambientIntensity: Float,
): Modifier {
    val alpha = ambientIntensity.coerceIn(0f, 1f)
    val brush =
        remember(style, accentColor, alpha) {
            when (style) {
                AodBackgroundStyle.PURE_BLACK -> {
                    Brush.verticalGradient(listOf(Color.Black, Color.Black))
                }

                AodBackgroundStyle.SOFT_RADIAL -> {
                    Brush.radialGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.22f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.TONAL_EDGE -> {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.18f * alpha),
                                Color.Black,
                                accentColor.copy(alpha = 0.12f * alpha),
                            ),
                    )
                }

                AodBackgroundStyle.AMBIENT_GLOW -> {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.28f * alpha),
                                Color.Black,
                                Color(0xFF101010),
                            ),
                    )
                }
            }
        }

    return background(brush)
}

@Composable
private fun AodThumbnailShape.label(): String =
    when (this) {
        AodThumbnailShape.ROUNDED -> stringResource(R.string.aod_shape_rounded)
        AodThumbnailShape.SQUARE -> stringResource(R.string.aod_shape_square)
        AodThumbnailShape.CIRCLE -> stringResource(R.string.aod_shape_circle)
        AodThumbnailShape.PILL -> stringResource(R.string.aod_shape_pill)
        AodThumbnailShape.ARCH -> stringResource(R.string.aod_shape_arch)
        AodThumbnailShape.SLANTED -> stringResource(R.string.aod_shape_slanted)
        AodThumbnailShape.DIAMOND -> stringResource(R.string.aod_shape_diamond)
        AodThumbnailShape.PENTAGON -> stringResource(R.string.aod_shape_pentagon)
        AodThumbnailShape.TRIANGLE -> stringResource(R.string.aod_shape_triangle)
        AodThumbnailShape.HEART -> stringResource(R.string.aod_shape_heart)
        AodThumbnailShape.FLOWER -> stringResource(R.string.aod_shape_flower)
        AodThumbnailShape.CLOVER_4 -> stringResource(R.string.aod_shape_clover_4)
        AodThumbnailShape.COOKIE_6 -> stringResource(R.string.aod_shape_cookie_6)
        AodThumbnailShape.COOKIE_9 -> stringResource(R.string.aod_shape_cookie_9)
        AodThumbnailShape.SUNNY -> stringResource(R.string.aod_shape_sunny)
        AodThumbnailShape.SOFT_BURST -> stringResource(R.string.aod_shape_soft_burst)
        AodThumbnailShape.GHOSTISH -> stringResource(R.string.aod_shape_ghostish)
        AodThumbnailShape.PIXEL_CIRCLE -> stringResource(R.string.aod_shape_pixel_circle)
    }

@Composable
private fun AodBackgroundStyle.label(): String =
    when (this) {
        AodBackgroundStyle.PURE_BLACK -> stringResource(R.string.aod_background_pure_black)
        AodBackgroundStyle.SOFT_RADIAL -> stringResource(R.string.aod_background_soft_radial)
        AodBackgroundStyle.TONAL_EDGE -> stringResource(R.string.aod_background_tonal_edge)
        AodBackgroundStyle.AMBIENT_GLOW -> stringResource(R.string.aod_background_ambient_glow)
    }

@Composable
private fun AodAccentStyle.label(): String =
    when (this) {
        AodAccentStyle.MONOCHROME -> stringResource(R.string.aod_accent_monochrome)
        AodAccentStyle.THEME -> stringResource(R.string.aod_accent_theme)
    }

@Composable
private fun AodContentPosition.label(): String =
    when (this) {
        AodContentPosition.TOP -> stringResource(R.string.aod_position_top)
        AodContentPosition.CENTER -> stringResource(R.string.aod_position_center)
        AodContentPosition.BOTTOM -> stringResource(R.string.aod_position_bottom)
    }

@Composable
private fun AodTextAlignment.label(): String =
    when (this) {
        AodTextAlignment.START -> stringResource(R.string.aod_alignment_start)
        AodTextAlignment.CENTER -> stringResource(R.string.aod_alignment_center)
        AodTextAlignment.END -> stringResource(R.string.aod_alignment_end)
    }

@Composable
private fun AodControlStyle.label(): String =
    when (this) {
        AodControlStyle.FILLED -> stringResource(R.string.aod_control_filled)
        AodControlStyle.TONAL -> stringResource(R.string.aod_control_tonal)
        AodControlStyle.MINIMAL -> stringResource(R.string.aod_control_minimal)
    }

private fun AodContentPosition.toBoxAlignment(): Alignment =
    when (this) {
        AodContentPosition.TOP -> Alignment.TopCenter
        AodContentPosition.CENTER -> Alignment.Center
        AodContentPosition.BOTTOM -> Alignment.BottomCenter
    }

private fun AodTextAlignment.toTextAlign(): TextAlign =
    when (this) {
        AodTextAlignment.START -> TextAlign.Start
        AodTextAlignment.CENTER -> TextAlign.Center
        AodTextAlignment.END -> TextAlign.End
    }

private fun AodTextAlignment.toHorizontalAlignment(): Alignment.Horizontal =
    when (this) {
        AodTextAlignment.START -> Alignment.Start
        AodTextAlignment.CENTER -> Alignment.CenterHorizontally
        AodTextAlignment.END -> Alignment.End
    }
