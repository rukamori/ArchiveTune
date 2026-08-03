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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.AodPlayerScreen
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import javax.inject.Inject

@AndroidEntryPoint
class AodDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    @Inject
    lateinit var database: MusicDatabase

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

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

        isInteractive = false  // true ambient mode — OS won't kill it for inactivity
        isFullscreen = true
        isScreenBright = false

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AodDreamService)
            setViewTreeSavedStateRegistryOwner(this@AodDreamService)
            setContent {
                ArchiveTuneTheme {
                    val conn = playerConnection
                    val mediaMetadata by (conn?.mediaMetadata ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
                    val isPlaying by (conn?.isPlaying ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

                    var currentPos by remember { mutableLongStateOf(0L) }
                    var songDuration by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(conn) {
                        while (true) {
                            currentPos = conn?.player?.currentPosition ?: 0L
                            songDuration = conn?.player?.duration?.coerceAtLeast(0L) ?: 0L
                            delay(1000L)
                        }
                    }

                    mediaMetadata?.let { metadata ->
                        AodPlayerScreen(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            position = currentPos,
                            duration = songDuration,
                            sliderPosition = null,
                            canSkipPrevious = true,
                            canSkipNext = true,
                            thumbnailCornerRadius = 16f,
                            onPlayPause = { conn?.player?.togglePlayPause() },
                            onSkipPrevious = { conn?.seekToPrevious() },
                            onSkipNext = { conn?.seekToNext() },
                            onSeek = { conn?.player?.seekTo(it) },
                            onSeekFinished = {},
                            onExit = { finish() },
                        )
                    }
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
        serviceScope.cancel()
        runCatching { unbindService(serviceConnection) }
        super.onDestroy()
    }
}
