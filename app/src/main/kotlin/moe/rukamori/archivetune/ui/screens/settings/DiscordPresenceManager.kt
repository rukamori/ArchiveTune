/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.discord.DiscordOAuthRepository
import moe.rukamori.archivetune.discord.DiscordSocialPresenceClient
import moe.rukamori.archivetune.utils.DiscordImageResolver
import moe.rukamori.archivetune.utils.DiscordRPC
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object DiscordPresenceManager {
    private const val LOG_TAG = "DiscordPresenceManager"
    private const val IMAGE_RESOLUTION_TIMEOUT_MS = 8_000L
    private const val STOP_TIMEOUT_MS = 5_000L

    private val started = AtomicBoolean(false)
    private val updateGeneration = AtomicLong(0L)
    private val rpcMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var scope: CoroutineScope? = null
    private var lifecycleObserver: LifecycleEventObserver? = null
    private var rpcInstance: DiscordRPC? = null
    private var rpcToken: String? = null

    private var consecutiveFailures = 0

    private val lastRpcStartTimeState = MutableStateFlow<Long?>(null)
    val lastRpcStartTimeFlow = lastRpcStartTimeState.asStateFlow()
    val lastRpcStartTime: Long? get() = lastRpcStartTimeState.value

    private val lastRpcEndTimeState = MutableStateFlow<Long?>(null)
    val lastRpcEndTimeFlow = lastRpcEndTimeState.asStateFlow()
    val lastRpcEndTime: Long? get() = lastRpcEndTimeState.value

    fun setLastRpcTimestamps(
        start: Long?,
        end: Long?,
    ) {
        lastRpcStartTimeState.value = start
        lastRpcEndTimeState.value = end
    }

    private fun addLifecycleObserverOnMain(observer: LifecycleEventObserver) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        } else {
            Handler(Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
            }
        }
    }

    private fun removeLifecycleObserverOnMain(observer: LifecycleEventObserver) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        } else {
            Handler(Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
            }
        }
    }

    private suspend fun getOrCreateRpc(
        context: Context,
        token: String,
    ): DiscordRPC {
        val activeToken = DiscordOAuthRepository.getValidAccessToken(context) ?: token
        if (rpcInstance == null || rpcToken != activeToken) {
            runCatching { rpcInstance?.stopActivity() }
                .onFailure { Timber.tag(LOG_TAG).v(it, "failed to stop previous activity") }
            runCatching { rpcInstance?.closeRPC() }
                .onFailure { Timber.tag(LOG_TAG).v(it, "failed to close previous RPC instance") }

            rpcInstance = DiscordRPC(context.applicationContext, activeToken)
            rpcToken = activeToken
        }
        return rpcInstance ?: error("Discord RPC instance was not created")
    }

    suspend fun updatePresence(
        context: Context,
        token: String,
        song: Song?,
        positionMs: Long,
        isPaused: Boolean,
    ): Boolean =
        updatePresence(
            context = context,
            token = token,
            song = song,
            positionMs = positionMs,
            isPaused = isPaused,
            generation = updateGeneration.incrementAndGet(),
        )

    suspend fun clearNow(
        context: Context,
        token: String? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            rpcMutex.withLock {
                try {
                    Timber.tag(LOG_TAG).d(
                        "clearNow tokenProvided=%s hasRpcInstance=%s",
                        !token.isNullOrBlank(),
                        rpcInstance != null,
                    )
                    clearPresenceLocked(appContext, token)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.tag(LOG_TAG).e(error, "clearNow failed")
                    false
                }
            }
        }

    private suspend fun updatePresence(
        context: Context,
        token: String,
        song: Song?,
        positionMs: Long,
        isPaused: Boolean,
        generation: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            rpcMutex.withLock {
                if (generation != updateGeneration.get()) {
                    Timber.tag(LOG_TAG).d("skipped stale presence update")
                    return@withLock true
                }

                try {
                    val activeToken = DiscordOAuthRepository.getValidAccessToken(appContext) ?: token
                    if (activeToken.isBlank()) {
                        Timber.tag(LOG_TAG).w("updatePresence skipped because token is missing")
                        return@withLock false
                    }

                    if (song == null) {
                        val rpc = getOrCreateRpc(appContext, activeToken)
                        rpc.stopActivity()
                        setLastRpcTimestamps(null, null)
                        consecutiveFailures = 0
                        Timber.tag(LOG_TAG).d("cleared presence because no song is active")
                        return@withLock true
                    }

                    runCatching {
                        withTimeout(IMAGE_RESOLUTION_TIMEOUT_MS) {
                            DiscordImageResolver.resolveImagesForSong(appContext, song)
                        }
                    }.onFailure {
                        Timber.tag(LOG_TAG).v(it, "image resolution for presence failed or timed out")
                    }

                    if (generation != updateGeneration.get()) {
                        Timber.tag(LOG_TAG).d("skipped stale presence update after image resolution")
                        return@withLock true
                    }

                    val rpc = getOrCreateRpc(appContext, activeToken)
                    val result =
                        rpc.updateSong(
                            song = song,
                            currentPlaybackTimeMillis = positionMs,
                            isPaused = isPaused,
                        )
                    if (result.isSuccess) {
                        consecutiveFailures = 0
                        updateLastTimestamps(song = song, positionMs = positionMs, isPaused = isPaused)
                        Timber.tag(LOG_TAG).d("updated presence song=%s paused=%s", song.song.title, isPaused)
                        true
                    } else {
                        consecutiveFailures++
                        Timber.tag(LOG_TAG).w(
                            "updatePresence returned failure consecutive=%d",
                            consecutiveFailures,
                        )
                        false
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    consecutiveFailures++
                    Timber.tag(LOG_TAG).e(error, "updatePresence failed consecutive=%d", consecutiveFailures)
                    false
                }
            }
        }

    fun start(
        context: Context,
        token: String,
    ) {
        if (!started.getAndSet(true)) {
            consecutiveFailures = 0
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            lifecycleObserver =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        stop()
                    }
                }
            addLifecycleObserverOnMain(lifecycleObserver!!)
        }

        if (token.isNotBlank()) {
            rpcToken = token
        }
        Timber.tag(LOG_TAG).d("started manager runtime; awaiting external sync trigger")
    }

    suspend fun updateNow(
        context: Context,
        token: String,
        song: Song?,
        positionMs: Long,
        isPaused: Boolean,
    ): Boolean =
        updatePresence(
            context = context,
            token = token,
            song = song,
            positionMs = positionMs,
            isPaused = isPaused,
        )

    fun setOnTransportInvalidated(listener: ((String) -> Unit)?) {
        DiscordSocialPresenceClient.setOnTransportInvalidated(listener)
    }

    private suspend fun clearPresenceLocked(
        context: Context,
        token: String? = null,
    ): Boolean {
        val existingRpc = rpcInstance
        if (existingRpc != null) {
            Timber.tag(LOG_TAG).d("clearPresenceLocked using existing RPC instance")
            existingRpc.stopActivity()
            setLastRpcTimestamps(null, null)
            consecutiveFailures = 0
            return true
        }

        val activeToken = DiscordOAuthRepository.getValidAccessToken(context) ?: token.orEmpty()
        if (activeToken.isBlank()) {
            Timber.tag(LOG_TAG).w("clearPresenceLocked skipped because token is missing")
            return false
        }

        Timber.tag(LOG_TAG).d("clearPresenceLocked creating RPC instance for clear")
        val rpc = getOrCreateRpc(context, activeToken)
        rpc.stopActivity()
        setLastRpcTimestamps(null, null)
        consecutiveFailures = 0
        return true
    }

    fun stop() {
        if (!started.getAndSet(false)) return

        DiscordSocialPresenceClient.setOnTransportInvalidated(null)
        updateGeneration.incrementAndGet()
        scope?.cancel()
        scope = null

        lifecycleObserver?.let { observer ->
            removeLifecycleObserverOnMain(observer)
        }
        lifecycleObserver = null

        val rpcToClose = rpcInstance
        rpcInstance = null
        rpcToken = null
        setLastRpcTimestamps(null, null)

        if (rpcToClose != null) {
            cleanupScope.launch {
                rpcMutex.withLock {
                    runCatching {
                        withTimeout(STOP_TIMEOUT_MS) {
                            rpcToClose.stopActivity()
                            rpcToClose.closeRPC()
                        }
                    }.onFailure {
                        Timber.tag(LOG_TAG).v(it, "stop cleanup failed or timed out")
                    }
                }
            }
        }

        Timber.tag(LOG_TAG).d("stopped")
    }

    fun isRunning(): Boolean = started.get()

    private fun updateLastTimestamps(
        song: Song,
        positionMs: Long,
        isPaused: Boolean,
    ) {
        val durationMs =
            song.song.duration
                .takeIf { it > 0 }
                ?.toLong()
                ?.times(1000L)
        if (isPaused || durationMs == null) {
            setLastRpcTimestamps(null, null)
            return
        }

        val startMs = System.currentTimeMillis() - positionMs.coerceAtLeast(0L)
        setLastRpcTimestamps(startMs, startMs + durationMs)
    }
}
