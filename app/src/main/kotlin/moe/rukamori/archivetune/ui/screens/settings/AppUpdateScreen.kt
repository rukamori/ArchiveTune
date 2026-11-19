/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.updates.AppUpdateRelease
import moe.rukamori.archivetune.utils.AppUpdateInstaller
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.viewmodels.AppUpdateEvent
import moe.rukamori.archivetune.viewmodels.AppUpdateState
import moe.rukamori.archivetune.viewmodels.AppUpdateTransfer
import moe.rukamori.archivetune.viewmodels.AppUpdateViewModel
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Date
import kotlin.math.roundToInt

const val AppUpdateRoute = "app_update"

private val StatusBadgeSize = 96.dp
private val CompactStatusBadgeSize = 72.dp
private val MaximumSinglePaneWidth = 680.dp
private val MaximumTwoPaneWidth = 1_200.dp

@Composable
fun AppUpdateScreen(
    navController: NavController,
    onReleaseChecked: (AppUpdateRelease?) -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateRequired by viewModel.updateRequired.collectAsStateWithLifecycle()
    val mandatory = updateRequired || (state as? AppUpdateState.Success)?.release?.isMandatory == true
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val releaseChecked by rememberUpdatedState(onReleaseChecked)
    val checkedRelease = (state as? AppUpdateState.Success)?.release
    LaunchedEffect(checkedRelease, state is AppUpdateState.Empty) {
        if (checkedRelease != null) releaseChecked(checkedRelease)
        else if (state is AppUpdateState.Empty) releaseChecked(null)
    }
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.externalActionFinished()
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.installPermissionResult(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls(),
        )
    }
    LaunchedEffect(viewModel, lifecycleOwner, navController, context, uriHandler) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                when (event) {
                    AppUpdateEvent.NavigateBack -> navController.navigateUp()
                    is AppUpdateEvent.OpenReleasePage,
                    is AppUpdateEvent.InstallApk,
                    -> {
                        val errorRes = if (event is AppUpdateEvent.OpenReleasePage) {
                            R.string.app_update_open_release_failed
                        } else {
                            R.string.app_update_install_failed
                        }
                        try {
                            when (event) {
                                is AppUpdateEvent.OpenReleasePage -> {
                                    uriHandler.openUri(event.url)
                                    viewModel.externalActionFinished()
                                }
                                is AppUpdateEvent.InstallApk -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                        installPermission.launch(
                                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                                        )
                                    } else {
                                        installer.launch(AppUpdateInstaller.createInstallIntent(context, event.file))
                                    }
                                }
                                else -> Unit
                            }
                        } catch (exception: ActivityNotFoundException) {
                            reportException(exception)
                            viewModel.externalActionFinished(errorRes)
                        } catch (exception: SecurityException) {
                            reportException(exception)
                            viewModel.externalActionFinished(errorRes)
                        } catch (exception: IllegalArgumentException) {
                            reportException(exception)
                            viewModel.externalActionFinished(errorRes)
                        }
                    }
                }
            }
        }
    }
    BackHandler { viewModel.back() }
    AppUpdateContent(
        state = state,
        mandatory = mandatory,
        animationsDisabled = LocalAnimationsDisabled.current || (durationScale?.scaleFactor ?: 1f) == 0f,
        onBack = viewModel::back,
        onCheck = { viewModel.checkForUpdate() },
        onPrimaryAction = viewModel::primaryAction,
        onCancelDownload = { viewModel.cancelDownload() },
    )
    if (!mandatory && (state as? AppUpdateState.Success)?.confirmLeave == true) {
        AlertDialog(
            onDismissRequest = viewModel::keepDownloading,
            icon = { Icon(painterResource(R.drawable.download), contentDescription = null) },
            title = { Text(stringResource(R.string.app_update_cancel_title)) },
            text = { Text(stringResource(R.string.app_update_cancel_description)) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDownload(leave = true) }) {
                    Text(stringResource(R.string.app_update_cancel_and_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::keepDownloading) {
                    Text(stringResource(R.string.app_update_keep_downloading))
                }
            },
        )
    }
}

private enum class UpdatePhase {
    Checking,
    CheckFailed,
    Empty,
    ActionFailed,
    Downloading,
    Ready,
    Required,
    Available,
    Current,
}

private data class UpdateHeader(
    val phase: UpdatePhase,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:DrawableRes val icon: Int,
)

private data class PhaseColors(
    val container: Color,
    val content: Color,
    val supporting: Color,
    val badge: Color,
    val onBadge: Color,
)

