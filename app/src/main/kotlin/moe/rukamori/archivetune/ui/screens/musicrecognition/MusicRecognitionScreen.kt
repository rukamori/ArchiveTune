/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import moe.rukamori.archivetune.ui.component.BackAwareModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicrecognition.FloatingMusicRecognitionService
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionCaptureActivity
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.musicrecognition.navigateToMusicRecognitionDetails
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.viewmodels.MusicRecognitionErrorUi
import moe.rukamori.archivetune.viewmodels.MusicRecognitionEvent
import moe.rukamori.archivetune.viewmodels.MusicRecognitionScreenState
import moe.rukamori.archivetune.viewmodels.MusicRecognitionSettingsUiState
import moe.rukamori.archivetune.viewmodels.MusicRecognitionViewModel
import moe.rukamori.archivetune.viewmodels.RecognitionHistoryItemUiModel
import moe.rukamori.archivetune.viewmodels.RecognitionHistorySheetUiState
import moe.rukamori.archivetune.viewmodels.RecognitionPhaseUi
import kotlin.math.ceil

@Composable
fun MusicRecognitionScreen(
    navController: NavHostController,
    viewModel: MusicRecognitionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val captureState = viewModel.captureState.collectAsStateWithLifecycle()
    val historyState by viewModel.historySheetState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onBack: () -> Unit = remember(navController, viewModel) {
        {
            viewModel.onCancelRecognition()
            navController.navigateUp()
            Unit
        }
    }
    val onListen = remember(viewModel) { viewModel::onListenRequested }
    val onCancel = remember(viewModel) { viewModel::onCancelRecognition }
    val onAppSettings = remember(viewModel) { viewModel::onAppSettingsRequested }
    val onResultRevealed = remember(viewModel) { viewModel::onResultRevealed }
    val onShowHistory = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(true) } }
    val onHideHistory = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(false) } }
    val onShowSettings = remember(viewModel) { { viewModel.onSettingsVisibilityChanged(true) } }
    val onHideSettings = remember(viewModel) { { viewModel.onSettingsVisibilityChanged(false) } }
    val onQueryChange = remember(viewModel) { viewModel::onHistoryQueryChanged }
    val onSearch = remember(viewModel) { viewModel::onTrackSearchRequested }
    val onOpenUri = remember(viewModel) { viewModel::onExternalUriRequested }
    val onFloatingChange = remember(viewModel) { viewModel::onFloatingRecognitionEnabledChanged }
    val captureLevel = remember(captureState) { { captureState.value.level } }
    val captureProgress = remember(captureState) { { captureState.value.progress } }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permanentlyDenied = !granted && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            viewModel.onMicrophonePermissionResult(granted, permanentlyDenied)
        }
    val floatingRecognitionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.onFloatingRecognitionStartResult(result.resultCode == Activity.RESULT_OK)
        }

    val recognitionActive = state is MusicRecognitionScreenState.Loading ||
        state is MusicRecognitionScreenState.Success
    BackHandler(onBack = if (recognitionActive) onCancel else onBack)
    LaunchedEffect(viewModel, context, navController, lifecycleOwner, hapticFeedback, floatingRecognitionLauncher) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                if (navController.currentDestination?.route != MusicRecognitionRoute) return@collect
                when (event) {
                    is MusicRecognitionEvent.RequestMicrophonePermission -> {
                        if (!viewModel.onPermissionRequestDispatched(event.requestId)) return@collect
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.onMicrophonePermissionResult(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    MusicRecognitionEvent.RecognitionStarted ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    is MusicRecognitionEvent.RecognitionFinished ->
                        hapticFeedback.performHapticFeedback(
                            if (event.matched) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                        )
                    MusicRecognitionEvent.StartFloatingRecognition ->
                        floatingRecognitionLauncher.launch(
                            MusicRecognitionCaptureActivity.intent(context, listenNow = false),
                        )
                    MusicRecognitionEvent.StopFloatingRecognition ->
                        context.startService(FloatingMusicRecognitionService.closeIntent(context))
                    MusicRecognitionEvent.OpenAppSettings -> {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }
                    is MusicRecognitionEvent.Search ->
                        navController.navigate(onlineSearchResultRoute(event.query)) { launchSingleTop = true }
                    is MusicRecognitionEvent.OpenUri -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.uri))
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }
                    is MusicRecognitionEvent.NavigateToDetails -> {
                        if (viewModel.onDetailsNavigationRequested(event.requestId)) {
                            navController.navigateToMusicRecognitionDetails(event.track)
                        }
                    }
                }
            }
        }
    }

    MusicRecognitionContent(
        state = state,
        captureLevel = captureLevel,
        captureProgress = captureProgress,
        onBack = onBack,
        onShowHistory = onShowHistory,
        onShowSettings = onShowSettings,
        onListen = onListen,
        onCancel = onCancel,
        onAppSettings = onAppSettings,
        onResultRevealed = onResultRevealed,
        onSearch = onSearch,
        onOpenUri = onOpenUri,
    )
    if (historyState.visible) {
        RecognitionHistoryBottomSheet(
            state = historyState,
            sheetState = historySheetState,
            onDismiss = onHideHistory,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onOpenUri = onOpenUri,
        )
    }
    if (settingsState.visible) {
        MusicRecognitionSettingsBottomSheet(
            state = settingsState,
            sheetState = settingsSheetState,
            onDismiss = onHideSettings,
            onFloatingChange = onFloatingChange,
        )
    }
}

