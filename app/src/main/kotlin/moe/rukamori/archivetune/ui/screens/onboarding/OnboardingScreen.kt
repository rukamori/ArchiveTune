/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.onboarding.OnboardingCommunityActionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingEvent
import moe.rukamori.archivetune.onboarding.OnboardingPageId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionAction
import moe.rukamori.archivetune.onboarding.OnboardingPermissionStatus
import moe.rukamori.archivetune.onboarding.OnboardingPermissionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingScreenState
import moe.rukamori.archivetune.onboarding.OnboardingUiState
import moe.rukamori.archivetune.onboarding.OnboardingViewModel

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onPermissionResult()
        }

    LaunchedEffect(context, viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.RequestPermission -> permissionLauncher.launch(event.permission)
                OnboardingEvent.OpenInstallPackagesSettings -> {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData("package:${context.packageName}".toUri())
                    runCatching {
                        settingsLauncher.launch(intent)
                    }
                }
                is OnboardingEvent.OpenUri -> {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, event.url.toUri()))
                    }
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onComplete = viewModel::complete,
        onPermissionAction = viewModel::onPermissionAction,
        onCommunityAction = viewModel::onCommunityAction,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state) {
            OnboardingScreenState.Loading -> LoadingContent(contentPadding = padding)
            OnboardingScreenState.Empty ->
                MessageContent(
                    title = stringResource(R.string.onboarding_empty_title),
                    subtitle = stringResource(R.string.onboarding_empty_subtitle),
                    actionLabel = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                    contentPadding = padding,
                )
            is OnboardingScreenState.Error ->
                MessageContent(
                    title = stringResource(state.messageResId),
                    subtitle = stringResource(R.string.onboarding_empty_subtitle),
                    actionLabel = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                    contentPadding = padding,
                )
            is OnboardingScreenState.Success ->
                OnboardingSuccessContent(
                    uiState = state.uiState,
                    onNext = onNext,
                    onBack = onBack,
                    onPermissionAction = onPermissionAction,
                    onCommunityAction = onCommunityAction,
                    contentPadding = padding,
                )
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageContent(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = OnboardingContentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun OnboardingSuccessContent(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    contentPadding: PaddingValues,
) {
    val pagerState =
        rememberPagerState(
            initialPage = uiState.currentPage,
            pageCount = { uiState.pages.size },
        )

    LaunchedEffect(uiState.currentPage, uiState.pages.size) {
        val targetPage = uiState.currentPage.coerceIn(0, uiState.pages.lastIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        containerColor = Color.Transparent,
        bottomBar = {
            OnboardingBottomBar(
                currentPage = uiState.currentPage,
                pageCount = uiState.pages.size,
                onBack = onBack,
                onNext = onNext,
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) { pageIndex ->
            when (uiState.pages[pageIndex].id) {
                OnboardingPageId.WELCOME ->
                    WelcomePage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                    )
                OnboardingPageId.PERMISSIONS ->
                    PermissionsPage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                        onPermissionAction = onPermissionAction,
                    )
                OnboardingPageId.COMMUNITY ->
                    CommunityPage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                        onCommunityAction = onCommunityAction,
                    )
            }
        }
    }
}

@Composable
private fun WelcomePage(
    uiState: OnboardingUiState,
    pageIndex: Int,
) {
    val page = uiState.pages[pageIndex]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item(key = page.id.name, contentType = "welcome") {
            Column(
                modifier = Modifier.widthIn(max = OnboardingContentMaxWidth),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        painter = painterResource(page.iconResId),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .padding(18.dp)
                                .size(56.dp),
                        tint = Color.Unspecified,
                    )
                }
                PageTitle(page.titleResId, page.subtitleResId)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(text = stringResource(uiState.variantLabelResId)) },
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text =
                                    stringResource(
                                        R.string.onboarding_version_label,
                                        uiState.versionName,
                                    ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val page = uiState.pages[pageIndex]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = page.id.name, contentType = "header") {
            PageHeader(
                iconResId = page.iconResId,
                titleResId = page.titleResId,
                subtitleResId = page.subtitleResId,
            )
        }
        itemsIndexed(
            items = uiState.permissions,
            key = { _, item -> item.id.name },
            contentType = { _, item -> "permission-${item.id.name}" },
        ) { index, item ->
            PermissionRow(
                permission = item,
                index = index,
                count = uiState.permissions.size,
                onPermissionAction = onPermissionAction,
            )
        }
    }
}

