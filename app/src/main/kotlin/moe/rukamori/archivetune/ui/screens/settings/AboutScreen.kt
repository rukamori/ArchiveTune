/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AboutContributorUiCollection
import moe.rukamori.archivetune.viewmodels.AboutContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutDependencyLicenseUiCollection
import moe.rukamori.archivetune.viewmodels.AboutDependencyLicensesUiState
import moe.rukamori.archivetune.viewmodels.AboutDialog
import moe.rukamori.archivetune.viewmodels.AboutLinkCollection
import moe.rukamori.archivetune.viewmodels.AboutLinkEmphasis
import moe.rukamori.archivetune.viewmodels.AboutLinkUiModel
import moe.rukamori.archivetune.viewmodels.AboutScreenEffect
import moe.rukamori.archivetune.viewmodels.AboutScreenState
import moe.rukamori.archivetune.viewmodels.AboutTranslationContributorUiCollection
import moe.rukamori.archivetune.viewmodels.AboutTranslationContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutUiModel
import moe.rukamori.archivetune.viewmodels.AboutViewModel
import moe.rukamori.archivetune.viewmodels.TeamMember
import moe.rukamori.archivetune.viewmodels.TeamMemberCollection
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(viewModel, uriHandler) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutScreenEffect.OpenUri -> uriHandler.openUri(effect.uri)
            }
        }
    }

    AboutScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onNavigateUp = navController::navigateUp,
        onNavigateHome = navController::backToMain,
        onOpenUri = viewModel::openUri,
        onRetryContributors = viewModel::retryContributors,
        onOpenTranslationContributors = viewModel::openTranslationContributors,
        onOpenDependencyLicenses = viewModel::openDependencyLicenses,
        onDismissDialog = viewModel::dismissDialog,
        onRetryTranslationContributors = viewModel::retryTranslationContributors,
        onRetryDependencyLicenses = viewModel::retryDependencyLicenses,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenContent(
    state: AboutScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenUri: (String) -> Unit,
    onRetryContributors: () -> Unit,
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    onDismissDialog: () -> Unit,
    onRetryTranslationContributors: () -> Unit,
    onRetryDependencyLicenses: () -> Unit,
) {
    val listState = rememberLazyListState()
    val titleRevealOffsetPx = with(LocalDensity.current) { AboutDimensions.TitleRevealOffset.roundToPx() }
    val heroScrolledAway by remember(listState, titleRevealOffsetPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > titleRevealOffsetPx
        }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (heroScrolledAway || state !is AboutScreenState.Success) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "aboutTitleAlpha",
    )

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = titleAlpha
                                translationY = (1f - titleAlpha) * AboutDimensions.TitleRevealTravel.toPx()
                            },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        onLongClick = onNavigateHome,
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
        val playerAwareInsets =
            LocalPlayerAwareWindowInsets.current.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            )

        when (state) {
            AboutScreenState.Loading -> {
                AboutLoadingContent(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .windowInsetsPadding(playerAwareInsets),
                )
            }

            AboutScreenState.Empty -> {
                AboutMessageContent(
                    message = stringResource(R.string.no_results_found),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .windowInsetsPadding(playerAwareInsets),
                )
            }

            is AboutScreenState.Error -> {
                AboutMessageContent(
                    message = stringResource(state.messageResId),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .windowInsetsPadding(playerAwareInsets),
                )
            }

            is AboutScreenState.Success -> {
                AboutSuccessContent(
                    model = state.model,
                    onOpenUri = onOpenUri,
                    onRetryContributors = onRetryContributors,
                    onOpenTranslationContributors = onOpenTranslationContributors,
                    onOpenDependencyLicenses = onOpenDependencyLicenses,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(playerAwareInsets),
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding() + AboutSpacing.xs,
                            bottom = SettingsDimensions.ScreenBottomPadding,
                        ),
                    listState = listState,
                )
            }
        }
    }

    if (state is AboutScreenState.Success) {
        AboutFullScreenDialogs(
            model = state.model,
            onDismiss = onDismissDialog,
            onRetryTranslationContributors = onRetryTranslationContributors,
            onRetryDependencyLicenses = onRetryDependencyLicenses,
        )
    }
}

