/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerCustomBlurKey
import moe.rukamori.archivetune.constants.PlayerCustomBrightnessKey
import moe.rukamori.archivetune.constants.PlayerCustomContrastKey
import moe.rukamori.archivetune.constants.PlayerCustomImageUriKey
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

private const val DEFAULT_BLUR = 0f
private const val DEFAULT_CONTRAST = 1f
private const val DEFAULT_BRIGHTNESS = 1f
private val BLUR_RANGE = 0f..50f
private val TONE_RANGE = 0.5f..2f
private val EXPANDED_CONTENT_BREAKPOINT = 840.dp
private val CONTENT_MAX_WIDTH = 1200.dp
private const val PLAYER_PREVIEW_ASPECT_RATIO = 718f / 1518f
private const val LYRICS_PREVIEW_ASPECT_RATIO = 720f / 1386f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeBackground(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val (imageUri, onImageUriChange) = rememberPreference(PlayerCustomImageUriKey, "")
    val (blur, onBlurChange) = rememberPreference(PlayerCustomBlurKey, DEFAULT_BLUR)
    val (contrast, onContrastChange) = rememberPreference(PlayerCustomContrastKey, DEFAULT_CONTRAST)
    val (brightness, onBrightnessChange) =
        rememberPreference(PlayerCustomBrightnessKey, DEFAULT_BRIGHTNESS)

    val permissionErrorMessage = stringResource(R.string.custom_background_permission_error)
    val permissionCleanupErrorMessage =
        stringResource(R.string.custom_background_permission_cleanup_error)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri ->
            selectedUri ?: return@rememberLauncherForActivityResult

            if (persistBackgroundImagePermission(context, selectedUri)) {
                val previousUri = imageUri
                onImageUriChange(selectedUri.toString())
                if (previousUri.isNotBlank() && previousUri != selectedUri.toString()) {
                    if (!releaseBackgroundImagePermission(context, previousUri)) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(permissionCleanupErrorMessage)
                        }
                    }
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(permissionErrorMessage)
                }
            }
        }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.customize_background_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = {
                    Text(text = stringResource(R.string.custom_background_subtitle))
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        modifier = Modifier.padding(start = 5.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).verticalScroll(rememberScrollState())
                    .padding(
                        start = SettingsDimensions.ScreenHorizontalPadding,
                        top = 12.dp,
                        end = SettingsDimensions.ScreenHorizontalPadding,
                        bottom = SettingsDimensions.ScreenBottomPadding,
                    ),
            contentAlignment = Alignment.TopCenter,
        ) {
            CustomizeBackgroundContent(
                imageUri = imageUri,
                blur = blur.coerceIn(BLUR_RANGE),
                contrast = contrast.coerceIn(TONE_RANGE),
                brightness = brightness.coerceIn(TONE_RANGE),
                onChooseImage = { launcher.launch(arrayOf("image/*")) },
                onRemoveImage = {
                    val permissionReleased = releaseBackgroundImagePermission(context, imageUri)
                    onImageUriChange("")
                    if (!permissionReleased) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(permissionCleanupErrorMessage)
                        }
                    }
                },
                onBlurChange = onBlurChange,
                onContrastChange = onContrastChange,
                onBrightnessChange = onBrightnessChange,
                onResetAdjustments = {
                    onBlurChange(DEFAULT_BLUR)
                    onContrastChange(DEFAULT_CONTRAST)
                    onBrightnessChange(DEFAULT_BRIGHTNESS)
                },
                onSave = {
                    onBlurChange(blur.coerceIn(BLUR_RANGE))
                    onContrastChange(contrast.coerceIn(TONE_RANGE))
                    onBrightnessChange(brightness.coerceIn(TONE_RANGE))
                    navController.navigateUp()
                },
                modifier =
                    Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CustomizeBackgroundContent(
    imageUri: String,
    blur: Float,
    contrast: Float,
    brightness: Float,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onBlurChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onResetAdjustments: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImage = imageUri.isNotBlank()
    val resetEnabled =
        blur != DEFAULT_BLUR || contrast != DEFAULT_CONTRAST || brightness != DEFAULT_BRIGHTNESS

    BoxWithConstraints(modifier = modifier) {
        if (maxWidth >= EXPANDED_CONTENT_BREAKPOINT) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackgroundPreviewSection(
                        imageUri = imageUri,
                        blur = blur,
                        contrast = contrast,
                        brightness = brightness,
                    )
                    BackgroundImageActions(
                        hasImage = hasImage,
                        onChooseImage = onChooseImage,
                        onRemoveImage = onRemoveImage,
                    )
                }

                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackgroundAdjustmentSection(
                        blur = blur,
                        contrast = contrast,
                        brightness = brightness,
                        resetEnabled = resetEnabled,
                        onBlurChange = onBlurChange,
                        onContrastChange = onContrastChange,
                        onBrightnessChange = onBrightnessChange,
                        onReset = onResetAdjustments,
                    )
                    SaveBackgroundButton(onClick = onSave)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BackgroundPreviewSection(
                    imageUri = imageUri,
                    blur = blur,
                    contrast = contrast,
                    brightness = brightness,
                )
                BackgroundImageActions(
                    hasImage = hasImage,
                    onChooseImage = onChooseImage,
                    onRemoveImage = onRemoveImage,
                )
                BackgroundAdjustmentSection(
                    blur = blur,
                    contrast = contrast,
                    brightness = brightness,
                    resetEnabled = resetEnabled,
                    onBlurChange = onBlurChange,
                    onContrastChange = onContrastChange,
                    onBrightnessChange = onBrightnessChange,
                    onReset = onResetAdjustments,
                )
                SaveBackgroundButton(onClick = onSave)
            }
        }
    }
}