@Composable
private fun CommunityPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val page = uiState.pages[pageIndex]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = page.id.name, contentType = "header") {
            PageHeader(
                iconResId = page.iconResId,
                titleResId = page.titleResId,
                subtitleResId = page.subtitleResId,
            )
        }
        itemsIndexed(
            items = uiState.communityActions,
            key = { _, item -> item.id },
            contentType = { _, item -> "community-${item.id}" },
        ) { index, item ->
            CommunityRow(
                action = item,
                index = index,
                count = uiState.communityActions.size,
                onCommunityAction = onCommunityAction,
            )
        }
    }
}

@Composable
private fun PageHeader(
    iconResId: Int,
    titleResId: Int,
    subtitleResId: Int,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(14.dp)
                        .size(32.dp),
            )
        }
        PageTitle(titleResId = titleResId, subtitleResId = subtitleResId)
    }
}

@Composable
private fun PageTitle(
    titleResId: Int,
    subtitleResId: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(subtitleResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionRow(
    permission: OnboardingPermissionUiModel,
    index: Int,
    count: Int,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val onClick =
        remember(permission.action, onPermissionAction) {
            {
                val action = permission.action
                if (action != null) {
                    onPermissionAction(action)
                }
            }
        }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 76.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        leadingContent = {
            PermissionIcon(permission = permission)
        },
        supportingContent = {
            Text(
                text = stringResource(permission.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            PermissionStatusAction(
                permission = permission,
                onPermissionAction = onPermissionAction,
            )
        },
    ) {
        Text(
            text = stringResource(permission.titleResId),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PermissionIcon(permission: OnboardingPermissionUiModel) {
    val containerColor =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> MaterialTheme.colorScheme.primaryContainer
            OnboardingPermissionStatus.NEEDS_ACTION -> MaterialTheme.colorScheme.tertiaryContainer
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> MaterialTheme.colorScheme.secondaryContainer
            OnboardingPermissionStatus.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> MaterialTheme.colorScheme.onPrimaryContainer
            OnboardingPermissionStatus.NEEDS_ACTION -> MaterialTheme.colorScheme.onTertiaryContainer
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> MaterialTheme.colorScheme.onSecondaryContainer
            OnboardingPermissionStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Icon(
            painter = painterResource(permission.iconResId),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(10.dp)
                    .size(24.dp),
        )
    }
}

@Composable
private fun PermissionStatusAction(
    permission: OnboardingPermissionUiModel,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val action = permission.action

    if (action != null) {
        FilledTonalButton(onClick = { onPermissionAction(action) }) {
            Text(text = stringResource(R.string.allow))
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = stringResource(permission.status.labelResId()),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommunityRow(
    action: OnboardingCommunityActionUiModel,
    index: Int,
    count: Int,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val onClick = remember(action, onCommunityAction) { { onCommunityAction(action) } }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 76.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painter = painterResource(action.iconResId),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(24.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text = stringResource(action.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(
            text = stringResource(action.titleResId),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val isLastPage = currentPage >= pageCount - 1

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.widthIn(max = OnboardingContentMaxWidth),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = currentPage > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.back_button_desc))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            if (isLastPage) {
                                stringResource(R.string.onboarding_finish)
                            } else {
                                stringResource(R.string.next)
                            },
                    )
                }
            }
        }
    }
}

private fun OnboardingPermissionStatus.labelResId(): Int =
    when (this) {
        OnboardingPermissionStatus.ALLOWED -> R.string.permission_status_allowed
        OnboardingPermissionStatus.NEEDS_ACTION -> R.string.allow
        OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> R.string.onboarding_permission_allowed_by_install
        OnboardingPermissionStatus.UNAVAILABLE -> R.string.onboarding_permission_unavailable
    }

private val OnboardingContentMaxWidth = 680.dp