private enum class RecognitionStage {
    Idle,
    Listening,
    Processing,
    Result,
    Failure,
}

private fun MusicRecognitionScreenState.stage(): RecognitionStage =
    when (this) {
        is MusicRecognitionScreenState.Empty -> RecognitionStage.Idle
        is MusicRecognitionScreenState.Loading ->
            if (phase == RecognitionPhaseUi.Listening) RecognitionStage.Listening else RecognitionStage.Processing
        is MusicRecognitionScreenState.Success -> RecognitionStage.Result
        is MusicRecognitionScreenState.Error -> RecognitionStage.Failure
    }

private val MusicRecognitionErrorUi?.isPermissionError: Boolean
    get() = this == MusicRecognitionErrorUi.PermissionRequired || this == MusicRecognitionErrorUi.PermissionBlocked

@Composable
private fun MusicRecognitionContent(
    state: MusicRecognitionScreenState,
    captureLevel: () -> Float,
    captureProgress: () -> Float,
    onBack: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
    onResultRevealed: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val motion = MaterialTheme.motionScheme
    val animationsEnabled = recognitionAnimationsEnabled()
    val stage = state.stage()
    val focused = stage != RecognitionStage.Idle && stage != RecognitionStage.Failure
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.music_recognition)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
                actions = {
                    IconButton(onClick = onShowHistory) {
                        Icon(painterResource(R.drawable.history), stringResource(R.string.music_recognition_history))
                    }
                    IconButton(onClick = onShowSettings) {
                        Icon(painterResource(R.drawable.settings), stringResource(R.string.music_recognition_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val heroMinHeight by animateDpAsState(
                targetValue = if (focused) maxHeight else maxHeight * HeroIdleHeightFraction,
                animationSpec = motion.defaultSpatialSpec(),
                label = "HeroMinHeight",
            )
            val orbSize = minOf(maxWidth * 0.56f, maxHeight * 0.34f, OrbMaxSize).coerceAtLeast(OrbMinSize)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecognitionHero(
                    state = state,
                    stage = stage,
                    orbSize = orbSize,
                    captureLevel = captureLevel,
                    captureProgress = captureProgress,
                    animationsEnabled = animationsEnabled,
                    onListen = onListen,
                    onCancel = onCancel,
                    onAppSettings = onAppSettings,
                    onResultRevealed = onResultRevealed,
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().heightIn(min = heroMinHeight),
                )
                AnimatedVisibility(
                    visible = !focused,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
                ) {
                    RecentRecognitions(
                        items = state.history.recentItems,
                        onShowHistory = onShowHistory,
                        onSearch = onSearch,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionHero(
    state: MusicRecognitionScreenState,
    stage: RecognitionStage,
    orbSize: Dp,
    captureLevel: () -> Float,
    captureProgress: () -> Float,
    animationsEnabled: Boolean,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
    onResultRevealed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = stage == RecognitionStage.Listening || stage == RecognitionStage.Processing ||
                stage == RecognitionStage.Result,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
        ) {
            RecognitionPhaseTrack(stage = stage, modifier = Modifier.padding(bottom = 24.dp))
        }
        RecognitionHeadline(state = state, animationsEnabled = animationsEnabled)
        Spacer(Modifier.height(32.dp))
        RecognitionOrb(
            state = state,
            stage = stage,
            size = orbSize,
            captureLevel = captureLevel,
            captureProgress = captureProgress,
            animationsEnabled = animationsEnabled,
            onListen = onListen,
            onResultRevealed = onResultRevealed,
        )
        Spacer(Modifier.height(28.dp))
        RecognitionActions(
            state = state,
            animationsEnabled = animationsEnabled,
            onListen = onListen,
            onCancel = onCancel,
            onAppSettings = onAppSettings,
        )
    }
}

@Composable
private fun RecognitionPhaseTrack(stage: RecognitionStage, modifier: Modifier = Modifier) {
    val activeIndex = when (stage) {
        RecognitionStage.Listening -> 0
        RecognitionStage.Processing -> 1
        RecognitionStage.Result -> 2
        else -> -1
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecognitionPhaseStep(stringResource(R.string.music_recognition_step_listen), 0, activeIndex)
        RecognitionPhaseStep(stringResource(R.string.music_recognition_step_process), 1, activeIndex)
        RecognitionPhaseStep(stringResource(R.string.music_recognition_step_result), 2, activeIndex)
    }
}

@Composable
private fun RecognitionPhaseStep(label: String, index: Int, activeIndex: Int) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val current = index == activeIndex
    val done = index < activeIndex
    val containerColor by animateColorAsState(
        targetValue = when {
            current -> colors.primary
            done -> colors.secondaryContainer
            else -> colors.surfaceContainerHigh
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "PhaseStepContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            current -> colors.onPrimary
            done -> colors.onSecondaryContainer
            else -> colors.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "PhaseStepContent",
    )
    val stepState = when {
        current -> stringResource(R.string.music_recognition_step_state_current)
        done -> stringResource(R.string.music_recognition_step_state_done)
        else -> null
    }
    Surface(
        modifier = Modifier.semantics { if (stepState != null) stateDescription = stepState },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .heightIn(min = 32.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (done) {
                Icon(painterResource(R.drawable.check), contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = if (current) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Immutable
private data class RecognitionHeadlineContent(
    val overline: String?,
    val title: String,
    val body: String,
)

@Composable
private fun recognitionHeadlineContent(state: MusicRecognitionScreenState): RecognitionHeadlineContent =
    when (state) {
        is MusicRecognitionScreenState.Empty -> RecognitionHeadlineContent(
            overline = null,
            title = stringResource(R.string.music_recognition_whats_playing),
            body = stringResource(R.string.music_recognition_idle_hint),
        )
        is MusicRecognitionScreenState.Loading ->
            if (state.phase == RecognitionPhaseUi.Listening) {
                RecognitionHeadlineContent(
                    overline = null,
                    title = stringResource(R.string.music_recognition_listening),
                    body = stringResource(R.string.music_recognition_nearby_hint),
                )
            } else {
                RecognitionHeadlineContent(
                    overline = null,
                    title = stringResource(R.string.music_recognition_searching),
                    body = stringResource(R.string.music_recognition_notification_processing_text),
                )
            }
        is MusicRecognitionScreenState.Success -> RecognitionHeadlineContent(
            overline = stringResource(R.string.music_recognition_match_found),
            title = state.track.title,
            body = state.track.artist,
        )
        is MusicRecognitionScreenState.Error -> RecognitionHeadlineContent(
            overline = null,
            title = stringResource(
                when (state.error) {
                    MusicRecognitionErrorUi.PermissionRequired, MusicRecognitionErrorUi.PermissionBlocked ->
                        R.string.music_recognition_permission_title
                    MusicRecognitionErrorUi.NoMatch -> R.string.music_recognition_no_match
                    else -> R.string.music_recognition_error
                },
            ),
            body = stringResource(
                when (state.error) {
                    MusicRecognitionErrorUi.PermissionRequired -> R.string.music_recognition_permission_desc
                    MusicRecognitionErrorUi.PermissionBlocked -> R.string.music_recognition_permission_settings
                    MusicRecognitionErrorUi.NoMatch -> R.string.music_recognition_notification_no_match_text
                    MusicRecognitionErrorUi.RecordingFailed -> R.string.music_recognition_recording_failed
                    MusicRecognitionErrorUi.SignatureFailed ->
                        R.string.music_recognition_notification_signature_failed_text
                    MusicRecognitionErrorUi.RecognitionFailed ->
                        R.string.music_recognition_notification_recognition_failed_text
                },
            ),
        )
    }

private fun MusicRecognitionScreenState.headlineKey(): Any =
    when (this) {
        is MusicRecognitionScreenState.Empty -> RecognitionStage.Idle
        is MusicRecognitionScreenState.Loading -> phase
        is MusicRecognitionScreenState.Success -> track.searchQuery
        is MusicRecognitionScreenState.Error -> error
    }

@Composable
private fun RecognitionHeadline(state: MusicRecognitionScreenState, animationsEnabled: Boolean) {
    val motion = MaterialTheme.motionScheme
    if (animationsEnabled) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.headlineKey() },
            transitionSpec = {
                (
                    fadeIn(motion.defaultEffectsSpec()) +
                        slideInVertically(motion.defaultSpatialSpec()) { it / 4 }
                    ) togetherWith (
                    fadeOut(motion.fastEffectsSpec()) +
                        slideOutVertically(motion.fastSpatialSpec()) { -it / 4 }
                    ) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
            },
            contentAlignment = Alignment.Center,
            label = "RecognitionHeadline",
        ) { current ->
            RecognitionHeadlineText(recognitionHeadlineContent(current))
        }
    } else {
        RecognitionHeadlineText(recognitionHeadlineContent(state))
    }
}

@Composable
private fun RecognitionHeadlineText(content: RecognitionHeadlineContent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (content.overline != null) {
            Text(
                text = content.overline,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = content.title,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = content.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Stable
internal class PolygonMorphState(initial: RoundedPolygon) {
    private var settled: RoundedPolygon = initial
    var morph by mutableStateOf(Morph(initial, initial))
        private set
    val progress = Animatable(1f)

    suspend fun morphTo(target: RoundedPolygon, animationSpec: AnimationSpec<Float>?) {
        if (target === settled) return
        morph = Morph(settled, target)
        settled = target
        progress.snapTo(0f)
        if (animationSpec == null) progress.snapTo(1f) else progress.animateTo(1f, animationSpec)
    }
}

private fun RecognitionStage.polygon(): RoundedPolygon =
    when (this) {
        RecognitionStage.Idle -> MaterialShapes.Cookie9Sided
        RecognitionStage.Listening -> MaterialShapes.SoftBurst
        RecognitionStage.Processing -> MaterialShapes.Circle
        RecognitionStage.Result -> MaterialShapes.Sunny
        RecognitionStage.Failure -> MaterialShapes.Cookie4Sided
    }

private enum class OrbProgressMode {
    None,
    Capture,
    Matching,
}

@Composable
private fun RecognitionOrb(
    state: MusicRecognitionScreenState,
    stage: RecognitionStage,
    size: Dp,
    captureLevel: () -> Float,
    captureProgress: () -> Float,
    animationsEnabled: Boolean,
    onListen: () -> Unit,
    onResultRevealed: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val error = (state as? MusicRecognitionScreenState.Error)?.error
    val listening = stage == RecognitionStage.Listening
    val idle = stage == RecognitionStage.Idle

    val containerColor = animateColorAsState(
        targetValue = when (stage) {
            RecognitionStage.Idle, RecognitionStage.Listening -> colors.primary
            RecognitionStage.Processing -> colors.tertiary
            RecognitionStage.Result -> colors.primaryContainer
            RecognitionStage.Failure ->
                if (error.isPermissionError) colors.secondaryContainer else colors.errorContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "OrbContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when (stage) {
            RecognitionStage.Idle, RecognitionStage.Listening -> colors.onPrimary
            RecognitionStage.Processing -> colors.onTertiary
            RecognitionStage.Result -> colors.onPrimaryContainer
            RecognitionStage.Failure ->
                if (error.isPermissionError) colors.onSecondaryContainer else colors.onErrorContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "OrbContent",
    )
    val fieldColor = animateColorAsState(
        targetValue = when (stage) {
            RecognitionStage.Processing -> colors.tertiary
            RecognitionStage.Failure -> colors.outlineVariant
            else -> colors.primary
        },
        animationSpec = motion.slowEffectsSpec(),
        label = "OrbField",
    )
    val glowIntensity = animateFloatAsState(
        targetValue = when (stage) {
            RecognitionStage.Idle -> 0.14f
            RecognitionStage.Listening -> 0.2f
            RecognitionStage.Processing -> 0.22f
            RecognitionStage.Result -> 0.3f
            RecognitionStage.Failure -> 0f
        },
        animationSpec = motion.slowEffectsSpec(),
        label = "OrbGlow",
    )
    val baseScale = animateFloatAsState(
        targetValue = when (stage) {
            RecognitionStage.Processing -> 0.88f
            RecognitionStage.Failure -> 0.84f
            else -> 1f
        },
        animationSpec = motion.defaultSpatialSpec(),
        label = "OrbScale",
    )

    val morphState = remember { PolygonMorphState(stage.polygon()) }
    val polygon = stage.polygon()
    LaunchedEffect(polygon, animationsEnabled) {
        morphState.morphTo(polygon, if (animationsEnabled) motion.defaultSpatialSpec<Float>() else null)
    }

    val level = remember { Animatable(0f) }
    LaunchedEffect(listening, animationsEnabled) {
        if (listening && animationsEnabled) {
            snapshotFlow(captureLevel).collectLatest { level.animateTo(it, motion.fastSpatialSpec()) }
        } else {
            level.animateTo(0f, motion.defaultSpatialSpec())
        }
    }

    val spin = remember { Animatable(0f) }
    val spinMillis = when (stage) {
        RecognitionStage.Idle -> IdleSpinMillis
        RecognitionStage.Listening -> ListeningSpinMillis
        RecognitionStage.Processing -> ProcessingSpinMillis
        else -> 0
    }
    LaunchedEffect(spinMillis, animationsEnabled) {
        if (!animationsEnabled) {
            spin.snapTo(0f)
        } else if (spinMillis == 0) {
            spin.animateTo(ceil(spin.value / SettledSpinStep) * SettledSpinStep, motion.slowSpatialSpec())
        } else {
            while (true) {
                spin.snapTo(spin.value % 360f)
                spin.animateTo(spin.value + 360f, tween(spinMillis, easing = LinearEasing))
            }
        }
    }

    val breath = if (animationsEnabled) {
        rememberInfiniteTransition(label = "OrbBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BreathMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "OrbBreathScale",
        )
    } else {
        null
    }

    val successTrack = (state as? MusicRecognitionScreenState.Success)?.track
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(successTrack, animationsEnabled) {
        if (successTrack == null) {
            reveal.snapTo(0f)
            return@LaunchedEffect
        }
        if (animationsEnabled) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, motion.slowSpatialSpec())
        } else {
            reveal.snapTo(1f)
        }
        onResultRevealed()
    }

    val captureStep by remember(captureProgress) {
        derivedStateOf { (captureProgress() * 10f).toInt().coerceIn(0, 10) }
    }
    val orbAction: (() -> Unit)? = when {
        idle -> onListen
        stage == RecognitionStage.Failure && !error.isPermissionError -> onListen
        else -> null
    }
    val orbActionLabel = stringResource(
        if (idle) R.string.music_recognition_tap_to_listen else R.string.music_recognition_listen_again,
    )
    val captureDescription = stringResource(R.string.music_recognition_capture_progress, captureStep * 10)

    Box(Modifier.size(size * OrbFieldScale), contentAlignment = Alignment.Center) {
        if (animationsEnabled) {
            RecognitionField(
                listening = listening,
                captureLevel = captureLevel,
                color = fieldColor,
                glowIntensity = glowIntensity,
                coreRadius = size / 2,
                modifier = Modifier.matchParentSize(),
            )
        }
        val progressMode = when (stage) {
            RecognitionStage.Listening -> OrbProgressMode.Capture
            RecognitionStage.Processing -> if (animationsEnabled) OrbProgressMode.Matching else OrbProgressMode.None
            else -> OrbProgressMode.None
        }
        AnimatedContent(
            targetState = progressMode,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.06f))
            },
            contentAlignment = Alignment.Center,
            label = "OrbProgress",
        ) { mode ->
            val ringModifier = Modifier.size(size + OrbRingInset * 2)
            when (mode) {
                OrbProgressMode.Capture -> CircularWavyProgressIndicator(
                    progress = captureProgress,
                    modifier = ringModifier,
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    amplitude = if (animationsEnabled) WavyProgressIndicatorDefaults.indicatorAmplitude else NoWaveAmplitude,
                )
                OrbProgressMode.Matching -> CircularWavyProgressIndicator(
                    modifier = ringModifier,
                    color = colors.tertiary,
                    trackColor = colors.surfaceContainerHighest,
                )
                OrbProgressMode.None -> Spacer(ringModifier)
            }
        }
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (orbAction != null) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = size / 2),
                            role = Role.Button,
                            onClickLabel = orbActionLabel,
                            onClick = orbAction,
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    when (stage) {
                        RecognitionStage.Idle, RecognitionStage.Failure ->
                            if (orbAction != null) {
                                contentDescription = orbActionLabel
                            }
                        RecognitionStage.Listening -> {
                            progressBarRangeInfo = ProgressBarRangeInfo(captureStep / 10f, 0f..1f)
                            stateDescription = captureDescription
                        }
                        RecognitionStage.Processing -> progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        RecognitionStage.Result -> Unit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        rotationZ = spin.value
                        val pulse = level.value * ListeningScaleRange
                        val breathing = if (idle) (breath?.value ?: 0f) * BreathScaleRange else 0f
                        val scale = baseScale.value * (1f + pulse + breathing)
                        scaleX = scale
                        scaleY = scale
                    }
                    .graphicsLayer {
                        shape = EditorialMorphShape(morphState.morph, morphState.progress.value)
                        clip = true
                    }
                    .drawBehind { drawRect(containerColor.value) },
            )
            RecognitionOrbContent(
                state = state,
                size = size,
                contentColor = contentColor,
                animationsEnabled = animationsEnabled,
                reveal = reveal,
            )
        }
    }
}

@Composable
private fun RecognitionOrbContent(
    state: MusicRecognitionScreenState,
    size: Dp,
    contentColor: Color,
    animationsEnabled: Boolean,
    reveal: Animatable<Float, AnimationVector1D>,
) {
    val motion = MaterialTheme.motionScheme
    val artworkShape = MaterialShapes.Cookie12Sided.toShape()
    AnimatedContent(
        targetState = state,
        contentKey = { it.stage() },
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f))
        },
        contentAlignment = Alignment.Center,
        label = "OrbContent",
    ) { current ->
        val iconModifier = Modifier.size(size * 0.3f)
        when (current) {
            is MusicRecognitionScreenState.Empty ->
                Icon(painterResource(R.drawable.mic), null, iconModifier, tint = contentColor)
            is MusicRecognitionScreenState.Loading ->
                if (current.phase == RecognitionPhaseUi.Listening) {
                    Icon(painterResource(R.drawable.listening), null, iconModifier, tint = contentColor)
                } else if (animationsEnabled) {
                    LoadingIndicator(modifier = Modifier.size(size * 0.46f), color = contentColor)
                } else {
                    Icon(painterResource(R.drawable.cached), null, iconModifier, tint = contentColor)
                }
            is MusicRecognitionScreenState.Success ->
                Box(
                    Modifier.graphicsLayer {
                        val progress = reveal.value
                        val scale = 0.55f + 0.45f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = progress.coerceIn(0f, 1f)
                    },
                ) {
                    CoverArt(current.track.artworkUrl, size * 0.78f, artworkShape)
                }
            is MusicRecognitionScreenState.Error ->
                Icon(
                    painterResource(if (current.error.isPermissionError) R.drawable.mic else R.drawable.search_off),
                    null,
                    iconModifier,
                    tint = contentColor,
                )
        }
    }
}

