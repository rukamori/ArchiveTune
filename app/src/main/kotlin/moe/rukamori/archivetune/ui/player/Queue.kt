/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import android.view.ViewTreeObserver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.oem.SystemMediaControlResolver
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.QueueViewModel

@Composable
fun Queue(
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    viewModel: QueueViewModel,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueVisible by viewModel.isQueueVisible.collectAsStateWithLifecycle()
    val latestQueueVisible by rememberUpdatedState(queueVisible)
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val chrome by viewModel.chrome.collectAsStateWithLifecycle()
    val latestChrome by rememberUpdatedState(chrome)
    val showSleepTimerDialog = chrome.showSleepTimer
    val model = when (val screen = screenState) {
        is QueueScreenState.Success -> screen.model
        is QueueScreenState.Error -> screen.previous
        else -> null
    }
    val haptic = LocalHapticFeedback.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val onAction: (QueueAction) -> Unit = remember(viewModel, haptic, enableHapticFeedback) {
        { action ->
            if (enableHapticFeedback && action is QueueAction.Select) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.onAction(action)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, playerConnection) {
        viewModel.bind(playerConnection)
        onDispose { viewModel.unbind(playerConnection) }
    }
    LaunchedEffect(queueVisible, model?.currentId, model?.autoplay, model?.entries?.values?.size, model?.title, model?.loadingRecommendations) {
        if (queueVisible && model?.autoplay == true) viewModel.visible()
    }
    LaunchedEffect(queueVisible, snackbar) {
        if (!queueVisible) snackbar.currentSnackbarData?.dismiss()
    }
    LaunchedEffect(viewModel, lifecycleOwner, menuState, navController, playerBottomSheetState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is QueueEvent.Message -> snackbar.showSnackbar(
                        context.getString(event.resource, *event.arguments.toTypedArray()),
                    )
                    is QueueEvent.Removed -> {
                        var undo = false
                        try {
                            if (latestQueueVisible) {
                                val message = if (event.count == 1) context.getString(R.string.removed_song_from_queue, event.title.orEmpty())
                                    else context.getString(R.string.removed_n_songs_from_queue, event.count)
                                undo = snackbar.showSnackbar(message, actionLabel = context.getString(R.string.undo)) == SnackbarResult.ActionPerformed
                            }
                        } finally { viewModel.finishRemoval(event.token, undo) }
                    }
                    is QueueEvent.Menu -> menuState.show {
                        PlayerMenu(
                            mediaMetadata = event.metadata,
                            navController = navController,
                            playerBottomSheetState = playerBottomSheetState,
                            isQueueTrigger = true,
                            sleepTimerEnabled = latestChrome.sleepTimerEnabled,
                            onSleepTimerClick = viewModel::sleepTimerClick,
                            onPlayNextFromQueue = if (event.canPlayNext) ({ onAction(QueueAction.PlayNext(event.id)) }) else null,
                            onRemoveFromQueue = { onAction(QueueAction.Remove(event.id)) },
                            onShowDetailsDialog = {
                                viewModel.dismissQueue()
                                bottomSheetPageState.show { ShowMediaInfo(event.metadata.id) }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            }
        }
    }
    AddToPlaylistDialog(
        isVisible = model?.playlistDialog == true,
        onGetSong = viewModel::prepareSelectedSongs,
        onDismiss = { onAction(QueueAction.DismissPlaylist) },
        onAddComplete = viewModel::playlistAdded,
    )
    if (model?.createPlaylistDialog == true) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.create_playlist)) },
            placeholder = { Text(stringResource(R.string.playlist_name)) },
            textFieldValue = model.playlistName,
            onTextFieldValueChange = { onAction(QueueAction.PlaylistName(it)) },
            enabled = !model.savingPlaylist,
            dismissOnDone = false,
            isInputValid = { it.isNotBlank() && model.selectionCount > 0 },
            onDismiss = { onAction(QueueAction.DismissPlaylist) },
            onDone = { onAction(QueueAction.CreatePlaylist(it)) },
        )
    }
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = viewModel::dismissSleepTimer,
            onConfirm = viewModel::startSleepTimer,
            onEndOfSong = { viewModel.startSleepTimer(-1) },
            value = chrome.sleepTimerValue,
            onValueChange = viewModel::sleepTimerValue,
        )
    }
    if (queueVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissQueue,
            sheetState = sheetState,
            sheetMaxWidth = Dp.Unspecified,
            modifier = Modifier.fillMaxSize(),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
            contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Box(Modifier.fillMaxSize()) {
                QueueScreen(
                    screenState = screenState,
                    visible = true,
                    pureBlack = pureBlack,
                    onAction = onAction,
                )
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(
                        bottom = (if ((model?.selectionCount ?: 0) > 0) 128.dp else 24.dp) +
                            WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun QueueFooter(
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    viewModel: QueueViewModel,
    peekHeight: Dp,
    onExpandQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    modifier: Modifier = Modifier,
    onAddToPlaylistClick: () -> Unit = {},
) {
    if (peekHeight <= 0.dp) return
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val chrome by viewModel.chrome.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val sleepTimerEnabled = chrome.sleepTimerEnabled
    val sleepTimerTimeLeft = chrome.sleepTimerTimeLeft
    val playerDesignStyle by rememberEnumPreference(key = PlayerDesignStyleKey, defaultValue = PlayerDesignStyle.V4)
    val (showCodecOnPlayer) = rememberPreference(booleanPreferencesKey("show_codec_on_player"), false)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(peekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .pointerInput(onExpandQueue) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount < 0f) {
                        change.consume()
                        onExpandQueue()
                    }
                }
            }
            .clickable(onClickLabel = stringResource(R.string.queue), onClick = onExpandQueue),
    ) {
        when (playerDesignStyle) {
            PlayerDesignStyle.V2 -> {
                QueueCollapsedContentV2(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    textButtonColor = textButtonColor,
                    iconButtonColor = iconButtonColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    repeatMode = repeatMode,
                    mediaMetadata = mediaMetadata,
                    onExpandQueue = onExpandQueue,
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                    onShowLyrics = onShowLyrics,
                    onRepeatModeClick = { viewModel.onAction(QueueAction.ToggleRepeat) },
                    onMenuClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                sleepTimerEnabled = sleepTimerEnabled,
                                onSleepTimerClick = {
                                    viewModel.sleepTimerClick()
                                },
                                onShowDetailsDialog = {
                                    mediaMetadata?.id?.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                )
            }

            PlayerDesignStyle.V3 -> {
                QueueCollapsedContentV3(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    onExpandQueue = onExpandQueue,
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                    onShowLyrics = onShowLyrics,
                    onMenuClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                sleepTimerEnabled = sleepTimerEnabled,
                                onSleepTimerClick = {
                                    viewModel.sleepTimerClick()
                                },
                                onShowDetailsDialog = {
                                    mediaMetadata?.id?.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                )
            }

            PlayerDesignStyle.V4 -> {
                QueueCollapsedContentV4(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    textButtonColor = textButtonColor,
                    iconButtonColor = iconButtonColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    mediaMetadata = mediaMetadata,
                    onExpandQueue = onExpandQueue,
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                    onShowLyrics = onShowLyrics,
                )
            }

            PlayerDesignStyle.V1 -> {
                QueueCollapsedContentV1(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    onExpandQueue = onExpandQueue,
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                    onShowLyrics = onShowLyrics,
                )
            }

            PlayerDesignStyle.V6 -> {
                QueueCollapsedContentV4(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    textButtonColor = textButtonColor,
                    iconButtonColor = iconButtonColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    mediaMetadata = mediaMetadata,
                    onExpandQueue = onExpandQueue,
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                    onShowLyrics = onShowLyrics,
                )
            }

            PlayerDesignStyle.V9 -> {
                val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
                QueueCollapsedContentV9(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    onShuffleClick = {
                        viewModel.onAction(QueueAction.ToggleShuffle)
                    },
                    onRepeatModeClick = { viewModel.onAction(QueueAction.ToggleRepeat) },
                    onMenuClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                sleepTimerEnabled = sleepTimerEnabled,
                                onSleepTimerClick = {
                                    viewModel.sleepTimerClick()
                                },
                                onShowDetailsDialog = {
                                    mediaMetadata?.id?.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                )
            }

            PlayerDesignStyle.V7 -> {
                val audioDevice by playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle()

                val view = LocalView.current
                DisposableEffect(view) {
                    val listener =
                        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                            if (hasFocus) playerConnection.service.refreshActiveDevice()
                        }
                    view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
                    onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
                }

                QueueCollapsedContentV7(
                    textBackgroundColor = Color.White,
                    onExpandQueue = onExpandQueue,
                    onShowLyrics = onShowLyrics,
                    onDeviceClick = {
                        SystemMediaControlResolver.openMediaOutputSwitcher(context)
                    },
                    onMusicTogetherClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigate("settings/music_together")
                    },
                    device = audioDevice,
                )
            }

            PlayerDesignStyle.V8 -> {
                val audioDevice by playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle()

                val view = LocalView.current
                DisposableEffect(view) {
                    val listener =
                        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                            if (hasFocus) playerConnection.service.refreshActiveDevice()
                        }
                    view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
                    onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
                }

                QueueCollapsedContentV8(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    textBackgroundColor = TextBackgroundColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    onExpandQueue = onExpandQueue,
                    onShowLyrics = onShowLyrics,
                    onSleepTimerClick = {
                        viewModel.sleepTimerClick()
                    },
                    onDeviceClick = {
                        SystemMediaControlResolver.openMediaOutputSwitcher(context)
                    },
                    device = audioDevice,
                )
            }

            PlayerDesignStyle.V10 -> {
                val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
                val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
                val currentSongLiked = currentSong?.song?.liked == true
                QueueCollapsedContentV10(
                    showCodecOnPlayer = showCodecOnPlayer,
                    currentFormat = currentFormat,
                    accent = TextBackgroundColor,
                    field = textButtonColor,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    currentSongLiked = currentSongLiked,
                    onExpandQueue = onExpandQueue,
                    onShuffleClick = { viewModel.onAction(QueueAction.ToggleShuffle) },
                    onRepeatModeClick = { viewModel.onAction(QueueAction.ToggleRepeat) },
                    onToggleLike = playerConnection::toggleLike,
                    onAddToPlaylistClick = onAddToPlaylistClick,
                )
            }

            PlayerDesignStyle.V5 -> Unit
        }
    }
}