@Composable
private fun AboutLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun AboutMessageContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(AboutSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutFullScreenDialogs(
    model: AboutUiModel,
    onDismiss: () -> Unit,
    onRetryTranslationContributors: () -> Unit,
    onRetryDependencyLicenses: () -> Unit,
) {
    when (model.activeDialog) {
        AboutDialog.NONE -> {
            Unit
        }

        AboutDialog.TRANSLATION_CONTRIBUTORS -> {
            AboutFullScreenDialog(
                title = stringResource(R.string.about_contributor_translation),
                onDismiss = onDismiss,
            ) { modifier ->
                TranslationContributorsDialogContent(
                    state = model.translationContributorsState,
                    onRetry = onRetryTranslationContributors,
                    modifier = modifier,
                )
            }
        }

        AboutDialog.DEPENDENCY_LICENSES -> {
            AboutFullScreenDialog(
                title = stringResource(R.string.about_license),
                onDismiss = onDismiss,
            ) { modifier ->
                DependencyLicensesDialogContent(
                    state = model.dependencyLicensesState,
                    onRetry = onRetryDependencyLicenses,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun AboutFullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close_dialog),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
            },
        ) { innerPadding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun TranslationContributorsDialogContent(
    state: AboutTranslationContributorsUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AboutTranslationContributorsUiState.Loading -> {
            DialogStatusContent(
                message = stringResource(R.string.loading),
                showRetry = false,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        AboutTranslationContributorsUiState.Empty -> {
            DialogStatusContent(
                message = stringResource(R.string.no_results_found),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutTranslationContributorsUiState.Error -> {
            DialogStatusContent(
                message = stringResource(state.messageResId),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutTranslationContributorsUiState.Success -> {
            TranslationContributorList(
                contributors = state.contributors,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DependencyLicensesDialogContent(
    state: AboutDependencyLicensesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AboutDependencyLicensesUiState.Loading -> {
            DialogStatusContent(
                message = stringResource(R.string.loading),
                showRetry = false,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        AboutDependencyLicensesUiState.Empty -> {
            DialogStatusContent(
                message = stringResource(R.string.no_results_found),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutDependencyLicensesUiState.Error -> {
            DialogStatusContent(
                message = stringResource(state.messageResId),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutDependencyLicensesUiState.Success -> {
            DependencyLicenseList(
                licenses = state.licenses,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DialogStatusContent(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(AboutSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!showRetry) {
            LoadingIndicator(modifier = Modifier.size(40.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AboutSpacing.sm),
        )
        if (showRetry) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = AboutSpacing.sm),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun TranslationContributorList(
    contributors: AboutTranslationContributorUiCollection,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = AboutDimensions.DialogContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding = AboutDimensions.DialogListPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            items(
                count = contributors.size,
                key = { index -> contributors[index].language },
                contentType = { "translation_contributor" },
            ) { index ->
                val contributor = contributors[index]
                TranslationContributorListItem(
                    language = contributor.language,
                    contributors = contributor.contributors,
                    index = index,
                    itemCount = contributors.size,
                )
            }
        }
    }
}

@Composable
private fun TranslationContributorListItem(
    language: String,
    contributors: String?,
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = NoOpAction,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = itemCount),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (contributors == null) 64.dp else 76.dp),
        colors = AboutListItemDefaults.colors(),
        leadingContent = {
            AboutLeadingIcon(iconResId = R.drawable.language)
        },
        supportingContent =
            contributors?.let { contributorNames ->
                {
                    Text(
                        text = contributorNames,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        content = {
            Text(
                text = language,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun DependencyLicenseList(
    licenses: AboutDependencyLicenseUiCollection,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = AboutDimensions.DialogContentMaxWidth)
                    .fillMaxWidth(),
            contentPadding = AboutDimensions.DialogListPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            items(
                count = licenses.size,
                key = { index -> "${licenses[index].name}:${licenses[index].version.orEmpty()}:$index" },
                contentType = { "dependency_license" },
            ) { index ->
                val dependency = licenses[index]
                DependencyLicenseListItem(
                    name = dependency.name,
                    version = dependency.version,
                    licenses = dependency.licenses,
                    index = index,
                    itemCount = licenses.size,
                )
            }
        }
    }
}

@Composable
private fun DependencyLicenseListItem(
    name: String,
    version: String?,
    licenses: String?,
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = NoOpAction,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = itemCount),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp),
        colors = AboutListItemDefaults.colors(),
        leadingContent = {
            AboutLeadingIcon(iconResId = R.drawable.info)
        },
        overlineContent =
            version?.let { versionName ->
                {
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        supportingContent = {
            Text(
                text = licenses ?: stringResource(R.string.about_license_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun AboutSuccessContent(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    onRetryContributors: () -> Unit,
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val entrance = rememberAboutEntrance(sectionCount = AboutSection.entries.size)

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.lg),
    ) {
        item(key = AboutSection.HERO.name, contentType = "about_hero") {
            AboutContentContainer(modifier = Modifier.aboutEntrance(entrance[AboutSection.HERO.ordinal])) {
                AboutHero(
                    model = model,
                    listState = listState,
                )
            }
        }

        item(key = AboutSection.LINKS.name, contentType = "about_links") {
            AboutContentContainer(modifier = Modifier.aboutEntrance(entrance[AboutSection.LINKS.ordinal])) {
                AboutLinksSection(
                    links = model.primaryLinks,
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = AboutSection.PROJECT.name, contentType = "about_project") {
            AboutContentContainer(modifier = Modifier.aboutEntrance(entrance[AboutSection.PROJECT.ordinal])) {
                AboutProjectSection(
                    onOpenTranslationContributors = onOpenTranslationContributors,
                    onOpenDependencyLicenses = onOpenDependencyLicenses,
                )
            }
        }

        item(key = AboutSection.LEAD_DEVELOPER.name, contentType = "about_lead_developer") {
            AboutContentContainer(modifier = Modifier.aboutEntrance(entrance[AboutSection.LEAD_DEVELOPER.ordinal])) {
                LeadDeveloperSpotlight(
                    member = model.leadDeveloper,
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = AboutSection.TEAM.name, contentType = "about_team") {
            TeamCarouselSection(
                title = stringResource(R.string.about_archive_tune_team),
                members = model.collaborators,
                onOpenUri = onOpenUri,
                modifier = Modifier.aboutEntrance(entrance[AboutSection.TEAM.ordinal]),
            )
        }

        item(key = AboutSection.RESPECTERS.name, contentType = "about_respecters") {
            AboutContentContainer(modifier = Modifier.aboutEntrance(entrance[AboutSection.RESPECTERS.ordinal])) {
                RespecterSection(
                    members = model.respecters,
                    onOpenUri = onOpenUri,
                )
            }
        }

        item(key = AboutSection.CONTRIBUTORS.name, contentType = "about_contributors") {
            AboutContentContainer(modifier = Modifier.aboutEntrance(entrance[AboutSection.CONTRIBUTORS.ordinal])) {
                ContributorsSection(
                    state = model.contributorsState,
                    readMoreUrl = model.contributorsReadMoreUrl,
                    onOpenProfile = onOpenUri,
                    onRetry = onRetryContributors,
                )
            }
        }
    }
}

@Composable
private fun rememberAboutEntrance(sectionCount: Int): List<Animatable<Float, AnimationVector1D>> {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember(sectionCount) { List(sectionCount) { Animatable(if (played) 1f else 0f) } }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(progress) {
        if (played) return@LaunchedEffect
        coroutineScope {
            progress.forEachIndexed { index, animatable ->
                launch {
                    delay(index * AboutMotion.EntranceStaggerMillis)
                    animatable.animateTo(1f, spec)
                }
            }
        }
        played = true
    }
    return progress
}

private fun Modifier.aboutEntrance(progress: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer {
        val value = progress.value
        val scale = AboutMotion.EntranceStartScale + (1f - AboutMotion.EntranceStartScale) * value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * AboutDimensions.EntranceTravel.toPx()
        scaleX = scale
        scaleY = scale
    }

@Composable
private fun AboutContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = AboutDimensions.ScreenContentMaxWidth)
                    .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun AboutHero(
    model: AboutUiModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val offset =
                        if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset.toFloat()
                        } else {
                            0f
                        }
                    translationY = offset * AboutMotion.HeroParallaxFactor
                    alpha = 1f - (offset / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                }.padding(vertical = AboutSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
    ) {
        AboutHeroEmblem()
        Text(
            text = stringResource(model.appNameResId),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        AboutBuildMetadata(
            versionName = model.versionName,
            buildHash = model.buildHash,
            buildVariant = model.buildVariant,
        )
    }
}

@Composable
private fun AboutHeroEmblem(modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val morphs =
        remember {
            List(EmblemPolygons.size) { index ->
                Morph(EmblemPolygons[index], EmblemPolygons[(index + 1) % EmblemPolygons.size])
            }
        }
    val backdropShape = MaterialShapes.SoftBurst.toShape()
    var shapeStep by rememberSaveable { mutableIntStateOf(0) }
    val morphProgress = remember { Animatable(shapeStep.toFloat()) }
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) AboutMotion.EmblemPressedScale else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "aboutEmblemPress",
    )
    val rotationTransition = rememberInfiniteTransition(label = "aboutEmblemRotation")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = AboutMotion.EmblemRotationMillis, easing = LinearEasing),
            ),
        label = "aboutEmblemRotationValue",
    )
    val morphSpec = motion.slowSpatialSpec<Float>()

    LaunchedEffect(shapeStep) {
        morphProgress.animateTo(shapeStep.toFloat(), morphSpec)
    }

    val backdropColor = MaterialTheme.colorScheme.tertiaryContainer
    val emblemColor = MaterialTheme.colorScheme.primary
    val iconTint = MaterialTheme.colorScheme.onPrimary
    val iconColorFilter = remember(iconTint) { ColorFilter.tint(iconTint) }

    Box(
        modifier =
            modifier
                .size(AboutDimensions.EmblemBackdropSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { shapeStep += 1 },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = -rotation
                        shape = backdropShape
                        clip = true
                    }.background(backdropColor),
        )
        Box(
            modifier =
                Modifier
                    .size(AboutDimensions.EmblemSize)
                    .graphicsLayer {
                        val value = morphProgress.value
                        val segment = floor(value).toInt()
                        rotationZ = rotation
                        shape = EditorialMorphShape(morphs[segment.mod(morphs.size)], value - segment)
                        clip = true
                    }.background(emblemColor),
        )
        Image(
            painter = painterResource(R.drawable.about_splash),
            contentDescription = null,
            colorFilter = iconColorFilter,
            modifier = Modifier.size(AboutDimensions.EmblemIconSize),
        )
    }
}

@Composable
private fun AboutBuildMetadata(
    versionName: String,
    buildHash: String?,
    buildVariant: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        AboutMetadataPill(
            text = versionName,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            emphasized = true,
        )
        buildHash?.let { hash ->
            AboutMetadataPill(
                text = hash,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AboutMetadataPill(
            text = buildVariant,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun AboutMetadataPill(
    text: String,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = AboutDimensions.MetadataPillHeight),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style =
                    if (emphasized) {
                        MaterialTheme.typography.labelLargeEmphasized
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun AboutSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = AboutSpacing.xxs)
                .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
private fun AboutLinksSection(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm - AboutSpacing.xxs),
    ) {
        AboutSectionHeading(title = stringResource(R.string.about_section_links))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= AboutDimensions.WideLayoutBreakpoint) links.size.coerceAtLeast(1) else 3
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
            ) {
                repeat(links.size) { index ->
                    AboutLinkTile(
                        link = links[index],
                        onOpenUri = onOpenUri,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLinkTile(
    link: AboutLinkUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) AboutDimensions.TilePressedCorner else AboutDimensions.TileCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "aboutLinkTileCorner",
    )
    val (containerColor, contentColor) =
        when (link.emphasis) {
            AboutLinkEmphasis.PRIMARY -> {
                MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            }

            AboutLinkEmphasis.ACCENT -> {
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            }

            AboutLinkEmphasis.STANDARD -> {
                MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
            }
        }

    Surface(
        onClick = { onOpenUri(link.url) },
        modifier = modifier.heightIn(min = AboutDimensions.LinkTileMinHeight),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AboutSpacing.xs, vertical = AboutSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(link.iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(link.labelResId),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AboutProjectSection(
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val translationBadge = MaterialShapes.Cookie6Sided.toShape()
    val licenseBadge = MaterialShapes.Cookie4Sided.toShape()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm - AboutSpacing.xxs),
    ) {
        AboutSectionHeading(title = stringResource(R.string.about_section_project))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        ) {
            AboutProjectTile(
                title = stringResource(R.string.about_translations),
                summary = stringResource(R.string.about_translations_summary),
                iconResId = R.drawable.translate,
                badgeShape = translationBadge,
                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenTranslationContributors,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
            AboutProjectTile(
                title = stringResource(R.string.about_open_source_licenses),
                summary = stringResource(R.string.about_open_source_licenses_summary),
                iconResId = R.drawable.info,
                badgeShape = licenseBadge,
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenDependencyLicenses,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AboutProjectTile(
    title: String,
    summary: String,
    iconResId: Int,
    badgeShape: Shape,
    badgeColor: Color,
    badgeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (pressed) AboutDimensions.TilePressedCorner else AboutDimensions.ProjectTileCorner,
        animationSpec = motion.fastSpatialSpec(),
        label = "aboutProjectTileCorner",
    )
    val badgeRotation by animateFloatAsState(
        targetValue = if (pressed) AboutMotion.BadgePressedRotation else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "aboutProjectBadgeRotation",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = AboutDimensions.ProjectTileMinHeight),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(AboutSpacing.sm + AboutSpacing.xxs),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(AboutDimensions.ProjectBadgeSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = badgeRotation
                                    shape = badgeShape
                                    clip = true
                                }.background(badgeColor),
                    )
                    Icon(
                        painter = painterResource(iconResId),
                        contentDescription = null,
                        tint = badgeContentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(AboutSpacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LeadDeveloperSpotlight(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val profileUrl = member.profileUrl

    Surface(
        onClick = { profileUrl?.let(onOpenUri) },
        enabled = !profileUrl.isNullOrBlank(),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(AboutSpacing.md - AboutSpacing.xxs),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.sm + AboutSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AboutMorphAvatar(
                    avatarUrl = member.avatarUrl,
                    pressed = pressed,
                    restPolygon = MaterialShapes.Cookie12Sided,
                    pressedPolygon = MaterialShapes.Circle,
                    size = AboutDimensions.SpotlightAvatarSize,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs / 2),
                ) {
                    Text(
                        text = stringResource(R.string.about_lead_developer),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(member.positionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (member.links.size > 0) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
                ) {
                    repeat(member.links.size) { index ->
                        val link = member.links[index]
                        FilledIconButton(
                            onClick = { onOpenUri(link.url) },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                        ) {
                            Icon(
                                painter = painterResource(link.iconResId),
                                contentDescription = stringResource(link.labelResId),
                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamCarouselSection(
    title: String,
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = AboutDimensions.ScreenContentMaxWidth + SettingsDimensions.ScreenHorizontalPadding * 2)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm - AboutSpacing.xxs),
        ) {
            AboutSectionHeading(
                title = title,
                modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                trailing = { AboutCountBadge(count = members.size) },
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs + AboutSpacing.xxs),
            ) {
                items(
                    count = members.size,
                    key = { index -> members[index].name },
                    contentType = { "about_team_member" },
                ) { index ->
                    TeamMemberCard(
                        member = members[index],
                        restPolygon = TeamAvatarPolygons[index % TeamAvatarPolygons.size],
                        onOpenUri = onOpenUri,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamMemberCard(
    member: TeamMember,
    restPolygon: RoundedPolygon,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val profileUrl = member.profileUrl

    Surface(
        onClick = { profileUrl?.let(onOpenUri) },
        enabled = !profileUrl.isNullOrBlank(),
        modifier = modifier.width(AboutDimensions.TeamCardWidth),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(AboutSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        ) {
            AboutMorphAvatar(
                avatarUrl = member.avatarUrl,
                pressed = pressed,
                restPolygon = restPolygon,
                pressedPolygon = MaterialShapes.Circle,
                size = AboutDimensions.TeamAvatarSize,
            )
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(member.positionResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MemberLinkButtons(
                links = member.links,
                onOpenUri = onOpenUri,
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xxs, Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun RespecterSection(
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm - AboutSpacing.xxs),
    ) {
        AboutSectionHeading(
            title = stringResource(R.string.about_respecter),
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.favorite),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs)) {
            repeat(members.size) { index ->
                RespecterCard(
                    member = members[index],
                    onOpenUri = onOpenUri,
                )
            }
        }
    }
}

@Composable
private fun RespecterCard(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val profileUrl = member.profileUrl

    Surface(
        onClick = { profileUrl?.let(onOpenUri) },
        enabled = !profileUrl.isNullOrBlank(),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(AboutSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutMorphAvatar(
                avatarUrl = member.avatarUrl,
                pressed = pressed,
                restPolygon = MaterialShapes.Circle,
                pressedPolygon = MaterialShapes.Cookie9Sided,
                size = AboutDimensions.RespecterAvatarSize,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs / 2),
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(member.positionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MemberLinkButtons(
                links = member.links,
                onOpenUri = onOpenUri,
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xxs, Alignment.End),
                modifier = Modifier.widthIn(max = AboutDimensions.MemberLinksMaxWidth),
            )
        }
    }
}

@Composable
private fun MemberLinkButtons(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
) {
    if (links.size == 0) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            FilledTonalIconButton(
                onClick = { onOpenUri(link.url) },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
            ) {
                Icon(
                    painter = painterResource(link.iconResId),
                    contentDescription = stringResource(link.labelResId),
                    modifier = Modifier.size(IconButtonDefaults.smallIconSize),
                )
            }
        }
    }
}

@Composable
private fun AboutMorphAvatar(
    avatarUrl: String,
    pressed: Boolean,
    restPolygon: RoundedPolygon,
    pressedPolygon: RoundedPolygon,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val morph = remember(restPolygon, pressedPolygon) { Morph(restPolygon, pressedPolygon) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "aboutAvatarMorph",
    )

    AsyncImage(
        model = avatarUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    shape = EditorialMorphShape(morph, progress.coerceIn(0f, 1f))
                    clip = true
                }.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun AboutCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = AboutDimensions.CountBadgeSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(
            modifier = Modifier.widthIn(min = AboutDimensions.CountBadgeSize),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ContributorsSection(
    state: AboutContributorsUiState,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm - AboutSpacing.xxs),
    ) {
        AboutSectionHeading(
            title = stringResource(R.string.about_contributors),
            trailing =
                (state as? AboutContributorsUiState.Success)?.let { success ->
                    { AboutCountBadge(count = success.contributors.size) }
                },
        )
        AnimatedContent(
            targetState = state,
            contentKey = { contributorsState -> contributorsState::class },
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "aboutContributorsState",
        ) { contributorsState ->
            when (contributorsState) {
                AboutContributorsUiState.Loading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = AboutDimensions.ContributorStatusMinHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                AboutContributorsUiState.Empty -> {
                    ContributorStatusContent(
                        message = stringResource(R.string.no_results_found),
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Error -> {
                    ContributorStatusContent(
                        message = stringResource(contributorsState.messageResId),
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Success -> {
                    ContributorGrid(
                        contributors = contributorsState.contributors,
                        readMoreUrl = readMoreUrl,
                        onOpenProfile = onOpenProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorStatusContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AboutDimensions.ContributorStatusMinHeight)
                    .padding(AboutSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm, Alignment.CenterVertically),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun ContributorGrid(
    contributors: AboutContributorUiCollection,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val reveal = remember(contributors.size) { List(contributors.size) { Animatable(if (revealed) 1f else 0f) } }
    val revealSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(reveal) {
        if (revealed) return@LaunchedEffect
        coroutineScope {
            reveal.forEachIndexed { index, animatable ->
                launch {
                    delay(index * AboutMotion.ContributorStaggerMillis)
                    animatable.animateTo(1f, revealSpec)
                }
            }
        }
        revealed = true
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(AboutSpacing.xs),
            ) {
                val spacing = AboutSpacing.xxs
                val columns = (maxWidth / AboutDimensions.ContributorCellMinWidth).toInt().coerceAtLeast(3)
                val rowCount = (contributors.size + columns - 1) / columns

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    repeat(rowCount) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            repeat(columns) { column ->
                                val index = row * columns + column
                                if (index < contributors.size) {
                                    val contributor = contributors[index]
                                    val progress = reveal.getOrNull(index)
                                    ContributorCell(
                                        login = contributor.login,
                                        avatarUrl = contributor.avatarUrl,
                                        onClick = { onOpenProfile(contributor.profileUrl) },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .graphicsLayer {
                                                    val value = progress?.value ?: 1f
                                                    val scale = AboutMotion.ContributorStartScale +
                                                        (1f - AboutMotion.ContributorStartScale) * value
                                                    alpha = value.coerceIn(0f, 1f)
                                                    scaleX = scale
                                                    scaleY = scale
                                                },
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onOpenProfile(readMoreUrl) },
            shapes = ButtonDefaults.shapes(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = ButtonDefaults.MinHeight),
        ) {
            Text(text = stringResource(R.string.about_contributors_see_all))
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
    }
}

@Composable
private fun ContributorCell(
    login: String,
    avatarUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ).padding(vertical = AboutSpacing.xs, horizontal = AboutSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        AboutMorphAvatar(
            avatarUrl = avatarUrl,
            pressed = pressed,
            restPolygon = MaterialShapes.Circle,
            pressedPolygon = MaterialShapes.Cookie9Sided,
            size = AboutDimensions.ContributorAvatarSize,
        )
        Text(
            text = login,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AboutLeadingIcon(
    iconResId: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(AboutSpacing.sm)
                    .size(20.dp),
        )
    }
}

private enum class AboutSection {
    HERO,
    LINKS,
    PROJECT,
    LEAD_DEVELOPER,
    TEAM,
    RESPECTERS,
    CONTRIBUTORS,
}

private val EmblemPolygons =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie12Sided,
    )

private val TeamAvatarPolygons =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Sunny,
    )

private object AboutDimensions {
    val ScreenContentMaxWidth = 840.dp
    val DialogContentMaxWidth = 920.dp
    val WideLayoutBreakpoint = 600.dp
    val DialogListPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    val TitleRevealOffset = 160.dp
    val TitleRevealTravel = 8.dp
    val EntranceTravel = 40.dp
    val EmblemBackdropSize = 184.dp
    val EmblemSize = 136.dp
    val EmblemIconSize = 72.dp
    val MetadataPillHeight = 32.dp
    val TileCorner = 28.dp
    val ProjectTileCorner = 32.dp
    val TilePressedCorner = 14.dp
    val LinkTileMinHeight = 88.dp
    val ProjectTileMinHeight = 156.dp
    val ProjectBadgeSize = 52.dp
    val SpotlightAvatarSize = 96.dp
    val TeamCardWidth = 172.dp
    val TeamAvatarSize = 88.dp
    val RespecterAvatarSize = 56.dp
    val MemberLinksMaxWidth = 96.dp
    val CountBadgeSize = 32.dp
    val ContributorStatusMinHeight = 160.dp
    val ContributorCellMinWidth = 84.dp
    val ContributorAvatarSize = 52.dp
}

private object AboutSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
}

private object AboutMotion {
    const val EntranceStaggerMillis = 70L
    const val EntranceStartScale = 0.94f
    const val ContributorStaggerMillis = 28L
    const val ContributorStartScale = 0.6f
    const val HeroParallaxFactor = 0.4f
    const val EmblemRotationMillis = 24_000
    const val EmblemPressedScale = 0.92f
    const val BadgePressedRotation = 45f
}

private object AboutListItemDefaults {
    @Composable
    fun colors() =
        ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
}

private val NoOpAction: () -> Unit = {}
