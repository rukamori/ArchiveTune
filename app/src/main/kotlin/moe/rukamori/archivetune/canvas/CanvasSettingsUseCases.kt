/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartCanvasPolicyUseCase @Inject constructor(
    private val repository: CanvasSettingsRepository,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        CanvasNetworkAccess.connectivity = repository::currentConnectivity
        CanvasRequestPolicy.check = { source -> CanvasNetworkAccess.check(source) }
        CanvasRequestPolicy.intercept = CanvasNetworkAccess::intercept
        job = scope.launch(Dispatchers.IO) {
            var previous: CanvasPolicy? = null
            flow {
                repository.initializeCache()
                emitAll(combine(repository.configuration, repository.connectivity) { configuration, connectivity ->
                    CanvasPolicy(configuration, connectivity, ready = true)
                })
            }.onEach { policy ->
                CanvasNetworkAccess.update(policy)
                if (!policy.networkAllowed || previous?.configuration?.source != policy.configuration.source) {
                    repository.cancelDownloads()
                }
                if (previous?.configuration?.cacheLimitMb != policy.configuration.cacheLimitMb) {
                    repository.applyCacheLimit(policy.configuration.cacheLimitMb)
                }
                previous = policy
            }.retryWhen { cause, _ ->
                if (cause is CancellationException) throw cause
                Timber.e(cause, "Canvas configuration observation failed")
                CanvasNetworkAccess.update(CanvasPolicy(configurationError = true))
                repository.cancelDownloads()
                previous = null
                delay(5_000)
                true
            }.collect {}
        }
    }
}

class CanvasSettingsUseCases @Inject constructor(
    private val repository: CanvasSettingsRepository,
) {
    val policy = CanvasNetworkAccess.policy
    val spotifyConnected = repository.spotifyConnected

    suspend fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)
    suspend fun setSource(source: CanvasSource) = repository.setSource(source)
    suspend fun setWifiOnly(wifiOnly: Boolean) = repository.setWifiOnly(wifiOnly)

    suspend fun setCacheLimit(limitMb: Int) {
        require(limitMb in CACHE_LIMITS)
        repository.setCacheLimit(limitMb)
        repository.applyCacheLimit(limitMb)
    }

    suspend fun cacheBytes(): Long = repository.cacheBytes()
    suspend fun clearCache() = repository.clearCache()

    fun pendingHealth(policy: CanvasPolicy, spotifyConnected: Boolean): CanvasHealthStatus = CanvasHealthStatus(
        betterLyrics = healthAvailability(policy, CanvasSource.BETTER_LYRICS),
        appleMusic = healthAvailability(policy, CanvasSource.APPLE_MUSIC),
        tidal = healthAvailability(policy, CanvasSource.TIDAL),
        spotify = healthAvailability(policy, CanvasSource.SPOTIFY, spotifyConnected),
        modules = healthAvailability(policy, CanvasSource.MODULES),
    )

    suspend fun checkHealth(policy: CanvasPolicy, spotifyConnected: Boolean): CanvasHealthStatus = coroutineScope {
        val betterLyrics = async { checkProvider(policy, CanvasSource.BETTER_LYRICS) }
        val appleMusic = async { checkProvider(policy, CanvasSource.APPLE_MUSIC) }
        val tidal = async { checkProvider(policy, CanvasSource.TIDAL) }
        val spotify = async { checkProvider(policy, CanvasSource.SPOTIFY, spotifyConnected) }
        val modules = async { checkProvider(policy, CanvasSource.MODULES) }
        CanvasHealthStatus(betterLyrics.await(), appleMusic.await(), tidal.await(), spotify.await(), modules.await())
    }

    private suspend fun checkProvider(policy: CanvasPolicy, source: CanvasSource, spotifyConnected: Boolean = true): CanvasHealth {
        val availability = healthAvailability(policy, source, spotifyConnected)
        if (availability != CanvasHealth.CHECKING) return availability
        return try {
            if (withTimeoutOrNull(if (source == CanvasSource.SPOTIFY) 45_000L else 20_000L) { repository.isHealthy(source) } == true) {
                CanvasHealth.AVAILABLE
            } else {
                CanvasHealth.UNAVAILABLE
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Canvas provider health check failed: %s", source)
            CanvasHealth.UNAVAILABLE
        }
    }

    private fun healthAvailability(policy: CanvasPolicy, source: CanvasSource, spotifyConnected: Boolean = true): CanvasHealth = when {
        !policy.ready -> CanvasHealth.NOT_CHECKED
        !policy.configuration.source.accepts(source) -> CanvasHealth.NOT_SELECTED
        !policy.configuration.enabled -> CanvasHealth.DISABLED
        source == CanvasSource.SPOTIFY && !spotifyConnected -> CanvasHealth.NOT_CONNECTED
        !policy.connectivity.online -> CanvasHealth.OFFLINE
        policy.configuration.wifiOnly && !policy.connectivity.wifi -> CanvasHealth.WIFI_REQUIRED
        policy.configuration.lowDataMode && policy.connectivity.metered -> CanvasHealth.LOW_DATA_MODE
        else -> CanvasHealth.CHECKING
    }

    companion object {
        val CACHE_LIMITS: List<Int> = listOf(0, 64, 128, 256, 512, 1024, 2048, 4096, 8192, -1)
    }
}
