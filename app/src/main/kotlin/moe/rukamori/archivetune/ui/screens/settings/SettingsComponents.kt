/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.EditorialMorphShape

private const val RevealStaggerMillis = 45L
private const val RevealMaxStaggerSteps = 10
private const val RevealStartScale = 0.94f
private const val CoverParallaxFactor = 0.45f

@Stable
class SettingsRevealState internal constructor(
    private val animate: Boolean,
) {
    private val revealedKeys = HashSet<Any>()

    var isInitialPass: Boolean = true
        private set

    fun claim(key: Any): Boolean = animate && revealedKeys.add(key)

    fun finishInitialPass() {
        isInitialPass = false
    }
}

@Composable
fun rememberSettingsRevealState(): SettingsRevealState {
    val animationsDisabled = LocalAnimationsDisabled.current
    var hasPlayedEntrance by rememberSaveable { mutableStateOf(false) }
    val state = remember { SettingsRevealState(animate = !hasPlayedEntrance && !animationsDisabled) }
    LaunchedEffect(state) {
        withFrameNanos { }
        state.finishInitialPass()
        hasPlayedEntrance = true
    }
    return state
}

@Composable
fun Modifier.settingsReveal(
    state: SettingsRevealState,
    key: Any,
    order: Int,
): Modifier {
    val shouldAnimate = remember(state, key) { state.claim(key) }
    val progress = remember(state, key) { Animatable(if (shouldAnimate) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val offsetPx = with(LocalDensity.current) { SettingsDimensions.RevealOffset.toPx() }

    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            if (state.isInitialPass) {
                delay(order.coerceIn(0, RevealMaxStaggerSteps) * RevealStaggerMillis)
            }
            progress.animateTo(1f, spec)
        }
    }

    return graphicsLayer {
        val value = progress.value
        val scale = RevealStartScale + (1f - RevealStartScale) * value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * offsetPx
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun SettingsProfileCard(
    state: SettingsProfileState,
    revealState: SettingsRevealState,
    onClick: () -> Unit,
    coverScrollOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "profileCardScale",
    )
    val avatarOverlap = SettingsDimensions.ProfileAvatarSize / 2 + SettingsDimensions.ProfileAvatarRing

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                },
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer,
        interactionSource = interactionSource,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsProfileCover(
                    bannerUrl = state.accountBannerUrl,
                    scrollOffset = coverScrollOffset,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(SettingsDimensions.ProfileCoverHeight),
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = SettingsDimensions.HubContentInset,
                                end = SettingsDimensions.HubContentInset,
                                top = SettingsDimensions.ProfileCoverHeight - avatarOverlap,
                            ),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SettingsProfileAvatar(
                        imageUrl = state.accountImageUrl,
                        isPressed = isPressed,
                        playEntrance = remember(revealState) { revealState.claim("profile_avatar") },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SettingsProfileManagePill(isPressed = isPressed)
                }
            }

            Column(
                modifier =
                    Modifier.padding(
                        start = SettingsDimensions.HubContentInset,
                        end = SettingsDimensions.HubContentInset,
                        top = 12.dp,
                        bottom = 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.accountName,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.accountEmail.isNotBlank()) {
                    Text(
                        text = state.accountEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.accountHandle.isNotBlank()) {
                    Text(
                        text = state.accountHandle,
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsProfileLoadingRow(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsDimensions.ListItemMinHeight)
                    .padding(horizontal = SettingsDimensions.HubContentInset, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(modifier = Modifier.size(SettingsDimensions.ListItemIconSize))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsProfileCover(
    bannerUrl: String?,
    scrollOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier =
            modifier
                .clipToBounds()
                .background(
                    Brush.linearGradient(
                        listOf(colorScheme.tertiaryContainer, colorScheme.primaryContainer),
                    ),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 36.dp, y = (-44).dp)
                    .size(168.dp)
                    .graphicsLayer {
                        translationY = scrollOffset() * CoverParallaxFactor
                        rotationZ = scrollOffset() * 0.08f
                    }.background(colorScheme.primary.copy(alpha = 0.14f), MaterialShapes.Flower.toShape()),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 132.dp, y = 28.dp)
                    .size(72.dp)
                    .graphicsLayer {
                        translationY = scrollOffset() * CoverParallaxFactor * 0.5f
                        rotationZ = -scrollOffset() * 0.12f
                    }.background(colorScheme.tertiary.copy(alpha = 0.18f), MaterialShapes.SoftBurst.toShape()),
        )

        if (!bannerUrl.isNullOrBlank()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = scrollOffset() * CoverParallaxFactor },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to colorScheme.primaryContainer,
                        ),
                    ),
        )
    }
}

