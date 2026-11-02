/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.extractThemeColor
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize

@Immutable
data class ImmersiveDetailColors(
    val backdrop: Color,
    val content: Color,
    val contentVariant: Color,
    val accent: Color,
    val onAccent: Color,
)

@Composable
fun rememberImmersiveDetailColors(artworkUrl: String?): ImmersiveDetailColors {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var artworkSeed by remember(artworkUrl) { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) return@LaunchedEffect
        val result =
            context.imageLoader.execute(
                ImageRequest
                    .Builder(context)
                    .data(artworkUrl)
                    .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                    .allowHardware(false)
                    .build(),
            )
        val image = (result as? SuccessResult)?.image ?: return@LaunchedEffect
        artworkSeed = withContext(Dispatchers.Default) { image.toBitmap().extractThemeColor() }
    }

    val target =
        remember(artworkSeed, colorScheme.primary, colorScheme.surface, colorScheme.onSurface, colorScheme.onSurfaceVariant) {
            immersiveDetailColorsFor(
                seed = artworkSeed ?: colorScheme.primary,
                surface = colorScheme.surface,
                onSurface = colorScheme.onSurface,
                onSurfaceVariant = colorScheme.onSurfaceVariant,
            )
        }
    val animationSpec = tween<Color>(ImmersiveColorAnimationMillis)
    val backdrop by animateColorAsState(target.backdrop, animationSpec, label = "immersiveBackdrop")
    val accent by animateColorAsState(target.accent, animationSpec, label = "immersiveAccent")
    val onAccent by animateColorAsState(target.onAccent, animationSpec, label = "immersiveOnAccent")

    return ImmersiveDetailColors(
        backdrop = backdrop,
        content = target.content,
        contentVariant = target.contentVariant,
        accent = accent,
        onAccent = onAccent,
    )
}

private fun immersiveDetailColorsFor(
    seed: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
): ImmersiveDetailColors {
    val isDark = surface.luminance() < 0.5f
    val backdrop =
        if (isDark) {
            lerp(lerp(surface, seed, if (surface == Color.Black) 0.14f else 0.28f), Color.Black, 0.16f)
        } else {
            lerp(surface, seed, 0.12f)
        }
    val contrastTarget = if (isDark) Color.White else Color.Black
    var accent = lerp(seed, contrastTarget, 0.28f)
    var step = 0
    while (contrastRatio(accent, backdrop) < ImmersiveMinAccentContrast && step < 6) {
        accent = lerp(accent, contrastTarget, 0.25f)
        step++
    }
    val onAccent = if (accent.luminance() > 0.42f) Color.Black else Color.White

    return ImmersiveDetailColors(
        backdrop = backdrop,
        content = onSurface,
        contentVariant = onSurfaceVariant,
        accent = accent,
        onAccent = onAccent,
    )
}

private fun contrastRatio(
    first: Color,
    second: Color,
): Float {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun immersiveTopAppBarColors(
    colors: ImmersiveDetailColors,
    overArtwork: Boolean,
): TopAppBarColors =
    if (overArtwork) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = colors.backdrop,
            scrolledContainerColor = colors.backdrop,
            navigationIconContentColor = colors.content,
            titleContentColor = colors.content,
            actionIconContentColor = colors.content,
        )
    }

@Composable
fun ImmersiveArtwork(
    thumbnailUrl: String?,
    @DrawableRes fallbackIcon: Int,
    colors: ImmersiveDetailColors,
    modifier: Modifier = Modifier,
    fadeStart: Float = ImmersiveArtworkFadeStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model =
                    thumbnailUrl.resize(
                        width = ImmersiveArtworkSizePx,
                        height = ImmersiveArtworkSizePx,
                        sizeBuckets = ImmersiveArtworkSizeBuckets,
                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                    ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(colors.content.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = colors.contentVariant,
                    modifier = Modifier.size(96.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.38f),
                            0.22f to Color.Transparent,
                            fadeStart to Color.Transparent,
                            (fadeStart + 1f) / 2f to colors.backdrop.copy(alpha = 0.72f),
                            1f to colors.backdrop,
                        ),
                    ),
        )

        content()
    }
}

