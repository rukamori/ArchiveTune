/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.viewmodels.CommitDayGroup
import moe.rukamori.archivetune.viewmodels.CommitHistoryState
import moe.rukamori.archivetune.viewmodels.CommitUiModel
import moe.rukamori.archivetune.viewmodels.UpdateSettingsAction
import moe.rukamori.archivetune.viewmodels.UpdateSettingsEvent
import moe.rukamori.archivetune.viewmodels.UpdateSettingsScreenState
import moe.rukamori.archivetune.viewmodels.UpdateSettingsViewModel

private enum class UpdateStatus {
    Unchecked,
    Current,
    Available,
}

private const val EntranceStaggerMillis = 70L
private val EntranceOffset = 40.dp
private val CommitAvatarSize = 40.dp
private val CommitRowVerticalPadding = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    latestVersion: String?,
    viewModel: UpdateSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = appBarScrollBehavior()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useWideLayout =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )
    val maximumContentWidth = if (useWideLayout) 1_040.dp else 680.dp
    val updateSettingsState by viewModel.state.collectAsStateWithLifecycle()
    val commitHistoryState by viewModel.commitHistory.collectAsStateWithLifecycle()
    val updateSettingsModel =
        when (val state = updateSettingsState) {
            is UpdateSettingsScreenState.Success -> state.model
            is UpdateSettingsScreenState.Error -> state.lastKnownModel
            UpdateSettingsScreenState.Loading,
            UpdateSettingsScreenState.Empty,
            -> null
        }
    val automaticUpdateChecksEnabled = updateSettingsModel?.automaticChecksEnabled ?: true
    val enableUpdateNotification = updateSettingsModel?.notificationsEnabled ?: false
    val updateSettingsControlsEnabled = updateSettingsState is UpdateSettingsScreenState.Success
    val updateSettingsErrorRes = (updateSettingsState as? UpdateSettingsScreenState.Error)?.messageRes
    val onUpdateSettingsAction = remember(viewModel) { viewModel::onAction }
    var isHistoryExpanded by rememberSaveable { mutableStateOf(true) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val updateStatus =
        when {
            latestVersion == null -> UpdateStatus.Unchecked
            BuildConfig.UPDATER_AVAILABLE &&
                Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME) -> UpdateStatus.Available
            else -> UpdateStatus.Current
        }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val onJoinEarlyAccess = remember(onUpdateSettingsAction) {
        { onUpdateSettingsAction(UpdateSettingsAction.JoinEarlyAccess) }
    }
    val onRetryCommitHistory = remember(onUpdateSettingsAction) {
        { onUpdateSettingsAction(UpdateSettingsAction.RetryCommitHistory) }
    }

    LaunchedEffect(viewModel, lifecycleOwner, uriHandler) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is UpdateSettingsEvent.OpenUri -> {
                        try {
                            uriHandler.openUri(event.uri)
                        } catch (exception: ActivityNotFoundException) {
                            reportException(exception)
                            onUpdateSettingsAction(UpdateSettingsAction.EarlyAccessLinkFailed)
                        } catch (exception: IllegalArgumentException) {
                            reportException(exception)
                            onUpdateSettingsAction(UpdateSettingsAction.EarlyAccessLinkFailed)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(updateSettingsErrorRes) {
        val messageRes = updateSettingsErrorRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(messageRes))
        onUpdateSettingsAction(UpdateSettingsAction.DismissError)
    }

    val onCheckForUpdate: () -> Unit = {
        navController.navigate("$AppUpdateRoute?forceRefresh=true") {
            launchSingleTop = true
        }
    }
    val onOpenChangelog: () -> Unit = {
        navController.navigate("settings/changelog")
    }
    val onCommitClick: (CommitUiModel) -> Unit = { commit ->
        try {
            uriHandler.openUri(commit.url)
        } catch (exception: ActivityNotFoundException) {
            reportException(exception)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_unknown))
            }
        } catch (exception: IllegalArgumentException) {
            reportException(exception)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_unknown))
            }
        }
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                onUpdateSettingsAction(UpdateSettingsAction.SetNotificationsEnabled(true))
            }
        }
    val onUpdateNotificationChange: (Boolean) -> Unit = { enabled ->
        if (
            enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onUpdateSettingsAction(UpdateSettingsAction.SetNotificationsEnabled(enabled))
        }
    }
    val onAutomaticUpdateChecksChange: (Boolean) -> Unit = { enabled ->
        onUpdateSettingsAction(UpdateSettingsAction.SetAutomaticChecksEnabled(enabled))
    }

    val motionScheme = MaterialTheme.motionScheme
    val contentModifier =
        Modifier
            .fillMaxWidth()
            .widthIn(max = maximumContentWidth)

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.updates),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    start = SettingsDimensions.ScreenHorizontalPadding,
                    top = 8.dp,
                    end = SettingsDimensions.ScreenHorizontalPadding,
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (useWideLayout) {
                item(key = "overview", contentType = "overview") {
                    val entrance = rememberEntranceProgress(order = 0)
                    Row(
                        modifier = contentModifier.entranceReveal(entrance),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        UpdateHero(
                            status = updateStatus,
                            currentVersion = BuildConfig.VERSION_NAME,
                            latestVersion = latestVersion,
                            onPrimaryAction = onCheckForUpdate,
                            onOpenChangelog = onOpenChangelog,
                            modifier = Modifier.weight(1f),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            UpdatePreferencesSection(
                                automaticUpdateChecksEnabled = automaticUpdateChecksEnabled,
                                enableUpdateNotification = enableUpdateNotification,
                                controlsEnabled = updateSettingsControlsEnabled,
                                onAutomaticUpdateChecksChange = onAutomaticUpdateChecksChange,
                                onUpdateNotificationChange = onUpdateNotificationChange,
                            )
                            EarlyAccessCard(onJoin = onJoinEarlyAccess)
                        }
                    }
                }
            } else {
                item(key = "hero", contentType = "hero") {
                    UpdateHero(
                        status = updateStatus,
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestVersion = latestVersion,
                        onPrimaryAction = onCheckForUpdate,
                        onOpenChangelog = onOpenChangelog,
                        modifier = contentModifier.entranceReveal(rememberEntranceProgress(order = 0)),
                    )
                }
                item(key = "preferences", contentType = "preferences") {
                    UpdatePreferencesSection(
                        automaticUpdateChecksEnabled = automaticUpdateChecksEnabled,
                        enableUpdateNotification = enableUpdateNotification,
                        controlsEnabled = updateSettingsControlsEnabled,
                        onAutomaticUpdateChecksChange = onAutomaticUpdateChecksChange,
                        onUpdateNotificationChange = onUpdateNotificationChange,
                        modifier = contentModifier.entranceReveal(rememberEntranceProgress(order = 1)),
                    )
                }
                item(key = "early_access", contentType = "early_access") {
                    EarlyAccessCard(
                        onJoin = onJoinEarlyAccess,
                        modifier = contentModifier.entranceReveal(rememberEntranceProgress(order = 2)),
                    )
                }
            }

            item(key = "history_header", contentType = "history_header") {
                CommitHistoryHeader(
                    state = commitHistoryState,
                    isExpanded = isHistoryExpanded,
                    onToggleExpanded = { isHistoryExpanded = !isHistoryExpanded },
                    modifier =
                        contentModifier
                            .padding(top = 8.dp)
                            .entranceReveal(rememberEntranceProgress(order = if (useWideLayout) 1 else 3)),
                )
            }

            if (isHistoryExpanded) {
                when (val history = commitHistoryState) {
                    CommitHistoryState.Loading -> {
                        item(key = "history_loading", contentType = "history_status") {
                            CommitHistoryLoading(modifier = historyItemModifier(contentModifier, motionScheme))
                        }
                    }

                    CommitHistoryState.Empty -> {
                        item(key = "history_empty", contentType = "history_status") {
                            CommitHistoryMessage(
                                iconRes = R.drawable.github,
                                message = stringResource(R.string.updates_no_commits),
                                modifier = historyItemModifier(contentModifier, motionScheme),
                            )
                        }
                    }

                    CommitHistoryState.Error -> {
                        item(key = "history_error", contentType = "history_status") {
                            CommitHistoryMessage(
                                iconRes = R.drawable.error,
                                message = stringResource(R.string.updates_commits_error),
                                isError = true,
                                onRetry = onRetryCommitHistory,
                                modifier = historyItemModifier(contentModifier, motionScheme),
                            )
                        }
                    }

                    is CommitHistoryState.Content -> {
                        items(
                            items = history.groups,
                            key = { group -> "day_${group.dayStartMillis}_${group.commits.first().sha}" },
                            contentType = { "commit_day" },
                        ) { group ->
                            CommitDayCard(
                                group = group,
                                onCommitClick = onCommitClick,
                                modifier = historyItemModifier(contentModifier, motionScheme),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberEntranceProgress(order: Int): Animatable<Float, AnimationVector1D> {
    val animationsDisabled = LocalAnimationsDisabled.current
    var hasEntered by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (hasEntered || animationsDisabled) 1f else 0f) }
    val animationSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(progress) {
        if (!hasEntered) {
            if (!animationsDisabled) {
                delay(EntranceStaggerMillis * order)
                progress.animateTo(1f, animationSpec)
            } else {
                progress.snapTo(1f)
            }
            hasEntered = true
        }
    }
    return progress
}

private fun LazyItemScope.historyItemModifier(
    base: Modifier,
    motionScheme: MotionScheme,
): Modifier =
    base.animateItem(
        fadeInSpec = motionScheme.defaultEffectsSpec(),
        placementSpec = motionScheme.defaultSpatialSpec(),
        fadeOutSpec = motionScheme.fastEffectsSpec(),
    )

internal fun Modifier.entranceReveal(progress: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * EntranceOffset.toPx()
    }

@Composable
private fun UpdateHero(
    status: UpdateStatus,
    currentVersion: String,
    latestVersion: String?,
    onPrimaryAction: () -> Unit,
    onOpenChangelog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val isAvailable = status == UpdateStatus.Available
    val containerColor by animateColorAsState(
        targetValue = if (isAvailable) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "heroContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isAvailable) colorScheme.onPrimaryContainer else colorScheme.onSurface,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "heroContent",
    )
    val supportingColor by animateColorAsState(
        targetValue = if (isAvailable) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "heroSupporting",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                UpdateStatusBadge(status = status)
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        (
                            fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 }
                        ) togetherWith (
                            fadeOut(motionScheme.fastEffectsSpec()) +
                                slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 3 }
                        ) using SizeTransform(clip = false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "heroStatus",
                ) { targetStatus ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text =
                                stringResource(
                                    when (targetStatus) {
                                        UpdateStatus.Unchecked -> R.string.updates_status_unchecked_title
                                        UpdateStatus.Current -> R.string.updates_status_current_title
                                        UpdateStatus.Available -> R.string.updates_status_available_title
                                    },
                                ),
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text =
                                stringResource(
                                    when (targetStatus) {
                                        UpdateStatus.Unchecked -> R.string.app_update_not_checked
                                        UpdateStatus.Current -> R.string.updates_status_current
                                        UpdateStatus.Available -> R.string.updates_status_available_desc
                                    },
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = supportingColor,
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VersionTile(
                    label = stringResource(R.string.current_version),
                    version = currentVersion,
                    emphasized = false,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = supportingColor,
                    modifier = Modifier.size(20.dp),
                )
                VersionTile(
                    label = stringResource(R.string.updates_latest_version),
                    version = latestVersion ?: stringResource(R.string.updates_latest_version_unknown),
                    emphasized = isAvailable,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val primaryHeight = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = onPrimaryAction,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = primaryHeight),
                    shapes = ButtonDefaults.shapesFor(primaryHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(primaryHeight, hasStartIcon = true),
                ) {
                    AnimatedContent(
                        targetState = isAvailable,
                        transitionSpec = {
                            (fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.85f)) togetherWith
                                fadeOut(motionScheme.fastEffectsSpec())
                        },
                        label = "heroPrimaryAction",
                    ) { available ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(if (available) R.drawable.download else R.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(primaryHeight)),
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(primaryHeight)))
                            Text(
                                text = stringResource(if (available) R.string.updates_get_update else R.string.check_for_update),
                                style = ButtonDefaults.textStyleFor(primaryHeight),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = onOpenChangelog,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    shapes =
                        ButtonShapes(
                            shape = MaterialTheme.shapes.large,
                            pressedShape = MaterialTheme.shapes.medium,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.newspaper),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.view_changelog))
                }
            }

            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_channel),
                    style = MaterialTheme.typography.labelMedium,
                    color = supportingColor,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.new_release),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.update_channel_release),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.update_channel_release_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusBadge(
    status: UpdateStatus,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val isAvailable = status == UpdateStatus.Available
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val morphProgress by animateFloatAsState(
        targetValue = if (isAvailable) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "badgeMorph",
    )
    val badgeColor by animateColorAsState(
        targetValue =
            when (status) {
                UpdateStatus.Available -> colorScheme.primary
                UpdateStatus.Current -> colorScheme.secondaryContainer
                UpdateStatus.Unchecked -> colorScheme.tertiaryContainer
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "badgeColor",
    )
    val iconColor by animateColorAsState(
        targetValue =
            when (status) {
                UpdateStatus.Available -> colorScheme.onPrimary
                UpdateStatus.Current -> colorScheme.onSecondaryContainer
                UpdateStatus.Unchecked -> colorScheme.onTertiaryContainer
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "badgeIcon",
    )
    val rotation: State<Float>? =
        if (isAvailable && !LocalAnimationsDisabled.current) {
            rememberInfiniteTransition(label = "badgeSpin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing)),
                label = "badgeRotation",
            )
        } else {
            null
        }

    Box(
        modifier = modifier.size(88.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation?.value ?: 0f }
                    .background(badgeColor, EditorialMorphShape(morph, morphProgress)),
        )
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.5f)) togetherWith
                    (fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.5f))
            },
            label = "badgeIcon",
        ) { targetStatus ->
            Icon(
                painter =
                    painterResource(
                        when (targetStatus) {
                            UpdateStatus.Available -> R.drawable.download
                            UpdateStatus.Current -> R.drawable.check
                            UpdateStatus.Unchecked -> R.drawable.update
                        },
                    ),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun VersionTile(
    label: String,
    version: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (emphasized) colorScheme.primary else colorScheme.surfaceContainerLowest,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "versionTileContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (emphasized) colorScheme.onPrimary else colorScheme.onSurface,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "versionTileContent",
    )
    val labelColor by animateColorAsState(
        targetValue = if (emphasized) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "versionTileLabel",
    )

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = version,
                transitionSpec = {
                    (fadeIn(motionScheme.defaultEffectsSpec()) + slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 }) togetherWith
                        (fadeOut(motionScheme.fastEffectsSpec()) + slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 2 })
                },
                label = "versionTileValue",
            ) { targetVersion ->
                Text(
                    text = targetVersion,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .padding(start = 4.dp)
                .semantics { heading() },
    )
}