@Composable
private fun RecognitionField(
    listening: Boolean,
    captureLevel: () -> Float,
    color: State<Color>,
    glowIntensity: State<Float>,
    coreRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val rings = remember { List(BeatRingCount) { Animatable(1f) } }
    val strengths = remember { FloatArray(BeatRingCount) }
    val ringScope = rememberCoroutineScope()
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        var envelope = 0f
        var lastPulseAt = 0L
        var nextRing = 0
        snapshotFlow(captureLevel).collect { level ->
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastPulseAt
            val onset = level >= MinBeatLevel && level - envelope >= BeatOnsetThreshold &&
                elapsed >= MinBeatIntervalMillis
            if (onset || elapsed >= AmbientPulseIntervalMillis) {
                lastPulseAt = now
                val ring = rings[nextRing]
                strengths[nextRing] = if (onset) 0.55f + level * 0.45f else AmbientPulseStrength
                nextRing = (nextRing + 1) % BeatRingCount
                ringScope.launch {
                    ring.snapTo(0f)
                    ring.animateTo(1f, tween(BeatRingMillis, easing = EaseOutCubic))
                }
            }
            envelope += (level - envelope) * EnvelopeSmoothing
        }
    }
    Canvas(modifier) {
        val core = coreRadius.toPx()
        val maxRadius = size.minDimension / 2f
        val tint = color.value
        val level = if (listening) captureLevel() else 0f
        val glow = glowIntensity.value * (1f + level)
        if (glow > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = glow.coerceAtMost(1f)), Color.Transparent),
                    center = center,
                    radius = maxRadius,
                ),
                radius = maxRadius,
            )
        }
        rings.forEachIndexed { index, ring ->
            val progress = ring.value
            if (progress >= 1f) return@forEachIndexed
            val fade = (1f - progress) * strengths[index]
            val radius = core + (maxRadius - core) * progress
            drawCircle(tint, radius = radius, alpha = fade * 0.14f)
            drawCircle(
                tint,
                radius = radius,
                alpha = fade * 0.6f,
                style = Stroke(width = (1.5f + 4f * (1f - progress)) * density),
            )
        }
    }
}