@Composable
private fun BackgroundPreviewSection(
    imageUri: String,
    blur: Float,
    contrast: Float,
    brightness: Float,
    modifier: Modifier = Modifier,
) {
    val parsedUri = remember(imageUri) { imageUri.takeIf { it.isNotBlank() }?.let(Uri::parse) }
    val colorFilter =
        remember(contrast, brightness) {
            ColorFilter.colorMatrix(backgroundColorMatrix(contrast, brightness))
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                BackgroundPreviewPane(
                    title = stringResource(R.string.custom_background_preview_player),
                    previewDrawable = R.drawable.player_preview,
                    aspectRatio = PLAYER_PREVIEW_ASPECT_RATIO,
                    imageUri = parsedUri,
                    blur = blur,
                    colorFilter = colorFilter,
                    modifier = Modifier.weight(1f),
                )
                BackgroundPreviewPane(
                    title = stringResource(R.string.custom_background_preview_lyrics),
                    previewDrawable = R.drawable.lyrics_preview,
                    aspectRatio = LYRICS_PREVIEW_ASPECT_RATIO,
                    imageUri = parsedUri,
                    blur = blur,
                    colorFilter = colorFilter,
                    modifier = Modifier.weight(1f),
                )
            }
            if (parsedUri == null) {
                Text(
                    text = stringResource(R.string.custom_background_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackgroundPreviewPane(
    title: String,
    previewDrawable: Int,
    aspectRatio: Float,
    imageUri: Uri?,
    blur: Float,
    colorFilter: ColorFilter,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUri == null) {
                Icon(
                    painter = painterResource(R.drawable.image),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .blur(blur.dp),
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                )
                Image(
                    painter = painterResource(previewDrawable),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

@Composable
private fun BackgroundImageActions(
    hasImage: Boolean,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onChooseImage,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(
                painter = painterResource(if (hasImage) R.drawable.edit else R.drawable.image),
                contentDescription = null,
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text =
                    stringResource(
                        if (hasImage) R.string.custom_background_replace_image else R.string.add_image,
                    ),
            )
        }
        if (hasImage) {
            OutlinedButton(
                onClick = onRemoveImage,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null,
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.custom_background_remove_image))
            }
        }
    }
}

@Composable
private fun BackgroundAdjustmentSection(
    blur: Float,
    contrast: Float,
    brightness: Float,
    resetEnabled: Boolean,
    onBlurChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.custom_background_adjustments),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = stringResource(R.string.custom_background_adjustments_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            BackgroundAdjustmentSlider(
                label = stringResource(R.string.blur),
                valueLabel =
                    stringResource(
                        R.string.custom_background_blur_value,
                        blur.roundToInt(),
                    ),
                value = blur,
                valueRange = BLUR_RANGE,
                onValueChange = onBlurChange,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            BackgroundAdjustmentSlider(
                label = stringResource(R.string.contrast),
                valueLabel =
                    stringResource(
                        R.string.custom_background_scale_value,
                        (contrast * 100f).roundToInt(),
                    ),
                value = contrast,
                valueRange = TONE_RANGE,
                onValueChange = onContrastChange,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            BackgroundAdjustmentSlider(
                label = stringResource(R.string.brightness),
                valueLabel =
                    stringResource(
                        R.string.custom_background_scale_value,
                        (brightness * 100f).roundToInt(),
                    ),
                value = brightness,
                valueRange = TONE_RANGE,
                onValueChange = onBrightnessChange,
            )
        }
        OutlinedButton(
            onClick = onReset,
            enabled = resetEnabled,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(text = stringResource(R.string.reset))
        }
    }
}

@Composable
private fun BackgroundAdjustmentSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun SaveBackgroundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(text = stringResource(R.string.save))
    }
}

private fun backgroundColorMatrix(
    contrast: Float,
    brightness: Float,
): ColorMatrix {
    val translation = (1f - contrast) * 128f + (brightness - 1f) * 255f
    return ColorMatrix(
        floatArrayOf(
            contrast,
            0f,
            0f,
            0f,
            translation,
            0f,
            contrast,
            0f,
            0f,
            translation,
            0f,
            0f,
            contrast,
            0f,
            translation,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )
}

private fun persistBackgroundImagePermission(
    context: Context,
    uri: Uri,
): Boolean =
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        true
    } catch (_: SecurityException) {
        false
    }

private fun releaseBackgroundImagePermission(
    context: Context,
    uriString: String,
): Boolean {
    val uri = uriString.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return true
    val permission =
        context.contentResolver.persistedUriPermissions.firstOrNull { persistedPermission ->
            persistedPermission.uri == uri
        }
    if (permission != null) {
        val permissionFlags =
            (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (permissionFlags == 0) return true
        return try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                permissionFlags,
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }
    return true
}
