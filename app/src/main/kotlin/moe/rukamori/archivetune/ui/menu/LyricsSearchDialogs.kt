/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.google.common.collect.ImmutableMap
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.buildLyricsSourceLabel
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.viewmodels.LyricsSearchFormat
import moe.rukamori.archivetune.viewmodels.LyricsSearchResultUiModel
import moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState
import kotlin.math.ceil

private val FieldOuterCorner = 24.dp
private val FieldInnerCorner = 6.dp
private val TitleFieldShape =
    RoundedCornerShape(
        topStart = FieldOuterCorner,
        topEnd = FieldOuterCorner,
        bottomStart = FieldInnerCorner,
        bottomEnd = FieldInnerCorner,
    )
private val ArtistFieldShape =
    RoundedCornerShape(
        topStart = FieldInnerCorner,
        topEnd = FieldInnerCorner,
        bottomStart = FieldOuterCorner,
        bottomEnd = FieldOuterCorner,
    )
private val ResultCollapsedCorner = 20.dp
private val ResultExpandedCorner = 32.dp
private const val PreviewCollapsedLines = 3
private const val PreviewExpandedLines = 14

private data class LyricsSearchFormatStyle(
    @StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int,
    val polygon: RoundedPolygon,
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchFormat.style(): LyricsSearchFormatStyle {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        LyricsSearchFormat.WORD_SYNCED ->
            LyricsSearchFormatStyle(
                labelResId = R.string.lyrics_word_sync,
                iconResId = R.drawable.lyrics,
                polygon = MaterialShapes.Cookie9Sided,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                accentColor = colors.primary,
            )

        LyricsSearchFormat.LINE_SYNCED ->
            LyricsSearchFormatStyle(
                labelResId = R.string.lyrics_synced_badge,
                iconResId = R.drawable.sync,
                polygon = MaterialShapes.Clover4Leaf,
                containerColor = colors.tertiary,
                contentColor = colors.onTertiary,
                accentColor = colors.tertiary,
            )

        LyricsSearchFormat.PLAIN ->
            LyricsSearchFormatStyle(
                labelResId = R.string.lyrics_search_plain_badge,
                iconResId = R.drawable.format_align_left,
                polygon = MaterialShapes.Cookie4Sided,
                containerColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                accentColor = colors.outline,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchLyricsInputDialog(
    songTitle: String,
    songArtists: String,
    thumbnailUrl: String?,
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maximumDialogHeight = (configuration.screenHeightDp.dp - 48.dp).coerceAtLeast(280.dp)
    val canSearch = titleField.text.isNotBlank()
    val canSearchOnline = canSearch || artistField.text.isNotBlank()
    val isEdited = titleField.text != songTitle || artistField.text != songArtists
    val guardedSearch = {
        if (canSearch) onSearch()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maximumDialogHeight),
        ) {
            Box {
                LyricsSearchAmbientShape(
                    polygon = MaterialShapes.Sunny,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 72.dp, y = (-88).dp)
                            .size(220.dp),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                ) {
                    LyricsSearchSongHeader(
                        songTitle = songTitle,
                        songArtists = songArtists,
                        thumbnailUrl = thumbnailUrl,
                        onDismiss = onDismiss,
                    )
                    Spacer(Modifier.height(24.dp))
                    Column(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        LyricsSearchTextField(
                            value = titleField,
                            onValueChange = onTitleFieldChange,
                            label = stringResource(R.string.song_title),
                            iconResId = R.drawable.music_note,
                            shape = TitleFieldShape,
                            isError = !canSearch,
                            imeAction = ImeAction.Next,
                            onSearch = guardedSearch,
                        )
                        LyricsSearchTextField(
                            value = artistField,
                            onValueChange = onArtistFieldChange,
                            label = stringResource(R.string.song_artists),
                            iconResId = R.drawable.artist,
                            shape = ArtistFieldShape,
                            isError = false,
                            imeAction = ImeAction.Search,
                            onSearch = guardedSearch,
                        )
                    }
                    LyricsSearchFieldFeedback(
                        showTitleRequired = !canSearch,
                        showReset = isEdited,
                        onReset = {
                            onTitleFieldChange(TextFieldValue(songTitle, TextRange(songTitle.length)))
                            onArtistFieldChange(TextFieldValue(songArtists, TextRange(songArtists.length)))
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    LyricsSearchInputActions(
                        canSearch = canSearch,
                        canSearchOnline = canSearchOnline,
                        onSearchOnline = onSearchOnline,
                        onSearch = guardedSearch,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchSongHeader(
    songTitle: String,
    songArtists: String,
    thumbnailUrl: String?,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LyricsSearchArtwork(thumbnailUrl = thumbnailUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.search_lyrics),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = songTitle,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (songArtists.isNotBlank()) {
                Text(
                    text = songArtists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.Top),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchArtwork(thumbnailUrl: String?) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motionScheme = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = remember { Animatable(if (animationsDisabled) 1f else 0f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, motionScheme.slowSpatialSpec())
        }
    }

    Box(modifier = Modifier.size(80.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val value = progress.value
                        val scale = 0.82f + 0.18f * value
                        scaleX = scale
                        scaleY = scale
                        rotationZ = (1f - value) * -24f
                        shape = EditorialMorphShape(morph, value.coerceIn(0f, 1f))
                        clip = true
                    }.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsSearchTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    @DrawableRes iconResId: Int,
    shape: Shape,
    isError: Boolean,
    imeAction: ImeAction,
    onSearch: () -> Unit,
) {
    val keyboardOptions = remember(imeAction) { KeyboardOptions(imeAction = imeAction) }
    val currentOnSearch by rememberUpdatedState(onSearch)
    val keyboardActions =
        remember(imeAction) {
            if (imeAction == ImeAction.Search) {
                KeyboardActions(onSearch = { currentOnSearch() })
            } else {
                KeyboardActions.Default
            }
        }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon =
            if (value.text.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange(TextFieldValue()) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            } else {
                null
            },
        isError = isError,
        shape = shape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                errorContainerColor = containerColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchFieldFeedback(
    showTitleRequired: Boolean,
    showReset: Boolean,
    onReset: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AnimatedVisibility(
                visible = showTitleRequired,
                enter = fadeIn(motionScheme.fastEffectsSpec()) + expandVertically(motionScheme.fastSpatialSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.fastSpatialSpec()),
            ) {
                Text(
                    text = stringResource(R.string.lyrics_search_title_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = showReset,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) + slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(motionScheme.fastEffectsSpec()) + slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 },
        ) {
            TextButton(
                onClick = onReset,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.history),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.reset))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchInputActions(
    canSearch: Boolean,
    canSearchOnline: Boolean,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onSearchOnline,
            enabled = canSearchOnline,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = buttonHeight),
        ) {
            Icon(
                painter = painterResource(R.drawable.language),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.search_online),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onSearch,
            enabled = canSearch,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = buttonHeight),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.search),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsSearchResultDialog(
    state: LyricsSearchScreenState,
    queryTitle: String,
    queryArtists: String,
    selectedFormat: LyricsSearchFormat?,
    onFormatSelected: (LyricsSearchFormat?) -> Unit,
    expandedResultId: String?,
    onExpandedResultChange: (String) -> Unit,
    onRefetch: () -> Unit,
    onResultSelected: (LyricsSearchResultUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val visibleResults =
        remember(state, selectedFormat) {
            when (state) {
                is LyricsSearchScreenState.Success ->
                    if (selectedFormat == null) {
                        state.results
                    } else {
                        state.results.filter { it.format == selectedFormat }
                    }

                else -> emptyList()
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box {
                    LyricsSearchAmbientShape(
                        polygon = MaterialShapes.Cookie12Sided,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 96.dp, y = (-104).dp)
                                .size(240.dp),
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LyricsSearchResultHeader(
                            state = state,
                            queryTitle = queryTitle,
                            queryArtists = queryArtists,
                            onRefetch = onRefetch,
                            onDismiss = onDismiss,
                        )
                        if (state is LyricsSearchScreenState.Success) {
                            LyricsSearchFormatFilter(
                                totalCount = state.results.size,
                                formatCounts = state.formatCounts,
                                selectedFormat = selectedFormat,
                                onFormatSelected = onFormatSelected,
                            )
                        }
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (state) {
                                LyricsSearchScreenState.Loading -> {
                                    item(key = "lyrics_search_loading", contentType = "lyrics_search_loading") {
                                        LyricsSearchLoadingContent()
                                    }
                                }

                                LyricsSearchScreenState.Empty -> {
                                    item(key = "lyrics_search_empty", contentType = "lyrics_search_message") {
                                        LyricsSearchMessageContent(
                                            iconResId = R.drawable.search_off,
                                            text = stringResource(R.string.lyrics_not_found),
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            actionLabel = stringResource(R.string.retry),
                                            onAction = onRefetch,
                                        )
                                    }
                                }

                                is LyricsSearchScreenState.Error -> {
                                    item(key = "lyrics_search_error", contentType = "lyrics_search_message") {
                                        LyricsSearchMessageContent(
                                            iconResId = R.drawable.error,
                                            text = stringResource(state.messageResId),
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                            actionLabel = stringResource(R.string.retry),
                                            onAction = onRefetch,
                                        )
                                    }
                                }

                                is LyricsSearchScreenState.Success -> {
                                    if (visibleResults.isEmpty()) {
                                        item(key = "lyrics_search_filter_empty", contentType = "lyrics_search_message") {
                                            LyricsSearchMessageContent(
                                                iconResId = R.drawable.filter_alt,
                                                text = stringResource(R.string.lyrics_search_filter_empty),
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                                actionLabel = stringResource(R.string.lyrics_search_show_all_formats),
                                                onAction = { onFormatSelected(null) },
                                                modifier =
                                                    if (animationsDisabled) {
                                                        Modifier
                                                    } else {
                                                        Modifier.animateItem(
                                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                                            placementSpec = motionScheme.defaultSpatialSpec(),
                                                            fadeOutSpec = motionScheme.fastEffectsSpec(),
                                                        )
                                                    },
                                            )
                                        }
                                    }
                                    items(
                                        items = visibleResults,
                                        key = { it.id },
                                        contentType = { "lyrics_search_result" },
                                    ) { result ->
                                        LyricsSearchResultCard(
                                            result = result,
                                            isExpanded = result.id == expandedResultId,
                                            onExpandedChange = { onExpandedResultChange(result.id) },
                                            onApply = { onResultSelected(result) },
                                            modifier =
                                                if (animationsDisabled) {
                                                    Modifier
                                                } else {
                                                    Modifier.animateItem(
                                                        fadeInSpec = motionScheme.defaultEffectsSpec(),
                                                        placementSpec = motionScheme.defaultSpatialSpec(),
                                                        fadeOutSpec = motionScheme.fastEffectsSpec(),
                                                    )
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchResultHeader(
    state: LyricsSearchScreenState,
    queryTitle: String,
    queryArtists: String,
    onRefetch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val isSearching =
        state == LyricsSearchScreenState.Loading ||
            state is LyricsSearchScreenState.Success && state.isSearching
    val isStreaming = state is LyricsSearchScreenState.Success && state.isSearching
    val status =
        when (state) {
            LyricsSearchScreenState.Loading -> stringResource(R.string.lyrics_searching_providers)
            LyricsSearchScreenState.Empty -> stringResource(R.string.lyrics_not_found)
            is LyricsSearchScreenState.Error -> stringResource(state.messageResId)
            is LyricsSearchScreenState.Success ->
                if (state.isSearching) {
                    stringResource(R.string.lyrics_search_still_searching)
                } else {
                    stringResource(R.string.lyrics_search_results_count, state.results.size)
                }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LyricsSearchStatusBadge(isSearching = isSearching)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_lyrics),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = queryTitle,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (queryArtists.isNotBlank()) {
                    Text(
                        text = queryArtists,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.Top),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (
                        slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 } +
                            fadeIn(motionScheme.defaultEffectsSpec())
                    ) togetherWith
                        (
                            slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 2 } +
                                fadeOut(motionScheme.fastEffectsSpec())
                        )
                },
                modifier = Modifier.weight(1f),
                label = "lyricsSearchStatus",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = !isSearching,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) + slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 },
                exit = fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                FilledTonalButton(
                    onClick = onRefetch,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.cached),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.refetch))
                }
            }
        }
        AnimatedVisibility(
            visible = isStreaming,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.fastSpatialSpec()),
        ) {
            LinearWavyProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp, end = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchStatusBadge(isSearching: Boolean) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motionScheme = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val morphProgress by animateFloatAsState(
        targetValue = if (isSearching) 1f else 0f,
        animationSpec = if (animationsDisabled) tween(0) else motionScheme.slowSpatialSpec(),
        label = "lyricsSearchBadgeMorph",
    )
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSearching, animationsDisabled) {
        if (isSearching && !animationsDisabled) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 5200, easing = LinearEasing), RepeatMode.Restart),
            )
        } else if (animationsDisabled) {
            rotation.snapTo(0f)
        } else {
            rotation.animateTo(ceil(rotation.value / 90f) * 90f, motionScheme.defaultSpatialSpec())
        }
    }

    Box(
        modifier =
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    rotationZ = rotation.value
                    shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f))
                    clip = true
                }.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.manage_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = -rotation.value },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchFormatFilter(
    totalCount: Int,
    formatCounts: ImmutableMap<LyricsSearchFormat, Int>,
    selectedFormat: LyricsSearchFormat?,
    onFormatSelected: (LyricsSearchFormat?) -> Unit,
) {
    val options = remember { listOf<LyricsSearchFormat?>(null) + LyricsSearchFormat.entries }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, format ->
            val count = if (format == null) totalCount else formatCounts[format] ?: 0
            val label = stringResource(format?.style()?.labelResId ?: R.string.filter_all)
            val iconResId = format?.style()?.iconResId ?: R.drawable.manage_search
            val checked = selectedFormat == format
            val description =
                pluralStringResource(R.plurals.lyrics_search_format_filter_description, count, label, count)

            ToggleButton(
                checked = checked,
                onCheckedChange = { onFormatSelected(format) },
                enabled = checked || count > 0,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier =
                    Modifier.semantics {
                        role = Role.RadioButton
                        contentDescription = description
                    },
            ) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = label,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                LyricsSearchCountBadge(count = count)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchCountBadge(count: Int) {
    val motionScheme = MaterialTheme.motionScheme
    val contentColor = LocalContentColor.current

    Box(
        modifier =
            Modifier
                .heightIn(min = 20.dp)
                .widthIn(min = 24.dp)
                .background(contentColor.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInVertically(motionScheme.fastSpatialSpec()) { it * direction } +
                        fadeIn(motionScheme.fastEffectsSpec())
                ) togetherWith
                    (
                        slideOutVertically(motionScheme.fastSpatialSpec()) { -it * direction } +
                            fadeOut(motionScheme.fastEffectsSpec())
                    )
            },
            label = "lyricsSearchFormatCount",
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchResultCard(
    result: LyricsSearchResultUiModel,
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val style = result.format.style()
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded) colors.secondaryContainer else colors.surfaceContainer,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsResultContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isExpanded) colors.onSecondaryContainer else colors.onSurface,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "lyricsResultContent",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) ResultExpandedCorner else ResultCollapsedCorner,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "lyricsResultCorner",
    )
    val expansion by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "lyricsResultExpansion",
    )
    val stats = stringResource(R.string.lyrics_search_result_stats, result.lineCount, result.characterCount)
    val providerLabel =
        remember(result.providerName, result.providerUrl, result.author, result.authorUrl) {
            buildLyricsSourceLabel(
                text = result.providerName,
                providerName = result.providerName,
                providerUrl = result.providerUrl,
                author = result.author,
                authorUrl = result.authorUrl,
            )
        }

    Surface(
        onClick = onExpandedChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier =
                Modifier
                    .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LyricsSearchFormatGlyph(style = style, expansion = expansion)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = providerLabel,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(style.labelResId),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isExpanded) contentColor else style.accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = stringResource(R.string.details),
                    modifier = Modifier.graphicsLayer { rotationZ = 180f * expansion },
                )
            }
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(style.accentColor.copy(alpha = (0.6f + 0.4f * expansion).coerceIn(0f, 1f)), CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = result.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.86f),
                    maxLines = if (isExpanded) PreviewExpandedLines else PreviewCollapsedLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stats,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isExpanded) {
                    Button(
                        onClick = onApply,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        LyricsSearchApplyContent()
                    }
                } else {
                    FilledTonalButton(
                        onClick = onApply,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        LyricsSearchApplyContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSearchApplyContent() {
    Icon(
        painter = painterResource(R.drawable.check),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(stringResource(R.string.apply))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchFormatGlyph(
    style: LyricsSearchFormatStyle,
    expansion: Float,
) {
    val shape = style.polygon.toShape()

    Box(
        modifier =
            Modifier
                .size(48.dp)
                .graphicsLayer {
                    val scale = 1f + 0.08f * expansion
                    scaleX = scale
                    scaleY = scale
                    rotationZ = 45f * expansion
                }.background(style.containerColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(style.iconResId),
            contentDescription = null,
            tint = style.contentColor,
            modifier =
                Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = -45f * expansion },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchLoadingContent() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchMessageContent(
    @DrawableRes iconResId: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motionScheme = MaterialTheme.motionScheme
    val entrance = remember { Animatable(if (animationsDisabled) 1f else 0f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(1f, motionScheme.slowSpatialSpec())
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val progress = entrance.value
                        val scale = 0.6f + 0.4f * progress
                        scaleX = scale
                        scaleY = scale
                        rotationZ = -40f * (1f - progress)
                    }.background(containerColor, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(
            onClick = onAction,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(actionLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchAmbientShape(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = polygon.toShape()

    Box(
        modifier =
            modifier
                .graphicsLayer { rotationZ = 18f }
                .background(color, shape),
    )
}
