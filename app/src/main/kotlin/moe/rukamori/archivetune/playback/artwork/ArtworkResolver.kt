/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.artwork

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * The single authoritative artwork-resolution pipeline.
 *
 * Deterministic provider priority (never "last request wins"):
 *   1. [ArtworkProvider.LOCAL_EMBEDDED] for local files / local content URIs.
 *   2. [ArtworkProvider.ORIGINAL_METADATA] when the metadata already carries an artwork URL.
 *   3. [ArtworkProvider.TIDAL] as a fallback only when: the user enabled Tidal artwork,
 *      Tidal is available, no original artwork exists, and the match confidence clears
 *      [MIN_TIDAL_CONFIDENCE].
 *   4. Otherwise [ArtworkProvider.ORIGINAL_METADATA] with a null URL (UI keeps its placeholder).
 *
 * Guarantees:
 *  - Single-flight: concurrent resolves for the same [ArtworkCacheKey] share one fetch.
 *  - A provider disabled mid-flight cannot publish its late result.
 *  - [beginTrack]/[isCurrent] let callers reject results for tracks that are no longer current.
 *  - Successes are cached by artwork identity; failures only briefly (never permanently).
 *  - [CancellationException] is always rethrown.
 */
class ArtworkResolver(
    private val tidalFetcher: TidalArtworkFetcher,
    private val settings: StateFlow<ArtworkSettings>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val generation = AtomicLong(0)

    @Volatile
    private var currentMediaId: String? = null

    @Volatile
    private var activeGeneration: Long = 0

    /** Success cache keyed by artwork identity; access-ordered LRU. */
    private val successCache =
        object : LinkedHashMap<ArtworkCacheKey, ResolvedArtwork>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<ArtworkCacheKey, ResolvedArtwork>?,
            ): Boolean = size > MAX_CACHE_ENTRIES
        }

    /** key -> expiry timestamp (ms). Short-lived so transient failures never poison the cache. */
    private val failureCache = HashMap<ArtworkCacheKey, Long>()
    private val cacheLock = Any()
    private val keyMutexes = ConcurrentHashMap<ArtworkCacheKey, Mutex>()

    /**
     * Marks [mediaId] as the current track and returns the new generation token.
     * Callers must verify [isCurrent] before committing any asynchronously resolved artwork.
     */
    fun beginTrack(mediaId: String): Long {
        val next = generation.incrementAndGet()
        currentMediaId = mediaId
        activeGeneration = next
        Timber.tag(TAG).d("artwork track begin mediaId=%s generation=%d", mediaId, next)
        return next
    }

    /** True when [mediaId] is still the current track and [gen] is the latest generation. */
    fun isCurrent(
        mediaId: String,
        gen: Long,
    ): Boolean = currentMediaId == mediaId && activeGeneration == gen

    /**
     * Invalidates in-flight work, e.g. when the provider settings changed. Late results from
     * before this call can no longer be committed by generation-checked callers.
     */
    fun invalidate() {
        val next = generation.incrementAndGet()
        activeGeneration = next
        Timber.tag(TAG).d("artwork resolution invalidated generation=%d", next)
    }

    /** Cache lookup exposed for consumers that only need to dedupe derived work. */
    fun cached(key: ArtworkCacheKey): ResolvedArtwork? = synchronized(cacheLock) { successCache[key] }

    suspend fun resolve(request: ArtworkRequest): ResolvedArtwork {
        val localUrl = request.originalArtworkUrl
        if (request.isLocal || localUrl.isLocalArtworkUri()) {
            return ResolvedArtwork(
                mediaId = request.mediaId,
                url = localUrl,
                provider = ArtworkProvider.LOCAL_EMBEDDED,
                artworkIdentity = localUrl ?: "local:${request.mediaId}",
            ).also { logResolution(request, it, "local") }
        }

        if (!localUrl.isNullOrBlank()) {
            return ResolvedArtwork(
                mediaId = request.mediaId,
                url = localUrl,
                provider = ArtworkProvider.ORIGINAL_METADATA,
                artworkIdentity = normalizeArtworkIdentity(localUrl),
            ).also { logResolution(request, it, "original") }
        }

        return resolveTidalFallback(request)
    }

    private suspend fun resolveTidalFallback(request: ArtworkRequest): ResolvedArtwork {
        val settingsSnapshot = settings.value
        if (!settingsSnapshot.tidalArtworkEnabled || !settingsSnapshot.tidalAvailable) {
            return noArtwork(request, "tidal disabled/unavailable")
        }

        val key =
            ArtworkCacheKey(
                mediaId = request.mediaId,
                provider = ArtworkProvider.TIDAL,
                artworkIdentity = tidalRequestIdentity(request),
                requestedSize = TIDAL_ARTWORK_SIZE,
            )

        synchronized(cacheLock) {
            successCache[key]?.let { return it }
            val failureExpiry = failureCache[key]
            if (failureExpiry != null && failureExpiry > clock()) {
                Timber.tag(TAG).d("artwork tidal negative-cache hit mediaId=%s", request.mediaId)
                return noArtwork(request, "tidal cached miss")
            }
        }

        val mutex = keyMutexes.getOrPut(key) { Mutex() }
        return mutex.withLock {
            try {
                // Re-check inside the single-flight section: a concurrent caller may have
                // completed the fetch while we waited.
                synchronized(cacheLock) {
                    successCache[key]?.let { return@withLock it }
                }

                Timber.tag(TAG).d(
                    "artwork tidal fetch start mediaId=%s title=%s",
                    request.mediaId,
                    request.title,
                )
                val match =
                    try {
                        withContext(ioDispatcher) { tidalFetcher.fetchArtwork(request) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Timber.tag(TAG).w(error, "artwork tidal fetch failed mediaId=%s", request.mediaId)
                        synchronized(cacheLock) {
                            failureCache[key] = clock() + FAILURE_CACHE_MS
                        }
                        null
                    }

                // The provider must not publish a late result after being disabled mid-flight.
                val settingsAfter = settings.value
                if (!settingsAfter.tidalArtworkEnabled || !settingsAfter.tidalAvailable) {
                    Timber.tag(TAG).d(
                        "artwork tidal result discarded; provider disabled mid-flight mediaId=%s",
                        request.mediaId,
                    )
                    return@withLock noArtwork(request, "tidal disabled mid-flight")
                }

                if (match == null || match.confidence < MIN_TIDAL_CONFIDENCE) {
                    if (match != null) {
                        Timber.tag(TAG).d(
                            "artwork tidal low-confidence rejection mediaId=%s method=%s confidence=%.2f",
                            request.mediaId,
                            match.matchMethod,
                            match.confidence,
                        )
                        synchronized(cacheLock) {
                            failureCache[key] = clock() + NO_MATCH_CACHE_MS
                        }
                    }
                    return@withLock noArtwork(
                        request,
                        if (match == null) "tidal no match" else "tidal low confidence",
                    )
                }

                val resolved =
                    ResolvedArtwork(
                        mediaId = request.mediaId,
                        url = match.artworkUrl,
                        provider = ArtworkProvider.TIDAL,
                        artworkIdentity = normalizeArtworkIdentity(match.artworkUrl),
                        matchConfidence = match.confidence,
                    )
                synchronized(cacheLock) {
                    successCache[key] = resolved
                }
                logResolution(request, resolved, match.matchMethod)
                resolved
            } finally {
                if (!mutex.isLocked) {
                    keyMutexes.remove(key, mutex)
                }
            }
        }
    }

    private fun noArtwork(
        request: ArtworkRequest,
        reason: String,
    ): ResolvedArtwork =
        ResolvedArtwork(
            mediaId = request.mediaId,
            url = null,
            provider = ArtworkProvider.ORIGINAL_METADATA,
            artworkIdentity = "none:${request.mediaId}",
        ).also { logResolution(request, it, reason) }

    private fun logResolution(
        request: ArtworkRequest,
        resolved: ResolvedArtwork,
        detail: String,
    ) {
        Timber.tag(TAG).d(
            "artwork resolved mediaId=%s provider=%s identity=%s confidence=%s detail=%s generation=%d",
            request.mediaId,
            resolved.provider,
            resolved.artworkIdentity,
            resolved.matchConfidence?.let { "%.2f".format(it) } ?: "-",
            detail,
            activeGeneration,
        )
    }

    companion object {
        private const val TAG = "ArtworkResolver"

        /** Minimum Tidal match confidence accepted for display. Below this, fall back. */
        const val MIN_TIDAL_CONFIDENCE = 0.45f
        const val TIDAL_ARTWORK_SIZE = 1080
        const val MAX_CACHE_ENTRIES = 128

        /** Transient network/provider failures: retried after a short window. */
        const val FAILURE_CACHE_MS = 60_000L

        /** Legitimate "nothing matches" results: kept a bit longer to avoid search storms. */
        const val NO_MATCH_CACHE_MS = 10 * 60_000L

        fun normalizeArtworkIdentity(url: String): String = url.trim()

        fun tidalRequestIdentity(request: ArtworkRequest): String {
            val isrc = request.isrc?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            if (isrc != null) return "isrc:$isrc"
            val title = request.title.trim().lowercase()
            val artist = request.artists.firstOrNull()?.trim()?.lowercase().orEmpty()
            val album = request.album?.trim()?.lowercase().orEmpty()
            return "meta:$title|$artist|$album"
        }
    }
}
