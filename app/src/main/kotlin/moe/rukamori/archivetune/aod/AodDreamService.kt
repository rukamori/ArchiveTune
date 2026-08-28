/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aod

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.dreams.DreamService
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodModeEnabledKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.AodPlayerScreen
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

@AndroidEntryPoint
class AodDreamService :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner {
    @Inject
    lateinit var database: MusicDatabase

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val backPressedDispatcher = OnBackPressedDispatcher { finish() }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backPressedDispatcher

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder is MusicService.MusicBinder) {
                playerConnection = PlayerConnection(this@AodDreamService, binder, database, serviceScope)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        if (!dataStore.get(AodModeEnabledKey, false)) {
            finish()
            return
        }

        isInteractive = true
        isFullscreen = true
        isScreenBright = false

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AodDreamService)
            setViewTreeSavedStateRegistryOwner(this@AodDreamService)
            setViewTreeOnBackPressedDispatcherOwner(this@AodDreamService)
            setContent {
                ArchiveTuneTheme {
                    val conn = playerConnection
                    val fallbackMetadata = remember { MutableStateFlow<MediaMetadata?>(null) }
                    val fallbackPlaying = remember { MutableStateFlow(false) }
                    val mediaMetadata by (conn?.mediaMetadata ?: fallbackMetadata).collectAsStateWithLifecycle()
                    val isPlaying by (conn?.isPlaying ?: fallbackPlaying).collectAsStateWithLifecycle()

                    var currentPos by remember { mutableLongStateOf(0L) }
                    var songDuration by remember { mutableLongStateOf(0L) }
                    var sliderPos by remember { mutableStateOf<Long?>(null) }

                    LaunchedEffect(conn, isPlaying) {
                        if (conn != null) {
                            currentPos = (conn.player?.currentPosition ?: 0L).coerceAtLeast(0L)
                            songDuration = conn.player?.duration?.coerceAtLeast(0L) ?: 0L
                            while (isPlaying) {
                                currentPos = (conn.player?.currentPosition ?: 0L).coerceAtLeast(0L)
                                songDuration = conn.player?.duration?.coerceAtLeast(0L) ?: 0L
                                delay(1000L)
                            }
                        }
                    }

                    val fallbackSkip = remember { MutableStateFlow(true) }
                    val canSkipPrev by (conn?.canSkipPrevious ?: fallbackSkip).collectAsStateWithLifecycle()
                    val canSkipNxt by (conn?.canSkipNext ?: fallbackSkip).collectAsStateWithLifecycle()

                    val fallbackLyrics = remember { MutableStateFlow<LyricsEntity?>(null) }
                    val currentLyricsEntity by (conn?.currentLyrics ?: fallbackLyrics).collectAsStateWithLifecycle(initialValue = null)

                    val metadata = mediaMetadata ?: MediaMetadata(
                        id = "",
                        title = getString(R.string.app_name),
                        artists = emptyList(),
                        duration = 0,
                    )
                    AodPlayerScreen(
                        mediaMetadata = metadata,
                        isPlaying = isPlaying,
                        position = currentPos,
                        duration = songDuration,
                        sliderPosition = sliderPos,
                        canSkipPrevious = canSkipPrev,
                        canSkipNext = canSkipNxt,
                        thumbnailCornerRadius = 16f,
                        onPlayPause = { conn?.player?.togglePlayPause() },
                        onSkipPrevious = { conn?.seekToPrevious() },
                        onSkipNext = { conn?.seekToNext() },
                        onSeek = { sliderPos = it },
                        onSeekFinished = {
                            sliderPos?.let { pos ->
                                conn?.player?.seekTo(pos)
                                currentPos = pos
                                sliderPos = null
                            }
                        },
                        onExit = { finish() },
                        lyricsText = currentLyricsEntity?.lyrics,
                    )
                }
            }
        }

        setContentView(composeView)
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        playerConnection?.dispose()
        playerConnection = null
        serviceScope.cancel()
        runCatching { unbindService(serviceConnection) }
        super.onDestroy()
    }
}