private fun AppUpdateState.toHeader(mandatory: Boolean): UpdateHeader {
    val success = this as? AppUpdateState.Success
    val transfer = success?.transfer
    return when {
        this is AppUpdateState.Loading ->
            UpdateHeader(UpdatePhase.Checking, R.string.app_update_checking, R.string.app_update_checking_description, R.drawable.update)
        this is AppUpdateState.Error ->
            UpdateHeader(UpdatePhase.CheckFailed, R.string.app_update_check_failed, R.string.app_update_check_failed_description, R.drawable.error)
        this is AppUpdateState.Empty ->
            UpdateHeader(UpdatePhase.Empty, R.string.app_update_empty, R.string.app_update_empty_description, R.drawable.info)
        success?.errorRes != null ->
            UpdateHeader(
                phase = UpdatePhase.ActionFailed,
                title = when (success.errorRes) {
                    R.string.app_update_download_failed -> R.string.app_update_download_failed_title
                    R.string.app_update_permission_required -> R.string.app_update_permission_title
                    R.string.app_update_open_release_failed -> R.string.app_update_open_release_failed_title
                    else -> R.string.app_update_install_failed_title
                },
                description = success.errorRes,
                icon = R.drawable.error,
            )
        transfer is AppUpdateTransfer.Downloading ->
            UpdateHeader(UpdatePhase.Downloading, R.string.app_update_downloading, R.string.app_update_downloading_description, R.drawable.download)
        transfer is AppUpdateTransfer.Ready ->
            UpdateHeader(UpdatePhase.Ready, R.string.app_update_ready, R.string.app_update_ready_description, R.drawable.check)
        mandatory && success?.release?.isUpdateAvailable == true ->
            UpdateHeader(UpdatePhase.Required, R.string.app_update_required, R.string.app_update_required_description, R.drawable.update)
        success?.release?.isUpdateAvailable == true ->
            UpdateHeader(UpdatePhase.Available, R.string.app_update_available, R.string.app_update_available_description, R.drawable.new_release)
        else ->
            UpdateHeader(UpdatePhase.Current, R.string.app_update_current, R.string.updates_status_current, R.drawable.check)
    }
}

private fun UpdatePhase.badgePolygon(): RoundedPolygon =
    when (this) {
        UpdatePhase.Checking,
        UpdatePhase.Downloading,
        -> MaterialShapes.Circle
        UpdatePhase.CheckFailed,
        UpdatePhase.ActionFailed,
        -> MaterialShapes.Cookie4Sided
        UpdatePhase.Empty -> MaterialShapes.Clover8Leaf
        UpdatePhase.Ready -> MaterialShapes.Cookie9Sided
        UpdatePhase.Required -> MaterialShapes.Burst
        UpdatePhase.Available -> MaterialShapes.SoftBurst
        UpdatePhase.Current -> MaterialShapes.Cookie12Sided
    }

@Composable
private fun UpdatePhase.colors(): PhaseColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        UpdatePhase.CheckFailed,
        UpdatePhase.ActionFailed,
        -> PhaseColors(scheme.errorContainer, scheme.onErrorContainer, scheme.onErrorContainer, scheme.error, scheme.onError)
        UpdatePhase.Required,
        UpdatePhase.Available,
        -> PhaseColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
        UpdatePhase.Ready ->
            PhaseColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary, scheme.onTertiary)
        UpdatePhase.Downloading ->
            PhaseColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.onSecondaryContainer, scheme.primary, scheme.onPrimary)
        UpdatePhase.Empty ->
            PhaseColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.onSurfaceVariant, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        UpdatePhase.Checking,
        UpdatePhase.Current,
        -> PhaseColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.onSurfaceVariant, scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
}

@Composable
private fun <T> effectsSpec(animationsDisabled: Boolean): FiniteAnimationSpec<T> =
    if (animationsDisabled) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

