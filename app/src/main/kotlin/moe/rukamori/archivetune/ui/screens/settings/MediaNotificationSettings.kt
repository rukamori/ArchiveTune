/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.notification.MediaNotificationAction
import moe.rukamori.archivetune.playback.notification.MediaNotificationChannelImportance
import moe.rukamori.archivetune.playback.notification.MediaNotificationConfiguration
import moe.rukamori.archivetune.playback.notification.MediaNotificationHealth
import moe.rukamori.archivetune.playback.notification.MediaNotificationNavigation
import moe.rukamori.archivetune.playback.notification.MediaNotificationSystemStatus
import moe.rukamori.archivetune.ui.component.BackAwareModalBottomSheet
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupTitle
import moe.rukamori.archivetune.ui.component.SegmentedPreference
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.viewmodels.MediaNotificationSettingsEvent
import moe.rukamori.archivetune.viewmodels.MediaNotificationSettingsState
import moe.rukamori.archivetune.viewmodels.MediaNotificationSettingsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.IconButton as MaterialIconButton

private val PreviewCornerRadius = 32.dp
private val PreviewArtworkSize = 64.dp
private val PreviewPlayButtonSize = 56.dp
private val PreviewControlSize = 44.dp
private val SlotOuterCornerRadius = 24.dp
private val SlotInnerCornerRadius = 8.dp
private val SlotBadgeSize = 48.dp
private val SlotGap = 2.dp
private val PreferenceGroupInset = 30.dp

private val SlotBadgeShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBurst,
)

private const val SlotKeyPrefix = "slot_"