@Composable
private fun UpdatePreferencesSection(
    automaticUpdateChecksEnabled: Boolean,
    enableUpdateNotification: Boolean,
    controlsEnabled: Boolean,
    onAutomaticUpdateChecksChange: (Boolean) -> Unit,
    onUpdateNotificationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(text = stringResource(R.string.updates_preferences))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            PreferenceToggleItem(
                index = 0,
                count = 2,
                checked = automaticUpdateChecksEnabled,
                enabled = controlsEnabled,
                iconRes = R.drawable.sync,
                title = stringResource(R.string.automatic_update_checks),
                description = stringResource(R.string.automatic_update_checks_desc),
                onCheckedChange = onAutomaticUpdateChecksChange,
            )
            PreferenceToggleItem(
                index = 1,
                count = 2,
                checked = enableUpdateNotification,
                enabled = controlsEnabled,
                iconRes = R.drawable.new_release,
                title = stringResource(R.string.enable_update_notification),
                description = stringResource(R.string.enable_update_notification_channel_desc),
                onCheckedChange = onUpdateNotificationChange,
            )
        }
    }
}

@Composable
private fun PreferenceToggleItem(
    index: Int,
    count: Int,
    checked: Boolean,
    enabled: Boolean,
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            ToggleStateIcon(
                iconRes = iconRes,
                checked = checked,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent =
                    if (checked) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        },
        supportingContent = {
            Text(text = description)
        },
        content = {
            Text(text = title)
        },
    )
}