@Composable
private fun AppUpdateContent(
    state: AppUpdateState,
    mandatory: Boolean,
    animationsDisabled: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onPrimaryAction: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val compactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val expandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val insets = LocalPlayerAwareWindowInsets.current
    val success = state as? AppUpdateState.Success
    val release = success?.release
    val showReleaseNotes = release?.isUpdateAvailable == true
    val useTwoPane = expandedWidth && showReleaseNotes
    val statusListState = rememberLazyListState()
    val notesListState = rememberLazyListState()
    LaunchedEffect(success?.errorRes) {
        if (success?.errorRes != null) statusListState.scrollToItem(0)
    }
    val downloading = success?.transfer as? AppUpdateTransfer.Downloading
    val header = state.toHeader(mandatory)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            val title: @Composable () -> Unit = { Text(stringResource(R.string.app_update_title)) }
            val navigationIcon: @Composable () -> Unit = {
                if (!mandatory) {
                    FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                }
            }
            val colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            if (compactHeight) {
                TopAppBar(title = title, navigationIcon = navigationIcon, colors = colors)
            } else {
                MediumFlexibleTopAppBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            UpdateActionBar(
                state = state,
                mandatory = mandatory,
                compactHeight = compactHeight,
                animationsDisabled = animationsDisabled,
                maximumContentWidth = if (useTwoPane) MaximumTwoPaneWidth else MaximumSinglePaneWidth,
                onBack = onBack,
                onCheck = onCheck,
                onPrimaryAction = onPrimaryAction,
                onCancelDownload = onCancelDownload,
                modifier = Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (useTwoPane && release != null) {
                Row(
                    modifier = Modifier.widthIn(max = MaximumTwoPaneWidth).fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdatePane(listState = statusListState, modifier = Modifier.weight(1f)) {
                        statusItems(header, state, release, downloading, mandatory, compactHeight, animationsDisabled)
                    }
                    UpdatePane(listState = notesListState, modifier = Modifier.weight(1.25f)) {
                        item(key = "release_notes", contentType = "release_notes") {
                            ReleaseNotesSection(
                                notes = release.notes,
                                modifier = Modifier.entranceReveal(rememberEntranceProgress(order = 1)),
                            )
                        }
                    }
                }
            } else {
                UpdatePane(
                    listState = statusListState,
                    modifier = Modifier.widthIn(max = MaximumSinglePaneWidth),
                ) {
                    statusItems(header, state, release, downloading, mandatory, compactHeight, animationsDisabled)
                    if (showReleaseNotes && release != null) {
                        item(key = "release_notes", contentType = "release_notes") {
                            ReleaseNotesSection(
                                notes = release.notes,
                                modifier = Modifier.entranceReveal(rememberEntranceProgress(order = 2)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePane(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

private fun LazyListScope.statusItems(
    header: UpdateHeader,
    state: AppUpdateState,
    release: AppUpdateRelease?,
    downloading: AppUpdateTransfer.Downloading?,
    mandatory: Boolean,
    compactHeight: Boolean,
    animationsDisabled: Boolean,
) {
    item(key = "status", contentType = "status") {
        UpdateStatusHero(
            header = header,
            downloading = downloading,
            compactHeight = compactHeight,
            animationsDisabled = animationsDisabled,
            modifier = Modifier.entranceReveal(rememberEntranceProgress(order = 0)),
        )
    }
    item(key = "versions", contentType = "versions") {
        UpdateVersionDetails(
            release = release,
            mandatory = mandatory,
            showReleaseFacts = state is AppUpdateState.Success && release?.isUpdateAvailable == true,
            modifier = Modifier.entranceReveal(rememberEntranceProgress(order = 1)),
        )
    }
}

@Composable
private fun UpdateStatusHero(
    header: UpdateHeader,
    downloading: AppUpdateTransfer.Downloading?,
    compactHeight: Boolean,
    animationsDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val phaseColors = header.phase.colors()
    val containerColor by animateColorAsState(phaseColors.container, effectsSpec(animationsDisabled), label = "heroContainer")
    val contentColor by animateColorAsState(phaseColors.content, effectsSpec(animationsDisabled), label = "heroContent")
    val supportingColor by animateColorAsState(phaseColors.supporting, effectsSpec(animationsDisabled), label = "heroSupporting")
    val badgeSize = if (compactHeight) CompactStatusBadgeSize else StatusBadgeSize

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = containerColor,
        contentColor = contentColor,
    ) {
        val text: @Composable (Modifier) -> Unit = { textModifier ->
            AnimatedContent(
                targetState = header,
                transitionSpec = {
                    if (animationsDisabled) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        (
                            fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 }
                        ) togetherWith (
                            fadeOut(motionScheme.fastEffectsSpec()) +
                                slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 3 }
                        ) using SizeTransform(clip = false)
                    }
                },
                contentKey = { it.title to it.description },
                modifier = textModifier.semantics { liveRegion = LiveRegionMode.Polite },
                label = "heroText",
            ) { targetHeader ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(targetHeader.title),
                        style = if (compactHeight) {
                            MaterialTheme.typography.headlineSmallEmphasized
                        } else {
                            MaterialTheme.typography.headlineMediumEmphasized
                        },
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(targetHeader.description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = supportingColor,
                    )
                }
            }
        }

        if (compactHeight) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(header, downloading, phaseColors, badgeSize, animationsDisabled)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    text(Modifier.fillMaxWidth())
                    if (downloading != null) TransferLabel(downloading)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StatusBadge(header, downloading, phaseColors, badgeSize, animationsDisabled)
                text(Modifier.fillMaxWidth())
                AnimatedVisibility(
                    visible = downloading != null,
                    enter = if (animationsDisabled) fadeIn(snap()) else fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
                    exit = if (animationsDisabled) fadeOut(snap()) else fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.defaultSpatialSpec()),
                ) {
                    if (downloading != null) TransferLabel(downloading)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    header: UpdateHeader,
    downloading: AppUpdateTransfer.Downloading?,
    phaseColors: PhaseColors,
    size: Dp,
    animationsDisabled: Boolean,
) {
    val motionScheme = MaterialTheme.motionScheme
    val badgeColor by animateColorAsState(phaseColors.badge, effectsSpec(animationsDisabled), label = "badgeColor")
    val onBadgeColor by animateColorAsState(phaseColors.onBadge, effectsSpec(animationsDisabled), label = "badgeContent")
    val badgeShape = rememberMorphingShape(header.phase.badgePolygon(), animationsDisabled)
    val mode = when {
        header.phase == UpdatePhase.Checking -> BadgeMode.Checking
        downloading != null -> BadgeMode.Downloading
        else -> BadgeMode.Icon
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            if (animationsDisabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.defaultSpatialSpec(), initialScale = 0.6f)) togetherWith
                    (fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.6f))
            }
        },
        modifier = Modifier.size(size),
        label = "statusBadge",
    ) { targetMode ->
        when (targetMode) {
            BadgeMode.Checking -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (animationsDisabled) {
                        Icon(
                            painter = painterResource(R.drawable.update),
                            contentDescription = null,
                            tint = onBadgeColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(badgeColor, badgeShape)
                                .padding(size / 3),
                        )
                    } else {
                        LoadingIndicator(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            BadgeMode.Downloading -> DownloadProgressBadge(downloading, animationsDisabled)

            BadgeMode.Icon -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(badgeColor, badgeShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = header.icon,
                        transitionSpec = {
                            if (animationsDisabled) {
                                fadeIn(snap()) togetherWith fadeOut(snap())
                            } else {
                                (fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.4f)) togetherWith
                                    (fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.4f))
                            }
                        },
                        label = "statusBadgeIcon",
                    ) { icon ->
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = onBadgeColor,
                            modifier = Modifier.size(size * 0.4f),
                        )
                    }
                }
            }
        }
    }
}