@Composable
fun ImmersiveMediaHeader(
    title: String,
    thumbnailUrl: String?,
    @DrawableRes fallbackIcon: Int,
    colors: ImmersiveDetailColors,
    isAdded: Boolean,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    metadata: String? = null,
    description: String? = null,
    additionalPrimaryActions: (@Composable RowScope.(Color) -> Unit)? = null,
    isToggleAddEnabled: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val confirmedToggleAdd =
        remember(isAdded, menuState, onToggleAdd, removeContentDescription, title) {
            onToggleAdd?.let { toggleAdd ->
                if (isAdded) {
                    {
                        menuState.showDialog {
                            MediaDetailRemovalConfirmationDialog(
                                title = title,
                                removeContentDescription = removeContentDescription,
                                onDismiss = menuState::dismissDialog,
                                onConfirm = {
                                    menuState.dismissDialog()
                                    toggleAdd()
                                },
                            )
                        }
                    }
                } else {
                    toggleAdd
                }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.backdrop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            ImmersiveArtwork(
                thumbnailUrl = thumbnailUrl,
                fallbackIcon = fallbackIcon,
                colors = colors,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(minOf(maxWidth, ImmersiveArtworkMaxHeight)),
            ) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .widthIn(max = ImmersiveContentMaxWidth)
                            .padding(horizontal = ImmersiveHorizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.content,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    subtitle?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    metadata?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.contentVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        ImmersivePrimaryActions(
            colors = colors,
            isAdded = isAdded,
            addContentDescription = addContentDescription,
            removeContentDescription = removeContentDescription,
            onShuffle = onShuffle,
            onPlay = onPlay,
            onToggleAdd = confirmedToggleAdd,
            isToggleAddEnabled = isToggleAddEnabled,
            modifier = Modifier.padding(top = 20.dp),
        )

        additionalPrimaryActions?.let { actions ->
            ImmersiveSecondaryActions(
                colors = colors,
                actions = actions,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        description?.takeIf(String::isNotBlank)?.let {
            ImmersiveDescription(
                text = it,
                colors = colors,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ImmersivePrimaryActions(
    colors: ImmersiveDetailColors,
    isAdded: Boolean,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isToggleAddEnabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = ImmersiveContentMaxWidth)
                .padding(horizontal = ImmersiveHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(ImmersiveActionSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onShuffle != null) {
            ImmersiveCircleAction(
                icon = R.drawable.shuffle,
                contentDescription = R.string.shuffle,
                colors = colors,
                onClick = onShuffle,
            )
        } else if (onToggleAdd != null) {
            Spacer(modifier = Modifier.size(ImmersiveCircleActionSize))
        }

        onPlay?.let { play ->
            ImmersivePlayButton(
                colors = colors,
                onClick = play,
                modifier = Modifier.widthIn(min = ImmersivePlayButtonMinWidth),
            )
        }

        if (onToggleAdd != null) {
            ImmersiveCircleAction(
                icon = if (isAdded) R.drawable.done else R.drawable.add,
                contentDescription = if (isAdded) removeContentDescription else addContentDescription,
                colors = colors,
                onClick = onToggleAdd,
                enabled = isToggleAddEnabled,
                selected = isAdded,
            )
        } else if (onShuffle != null) {
            Spacer(modifier = Modifier.size(ImmersiveCircleActionSize))
        }
    }
}

@Composable
fun ImmersivePlayButton(
    colors: ImmersiveDetailColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes label: Int = R.string.play,
    @DrawableRes icon: Int = R.drawable.play,
) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
        modifier = modifier.heightIn(min = buttonHeight),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
        Text(
            text = stringResource(label),
            style = ButtonDefaults.textStyleFor(buttonHeight),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun ImmersiveCircleAction(
    @DrawableRes icon: Int,
    @StringRes contentDescription: Int,
    colors: ImmersiveDetailColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (selected) colors.accent.copy(alpha = 0.22f) else colors.content.copy(alpha = 0.1f),
                contentColor = if (selected) colors.accent else colors.content,
                disabledContainerColor = colors.content.copy(alpha = 0.05f),
                disabledContentColor = colors.content.copy(alpha = 0.38f),
            ),
        modifier = modifier.size(ImmersiveCircleActionSize),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(contentDescription),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun ImmersiveSecondaryActions(
    colors: ImmersiveDetailColors,
    actions: @Composable RowScope.(Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ImmersiveHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions(colors.content)
        }
    }
}

@Composable
fun ImmersiveDescription(
    text: String,
    colors: ImmersiveDetailColors,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    var isExpanded by rememberSaveable(text) { mutableStateOf(false) }
    var isOverflowing by remember(text) { mutableStateOf(false) }
    val toggleLabel = stringResource(if (isExpanded) R.string.less else R.string.more)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = ImmersiveContentMaxWidth)
                .clickable(
                    enabled = isOverflowing || isExpanded,
                    onClickLabel = toggleLabel,
                    role = Role.Button,
                ) { isExpanded = !isExpanded }
                .padding(horizontal = ImmersiveHorizontalPadding, vertical = 4.dp)
                .animateContentSize(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentVariant,
            textAlign = textAlign,
            maxLines = if (isExpanded) Int.MAX_VALUE else ImmersiveDescriptionCollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (!isExpanded) isOverflowing = layout.hasVisualOverflow
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (isOverflowing || isExpanded) {
            Text(
                text = toggleLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun ImmersiveSectionHeader(
    title: String,
    colors: ImmersiveDetailColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
                .padding(horizontal = ImmersiveSectionHorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.content,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = colors.contentVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun ImmersiveDetailFooter(
    songCount: Int,
    totalDurationSeconds: Long?,
    colors: ImmersiveDetailColors,
    modifier: Modifier = Modifier,
    leadingLine: String? = null,
) {
    val songsText = pluralStringResource(R.plurals.n_song, songCount, songCount)
    val durationText =
        totalDurationSeconds
            ?.takeIf { it >= 60 }
            ?.let { seconds ->
                val totalMinutes = (seconds / 60).toInt()
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (hours > 0) {
                    stringResource(R.string.media_detail_duration_hours, hours, minutes)
                } else {
                    pluralStringResource(R.plurals.n_minute, minutes, minutes)
                }
            }
    val summary =
        if (durationText == null) {
            songsText
        } else {
            stringResource(R.string.media_detail_summary, songsText, durationText)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = ImmersiveSectionHorizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        leadingLine?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentVariant,
            )
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.contentVariant,
        )
    }
}

private const val ImmersiveColorAnimationMillis = 450
private const val ImmersiveMinAccentContrast = 3.2f
private const val ImmersiveArtworkSizePx = 1200
private const val ImmersiveArtworkFadeStart = 0.46f
private const val ImmersiveDescriptionCollapsedLines = 3
private val ImmersiveArtworkSizeBuckets = listOf(ImmersiveArtworkSizePx)
private val ImmersiveArtworkMaxHeight = 560.dp
private val ImmersiveContentMaxWidth = 720.dp
private val ImmersiveHorizontalPadding = 24.dp
private val ImmersiveSectionHorizontalPadding = 20.dp
private val ImmersiveActionSpacing = 20.dp
private val ImmersiveCircleActionSize = 48.dp
private val ImmersivePlayButtonMinWidth = 148.dp