@Composable
private fun ToggleStateIcon(
    @DrawableRes iconRes: Int,
    checked: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (checked) 22.dp else 12.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "toggleIconCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (checked) colorScheme.secondaryContainer else colorScheme.surfaceContainerHighest,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "toggleIconContainer",
    )
    val iconColor by animateColorAsState(
        targetValue = if (checked) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "toggleIconTint",
    )

    Box(
        modifier =
            Modifier
                .size(44.dp)
                .background(containerColor, RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun EarlyAccessCard(
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val badgeShape = remember { MaterialShapes.Clover4Leaf.toShape() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.tertiaryContainer,
        contentColor = colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(colorScheme.tertiary, badgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.experiment),
                    contentDescription = null,
                    tint = colorScheme.onTertiary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.early_access_programs),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.early_access_programs_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onJoin,
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .heightIn(min = 48.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colorScheme.tertiary,
                            contentColor = colorScheme.onTertiary,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.discord),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.join))
                }
            }
        }
    }
}

@Composable
private fun CommitHistoryHeader(
    state: CommitHistoryState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "historyChevron",
    )
    val expansionState =
        stringResource(if (isExpanded) R.string.updates_history_expanded else R.string.updates_history_collapsed)
    val supportingText =
        when (state) {
            CommitHistoryState.Loading -> stringResource(R.string.updates_loading_commits)
            CommitHistoryState.Empty -> stringResource(R.string.updates_no_commits)
            CommitHistoryState.Error -> stringResource(R.string.updates_commits_error)
            is CommitHistoryState.Content -> stringResource(R.string.updates_recent_commits_count, state.commitCount)
        }

    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .semantics { stateDescription = expansionState }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.updates_commit_branch),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.recent_commits),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            AnimatedContent(
                targetState = supportingText,
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                },
                label = "historySupporting",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }
    }
}

