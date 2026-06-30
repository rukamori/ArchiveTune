/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.QueueEditLockKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheet
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaMetadataListItem
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onBackgroundColor: Color,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onShowLyrics: () -> Unit = {},
    pureBlack: Boolean,
) {
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboard.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentSongLiked = currentSong?.song?.liked == true

    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }
    var selection by remember { mutableStateOf(false) }

    fun clearSelection() {
        selection = false
        selectedSongs.clear()
        selectedItems.clear()
    }

    if (selection) {
        BackHandler {
            clearSelection()
        }
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)
    var infiniteQueueEnabled by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val infiniteQueueLoading by playerConnection.service.infiniteQueueLoading.collectAsState()
    val togetherSessionState by playerConnection.service.togetherSessionState.collectAsState()
    val togetherForcesLock =
        togetherSessionState is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
            (togetherSessionState as moe.rukamori.archivetune.together.TogetherSessionState.Joined).role is moe.rukamori.archivetune.together.TogetherRole.Guest
    val effectiveLocked = locked || togetherForcesLock

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateQueuePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            selectedSongs.map {
                database.withTransaction {
                    insert(it)
                }
                it.id
            }
        },
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            clearSelection()
        },
    )

    if (showCreateQueuePlaylistDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                )
            },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            placeholder = { Text(text = stringResource(R.string.playlist_name)) },
            initialTextFieldValue = TextFieldValue(queueTitle ?: context.getString(R.string.queue)),
            isInputValid = { it.trim().isNotEmpty() && selectedSongs.isNotEmpty() },
            onDismiss = { showCreateQueuePlaylistDialog = false },
            onDone = onDone@{ rawPlaylistName ->
                val playlistName = rawPlaylistName.trim()
                val songs = selectedSongs.toList()
                if (playlistName.isEmpty() || songs.isEmpty()) return@onDone

                coroutineScope.launch(Dispatchers.IO) {
                    val playlist =
                        PlaylistEntity(
                            name = playlistName,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                        )

                    database.withTransaction {
                        insert(playlist)
                        songs.forEachIndexed { index, song ->
                            insert(song)
                            insert(
                                PlaylistSongMap(
                                    playlistId = playlist.id,
                                    songId = song.id,
                                    position = index,
                                    setVideoId = song.setVideoId,
                                ),
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val message =
                            if (songs.size == 1) {
                                context.getString(R.string.added_to_playlist, playlistName)
                            } else {
                                context.getString(R.string.added_n_songs_to_playlist, songs.size, playlistName)
                            }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        clearSelection()
                    }
                }
            },
        )
    }

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindow =
        remember(currentWindowIndex, queueWindows) {
            queueWindows.getOrNull(currentWindowIndex)
        }

    val onRemoveWithUndo: (Timeline.Window) -> Unit = { window ->
        val index = window.firstPeriodIndex
        playerConnection.player.removeMediaItem(index)
        dismissJob?.cancel()
        dismissJob =
            coroutineScope.launch {
                val snackbarResult =
                    snackbarHostState.showSnackbar(
                        message =
                            context.getString(
                                R.string.removed_song_from_queue,
                                window.mediaItem.metadata?.title,
                            ),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    playerConnection.player.addMediaItem(window.mediaItem)
                    playerConnection.player.moveMediaItem(
                        playerConnection.player.mediaItemCount - 1,
                        index,
                    )
                }
            }
    }

    val onRemoveMultipleWithUndo: (List<Timeline.Window>) -> Unit = { windows ->
        if (windows.isNotEmpty()) {
            val sortedWindows = windows.sortedBy { it.firstPeriodIndex }
            var i = 0
            sortedWindows.forEach { window ->
                playerConnection.player.removeMediaItem(window.firstPeriodIndex - i++)
            }
            dismissJob?.cancel()
            dismissJob =
                coroutineScope.launch {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message =
                                if (windows.size == 1) {
                                    context.getString(
                                        R.string.removed_song_from_queue,
                                        windows
                                            .first()
                                            .mediaItem.metadata
                                            ?.title,
                                    )
                                } else {
                                    context.getString(R.string.removed_n_songs_from_queue, windows.size)
                                },
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        sortedWindows.forEach { window ->
                            playerConnection.player.addMediaItem(window.mediaItem)
                            playerConnection.player.moveMediaItem(
                                playerConnection.player.mediaItemCount - 1,
                                window.firstPeriodIndex,
                            )
                        }
                    }
                }
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableStateOf(30f) }
    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer.isActive
        }
    var sleepTimerTimeLeft by remember { mutableStateOf(0L) }

    val (showCodecOnPlayer) =
        rememberPreference(
            key = booleanPreferencesKey("show_codec_on_player"),
            defaultValue = false,
        )

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }
    val openQueue =
        remember(playerBottomSheetState, state) {
            {
                if (!playerBottomSheetState.isExpandedOrExpanding) {
                    playerBottomSheetState.expandSoft()
                }
                state.expandSoft()
            }
        }

    BottomSheet(
        state = state,
        backgroundColor = Color.Unspecified,
        modifier = modifier,
        collapsedContent = {
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
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
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
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
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

                PlayerDesignStyle.V5 -> {
                    QueueCollapsedContentV3(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
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
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
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
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
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
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerDesignStyle.V9 -> {
                    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
                    QueueCollapsedContentV9(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        onShuffleClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onMenuClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
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
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                    )
                }

                PlayerDesignStyle.V7, PlayerDesignStyle.V8 -> {
                    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
                    val activeDevice =
                        remember(audioManager) {
                            audioManager
                                .getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                                .firstOrNull {
                                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                                }?.productName
                                ?.toString() ?: "Speaker"
                        }
                    QueueCollapsedContentV7(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onShowLyrics = onShowLyrics,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onDeviceClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        deviceName = activeDevice,
                    )
                }
            }

            if (showSleepTimerDialog) {
                SleepTimerDialog(
                    onDismiss = { showSleepTimerDialog = false },
                    onConfirm = { minutes ->
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(minutes)
                    },
                    onEndOfSong = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(-1)
                    },
                    initialValue = sleepTimerValue,
                )
            }
        },
    ) {
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength by remember {
            derivedStateOf {
                queueWindows.sumOf { it.mediaItem.metadata?.duration ?: 0 }
            }
        }

        val headerItems = 1
        val lazyListState = rememberLazyListState()
        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        var shouldScrollToCurrent by remember { mutableStateOf(false) }
        var lastScrolledUid by remember { mutableStateOf<Long?>(null) }

        val currentPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                if (currentWindowIndex in queueWindows.indices) {
                    queueWindows[currentWindowIndex].uid
                } else {
                    null
                }
            }

        val reorderableState =
            rememberReorderableLazyListState(
                lazyListState = lazyListState,
                scrollThresholdPadding =
                    WindowInsets.systemBars
                        .add(
                            WindowInsets(
                                top = ListItemHeight,
                                bottom = ListItemHeight,
                            ),
                        ).asPaddingValues(),
            ) { from, to ->
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        from.index to to.index
                    } else {
                        currentDragInfo.first to to.index
                    }

                val safeFrom = (from.index - headerItems).coerceIn(0, mutableQueueWindows.lastIndex)
                val safeTo = (to.index - headerItems).coerceIn(0, mutableQueueWindows.lastIndex)

                mutableQueueWindows.move(safeFrom, safeTo)

                if (selection && currentWindowIndex in mutableQueueWindows.indices) {
                    val draggedItemUid = mutableQueueWindows[if (to.index > from.index) safeTo else safeFrom].uid
                    val currentItem = queueWindows.getOrNull(currentWindowIndex)

                    if (currentItem?.uid == draggedItemUid) {
                        val newIndex = mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
                        if (newIndex != -1) {
                            selectedSongs.clear()
                            selectedItems.clear()
                            mutableQueueWindows.getOrNull(newIndex)?.let { window ->
                                window.mediaItem.metadata?.let { metadata ->
                                    selectedSongs.add(metadata)
                                    selectedItems.add(window)
                                }
                            }
                        }
                    }
                }
            }

        LaunchedEffect(mutableQueueWindows) {
            if (mutableQueueWindows.isNotEmpty() && !shouldScrollToCurrent) {
                shouldScrollToCurrent = true
            }
        }

        LaunchedEffect(currentPlayingUid, shouldScrollToCurrent) {
            if (currentPlayingUid != null && shouldScrollToCurrent) {
                val indexInMutableList = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
                if (indexInMutableList != -1) {
                    lazyListState.scrollToItem(indexInMutableList + 1)
                }
            }
        }

        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                dragInfo?.let { (from, to) ->
                    val safeFrom = (from - headerItems).coerceIn(0, queueWindows.lastIndex)
                    val safeTo = (to - headerItems).coerceIn(0, queueWindows.lastIndex)

                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(safeFrom, safeTo)
                    } else {
                        playerConnection.localPlayer.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows
                                    .map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(safeFrom, safeTo)
                                    .toIntArray(),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    dragInfo = null
                }
            }
        }

        LaunchedEffect(queueWindows) {
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }
        }

        LaunchedEffect(state.isCollapsed) {
            if (!state.isCollapsed && currentPlayingUid != null) {
                val indexInMutableList = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
                if (indexInMutableList != -1) {
                    // Scroll to the item + headerItems (Spacer)
                    // The Spacer is at index 0, so the first song is at index 1.
                    // If indexInMutableList is 0 (first song), we want to scroll to index 1.
                    lazyListState.scrollToItem(indexInMutableList + 1)
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CurrentSongHeader(
                    sheetState = state,
                    mediaMetadata = mediaMetadata,
                    liked = currentSongLiked,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    shuffleModeEnabled = playerConnection.player.shuffleModeEnabled,
                    locked = effectiveLocked,
                    songCount = queueWindows.size,
                    queueDuration = queueLength,
                    infiniteQueueEnabled = infiniteQueueEnabled,
                    infiniteQueueLoading = infiniteQueueLoading,
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    onToggleLike = {
                        playerConnection.service.toggleLike()
                    },
                    onMenuClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                isQueueTrigger = true,
                                onRemoveFromQueue = {
                                    currentWindow?.let { onRemoveWithUndo(it) }
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
                    onClearQueueClick = {
                        val windowsToRemove =
                            if (currentWindowIndex in queueWindows.indices) {
                                queueWindows.filterIndexed { index, _ -> index != currentWindowIndex }
                            } else {
                                emptyList()
                            }

                        if (windowsToRemove.isNotEmpty()) {
                            onRemoveMultipleWithUndo(windowsToRemove)
                            selection = false
                            selectedSongs.clear()
                            selectedItems.clear()
                        }

                        if (infiniteQueueEnabled) {
                            infiniteQueueEnabled = false
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    },
                    onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                    onShuffleClick = {
                        coroutineScope.launch(Dispatchers.Main) {
                            playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                        }
                    },
                    onLockClick = {
                        if (togetherForcesLock) {
                            Toast.makeText(context, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                        } else {
                            locked = !locked
                        }
                    },
                    onInfiniteQueueClick = {
                        val nextInfiniteQueueEnabled = !infiniteQueueEnabled
                        infiniteQueueEnabled = nextInfiniteQueueEnabled
                        if (nextInfiniteQueueEnabled) {
                            playerConnection.service.onInfiniteQueueEnabled()
                        } else {
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    },
                )

                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(
                                WindowInsets(
                                    bottom = ListItemHeight + if (selection) 88.dp else 8.dp,
                                ),
                            ).asPaddingValues(),
                    modifier =
                        Modifier
                            .weight(1f)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection),
                ) {
                    item {
                        Spacer(
                            modifier =
                                Modifier
                                    .animateContentSize()
                                    .height(if (selection) 48.dp else 0.dp),
                        )
                    }

                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, item -> item.uid.hashCode() },
                    ) { index, window ->
                        ReorderableItem(
                            state = reorderableState,
                            key = window.uid.hashCode(),
                        ) {
                            val currentItem by rememberUpdatedState(window)
                            val isActive = window.uid == currentPlayingUid
                            val dismissBoxState =
                                rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance },
                                )

                            var processedDismiss by remember { mutableStateOf(false) }
                            LaunchedEffect(dismissBoxState.currentValue) {
                                val dv = dismissBoxState.currentValue
                                if (!processedDismiss && (
                                        dv == SwipeToDismissBoxValue.StartToEnd ||
                                            dv == SwipeToDismissBoxValue.EndToStart
                                    )
                                ) {
                                    processedDismiss = true
                                    onRemoveWithUndo(currentItem)
                                }
                                if (dv == SwipeToDismissBoxValue.Settled) {
                                    processedDismiss = false
                                }
                            }

                            val content: @Composable () -> Unit = {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier =
                                        Modifier.graphicsLayer {
                                            // Enable hardware acceleration for smoother dragging
                                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                        },
                                ) {
                                    val shouldLoadImages by remember {
                                        derivedStateOf {
                                            state.value > state.collapsedBound + 80.dp
                                        }
                                    }

                                    val trackMetadata = window.mediaItem.metadata ?: return@Row
                                    MediaMetadataListItem(
                                        mediaMetadata = trackMetadata,
                                        isSelected = selection && trackMetadata in selectedSongs,
                                        isActive = isActive,
                                        isPlaying = isPlaying && isActive,
                                        shouldLoadImage = shouldLoadImages,
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    menuState.show {
                                                        PlayerMenu(
                                                            mediaMetadata = trackMetadata,
                                                            navController = navController,
                                                            playerBottomSheetState = playerBottomSheetState,
                                                            isQueueTrigger = true,
                                                            onRemoveFromQueue = {
                                                                onRemoveWithUndo(window)
                                                            },
                                                            onShowDetailsDialog = {
                                                                window.mediaItem.mediaId.let {
                                                                    bottomSheetPageState.show {
                                                                        ShowMediaInfo(it)
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.more_vert),
                                                    contentDescription = null,
                                                )
                                            }
                                            if (!effectiveLocked) {
                                                IconButton(
                                                    onClick = { },
                                                    modifier =
                                                        Modifier
                                                            .draggableHandle()
                                                            .graphicsLayer {
                                                                // Improve touch response
                                                                alpha = 0.99f
                                                            },
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.drag_handle),
                                                        contentDescription = null,
                                                    )
                                                }
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(backgroundColor)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (selection) {
                                                            if (trackMetadata in selectedSongs) {
                                                                selectedSongs.remove(trackMetadata)
                                                                selectedItems.remove(currentItem)
                                                                if (selectedSongs.isEmpty()) {
                                                                    selection = false
                                                                }
                                                            } else {
                                                                selectedSongs.add(trackMetadata)
                                                                selectedItems.add(currentItem)
                                                            }
                                                        } else {
                                                            if (index == currentWindowIndex) {
                                                                playerConnection.player.togglePlayPause()
                                                            } else {
                                                                val joined =
                                                                    togetherSessionState as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                                                val isGuest = joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest
                                                                if (isGuest) {
                                                                    if (joined?.roomState?.settings?.allowGuestsToControlPlayback != true) {
                                                                        Toast
                                                                            .makeText(
                                                                                context,
                                                                                R.string.not_allowed,
                                                                                Toast.LENGTH_SHORT,
                                                                            ).show()
                                                                        return@combinedClickable
                                                                    }
                                                                    val trackId =
                                                                        window.mediaItem.metadata?.id?.trim().orEmpty().ifBlank {
                                                                            window.mediaItem.mediaId.trim()
                                                                        }
                                                                    if (trackId.isBlank()) return@combinedClickable
                                                                    Toast
                                                                        .makeText(
                                                                            context,
                                                                            R.string.together_requesting_song_change,
                                                                            Toast.LENGTH_SHORT,
                                                                        ).show()
                                                                    playerConnection.service.requestTogetherControl(
                                                                        moe.rukamori.archivetune.together.ControlAction.SeekToTrack(
                                                                            trackId = trackId,
                                                                            positionMs = 0L,
                                                                        ),
                                                                    )
                                                                    shouldScrollToCurrent = false
                                                                } else {
                                                                    playerConnection.player.seekToDefaultPosition(
                                                                        window.firstPeriodIndex,
                                                                    )
                                                                    playerConnection.player.playWhenReady = true
                                                                    shouldScrollToCurrent = false
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!selection) {
                                                            selection = true
                                                        }
                                                        selectedSongs.clear()
                                                        selectedItems.clear()
                                                        selectedSongs.add(trackMetadata)
                                                        selectedItems.add(currentItem)
                                                    },
                                                ),
                                    )
                                }
                            }

                            if (effectiveLocked) {
                                content()
                            } else {
                                SwipeToDismissBox(
                                    state = dismissBoxState,
                                    backgroundContent = {},
                                ) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .padding(
                                bottom =
                                    (if (selection) ListItemHeight * 2 + 16.dp else ListItemHeight) +
                                        WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateBottomPadding(),
                            ).align(Alignment.BottomCenter),
                )

                AnimatedVisibility(
                    visible = selection,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom =
                                    ListItemHeight +
                                        WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateBottomPadding(),
                            ),
                ) {
                    QueueSelectionFloatingToolbar(
                        allSelected = selectedSongs.size == mutableQueueWindows.size,
                        pureBlack = pureBlack,
                        onClose = ::clearSelection,
                        onToggleSelectAll = {
                            if (selectedSongs.size == mutableQueueWindows.size) {
                                clearSelection()
                            } else {
                                selectedSongs.clear()
                                selectedItems.clear()
                                mutableQueueWindows.forEach { window ->
                                    window.mediaItem.metadata?.let { metadata ->
                                        selectedSongs.add(metadata)
                                        selectedItems.add(window)
                                    }
                                }
                            }
                        },
                        onAddToPlaylist = { showChoosePlaylistDialog = true },
                        onCreatePlaylist = { showCreateQueuePlaylistDialog = true },
                        onDelete = {
                            onRemoveMultipleWithUndo(selectedItems.toList())
                            clearSelection()
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSelectionFloatingToolbar(
    allSelected: Boolean,
    pureBlack: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val toolbarContainerColor = if (pureBlack) Color.Black else colorScheme.surfaceContainerHigh
    val toolbarContentColor = if (pureBlack) Color.White else colorScheme.onSurface
    val fabContainerColor = if (pureBlack) Color.White.copy(alpha = 0.12f) else colorScheme.surfaceContainerHighest
    val fabContentColor = if (pureBlack) Color.White else colorScheme.onSurface

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.widthIn(max = 420.dp),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onClose,
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = toolbarContainerColor,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueSelectionToolbarAction(
                icon = if (allSelected) R.drawable.deselect else R.drawable.select_all,
                contentDescription = null,
                tint = toolbarContentColor,
                onClick = onToggleSelectAll,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.playlist_add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = colorScheme.primary,
                onClick = onAddToPlaylist,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.queue_music,
                contentDescription = stringResource(R.string.create_playlist),
                tint = colorScheme.primary,
                onClick = onCreatePlaylist,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.delete,
                contentDescription = stringResource(R.string.delete),
                tint = colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun QueueSelectionToolbarAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}
