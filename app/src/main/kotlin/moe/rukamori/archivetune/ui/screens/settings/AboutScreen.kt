/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AboutContributorUiCollection
import moe.rukamori.archivetune.viewmodels.AboutContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutDependencyLicenseUiCollection
import moe.rukamori.archivetune.viewmodels.AboutDependencyLicensesUiState
import moe.rukamori.archivetune.viewmodels.AboutDialog
import moe.rukamori.archivetune.viewmodels.AboutLinkCollection
import moe.rukamori.archivetune.viewmodels.AboutScreenEffect
import moe.rukamori.archivetune.viewmodels.AboutScreenState
import moe.rukamori.archivetune.viewmodels.AboutTranslationContributorUiCollection
import moe.rukamori.archivetune.viewmodels.AboutTranslationContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutUiModel
import moe.rukamori.archivetune.viewmodels.AboutViewModel
import moe.rukamori.archivetune.viewmodels.TeamMember
import moe.rukamori.archivetune.viewmodels.TeamMemberCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = appBarScrollBehavior()

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
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
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
                    TopAppBarDefaults.largeTopAppBarColors(
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
                    .fillMaxWidth()
                    .widthIn(max = AboutDimensions.DialogContentMaxWidth),
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
                    .fillMaxWidth()
                    .widthIn(max = AboutDimensions.DialogContentMaxWidth),
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
    val leadDevelopers = remember(model.leadDeveloper) { TeamMemberCollection.of(model.leadDeveloper) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.lg),
    ) {
        item(key = "identity", contentType = "about_identity") {
            AboutContentContainer {
                AboutIdentityCard(
                    model = model,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "project_information", contentType = "about_actions") {
            AboutContentContainer {
                AboutProjectInformationSection(
                    onOpenTranslationContributors = onOpenTranslationContributors,
                    onOpenDependencyLicenses = onOpenDependencyLicenses,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "lead_developer", contentType = "about_team_section") {
            AboutContentContainer {
                TeamMemberSection(
                    title = stringResource(R.string.about_lead_developer),
                    members = leadDevelopers,
                    onOpenUri = onOpenUri,
                    prominentFirstItem = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "team", contentType = "about_team_section") {
            AboutContentContainer {
                TeamMemberSection(
                    title = stringResource(R.string.about_archive_tune_team),
                    members = model.collaborators,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "respecters", contentType = "about_team_section") {
            AboutContentContainer {
                TeamMemberSection(
                    title = stringResource(R.string.about_respecter),
                    members = model.respecters,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "contributors", contentType = "about_contributors") {
            AboutContentContainer {
                ContributorsSection(
                    state = model.contributorsState,
                    readMoreUrl = model.contributorsReadMoreUrl,
                    onOpenProfile = onOpenUri,
                    onRetry = onRetryContributors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
                    .fillMaxWidth()
                    .widthIn(max = AboutDimensions.ScreenContentMaxWidth),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutIdentityCard(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= AboutDimensions.HorizontalHeroBreakpoint) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(AboutSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AboutSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AboutIdentity(
                        model = model,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f),
                    )
                    LinkChipRow(
                        links = model.primaryLinks,
                        onOpenUri = onOpenUri,
                        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs, Alignment.End),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(AboutSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AboutSpacing.md),
                ) {
                    AboutIdentity(
                        model = model,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkChipRow(
                        links = model.primaryLinks,
                        onOpenUri = onOpenUri,
                        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs, Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutIdentity(
    model: AboutUiModel,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
    ) {
        SurfaceAppIcon()
        Text(
            text = stringResource(model.appNameResId),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs, horizontalAlignment),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        ) {
            AboutMetadataBadge(text = model.versionName)
            model.buildHash?.let { buildHash ->
                AboutMetadataBadge(text = buildHash)
            }
            AboutMetadataBadge(text = model.buildVariant)
        }
    }
}

@Composable
private fun SurfaceAppIcon(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        val iconTint = MaterialTheme.colorScheme.onPrimaryContainer
        val iconColorFilter = remember(iconTint) { ColorFilter.tint(iconTint) }
        Image(
            painter = painterResource(R.drawable.about_splash),
            contentDescription = null,
            colorFilter = iconColorFilter,
            modifier =
                Modifier
                    .padding(AboutSpacing.sm)
                    .size(64.dp),
        )
    }
}

@Composable
private fun AboutMetadataBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier.heightIn(min = 32.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkChipRow(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val onClick = remember(link.url, onOpenUri) { { onOpenUri(link.url) } }

            AssistChip(
                onClick = onClick,
                leadingIcon = {
                    Icon(
                        painter = painterResource(link.iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(link.labelResId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun AboutProjectInformationSection(
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        AboutActionListItem(
            title = stringResource(R.string.about_contributor_translation),
            iconResId = R.drawable.translate,
            index = 0,
            itemCount = 2,
            onClick = onOpenTranslationContributors,
        )
        AboutActionListItem(
            title = stringResource(R.string.about_license),
            iconResId = R.drawable.info,
            index = 1,
            itemCount = 2,
            onClick = onOpenDependencyLicenses,
        )
    }
}

@Composable
private fun AboutActionListItem(
    title: String,
    iconResId: Int,
    index: Int,
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = itemCount),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        colors = AboutListItemDefaults.colors(),
        leadingContent = {
            AboutLeadingIcon(iconResId = iconResId)
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun TeamMemberSection(
    title: String,
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    prominentFirstItem: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        AboutSectionHeader(title = title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            repeat(members.size) { index ->
                TeamMemberListItem(
                    member = members[index],
                    index = index,
                    itemCount = members.size,
                    onOpenUri = onOpenUri,
                    avatarSize = if (prominentFirstItem && index == 0) 64.dp else 52.dp,
                    minHeight = if (prominentFirstItem && index == 0) 96.dp else 80.dp,
                )
            }
        }
    }
}

@Composable
private fun AboutSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
    )
}

@Composable
private fun TeamMemberListItem(
    member: TeamMember,
    index: Int,
    itemCount: Int,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp,
    minHeight: Dp,
) {
    val onClick: () -> Unit =
        remember(member.profileUrl, onOpenUri) {
            {
                val profileUrl = member.profileUrl
                if (!profileUrl.isNullOrBlank()) {
                    onOpenUri(profileUrl)
                }
            }
        }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = itemCount),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
        colors = AboutListItemDefaults.colors(),
        leadingContent = {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = member.name,
                modifier =
                    Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        supportingContent = {
            Text(
                text = stringResource(member.positionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            MemberLinkActions(
                links = member.links,
                onOpenUri = onOpenUri,
            )
        },
        content = {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberLinkActions(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.widthIn(max = 156.dp),
        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xxs, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val onClick = remember(link.url, onOpenUri) { { onOpenUri(link.url) } }

            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(link.iconResId),
                    contentDescription = stringResource(link.labelResId),
                    modifier = Modifier.size(18.dp),
                )
            }
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_contributors))
        when (state) {
            AboutContributorsUiState.Loading -> {
                ContributorStatusContent(
                    message = stringResource(R.string.loading),
                    showRetry = false,
                    onRetry = onRetry,
                )
            }

            AboutContributorsUiState.Empty -> {
                ContributorStatusContent(
                    message = stringResource(R.string.no_results_found),
                    showRetry = true,
                    onRetry = onRetry,
                )
            }

            is AboutContributorsUiState.Error -> {
                ContributorStatusContent(
                    message = stringResource(state.messageResId),
                    showRetry = true,
                    onRetry = onRetry,
                )
            }

            is AboutContributorsUiState.Success -> {
                ContributorList(
                    contributors = state.contributors,
                    readMoreUrl = readMoreUrl,
                    onOpenProfile = onOpenProfile,
                )
            }
        }
    }
}

@Composable
private fun ContributorStatusContent(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AboutSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.sm),
        ) {
            if (!showRetry) {
                LoadingIndicator(modifier = Modifier.size(32.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showRetry) {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun ContributorList(
    contributors: AboutContributorUiCollection,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemCount = contributors.size + 1
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        repeat(contributors.size) { index ->
            val contributor = contributors[index]
            ContributorListItem(
                login = contributor.login,
                avatarUrl = contributor.avatarUrl,
                profileUrl = contributor.profileUrl,
                index = index,
                itemCount = itemCount,
                onOpenProfile = onOpenProfile,
            )
        }
        ContributorReadMoreListItem(
            readMoreUrl = readMoreUrl,
            index = itemCount - 1,
            itemCount = itemCount,
            onOpenProfile = onOpenProfile,
        )
    }
}

@Composable
private fun ContributorListItem(
    login: String,
    avatarUrl: String,
    profileUrl: String,
    index: Int,
    itemCount: Int,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick = remember(profileUrl, onOpenProfile) { { onOpenProfile(profileUrl) } }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = itemCount),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        colors = AboutListItemDefaults.colors(),
        leadingContent = {
            AsyncImage(
                model = avatarUrl,
                contentDescription = login,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        content = {
            Text(
                text = login,
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun ContributorReadMoreListItem(
    readMoreUrl: String,
    index: Int,
    itemCount: Int,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick = remember(readMoreUrl, onOpenProfile) { { onOpenProfile(readMoreUrl) } }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = itemCount),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        colors = AboutListItemDefaults.colors(),
        leadingContent = {
            AboutLeadingIcon(iconResId = R.drawable.add_circle)
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            Text(
                text = stringResource(R.string.more),
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun AboutLeadingIcon(
    iconResId: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
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

private object AboutDimensions {
    val ScreenContentMaxWidth = 840.dp
    val DialogContentMaxWidth = 920.dp
    val HorizontalHeroBreakpoint = 600.dp
    val DialogListPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}

private object AboutSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
}

private object AboutListItemDefaults {
    @Composable
    fun colors() =
        ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
}

private val NoOpAction: () -> Unit = {}
