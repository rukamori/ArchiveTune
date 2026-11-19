/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.BackAwareModalBottomSheet
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AppIconSortOrder
import moe.rukamori.archivetune.viewmodels.AppIconUiModel
import moe.rukamori.archivetune.viewmodels.IconScreenEffect
import moe.rukamori.archivetune.viewmodels.IconScreenState
import moe.rukamori.archivetune.viewmodels.IconScreenUiModel
import moe.rukamori.archivetune.viewmodels.IconViewModel
import androidx.compose.material3.IconButton as MaterialIconButton

@Composable
fun IconScreen(
    navController: NavController,
    viewModel: IconViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uriHandler = LocalUriHandler.current
    val selectedTaskIconResId =
        (state as? IconScreenState.Success)
            ?.model
            ?.selectedIcon
            ?.previewDrawableResId

    LaunchedEffect(activity, selectedTaskIconResId) {
        if (activity != null && selectedTaskIconResId != null) {
            activity.updateTaskIcon(selectedTaskIconResId)
        }
    }
    LaunchedEffect(viewModel, uriHandler) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is IconScreenEffect.OpenUri -> uriHandler.openUri(effect.uri)
            }
        }
    }
    val navigateUp: () -> Unit =
        remember(navController) {
            {
                navController.navigateUp()
                Unit
            }
        }
    val navigateHome: () -> Unit =
        remember(navController) {
            {
                navController.backToMain()
            }
        }
    val retry = remember(viewModel) { { viewModel.retry() } }
    val selectIcon =
        remember(viewModel) {
            { iconId: String -> viewModel.selectIcon(iconId) }
        }
    val openAuthorProfile =
        remember(viewModel) {
            { iconId: String -> viewModel.openAuthorProfile(iconId) }
        }
    val openSource =
        remember(viewModel) {
            { iconId: String -> viewModel.openSource(iconId) }
        }
    val updateSearchQuery =
        remember(viewModel) {
            { query: String -> viewModel.updateSearchQuery(query) }
        }
    val updateSortOrder =
        remember(viewModel) {
            { order: AppIconSortOrder -> viewModel.updateSortOrder(order) }
        }

    IconScreenContent(
        state = state,
        onNavigateUp = navigateUp,
        onNavigateHome = navigateHome,
        onRetry = retry,
        onSelectIcon = selectIcon,
        onOpenAuthorProfile = openAuthorProfile,
        onOpenSource = openSource,
        onSearchQueryChange = updateSearchQuery,
        onSortOrderChange = updateSortOrder,
    )
}