@Composable
private fun SettingsProfileAvatar(
    imageUrl: String?,
    isPressed: Boolean,
    playEntrance: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    var hasEntered by remember { mutableStateOf(!playEntrance) }
    LaunchedEffect(Unit) { hasEntered = true }

    val morphProgress =
        animateFloatAsState(
            targetValue = if (isPressed || !hasEntered) 1f else 0f,
            animationSpec = if (animationsDisabled) snap() else motion.defaultSpatialSpec(),
            label = "profileAvatarMorph",
        )
    val entranceScale by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0.6f,
        animationSpec = if (animationsDisabled) snap() else motion.slowSpatialSpec(),
        label = "profileAvatarScale",
    )

    Box(
        modifier =
            Modifier
                .size(SettingsDimensions.ProfileAvatarSize + SettingsDimensions.ProfileAvatarRing * 2)
                .graphicsLayer {
                    scaleX = entranceScale
                    scaleY = entranceScale
                    shape = EditorialMorphShape(morph, morphProgress.value.coerceIn(0f, 1f))
                    clip = true
                }.background(colorScheme.primaryContainer)
                .padding(SettingsDimensions.ProfileAvatarRing)
                .graphicsLayer {
                    shape = EditorialMorphShape(morph, morphProgress.value.coerceIn(0f, 1f))
                    clip = true
                }.background(colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.account),
            contentDescription = null,
            tint = colorScheme.onSecondaryContainer,
            modifier = Modifier.size(SettingsDimensions.ProfileAvatarIconSize),
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsProfileManagePill(isPressed: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val arrowShift by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "profileManageArrow",
    )

    Surface(
        shape = CircleShape,
        color = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 40.dp)
                    .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_profile_manage),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(18.dp)
                        .offset(x = arrowShift),
            )
        }
    }
}

