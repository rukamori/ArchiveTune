/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.palette.graphics.Palette
import androidx.window.core.layout.WindowSizeClass
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.ComposeToImage

@Immutable
private data class LyricsGlassStyleOptions(
    val items: ImmutableList<LyricsGlassStyle>,
)

fun shareLyricsAsText(
    context: Context,
    payload: LyricsSharePayload,
    songId: String?,
) {
    val songLink = songId?.takeIf { it.isNotBlank() }?.let { moe.rukamori.archivetune.utils.ArchiveTuneShareLinks.buildSongShareUrl(it) }
    val shareBody =
        buildString {
            append("\"")
            append(payload.lyricsText)
            append("\"\n\n")
            append(payload.songTitle)
            append(" - ")
            append(payload.artists)
            if (songLink != null) {
                append('\n')
                append(songLink)
            }
        }

    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_lyrics),
        ),
    )
}

@Composable
fun LyricsShareImageDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompactLayout =
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    var isSharing by remember { mutableStateOf(false) }
    var selectedGlassStyle by remember { mutableStateOf(LyricsGlassStyle.FrostedDark) }
    var paletteGlassStyle by remember { mutableStateOf<LyricsGlassStyle?>(null) }
    var options by remember { mutableStateOf(LyricsShareImageOptions()) }
    var areAdvancedOptionsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val coverUrl = mediaMetadata?.thumbnailUrl
        if (coverUrl == null) {
            paletteGlassStyle = null
            return@LaunchedEffect
        }
        val extractedStyle =
            withContext(Dispatchers.IO) {
                runCatching {
                    val loader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverUrl)
                            .allowHardware(false)
                            .build()
                    val bitmap = loader.execute(request).image?.toBitmap() ?: return@runCatching null
                    LyricsGlassStyle.fromPalette(Palette.from(bitmap).generate())
                }.getOrNull()
            }
        paletteGlassStyle = extractedStyle
    }

    val availableStyles by remember(paletteGlassStyle) {
        derivedStateOf {
            LyricsGlassStyleOptions(
                items =
                    ImmutableList.copyOf(
                        buildList {
                            paletteGlassStyle?.let(::add)
                            addAll(LyricsGlassStyle.allPresets.filterNot { it == paletteGlassStyle })
                        },
                    ),
            )
        }
    }

    val handleShare: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(context, R.string.lyrics_share_export_not_supported, Toast.LENGTH_SHORT).show()
        } else {
            isSharing = true
            scope.launch {
                try {
                    val image =
                        ComposeToImage.createLyricsImage(
                            context = context,
                            coverArtUrl = mediaMetadata?.thumbnailUrl,
                            songTitle = payload.songTitle,
                            artistName = payload.artists,
                            lyrics = payload.lyricsText,
                            width = options.aspectRatio.exportWidth,
                            height = options.aspectRatio.exportHeight,
                            glassStyle = selectedGlassStyle,
                            shareOptions = options,
                        )
                    val fileName = "lyrics_${System.currentTimeMillis()}"
                    val uri = ComposeToImage.saveBitmapAsFile(context, image, fileName)
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.share_lyrics),
                        ),
                    )
                    onDismissRequest()
                } catch (e: Exception) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.lyrics_share_export_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT,
                        ).show()
                } finally {
                    isSharing = false
                }
            }
        }
    }

    LyricsShareStudioDialog(
        mediaMetadata = mediaMetadata,
        payload = payload,
        options = options,
        onOptionsChange = { options = it },
        availableStyles = availableStyles,
        selectedGlassStyle = selectedGlassStyle,
        onStyleSelect = { selectedGlassStyle = it },
        areAdvancedOptionsVisible = !isCompactLayout || areAdvancedOptionsVisible,
        onShowAdvancedOptions = { areAdvancedOptionsVisible = true },
        isSharing = isSharing,
        isCompactLayout = isCompactLayout,
        onShare = handleShare,
        onDismissRequest = onDismissRequest,
    )

    if (isSharing) {
        LyricsShareLoadingDialog()
    }
}

