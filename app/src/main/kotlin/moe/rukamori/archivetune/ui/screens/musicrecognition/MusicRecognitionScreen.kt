/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.R
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
import moe.rukamori.archivetune.viewmodels.RecognitionHistoryUiModel
import moe.rukamori.archivetune.viewmodels.RecognitionPhaseUi
import moe.rukamori.archivetune.viewmodels.RecognizedTrackUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionScreen(
    navController: NavHostController,
    viewModel: MusicRecognitionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val historySheetState by viewModel.historySheetState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val historyModalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsModalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onNavigateBack =
        remember(navController) {
            {
                navController.navigateUp()
                Unit
            }
        }
    val onShowHistory = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(true) } }
    val onShowSettings = remember(viewModel) { { viewModel.onSettingsVisibilityChanged(true) } }
    val onListen = remember(viewModel) { { viewModel.onListenRequested() } }
    val onCancel = remember(viewModel) { { viewModel.onCancelRecognition() } }
    val onHistoryDismiss = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(false) } }
    val onSettingsDismiss = remember(viewModel) { { viewModel.onSettingsVisibilityChanged(false) } }
    val onBackgroundRecognitionEnabledChange =
        remember(viewModel) {
            { enabled: Boolean -> viewModel.onBackgroundRecognitionEnabledChanged(enabled) }
        }
    val onHistoryQueryChange =
        remember(viewModel) { { query: String -> viewModel.onHistoryQueryChanged(query) } }
    val onSearch =
        remember(viewModel) { { query: String -> viewModel.onTrackSearchRequested(query) } }
    val onOpenUri =
        remember(viewModel) { { uri: String -> viewModel.onExternalUriRequested(uri) } }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onMicrophonePermissionResult(granted)
        }

    LaunchedEffect(viewModel, context, navController, hapticFeedback) {
        viewModel.events.collect { event ->
            when (event) {
                MusicRecognitionEvent.RequestMicrophonePermission -> {
                    val permissionGranted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (permissionGranted) {
                        viewModel.onMicrophonePermissionResult(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                MusicRecognitionEvent.RecognitionStarted -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                is MusicRecognitionEvent.Search -> {
                    navController.navigate(onlineSearchResultRoute(event.query))
                }

                is MusicRecognitionEvent.OpenUri -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.uri))
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    MusicRecognitionContent(
        state = screenState,
        onNavigateBack = onNavigateBack,
        onShowHistory = onShowHistory,
        onShowSettings = onShowSettings,
        onListen = onListen,
        onCancel = onCancel,
        onAllowPermission = onListen,
        onSearch = onSearch,
        onOpenUri = onOpenUri,
    )

    if (historySheetState.visible) {
        RecognitionHistoryBottomSheet(
            state = historySheetState,
            sheetState = historyModalSheetState,
            onDismiss = onHistoryDismiss,
            onQueryChange = onHistoryQueryChange,
            onSearch = onSearch,
            onOpenUri = onOpenUri,
        )
    }

    if (settingsState.visible) {
        MusicRecognitionSettingsBottomSheet(
            state = settingsState,
            sheetState = settingsModalSheetState,
            onDismiss = onSettingsDismiss,
            onBackgroundRecognitionEnabledChange = onBackgroundRecognitionEnabledChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicRecognitionContent(
    state: MusicRecognitionScreenState,
    onNavigateBack: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAllowPermission: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val scrollBehavior = appBarScrollBehavior()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useWideLayout =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )
    val maximumContentWidth = if (useWideLayout) 1_040.dp else 680.dp

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.music_recognition),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onShowHistory) {
                        Icon(
                            painter = painterResource(R.drawable.history),
                            contentDescription = stringResource(R.string.music_recognition_history),
                        )
                    }
                    FilledTonalIconButton(onClick = onShowSettings) {
                        Icon(
                            painter = painterResource(R.drawable.settings),
                            contentDescription = stringResource(R.string.music_recognition_settings),
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
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .widthIn(max = maximumContentWidth)
                        .fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        start = if (useWideLayout) 24.dp else 16.dp,
                        top = 24.dp,
                        end = if (useWideLayout) 24.dp else 16.dp,
                        bottom = 40.dp,
                    ),
            ) {
                item(
                    key = MusicRecognitionStateItemKey,
                    contentType = MusicRecognitionStateItemContentType,
                ) {
                    val motionScheme = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = state,
                        contentKey = MusicRecognitionScreenState::contentKey,
                        transitionSpec = {
                            (
                                fadeIn(motionScheme.defaultEffectsSpec()) +
                                    scaleIn(
                                        animationSpec = motionScheme.defaultSpatialSpec(),
                                        initialScale = StateTransitionInitialScale,
                                    )
                            ).togetherWith(
                                fadeOut(motionScheme.fastEffectsSpec()) +
                                    scaleOut(
                                        animationSpec = motionScheme.fastSpatialSpec(),
                                        targetScale = StateTransitionTargetScale,
                                    ),
                            )
                        },
                        label = "MusicRecognitionState",
                    ) { animatedState ->
                        when (animatedState) {
                            is MusicRecognitionScreenState.Empty -> {
                                RecognitionTaskState(
                                    phase = null,
                                    onListen = onListen,
                                    onCancel = onCancel,
                                )
                            }

                            is MusicRecognitionScreenState.Loading -> {
                                RecognitionTaskState(
                                    phase = animatedState.phase,
                                    onListen = onListen,
                                    onCancel = onCancel,
                                )
                            }

                            is MusicRecognitionScreenState.Error -> {
                                RecognitionErrorState(
                                    error = animatedState.error,
                                    onListen = onListen,
                                    onAllowPermission = onAllowPermission,
                                )
                            }

                            is MusicRecognitionScreenState.Success -> {
                                val searchResult =
                                    remember(animatedState.track.searchQuery, onSearch) {
                                        { onSearch(animatedState.track.searchQuery) }
                                    }
                                RecognitionResultContent(
                                    track = animatedState.track,
                                    useWideLayout = useWideLayout,
                                    onListenAgain = onListen,
                                    onSearch = searchResult,
                                    onOpenUri = onOpenUri,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionTaskState(
    phase: RecognitionPhaseUi?,
    onListen: () -> Unit,
    onCancel: () -> Unit,
) {
    val isLoading = phase != null
    val title =
        when (phase) {
            RecognitionPhaseUi.Listening -> stringResource(R.string.music_recognition_listening)
            RecognitionPhaseUi.Processing -> stringResource(R.string.music_recognition_processing)
            null -> null
        }
    val icon =
        when (phase) {
            RecognitionPhaseUi.Listening -> R.drawable.listening
            RecognitionPhaseUi.Processing -> R.drawable.cached
            null -> R.drawable.mic
        }
    val heroShape = MaterialShapes.Cookie9Sided.toShape()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier.size(192.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (phase == RecognitionPhaseUi.Listening) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Surface(
                modifier = Modifier.size(152.dp),
                shape = heroShape,
                color =
                    if (isLoading) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                contentColor =
                    if (isLoading) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (phase == RecognitionPhaseUi.Processing) {
                        LoadingIndicator(
                            modifier = Modifier.size(72.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
        }

        title?.let {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (isLoading) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        } else {
            Button(
                onClick = onListen,
                modifier =
                    Modifier
                        .widthIn(min = 180.dp)
                        .heightIn(min = 56.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_tap_to_listen))
            }
        }
    }
}

@Composable
private fun RecognitionErrorState(
    error: MusicRecognitionErrorUi,
    onListen: () -> Unit,
    onAllowPermission: () -> Unit,
) {
    val isPermissionError = error == MusicRecognitionErrorUi.PermissionRequired
    val title =
        when (error) {
            MusicRecognitionErrorUi.PermissionRequired -> {
                stringResource(R.string.music_recognition_permission_title)
            }

            MusicRecognitionErrorUi.NoMatch -> {
                stringResource(R.string.music_recognition_no_match)
            }

            else -> {
                stringResource(R.string.music_recognition_error)
            }
        }
    val message =
        when (error) {
            MusicRecognitionErrorUi.PermissionRequired -> {
                stringResource(R.string.music_recognition_permission_desc)
            }

            MusicRecognitionErrorUi.SignatureFailed -> {
                stringResource(R.string.music_recognition_signature_failed)
            }

            MusicRecognitionErrorUi.RecordingFailed,
            MusicRecognitionErrorUi.RecognitionFailed,
            -> {
                stringResource(R.string.music_recognition_recognition_failed)
            }

            MusicRecognitionErrorUi.NoMatch -> {
                null
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier = Modifier.size(112.dp),
            shape = CircleShape,
            color =
                if (isPermissionError) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter =
                        painterResource(
                            if (isPermissionError) R.drawable.mic else R.drawable.search_off,
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint =
                        if (isPermissionError) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                )
            }
        }

        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = if (isPermissionError) onAllowPermission else onListen,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        stringResource(
                            if (isPermissionError) {
                                R.string.music_recognition_permission_action
                            } else {
                                R.string.music_recognition_listen_again
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionResultContent(
    track: RecognizedTrackUiModel,
    useWideLayout: Boolean,
    onListenAgain: () -> Unit,
    onSearch: () -> Unit,
    onOpenUri: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            if (useWideLayout) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ResultIdentity(
                        track = track,
                        artworkSize = 200.dp,
                        modifier = Modifier.weight(0.9f),
                    )
                    ResultSupportingContent(
                        track = track,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.weight(1.1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ResultIdentity(
                        track = track,
                        artworkSize = 184.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ResultSupportingContent(
                        track = track,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        ResultActions(
            onListenAgain = onListenAgain,
            onSearch = onSearch,
        )
    }
}

@Composable
private fun ResultIdentity(
    track: RecognizedTrackUiModel,
    artworkSize: Dp,
    modifier: Modifier = Modifier,
) {
    val artworkShape = MaterialShapes.Cookie4Sided.toShape()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverArt(
            artworkUrl = track.artworkUrl,
            displaySize = artworkSize,
            shape = artworkShape,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            track.album?.takeIf(String::isNotBlank)?.let {
                MetadataPill(iconRes = R.drawable.album, text = it)
            }
            track.metadata.takeIf(String::isNotBlank)?.let {
                MetadataPill(iconRes = R.drawable.info, text = it)
            }
            track.isrc?.let {
                MetadataPill(iconRes = R.drawable.link, text = it)
            }
        }
    }
}

@Composable
private fun ResultSupportingContent(
    track: RecognizedTrackUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shazamUrl = track.shazamUrl
    val openShazam: () -> Unit =
        remember(shazamUrl, onOpenUri) {
            { shazamUrl?.let(onOpenUri) }
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        track.lyricsPreview?.let { lyrics ->
            ResultInformationBlock(
                iconRes = R.drawable.lyrics,
                title = stringResource(R.string.music_recognition_lyrics_preview),
                body = lyrics,
            )
        }

        track.label?.let { label ->
            ResultInformationBlock(
                iconRes = R.drawable.info,
                title = label,
                body = null,
            )
        }

        if (shazamUrl != null) {
            OutlinedButton(
                onClick = openShazam,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.link),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.music_recognition_open_shazam))
            }
        }
    }
}

@Composable
private fun ResultInformationBlock(
    iconRes: Int,
    title: String,
    body: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ResultActions(
    onListenAgain: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        FilledTonalButton(
            onClick = onListenAgain,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            shapes =
                ButtonDefaults.shapes(
                    shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                    pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.replay),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.music_recognition_listen_again),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Button(
            onClick = onSearch,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            shapes =
                ButtonDefaults.shapes(
                    shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                    pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.search),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetadataPill(
    iconRes: Int,
    text: String,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicRecognitionSettingsBottomSheet(
    state: MusicRecognitionSettingsUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onBackgroundRecognitionEnabledChange: (Boolean) -> Unit,
) {
    val description =
        stringResource(
            if (state.backgroundRecognitionAvailable) {
                R.string.music_recognition_background_description
            } else {
                R.string.music_recognition_background_foss_description
            },
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.music_recognition_settings),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }

            SwitchPreference(
                title = {
                    Text(stringResource(R.string.music_recognition_background_title))
                },
                description = description,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.mic),
                        contentDescription = null,
                    )
                },
                checked = state.backgroundRecognitionEnabled,
                onCheckedChange = onBackgroundRecognitionEnabledChange,
                isEnabled = state.backgroundRecognitionAvailable,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.music_recognition_history),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }

                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                Text(stringResource(R.string.music_recognition_history_search))
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon =
                                if (state.query.isNotEmpty()) {
                                    {
                                        IconButton(onClick = clearQuery) {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = stringResource(R.string.clear),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                ) {}

                when {
                    state.allItems.items.isEmpty() -> {
                        RecognitionHistoryEmptyState(
                            iconRes = R.drawable.history,
                            title = stringResource(R.string.music_recognition_history_empty_title),
                            body = stringResource(R.string.music_recognition_history_empty_body),
                        )
                    }

                    state.filteredItems.items.isEmpty() -> {
                        RecognitionHistoryEmptyState(
                            iconRes = R.drawable.search_off,
                            title = stringResource(R.string.music_recognition_history_no_results_title),
                            body = stringResource(R.string.music_recognition_history_no_results_body),
                        )
                    }

                    else -> {
                        RecognitionHistoryList(
                            history = state.filteredItems,
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
private fun RecognitionHistoryList(
    history: RecognitionHistoryUiModel,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = history.items,
            key = { _, item -> item.stableKey },
            contentType = { _, _ -> RecognitionHistoryItemContentType },
        ) { index, item ->
            RecognitionHistoryListItem(
                item = item,
                index = index,
                count = history.items.size,
                onSearch = onSearch,
                onOpenUri = onOpenUri,
            )
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
    val searchAction = remember(item.searchQuery, onSearch) { { onSearch(item.searchQuery) } }
    val shazamUrl = item.shazamUrl
    val openShazamAction: () -> Unit =
        remember(shazamUrl, onOpenUri) {
            { shazamUrl?.let(onOpenUri) }
        }
    SegmentedListItem(
        onClick = searchAction,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            CoverArt(
                artworkUrl = item.artworkUrl,
                displaySize = 64.dp,
            )
        },
        overlineContent = {
            Text(
                text =
                    stringResource(
                        R.string.music_recognition_history_recognized_at,
                        item.recognizedAt,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = item.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.metadata.isNotEmpty()) {
                    Text(
                        text = item.metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shazamUrl != null) {
                    IconButton(onClick = openShazamAction) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = stringResource(R.string.music_recognition_open_shazam),
                        )
                    }
                }
                IconButton(onClick = searchAction) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search),
                    )
                }
            }
        },
        content = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun RecognitionHistoryEmptyState(
    iconRes: Int,
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CoverArt(
    artworkUrl: String?,
    displaySize: Dp,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(displaySize, density) { with(density) { displaySize.roundToPx() } }
    val imageRequest =
        remember(context, artworkUrl, sizePx) {
            artworkUrl?.let {
                ImageRequest
                    .Builder(context)
                    .data(it)
                    .size(sizePx, sizePx)
                    .allowHardware(true)
                    .build()
            }
        }

    Surface(
        modifier = Modifier.size(displaySize),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(displaySize / 3),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val RecognitionHistoryItemContentType = "recognition_history_item"
private const val MusicRecognitionStateItemContentType = "music_recognition_state"
private const val MusicRecognitionStateItemKey = "music_recognition_state"
private const val StateTransitionInitialScale = 0.96f
private const val StateTransitionTargetScale = 0.98f

private enum class MusicRecognitionContentKey {
    Empty,
    Listening,
    Processing,
    Success,
    Error,
}

private fun MusicRecognitionScreenState.contentKey(): MusicRecognitionContentKey =
    when (this) {
        is MusicRecognitionScreenState.Empty -> MusicRecognitionContentKey.Empty
        is MusicRecognitionScreenState.Loading ->
            when (phase) {
                RecognitionPhaseUi.Listening -> MusicRecognitionContentKey.Listening
                RecognitionPhaseUi.Processing -> MusicRecognitionContentKey.Processing
            }
        is MusicRecognitionScreenState.Success -> MusicRecognitionContentKey.Success
        is MusicRecognitionScreenState.Error -> MusicRecognitionContentKey.Error
    }