@Composable
private fun CommitHistoryLoading(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(48.dp))
            Text(
                text = stringResource(R.string.updates_loading_commits),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CommitHistoryMessage(
    @DrawableRes iconRes: Int,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(
                            color = if (isError) colorScheme.errorContainer else colorScheme.surfaceContainerHighest,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (isError) colorScheme.onErrorContainer else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun CommitDayCard(
    group: CommitDayGroup,
    onCommitClick: (CommitUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val unknownDayLabel = stringResource(R.string.updates_commit_date_unknown)
    val dayLabel =
        remember(group.dayStartMillis, unknownDayLabel) {
            group.dayStartMillis?.let { dayStartMillis ->
                DateUtils
                    .getRelativeTimeSpanString(
                        dayStartMillis,
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS,
                    ).toString()
            } ?: unknownDayLabel
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Surface(
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        .semantics { heading() },
                shape = CircleShape,
                color = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            group.commits.forEachIndexed { index, commit ->
                key(commit.sha) {
                    CommitTimelineRow(
                        commit = commit,
                        isFirst = index == 0,
                        isLast = index == group.commits.lastIndex,
                        onClick = { onCommitClick(commit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitTimelineRow(
    commit: CommitUiModel,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val railColor = colorScheme.outlineVariant
    val commitTime =
        remember(commit.committedAtMillis, context) {
            commit.committedAtMillis?.let { millis ->
                DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
            }
        }
    val metaText =
        if (commitTime != null) {
            stringResource(R.string.updates_commit_meta, commit.author, commitTime)
        } else {
            commit.author
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(
                    onClickLabel = stringResource(R.string.updates_open_commit),
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(CommitAvatarSize)
                    .fillMaxHeight()
                    .drawBehind {
                        val centerX = size.width / 2f
                        val avatarCenterY = (CommitRowVerticalPadding + CommitAvatarSize / 2).toPx()
                        val top = if (isFirst) avatarCenterY else 0f
                        val bottom = if (isLast) avatarCenterY else size.height
                        if (bottom > top) {
                            drawLine(
                                color = railColor,
                                start = Offset(centerX, top),
                                end = Offset(centerX, bottom),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    },
            contentAlignment = Alignment.TopCenter,
        ) {
            CommitAvatar(
                avatarUrl = commit.avatarUrl,
                modifier = Modifier.padding(top = CommitRowVerticalPadding),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = CommitRowVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.primary,
                ) {
                    Text(
                        text = commit.sha,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CommitAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(CommitAvatarSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.github),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