@Composable
private fun IconScreenContent(
    state: IconScreenState,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onRetry: () -> Unit,
    onSelectIcon: (String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (AppIconSortOrder) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_icon))
                },
                subtitle = {
                    Text(text = stringResource(R.string.app_icon_subtitle))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        onLongClick = onNavigateHome,
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
        AnimatedContent(
            targetState = state,
            contentKey = { screenState -> screenState::class },
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            modifier = Modifier.fillMaxSize(),
            label = "iconScreenState",
        ) { screenState ->
            val messageModifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .playerAwareInsets()
            when (screenState) {
                IconScreenState.Loading -> {
                    IconScreenLoading(modifier = messageModifier)
                }

                IconScreenState.Empty -> {
                    IconScreenMessage(
                        polygon = MaterialShapes.Flower,
                        iconResId = R.drawable.palette,
                        message = stringResource(R.string.app_icon_empty),
                        isError = false,
                        modifier = messageModifier,
                    )
                }

                is IconScreenState.Error -> {
                    IconScreenMessage(
                        polygon = MaterialShapes.Cookie9Sided,
                        iconResId = R.drawable.error,
                        message = stringResource(screenState.messageResId),
                        isError = true,
                        modifier = messageModifier,
                        action = onRetry,
                    )
                }

                is IconScreenState.Success -> {
                    IconGallery(
                        model = screenState.model,
                        contentPadding =
                            PaddingValues(
                                start = SettingsDimensions.ScreenHorizontalPadding,
                                top = innerPadding.calculateTopPadding() + 8.dp,
                                end = SettingsDimensions.ScreenHorizontalPadding,
                                bottom = SettingsDimensions.ScreenBottomPadding,
                            ),
                        onSelectIcon = onSelectIcon,
                        onOpenAuthorProfile = onOpenAuthorProfile,
                        onOpenSource = onOpenSource,
                        onSearchQueryChange = onSearchQueryChange,
                        onSortOrderChange = onSortOrderChange,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .playerAwareInsets(),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconGallery(
    model: IconScreenUiModel,
    contentPadding: PaddingValues,
    onSelectIcon: (String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (AppIconSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    var detailsIconId by rememberSaveable { mutableStateOf<String?>(null) }
    val showDetails = remember { { iconId: String -> detailsIconId = iconId } }
    val dismissDetails = remember { { detailsIconId = null } }
    val selectionInProgressId = model.selectionInProgressId
    val isSelectionLocked = selectionInProgressId != null
    val applyingIcon = selectionInProgressId?.let(model.icons::findById)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TileMinWidth),
            modifier =
                Modifier
                    .widthIn(max = GalleryMaxWidth)
                    .fillMaxSize()
                    .selectableGroup(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            item(
                key = StageContentKey,
                span = FullLineSpan,
                contentType = StageContentKey,
            ) {
                IconStage(
                    icon = model.selectedIcon,
                    applyingIcon = applyingIcon,
                    onOpenAuthorProfile = onOpenAuthorProfile,
                    onOpenSource = onOpenSource,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(
                key = ControlsContentKey,
                span = FullLineSpan,
                contentType = ControlsContentKey,
            ) {
                GalleryControls(
                    query = model.searchQuery,
                    sortOrder = model.sortOrder,
                    onQueryChange = onSearchQueryChange,
                    onSortOrderChange = onSortOrderChange,
                )
            }
            item(
                key = HeaderContentKey,
                span = FullLineSpan,
                contentType = HeaderContentKey,
            ) {
                GalleryHeader(
                    iconCount = model.icons.size,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp),
                )
            }
            items(
                count = model.icons.size,
                key = { index -> model.icons[index].id },
                contentType = { TileContentType },
            ) { index ->
                val icon = model.icons[index]
                IconTile(
                    icon = icon,
                    enabled = !isSelectionLocked,
                    isApplying = selectionInProgressId == icon.id,
                    onSelect = onSelectIcon,
                    onShowDetails = showDetails,
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                        ),
                )
            }
            if (model.hasNoSearchMatches) {
                item(
                    key = NoMatchesContentKey,
                    span = FullLineSpan,
                    contentType = NoMatchesContentKey,
                ) {
                    NoSearchMatches(
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                            ),
                    )
                }
            }
            if (model.hasCommunityIcons) {
                item(
                    key = CommunityFooterContentKey,
                    span = FullLineSpan,
                    contentType = CommunityFooterContentKey,
                ) {
                    CommunityFooter(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    val detailsIcon = detailsIconId?.let(model.icons::findById)
    LaunchedEffect(detailsIconId, detailsIcon == null) {
        if (detailsIconId != null && detailsIcon == null) {
            detailsIconId = null
        }
    }
    if (detailsIcon != null) {
        IconDetailsSheet(
            icon = detailsIcon,
            enabled = !isSelectionLocked,
            isApplying = selectionInProgressId == detailsIcon.id,
            onSelect = onSelectIcon,
            onOpenAuthorProfile = onOpenAuthorProfile,
            onOpenSource = onOpenSource,
            onDismiss = dismissDetails,
        )
    }
}

@Composable
private fun IconStage(
    icon: AppIconUiModel,
    applyingIcon: AppIconUiModel?,
    onOpenAuthorProfile: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box {
            StageOrnaments(modifier = Modifier.matchParentSize())
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StageArtwork(
                    icon = icon,
                    isApplying = applyingIcon != null,
                )
                Spacer(modifier = Modifier.size(20.dp))
                StageStatus(applyingIcon = applyingIcon)
                Spacer(modifier = Modifier.size(14.dp))
                AnimatedContent(
                    targetState = icon,
                    contentKey = AppIconUiModel::id,
                    transitionSpec = {
                        (
                            fadeIn(motion.defaultEffectsSpec()) +
                                slideInVertically(motion.defaultSpatialSpec()) { height -> height / 4 }
                        ) togetherWith
                            (
                                fadeOut(motion.fastEffectsSpec()) +
                                    slideOutVertically(motion.fastSpatialSpec()) { height -> -height / 4 }
                            ) using SizeTransform(clip = false)
                    },
                    label = "stageDetails",
                ) { currentIcon ->
                    IconIdentity(
                        icon = currentIcon,
                        onOpenAuthorProfile = onOpenAuthorProfile,
                        onOpenSource = onOpenSource,
                        largeTitle = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun StageOrnaments(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-28).dp, y = (-24).dp)
                    .size(112.dp)
                    .graphicsLayer { rotationZ = -18f }
                    .background(
                        color = colors.primaryContainer.copy(alpha = 0.5f),
                        shape = MaterialShapes.Clover4Leaf.toShape(),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 36.dp, y = 32.dp)
                    .size(148.dp)
                    .background(
                        color = colors.tertiaryContainer.copy(alpha = 0.45f),
                        shape = MaterialShapes.Flower.toShape(),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 32.dp)
                    .size(22.dp)
                    .background(
                        color = colors.secondary.copy(alpha = 0.3f),
                        shape = MaterialShapes.Diamond.toShape(),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 36.dp, bottom = 40.dp)
                    .size(14.dp)
                    .background(
                        color = colors.tertiary.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
        )
    }
}

@Composable
private fun StageArtwork(
    icon: AppIconUiModel,
    isApplying: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val reduceMotion = motion.defaultEffectsSpec<Float>() is SnapSpec<*>
    val haloRotation: State<Float> =
        if (reduceMotion) {
            remember { mutableFloatStateOf(0f) }
        } else {
            rememberInfiniteTransition(label = "stageHalo").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(HaloRotationMillis, easing = LinearEasing)),
                label = "stageHaloRotation",
            )
        }
    val haloScale =
        animateFloatAsState(
            targetValue = if (isApplying) 1.08f else 1f,
            animationSpec = motion.defaultSpatialSpec(),
            label = "stageHaloScale",
        )

    Box(
        modifier = Modifier.size(StageArtworkSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        rotationZ = haloRotation.value
                        scaleX = haloScale.value
                        scaleY = haloScale.value
                    }.background(
                        color = colors.primaryContainer,
                        shape = MaterialShapes.Cookie12Sided.toShape(),
                    ),
        )
        AnimatedContent(
            targetState = icon,
            contentKey = AppIconUiModel::id,
            transitionSpec = {
                (
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f) +
                        fadeIn(motion.defaultEffectsSpec())
                ) togetherWith
                    (
                        scaleOut(motion.fastSpatialSpec(), targetScale = 1.15f) +
                            fadeOut(motion.fastEffectsSpec())
                    )
            },
            label = "stageArtwork",
        ) { currentIcon ->
            IconArtwork(
                icon = currentIcon,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.size(StageIconSize),
                elevated = true,
            )
        }
    }
}

@Composable
private fun StageStatus(applyingIcon: AppIconUiModel?) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val applyingName = applyingIcon?.let { icon -> appIconName(icon) }

    AnimatedContent(
        targetState = applyingName,
        transitionSpec = {
            (scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f) + fadeIn(motion.fastEffectsSpec())) togetherWith
                (scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f) + fadeOut(motion.fastEffectsSpec())) using
                SizeTransform(clip = false)
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        label = "stageStatus",
    ) { name ->
        val isApplying = name != null
        Surface(
            shape = CircleShape,
            color = if (isApplying) colors.tertiaryContainer else colors.primary,
            contentColor = if (isApplying) colors.onTertiaryContainer else colors.onPrimary,
        ) {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (name != null) {
                    LoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.onTertiaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.app_icon_applying, name),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_icon_current),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconIdentity(
    icon: AppIconUiModel,
    onOpenAuthorProfile: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    largeTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val author = icon.author

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = appIconName(icon),
            modifier = Modifier.semantics { heading() },
            style =
                if (largeTitle) {
                    MaterialTheme.typography.headlineMediumEmphasized
                } else {
                    MaterialTheme.typography.headlineSmallEmphasized
                },
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                if (icon.isDefault || author == null) {
                    stringResource(R.string.app_icon_builtin_description)
                } else {
                    stringResource(R.string.app_icon_by_author, author)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTypeBadge(isDefault = icon.isDefault)
            if (!icon.isDefault) {
                IdentifierBadge(iconId = icon.id)
            }
        }
        IconLinkActions(
            icon = icon,
            onOpenAuthorProfile = onOpenAuthorProfile,
            onOpenSource = onOpenSource,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun IconLinkActions(
    icon: AppIconUiModel,
    onOpenAuthorProfile: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasProfile = icon.githubAuthorUrl != null
    val hasSource = icon.sourceUrl != null
    if (!hasProfile && !hasSource) return

    val openProfile = remember(icon.id, onOpenAuthorProfile) { { onOpenAuthorProfile(icon.id) } }
    val openSource = remember(icon.id, onOpenSource) { { onOpenSource(icon.id) } }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasProfile) {
            FilledTonalButton(
                onClick = openProfile,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.github),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.app_icon_github_profile))
            }
        }
        if (hasSource) {
            OutlinedButton(
                onClick = openSource,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.link),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.source))
            }
        }
    }
}

