/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableBetterLyricsPortatoKey
import moe.rukamori.archivetune.constants.EnableBinimumLyricsKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnableMegalobizLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PaxsenixApiKeyKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) {
        private val baseProviders =
            listOf(
                BetterLyricsProvider,
                BetterLyricsPortatoProvider,
                YouLyPlusLyricsProvider,
                BinimumLyricsProvider,
                LrcLibLyricsProvider,
                KuGouLyricsProvider,
                MegalobizLyricsProvider,
                SimpMusicLyricsProvider,
                UnisonLyricsProvider,
                PaxsenixAppleMusicLyricsProvider,
                PaxsenixSpotifyLyricsProvider,
                PaxsenixMusixmatchLyricsProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
            )

        private val providerPreferenceKeys: Map<LyricsProvider, Preferences.Key<Boolean>> =
            mapOf(
                BetterLyricsProvider to EnableBetterLyricsKey,
                BetterLyricsPortatoProvider to EnableBetterLyricsPortatoKey,
                YouLyPlusLyricsProvider to EnableYouLyPlusLyricsKey,
                BinimumLyricsProvider to EnableBinimumLyricsKey,
                LrcLibLyricsProvider to EnableLrcLibKey,
                KuGouLyricsProvider to EnableKugouKey,
                MegalobizLyricsProvider to EnableMegalobizLyricsKey,
                SimpMusicLyricsProvider to EnableSimpMusicLyricsKey,
                UnisonLyricsProvider to EnableUnisonLyricsKey,
                PaxsenixAppleMusicLyricsProvider to EnablePaxsenixAppleMusicLyricsKey,
                PaxsenixSpotifyLyricsProvider to EnablePaxsenixSpotifyLyricsKey,
                PaxsenixMusixmatchLyricsProvider to EnablePaxsenixMusixmatchLyricsKey,
            )

        private val paxsenixProviders =
            setOf(
                PaxsenixAppleMusicLyricsProvider,
                PaxsenixSpotifyLyricsProvider,
                PaxsenixMusixmatchLyricsProvider,
            )

        private val cacheLock = Any()
        private var cacheGeneration = 0L
        private val cache = object : LruCache<RequestKey, CachedResults>(SEARCH_CACHE_BYTES) {
            override fun sizeOf(key: RequestKey, value: CachedResults): Int =
                value.results.sumOf { it.lyrics.length.toLong() * 2L }
                    .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
        private val singleLyricsCache = object : LruCache<RequestKey, String>(SINGLE_CACHE_BYTES) {
            override fun sizeOf(key: RequestKey, value: String): Int =
                (value.length.toLong() * 2L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
        private val inFlight = mutableMapOf<RequestKey, CompletableDeferred<String>>()
        private val providerPermits = Semaphore(MAX_CONCURRENT_PROVIDERS)

        suspend fun getLyrics(
            mediaMetadata: MediaMetadata,
            preferredProviderOnly: Boolean = false,
            forceRefresh: Boolean = false,
        ): String = withContext(Dispatchers.IO) {
            val ordered = orderedProviders()
            val providers = if (preferredProviderOnly) ordered.take(1) else ordered
            val request = RequestKey(
                mediaMetadata.id,
                mediaMetadata.title,
                mediaMetadata.artists.joinToString { it.name },
                mediaMetadata.album?.title,
                mediaMetadata.duration,
                providers.map { it.name },
            )
            if (forceRefresh) invalidateCache(request)
            getOrFetchLyrics(request, providers)
        }

        private suspend fun getOrFetchLyrics(
            request: RequestKey,
            providers: List<LyricsProvider>,
        ): String {
            while (true) {
                currentCoroutineContext().ensureActive()
                val candidate = CompletableDeferred<String>()
                val generation: Long
                val shared: CompletableDeferred<String>
                synchronized(cacheLock) {
                    singleLyricsCache.get(request)?.let { return it }
                    generation = cacheGeneration
                    shared = inFlight.getOrPut(request) { candidate }
                }
                if (shared !== candidate) {
                    try {
                        return shared.await()
                    } catch (e: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        continue
                    }
                }

                try {
                    val lyrics = if (isNetworkAvailable()) {
                        fetchPriorityLyrics(providers, request)
                    } else {
                        LYRICS_NOT_FOUND
                    }
                    currentCoroutineContext().ensureActive()
                    synchronized(cacheLock) {
                        if (generation == cacheGeneration && lyrics != LYRICS_NOT_FOUND) {
                            singleLyricsCache.put(request, lyrics)
                        }
                    }
                    shared.complete(lyrics)
                    return lyrics
                } catch (e: Throwable) {
                    synchronized(cacheLock) {
                        if (inFlight[request] === shared) inFlight.remove(request)
                    }
                    shared.completeExceptionally(e)
                    throw e
                } finally {
                    synchronized(cacheLock) {
                        if (inFlight[request] === shared) inFlight.remove(request)
                    }
                }
            }
        }

        suspend fun getAllLyrics(
            mediaId: String,
            songTitle: String,
            songArtists: String,
            songAlbum: String?,
            duration: Int,
            forceRefresh: Boolean = false,
            callback: (LyricsResult) -> Unit,
        ): Unit = withContext(Dispatchers.IO) {
            val providers = orderedProviders()
            val request = RequestKey(
                mediaId, songTitle, songArtists, songAlbum, duration, providers.map { it.name },
            )
            if (forceRefresh) invalidateCache(request)
            val generation: Long
            val cached: CachedResults?
            synchronized(cacheLock) {
                generation = cacheGeneration
                cached = cache.get(request)?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }
            }
            if (cached != null) {
                cached.results.forEach(callback)
                return@withContext
            }
            if (!isNetworkAvailable()) return@withContext

            val results = mutableListOf<IndexedLyrics>()
            coroutineScope {
                val channel = Channel<IndexedLyrics>(Channel.UNLIMITED)
                val producer = launch {
                    try {
                        supervisorScope {
                            providers.forEachIndexed { index, provider ->
                                launch {
                                    providerPermits.withPermit {
                                        try {
                                            val completed = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                                                provider.getAllLyrics(
                                                    mediaId, songTitle, songArtists, songAlbum, duration,
                                                ) { lyrics ->
                                                    channel.trySend(IndexedLyrics(index, LyricsResult(provider.name, lyrics)))
                                                }
                                                currentCoroutineContext().ensureActive()
                                                true
                                            }
                                            if (completed == null) logTimeout(provider)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            reportException(e)
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        channel.close()
                    }
                }
                try {
                    for (result in channel) {
                        val normalized = withContext(Dispatchers.Default) {
                            LyricsUtils.lyricsOrNotFound(result.result.lyrics)
                        }
                        if (normalized == LYRICS_NOT_FOUND) continue
                        val entry = result.copy(result = result.result.copy(lyrics = normalized))
                        if (results.any { it.result == entry.result }) continue
                        results += entry
                        callback(entry.result)
                    }
                } finally {
                    producer.cancel()
                    channel.cancel()
                }
            }
            if (results.isNotEmpty()) {
                synchronized(cacheLock) {
                    if (generation == cacheGeneration) {
                        cache.put(
                            request,
                            CachedResults(
                                results.sortedBy { it.index }.map { it.result },
                                SystemClock.elapsedRealtime() + SEARCH_CACHE_TTL_MS,
                            ),
                        )
                    }
                }
            }
        }

        private suspend fun fetchPriorityLyrics(
            providers: List<LyricsProvider>,
            request: RequestKey,
        ): String = supervisorScope {
            if (providers.isEmpty()) return@supervisorScope LYRICS_NOT_FOUND
            val pending = providers.mapIndexed { index, provider ->
                async(Dispatchers.IO) {
                    providerPermits.withPermit {
                        fetchProviderLyrics(provider, request)?.let { lyrics ->
                            withContext(Dispatchers.Default) {
                                Candidate(index, lyrics, lyricsQuality(lyrics))
                            }
                        }
                    }
                }
            }.toMutableList()
            val jobs = pending.toList()
            val completed = BooleanArray(providers.size)
            var best: Candidate? = null
            var deadline = SystemClock.elapsedRealtime() + FETCH_TIMEOUT_MS
            try {
                while (pending.isNotEmpty()) {
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break
                    val (finished, result) = withTimeoutOrNull(remaining) {
                        select<Pair<Deferred<Candidate?>, Candidate?>> {
                            pending.forEach { deferred ->
                                deferred.onAwait { deferred to it }
                            }
                        }
                    } ?: break
                    completed[jobs.indexOf(finished)] = true
                    pending.remove(finished)
                    if (result != null) {
                        val previous = best
                        if (previous == null || result.quality > previous.quality ||
                            (result.quality == previous.quality && result.index < previous.index)
                        ) {
                            best = result
                        }
                        val grace = if (best?.quality == WORD_SYNCED_QUALITY) {
                            PROVIDER_PRIORITY_GRACE_MS
                        } else {
                            QUALITY_GRACE_MS
                        }
                        deadline = minOf(deadline, SystemClock.elapsedRealtime() + grace)
                    }
                    val selected = best
                    if (selected?.quality == WORD_SYNCED_QUALITY &&
                        (0 until selected.index).all { completed[it] }
                    ) break
                }
                best?.lyrics ?: LYRICS_NOT_FOUND
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

        private suspend fun fetchProviderLyrics(
            provider: LyricsProvider,
            request: RequestKey,
        ): String? =
            try {
                val result = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    provider.getLyrics(
                        request.mediaId, request.title, request.artists, request.album, request.duration,
                    ).also { currentCoroutineContext().ensureActive() }
                }
                if (result == null) {
                    logTimeout(provider)
                    null
                } else {
                    result.fold(
                        onSuccess = { lyrics ->
                            withContext(Dispatchers.Default) {
                                LyricsUtils.lyricsOrNotFound(lyrics).takeIf { it != LYRICS_NOT_FOUND }
                            }
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            reportException(it)
                            null
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                null
            }

        private fun lyricsQuality(lyrics: String): Int = when {
            LyricsUtils.hasWordSyncedLyrics(lyrics) -> WORD_SYNCED_QUALITY
            LyricsUtils.isTtml(lyrics) || LyricsUtils.isLineSyncedLrc(lyrics) -> 1
            else -> 0
        }

        private fun logTimeout(provider: LyricsProvider) {
            GlobalLog.append(Log.WARN, "LyricsHelper", "Lyrics request timed out: ${provider.name}")
        }

        private fun isNetworkAvailable(): Boolean =
            try {
                networkConnectivity.isCurrentlyConnected()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                true
            }

        private suspend fun orderedProviders(): List<LyricsProvider> {
            val preferences = context.dataStore.data.first()
            val orderStr = preferences[LyricsProviderOrderKey]
            val orderedEnums = deserializeLyricsProviderOrder(orderStr)
            val providerMap: Map<PreferredLyricsProvider, LyricsProvider> =
                mapOf(
                    PreferredLyricsProvider.LRCLIB to LrcLibLyricsProvider,
                    PreferredLyricsProvider.KUGOU to KuGouLyricsProvider,
                    PreferredLyricsProvider.MEGALOBIZ to MegalobizLyricsProvider,
                    PreferredLyricsProvider.BETTER_LYRICS to BetterLyricsProvider,
                    PreferredLyricsProvider.BETTER_LYRICS_PORTATO to BetterLyricsPortatoProvider,
                    PreferredLyricsProvider.YOULY_PLUS to YouLyPlusLyricsProvider,
                    PreferredLyricsProvider.BINIMUM to BinimumLyricsProvider,
                    PreferredLyricsProvider.SIMPMUSIC to SimpMusicLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC to PaxsenixAppleMusicLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_SPOTIFY to PaxsenixSpotifyLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_MUSIXMATCH to PaxsenixMusixmatchLyricsProvider,
                    PreferredLyricsProvider.UNISON to UnisonLyricsProvider,
                )
            val userOrdered = orderedEnums.mapNotNull { providerMap[it] }
            val rest = baseProviders.filterNot { it in userOrdered }
            val paxsenixEnabled = preferences[EnablePaxsenixLyricsKey] ?: true
            val paxsenixApiKeyConfigured = !preferences[PaxsenixApiKeyKey].isNullOrBlank()
            return (userOrdered + rest).distinct().filter { provider ->
                val providerEnabled = providerPreferenceKeys[provider]?.let { preferences[it] } ?: true
                val paxsenixProviderEnabled =
                    provider !in paxsenixProviders || (paxsenixEnabled && paxsenixApiKeyConfigured)
                providerEnabled && paxsenixProviderEnabled
            }
        }

        fun clearCache() {
            synchronized(cacheLock) {
                cacheGeneration++
                cache.evictAll()
                singleLyricsCache.evictAll()
                inFlight.clear()
            }
        }

        private fun invalidateCache(request: RequestKey) {
            synchronized(cacheLock) {
                cacheGeneration++
                cache.remove(request)
                singleLyricsCache.remove(request)
                inFlight.remove(request)
            }
        }

        private data class RequestKey(
            val mediaId: String,
            val title: String,
            val artists: String,
            val album: String?,
            val duration: Int,
            val providers: List<String>,
        )

        private data class CachedResults(
            val results: List<LyricsResult>,
            val expiresAt: Long,
        )

        private data class IndexedLyrics(
            val index: Int,
            val result: LyricsResult,
        )

        private data class Candidate(
            val index: Int,
            val lyrics: String,
            val quality: Int,
        )

        companion object {
            private const val SEARCH_CACHE_BYTES = 8 * 1024 * 1024
            private const val SINGLE_CACHE_BYTES = 4 * 1024 * 1024
            private const val MAX_CONCURRENT_PROVIDERS = 6
            private const val FETCH_TIMEOUT_MS = 12_000L
            private const val PROVIDER_TIMEOUT_MS = 8_000L
            private const val QUALITY_GRACE_MS = 1_500L
            private const val PROVIDER_PRIORITY_GRACE_MS = 350L
            private const val SEARCH_CACHE_TTL_MS = 120_000L
            private const val WORD_SYNCED_QUALITY = 2
        }
    }

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
