/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HistorySource
import moe.rukamori.archivetune.ui.player.EditorialMorphShape

@Immutable
internal data class HistoryHeroTrack(
    val id: String,
    val title: String,
    val artists: String,
    val thumbnailUrl: String?,
    val playedLabel: String,
)

@Composable
internal fun HistoryOverview(
    hero: HistoryHeroTrack?,
    isHeroActive: Boolean,
    isPlaying: Boolean,
    currentSource: HistorySource,
    availableSources: List<HistorySource>,
    visibleSongCount: Int,
    onHeroPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSourceChange: (HistorySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroSlot: @Composable (Modifier) -> Unit = { slotModifier ->
        HistoryHeroSlot(
            hero = hero,
            isHeroActive = isHeroActive,
            isPlaying = isPlaying,
            onPlayClick = onHeroPlayClick,
            onShuffleClick = onShuffleClick,
            modifier = slotModifier,
        )
    }
    val sourcePanel: @Composable (Modifier) -> Unit = { panelModifier ->
        Column(
            modifier = panelModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (availableSources.size > 1) {
                HistorySourceSwitcher(
                    currentSource = currentSource,
                    availableSources = availableSources,
                    onSourceChange = onSourceChange,
                )
            }
            HistorySourceSummary(
                source = currentSource,
                visibleSongCount = visibleSongCount,
            )
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (maxWidth >= HistoryWideBreakpoint && hero != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                heroSlot(Modifier.weight(1.6f))
                sourcePanel(Modifier.weight(1f))
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                heroSlot(Modifier.fillMaxWidth())
                sourcePanel(Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun HistoryHeroSlot(
    hero: HistoryHeroTrack?,
    isHeroActive: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    AnimatedContent(
        targetState = hero,
        contentKey = { it?.id },
        transitionSpec = {
            if (animationsDisabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (
                    fadeIn(motion.defaultEffectsSpec()) +
                        slideInVertically(motion.defaultSpatialSpec()) { it / 6 } +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)
                ) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.98f)) using
                    SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
            }
        },
        label = "HistoryHero",
        modifier = modifier,
    ) { track ->
        if (track != null) {
            HistoryHeroCard(
                track = track,
                isActive = isHeroActive,
                isPlaying = isPlaying,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
            )
        }
    }
}

@Composable
private fun HistoryHeroCard(
    track: HistoryHeroTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val isLive = isActive && isPlaying

    val accentScale by animateFloatAsState(
        targetValue = if (isLive) 1f else 0.82f,
        animationSpec = if (animationsDisabled) snap() else motion.slowSpatialSpec(),
        label = "HistoryHeroAccentScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isActive) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
        animationSpec = if (animationsDisabled) snap() else motion.slowEffectsSpec(),
        label = "HistoryHeroContainer",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Box {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 56.dp, y = (-48).dp)
                        .size(196.dp)
                        .graphicsLayer {
                            scaleX = accentScale
                            scaleY = accentScale
                        }.background(colorScheme.primary.copy(alpha = 0.10f), MaterialShapes.SoftBurst.toShape()),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HistoryHeroArtwork(
                        thumbnailUrl = track.thumbnailUrl,
                        isActive = isActive,
                        isSpinning = isLive,
                        modifier = Modifier.size(112.dp),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(if (isLive) R.drawable.graphic_eq else R.drawable.history),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(if (isLive) R.string.history_now_playing else R.string.history_last_played),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track.artists.isNotBlank()) {
                            Text(
                                text = track.artists,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = track.playedLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val buttonHeight = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = onPlayClick,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = buttonHeight),
                    ) {
                        AnimatedContent(
                            targetState = isLive,
                            transitionSpec = {
                                if (animationsDisabled) {
                                    fadeIn(snap()) togetherWith fadeOut(snap())
                                } else {
                                    (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f))
                                }
                            },
                            label = "HistoryHeroPlayIcon",
                        ) { live ->
                            Icon(
                                painter = painterResource(if (live) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                            )
                        }
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                        Text(
                            text = stringResource(if (isLive) R.string.history_pause else R.string.play),
                            style = ButtonDefaults.textStyleFor(buttonHeight),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = onShuffleClick,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                        modifier = Modifier.heightIn(min = buttonHeight),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                        )
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                        Text(
                            text = stringResource(R.string.shuffle),
                            style = ButtonDefaults.textStyleFor(buttonHeight),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeroArtwork(
    thumbnailUrl: String?,
    isActive: Boolean,
    isSpinning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val morphProgress =
        animateFloatAsState(
            targetValue = if (isActive) 1f else 0f,
            animationSpec = if (animationsDisabled) snap() else motion.slowSpatialSpec(),
            label = "HistoryHeroArtworkMorph",
        )
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isSpinning, animationsDisabled) {
        if (isSpinning && !animationsDisabled) {
            while (true) {
                rotation.snapTo(rotation.value % 360f)
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = HistoryHeroSpinMillis, easing = LinearEasing),
                )
            }
        } else {
            val settled = rotation.value % 360f
            rotation.snapTo(settled)
            if (animationsDisabled) {
                rotation.snapTo(0f)
            } else {
                rotation.animateTo(
                    targetValue = if (settled > 180f) 360f else 0f,
                    animationSpec = motion.slowSpatialSpec(),
                )
                rotation.snapTo(0f)
            }
        }
    }

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    rotationZ = rotation.value
                    shape = EditorialMorphShape(morph, morphProgress.value.coerceIn(0f, 1f))
                    clip = true
                }.background(colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = colorScheme.onTertiaryContainer,
            modifier = Modifier.size(40.dp),
        )
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HistorySourceSwitcher(
    currentSource: HistorySource,
    availableSources: List<HistorySource>,
    onSourceChange: (HistorySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth().selectableGroup(),
    ) {
        availableSources.forEachIndexed { index, source ->
            val checked = source == currentSource
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        onSourceChange(source)
                    }
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = buttonHeight)
                        .semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        availableSources.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            ) {
                Icon(
                    painter = painterResource(source.iconRes()),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                Text(
                    text = stringResource(source.labelRes()),
                    style = if (checked) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HistorySourceSummary(
    source: HistorySource,
    visibleSongCount: Int,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                stringResource(
                    if (source == HistorySource.LOCAL) R.string.history_local_summary else R.string.history_remote_summary,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AnimatedContent(
            targetState = visibleSongCount,
            transitionSpec = {
                if (animationsDisabled) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInVertically(motion.fastSpatialSpec()) { direction * it } + fadeIn(motion.fastEffectsSpec())) togetherWith
                        (slideOutVertically(motion.fastSpatialSpec()) { -direction * it } + fadeOut(motion.fastEffectsSpec())) using
                        SizeTransform(clip = true)
                }
            },
            label = "HistoryVisibleCount",
        ) { count ->
            Text(
                text = pluralStringResource(R.plurals.n_song, count, count),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun HistoryChapterHeader(
    title: String,
    songCount: Int,
    artistsLine: String,
    chapterIndex: Int,
    containsActive: Boolean,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songCountLabel = pluralStringResource(R.plurals.n_song, songCount, songCount)
    val playAllDescription = stringResource(R.string.history_play_chapter, title)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 12.dp, bottom = 10.dp),
        ) {
            HistoryChapterGlyph(
                songCount = songCount,
                chapterIndex = chapterIndex,
                containsActive = containsActive,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            heading()
                            contentDescription = "$title, $songCountLabel"
                        },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artistsLine.ifBlank { songCountLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = onPlayAll,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = playAllDescription,
                )
            }
        }
    }
}

@Composable
private fun HistoryChapterGlyph(
    songCount: Int,
    chapterIndex: Int,
    containsActive: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph =
        remember(chapterIndex) {
            Morph(HistoryChapterShapes[chapterIndex % HistoryChapterShapes.size], MaterialShapes.Circle)
        }
    val morphProgress =
        animateFloatAsState(
            targetValue = if (containsActive) 1f else 0f,
            animationSpec = if (animationsDisabled) snap() else motion.defaultSpatialSpec(),
            label = "HistoryChapterGlyphMorph",
        )
    val (containerColor, contentColor) =
        when {
            containsActive -> colorScheme.primary to colorScheme.onPrimary
            chapterIndex == 0 -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
            chapterIndex % 2 == 1 -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
            else -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        }
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = if (animationsDisabled) snap() else motion.defaultEffectsSpec(),
        label = "HistoryChapterGlyphColor",
    )

    Box(
        modifier =
            Modifier
                .size(48.dp)
                .graphicsLayer {
                    shape = EditorialMorphShape(morph, morphProgress.value.coerceIn(0f, 1f))
                    clip = true
                }.background(animatedContainer)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (songCount > HistoryGlyphCountCap) "$HistoryGlyphCountCap+" else songCount.toString(),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
internal fun HistoryTrackSurface(
    index: Int,
    lastIndex: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    content: @Composable (containerColor: Color) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val isEmphasized = isSelected || isActive

    val topCorner by animateDpAsState(
        targetValue = if (index == 0 || isEmphasized) HistoryOuterCorner else HistoryInnerCorner,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "HistoryTrackTopCorner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (index == lastIndex || isEmphasized) HistoryOuterCorner else HistoryInnerCorner,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "HistoryTrackBottomCorner",
    )
    val containerColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> colorScheme.primaryContainer
                isActive -> colorScheme.secondaryContainer
                else -> colorScheme.surfaceContainerLow
            },
        animationSpec = if (animationsDisabled) snap() else motion.defaultEffectsSpec(),
        label = "HistoryTrackContainer",
    )
    val top = topCorner.coerceAtLeast(0.dp)
    val bottom = bottomCorner.coerceAtLeast(0.dp)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 3.dp),
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = containerColor,
    ) {
        content(containerColor)
    }
}

@Composable
internal fun HistoryStatePanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    loading: Boolean = false,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val entrance = remember(title) { Animatable(if (animationsDisabled) 1f else 0f) }

    LaunchedEffect(title, animationsDisabled) {
        if (animationsDisabled) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(1f, motion.slowSpatialSpec())
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
        ) {
            if (loading) {
                ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
            } else if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(112.dp)
                            .graphicsLayer {
                                val progress = entrance.value
                                scaleX = 0.6f + 0.4f * progress
                                scaleY = 0.6f + 0.4f * progress
                                rotationZ = -40f * (1f - progress)
                            }.clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )

            if (actionLabel != null && onActionClick != null) {
                Button(
                    onClick = onActionClick,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.replay),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.HistorySelectionToolbar(
    visible: Boolean,
    allVisibleSelected: Boolean,
    onToggleAll: () -> Unit,
    onPlaySelection: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    AnimatedVisibility(
        visible = visible,
        enter =
            if (animationsDisabled) {
                fadeIn(snap())
            } else {
                fadeIn(motion.defaultEffectsSpec()) +
                    slideInVertically(motion.defaultSpatialSpec()) { it } +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f)
            },
        exit =
            if (animationsDisabled) {
                fadeOut(snap())
            } else {
                fadeOut(motion.fastEffectsSpec()) +
                    slideOutVertically(motion.fastSpatialSpec()) { it } +
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f)
            },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).padding(16.dp),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = onMoreClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            },
            colors =
                FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            HistoryToolbarAction(
                icon = if (allVisibleSelected) R.drawable.deselect else R.drawable.select_all,
                label = stringResource(if (allVisibleSelected) R.string.clear_selection else R.string.select),
                onClick = onToggleAll,
            )
            IconButton(
                onClick = onPlaySelection,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                )
            }
        }
    }
}

@Composable
private fun HistoryToolbarAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun HistorySource.labelRes(): Int =
    when (this) {
        HistorySource.LOCAL -> R.string.local_history
        HistorySource.REMOTE -> R.string.remote_history
    }

private fun HistorySource.iconRes(): Int =
    when (this) {
        HistorySource.LOCAL -> R.drawable.library_music
        HistorySource.REMOTE -> R.drawable.sync
    }

private val HistoryChapterShapes =
    listOf(
        MaterialShapes.Sunny,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.SoftBurst,
        MaterialShapes.Flower,
    )

private val HistoryWideBreakpoint = 600.dp
private val HistoryOuterCorner = 24.dp
private val HistoryInnerCorner = 6.dp
private const val HistoryGlyphCountCap = 99
private const val HistoryHeroSpinMillis = 14_000
