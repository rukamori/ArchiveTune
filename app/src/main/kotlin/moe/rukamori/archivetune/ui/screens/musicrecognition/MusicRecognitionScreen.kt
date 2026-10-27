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

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.musicrecognition.navigateToMusicRecognitionDetails
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.viewmodels.MusicRecognitionErrorUi
import moe.rukamori.archivetune.viewmodels.MusicRecognitionEvent
import moe.rukamori.archivetune.viewmodels.MusicRecognitionScreenState
import moe.rukamori.archivetune.viewmodels.MusicRecognitionSettingsUiState
import moe.rukamori.archivetune.viewmodels.MusicRecognitionViewModel
import moe.rukamori.archivetune.viewmodels.RecognitionHistoryItemUiModel
import moe.rukamori.archivetune.viewmodels.RecognitionHistorySheetUiState
import moe.rukamori.archivetune.viewmodels.RecognitionPhaseUi

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
    val onShowHistory = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(true) } }
    val onHideHistory = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(false) } }
    val onShowSettings = remember(viewModel) { { viewModel.onSettingsVisibilityChanged(true) } }
    val onHideSettings = remember(viewModel) { { viewModel.onSettingsVisibilityChanged(false) } }
    val onQueryChange = remember(viewModel) { viewModel::onHistoryQueryChanged }
    val onSearch = remember(viewModel) { viewModel::onTrackSearchRequested }
    val onOpenUri = remember(viewModel) { viewModel::onExternalUriRequested }
    val onBackgroundChange = remember(viewModel) { viewModel::onBackgroundRecognitionEnabledChanged }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permanentlyDenied = !granted && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            viewModel.onMicrophonePermissionResult(granted, permanentlyDenied)
        }

    BackHandler(onBack = onBack)
    LaunchedEffect(viewModel, context, navController, lifecycleOwner, hapticFeedback) {
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
        onBack = onBack,
        onShowHistory = onShowHistory,
        onShowSettings = onShowSettings,
        onListen = onListen,
        onCancel = onCancel,
        onAppSettings = onAppSettings,
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
            onBackgroundChange = onBackgroundChange,
        )
    }
}