private enum class BadgeMode {
    Checking,
    Downloading,
    Icon,
}

@Composable
private fun rememberMorphingShape(
    target: RoundedPolygon,
    animationsDisabled: Boolean,
): Shape {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(target, animationsDisabled) {
        if (target !== to) {
            from = to
            to = target
            if (animationsDisabled) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, animationSpec)
            }
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    return EditorialMorphShape(morph, progress.value)
}

@Composable
private fun DownloadProgressBadge(
    transfer: AppUpdateTransfer.Downloading?,
    animationsDisabled: Boolean,
) {
    val density = LocalDensity.current
    val fraction = transfer?.fraction()
    val animatedProgress by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = effectsSpec(animationsDisabled),
        label = "badgeDownloadProgress",
    )
    val stroke = remember(density) { Stroke(width = with(density) { 6.dp.toPx() }, cap = StrokeCap.Round) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            animationsDisabled -> CircularProgressIndicator(
                progress = { fraction ?: 0f },
                modifier = if (fraction == null) {
                    Modifier.fillMaxSize().clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                } else {
                    Modifier.fillMaxSize()
                },
                strokeWidth = 6.dp,
            )
            fraction != null -> CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                stroke = stroke,
                trackStroke = stroke,
            )
            else -> CircularWavyProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                stroke = stroke,
                trackStroke = stroke,
            )
        }
        if (fraction != null) {
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.clearAndSetSemantics {},
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun AppUpdateTransfer.Downloading.fraction(): Float? =
    takeIf { it.totalBytes > 0 }?.let { (it.downloadedBytes.toFloat() / it.totalBytes).coerceIn(0f, 1f) }

@Composable
private fun TransferLabel(transfer: AppUpdateTransfer.Downloading) {
    val context = LocalContext.current
    val fraction = transfer.fraction()
    val bytes = Formatter.formatShortFileSize(context, transfer.downloadedBytes)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = if (fraction != null) {
                stringResource(
                    R.string.app_update_transfer_progress,
                    bytes,
                    Formatter.formatShortFileSize(context, transfer.totalBytes),
                    (fraction * 100).roundToInt(),
                )
            } else {
                stringResource(R.string.app_update_transfer_bytes, bytes)
            },
            style = MaterialTheme.typography.labelLargeEmphasized,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun UpdateVersionDetails(
    release: AppUpdateRelease?,
    mandatory: Boolean,
    showReleaseFacts: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val releaseDate = remember(release?.publishedAt, context.resources.configuration.locales.toLanguageTags()) {
        release?.publishedAt?.takeIf { it.isNotBlank() }?.let {
            try {
                android.text.format.DateFormat.getMediumDateFormat(context).format(Date.from(Instant.parse(it)))
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
    val hasUpdate = release?.isUpdateAvailable == true

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReleaseVersionTile(
                    label = stringResource(R.string.current_version),
                    version = BuildConfig.VERSION_NAME,
                    emphasized = false,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (release != null) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    ReleaseVersionTile(
                        label = stringResource(if (hasUpdate) R.string.app_update_new_version else R.string.updates_latest_version),
                        version = release.version,
                        emphasized = hasUpdate,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            if (showReleaseFacts && release != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (mandatory) {
                        ReleaseFact(
                            iconRes = R.drawable.lock,
                            text = stringResource(R.string.app_update_required),
                            emphasized = true,
                        )
                    }
                    if (releaseDate != null) {
                        ReleaseFact(
                            iconRes = R.drawable.calendar_today,
                            text = stringResource(R.string.app_update_release_date, releaseDate),
                        )
                    }
                    release.downloadSizeBytes?.let { bytes ->
                        ReleaseFact(
                            iconRes = R.drawable.storage,
                            text = stringResource(R.string.app_update_download_size, Formatter.formatShortFileSize(context, bytes)),
                        )
                    }
                    if (!release.canInstallInApp) {
                        ReleaseFact(
                            iconRes = R.drawable.link,
                            text = stringResource(R.string.app_update_install_from_release_page),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseVersionTile(
    label: String,
    version: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) colorScheme.primary else colorScheme.surfaceContainerHighest,
        contentColor = if (emphasized) colorScheme.onPrimary else colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = version,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReleaseFact(
    @DrawableRes iconRes: Int,
    text: String,
    emphasized: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (emphasized) colorScheme.errorContainer else colorScheme.secondaryContainer,
        contentColor = if (emphasized) colorScheme.onErrorContainer else colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp).padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ReleaseNotesSection(
    notes: String?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colorScheme.tertiaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.newspaper),
                    contentDescription = null,
                    tint = colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(R.string.app_update_release_notes),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() },
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surfaceContainerLow,
        ) {
            if (notes.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.release_notes_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                MarkdownText(
                    markdown = notes,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun UpdateActionBar(
    state: AppUpdateState,
    mandatory: Boolean,
    compactHeight: Boolean,
    animationsDisabled: Boolean,
    maximumContentWidth: Dp,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onPrimaryAction: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val success = state as? AppUpdateState.Success
    val downloading = success?.transfer as? AppUpdateTransfer.Downloading
    val fraction = downloading?.fraction()
    val animatedProgress by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = effectsSpec(animationsDisabled),
        label = "actionBarProgress",
    )

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = downloading != null,
                enter = if (animationsDisabled) fadeIn(snap()) else fadeIn(motionScheme.defaultEffectsSpec()) + expandVertically(motionScheme.defaultSpatialSpec()),
                exit = if (animationsDisabled) fadeOut(snap()) else fadeOut(motionScheme.fastEffectsSpec()) + shrinkVertically(motionScheme.defaultSpatialSpec()),
            ) {
                val progressModifier = Modifier
                    .widthIn(max = maximumContentWidth)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                    .clearAndSetSemantics {}
                when {
                    animationsDisabled -> LinearProgressIndicator(progress = { fraction ?: 0f }, modifier = progressModifier)
                    fraction != null -> LinearWavyProgressIndicator(progress = { animatedProgress }, modifier = progressModifier)
                    else -> LinearWavyProgressIndicator(modifier = progressModifier)
                }
            }
            UpdateActions(
                state = state,
                mandatory = mandatory,
                compactHeight = compactHeight,
                animationsDisabled = animationsDisabled,
                onBack = onBack,
                onCheck = onCheck,
                onPrimaryAction = onPrimaryAction,
                onCancelDownload = onCancelDownload,
                modifier = Modifier
                    .widthIn(max = maximumContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private data class PrimaryActionContent(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int?,
)

@Composable
private fun UpdateActions(
    state: AppUpdateState,
    mandatory: Boolean,
    compactHeight: Boolean,
    animationsDisabled: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onPrimaryAction: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val success = state as? AppUpdateState.Success
    val hasUpdate = success?.release?.isUpdateAvailable == true
    val downloading = success?.transfer as? AppUpdateTransfer.Downloading
    val actionLabel = when {
        mandatory && downloading != null -> R.string.app_update_downloading
        downloading?.cancelling == true -> R.string.app_update_cancelling
        downloading != null -> R.string.app_update_cancel_download
        state is AppUpdateState.Loading -> R.string.app_update_checking
        !hasUpdate -> if (state is AppUpdateState.Error) R.string.app_update_try_again else R.string.app_update_check_again
        success?.release?.canInstallInApp == false -> R.string.app_update_open_release
        success?.errorRes == R.string.app_update_permission_required -> R.string.app_update_allow_installation
        success?.transfer is AppUpdateTransfer.Ready -> R.string.app_update_install
        success?.errorRes != null -> R.string.app_update_retry_download
        else -> R.string.app_update_download_install
    }
    val icon = when {
        state is AppUpdateState.Loading || (mandatory && downloading != null) -> null
        downloading != null -> R.drawable.close
        !hasUpdate -> R.drawable.sync
        success?.transfer is AppUpdateTransfer.Ready -> R.drawable.update
        success?.release?.canInstallInApp == false -> R.drawable.arrow_forward
        else -> R.drawable.download
    }
    val primaryHeight = ButtonDefaults.MediumContainerHeight
    val primaryColors: ButtonColors = if (downloading != null) {
        ButtonDefaults.filledTonalButtonColors()
    } else {
        ButtonDefaults.buttonColors()
    }
    val primary: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(
            onClick = when {
                downloading != null -> onCancelDownload
                hasUpdate -> onPrimaryAction
                else -> onCheck
            },
            enabled = state !is AppUpdateState.Loading && success?.openingExternalAction != true &&
                downloading?.cancelling != true && !(mandatory && downloading != null),
            shapes = ButtonDefaults.shapesFor(primaryHeight),
            colors = primaryColors,
            contentPadding = ButtonDefaults.contentPaddingFor(primaryHeight, hasStartIcon = icon != null),
            modifier = buttonModifier.heightIn(min = primaryHeight),
        ) {
            AnimatedContent(
                targetState = PrimaryActionContent(actionLabel, icon),
                transitionSpec = {
                    if (animationsDisabled) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        (
                            fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.fastSpatialSpec()) { it / 2 }
                        ) togetherWith (
                            fadeOut(motionScheme.fastEffectsSpec()) +
                                slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 2 }
                        ) using SizeTransform(clip = false)
                    }
                },
                label = "primaryAction",
            ) { content ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (content.icon != null) {
                        Icon(
                            painter = painterResource(content.icon),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(primaryHeight)),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(primaryHeight)))
                    }
                    Text(
                        text = stringResource(content.label),
                        style = ButtonDefaults.textStyleFor(primaryHeight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    val secondary: @Composable (Modifier) -> Unit = { buttonModifier ->
        TextButton(
            onClick = onBack,
            enabled = success?.openingExternalAction != true,
            shapes = ButtonDefaults.shapes(),
            modifier = buttonModifier.heightIn(min = 48.dp),
        ) {
            Text(
                stringResource(
                    when {
                        hasUpdate -> R.string.later
                        success != null -> R.string.app_update_done
                        else -> R.string.back_button_desc
                    },
                ),
            )
        }
    }
    if (compactHeight && downloading == null && !mandatory) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            primary(Modifier.weight(1f))
            secondary(Modifier.widthIn(max = 160.dp))
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mandatory) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.app_update_required_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            primary(Modifier.fillMaxWidth())
            if (downloading == null && !mandatory) secondary(Modifier.fillMaxWidth())
        }
    }
}