@Composable
private fun LyricsShareStudioDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (!isSharing) onDismissRequest()
        },
        properties =
            DialogProperties(
                dismissOnBackPress = !isSharing,
                dismissOnClickOutside = !isSharing,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val outerPadding = if (isCompactLayout) 12.dp else 24.dp
            val maxDialogHeight = (maxHeight - outerPadding * 2).coerceAtLeast(1.dp)
            val maxDialogWidth = if (isCompactLayout) 560.dp else 980.dp

            Surface(
                modifier =
                    Modifier
                        .padding(outerPadding)
                        .fillMaxWidth()
                        .widthIn(max = maxDialogWidth)
                        .heightIn(max = maxDialogHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
            ) {
                LyricsShareStudioScaffold(
                    mediaMetadata = mediaMetadata,
                    payload = payload,
                    options = options,
                    onOptionsChange = onOptionsChange,
                    availableStyles = availableStyles,
                    selectedGlassStyle = selectedGlassStyle,
                    onStyleSelect = onStyleSelect,
                    areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                    onShowAdvancedOptions = onShowAdvancedOptions,
                    isSharing = isSharing,
                    isCompactLayout = isCompactLayout,
                    onShare = onShare,
                    onDismiss = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareLoadingDialog() {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
                Text(
                    text = stringResource(R.string.generating_image),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.please_wait),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareStudioScaffold(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val motionScheme = MaterialTheme.motionScheme
    val horizontalPadding = if (isCompactLayout) 16.dp else 20.dp
    val verticalPadding = if (isCompactLayout) 16.dp else 20.dp
    val sectionSpacing = if (isCompactLayout) 14.dp else 18.dp

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            if (isCompactLayout) {
                LyricsShareHeader(
                    payload = payload,
                    options = options,
                    modifier = Modifier.fillMaxWidth(),
                )
                PreviewContainer(
                    payload = payload,
                    mediaMetadata = mediaMetadata,
                    selectedGlassStyle = selectedGlassStyle,
                    options = options,
                    isCompactLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ControlsSection(
                    options = options,
                    onOptionsChange = onOptionsChange,
                    availableStyles = availableStyles,
                    selectedGlassStyle = selectedGlassStyle,
                    onStyleSelect = onStyleSelect,
                    areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                    onShowAdvancedOptions = onShowAdvancedOptions,
                    isCompactLayout = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PreviewContainer(
                        payload = payload,
                        mediaMetadata = mediaMetadata,
                        selectedGlassStyle = selectedGlassStyle,
                        options = options,
                        isCompactLayout = false,
                        modifier = Modifier.weight(1.1f),
                    )
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        LyricsShareHeader(
                            payload = payload,
                            options = options,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ControlsSection(
                            options = options,
                            onOptionsChange = onOptionsChange,
                            availableStyles = availableStyles,
                            selectedGlassStyle = selectedGlassStyle,
                            onStyleSelect = onStyleSelect,
                            areAdvancedOptionsVisible = areAdvancedOptionsVisible,
                            onShowAdvancedOptions = onShowAdvancedOptions,
                            isCompactLayout = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionsSection(
            isSharing = isSharing,
            isCompactLayout = isCompactLayout,
            onShare = onShare,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun LyricsShareHeader(
    payload: LyricsSharePayload,
    options: LyricsShareImageOptions,
    modifier: Modifier = Modifier,
) {
    val lyricSnippet =
        remember(payload.lyricsText) {
            payload.lyricsText
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.share_lyrics),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = payload.songTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = payload.artists,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lyricSnippet.isNotBlank()) {
            Text(
                text = lyricSnippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LyricsShareInfoPill(
            text =
                stringResource(
                    R.string.lyrics_share_resolution_value,
                    options.aspectRatio.exportWidth,
                    options.aspectRatio.exportHeight,
                ),
            emphasized = true,
        )
    }
}

@Composable
private fun PreviewContainer(
    payload: LyricsSharePayload,
    mediaMetadata: MediaMetadata?,
    selectedGlassStyle: LyricsGlassStyle,
    options: LyricsShareImageOptions,
    isCompactLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewWidthFraction =
        when (options.aspectRatio) {
            LyricsShareAspectRatio.Square -> if (isCompactLayout) 0.84f else 0.82f
            LyricsShareAspectRatio.Portrait -> if (isCompactLayout) 0.62f else 0.62f
            LyricsShareAspectRatio.Story -> if (isCompactLayout) 0.44f else 0.42f
        }
    val previewMaxWidth = if (isCompactLayout) 320.dp else 420.dp

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(previewWidthFraction)
                            .widthIn(max = previewMaxWidth)
                            .aspectRatio(options.aspectRatio.previewAspectRatio)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.large,
                            ).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LyricsImageCard(
                        lyricText = payload.lyricsText,
                        songTitle = payload.songTitle,
                        artistName = payload.artists,
                        coverArtUrl = mediaMetadata?.thumbnailUrl,
                        glassStyle = selectedGlassStyle,
                        shareOptions = options,
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsSection(
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    areAdvancedOptionsVisible: Boolean,
    onShowAdvancedOptions: () -> Unit,
    isCompactLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LyricsShareControlGroup(title = stringResource(R.string.lyrics_share_layout)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LyricsShareAspectRatio.entries.forEach { aspectRatio ->
                        LyricsAspectRatioOption(
                            aspectRatio = aspectRatio,
                            selected = options.aspectRatio == aspectRatio,
                            onClick = { onOptionsChange(options.copy(aspectRatio = aspectRatio)) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LyricsShareControlGroup(title = stringResource(R.string.customize_colors)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = if (isCompactLayout) 2 else 2,
                ) {
                    availableStyles.items.forEach { style ->
                        LyricsStyleOption(
                            style = style,
                            selected = selectedGlassStyle == style,
                            onClick = { onStyleSelect(style) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (areAdvancedOptionsVisible) {
                LyricsShareControlGroup(title = stringResource(R.string.more_options)) {
                    LyricsShareSlider(
                        title = stringResource(R.string.lyrics_share_background_blur),
                        valueLabel = stringResource(R.string.lyrics_share_background_blur_value, options.sanitizedBlurRadius.toInt()),
                        value = options.blurRadius,
                        onValueChange = { onOptionsChange(options.copy(blurRadius = it)) },
                        valueRange = 0f..48f,
                    )
                    LyricsShareSlider(
                        title = stringResource(R.string.lyrics_share_background_dim),
                        valueLabel = stringResource(R.string.lyrics_share_background_dim_value, (options.sanitizedDimAmount * 100).toInt()),
                        value = options.dimAmount,
                        onValueChange = { onOptionsChange(options.copy(dimAmount = it)) },
                        valueRange = 0.6f..1.6f,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { onOptionsChange(options.copy(showArtwork = !options.showArtwork)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.lyrics_share_show_cover),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.lyrics_share_show_cover_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = options.showArtwork,
                                onCheckedChange = { onOptionsChange(options.copy(showArtwork = it)) },
                            )
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = onShowAdvancedOptions,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.more_options),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareControlGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun LyricsShareInfoPill(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsAspectRatioOption(
    aspectRatio: LyricsShareAspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsAspectContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsAspectContent",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 96.dp)
                .heightIn(min = 48.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = stringResource(aspectRatio.labelRes),
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsStyleOption(
    style: LyricsGlassStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val optionShape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large
    val borderColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsStyleBorder",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsStyleContainer",
    )

    Surface(
        modifier =
            modifier
                .widthIn(min = 108.dp)
                .heightIn(min = 48.dp)
                .clip(optionShape)
                .clickable(onClick = onClick),
        shape = optionShape,
        color = containerColor,
        border = BorderStroke(width = if (selected) 1.5.dp else 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(style.surfaceTint.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .background(
                                color = style.surfaceTint.copy(alpha = style.surfaceAlpha),
                                shape = MaterialTheme.shapes.extraLarge,
                            ),
                )
            }
            Text(
                text = stringResource(style.labelRes),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun LyricsShareSlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionsSection(
    isSharing: Boolean,
    isCompactLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionModifier = Modifier.height(52.dp)
    val contentPadding =
        Modifier.padding(
            horizontal = if (isCompactLayout) 16.dp else 24.dp,
            vertical = 14.dp,
        )

    if (isCompactLayout) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onShare,
                enabled = !isSharing,
                modifier = actionModifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isSharing,
                modifier = actionModifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isSharing,
                modifier =
                    actionModifier
                        .weight(1f)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onShare,
                enabled = !isSharing,
                modifier =
                    actionModifier
                        .weight(1.2f)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
