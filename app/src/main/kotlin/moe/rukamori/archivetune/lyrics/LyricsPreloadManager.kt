/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.PreloadQueueLyricsEnabledKey
import moe.rukamori.archivetune.constants.QueueLyricsPreloadCountKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

/**
 * Manages pre-loading of lyrics for upcoming songs in the queue.
 * This improves user experience by having lyrics ready when songs change.
 */
class LyricsPreloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: android.content.Context,
        private val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
        private val lyricsHelper: LyricsHelper,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var preloadJob: Job? = null
        private val preloadSemaphore = Semaphore(MAX_CONCURRENT_PRELOADS)

        /**
         * Called when the current song changes in the player.
         * Triggers pre-loading of lyrics for the next N songs in the queue.
         *
         * @param currentIndex The index of the currently playing song in the queue
         * @param queue Metadata entries preserving their positions in the player queue
         */
        fun onSongChanged(
            currentIndex: Int,
            queue: List<MediaMetadata?>,
        ) {
            preloadJob?.cancel()

            preloadJob =
                scope.launch {
                    try {
                        val preferences = context.dataStore.data.first()
                        val isEnabled = preferences[PreloadQueueLyricsEnabledKey] ?: true

                        if (!isEnabled) {
                            Log.d(TAG, "Queue lyrics pre-load is disabled")
                            return@launch
                        }

                        val isNetworkAvailable =
                            try {
                                networkConnectivity.isCurrentlyConnected()
                            } catch (e: Exception) {
                                true
                            }

                        if (!isNetworkAvailable) {
                            Log.w(TAG, "Network unavailable, skipping lyrics pre-load")
                            return@launch
                        }

                        if (context.isLowDataModeActive(preferences[LowDataModeKey] ?: true)) {
                            Log.d(TAG, "Low Data Mode active, skipping lyrics pre-load")
                            return@launch
                        }

                        val preloadCount = preferences[QueueLyricsPreloadCountKey] ?: DEFAULT_PRELOAD_COUNT
                        val nextSongs = getNextSongs(queue, currentIndex, preloadCount)

                        if (nextSongs.isEmpty()) {
                            Log.d(TAG, "No songs to pre-load")
                            return@launch
                        }

                        Log.d(TAG, "Starting pre-load for ${nextSongs.size} songs")
                        preloadLyrics(nextSongs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
        }

        /**
         * Get the next N songs from the queue after the current index.
         */
        private fun getNextSongs(
            queue: List<MediaMetadata?>,
            currentIndex: Int,
            count: Int,
        ): List<MediaMetadata> {
            if (queue.isEmpty() || currentIndex < 0 || count <= 0) {
                return emptyList()
            }

            return queue
                .asSequence()
                .drop(currentIndex + 1)
                .filterNotNull()
                .take(count)
                .toList()
        }

        /**
         * Pre-load lyrics for the given songs.
         * Uses parallel fetching with limited concurrency.
         */
        private suspend fun preloadLyrics(songs: List<MediaMetadata>) =
            supervisorScope {
                songs
                    .distinctBy { it.id }
                    .map { song ->
                        async {
                            preloadSemaphore.withPermit {
                                preloadLyrics(song)
                            }
                        }
                    }.awaitAll()
            }

        private suspend fun preloadLyrics(song: MediaMetadata) {
            val existingLyrics = database.getLyricsById(song.id)
            if (existingLyrics != null) {
                if (existingLyrics.lyrics == LyricsEntity.LYRICS_NOT_FOUND) {
                    Log.d(TAG, "Retrying missing lyrics for: ${song.title}")
                } else {
                    Log.d(TAG, "Lyrics already cached for: ${song.title}")
                    return
                }
            }

            try {
                val lyrics = lyricsHelper.getLyrics(song)
                if (lyrics == LyricsEntity.LYRICS_NOT_FOUND) return

                database.replaceLyricsIfAbsentOrNotFound(
                    id = song.id,
                    lyrics = lyrics,
                )
                Log.d(TAG, "Pre-loaded lyrics for: ${song.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-load lyrics for ${song.title}: ${e.message}")
                reportException(e)
            }
        }

        /**
         * Cancel any ongoing preload operations.
         */
        fun cancel() {
            preloadJob?.cancel()
            preloadJob = null
        }

        /**
         * Clean up resources when no longer needed.
         */
        fun destroy() {
            cancel()
            scope.cancel()
        }

        companion object {
            private const val TAG = "LyricsPreloadManager"
            private const val DEFAULT_PRELOAD_COUNT = 3
            private const val MAX_CONCURRENT_PRELOADS = 2
        }
    }