@Composable
private fun MusicRecognitionContent(
    state: MusicRecognitionScreenState,
    onBack: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val scrollBehavior = appBarScrollBehavior()
    val wide = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
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
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1_040.dp).fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = if (wide) 24.dp else 16.dp,
                    vertical = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (wide) {
                    item(key = "recognition_and_history", contentType = "recognition_and_history") {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            RecognitionStage(
                                state, onListen, onCancel, onAppSettings,
                                modifier = Modifier.weight(1f),
                            )
                            RecentRecognitions(
                                items = state.history.recentItems,
                                onShowHistory = onShowHistory,
                                onSearch = onSearch,
                                onOpenUri = onOpenUri,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    item(key = "recognition", contentType = "recognition") {
                        RecognitionStage(state, onListen, onCancel, onAppSettings)
                    }
                    item(key = "recent_recognitions", contentType = "recent_recognitions") {
                        RecentRecognitions(
                            items = state.history.recentItems,
                            onShowHistory = onShowHistory,
                            onSearch = onSearch,
                            onOpenUri = onOpenUri,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionStage(
    state: MusicRecognitionScreenState,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = recognitionAnimationsEnabled()
    val motion = MaterialTheme.motionScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (animationsEnabled) {
            AnimatedContent(
                targetState = state,
                contentKey = { current ->
                    when (current) {
                        is MusicRecognitionScreenState.Loading -> current.phase
                        is MusicRecognitionScreenState.Error -> current.error
                        else -> current::class
                    }
                },
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                        .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
                },
                label = "RecognitionStage",
            ) { current ->
                RecognitionStageContent(current, onListen, onCancel, onAppSettings, true)
            }
        } else {
            RecognitionStageContent(state, onListen, onCancel, onAppSettings, false)
        }
    }
}

@Composable
private fun RecognitionStageContent(
    state: MusicRecognitionScreenState,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAppSettings: () -> Unit,
    animationsEnabled: Boolean,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val phase = (state as? MusicRecognitionScreenState.Loading)?.phase
    val error = (state as? MusicRecognitionScreenState.Error)?.error
    val permissionError = error == MusicRecognitionErrorUi.PermissionRequired ||
        error == MusicRecognitionErrorUi.PermissionBlocked
    val title = when (state) {
        is MusicRecognitionScreenState.Empty -> stringResource(R.string.music_recognition_whats_playing)
        is MusicRecognitionScreenState.Loading -> stringResource(
            if (phase == RecognitionPhaseUi.Listening) R.string.music_recognition_listening
            else R.string.music_recognition_processing,
        )
        is MusicRecognitionScreenState.Success -> state.track.title
        is MusicRecognitionScreenState.Error -> stringResource(
            when (error) {
                MusicRecognitionErrorUi.PermissionRequired, MusicRecognitionErrorUi.PermissionBlocked ->
                    R.string.music_recognition_permission_title
                MusicRecognitionErrorUi.NoMatch -> R.string.music_recognition_no_match
                else -> R.string.music_recognition_error
            },
        )
    }
    val description = when (state) {
        is MusicRecognitionScreenState.Empty -> stringResource(R.string.music_recognition_nearby_hint)
        is MusicRecognitionScreenState.Loading -> stringResource(
            if (phase == RecognitionPhaseUi.Listening) R.string.music_recognition_nearby_hint
            else R.string.music_recognition_notification_processing_text,
        )
        is MusicRecognitionScreenState.Success -> state.track.artist
        is MusicRecognitionScreenState.Error -> stringResource(
            when (error) {
                MusicRecognitionErrorUi.PermissionRequired -> R.string.music_recognition_permission_desc
                MusicRecognitionErrorUi.PermissionBlocked -> R.string.music_recognition_permission_settings
                MusicRecognitionErrorUi.NoMatch -> R.string.music_recognition_notification_no_match_text
                MusicRecognitionErrorUi.RecordingFailed -> R.string.music_recognition_recording_failed
                MusicRecognitionErrorUi.SignatureFailed -> R.string.music_recognition_notification_signature_failed_text
                else -> R.string.music_recognition_notification_recognition_failed_text
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().heightIn(min = 224.dp),
            contentAlignment = Alignment.Center,
        ) {
            val feedbackSize = minOf(224.dp, maxWidth)
            if (state is MusicRecognitionScreenState.Empty) {
                Button(
                    onClick = onListen,
                    modifier = Modifier.widthIn(min = 192.dp, max = 280.dp).heightIn(min = 192.dp),
                    shapes = ButtonDefaults.shapes(
                        shape = if (largeText) MaterialTheme.shapes.extraLarge else CircleShape,
                        pressedShape = MaterialTheme.shapes.extraLarge,
                    ),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.mic), null, modifier = Modifier.size(56.dp))
                        Text(
                            stringResource(R.string.music_recognition_tap_to_listen),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                if (phase == RecognitionPhaseUi.Listening && animationsEnabled) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(feedbackSize),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                val centerSize = if (error != null) 112.dp else 192.dp
                Surface(
                    modifier = Modifier.size(minOf(centerSize, feedbackSize - 32.dp)),
                    shape = if (error != null) MaterialTheme.shapes.extraLarge else CircleShape,
                    color = if (error != null && !permissionError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (error != null && !permissionError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (phase == RecognitionPhaseUi.Processing && animationsEnabled) {
                            LoadingIndicator(
                                modifier = Modifier.size(72.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            val icon = when {
                                permissionError -> R.drawable.mic
                                error != null -> R.drawable.search_off
                                state is MusicRecognitionScreenState.Success -> R.drawable.check
                                phase == RecognitionPhaseUi.Processing -> R.drawable.cached
                                else -> R.drawable.listening
                            }
                            Icon(
                                painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).semantics {
                                    if (phase != null && !animationsEnabled) {
                                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (state is MusicRecognitionScreenState.Loading) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        } else if (state is MusicRecognitionScreenState.Error) {
            Button(
                onClick = if (error == MusicRecognitionErrorUi.PermissionBlocked) onAppSettings else onListen,
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(
                        when (error) {
                            MusicRecognitionErrorUi.PermissionBlocked -> R.string.settings
                            MusicRecognitionErrorUi.PermissionRequired -> R.string.music_recognition_permission_action
                            else -> R.string.music_recognition_listen_again
                        },
                    ),
                    textAlign = TextAlign.Center,
                )
            }
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.music_recognition_recent),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onShowHistory) { Text(stringResource(R.string.view_all)) }
        }
        if (items.isEmpty()) {
            RecognitionHistoryEmptyState(
                title = stringResource(R.string.music_recognition_history_empty_title),
                body = stringResource(R.string.music_recognition_history_empty_body),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                items.forEachIndexed { index, item ->
                    key(item.stableKey) {
                        RecognitionHistoryListItem(item, index, items.size, onSearch, onOpenUri)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicRecognitionSettingsBottomSheet(
    state: MusicRecognitionSettingsUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
) {
    ModalBottomSheet(
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
            item(key = "background_recognition", contentType = "setting") {
                SwitchPreference(
                    title = { Text(stringResource(R.string.music_recognition_background_title)) },
                    description = stringResource(
                        if (state.backgroundRecognitionAvailable) R.string.music_recognition_background_description
                        else R.string.music_recognition_background_foss_description,
                    ),
                    checked = state.backgroundRecognitionEnabled,
                    onCheckedChange = onBackgroundChange,
                    isEnabled = state.backgroundRecognitionAvailable && !state.isSaving,
                )
            }
            if (state.saveFailed) {
                item(key = "save_error", contentType = "error") {
                    Text(
                        stringResource(R.string.music_recognition_setting_save_failed),
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
    ModalBottomSheet(
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