private enum class RecognitionActionKind {
    None,
    Cancel,
    ListenAgain,
    AllowMicrophone,
    OpenSettings,
}

private fun MusicRecognitionScreenState.actionKind(): RecognitionActionKind =
    when (this) {
        is MusicRecognitionScreenState.Empty, is MusicRecognitionScreenState.Success -> RecognitionActionKind.None
        is MusicRecognitionScreenState.Loading -> RecognitionActionKind.Cancel
        is MusicRecognitionScreenState.Error -> when (error) {
            MusicRecognitionErrorUi.PermissionRequired -> RecognitionActionKind.AllowMicrophone
            MusicRecognitionErrorUi.PermissionBlocked -> RecognitionActionKind.OpenSettings
            else -> RecognitionActionKind.ListenAgain
        }
    }

@Composable
private fun RecognitionActions(
    state: MusicRecognitionScreenState,
    animationsEnabled: Boolean,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val kind = state.actionKind()
    Box(Modifier.heightIn(min = ActionAreaMinHeight), contentAlignment = Alignment.Center) {
        if (animationsEnabled) {
            AnimatedContent(
                targetState = kind,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f)) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                contentAlignment = Alignment.Center,
                label = "RecognitionActions",
            ) { current ->
                RecognitionActionButton(current, onListen, onCancel, onAppSettings)
            }
        } else {
            RecognitionActionButton(kind, onListen, onCancel, onAppSettings)
        }
    }
}