@Composable
private fun IconTypeBadge(
    isDefault: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (isDefault) colors.secondaryContainer else colors.tertiaryContainer,
        contentColor = if (isDefault) colors.onSecondaryContainer else colors.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(if (isDefault) R.drawable.auto_awesome else R.drawable.person),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(if (isDefault) R.string.app_icon_builtin else R.string.app_icon_community),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun IdentifierBadge(
    iconId: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.app_icon_identifier, iconId),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun GalleryControls(
    query: String,
    sortOrder: AppIconSortOrder,
    onQueryChange: (String) -> Unit,
    onSortOrderChange: (AppIconSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIconSearchField(
            query = query,
            onQueryChange = onQueryChange,
        )
        SortToggleGroup(
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
        )
    }
}

@Composable
private fun AppIconSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitSearch =
        remember(keyboardController) {
            { _: String ->
                keyboardController?.hide()
                Unit
            }
        }
    val clearSearch = remember(onQueryChange) { { onQueryChange("") } }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = submitSearch,
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(text = stringResource(R.string.search))
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                    ) {
                        MaterialIconButton(onClick = clearSearch) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.clear),
                            )
                        }
                    }
                },
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier.fillMaxWidth(),
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) {}
}

@Composable
private fun SortToggleGroup(
    sortOrder: AppIconSortOrder,
    onSortOrderChange: (AppIconSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val orders = AppIconSortOrder.entries

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        orders.forEachIndexed { index, order ->
            val checked = sortOrder == order
            val select =
                remember(order, onSortOrderChange) {
                    { _: Boolean -> onSortOrderChange(order) }
                }
            ToggleButton(
                checked = checked,
                onCheckedChange = select,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        orders.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.onSurfaceVariant,
                        checkedContainerColor = colors.secondary,
                        checkedContentColor = colors.onSecondary,
                    ),
            ) {
                Icon(
                    painter = painterResource(order.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(order.labelResId),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GalleryHeader(
    iconCount: Int,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_icon_available),
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            AnimatedContent(
                targetState = iconCount,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (
                        slideInVertically(motion.fastSpatialSpec()) { height -> height * direction } +
                            fadeIn(motion.fastEffectsSpec())
                    ) togetherWith
                        (
                            slideOutVertically(motion.fastSpatialSpec()) { height -> -height * direction } +
                                fadeOut(motion.fastEffectsSpec())
                        ) using SizeTransform(clip = true)
                },
                label = "galleryCount",
            ) { count ->
                Text(
                    text = pluralStringResource(R.plurals.app_icon_count, count, count),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

@Composable
private fun IconTile(
    icon: AppIconUiModel,
    enabled: Boolean,
    isApplying: Boolean,
    onSelect: (String) -> Unit,
    onShowDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale =
        animateFloatAsState(
            targetValue = if (isPressed) 0.94f else 1f,
            animationSpec = motion.fastSpatialSpec(),
            label = "tilePressScale",
        )
    val dimAlpha =
        animateFloatAsState(
            targetValue = if (enabled || isApplying) 1f else 0.6f,
            animationSpec = motion.defaultEffectsSpec(),
            label = "tileDimAlpha",
        )
    val selectionProgress by animateFloatAsState(
        targetValue = if (icon.isSelected) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "tileSelection",
    )
    val containerColor by animateColorAsState(
        targetValue = if (icon.isSelected) colors.secondaryContainer else colors.surfaceContainerLow,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContainer",
    )
    val select = remember(icon.id, onSelect) { { onSelect(icon.id) } }
    val showDetails = remember(icon.id, onShowDetails) { { onShowDetails(icon.id) } }
    val author = icon.author

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                    alpha = dimAlpha.value
                }.clip(MaterialTheme.shapes.large)
                .background(containerColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.RadioButton,
                    onLongClickLabel = stringResource(R.string.app_icon_details),
                    onLongClick = showDetails,
                    onClick = select,
                ).semantics { selected = icon.isSelected }
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TileArtwork(
            icon = icon,
            selectionProgress = selectionProgress,
            isApplying = isApplying,
        )
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = appIconName(icon),
                style =
                    if (icon.isSelected) {
                        MaterialTheme.typography.titleSmallEmphasized
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                color = if (icon.isSelected) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (icon.isDefault || author == null) {
                        stringResource(R.string.app_icon_builtin)
                    } else {
                        author
                    },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TileArtwork(
    icon: AppIconUiModel,
    selectionProgress: Float,
    isApplying: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val progress = selectionProgress.coerceIn(0f, 1f)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = selectionProgress * TileBackdropRotation }
                    .background(
                        color = lerp(colors.surfaceContainerHighest, colors.primary, progress),
                        shape = EditorialMorphShape(TileMorph, progress),
                    ),
        )
        IconArtwork(
            icon = icon,
            shape = MaterialTheme.shapes.large,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - (1f - SelectedArtworkScale) * selectionProgress
                        scaleX = scale
                        scaleY = scale
                    },
        )
        AnimatedVisibility(
            visible = icon.isSelected && !isApplying,
            enter = scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onPrimary,
                )
            }
        }
        AnimatedVisibility(
            visible = isApplying,
            enter = fadeIn(motion.fastEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = 0.32f), MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun IconArtwork(
    icon: AppIconUiModel,
    shape: Shape,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .then(
                    if (elevated) {
                        Modifier.shadow(elevation = 8.dp, shape = shape)
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = icon.previewDrawableResId,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(if (icon.isDefault) DefaultIconInset else 0.dp),
        )
    }
}

@Composable
private fun NoSearchMatches(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(colors.secondaryContainer, MaterialShapes.Ghostish.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.search_off),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.onSecondaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.no_results_found),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommunityFooter(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(colors.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(colors.tertiaryContainer, MaterialShapes.Flower.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.auto_awesome),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.onTertiaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.app_icon_community_notice),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconDetailsSheet(
    icon: AppIconUiModel,
    enabled: Boolean,
    isApplying: Boolean,
    onSelect: (String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val select = remember(icon.id, onSelect) { { onSelect(icon.id) } }
    val iconName = appIconName(icon)

    BackAwareModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(SheetArtworkSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                color = colors.secondaryContainer,
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                            ),
                )
                IconArtwork(
                    icon = icon,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.size(SheetIconSize),
                    elevated = true,
                )
            }
            IconIdentity(
                icon = icon,
                onOpenAuthorProfile = onOpenAuthorProfile,
                onOpenSource = onOpenSource,
                largeTitle = false,
            )
            Button(
                onClick = select,
                enabled = enabled && !icon.isSelected,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(top = 4.dp),
            ) {
                AnimatedContent(
                    targetState =
                        when {
                            isApplying -> DetailsActionState.Applying
                            icon.isSelected -> DetailsActionState.Current
                            else -> DetailsActionState.Available
                        },
                    transitionSpec = {
                        (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f)) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)) using
                            SizeTransform(clip = false)
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    label = "detailsAction",
                ) { actionState ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
                    ) {
                        when (actionState) {
                            DetailsActionState.Applying -> {
                                LoadingIndicator(
                                    modifier = Modifier.size(ButtonDefaults.IconSize + 4.dp),
                                    color = LocalContentColor.current,
                                )
                                Text(
                                    text = stringResource(R.string.app_icon_applying, iconName),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            DetailsActionState.Current -> {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Text(text = stringResource(R.string.app_icon_current))
                            }

                            DetailsActionState.Available -> {
                                Text(text = stringResource(R.string.app_icon_use))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DetailsActionState {
    Available,
    Applying,
    Current,
}

@Composable
private fun appIconName(icon: AppIconUiModel): String =
    icon.nameResId?.let { nameResId ->
        stringResource(nameResId)
    } ?: icon.name.orEmpty()

@Composable
private fun IconScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
    }
}

@Composable
private fun IconScreenMessage(
    polygon: RoundedPolygon,
    @DrawableRes iconResId: Int,
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.ScreenHorizontalPadding,
                vertical = 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier =
                Modifier
                    .size(112.dp)
                    .background(
                        color = if (isError) colors.errorContainer else colors.secondaryContainer,
                        shape = polygon.toShape(),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = if (isError) colors.onErrorContainer else colors.onSecondaryContainer,
            )
        }
        Text(
            text = message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            FilledTonalButton(
                onClick = action,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

private val AppIconSortOrder.labelResId: Int
    @StringRes get() =
        when (this) {
            AppIconSortOrder.NEW_ADDED -> R.string.recently_added
            AppIconSortOrder.ALPHABETICAL -> R.string.sort_a_to_z
        }

private val AppIconSortOrder.iconResId: Int
    @DrawableRes get() =
        when (this) {
            AppIconSortOrder.NEW_ADDED -> R.drawable.new_release
            AppIconSortOrder.ALPHABETICAL -> R.drawable.text_fields
        }

@Composable
private fun Modifier.playerAwareInsets(): Modifier =
    windowInsetsPadding(
        LocalPlayerAwareWindowInsets.current.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    )

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Suppress("DEPRECATION")
private suspend fun Activity.updateTaskIcon(
    @DrawableRes drawableResId: Int,
) {
    val description =
        withContext(Dispatchers.Default) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityManager.TaskDescription
                    .Builder()
                    .setIcon(drawableResId)
                    .build()
            } else {
                val iconSize =
                    getSystemService(ActivityManager::class.java)
                        ?.launcherLargeIconSize
                        ?.coerceIn(1, MaximumLegacyTaskIconSize)
                        ?: DefaultLegacyTaskIconSize
                val drawable = getDrawable(drawableResId) ?: return@withContext null
                val bitmap =
                    Bitmap.createBitmap(
                        iconSize,
                        iconSize,
                        Bitmap.Config.ARGB_8888,
                    )
                drawable.setBounds(0, 0, iconSize, iconSize)
                drawable.draw(Canvas(bitmap))
                ActivityManager.TaskDescription(null, bitmap)
            }
        } ?: return
    setTaskDescription(description)
}

private val TileMorph by lazy { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }
private val FullLineSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

private const val StageContentKey = "icon_stage"
private const val ControlsContentKey = "icon_controls"
private const val HeaderContentKey = "icon_gallery_header"
private const val NoMatchesContentKey = "icon_no_matches"
private const val CommunityFooterContentKey = "community_icon_footer"
private const val TileContentType = "icon_tile"
private const val HaloRotationMillis = 24_000
private const val TileBackdropRotation = 60f
private const val SelectedArtworkScale = 0.7f
private const val DefaultLegacyTaskIconSize = 192
private const val MaximumLegacyTaskIconSize = 512
private val GalleryMaxWidth = 840.dp
private val TileMinWidth = 112.dp
private val TileSpacing = 12.dp
private val StageArtworkSize = 184.dp
private val StageIconSize = 124.dp
private val SheetArtworkSize = 168.dp
private val SheetIconSize = 112.dp
private val DefaultIconInset = 6.dp