@Composable
fun SettingsHighlightGrid(
    highlights: List<SettingsHighlight>,
    revealState: SettingsRevealState,
    revealOrderStart: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= SettingsDimensions.HighlightTileWideBreakpoint) 4 else 2

        Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.HighlightTileGap)) {
            highlights.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.HighlightTileGap),
                ) {
                    row.forEachIndexed { columnIndex, highlight ->
                        SettingsHighlightTile(
                            highlight = highlight,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .settingsReveal(
                                        state = revealState,
                                        key = highlight.item.key,
                                        order = revealOrderStart + rowIndex * columns + columnIndex,
                                    ),
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHighlightTile(
    highlight: SettingsHighlight,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val item = highlight.item
    val containerColor: Color
    val contentColor: Color
    val glyphColor: Color
    val glyphContentColor: Color
    when (highlight.tone) {
        SettingsTone.Primary -> {
            containerColor = colorScheme.primaryContainer
            contentColor = colorScheme.onPrimaryContainer
            glyphColor = colorScheme.primary
            glyphContentColor = colorScheme.onPrimary
        }

        SettingsTone.Secondary -> {
            containerColor = colorScheme.secondaryContainer
            contentColor = colorScheme.onSecondaryContainer
            glyphColor = colorScheme.secondary
            glyphContentColor = colorScheme.onSecondary
        }

        SettingsTone.Tertiary -> {
            containerColor = colorScheme.tertiaryContainer
            contentColor = colorScheme.onTertiaryContainer
            glyphColor = colorScheme.tertiary
            glyphContentColor = colorScheme.onTertiary
        }

        SettingsTone.Neutral -> {
            containerColor = colorScheme.surfaceContainerHighest
            contentColor = colorScheme.onSurface
            glyphColor = colorScheme.inverseSurface
            glyphContentColor = colorScheme.inverseOnSurface
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 40.dp else 28.dp,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "highlightTileCorner",
    )
    val glyphRotation =
        animateFloatAsState(
            targetValue = if (isPressed) 45f else 0f,
            animationSpec = if (animationsDisabled) snap() else motion.defaultSpatialSpec(),
            label = "highlightGlyphRotation",
        )
    val glyphScale =
        animateFloatAsState(
            targetValue = if (isPressed) 1.1f else 1f,
            animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
            label = "highlightGlyphScale",
        )

    Surface(
        onClick = item.onClick,
        modifier = modifier.heightIn(min = SettingsDimensions.HighlightTileMinHeight),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(SettingsDimensions.HubContentInset),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(SettingsDimensions.HighlightGlyphSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                rotationZ = glyphRotation.value
                                scaleX = glyphScale.value
                                scaleY = glyphScale.value
                            }.background(glyphColor, highlight.glyph.toShape()),
                )
                Icon(
                    painter = item.icon,
                    contentDescription = null,
                    tint = glyphContentColor,
                    modifier = Modifier.size(SettingsDimensions.HighlightGlyphIconSize),
                )
            }

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
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
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = SettingsDimensions.HubContentInset,
                    end = SettingsDimensions.HubContentInset,
                    bottom = SettingsDimensions.HubSectionHeaderBottomPadding,
                ).semantics { heading() },
    )
}

@Composable
fun SettingsHubListItem(
    item: SettingsItem,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val accent = if (item.accentColor.isSpecified) item.accentColor else colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val iconCorner by animateDpAsState(
        targetValue = if (isPressed) SettingsDimensions.ListItemIconSize / 2 else 14.dp,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "settingsItemIconCorner",
    )
    val chevronShift by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "settingsItemChevron",
    )

    SegmentedListItem(
        onClick = item.onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = colorScheme.surfaceContainer),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = SettingsDimensions.ListItemMinHeight),
        contentPadding = PaddingValues(horizontal = SettingsDimensions.HubContentInset, vertical = 12.dp),
        interactionSource = interactionSource,
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.ListItemIconSize)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(iconCorner)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = colorScheme.error)
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(SettingsDimensions.ListItemIconInnerSize),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(SettingsDimensions.ListItemIconInnerSize),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.badge?.let { badge ->
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier =
                        Modifier
                            .size(SettingsDimensions.ChevronSize)
                            .offset(x = chevronShift),
                )
            }
        },
        supportingContent =
            item.subtitle?.let { subtitle ->
                {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.showUpdateIndicator) accent else colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SettingsUpdateBanner(
    latestVersion: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.tertiaryContainer,
        contentColor = colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = SettingsDimensions.HubContentInset, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsBannerGlyph(
                iconRes = R.drawable.update,
                containerColor = colorScheme.tertiary,
                contentColor = colorScheme.onTertiary,
                glyphShape = MaterialShapes.Sunny.toShape(),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v$latestVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }

            IconButton(
                onClick = onDismiss,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.dismiss),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.secondaryContainer,
        contentColor = colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.HubContentInset, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsBannerGlyph(
                iconRes = R.drawable.security,
                containerColor = colorScheme.secondary,
                contentColor = colorScheme.onSecondary,
                glyphShape = MaterialShapes.Cookie4Sided.toShape(),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.permissions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onRequestPermission,
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.secondary,
                        contentColor = colorScheme.onSecondary,
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.allow),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

@Composable
private fun SettingsBannerGlyph(
    iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    glyphShape: Shape,
) {
    Box(
        modifier =
            Modifier
                .size(SettingsDimensions.BannerIconSize)
                .background(containerColor, glyphShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
        )
    }
}