@Composable
fun MediaNotificationSettings(
    navController: NavController,
    viewModel: MediaNotificationSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSystemStatus() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is MediaNotificationSettingsEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshSystemStatus()
        }

    val openAppNotificationSettings = {
        context.launchSystemSettings(appNotificationSettingsIntent(context), viewModel::onExternalActionFailed)
    }
    val openChannelSettings: (MediaNotificationSystemStatus) -> Unit = { status ->
        if (status.channelImportance == MediaNotificationChannelImportance.NOT_CREATED) {
            openAppNotificationSettings()
        } else {
            context.launchSystemSettings(playerChannelSettingsIntent(context), viewModel::onExternalActionFailed)
        }
    }
    val requestPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_media_notification_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = stringResource(R.string.settings_media_notification_subtitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                actions = {
                    val ready = state as? MediaNotificationSettingsState.Ready
                    MaterialIconButton(
                        onClick = { showResetDialog = true },
                        enabled = ready != null && !ready.isDefault,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.restore),
                            contentDescription = stringResource(R.string.media_notification_reset),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val current = state) {
                MediaNotificationSettingsState.Loading ->
                    LoadingIndicator(modifier = Modifier.padding(top = 96.dp))

                is MediaNotificationSettingsState.Error ->
                    ErrorContent(messageRes = current.messageRes)

                is MediaNotificationSettingsState.Ready ->
                    MediaNotificationContent(
                        state = current,
                        reducedMotion = reducedMotion,
                        bottomPadding =
                            LocalPlayerAwareWindowInsets.current
                                .only(WindowInsetsSides.Bottom)
                                .asPaddingValues()
                                .calculateBottomPadding() + SettingsDimensions.ScreenBottomPadding,
                        onRequestPermission = requestPermission,
                        onOpenAppNotificationSettings = openAppNotificationSettings,
                        onOpenChannelSettings = { openChannelSettings(current.systemStatus) },
                        onAddClick = { showAddSheet = true },
                        onRemove = viewModel::removeAction,
                        onMoveBy = viewModel::moveActionBy,
                        onReorder = viewModel::onReorder,
                        onReorderFinished = viewModel::onReorderFinished,
                        onNavigationChange = viewModel::setNavigation,
                        onSeekIncrementChange = viewModel::setSeekIncrement,
                        onSleepTimerMinutesChange = viewModel::setSleepTimerMinutes,
                        onStopOnDismissChange = viewModel::setStopOnDismiss,
                    )
            }
        }
    }

    val ready = state as? MediaNotificationSettingsState.Ready

    if (showAddSheet && ready != null) {
        AddActionSheet(
            actions = ready.availableActions,
            canAdd = ready.canAddAction,
            onAdd = { action ->
                viewModel.addAction(action)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(painterResource(R.drawable.restore), contentDescription = null) },
            title = { Text(stringResource(R.string.media_notification_reset_confirm_title)) },
            text = { Text(stringResource(R.string.media_notification_reset_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetToDefaults()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun MediaNotificationContent(
    state: MediaNotificationSettingsState.Ready,
    reducedMotion: Boolean,
    bottomPadding: Dp,
    onRequestPermission: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenChannelSettings: () -> Unit,
    onAddClick: () -> Unit,
    onRemove: (MediaNotificationAction) -> Unit,
    onMoveBy: (MediaNotificationAction, Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit,
    onNavigationChange: (MediaNotificationNavigation) -> Unit,
    onSeekIncrementChange: (Int) -> Unit,
    onSleepTimerMinutesChange: (Int) -> Unit,
    onStopOnDismissChange: (Boolean) -> Unit,
) {
    val configuration = state.configuration
    val actions = configuration.actions
    val currentActions by rememberUpdatedState(actions)
    val preview = rememberNotificationPreviewState()
    val haptics = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = currentActions.indexOfFirst { it.slotKey == from.key }
            val toIndex = currentActions.indexOfFirst { it.slotKey == to.key }
            if (fromIndex >= 0 && toIndex >= 0) onReorder(fromIndex, toIndex)
        }
    val moveUpLabel = stringResource(R.string.queue_move_up)
    val moveDownLabel = stringResource(R.string.queue_move_down)
    val health = state.systemStatus.health

    LazyColumn(
        state = lazyListState,
        modifier =
            Modifier
                .widthIn(max = SettingsDimensions.HubContentMaxWidth)
                .fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(SlotGap),
    ) {
        item(key = "preview", contentType = "preview") {
            NotificationPreviewCard(
                configuration = configuration,
                preview = preview,
                reducedMotion = reducedMotion,
                modifier =
                    itemMotion(reducedMotion)
                        .padding(horizontal = PreferenceGroupInset),
            )
        }

        if (health != MediaNotificationHealth.READY) {
            item(key = "health", contentType = "group") {
                PreferenceGroup(
                    modifier =
                        itemMotion(reducedMotion)
                            .padding(top = 12.dp),
                ) {
                    item {
                        HealthPreference(
                            health = health,
                            onRequestPermission = onRequestPermission,
                            onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                            onOpenChannelSettings = onOpenChannelSettings,
                        )
                    }
                }
            }
        }

        item(key = "buttons_header", contentType = "header") {
            Row(
                modifier =
                    itemMotion(reducedMotion)
                        .fillMaxWidth()
                        .padding(start = PreferenceGroupInset, end = PreferenceGroupInset, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreferenceGroupTitle(
                    title = stringResource(R.string.media_notification_buttons_title),
                    modifier = Modifier.weight(1f),
                )
                SlotCounter(
                    used = actions.size,
                    total = MediaNotificationAction.MAX_ACTIONS,
                    reducedMotion = reducedMotion,
                )
            }
        }

        if (actions.isEmpty()) {
            item(key = "buttons_empty", contentType = "empty") {
                EmptySlots(
                    modifier =
                        itemMotion(reducedMotion)
                            .padding(horizontal = PreferenceGroupInset),
                )
            }
        }

        itemsIndexed(
            items = actions,
            key = { _, action -> action.slotKey },
            contentType = { _, _ -> "slot" },
        ) { index, action ->
            ReorderableItem(
                state = reorderState,
                key = action.slotKey,
                animateItemModifier = itemMotion(reducedMotion),
            ) { dragging ->
                val label = stringResource(action.labelRes())
                val accessibility =
                    Modifier.semantics {
                        customActions =
                            buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { onMoveBy(action, -1); true })
                                if (index < actions.lastIndex) add(CustomAccessibilityAction(moveDownLabel) { onMoveBy(action, 1); true })
                            }
                    }
                ActionSlotRow(
                    action = action,
                    position = index + 1,
                    isFirst = index == 0,
                    isLast = index == actions.lastIndex,
                    dragging = dragging,
                    reducedMotion = reducedMotion,
                    onRemove = { onRemove(action) },
                    modifier =
                        Modifier
                            .padding(horizontal = PreferenceGroupInset)
                            .then(accessibility),
                    handle = {
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .draggableHandle(
                                        enabled = !state.isReordering || dragging,
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = onReorderFinished,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = stringResource(R.string.media_notification_move_button, label),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        item(key = "add_button", contentType = "group") {
            val full = actions.size >= MediaNotificationAction.MAX_ACTIONS
            PreferenceGroup(
                modifier =
                    itemMotion(reducedMotion)
                        .padding(top = 10.dp),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.media_notification_add_button)) },
                        description =
                            if (full) {
                                stringResource(R.string.media_notification_buttons_full, MediaNotificationAction.MAX_ACTIONS)
                            } else {
                                stringResource(R.string.media_notification_buttons_desc, MediaNotificationAction.MAX_ACTIONS)
                            },
                        icon = { Icon(painterResource(R.drawable.add), null) },
                        trailingContent = { EntryChevron() },
                        onClick = onAddClick,
                        isEnabled = state.canAddAction,
                    )
                }
            }
        }

        item(key = "behavior", contentType = "group") {
            PreferenceGroup(
                title = stringResource(R.string.media_notification_behavior_title),
                modifier =
                    itemMotion(reducedMotion)
                        .padding(top = 8.dp),
            ) {
                item {
                    SegmentedPreference(
                        title = { Text(stringResource(R.string.media_notification_navigation_title)) },
                        description = stringResource(R.string.media_notification_navigation_desc),
                        icon = { Icon(painterResource(R.drawable.skip_next), null) },
                        selectedValue = configuration.navigation,
                        values = MediaNotificationNavigation.entries,
                        valueText = { stringResource(it.labelRes()) },
                        onValueSelected = onNavigationChange,
                    )
                }
                item(visible = configuration.navigation == MediaNotificationNavigation.SEEK) {
                    SegmentedPreference(
                        title = { Text(stringResource(R.string.media_notification_seek_increment)) },
                        icon = { Icon(painterResource(R.drawable.replay), null) },
                        selectedValue = configuration.seekIncrementSeconds,
                        values = MediaNotificationConfiguration.SeekIncrementOptions,
                        valueText = { stringResource(R.string.media_notification_seconds_short, it) },
                        onValueSelected = onSeekIncrementChange,
                    )
                }
                item(visible = MediaNotificationAction.SLEEP_TIMER in actions) {
                    ListPreference(
                        title = { Text(stringResource(R.string.media_notification_sleep_timer_title)) },
                        description = stringResource(R.string.media_notification_sleep_timer_desc),
                        icon = { Icon(painterResource(R.drawable.bedtime), null) },
                        selectedValue = configuration.sleepTimerMinutes,
                        values = MediaNotificationConfiguration.SleepTimerOptions,
                        valueText = { pluralStringResource(R.plurals.minute, it, it) },
                        onValueSelected = onSleepTimerMinutesChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.media_notification_dismiss_title)) },
                        description = stringResource(R.string.media_notification_dismiss_desc),
                        icon = { Icon(painterResource(R.drawable.close), null) },
                        checked = configuration.stopOnDismiss,
                        onCheckedChange = onStopOnDismissChange,
                    )
                }
            }
        }

        item(key = "system", contentType = "group") {
            PreferenceGroup(
                title = stringResource(R.string.media_notification_system_title),
                modifier = itemMotion(reducedMotion),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.media_notification_channel)) },
                        description = stringResource(state.systemStatus.channelImportance.labelRes()),
                        icon = { Icon(painterResource(R.drawable.notifications), null) },
                        trailingContent = { EntryChevron() },
                        onClick = onOpenChannelSettings,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.media_notification_app_notifications)) },
                        description = stringResource(R.string.media_notification_app_notifications_desc),
                        icon = { Icon(painterResource(R.drawable.settings), null) },
                        trailingContent = { EntryChevron() },
                        onClick = onOpenAppNotificationSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthPreference(
    health: MediaNotificationHealth,
    onRequestPermission: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenChannelSettings: () -> Unit,
) {
    val (titleRes, descriptionRes) = health.messageRes()
    val onClick: (() -> Unit)? =
        when (health) {
            MediaNotificationHealth.PERMISSION_REQUIRED -> onRequestPermission
            MediaNotificationHealth.APP_BLOCKED -> onOpenAppNotificationSettings
            MediaNotificationHealth.CHANNEL_BLOCKED -> onOpenChannelSettings
            MediaNotificationHealth.CHANNEL_NOT_CREATED,
            MediaNotificationHealth.READY,
            -> null
        }
    PreferenceEntry(
        title = {
            Text(
                text = stringResource(titleRes),
                color = if (onClick != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        },
        description = stringResource(descriptionRes),
        icon = {
            Icon(
                painter = painterResource(if (onClick != null) R.drawable.error else R.drawable.info),
                contentDescription = null,
            )
        },
        trailingContent = if (onClick != null) { { EntryChevron() } } else null,
        onClick = onClick,
    )
}

@Composable
private fun EntryChevron() {
    Icon(
        painter = painterResource(R.drawable.arrow_forward),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
    )
}

@Immutable
private data class NotificationPreviewState(
    val title: String? = null,
    val artists: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val liked: Boolean = false,
    val inLibrary: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val downloadState: Int? = null,
    val sleepTimerActive: Boolean = false,
    val hasSong: Boolean = false,
    val isPodcast: Boolean = false,
    val isLocal: Boolean = false,
) {
    val hasMedia: Boolean
        get() = title != null
}

@Composable
private fun rememberNotificationPreviewState(): NotificationPreviewState {
    val playerConnection = LocalPlayerConnection.current ?: return NotificationPreviewState()
    val downloadUtil = LocalDownloadUtil.current
    val metadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val song by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val mediaId = metadata?.id
    val downloadFlow = remember(mediaId, downloadUtil) { mediaId?.let(downloadUtil::getDownload) ?: flowOf(null) }
    val download by downloadFlow.collectAsStateWithLifecycle(initialValue = null)
    val sleepTimerActive = playerConnection.service.sleepTimer.isActive
    val current = metadata ?: return NotificationPreviewState(isPlaying = isPlaying, repeatMode = repeatMode, shuffleEnabled = shuffleEnabled, sleepTimerActive = sleepTimerActive)
    return NotificationPreviewState(
        title = current.title,
        artists = current.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
        artworkUrl = current.thumbnailUrl,
        isPlaying = isPlaying,
        liked = song?.song?.liked == true,
        inLibrary = song?.song?.inLibrary != null,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        downloadState = download?.state,
        sleepTimerActive = sleepTimerActive,
        hasSong = song != null,
        isPodcast = current.isPodcast,
        isLocal = current.id.isLocalMediaId() || song?.song?.isLocal == true,
    )
}

@Composable
private fun NotificationPreviewCard(
    configuration: MediaNotificationConfiguration,
    preview: NotificationPreviewState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val actionLabels = configuration.actions.map { stringResource(it.labelRes()) }
    val buttonsDescription =
        stringResource(
            R.string.media_notification_preview_buttons_a11y,
            actionLabels.ifEmpty { listOf(stringResource(R.string.media_notification_buttons_empty)) }.joinToString(),
        )
    val glyphRotation =
        if (reducedMotion || !preview.isPlaying) {
            0f
        } else {
            val transition = rememberInfiniteTransition(label = "preview_glyph")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
                label = "preview_glyph_rotation",
            )
            rotation
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(PreviewCornerRadius),
            color = colors.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 56.dp, y = (-56).dp)
                            .size(180.dp)
                            .graphicsLayer { rotationZ = glyphRotation }
                            .clip(MaterialShapes.SoftBurst.toShape())
                            .background(colors.primaryContainer.copy(alpha = 0.45f)),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .semantics(mergeDescendants = true) {},
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.small_icon),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.media_notification_preview_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSecondaryContainer,
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(colors.secondaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    PreviewMetadata(preview = preview, reducedMotion = reducedMotion)
                    Spacer(Modifier.size(18.dp))
                    PreviewTransport(
                        configuration = configuration,
                        preview = preview,
                        reducedMotion = reducedMotion,
                    )
                    Spacer(Modifier.size(12.dp))
                    PreviewActionTray(
                        actions = configuration.actions,
                        preview = preview,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.semantics { contentDescription = buttonsDescription },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.media_notification_preview_caption),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
        )
    }
}

