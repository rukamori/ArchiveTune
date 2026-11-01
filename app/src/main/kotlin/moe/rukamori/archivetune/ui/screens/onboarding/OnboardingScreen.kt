/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.onboarding.OnboardingCommunityActionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingEvent
import moe.rukamori.archivetune.onboarding.OnboardingPageId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionAction
import moe.rukamori.archivetune.onboarding.OnboardingPermissionId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionStatus
import moe.rukamori.archivetune.onboarding.OnboardingPermissionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingScreenState
import moe.rukamori.archivetune.onboarding.OnboardingUiState
import moe.rukamori.archivetune.onboarding.OnboardingViewModel

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    onLoginRequested: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val openLogin by rememberUpdatedState(onLoginRequested)
    var requestedPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permission = requestedPermission
            val canRequestAgain =
                permission == null || activity == null ||
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            viewModel.onPermissionResult(permission, granted, canRequestAgain)
            requestedPermission = null
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onSettingsResult()
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    LaunchedEffect(context, lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                try {
                    when (event) {
                        is OnboardingEvent.RequestPermission -> {
                            requestedPermission = event.permission
                            permissionLauncher.launch(event.permission)
                        }
                        OnboardingEvent.OpenInstallPackagesSettings -> {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        }
                        OnboardingEvent.OpenAppSettings -> {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        }
                        OnboardingEvent.OpenLogin -> openLogin()
                        is OnboardingEvent.OpenUri -> {
                            context.startActivity(Intent(Intent.ACTION_VIEW, event.url.toUri()))
                            viewModel.onExternalActionFinished(event)
                        }
                    }
                } catch (_: ActivityNotFoundException) {
                    requestedPermission = null
                    viewModel.onExternalActionFailed(event)
                } catch (_: SecurityException) {
                    requestedPermission = null
                    viewModel.onExternalActionFailed(event)
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onRetry = viewModel::retryLoading,
        onLogin = viewModel::onLogin,
        onPermissionAction = viewModel::onPermissionAction,
        onCommunityAction = viewModel::onCommunityAction,
        onRetryExternalAction = viewModel::retryExternalAction,
        onDismissActionError = viewModel::dismissActionError,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onNext: (OnboardingPageId) -> Unit,
    onBack: (OnboardingPageId) -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    onRetryExternalAction: () -> Unit,
    onDismissActionError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = (state as? OnboardingScreenState.Success)?.uiState
    val page = uiState?.pages?.get(uiState.currentPage)
    val snackbarHostState = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()
    val errorMessage = uiState?.actionErrorResId?.let { stringResource(it) }
    val retryLabel = stringResource(R.string.retry)
    val retryExternalAction by rememberUpdatedState(onRetryExternalAction)
    val dismissActionError by rememberUpdatedState(onDismissActionError)

    LaunchedEffect(errorMessage, uiState?.actionErrorId) {
        if (errorMessage != null) {
            val result =
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = retryLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) retryExternalAction() else dismissActionError()
        }
    }

    BackHandler(enabled = uiState != null && uiState.currentPage > 0) {
        if (uiState != null && page != null && !uiState.isBusy) onBack(page.id)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (uiState != null && page != null) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.onboarding_step, uiState.currentPage + 1, uiState.pages.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        },
                        navigationIcon = {
                            if (uiState.currentPage > 0) {
                                IconButton(onClick = { onBack(page.id) }, enabled = !uiState.isBusy) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_back),
                                        contentDescription = stringResource(R.string.back_button_desc),
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            },
            bottomBar = {
                if (uiState != null && page != null) {
                    OnboardingActions(
                        uiState = uiState,
                        page = page.id,
                        onNext = onNext,
                        onLogin = onLogin,
                        maxHeight = maxHeight / 2,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when (state) {
                OnboardingScreenState.Loading -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        if (LocalAnimationsDisabled.current) {
                            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            LoadingIndicator()
                        }
                    }
                }
                OnboardingScreenState.Empty -> {
                    SetupError(R.string.onboarding_empty_title, onRetry, padding)
                }
                is OnboardingScreenState.Error -> {
                    SetupError(state.messageResId, onRetry, padding)
                }
                is OnboardingScreenState.Success -> {
                    Crossfade(
                        targetState = state.uiState.pages[state.uiState.currentPage].id,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        modifier = Modifier.fillMaxSize().padding(padding),
                        label = "onboardingPage",
                    ) { pageId ->
                        pageStateHolder.SaveableStateProvider(pageId.name) {
                            OnboardingPage(
                                page = pageId,
                                uiState = state.uiState,
                                onPermissionAction = onPermissionAction,
                                onCommunityAction = onCommunityAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupError(
    @StringRes messageResId: Int,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(R.drawable.info), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(messageResId), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun OnboardingActions(
    uiState: OnboardingUiState,
    page: OnboardingPageId,
    onNext: (OnboardingPageId) -> Unit,
    onLogin: () -> Unit,
    maxHeight: Dp,
) {
    val primaryLabel =
        when {
            uiState.isCompleting -> R.string.onboarding_saving
            uiState.completionErrorResId != null && page == OnboardingPageId.COMMUNITY -> R.string.retry
            page == OnboardingPageId.WELCOME -> R.string.onboarding_get_started
            page == OnboardingPageId.LOGIN -> R.string.onboarding_sign_in
            page == OnboardingPageId.COMMUNITY -> R.string.onboarding_finish
            else -> R.string.onboarding_continue
        }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = OnboardingContentMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (page == OnboardingPageId.COMMUNITY) {
                    uiState.completionErrorResId?.let { message ->
                        Text(
                            stringResource(message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                Button(
                    onClick = { if (page == OnboardingPageId.LOGIN) onLogin() else onNext(page) },
                    enabled = !uiState.isBusy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(primaryLabel), textAlign = TextAlign.Center)
                }
                if (page == OnboardingPageId.LOGIN) {
                    TextButton(onClick = { onNext(page) }, enabled = !uiState.isBusy) {
                        Text(stringResource(R.string.onboarding_login_skip))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    page: OnboardingPageId,
    uiState: OnboardingUiState,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val pageModel = uiState.pages.first { it.id == page }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val identitySize = minOf(144.dp, maxHeight / 3)
        Column(
            modifier =
                Modifier
                    .widthIn(max = OnboardingContentMaxWidth + 48.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (page == OnboardingPageId.WELCOME) {
                Icon(
                    painter = painterResource(R.drawable.app_icon_small),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(identitySize),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(pageModel.titleResId),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    text = stringResource(pageModel.subtitleResId),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            when (page) {
                OnboardingPageId.WELCOME -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(uiState.variantLabelResId),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.onboarding_version_label, uiState.versionName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OnboardingPageId.PERMISSIONS -> {
                    PermissionsContent(uiState = uiState, onPermissionAction = onPermissionAction)
                }
                OnboardingPageId.LOGIN -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.loginBenefits.forEach { benefit ->
                            ListItem(
                                headlineContent = { Text(stringResource(benefit.titleResId)) },
                                supportingContent = { Text(stringResource(benefit.descriptionResId)) },
                                leadingContent = { Icon(painterResource(benefit.iconResId), contentDescription = null) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.onboarding_login_optional),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                OnboardingPageId.COMMUNITY -> {
                    val communities = uiState.communityActions.filter { it.id != "donate" }
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        communities.forEachIndexed { index, action ->
                            SegmentedListItem(
                                onClick = { onCommunityAction(action) },
                                enabled = !uiState.isBusy,
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = communities.size),
                                leadingContent = { Icon(painterResource(action.iconResId), contentDescription = null) },
                                supportingContent = { Text(stringResource(action.descriptionResId)) },
                                trailingContent = { Icon(painterResource(R.drawable.arrow_forward), contentDescription = null) },
                                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(action.titleResId))
                            }
                        }
                    }
                    val donation = uiState.communityActions.first { it.id == "donate" }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.onboarding_support_optional),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalButton(
                            onClick = { onCommunityAction(donation) },
                            enabled = !uiState.isBusy,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(donation.iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(donation.titleResId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsContent(
    uiState: OnboardingUiState,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var explanationExpanded by rememberSaveable { mutableStateOf(false) }
    val primary =
        uiState.permissions.filter {
            it.id == OnboardingPermissionId.NOTIFICATIONS || it.id == OnboardingPermissionId.LOCAL_AUDIO
        }
    val secondary =
        uiState.permissions.filter {
            it.id == OnboardingPermissionId.MICROPHONE || it.id == OnboardingPermissionId.BLUETOOTH_CONNECT ||
                it.id == OnboardingPermissionId.BLUETOOTH_SCAN || it.id == OnboardingPermissionId.APP_INSTALLATION
        }
    val informational =
        uiState.permissions.filter {
            it.id == OnboardingPermissionId.NETWORK || it.id == OnboardingPermissionId.PLAYBACK_SERVICE ||
                it.id == OnboardingPermissionId.AUDIO_SETTINGS || it.id == OnboardingPermissionId.DEVICE_AUDIO_CAPTURE
        }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        primary.forEach { permission -> PermissionItem(permission, !uiState.isBusy, onPermissionAction) }
        PermissionDisclosure(
            titleResId = R.string.onboarding_more_permissions,
            expanded = moreExpanded,
            onClick = { moreExpanded = !moreExpanded },
        )
        if (moreExpanded) {
            secondary.forEach { permission -> PermissionItem(permission, !uiState.isBusy, onPermissionAction) }
        }
        PermissionDisclosure(
            titleResId = R.string.onboarding_how_access_works,
            expanded = explanationExpanded,
            onClick = { explanationExpanded = !explanationExpanded },
        )
        if (explanationExpanded) {
            informational.forEach { permission -> PermissionItem(permission, !uiState.isBusy, onPermissionAction) }
        }
    }
}

@Composable
private fun PermissionDisclosure(
    @StringRes titleResId: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val stateLabel = stringResource(if (expanded) R.string.onboarding_expanded else R.string.onboarding_collapsed)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        trailingContent = {
            Icon(painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more), contentDescription = null)
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = stateLabel },
    ) {
        Text(stringResource(titleResId), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PermissionItem(
    permission: OnboardingPermissionUiModel,
    enabled: Boolean,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val statusLabel =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> R.string.permission_status_allowed
            OnboardingPermissionStatus.NEEDS_ACTION -> R.string.onboarding_permission_optional
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> R.string.onboarding_permission_allowed_by_install
            OnboardingPermissionStatus.APPROVAL_AT_USE -> R.string.onboarding_permission_approval_at_use
            OnboardingPermissionStatus.BLOCKED -> R.string.onboarding_permission_blocked
            OnboardingPermissionStatus.UNAVAILABLE -> R.string.onboarding_permission_unavailable
        }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(permission.titleResId)) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(permission.descriptionResId))
                        Text(
                            text = stringResource(statusLabel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                },
                leadingContent = { Icon(painterResource(permission.iconResId), contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
            permission.action?.let { action ->
                val label =
                    if (action is OnboardingPermissionAction.RequestRuntimePermission) {
                        R.string.allow
                    } else {
                        R.string.onboarding_open_settings
                    }
                val actionDescription =
                    stringResource(
                        R.string.onboarding_permission_action_description,
                        stringResource(label),
                        stringResource(permission.titleResId),
                    )
                TextButton(
                    onClick = { onPermissionAction(action) },
                    enabled = enabled,
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 8.dp)
                            .semantics { contentDescription = actionDescription },
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

private val OnboardingContentMaxWidth = 560.dp
