/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

fun BrowseEndpoint.moodAndGenresArtworkKey(): String = "$browseId:${params.orEmpty()}"

@Singleton
class MoodAndGenresArtworkRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private class Entry {
            val mutex = Mutex()

            @Volatile
            var version = 0L
            var result: Result<String?>? = null
            var cachedAt = 0L
        }

        private val entries = ConcurrentHashMap<String, Entry>()
        private val requests = Semaphore(3)

        suspend fun load(endpoint: BrowseEndpoint): Result<String?> {
            val key = endpoint.moodAndGenresArtworkKey()
            val entry = entries.getOrPut(key) { Entry() }
            val version = entry.version
            return entry.mutex.withLock {
                if (entry.version != version) return@withLock checkNotNull(entry.result)
                val cachedUrl = entry.result?.getOrNull()
                if (cachedUrl != null && isFresh(entry.cachedAt)) {
                    return@withLock Result.success(cachedUrl)
                }

                val result: Result<String?> =
                    try {
                        requests.withPermit {
                            withContext(Dispatchers.IO) {
                                val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                                val encoded = preferences.getString(key, null)
                                val separator = encoded?.lastIndexOf('|') ?: -1
                                val persistedUrl = encoded?.takeIf { separator > 0 }?.substring(0, separator)
                                val persistedAt = encoded?.substring(separator + 1)?.toLongOrNull()
                                if (!persistedUrl.isNullOrBlank() && persistedAt != null && isFresh(persistedAt)) {
                                    entry.cachedAt = persistedAt
                                    Result.success(persistedUrl)
                                } else {
                                    if (encoded != null) preferences.edit().remove(key).apply()
                                    val url = YouTube.browse(endpoint.browseId, endpoint.params).getOrThrow().thumbnail
                                        ?.takeIf(String::isNotBlank)
                                    if (url != null) {
                                        entry.cachedAt = System.currentTimeMillis()
                                        preferences.edit().putString(key, "$url|${entry.cachedAt}").apply()
                                    }
                                    Result.success(url)
                                }
                            }
                        }
                    } catch (exception: Exception) {
                        if (exception is CancellationException) throw exception
                        Result.failure(exception)
                    }
                entry.result = result
                entry.version++
                result
            }
        }

        private fun isFresh(cachedAt: Long): Boolean =
            cachedAt > 0L && System.currentTimeMillis() - cachedAt in 0..ArtworkTtlMs

        private companion object {
            const val PreferencesName = "mood_genre_artwork_cache"
            const val ArtworkTtlMs = 7L * 24 * 60 * 60 * 1000
        }
    }
