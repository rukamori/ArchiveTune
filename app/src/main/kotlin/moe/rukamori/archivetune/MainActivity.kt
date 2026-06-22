/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.aod.ACTION_AOD_MODE
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.AppLanguageKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FloatingToolbarBottomPadding
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.HasPressedStarKey
import moe.rukamori.archivetune.constants.LaunchCountKey
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerLastAnchorKey
import moe.rukamori.archivetune.constants.NavigationBarAnimationSpec
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.RemindAfterKey
import moe.rukamori.archivetune.constants.SYSTEM_DEFAULT
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.SearchSourceKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.musicrecognition.ACTION_MUSIC_RECOGNITION
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.musicrecognition.openMusicRecognition
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.BottomSheetMenu
import moe.rukamori.archivetune.ui.component.BottomSheetPage
import moe.rukamori.archivetune.ui.component.COLLAPSED_ANCHOR
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.DISMISSED_ANCHOR
import moe.rukamori.archivetune.ui.component.EXPANDED_ANCHOR
import moe.rukamori.archivetune.ui.component.FloatingNavigationToolbar
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.component.NetworkStatusBanner
import moe.rukamori.archivetune.ui.component.StarDialog
import moe.rukamori.archivetune.ui.component.TopSearch
import moe.rukamori.archivetune.ui.component.TvNavigationRail
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.player.BottomSheetPlayer
import moe.rukamori.archivetune.ui.screens.LOGIN_URL_ARGUMENT
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.screens.navigationBuilder
import moe.rukamori.archivetune.ui.screens.search.LocalSearchScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchScreen
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.DiscordPresenceManager
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.theme.extractThemeColor
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.resetHeightOffset
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.setAppLocale
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.NetworkBannerViewModel
import moe.rukamori.archivetune.viewmodels.NewsViewModel
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var pendingDeepLinkQueue: Queue? = null
    private var pendingVoiceSearchQuery: String? = null
    private var pendingAodModeRequest = false
    private var pendingAodModeJob: Job? = null
    private var pendingTogetherJoinLink: String? = null
    private var pendingBackupRestoreUri by mutableStateOf<Uri?>(null)
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)
    private var latestUpdateChannel by mutableStateOf(defaultUpdateChannel)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingDeepLinkQueueIfReady()
                    playPendingVoiceSearchIfReady()
                    openPendingAodModeIfReady()
                    joinPendingTogetherIfReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                pendingAodModeJob?.cancel()
                pendingAodModeJob = null
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private fun playPendingDeepLinkQueueIfReady() {
        val pending = pendingDeepLinkQueue ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkQueue = null
        connection.playQueue(pending)
    }

    private fun playPendingVoiceSearchIfReady() {
        val query = pendingVoiceSearchQuery ?: return
        val connection = playerConnection ?: return
        pendingVoiceSearchQuery = null
        connection.playFromVoiceSearch(query)
    }

    private fun requestAodMode() {
        pendingAodModeRequest = true
        startMusicServiceSafely()
        openPendingAodModeIfReady()
    }

    private fun openPendingAodModeIfReady() {
        if (!pendingAodModeRequest) return
        val connection = playerConnection ?: return
        pendingAodModeRequest = false
        pendingAodModeJob?.cancel()
        pendingAodModeJob =
            lifecycleScope.launch {
                connection.queueRestoreCompleted.first { it }
                if (awaitRestorablePlayback(connection)) {
                    connection.aodModeEnabled.value = true
                }
            }
    }

    private fun joinPendingTogetherIfReady() {
        val pending = pendingTogetherJoinLink ?: return
        val connection = playerConnection ?: return
        pendingTogetherJoinLink = null
        lifecycleScope.launch(Dispatchers.IO) {
            val displayName =
                runCatching { dataStore.data.first()[moe.rukamori.archivetune.constants.TogetherDisplayNameKey] }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Build.MODEL ?: getString(R.string.app_name) }
            withContext(Dispatchers.Main) {
                connection.service.joinTogether(pending, displayName)
            }
        }
    }

    private suspend fun awaitRestorablePlayback(connection: PlayerConnection): Boolean {
        repeat(15) {
            if (
                connection.player.currentMediaItem != null ||
                connection.player.mediaItemCount > 0 ||
                connection.mediaMetadata.value != null
            ) {
                return true
            }
            delay(100)
        }

        return (
            connection.player.currentMediaItem != null ||
                connection.player.mediaItemCount > 0 ||
                connection.mediaMetadata.value != null
        )
    }

    override fun onStart() {
        super.onStart()
        isMusicServiceBound =
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        playPendingDeepLinkQueueIfReady()
        openPendingAodModeIfReady()
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        safeUnbindMusicService()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only clear/stop presence when the activity is actually finishing (not on rotation)
        // and do not clear it for transient configuration changes.
        if (isFinishing && !isChangingConfigurations) {
            try {
                DiscordPresenceManager.stop()
            } catch (_: Exception) {
            }
        }

        val shouldStopOnTaskClear =
            if (!isFinishing) {
                false
            } else {
                dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            playerConnection?.service?.stopAndClearPlayback(clearPersistentState = true)
            safeUnbindMusicService()
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val initialLocale =
                PreferenceStore
                    .get(AppLanguageKey)
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: Locale.getDefault()
            setAppLocale(this, initialLocale)

            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    dataStore.data.first()[AppLanguageKey]
                }.onSuccess { lang ->
                    val targetLocale =
                        lang
                            ?.takeUnless { it == SYSTEM_DEFAULT }
                            ?.let { Locale.forLanguageTag(it) }
                            ?: Locale.getDefault()
                    if (targetLocale != initialLocale) {
                        withContext(Dispatchers.Main) {
                            setAppLocale(this@MainActivity, targetLocale)
                            recreate()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        setContent {
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        playerConnection?.service?.refreshPlaybackNotification()
                    }
                }

            val updateChannel by rememberEnumPreference(UpdateChannelKey, defaultValue = defaultUpdateChannel)

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                while (playerConnection == null) {
                    delay(100)
                }
                delay(500)

                if (
                    BuildConfig.UPDATER_AVAILABLE &&
                    System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds
                ) {
                    val channelString = withContext(Dispatchers.IO) { dataStore.data.first()[UpdateChannelKey] }
                    val actualChannel =
                        channelString?.let {
                            try {
                                UpdateChannel.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        } ?: defaultUpdateChannel

                    if (actualChannel != UpdateChannel.NIGHTLY) {
                        val versionResult =
                            when (actualChannel) {
                                UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestDailyNightlyVersionName()
                                else -> Updater.getLatestVersionName()
                            }
                        versionResult.onSuccess {
                            if (Updater.isUpdateAvailable(it, BuildConfig.VERSION_NAME)) {
                                latestUpdateChannel = actualChannel
                                latestVersionName = it
                            }
                        }
                    }
                }
                moe.rukamori.archivetune.utils.UpdateNotificationManager
                    .checkForUpdates(this@MainActivity)
            }

            // Use remembered instances so the same state object is used everywhere
            // (previously retrieving the composition local directly created different
            // instances in different composition scopes which caused the update
            // bottom sheet to not appear and overlay interactions to be blocked).
            val bottomSheetPageState =
                remember {
                    moe.rukamori.archivetune.ui.component
                        .BottomSheetPageState()
                }
            val menuState =
                remember {
                    moe.rukamori.archivetune.ui.component
                        .MenuState()
                }
            val uriHandler = LocalUriHandler.current
            val releaseNotesState = remember { mutableStateOf<String?>(null) }
            val updateSheetContent: @Composable ColumnScope.() -> Unit = {
                // receiver: ColumnScope
                Text(
                    text = stringResource(R.string.new_update_available),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp),
                )

                Spacer(Modifier.height(8.dp))

                androidx.compose.material3.OutlinedButton(
                    onClick = {},
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 5.dp,
                            vertical = 5.dp,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = latestVersionName, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                ) {
                    val notes = releaseNotesState.value
                    if (notes != null && notes.isNotBlank()) {
                        MarkdownText(
                            markdown = notes,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.release_notes_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                androidx.compose.material3.Button(
                    onClick = {
                        try {
                            val downloadUrl =
                                when (latestUpdateChannel) {
                                    UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestDailyNightlyDownloadUrl()
                                    UpdateChannel.NIGHTLY -> Updater.getLatestNightlyDownloadUrl()
                                    UpdateChannel.STABLE -> Updater.getLatestDownloadUrl()
                                }
                            uriHandler.openUri(downloadUrl)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.update_text))
                }
            }

            // fetch release notes and show sheet when a new version is detected
            LaunchedEffect(latestVersionName, latestUpdateChannel, updateChannel) {
                if (
                    BuildConfig.UPDATER_AVAILABLE &&
                    latestUpdateChannel == updateChannel &&
                    Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
                ) {
                    val releaseNotesResult =
                        when (latestUpdateChannel) {
                            UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestDailyNightlyReleaseNotes()
                            else -> Updater.getLatestReleaseNotes()
                        }
                    releaseNotesResult
                        .onSuccess {
                            releaseNotesState.value = it
                        }.onFailure {
                            releaseNotesState.value = null
                        }

                    bottomSheetPageState.show(updateSheetContent)
                }
            }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val customThemeColorValue by rememberPreference(CustomThemeColorKey, defaultValue = "default")
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val defaultDisableAnimations = remember(this@MainActivity) { applicationContext.isLowRamDevice() }
            val disableAnimations by rememberPreference(
                DisableAnimationsKey,
                defaultValue = defaultDisableAnimations,
            )
            val fontPreference by rememberEnumPreference(FontPreferenceKey, defaultValue = AppFontPreference.DEFAULT)
            val customFontUri by rememberPreference(CustomFontUriKey, defaultValue = "")
            val legacyUseSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = pureBlackEnabled && useDarkTheme

            val customThemeSeedPalette =
                remember(customThemeColorValue) {
                    if (customThemeColorValue.startsWith("#")) {
                        null
                    } else if (customThemeColorValue.startsWith("seedPalette:")) {
                        moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
                            .decodeFromPreference(customThemeColorValue)
                    } else {
                        moe.rukamori.archivetune.ui.screens.settings.ThemePalettes
                            .findById(customThemeColorValue)
                            ?.let {
                                moe.rukamori.archivetune.ui.theme.ThemeSeedPalette(
                                    primary = it.primary,
                                    secondary = it.secondary,
                                    tertiary = it.tertiary,
                                    neutral = it.neutral,
                                )
                            }
                    }
                }

            val customThemeColor =
                remember(customThemeColorValue, customThemeSeedPalette) {
                    if (customThemeColorValue.startsWith("#")) {
                        try {
                            val colorString = customThemeColorValue.removePrefix("#")
                            Color(android.graphics.Color.parseColor("#$colorString"))
                        } catch (e: Exception) {
                            DefaultThemeColor
                        }
                    } else {
                        customThemeSeedPalette?.primary ?: DefaultThemeColor
                    }
                }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(legacyUseSystemFont) {
                if (!legacyUseSystemFont) return@LaunchedEffect
                val preferences = dataStore.data.first()
                if (preferences[FontPreferenceKey] == null) {
                    dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
                }
            }

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val result =
                                    imageLoader.execute(
                                        ImageRequest
                                            .Builder(this@MainActivity)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .build(),
                                    )
                                val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                withContext(Dispatchers.Main) {
                                    themeColor = extractedColor ?: DefaultThemeColor
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    themeColor = DefaultThemeColor
                                }
                            }
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            themeColor = DefaultThemeColor
                        } else {
                            themeColor = customThemeColor
                        }
                    }
                }
            }

            ArchiveTuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
                seedPalette = if (!enableDynamicTheme) customThemeSeedPalette else null,
                disableAnimations = disableAnimations,
                fontPreference = fontPreference,
                customFontUri = customFontUri,
            ) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                            ),
                ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val topInset = with(density) { windowsInsets.getTop(density).toDp() }
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                    val isTvDevice = remember { applicationContext.isTvDevice() }
                    val useRail =
                        isTvDevice ||
                            currentWindowAdaptiveInfo()
                                .windowSizeClass
                                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

                    val navController = rememberNavController()
                    val coroutineScope = rememberCoroutineScope()
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    val networkBannerViewModel: NetworkBannerViewModel = hiltViewModel()
                    val newsViewModel: NewsViewModel = hiltViewModel()
                    val allLocalItems by homeViewModel.allLocalItems.collectAsState()
                    val allYtItems by homeViewModel.allYtItems.collectAsState()
                    val networkBannerState by networkBannerViewModel.bannerState.collectAsStateWithLifecycle()
                    val hasUnreadNews by newsViewModel.hasUnreadNews.collectAsStateWithLifecycle()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val (previousTab) = rememberSaveable { mutableStateOf("home") }
                    val currentRoute = navBackStackEntry?.destination?.route
                    val onlineSearchViewModel: OnlineSearchViewModel? =
                        if (currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true && navBackStackEntry != null) {
                            hiltViewModel(navBackStackEntry!!)
                        } else {
                            null
                        }
                    val onlineSearchSort =
                        if (onlineSearchViewModel != null) {
                            onlineSearchViewModel.sort.collectAsStateWithLifecycle().value
                        } else {
                            OnlineSearchSort.DEFAULT
                        }
                    val isYearInMusicScreen = currentRoute?.startsWith("year_in_music") == true

                    val navigationItems =
                        remember(isTvDevice) {
                            if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens
                        }
                    val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) =
                        rememberPreference(
                            MiniPlayerLastAnchorKey,
                            defaultValue = COLLAPSED_ANCHOR,
                        )
                    val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
                    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
                    val tabOpenedFromShortcut =
                        remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }
                    val launchMusicRecognitionFromShortcut =
                        remember {
                            intent?.action == ACTION_MUSIC_RECOGNITION
                        }

                    val topLevelScreens =
                        remember(navigationItems) {
                            navigationItems.map(Screens::route) + "settings"
                        }

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }

                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }
                    val tvRailFocusRequester = remember { FocusRequester() }
                    val contentAreaFocusRequester = remember { FocusRequester() }

                    val openSearch: () -> Unit = {
                        onActiveChange(true)
                        searchBarFocusRequester.requestFocus()
                    }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate(onlineSearchResultRoute(it))
                            if (!pauseSearchHistory) {
                                database.query {
                                    insert(SearchHistory(query = it))
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val shouldShowSearchBar =
                        remember(active, navBackStackEntry) {
                            active ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                        }

                    val shouldShowNavigationBar =
                        remember(navBackStackEntry, active) {
                            navBackStackEntry?.destination?.route == null ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } &&
                                !active
                        }

                    val shouldShowHomeShuffleButton =
                        currentRoute == Screens.Home.route &&
                            (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty())
                    val shouldShowLibraryCreatePlaylistButton =
                        currentRoute == Screens.Library.route

                    fun getBottomNavPadding(): Dp =
                        if (shouldShowNavigationBar && !useRail) {
                            FloatingToolbarHeight
                        } else {
                            0.dp
                        }

                    val floatingBarsBottomPadding = FloatingToolbarBottomPadding
                    val navVisibleHeight = FloatingToolbarHeight

                    val bottomNavigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar && !useRail) navVisibleHeight else 0.dp,
                        animationSpec = if (disableAnimations) snap() else NavigationBarAnimationSpec,
                        label = "",
                    )

                    val playerBottomSheetState =
                        rememberBottomSheetState(
                            dismissedBound = 0.dp,
                            collapsedBound =
                                bottomInset +
                                    (if (shouldShowNavigationBar && !useRail) floatingBarsBottomPadding else 0.dp) +
                                    getBottomNavPadding() +
                                    MiniPlayerBottomSpacing +
                                    MiniPlayerHeight,
                            expandedBound = maxHeight,
                        )

                    val playerBackground by rememberEnumPreference(
                        key = PlayerBackgroundStyleKey,
                        defaultValue = PlayerBackgroundStyle.DEFAULT,
                    )
                    val playerDesignStyle by rememberEnumPreference(
                        key = PlayerDesignStyleKey,
                        defaultValue = PlayerDesignStyle.V4,
                    )

                    val aodModeEnabled by remember(playerConnection) {
                        playerConnection?.aodModeEnabled ?: MutableStateFlow(false)
                    }.collectAsStateWithLifecycle()

                    LaunchedEffect(aodModeEnabled) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        if (aodModeEnabled) {
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    LaunchedEffect(useDarkTheme, playerBottomSheetState.isExpanded, playerBackground, aodModeEnabled) {
                        if (aodModeEnabled) return@LaunchedEffect
                        val isDarkStatusBar =
                            if (playerBottomSheetState.isExpanded &&
                                playerBackground != PlayerBackgroundStyle.DEFAULT
                            ) {
                                true
                            } else {
                                useDarkTheme
                            }
                        setSystemBarAppearance(isDarkStatusBar)
                    }

                    val miniPlayerAnchor by remember {
                        derivedStateOf {
                            when {
                                playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                                playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                                else -> COLLAPSED_ANCHOR
                            }
                        }
                    }

                    var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
                        mutableStateOf(false)
                    }

                    LaunchedEffect(miniPlayerAnchor, isYearInMusicScreen, miniPlayerAnchorPersistenceEnabled) {
                        if (!isYearInMusicScreen && miniPlayerAnchorPersistenceEnabled) {
                            setSavedMiniPlayerAnchor(miniPlayerAnchor)
                        }
                    }

                    var yearInMusicSavedPlayerAnchor by rememberSaveable { mutableStateOf(-1) }

                    val shouldHideStatusBars =
                        isYearInMusicScreen ||
                            (playerBottomSheetState.isExpanded && playerDesignStyle == PlayerDesignStyle.V7)

                    LaunchedEffect(shouldHideStatusBars, aodModeEnabled) {
                        if (aodModeEnabled) return@LaunchedEffect
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        if (shouldHideStatusBars) {
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            controller.hide(WindowInsetsCompat.Type.statusBars())
                        } else {
                            controller.show(WindowInsetsCompat.Type.statusBars())
                        }
                    }

                    LaunchedEffect(isYearInMusicScreen, playerConnection) {
                        val connection = playerConnection ?: return@LaunchedEffect
                        val player = connection.player

                        if (isYearInMusicScreen) {
                            if (yearInMusicSavedPlayerAnchor == -1) {
                                yearInMusicSavedPlayerAnchor =
                                    when {
                                        playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                                        playerBottomSheetState.isCollapsed -> COLLAPSED_ANCHOR
                                        playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                                        else -> COLLAPSED_ANCHOR
                                    }
                            }

                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else if (yearInMusicSavedPlayerAnchor != -1) {
                            val anchorToRestore = yearInMusicSavedPlayerAnchor
                            yearInMusicSavedPlayerAnchor = -1

                            if (!awaitRestorablePlayback(connection)) {
                                playerBottomSheetState.dismiss()
                            } else {
                                when (anchorToRestore) {
                                    EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                    COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    DISMISSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    else -> playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    }

                    val playerAwareWindowInsets =
                        remember(
                            useRail,
                            bottomInset,
                            shouldShowNavigationBar,
                            playerBottomSheetState.isDismissed,
                        ) {
                            var bottom = bottomInset
                            if (shouldShowNavigationBar && !useRail) {
                                bottom += getBottomNavPadding() + floatingBarsBottomPadding
                            }
                            if (!playerBottomSheetState.isDismissed) {
                                bottom += MiniPlayerHeight + MiniPlayerBottomSpacing
                            }
                            windowsInsets
                                .only(
                                    (
                                        if (useRail) {
                                            WindowInsetsSides.Right
                                        } else {
                                            WindowInsetsSides.Horizontal
                                        }
                                    ) + WindowInsetsSides.Top,
                                ).add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        }

                    appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                navBackStackEntry?.destination?.route != Screens.Library.route &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        },
                    )

                    val searchBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )

                    val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
                        if (isSelected) {
                            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                            coroutineScope.launch {
                                searchBarScrollBehavior.state.resetHeightOffset()
                            }
                        } else {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    var previousRoute by rememberSaveable { mutableStateOf<String?>(null) }

                    LaunchedEffect(navBackStackEntry) {
                        val currentRoute = navBackStackEntry?.destination?.route
                        val wasOnNonTopLevelScreen =
                            previousRoute != null &&
                                previousRoute !in topLevelScreens &&
                                previousRoute?.startsWith(OnlineSearchResultRoutePrefix) != true
                        val isReturningToHomeOrLibrary =
                            currentRoute == Screens.Home.route ||
                                currentRoute == Screens.Library.route

                        if (wasOnNonTopLevelScreen && isReturningToHomeOrLibrary) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }

                        val isEnteringSubScreen =
                            currentRoute != null &&
                                currentRoute !in topLevelScreens &&
                                currentRoute.startsWith(OnlineSearchResultRoutePrefix) != true
                        if (isEnteringSubScreen) {
                            topAppBarScrollBehavior.state.contentOffset = 0f
                        }

                        previousRoute = currentRoute

                        if ((
                                currentRoute?.startsWith("artist/") == true ||
                                    currentRoute?.startsWith("album/") == true
                            ) &&
                            playerBottomSheetState.isExpanded
                        ) {
                            playerBottomSheetState.collapseSoft()
                        }

                        if (navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                            val searchQuery =
                                decodeOnlineSearchQuery(
                                    navBackStackEntry
                                        ?.arguments
                                        ?.getString(OnlineSearchResultArgument)
                                        .orEmpty(),
                                )
                            onQueryChange(
                                TextFieldValue(
                                    searchQuery,
                                    TextRange(searchQuery.length),
                                ),
                            )
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                            navBackStackEntry?.destination?.route in topLevelScreens
                        ) {
                            onQueryChange(TextFieldValue())
                            if (navBackStackEntry?.destination?.route != Screens.Home.route) {
                                searchBarScrollBehavior.state.resetHeightOffset()
                                topAppBarScrollBehavior.state.resetHeightOffset()
                            }
                        }
                    }
                    LaunchedEffect(active) {
                        if (active) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(isTvDevice, useRail, active, currentRoute, shouldShowNavigationBar) {
                        if (
                            isTvDevice &&
                            useRail &&
                            shouldShowNavigationBar &&
                            !active &&
                            currentRoute in topLevelScreens
                        ) {
                            delay(100)
                            tvRailFocusRequester.requestFocus()
                        }
                    }

                    var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

                    LaunchedEffect(playerConnection, savedMiniPlayerAnchor, isYearInMusicScreen) {
                        if (restoredMiniPlayerAnchor) return@LaunchedEffect
                        val connection = playerConnection ?: return@LaunchedEffect
                        connection.queueRestoreCompleted.first { it }
                        if (!awaitRestorablePlayback(connection)) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (!isYearInMusicScreen) {
                                when (savedMiniPlayerAnchor) {
                                    EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                    COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    DISMISSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    else -> playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                        restoredMiniPlayerAnchor = true
                        miniPlayerAnchorPersistenceEnabled = true
                    }

                    val currentPlayerBottomSheetState = rememberUpdatedState(playerBottomSheetState)
                    val currentIsYearInMusicScreen = rememberUpdatedState(isYearInMusicScreen)

                    DisposableEffect(playerConnection) {
                        val player =
                            playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                private fun collapseDismissedMiniPlayerForActivePlayback() {
                                    if (
                                        player.mediaItemCount > 0 &&
                                        player.currentMediaItem != null &&
                                        player.playWhenReady &&
                                        player.playbackState != Player.STATE_IDLE &&
                                        player.playbackState != Player.STATE_ENDED &&
                                        currentPlayerBottomSheetState.value.isDismissed &&
                                        !currentIsYearInMusicScreen.value
                                    ) {
                                        currentPlayerBottomSheetState.value.collapseSoft()
                                    }
                                }

                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onTimelineChanged(
                                    timeline: Timeline,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }

                                override fun onPlayWhenReadyChanged(
                                    playWhenReady: Boolean,
                                    reason: Int,
                                ) {
                                    collapseDismissedMiniPlayerForActivePlayback()
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar =
                            !active && navBackStackEntry?.destination?.route in topLevelScreens &&
                            navBackStackEntry?.destination?.route != "settings"
                    }

                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleIntent(pendingIntent, navController)
                            pendingIntent = null
                        } else {
                            handleIntent(intent, navController)
                        }
                    }

                    var showStarDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)

                        withContext(Dispatchers.IO) {
                            val current = dataStore[LaunchCountKey] ?: 0
                            val newCount = current + 1
                            dataStore.edit { prefs ->
                                prefs[LaunchCountKey] = newCount
                            }
                        }

                        val shouldShow =
                            withContext(Dispatchers.IO) {
                                val hasPressed = dataStore[HasPressedStarKey] ?: false
                                val remindAfter = dataStore[RemindAfterKey] ?: 3
                                !hasPressed && (dataStore[LaunchCountKey] ?: 0) >= remindAfter
                            }

                        if (shouldShow) {
                            var waited = 0L
                            val waitStep = 500L
                            val maxWait = 30_000L
                            while (bottomSheetPageState.isVisible && waited < maxWait) {
                                delay(waitStep)
                                waited += waitStep
                            }
                            showStarDialog = true
                        }
                    }

                    if (showStarDialog) {
                        StarDialog(
                            onDismissRequest = { showStarDialog = false },
                            onSupport = {
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            dataStore.edit { prefs ->
                                                prefs[HasPressedStarKey] = true
                                                prefs[RemindAfterKey] = Int.MAX_VALUE
                                            }
                                        }
                                    } catch (e: Exception) {
                                        reportException(e)
                                    } finally {
                                        showStarDialog = false
                                    }
                                }
                            },
                            onLater = {
                                coroutineScope.launch {
                                    try {
                                        val launch = withContext(Dispatchers.IO) { dataStore[LaunchCountKey] ?: 0 }
                                        withContext(Dispatchers.IO) {
                                            dataStore.edit { prefs ->
                                                prefs[RemindAfterKey] = launch + 10
                                            }
                                        }
                                    } catch (e: Exception) {
                                        reportException(e)
                                    } finally {
                                        showStarDialog = false
                                    }
                                }
                            },
                        )
                    }

                    val currentTitleRes =
                        remember(navBackStackEntry) {
                            when (navBackStackEntry?.destination?.route) {
                                Screens.Home.route -> R.string.home
                                Screens.Search.route -> R.string.search
                                Screens.Library.route -> R.string.filter_library
                                else -> null
                            }
                        }
                    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

                    val haptic = LocalHapticFeedback.current
                    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
                    val customHaptic =
                        remember(haptic, enableHapticFeedback) {
                            object : HapticFeedback {
                                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                    if (enableHapticFeedback) {
                                        haptic.performHapticFeedback(hapticFeedbackType)
                                    }
                                }
                            }
                        }

                    CompositionLocalProvider(
                        LocalHapticFeedback provides customHaptic,
                        LocalAnimationsDisabled provides disableAnimations,
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState provides bottomSheetPageState,
                        moe.rukamori.archivetune.ui.component.LocalMenuState provides menuState,
                    ) {
                        if (showCreatePlaylistDialog) {
                            CreatePlaylistDialog(
                                onDismiss = { showCreatePlaylistDialog = false },
                            )
                        }

                        Row {
                            AnimatedVisibility(
                                visible = useRail && shouldShowNavigationBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 150)),
                                exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 100)),
                            ) {
                                if (isTvDevice) {
                                    TvNavigationRail(
                                        items = navigationItems,
                                        selectedItemRoute =
                                            if (active) {
                                                Screens.Search.route
                                            } else {
                                                currentRoute
                                            },
                                        modifier = Modifier,
                                        firstItemFocusRequester = tvRailFocusRequester,
                                        contentFocusRequester =
                                            if (active ||
                                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                            ) {
                                                searchBarFocusRequester
                                            } else {
                                                contentAreaFocusRequester
                                            },
                                        onItemClick = { screen ->
                                            val wasPlayerActive = playerBottomSheetState.isExpanded
                                            if (wasPlayerActive) {
                                                playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                            }
                                            val isSelected =
                                                navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                            if (wasPlayerActive && isSelected) {
                                                return@TvNavigationRail
                                            }
                                            handlePrimaryNavigationClick(screen, isSelected)
                                        },
                                    )
                                } else {
                                    NavigationRail(
                                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        header = { Spacer(Modifier.height(24.dp)) },
                                    ) {
                                        navigationItems.fastForEach { screen ->
                                            val isSelected =
                                                navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                                            NavigationRailItem(
                                                selected = isSelected,
                                                icon = {
                                                    Icon(
                                                        painter =
                                                            painterResource(
                                                                id = if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                            ),
                                                        contentDescription = null,
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                onClick = {
                                                    val wasPlayerActive = playerBottomSheetState.isExpanded

                                                    if (wasPlayerActive) {
                                                        playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                                    }

                                                    if (wasPlayerActive && isSelected) return@NavigationRailItem
                                                    handlePrimaryNavigationClick(screen, isSelected)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Scaffold(
                                topBar = {
                                    if (shouldShowTopBar) {
                                        val shouldUseFloatingTopBar =
                                            remember(navBackStackEntry) {
                                                navBackStackEntry?.destination?.route == Screens.Home.route ||
                                                    navBackStackEntry?.destination?.route == Screens.Search.route ||
                                                    navBackStackEntry?.destination?.route == Screens.Library.route
                                            }
                                        val shouldShowBlurBackground =
                                            remember(navBackStackEntry) {
                                                shouldUseFloatingTopBar
                                            }

                                        val surfaceColor = MaterialTheme.colorScheme.surface
                                        val currentScrollBehavior = if (shouldUseFloatingTopBar) searchBarScrollBehavior else topAppBarScrollBehavior
                                        val isLibraryRoute = navBackStackEntry?.destination?.route == Screens.Library.route

                                        Box(
                                            modifier =
                                                Modifier.offset {
                                                    IntOffset(
                                                        x = 0,
                                                        y = if (isLibraryRoute) 0 else currentScrollBehavior.state.heightOffset.toInt(),
                                                    )
                                                },
                                        ) {
                                            // Gradient shadow background
                                            if (shouldShowBlurBackground) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .height(
                                                                AppBarHeight +
                                                                    with(LocalDensity.current) {
                                                                        WindowInsets.systemBars.getTop(LocalDensity.current).toDp()
                                                                    },
                                                            ).background(
                                                                Brush.verticalGradient(
                                                                    colors =
                                                                        listOf(
                                                                            surfaceColor.copy(alpha = 0.95f),
                                                                            surfaceColor.copy(alpha = 0.85f),
                                                                            surfaceColor.copy(alpha = 0.6f),
                                                                            Color.Transparent,
                                                                        ),
                                                                ),
                                                            ),
                                                )
                                            }

                                            TopAppBar(
                                                windowInsets =
                                                    WindowInsets.safeDrawing.only(
                                                        (
                                                            if (useRail) {
                                                                WindowInsetsSides.Right
                                                            } else {
                                                                WindowInsetsSides.Horizontal
                                                            }
                                                        ) + WindowInsetsSides.Top,
                                                    ),
                                                title = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        // app icon
                                                        Icon(
                                                            painter = painterResource(R.drawable.about_appbar),
                                                            contentDescription = null,
                                                            modifier =
                                                                Modifier
                                                                    .size(35.dp)
                                                                    .padding(end = 3.dp),
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.app_name),
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                },
                                                actions = {
                                                    IconButton(onClick = { navController.navigate("history") }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.history),
                                                            contentDescription = stringResource(R.string.history),
                                                        )
                                                    }
                                                    TooltipBox(
                                                        positionProvider =
                                                            if (hasUnreadNews) {
                                                                TooltipDefaults.rememberRichTooltipPositionProvider()
                                                            } else {
                                                                TooltipDefaults.rememberPlainTooltipPositionProvider()
                                                            },
                                                        tooltip = {
                                                            if (hasUnreadNews) {
                                                                RichTooltip(
                                                                    title = { Text(stringResource(R.string.news_tooltip_title)) },
                                                                ) {
                                                                    Text(stringResource(R.string.news_tooltip_body))
                                                                }
                                                            } else {
                                                                PlainTooltip {
                                                                    Text(stringResource(R.string.news))
                                                                }
                                                            }
                                                        },
                                                        state = rememberTooltipState(),
                                                    ) {
                                                        IconButton(onClick = { navController.navigate("news") }) {
                                                            BadgedBox(badge = {
                                                                if (hasUnreadNews) {
                                                                    Badge()
                                                                }
                                                            }) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.newspaper),
                                                                    contentDescription = stringResource(R.string.news),
                                                                )
                                                            }
                                                        }
                                                    }
                                                    IconButton(onClick = { navController.navigate("new_release") }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.new_release),
                                                            contentDescription = stringResource(R.string.new_release_albums),
                                                        )
                                                    }
                                                    IconButton(onClick = { navController.navigate("settings") }) {
                                                        BadgedBox(badge = {
                                                            if (
                                                                BuildConfig.UPDATER_AVAILABLE &&
                                                                latestUpdateChannel == updateChannel &&
                                                                Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
                                                            ) {
                                                                Badge()
                                                            }
                                                        }) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.settings),
                                                                contentDescription = stringResource(R.string.settings),
                                                                modifier = Modifier.size(24.dp),
                                                            )
                                                        }
                                                    }
                                                },
                                                scrollBehavior =
                                                    if (navBackStackEntry?.destination?.route ==
                                                        Screens.Library.route
                                                    ) {
                                                        null
                                                    } else if (shouldUseFloatingTopBar) {
                                                        searchBarScrollBehavior
                                                    } else {
                                                        topAppBarScrollBehavior
                                                    },
                                                colors =
                                                    TopAppBarDefaults.topAppBarColors(
                                                        containerColor =
                                                            if (shouldUseFloatingTopBar) {
                                                                Color.Transparent
                                                            } else if (pureBlack) {
                                                                Color.Black
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        scrolledContainerColor =
                                                            if (shouldUseFloatingTopBar) {
                                                                Color.Transparent
                                                            } else if (pureBlack) {
                                                                Color.Black
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    ),
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible =
                                            active ||
                                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true,
                                        enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 200)),
                                    ) {
                                        TopSearch(
                                            query = query,
                                            onQueryChange = onQueryChange,
                                            onSearch = onSearch,
                                            active = active,
                                            onActiveChange = onActiveChange,
                                            placeholder = {
                                                Text(
                                                    text =
                                                        stringResource(
                                                            when (searchSource) {
                                                                SearchSource.LOCAL -> R.string.search_library
                                                                SearchSource.ONLINE -> R.string.search_yt_music
                                                            },
                                                        ),
                                                )
                                            },
                                            leadingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        when {
                                                            active -> {
                                                                onActiveChange(false)
                                                            }

                                                            !navigationItems.fastAny {
                                                                it.route == navBackStackEntry?.destination?.route
                                                            } -> {
                                                                navController.navigateUp()
                                                            }

                                                            else -> {
                                                                onActiveChange(true)
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        when {
                                                            active -> {}

                                                            !navigationItems.fastAny {
                                                                it.route == navBackStackEntry?.destination?.route
                                                            } -> {
                                                                navController.backToMain()
                                                            }

                                                            else -> {}
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        painterResource(
                                                            if (active ||
                                                                !navigationItems.fastAny {
                                                                    it.route == navBackStackEntry?.destination?.route
                                                                }
                                                            ) {
                                                                R.drawable.arrow_back
                                                            } else {
                                                                R.drawable.search
                                                            },
                                                        ),
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                Row {
                                                    if (active) {
                                                        if (query.text.isNotEmpty()) {
                                                            IconButton(
                                                                onClick = {
                                                                    onQueryChange(
                                                                        TextFieldValue(
                                                                            "",
                                                                        ),
                                                                    )
                                                                },
                                                            ) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.close),
                                                                    contentDescription = null,
                                                                )
                                                            }
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                searchSource =
                                                                    if (searchSource ==
                                                                        SearchSource.ONLINE
                                                                    ) {
                                                                        SearchSource.LOCAL
                                                                    } else {
                                                                        SearchSource.ONLINE
                                                                    }
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter =
                                                                    painterResource(
                                                                        when (searchSource) {
                                                                            SearchSource.LOCAL -> R.drawable.library_music
                                                                            SearchSource.ONLINE -> R.drawable.language
                                                                        },
                                                                    ),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    } else if (onlineSearchViewModel != null) {
                                                        OnlineSearchSortMenu(
                                                            selectedSort = onlineSearchSort,
                                                            onSortSelected = onlineSearchViewModel::updateSort,
                                                        )
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .focusRequester(searchBarFocusRequester)
                                                    .let { with(this@BoxWithConstraints) { it.align(Alignment.TopCenter) } },
                                            focusRequester = searchBarFocusRequester,
                                            leftFocusRequester = tvRailFocusRequester,
                                            colors =
                                                if (pureBlack && active) {
                                                    SearchBarDefaults.colors(
                                                        containerColor = Color.Black,
                                                        dividerColor = Color.DarkGray,
                                                        inputFieldColors =
                                                            TextFieldDefaults.colors(
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.Gray,
                                                                focusedContainerColor = Color.Transparent,
                                                                unfocusedContainerColor = Color.Transparent,
                                                                cursorColor = Color.White,
                                                                focusedIndicatorColor = Color.Transparent,
                                                                unfocusedIndicatorColor = Color.Transparent,
                                                            ),
                                                    )
                                                } else {
                                                    SearchBarDefaults.colors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    )
                                                },
                                        ) {
                                            Crossfade(
                                                targetState = searchSource,
                                                animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300),
                                                label = "",
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(
                                                            bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp,
                                                        ).navigationBarsPadding(),
                                            ) { searchSource ->
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> {
                                                        LocalSearchScreen(
                                                            query = query.text,
                                                            navController = navController,
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )
                                                    }

                                                    SearchSource.ONLINE -> {
                                                        OnlineSearchScreen(
                                                            query = query.text,
                                                            onQueryChange = onQueryChange,
                                                            navController = navController,
                                                            onSearch = {
                                                                navController.navigate(onlineSearchResultRoute(it))
                                                                if (!pauseSearchHistory) {
                                                                    database.query {
                                                                        insert(SearchHistory(query = it))
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                bottomBar = {
                                    Box {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )

                                        if (useRail) return@Box

                                        val navSlideDistance =
                                            bottomInset + floatingBarsBottomPadding + navVisibleHeight

                                        Box(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .height(navSlideDistance)
                                                    .offset {
                                                        if (bottomNavigationBarHeight == 0.dp) {
                                                            IntOffset(
                                                                x = 0,
                                                                y = navSlideDistance.roundToPx(),
                                                            )
                                                        } else {
                                                            val slideOffset =
                                                                navSlideDistance *
                                                                    playerBottomSheetState.progress.coerceIn(
                                                                        0f,
                                                                        1f,
                                                                    )
                                                            val hideOffset =
                                                                navSlideDistance *
                                                                    (
                                                                        1 -
                                                                            bottomNavigationBarHeight.coerceAtMost(navVisibleHeight) /
                                                                            navVisibleHeight
                                                                    )
                                                            IntOffset(
                                                                x = 0,
                                                                y = (slideOffset + hideOffset).roundToPx(),
                                                            )
                                                        }
                                                    },
                                        ) {
                                            FloatingNavigationToolbar(
                                                items = navigationItems,
                                                pureBlack = pureBlack,
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(
                                                            start = FloatingToolbarHorizontalPadding,
                                                            end = FloatingToolbarHorizontalPadding,
                                                            bottom = bottomInset + floatingBarsBottomPadding,
                                                        ).height(navVisibleHeight),
                                                onFabClick =
                                                    if (shouldShowLibraryCreatePlaylistButton) {
                                                        { showCreatePlaylistDialog = true }
                                                    } else {
                                                        null
                                                    },
                                                fabIconRes = if (shouldShowLibraryCreatePlaylistButton) R.drawable.add else null,
                                                fabContentDescription =
                                                    if (shouldShowLibraryCreatePlaylistButton) {
                                                        stringResource(R.string.create_playlist)
                                                    } else {
                                                        ""
                                                    },
                                                onShuffleClick =
                                                    if (shouldShowHomeShuffleButton) {
                                                        {
                                                            val useLocalSource =
                                                                when {
                                                                    allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> {
                                                                        Random.nextFloat() <
                                                                            0.5f
                                                                    }

                                                                    allLocalItems.isNotEmpty() -> {
                                                                        true
                                                                    }

                                                                    else -> {
                                                                        false
                                                                    }
                                                                }

                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                if (useLocalSource) {
                                                                    when (val luckyItem = allLocalItems.random()) {
                                                                        is Song -> {
                                                                            playerConnection?.playQueue(
                                                                                YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                                                                            )
                                                                        }

                                                                        is Album -> {
                                                                            val albumWithSongs =
                                                                                withContext(Dispatchers.IO) {
                                                                                    database.albumWithSongs(luckyItem.id).first()
                                                                                }

                                                                            albumWithSongs?.let {
                                                                                playerConnection?.playQueue(LocalAlbumRadio(it))
                                                                            }
                                                                        }

                                                                        is Artist -> {
                                                                            Unit
                                                                        }

                                                                        is Playlist -> {
                                                                            Unit
                                                                        }
                                                                    }
                                                                } else {
                                                                    when (val luckyItem = allYtItems.random()) {
                                                                        is SongItem -> {
                                                                            playerConnection?.playQueue(
                                                                                YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                                                                            )
                                                                        }

                                                                        is AlbumItem -> {
                                                                            playerConnection?.playQueue(
                                                                                YouTubeAlbumRadio(luckyItem.playlistId),
                                                                            )
                                                                        }

                                                                        is ArtistItem -> {
                                                                            luckyItem.radioEndpoint?.let {
                                                                                playerConnection?.playQueue(YouTubeQueue(it))
                                                                            }
                                                                        }

                                                                        is PlaylistItem -> {
                                                                            luckyItem.playEndpoint?.let {
                                                                                playerConnection?.playQueue(YouTubeQueue.playlist(it))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                shuffleIconRes = if (shouldShowHomeShuffleButton) R.drawable.shuffle else null,
                                                shuffleContentDescription =
                                                    if (shouldShowHomeShuffleButton) {
                                                        stringResource(
                                                            R.string.shuffle,
                                                        )
                                                    } else {
                                                        ""
                                                    },
                                                onMusicRecognitionClick =
                                                    if (shouldShowHomeShuffleButton) {
                                                        { navController.navigate(MusicRecognitionRoute) }
                                                    } else {
                                                        null
                                                    },
                                                musicRecognitionContentDescription =
                                                    if (shouldShowHomeShuffleButton) {
                                                        stringResource(
                                                            R.string.music_recognition,
                                                        )
                                                    } else {
                                                        ""
                                                    },
                                                onMusicTogetherClick =
                                                    if (shouldShowHomeShuffleButton) {
                                                        { navController.navigate("settings/music_together") }
                                                    } else {
                                                        null
                                                    },
                                                isSelected = { screen ->
                                                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } ==
                                                        true
                                                },
                                                onItemClick = { screen, isSelected ->
                                                    handlePrimaryNavigationClick(screen, isSelected)
                                                },
                                            )
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .nestedScroll(searchBarScrollBehavior.nestedScrollConnection),
                            ) {
                                var transitionDirection =
                                    AnimatedContentTransitionScope.SlideDirection.Left

                                if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                    if (navigationItems.fastAny { it.route == previousTab }) {
                                        val curIndex =
                                            navigationItems.indexOf(
                                                navigationItems.fastFirstOrNull {
                                                    it.route == navBackStackEntry?.destination?.route
                                                },
                                            )

                                        val prevIndex =
                                            navigationItems.indexOf(
                                                navigationItems.fastFirstOrNull {
                                                    it.route == previousTab
                                                },
                                            )

                                        if (prevIndex > curIndex) {
                                            AnimatedContentTransitionScope.SlideDirection.Right.also {
                                                transitionDirection = it
                                            }
                                        }
                                    }
                                }

                                NavHost(
                                    navController = navController,
                                    startDestination =
                                        if (launchMusicRecognitionFromShortcut) {
                                            MusicRecognitionRoute
                                        } else {
                                            when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                                NavigationTab.HOME -> Screens.Home.route
                                                NavigationTab.LIBRARY -> Screens.Library.route
                                                else -> Screens.Home.route
                                            }
                                        },
                                    enterTransition = {
                                        if (disableAnimations) {
                                            fadeIn(tween(0))
                                        } else if (initialState.destination.route in topLevelScreens &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            fadeIn(tween(250))
                                        } else {
                                            fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                                        }
                                    },
                                    exitTransition = {
                                        if (disableAnimations) {
                                            fadeOut(tween(0))
                                        } else if (initialState.destination.route in topLevelScreens &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            fadeOut(tween(200))
                                        } else {
                                            fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                                        }
                                    },
                                    popEnterTransition = {
                                        if (disableAnimations) {
                                            fadeIn(tween(0))
                                        } else if ((
                                                initialState.destination.route in topLevelScreens ||
                                                    initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                            ) &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            fadeIn(tween(250))
                                        } else {
                                            fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                                        }
                                    },
                                    popExitTransition = {
                                        if (disableAnimations) {
                                            fadeOut(tween(0))
                                        } else if ((
                                                initialState.destination.route in topLevelScreens ||
                                                    initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                            ) &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            fadeOut(tween(200))
                                        } else {
                                            fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .then(
                                                if (isTvDevice) {
                                                    Modifier
                                                        .focusRequester(contentAreaFocusRequester)
                                                        .focusGroup()
                                                        .focusable()
                                                } else {
                                                    Modifier
                                                },
                                            ).nestedScroll(
                                                if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                                    navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                                ) {
                                                    searchBarScrollBehavior.nestedScrollConnection
                                                } else {
                                                    topAppBarScrollBehavior.nestedScrollConnection
                                                },
                                            ),
                                ) {
                                    navigationBuilder(
                                        navController,
                                        topAppBarScrollBehavior,
                                        { latestVersionName },
                                        disableAnimations,
                                        onClearUpdateBadge = { latestVersionName = BuildConfig.VERSION_NAME },
                                    )
                                }
                            }
                        }

                        BackHandler(enabled = playerBottomSheetState.isExpanded) {
                            playerBottomSheetState.collapseSoft()
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        NetworkStatusBanner(
                            state = networkBannerState,
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(
                                        top = if (shouldShowTopBar) topInset + AppBarHeight + 8.dp else topInset + 8.dp,
                                        start = 16.dp,
                                        end = 16.dp,
                                    ).zIndex(10f),
                        )
                    }

                    pendingBackupRestoreUri?.let { uri ->
                        BackupRestoreFromIntentDialog(uri = uri)
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            try {
                                delay(100)
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                            openSearchImmediately = false
                        }
                    }

                    val openSearchFromRoute =
                        navBackStackEntry
                            ?.savedStateHandle
                            ?.getStateFlow("openSearch", false)
                            ?.collectAsStateWithLifecycle()

                    LaunchedEffect(openSearchFromRoute?.value) {
                        if (openSearchFromRoute?.value == true) {
                            navBackStackEntry?.savedStateHandle?.set("openSearch", false)
                            openSearch()
                        }
                    }
                }
            }
        }
    }

    private fun isBackupUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val path = uri.lastPathSegment?.lowercase(Locale.US)
        return path?.endsWith(".backup") == true || uri.toString().lowercase(Locale.US).endsWith(".backup")
    }

    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        if (intent == null) return
        val dataUri = intent.data
        if (isBackupUri(dataUri)) {
            pendingBackupRestoreUri = dataUri
            return
        }
        if (intent.action == ACTION_MUSIC_RECOGNITION) {
            navController.openMusicRecognition()
            return
        }
        if (intent.action == ACTION_AOD_MODE) {
            requestAodMode()
            return
        }
        if (intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query =
                (
                    intent.getStringExtra("query")
                        ?: intent.getStringExtra("android.intent.extra.TITLE")
                        ?: ""
                ).trim()
            if (query.isNotBlank()) {
                pendingVoiceSearchQuery = query
                startMusicServiceSafely()
                playPendingVoiceSearchIfReady()
            }
            return
        }
        if (handleExternalAudioIntent(intent)) {
            return
        }
        handleDeepLinkIntent(intent, navController)
    }

    private fun handleExternalAudioIntent(intent: Intent): Boolean {
        val incomingUris =
            buildList {
                intent.data?.let(::add)
                when (intent.action) {
                    Intent.ACTION_SEND -> {
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                    }

                    Intent.ACTION_SEND_MULTIPLE -> {
                        addAll(
                            IntentCompat
                                .getParcelableArrayListExtra(
                                    intent,
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java,
                                ).orEmpty(),
                        )
                    }
                }
            }.distinct()

        if (incomingUris.isEmpty()) return false

        val fallbackMimeType = intent.type
        val playableUris =
            incomingUris.filter { uri ->
                val mimeType = contentResolver.getType(uri)
                mimeType.isAudioMimeType() || fallbackMimeType.isAudioMimeType() || uri.hasAudioExtension()
            }
        if (playableUris.isEmpty()) return false

        pendingDeepLinkQueue = ListQueue(items = playableUris.map(::toExternalAudioMediaItem))
        startMusicServiceSafely()
        playPendingDeepLinkQueueIfReady()
        return true
    }

    private fun toExternalAudioMediaItem(uri: Uri): MediaItem {
        val mediaId = uri.toString()
        val title = resolveExternalAudioTitle(uri)
        val metadata =
            moe.rukamori.archivetune.models.MediaMetadata(
                id = mediaId,
                title = title,
                artists = emptyList(),
                duration = -1,
            )
        return MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setTag(metadata)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()
    }

    private fun resolveExternalAudioTitle(uri: Uri): String {
        val displayName =
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
                }
            }.getOrNull()
        return displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown)
    }

    private fun String?.isAudioMimeType(): Boolean = this?.startsWith("audio/", ignoreCase = true) == true

    private fun Uri.hasAudioExtension(): Boolean {
        val extension = MimeTypeMap.getFileExtensionFromUrl(toString()).orEmpty()
        val normalized = extension.lowercase(Locale.US)
        return normalized in setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        val authority = uri.authority?.lowercase()
        if (uri.scheme.equals("archivetune", ignoreCase = true) && authority == "together") {
            pendingTogetherJoinLink = uri.toString()
            startMusicServiceSafely()
            joinPendingTogetherIfReady()
            return
        }

        if (uri.scheme.equals("archivetune", ignoreCase = true) && authority == "login") {
            navController.navigate(buildLoginRoute(uri.getQueryParameter(LOGIN_URL_ARGUMENT)))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                uri.getQueryParameter("list")?.let { playlistId ->
                    if (playlistId.startsWith("OLAK5uy_")) {
                        coroutineScope.launch {
                            YouTube
                                .albumSongs(playlistId)
                                .onSuccess { songs ->
                                    songs.firstOrNull()?.album?.id?.let { browseId ->
                                        navController.navigate("album/$browseId")
                                    }
                                }.onFailure { reportException(it) }
                        }
                    } else {
                        navController.navigate("online_playlist/$playlistId")
                    }
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    navController.navigate("album/$browseId")
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    navController.navigate("artist/$artistId")
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }
                val playlistId = uri.getQueryParameter("list")
                val shouldShufflePlaylist = uri.requestsShuffledPlayback()

                videoId?.let { vid ->
                    coroutineScope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                YouTube.queue(listOf(vid), playlistId)
                            }

                        result
                            .onSuccess { queued ->
                                val mediaItem =
                                    queued.firstOrNull { it.id == vid }?.toMediaItem()
                                        ?: queued.firstOrNull()?.toMediaItem()
                                        ?: MediaItem
                                            .Builder()
                                            .setMediaId(vid)
                                            .setUri(vid)
                                            .setCustomCacheKey(vid)
                                            .build()
                                pendingDeepLinkQueue = ListQueue(items = listOf(mediaItem))
                                startMusicServiceSafely()
                                playPendingDeepLinkQueueIfReady()
                            }.onFailure {
                                reportException(it)
                            }
                    }
                    return
                }

                if (path == "watch" && !playlistId.isNullOrBlank()) {
                    coroutineScope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                YouTube.playlist(playlistId)
                            }

                        result
                            .onSuccess { playlistPage ->
                                val endpoint =
                                    when {
                                        shouldShufflePlaylist -> {
                                            playlistPage.playlist.shuffleEndpoint
                                                ?: playlistPage.playlist.playEndpoint
                                        }

                                        else -> {
                                            playlistPage.playlist.playEndpoint
                                                ?: playlistPage.playlist.shuffleEndpoint
                                        }
                                    }

                                endpoint?.let {
                                    pendingDeepLinkQueue = YouTubeQueue.playlist(it)
                                    startMusicServiceSafely()
                                    playPendingDeepLinkQueueIfReady()
                                } ?: navController.navigate("online_playlist/$playlistId")
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    private fun android.net.Uri.requestsShuffledPlayback(): Boolean {
        val value = getQueryParameter("shuffle")?.trim()?.lowercase(Locale.US) ?: return false
        return value == "1" || value == "true"
    }

    private fun startMusicServiceSafely() {
        runCatching { startService(Intent(this, moe.rukamori.archivetune.playback.MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    @Composable
    private fun BackupRestoreFromIntentDialog(
        uri: Uri,
        viewModel: BackupRestoreViewModel = hiltViewModel(),
    ) {
        var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

        AlertDialog(
            onDismissRequest = { pendingBackupRestoreUri = null },
            icon = { Icon(painterResource(R.drawable.restore), null) },
            title = { Text(stringResource(R.string.restore_options_title)) },
            text = {
                Column {
                    BackupCategory.entries.forEach { category ->
                        val isChecked = category in selected
                        val labelRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library
                                BackupCategory.ACCOUNT -> R.string.backup_category_account
                                BackupCategory.SETTINGS -> R.string.backup_category_settings
                            }
                        val descRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                                BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                                BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                            }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent,
                            onClick = {
                                selected = if (isChecked) selected - category else selected + category
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.material3.Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + category else selected - category
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingBackupRestoreUri ?: return@TextButton
                        pendingBackupRestoreUri = null
                        viewModel.restore(this@MainActivity, uri, selected)
                    },
                    enabled = selected.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBackupRestoreUri = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    companion object {
        const val ACTION_SEARCH = "moe.rukamori.archivetune.action.SEARCH"
        const val ACTION_LIBRARY = "moe.rukamori.archivetune.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }

@Composable
private fun OnlineSearchSortMenu(
    selectedSort: OnlineSearchSort,
    onSortSelected: (OnlineSearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        remember {
            listOf(
                OnlineSearchSort.DEFAULT,
                OnlineSearchSort.VIEWS,
            )
        }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.filter_alt),
                contentDescription = null,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text =
                                stringResource(
                                    when (sort) {
                                        OnlineSearchSort.DEFAULT -> R.string.default_style
                                        OnlineSearchSort.VIEWS -> R.string.views
                                    },
                                ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortSelected(sort)
                    },
                    leadingIcon = {
                        if (sort == selectedSort) {
                            Icon(
                                painter = painterResource(R.drawable.done),
                                contentDescription = null,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

private fun Context.isTvDevice(): Boolean {
    val isTelevisionUiMode =
        (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    return isTelevisionUiMode ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}
