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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
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
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

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
        onShowOverflowMenu = viewModel::showOverflowMenu,
        onDismissOverflowMenu = viewModel::dismissOverflowMenu,
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
    onShowOverflowMenu: () -> Unit,
    onDismissOverflowMenu: () -> Unit,
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    onDismissDialog: () -> Unit,
    onRetryTranslationContributors: () -> Unit,
    onRetryDependencyLicenses: () -> Unit,
) {
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    if (state is AboutScreenState.Success) {
                        AboutOverflowMenu(
                            expanded = state.model.isOverflowMenuExpanded,
                            onShowMenu = onShowOverflowMenu,
                            onDismissMenu = onDismissOverflowMenu,
                            onOpenTranslationContributors = onOpenTranslationContributors,
                            onOpenDependencyLicenses = onOpenDependencyLicenses,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val stateModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )

        when (state) {
            AboutScreenState.Loading -> {
                AboutLoadingContent(modifier = stateModifier)
            }

            AboutScreenState.Empty -> {
                AboutMessageContent(
                    message = stringResource(R.string.no_results_found),
                    modifier = stateModifier,
                )
            }

            is AboutScreenState.Error -> {
                AboutMessageContent(
                    message = stringResource(state.messageResId),
                    modifier = stateModifier,
                )
            }

            is AboutScreenState.Success -> {
                AboutSuccessContent(
                    model = state.model,
                    onOpenUri = onOpenUri,
                    onRetryContributors = onRetryContributors,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        ),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = 32.dp,
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
private fun AboutOverflowMenu(
    expanded: Boolean,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onShowMenu,
            onLongClick = {},
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.more_options),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissMenu,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.about_contributor_translation)) },
                onClick = onOpenTranslationContributors,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.translate),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.about_license)) },
                onClick = onOpenDependencyLicenses,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun AboutLoadingContent(
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.padding(24.dp),
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
        AboutDialog.NONE -> Unit

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
        properties = DialogProperties(
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
                    colors = TopAppBarDefaults.topAppBarColors(
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
                        LocalPlayerAwareWindowInsets.current.only(
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
        modifier = modifier.padding(24.dp),
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
            modifier = Modifier.padding(top = 12.dp),
        )
        if (showRetry) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
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
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = contributors.size,
            key = { index -> contributors[index].language },
            contentType = { "translation_contributor" },
        ) { index ->
            val contributor = contributors[index]
            SegmentedListItemSurface(
                index = index,
                itemCount = contributors.size,
            ) {
                TranslationContributorListItem(
                    language = contributor.language,
                    contributors = contributor.contributors,
                )
            }
        }
    }
}

@Composable
private fun TranslationContributorListItem(
    language: String,
    contributors: String?,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.heightIn(min = if (contributors == null) 56.dp else 72.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.language),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        headlineContent = {
            Text(
                text = language,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = contributors?.let { contributorNames ->
            {
                Text(
                    text = contributorNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun DependencyLicenseList(
    licenses: AboutDependencyLicenseUiCollection,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = licenses.size,
            key = { index -> "${licenses[index].name}:${licenses[index].version.orEmpty()}:$index" },
            contentType = { "dependency_license" },
        ) { index ->
            val dependency = licenses[index]
            SegmentedListItemSurface(
                index = index,
                itemCount = licenses.size,
            ) {
                DependencyLicenseListItem(
                    name = dependency.name,
                    version = dependency.version,
                    licenses = dependency.licenses,
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
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.heightIn(min = 72.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                version?.let { versionName ->
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = licenses ?: stringResource(R.string.about_license_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun SegmentedListItemSurface(
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = segmentedListItemShape(
            index = index,
            itemCount = itemCount,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

private fun segmentedListItemShape(
    index: Int,
    itemCount: Int,
): Shape {
    val outerCorner = 24.dp
    val innerCorner = 4.dp
    return when {
        itemCount <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomEnd = innerCorner,
            bottomStart = innerCorner,
        )
        index == itemCount - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomEnd = outerCorner,
            bottomStart = outerCorner,
        )
        else -> RoundedCornerShape(innerCorner)
    }
}

@Composable
private fun AboutSuccessContent(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    onRetryContributors: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        item(key = "lead_developer", contentType = "about_lead_developer") {
            AboutContentContainer {
                LeadDeveloperSection(
                    member = model.leadDeveloper,
                    onOpenUri = onOpenUri,
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfaceAppIcon()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(model.appNameResId),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutMetadataBadge(text = model.versionName)
                    model.buildHash?.let { buildHash ->
                        AboutMetadataBadge(text = buildHash)
                    }
                    AboutMetadataBadge(text = model.buildVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LinkChipRow(
                links = model.primaryLinks,
                onOpenUri = onOpenUri,
            )
        }
    }
}

@Composable
private fun SurfaceAppIcon(
    modifier: Modifier = Modifier,
) {
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
            modifier = Modifier
                .padding(16.dp)
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
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val label = stringResource(link.labelResId)
            val onClick = remember(link.url, onOpenUri) {
                { onOpenUri(link.url) }
            }

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
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun LeadDeveloperSection(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_lead_developer))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            TeamMemberListItem(
                member = member,
                onOpenUri = onOpenUri,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                avatarSize = 72.dp,
                minHeight = 104.dp,
            )
        }
    }
}

@Composable
private fun TeamMemberSection(
    title: String,
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = title)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                repeat(members.size) { index ->
                    TeamMemberListItem(
                        member = members[index],
                        onOpenUri = onOpenUri,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )

                    if (index < members.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            thickness = SettingsDimensions.DividerThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
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
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier = modifier.padding(
            horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
            vertical = SettingsDimensions.SectionHeaderBottomPadding,
        ),
    )
}

@Composable
private fun TeamMemberListItem(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 56.dp,
    minHeight: Dp = 88.dp,
) {
    val profileUrl = member.profileUrl
    val itemClickModifier = remember(profileUrl, onOpenUri) {
        if (profileUrl.isNullOrBlank()) {
            Modifier
        } else {
            Modifier.clickable { onOpenUri(profileUrl) }
        }
    }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(itemClickModifier),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = member.name,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        headlineContent = {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        modifier = modifier.widthIn(max = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val onClick = remember(link.url, onOpenUri) {
                { onOpenUri(link.url) }
            }

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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_contributors))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
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
}

@Composable
private fun ContributorStatusContent(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun ContributorList(
    contributors: AboutContributorUiCollection,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(contributors.size) { index ->
            val contributor = contributors[index]

            ContributorListItem(
                login = contributor.login,
                avatarUrl = contributor.avatarUrl,
                profileUrl = contributor.profileUrl,
                onOpenProfile = onOpenProfile,
            )

            if (index < contributors.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = SettingsDimensions.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = SettingsDimensions.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        ContributorReadMoreListItem(
            readMoreUrl = readMoreUrl,
            onOpenProfile = onOpenProfile,
        )
    }
}

@Composable
private fun ContributorListItem(
    login: String,
    avatarUrl: String,
    profileUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemClickModifier = remember(profileUrl, onOpenProfile) {
        if (profileUrl.isBlank()) {
            Modifier
        } else {
            Modifier.clickable { onOpenProfile(profileUrl) }
        }
    }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(itemClickModifier),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = {
            AsyncImage(
                model = avatarUrl,
                contentDescription = login,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        headlineContent = {
            Text(
                text = login,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick = remember(readMoreUrl, onOpenProfile) {
        { onOpenProfile(readMoreUrl) }
    }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.add_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.more),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