@Composable
private fun PreviewMetadata(
    preview: NotificationPreviewState,
    reducedMotion: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val artworkShape = MaterialShapes.Cookie9Sided.toShape()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(PreviewArtworkSize)
                    .clip(artworkShape)
                    .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = colors.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
            )
            if (preview.artworkUrl != null) {
                AsyncImage(
                    model = preview.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        val fadeSpec = effectsSpec<Float>(reducedMotion)
        AnimatedContent(
            targetState = preview.title to preview.artists,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "preview_metadata",
            modifier = Modifier.weight(1f),
        ) { (title, artists) ->
            Column {
                Text(
                    text = title ?: stringResource(R.string.media_notification_preview_empty),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (title != null) artists.orEmpty() else stringResource(R.string.media_notification_preview_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PreviewTransport(
    configuration: MediaNotificationConfiguration,
    preview: NotificationPreviewState,
    reducedMotion: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val seek = configuration.navigation == MediaNotificationNavigation.SEEK
    val playCorner by animateIntAsState(
        targetValue = if (preview.isPlaying) 30 else 50,
        animationSpec = spatialSpec(reducedMotion),
        label = "preview_play_corner",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewNavigationGlyph(forward = false, seek = seek, seconds = configuration.seekIncrementSeconds, reducedMotion = reducedMotion)
        Box(
            modifier =
                Modifier
                    .size(PreviewPlayButtonSize)
                    .clip(RoundedCornerShape(playCorner))
                    .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            val fadeSpec = effectsSpec<Float>(reducedMotion)
            val scaleSpec = spatialSpec<Float>(reducedMotion)
            AnimatedContent(
                targetState = preview.isPlaying,
                transitionSpec = {
                    (fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = 0.6f)) togetherWith
                        (fadeOut(fadeSpec) + scaleOut(scaleSpec, targetScale = 0.6f))
                },
                label = "preview_play_icon",
            ) { playing ->
                Icon(
                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        PreviewNavigationGlyph(forward = true, seek = seek, seconds = configuration.seekIncrementSeconds, reducedMotion = reducedMotion)
    }
}

@Composable
private fun PreviewNavigationGlyph(
    forward: Boolean,
    seek: Boolean,
    seconds: Int,
    reducedMotion: Boolean,
) {
    Box(
        modifier = Modifier.size(PreviewControlSize),
        contentAlignment = Alignment.Center,
    ) {
        val fadeSpec = effectsSpec<Float>(reducedMotion)
        val scaleSpec = spatialSpec<Float>(reducedMotion)
        AnimatedContent(
            targetState = if (seek) seconds else null,
            transitionSpec = {
                (fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = 0.7f)) togetherWith
                    (fadeOut(fadeSpec) + scaleOut(scaleSpec, targetScale = 0.7f))
            },
            label = "preview_navigation",
        ) { seekSeconds ->
            if (seekSeconds == null) {
                Icon(
                    painter = painterResource(if (forward) R.drawable.skip_next else R.drawable.skip_previous),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                SeekGlyph(forward = forward, seconds = seekSeconds, size = 30.dp)
            }
        }
    }
}

@Composable
private fun SeekGlyph(
    forward: Boolean,
    seconds: Int,
    size: Dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(if (forward) R.drawable.seek_forward else R.drawable.replay),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = seconds.toString(),
            color = tint,
            fontSize = (size.value * 0.3f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.offset(y = size * 0.04f),
        )
    }
}

@Composable
private fun PreviewActionTray(
    actions: List<MediaNotificationAction>,
    preview: NotificationPreviewState,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerHighest)
                .animateContentSize(spatialSpec(reducedMotion))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (actions.isEmpty()) {
            Text(
                text = stringResource(R.string.media_notification_buttons_empty),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    key(action) {
                        val visibility = remember { MutableTransitionState(reducedMotion).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = visibility,
                            enter = fadeIn(effectsSpec(reducedMotion)) + scaleIn(spatialSpec(reducedMotion), initialScale = 0.4f),
                        ) {
                            PreviewActionGlyph(action = action, preview = preview, reducedMotion = reducedMotion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewActionGlyph(
    action: MediaNotificationAction,
    preview: NotificationPreviewState,
    reducedMotion: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val visual = action.previewVisual(preview)
    val containerAlpha by animateFloatAsState(
        targetValue = if (visual.active) 1f else 0f,
        animationSpec = effectsSpec(reducedMotion),
        label = "preview_action_active",
    )
    Box(
        modifier =
            Modifier
                .size(PreviewControlSize)
                .clip(CircleShape)
                .background(colors.primaryContainer.copy(alpha = containerAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        val fadeSpec = effectsSpec<Float>(reducedMotion)
        val scaleSpec = spatialSpec<Float>(reducedMotion)
        AnimatedContent(
            targetState = visual.iconRes,
            transitionSpec = {
                (fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = 0.6f)) togetherWith
                    fadeOut(fadeSpec)
            },
            label = "preview_action_icon",
        ) { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint =
                    when {
                        !visual.enabled -> colors.onSurface.copy(alpha = 0.38f)
                        visual.active -> colors.onPrimaryContainer
                        else -> colors.onSurfaceVariant
                    },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Immutable
private data class PreviewActionVisual(
    @param:DrawableRes val iconRes: Int,
    val active: Boolean,
    val enabled: Boolean,
)

private fun MediaNotificationAction.previewVisual(preview: NotificationPreviewState): PreviewActionVisual =
    when (this) {
        MediaNotificationAction.LIKE ->
            PreviewActionVisual(
                iconRes = if (preview.liked) R.drawable.favorite else R.drawable.favorite_border,
                active = preview.liked,
                enabled = preview.hasSong,
            )

        MediaNotificationAction.REPEAT ->
            PreviewActionVisual(
                iconRes =
                    when (preview.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                        Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                        else -> R.drawable.repeat
                    },
                active = preview.repeatMode != Player.REPEAT_MODE_OFF,
                enabled = true,
            )

        MediaNotificationAction.SHUFFLE ->
            PreviewActionVisual(
                iconRes = if (preview.shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                active = preview.shuffleEnabled,
                enabled = true,
            )

        MediaNotificationAction.START_RADIO ->
            PreviewActionVisual(
                iconRes = R.drawable.radio,
                active = false,
                enabled = preview.hasSong && !preview.isPodcast,
            )

        MediaNotificationAction.LIBRARY ->
            PreviewActionVisual(
                iconRes = if (preview.inLibrary) R.drawable.library_add_check else R.drawable.library_add,
                active = preview.inLibrary,
                enabled = preview.hasSong,
            )

        MediaNotificationAction.DOWNLOAD ->
            PreviewActionVisual(
                iconRes = if (preview.downloadState == Download.STATE_COMPLETED) R.drawable.offline else R.drawable.download,
                active = preview.downloadState == Download.STATE_COMPLETED,
                enabled = preview.hasMedia && !preview.isLocal && preview.downloadState != Download.STATE_REMOVING,
            )

        MediaNotificationAction.SLEEP_TIMER ->
            PreviewActionVisual(
                iconRes = if (preview.sleepTimerActive) R.drawable.bedtime else R.drawable.timer,
                active = preview.sleepTimerActive,
                enabled = preview.sleepTimerActive || preview.hasMedia,
            )

        MediaNotificationAction.CLOSE ->
            PreviewActionVisual(
                iconRes = R.drawable.close,
                active = false,
                enabled = preview.hasMedia,
            )
    }

@Composable
private fun SlotCounter(
    used: Int,
    total: Int,
    reducedMotion: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val full = used >= total
    val container by animateColorAsState(
        targetValue = if (full) colors.tertiaryContainer else colors.secondaryContainer,
        animationSpec = effectsSpec(reducedMotion),
        label = "slot_counter_container",
    )
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fadeSpec = effectsSpec<Float>(reducedMotion)
        val slideSpec = spatialSpec<IntOffset>(reducedMotion)
        AnimatedContent(
            targetState = used,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    fadeIn(fadeSpec) +
                        slideInVertically(slideSpec) { it * direction }
                ) togetherWith (
                    fadeOut(fadeSpec) +
                        slideOutVertically(slideSpec) { -it * direction }
                )
            },
            label = "slot_counter",
        ) { count ->
            Text(
                text = stringResource(R.string.media_notification_buttons_count, count, total),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (full) colors.onTertiaryContainer else colors.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EmptySlots(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(SlotOuterCornerRadius),
        color = colors.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SlotBadgeSize)
                        .clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.notifications),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.media_notification_buttons_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.media_notification_buttons_empty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionSlotRow(
    action: MediaNotificationAction,
    position: Int,
    isFirst: Boolean,
    isLast: Boolean,
    dragging: Boolean,
    reducedMotion: Boolean,
    onRemove: () -> Unit,
    handle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(action.labelRes())
    val topCorner by animateDpAsState(
        targetValue = if (dragging || isFirst) SlotOuterCornerRadius else SlotInnerCornerRadius,
        animationSpec = spatialSpec(reducedMotion),
        label = "slot_top_corner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (dragging || isLast) SlotOuterCornerRadius else SlotInnerCornerRadius,
        animationSpec = spatialSpec(reducedMotion),
        label = "slot_bottom_corner",
    )
    val elevation by animateDpAsState(
        targetValue = if (dragging) 8.dp else 0.dp,
        animationSpec = spatialSpec(reducedMotion),
        label = "slot_elevation",
    )
    val badgeRotation by animateFloatAsState(
        targetValue = if (dragging) 45f else 0f,
        animationSpec = spatialSpec(reducedMotion),
        label = "slot_badge_rotation",
    )
    val shape: Shape =
        RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner,
        )

    Surface(
        shape = shape,
        color = if (dragging) colors.surfaceContainerHighest else colors.surfaceContainer,
        shadowElevation = elevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(SlotBadgeSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = badgeRotation }
                            .clip(SlotBadgeShapes[(position - 1) % SlotBadgeShapes.size].toShape())
                            .background(if (dragging) colors.primary else colors.secondaryContainer),
                )
                Icon(
                    painter = painterResource(action.iconRes()),
                    contentDescription = null,
                    tint = if (dragging) colors.onPrimary else colors.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.media_notification_button_position, position),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(action.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MaterialIconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.media_notification_remove_button, label),
                    tint = colors.onSurfaceVariant,
                )
            }
            handle()
        }
    }
}

@Composable
private fun AddActionSheet(
    actions: List<MediaNotificationAction>,
    canAdd: Boolean,
    onAdd: (MediaNotificationAction) -> Unit,
    onDismiss: () -> Unit,
) {
    BackAwareModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        ) {
            Text(
                text = stringResource(R.string.media_notification_add_sheet_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier =
                    Modifier
                        .padding(start = 8.dp, bottom = 16.dp)
                        .semantics { heading() },
            )
            actions.forEachIndexed { index, action ->
                SegmentedListItem(
                    onClick = { onAdd(action) },
                    enabled = canAdd,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = actions.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = SlotGap),
                    leadingContent = {
                        Box(
                            modifier =
                                Modifier
                                    .size(SettingsDimensions.ListItemIconSize)
                                    .clip(SlotBadgeShapes[index % SlotBadgeShapes.size].toShape())
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(action.iconRes()),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(SettingsDimensions.ListItemIconInnerSize),
                            )
                        }
                    },
                    supportingContent = { Text(stringResource(action.descriptionRes())) },
                ) {
                    Text(stringResource(action.labelRes()))
                }
            }
            Spacer(Modifier.size(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ErrorContent(@StringRes messageRes: Int) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val animationsDisabled = LocalAnimationsDisabled.current
    val context = LocalContext.current
    val systemAnimationsOff =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    return animationsDisabled || systemAnimationsOff
}

@Composable
private fun <T> spatialSpec(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
private fun <T> effectsSpec(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

@Composable
private fun LazyItemScope.itemMotion(reducedMotion: Boolean): Modifier =
    if (reducedMotion) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        )
    }

private val MediaNotificationAction.slotKey: String
    get() = SlotKeyPrefix + name

@StringRes
private fun MediaNotificationAction.labelRes(): Int =
    when (this) {
        MediaNotificationAction.LIKE -> R.string.action_like
        MediaNotificationAction.REPEAT -> R.string.media_notification_action_repeat
        MediaNotificationAction.SHUFFLE -> R.string.shuffle
        MediaNotificationAction.START_RADIO -> R.string.start_radio
        MediaNotificationAction.LIBRARY -> R.string.add_to_library
        MediaNotificationAction.DOWNLOAD -> R.string.download
        MediaNotificationAction.SLEEP_TIMER -> R.string.sleep_timer
        MediaNotificationAction.CLOSE -> R.string.close
    }

@StringRes
private fun MediaNotificationAction.descriptionRes(): Int =
    when (this) {
        MediaNotificationAction.LIKE -> R.string.media_notification_action_like_desc
        MediaNotificationAction.REPEAT -> R.string.media_notification_action_repeat_desc
        MediaNotificationAction.SHUFFLE -> R.string.media_notification_action_shuffle_desc
        MediaNotificationAction.START_RADIO -> R.string.media_notification_action_radio_desc
        MediaNotificationAction.LIBRARY -> R.string.media_notification_action_library_desc
        MediaNotificationAction.DOWNLOAD -> R.string.media_notification_action_download_desc
        MediaNotificationAction.SLEEP_TIMER -> R.string.media_notification_action_sleep_timer_desc
        MediaNotificationAction.CLOSE -> R.string.media_notification_action_close_desc
    }

@DrawableRes
private fun MediaNotificationAction.iconRes(): Int =
    when (this) {
        MediaNotificationAction.LIKE -> R.drawable.favorite_border
        MediaNotificationAction.REPEAT -> R.drawable.repeat
        MediaNotificationAction.SHUFFLE -> R.drawable.shuffle
        MediaNotificationAction.START_RADIO -> R.drawable.radio
        MediaNotificationAction.LIBRARY -> R.drawable.library_add
        MediaNotificationAction.DOWNLOAD -> R.drawable.download
        MediaNotificationAction.SLEEP_TIMER -> R.drawable.timer
        MediaNotificationAction.CLOSE -> R.drawable.close
    }

@StringRes
private fun MediaNotificationNavigation.labelRes(): Int =
    when (this) {
        MediaNotificationNavigation.TRACKS -> R.string.media_notification_navigation_tracks
        MediaNotificationNavigation.SEEK -> R.string.media_notification_navigation_seek
    }

@StringRes
private fun MediaNotificationChannelImportance.labelRes(): Int =
    when (this) {
        MediaNotificationChannelImportance.NONE -> R.string.media_notification_importance_off
        MediaNotificationChannelImportance.MIN -> R.string.media_notification_importance_min
        MediaNotificationChannelImportance.LOW -> R.string.media_notification_importance_low
        MediaNotificationChannelImportance.DEFAULT -> R.string.media_notification_importance_default
        MediaNotificationChannelImportance.HIGH -> R.string.media_notification_importance_high
        MediaNotificationChannelImportance.NOT_CREATED -> R.string.media_notification_importance_not_created
    }

private fun MediaNotificationHealth.messageRes(): Pair<Int, Int> =
    when (this) {
        MediaNotificationHealth.PERMISSION_REQUIRED ->
            R.string.media_notification_health_permission_title to R.string.media_notification_health_permission_desc
        MediaNotificationHealth.APP_BLOCKED ->
            R.string.media_notification_health_app_blocked_title to R.string.media_notification_health_app_blocked_desc
        MediaNotificationHealth.CHANNEL_BLOCKED ->
            R.string.media_notification_health_channel_blocked_title to R.string.media_notification_health_channel_blocked_desc
        MediaNotificationHealth.CHANNEL_NOT_CREATED,
        MediaNotificationHealth.READY,
        -> R.string.media_notification_health_not_created_title to R.string.media_notification_health_not_created_desc
    }

private fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun playerChannelSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, MusicService.CHANNEL_ID)

private fun Context.launchSystemSettings(
    intent: Intent,
    onFailure: () -> Unit,
) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFailure()
    } catch (_: SecurityException) {
        onFailure()
    }
}
