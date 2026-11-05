/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.component.PlaylistThumbnail
import moe.rukamori.archivetune.ui.player.EditorialMorphShape

private val CompactDialogMaxWidth = 560.dp
private val WideDialogMaxWidth = 880.dp
private val WideDialogMaxHeight = 640.dp
private val WideLayoutMinWidth = 600.dp
private val HeroBadgeSize = 56.dp
private const val HeroBadgeCountLimit = 99

@Composable
internal fun AddToPlaylistFrame(
    onDismissRequest: () -> Unit,
    listState: LazyListState,
    header: @Composable ColumnScope.(wide: Boolean) -> Unit,
    controls: @Composable ColumnScope.(wide: Boolean) -> Unit,
    footer: @Composable ColumnScope.(wide: Boolean) -> Unit,
    list: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
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
            val wide = maxWidth >= WideLayoutMinWidth && maxWidth > maxHeight
            val motion = MaterialTheme.motionScheme
            val animationsDisabled = LocalAnimationsDisabled.current
            val entrance = remember { Animatable(if (animationsDisabled) 1f else 0f) }
            val entranceAlpha = remember { Animatable(if (animationsDisabled) 1f else 0f) }

            LaunchedEffect(animationsDisabled) {
                if (animationsDisabled) {
                    entrance.snapTo(1f)
                    entranceAlpha.snapTo(1f)
                } else {
                    launch { entranceAlpha.animateTo(1f, motion.defaultEffectsSpec()) }
                    entrance.animateTo(1f, motion.slowSpatialSpec())
                }
            }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = if (wide) WideDialogMaxWidth else CompactDialogMaxWidth)
                        .heightIn(max = maxHeight)
                        .graphicsLayer {
                            val progress = entrance.value
                            val scale = 0.9f + 0.1f * progress
                            scaleX = scale
                            scaleY = scale
                            translationY = (1f - progress) * 40.dp.toPx()
                            alpha = entranceAlpha.value
                        },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (wide) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(min(maxHeight, WideDialogMaxHeight)),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .weight(0.44f)
                                    .fillMaxHeight()
                                    .padding(8.dp),
                        ) {
                            header(true)
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                controls(true)
                            }
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                footer(true)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier =
                                Modifier
                                    .weight(0.56f)
                                    .fillMaxHeight(),
                            contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            content = list,
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                            header(false)
                        }

                        LazyColumn(
                            state = listState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            item(key = "add_to_playlist_controls", contentType = "controls") {
                                Column(
                                    modifier = Modifier.padding(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    controls(false)
                                }
                            }
                            list()
                        }

                        ScrollEdgeDivider(visible = listState.canScrollForward)

                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            footer(false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollEdgeDivider(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "addToPlaylistEdgeDivider",
    )
    HorizontalDivider(
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
internal fun AddToPlaylistHeader(
    title: String,
    supporting: String,
    @DrawableRes icon: Int,
    badgeCount: Int,
    badgeActive: Boolean,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.largeIncreased,
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroBadge(count = badgeCount, icon = icon, active = badgeActive)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedContent(
                        targetState = supporting,
                        transitionSpec = {
                            (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                                (slideOutVertically(motion.defaultSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec()))
                        },
                        label = "addToPlaylistSupporting",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = { onSearchActiveChange(!searchActive) },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    AnimatedContent(
                        targetState = searchActive,
                        transitionSpec = {
                            (scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn(motion.fastEffectsSpec())) togetherWith
                                (scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) + fadeOut(motion.fastEffectsSpec()))
                        },
                        label = "addToPlaylistSearchToggle",
                    ) { active ->
                        Icon(
                            painter = painterResource(if (active) R.drawable.close else R.drawable.search),
                            contentDescription = stringResource(if (active) R.string.close else R.string.search),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = searchActive,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            PlaylistSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PlaylistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        shape = MaterialTheme.shapes.largeIncreased,
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
    )
}

@Composable
private fun HeroBadge(
    count: Int,
    @DrawableRes icon: Int,
    active: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val morphProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "heroBadgeMorph",
    )
    val rotation = remember { Animatable(0f) }
    val containerColor by animateColorAsState(
        targetValue = if (active) colors.tertiary else colors.primary,
        animationSpec = motion.defaultEffectsSpec(),
        label = "heroBadgeContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) colors.onTertiary else colors.onPrimary,
        animationSpec = motion.defaultEffectsSpec(),
        label = "heroBadgeContent",
    )

    LaunchedEffect(count, active, animationsDisabled) {
        if (!active) {
            if (animationsDisabled) rotation.snapTo(0f) else rotation.animateTo(0f, motion.defaultSpatialSpec())
        } else if (!animationsDisabled) {
            rotation.animateTo(rotation.value + 40f, motion.slowSpatialSpec())
        }
    }

    Box(
        modifier =
            Modifier
                .size(HeroBadgeSize)
                .graphicsLayer {
                    rotationZ = rotation.value
                    shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f))
                    clip = true
                }.background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer { rotationZ = -rotation.value },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val rising = targetState > initialState
                    (
                        slideInVertically(motion.defaultSpatialSpec()) { if (rising) it else -it } +
                            fadeIn(motion.defaultEffectsSpec())
                    ) togetherWith
                        (
                            slideOutVertically(motion.defaultSpatialSpec()) { if (rising) -it else it } +
                                fadeOut(motion.fastEffectsSpec())
                        )
                },
                label = "heroBadgeCount",
            ) { value ->
                if (value <= 0) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    Text(
                        text =
                            if (value > HeroBadgeCountLimit) {
                                stringResource(R.string.add_to_playlist_count_overflow)
                            } else {
                                value.toString()
                            },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddToPlaylistActionTile(
    title: String,
    supporting: String?,
    @DrawableRes icon: Int,
    badgePolygon: RoundedPolygon,
    containerColor: Color,
    contentColor: Color,
    badgeColor: Color,
    badgeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "actionTilePress",
    )
    val badgeRotation by animateFloatAsState(
        targetValue = if (pressed) 30f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "actionTileBadgeRotation",
    )
    val badgeShape = badgePolygon.toShape()

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .heightIn(min = 72.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .graphicsLayer { rotationZ = badgeRotation }
                        .clip(badgeShape)
                        .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = badgeContentColor,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = -badgeRotation },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaylistDestinationArtwork(
    playlist: Playlist,
    selected: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "playlistArtworkMorph",
    )

    Box(
        modifier =
            Modifier
                .size(ListThumbnailSize)
                .graphicsLayer {
                    shape = EditorialMorphShape(morph, progress.coerceIn(0f, 1f))
                    clip = true
                },
    ) {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            },
            shape = RectangleShape,
        )
    }
}

@Composable
internal fun PlaylistDestinationSupporting(playlist: Playlist) {
    val songCount =
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            playlist.playlist.remoteSongCount
        } else {
            playlist.songCount
        }
    val songCountText = pluralStringResource(R.plurals.n_song, songCount, songCount)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (playlist.playlist.browseId != null) {
            Icon(
                painter = painterResource(R.drawable.playlist_online),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.add_to_playlist_synced_supporting, songCountText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = songCountText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlaylistSelectionIndicator(selected: Boolean) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "selectionIndicatorFill",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outline,
        animationSpec = motion.fastEffectsSpec(),
        label = "selectionIndicatorRing",
    )

    Box(
        modifier =
            Modifier
                .size(26.dp)
                .border(2.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        val scale = fill.coerceAtLeast(0f)
                        scaleX = scale
                        scaleY = scale
                        alpha = fill.coerceIn(0f, 1f)
                    }.background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun AddToPlaylistSectionLabel(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.n_playlist, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AddToPlaylistEmptyState(
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    val emptyShape = MaterialShapes.Cookie12Sided.toShape()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(emptyShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (searching) R.drawable.search_off else R.drawable.queue_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = stringResource(if (searching) R.string.no_matching_playlists else R.string.no_playlists_yet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text =
                stringResource(
                    if (searching) R.string.add_to_playlist_no_match_hint else R.string.add_to_playlist_empty_hint,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