@Composable
private fun RecognitionActionButton(
    kind: RecognitionActionKind,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
) {
    val buttonModifier = Modifier.heightIn(min = 48.dp)
    when (kind) {
        RecognitionActionKind.None -> Unit
        RecognitionActionKind.Cancel ->
            OutlinedButton(onClick = onCancel, modifier = buttonModifier, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.close), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.cancel))
            }
        RecognitionActionKind.ListenAgain ->
            Button(onClick = onListen, modifier = buttonModifier, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.mic), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_listen_again), textAlign = TextAlign.Center)
            }
        RecognitionActionKind.AllowMicrophone ->
            Button(onClick = onListen, modifier = buttonModifier, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.mic), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_permission_action), textAlign = TextAlign.Center)
            }
        RecognitionActionKind.OpenSettings ->
            Button(onClick = onAppSettings, modifier = buttonModifier, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.settings), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.settings), textAlign = TextAlign.Center)
            }
    }
}

@Composable
private fun RecentRecognitions(
    items: ImmutableList<RecognitionHistoryItemUiModel>,
    onShowHistory: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.music_recognition_recent),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            TextButton(onClick = onShowHistory) { Text(stringResource(R.string.view_all)) }
        }
        if (items.isEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                RecognitionHistoryEmptyState(
                    title = stringResource(R.string.music_recognition_history_empty_title),
                    body = stringResource(R.string.music_recognition_history_empty_body),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items.forEach { item ->
                    key(item.stableKey) {
                        RecentRecognitionTile(item, onSearch, onOpenUri)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRecognitionTile(
    item: RecognitionHistoryItemUiModel,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val search = remember(item.searchQuery, onSearch) { { onSearch(item.searchQuery) } }
    val shazamUrl = item.shazamUrl
    val openShazam: () -> Unit = remember(shazamUrl, onOpenUri) {
        {
            if (shazamUrl != null) {
                onOpenUri(shazamUrl)
            }
        }
    }
    Column(
        modifier = Modifier
            .width(RecentTileWidth)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = search)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            CoverArt(item.artworkUrl, RecentTileWidth, MaterialTheme.shapes.extraLarge)
            if (shazamUrl != null) {
                FilledTonalIconButton(
                    onClick = openShazam,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ) {
                    Icon(painterResource(R.drawable.link), stringResource(R.string.music_recognition_open_shazam))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.music_recognition_history_recognized_at, item.recognizedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MusicRecognitionSettingsBottomSheet(
    state: MusicRecognitionSettingsUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onFloatingChange: (Boolean) -> Unit,
) {
    BackAwareModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "settings_header", contentType = "header") {
                RecognitionSheetHeader(stringResource(R.string.music_recognition_settings), onDismiss)
            }
            item(key = "floating_recognition", contentType = "setting") {
                SwitchPreference(
                    title = { Text(stringResource(R.string.music_recognition_floating_title)) },
                    description = stringResource(
                        if (state.floatingRecognitionAvailable) R.string.music_recognition_floating_description
                        else R.string.music_recognition_floating_foss_description,
                    ),
                    checked = state.floatingRecognitionEnabled,
                    onCheckedChange = onFloatingChange,
                    isEnabled = state.floatingRecognitionAvailable && !state.isSaving,
                )
            }
            if (state.saveFailed || state.permissionsDenied) {
                item(key = "save_error", contentType = "error") {
                    Text(
                        stringResource(
                            if (state.permissionsDenied) R.string.music_recognition_floating_permissions_denied
                            else R.string.music_recognition_setting_save_failed,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp).semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionHistoryBottomSheet(
    state: RecognitionHistorySheetUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val clearQuery = remember(onQueryChange) { { onQueryChange("") } }
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardActions = remember(keyboard) { KeyboardActions(onSearch = { keyboard?.hide() }) }
    val keyboardOptions = remember { KeyboardOptions(imeAction = ImeAction.Search) }
    BackAwareModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()
                .align(Alignment.CenterHorizontally).imePadding().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "history_header", contentType = "header") {
                RecognitionSheetHeader(stringResource(R.string.music_recognition_history), onDismiss)
            }
            item(key = "history_search", contentType = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                    label = { Text(stringResource(R.string.music_recognition_history_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.search), null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = clearQuery) {
                                Icon(painterResource(R.drawable.close), stringResource(R.string.clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardActions = keyboardActions,
                    keyboardOptions = keyboardOptions,
                    shape = MaterialTheme.shapes.large,
                )
            }
            if (state.allItems.items.isEmpty() || state.filteredItems.items.isEmpty()) {
                item(key = "history_empty", contentType = "empty") {
                    val emptyHistory = state.allItems.items.isEmpty()
                    RecognitionHistoryEmptyState(
                        title = stringResource(
                            if (emptyHistory) R.string.music_recognition_history_empty_title
                            else R.string.music_recognition_history_no_results_title,
                        ),
                        body = stringResource(
                            if (emptyHistory) R.string.music_recognition_history_empty_body
                            else R.string.music_recognition_history_no_results_body,
                        ),
                    )
                }
            } else {
                itemsIndexed(
                    items = state.filteredItems.items,
                    key = { _, item -> item.stableKey },
                    contentType = { _, _ -> "recognition_history_item" },
                ) { index, item ->
                    RecognitionHistoryListItem(
                        item, index, state.filteredItems.items.size, onSearch, onOpenUri,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionSheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.close), stringResource(R.string.close))
        }
    }
}

@Composable
private fun RecognitionHistoryListItem(
    item: RecognitionHistoryItemUiModel,
    index: Int,
    count: Int,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val search = remember(item.searchQuery, onSearch) { { onSearch(item.searchQuery) } }
    val shazamUrl = item.shazamUrl
    val openShazam: () -> Unit = remember(shazamUrl, onOpenUri) {
        {
            if (shazamUrl != null) {
                onOpenUri(shazamUrl)
            }
        }
    }
    SegmentedListItem(
        onClick = search,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { CoverArt(item.artworkUrl, 56.dp) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.artist, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.music_recognition_history_recognized_at, item.recognizedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            if (shazamUrl != null) {
                IconButton(onClick = openShazam) {
                    Icon(painterResource(R.drawable.link), stringResource(R.string.music_recognition_open_shazam))
                }
            }
        },
    ) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecognitionHistoryEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CoverArt(
    artworkUrl: String?,
    displaySize: Dp,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(displaySize, density) { with(density) { displaySize.roundToPx().coerceAtLeast(1) } }
    val imageRequest = remember(context, artworkUrl, sizePx) {
        artworkUrl?.let { ImageRequest.Builder(context).data(it).size(sizePx, sizePx).build() }
    }
    Surface(
        modifier = Modifier.size(displaySize),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.music_note),
                contentDescription = null,
                modifier = Modifier.size(displaySize / 3),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
internal fun recognitionAnimationsEnabled(): Boolean {
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    return !LocalAnimationsDisabled.current && (durationScale?.scaleFactor ?: 1f) > 0f
}

private val ContentMaxWidth = 720.dp
private val NoWaveAmplitude: (Float) -> Float = { 0f }
private val OrbMaxSize = 248.dp
private val OrbMinSize = 160.dp
private val OrbRingInset = 18.dp
private val ActionAreaMinHeight = 56.dp
private val RecentTileWidth = 148.dp
private const val HeroIdleHeightFraction = 0.82f
private const val OrbFieldScale = 1.7f
private const val IdleSpinMillis = 22_000
private const val ListeningSpinMillis = 8_000
private const val ProcessingSpinMillis = 3_200
private const val SettledSpinStep = 90f
private const val BreathMillis = 1_800
private const val BreathScaleRange = 0.035f
private const val ListeningScaleRange = 0.16f
private const val BeatRingCount = 5
private const val BeatRingMillis = 1_400
private const val MinBeatLevel = 0.18f
private const val BeatOnsetThreshold = 0.1f
private const val MinBeatIntervalMillis = 220L
private const val AmbientPulseIntervalMillis = 1_100L
private const val AmbientPulseStrength = 0.3f
private const val EnvelopeSmoothing = 0.12f
