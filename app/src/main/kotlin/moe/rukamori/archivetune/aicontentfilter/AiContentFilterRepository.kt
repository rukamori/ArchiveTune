/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aicontentfilter

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AiContentFilterEnabledKey
import moe.rukamori.archivetune.constants.AiContentFilterIncludeModerateKey
import moe.rukamori.archivetune.constants.AiContentFilterLastUpdatedKey
import moe.rukamori.archivetune.utils.dataStore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AiContentFilterRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val cacheDirectory = File(context.filesDir, CACHE_DIRECTORY_NAME)
        private val blocklistFile = AtomicFile(File(cacheDirectory, BLOCKLIST_FILE_NAME))
        private val warnlistFile = AtomicFile(File(cacheDirectory, WARNLIST_FILE_NAME))
        private val refreshMutex = Mutex()
        private val cacheStatus =
            MutableStateFlow(
                AiContentFilterStatus(
                    blocklistCount = 0,
                    warnlistCount = 0,
                    lastUpdatedEpochMillis = 0L,
                ),
            )

        @Volatile private var cachedLists: AiChannelLists? = null

        private val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(NETWORK_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        fun observeSettings(): Flow<AiContentFilterSettings> =
            context.dataStore.data
                .map { preferences ->
                    AiContentFilterSettings(
                        enabled = preferences[AiContentFilterEnabledKey] ?: false,
                        includeModerateConfidence = preferences[AiContentFilterIncludeModerateKey] ?: false,
                    )
                }.distinctUntilChanged()

        fun observeStatus(): Flow<AiContentFilterStatus> =
            combine(
                cacheStatus,
                context.dataStore.data.map { preferences ->
                    preferences[AiContentFilterLastUpdatedKey] ?: 0L
                },
            ) { status, lastUpdated ->
                status.copy(lastUpdatedEpochMillis = lastUpdated)
            }.distinctUntilChanged()

        suspend fun getSettings(): AiContentFilterSettings =
            withContext(Dispatchers.IO) {
                val preferences = context.dataStore.data.first()
                AiContentFilterSettings(
                    enabled = preferences[AiContentFilterEnabledKey] ?: false,
                    includeModerateConfidence = preferences[AiContentFilterIncludeModerateKey] ?: false,
                )
            }

        suspend fun setEnabled(enabled: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[AiContentFilterEnabledKey] = enabled
            }
        }

        suspend fun setIncludeModerateConfidence(enabled: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[AiContentFilterIncludeModerateKey] = enabled
            }
        }

        suspend fun loadLists(): AiChannelLists =
            withContext(Dispatchers.IO) {
                cachedLists ?: readCachedLists().also { lists ->
                    cachedLists = lists
                    cacheStatus.value = lists.toStatus(cacheStatus.value.lastUpdatedEpochMillis)
                }
            }

        suspend fun refreshIfStale(force: Boolean = false): AiContentFilterRefreshResult =
            withContext(Dispatchers.IO) {
                refreshMutex.withLock {
                    val settings = getSettings()
                    if (!settings.enabled && !force) {
                        return@withLock AiContentFilterRefreshResult.Success(
                            loadLists().toStatus(cacheStatus.value.lastUpdatedEpochMillis),
                        )
                    }

                    val lastUpdated =
                        context.dataStore.data.first()[AiContentFilterLastUpdatedKey] ?: 0L
                    val now = System.currentTimeMillis()
                    val cached = loadLists()
                    if (!force && cached.blocklist.isNotEmpty() && now - lastUpdated < REFRESH_INTERVAL_MILLIS) {
                        return@withLock AiContentFilterRefreshResult.Success(cached.toStatus(lastUpdated))
                    }

                    val (downloadedBlocklist, downloadedWarnlist) =
                        coroutineScope {
                            val blocklist = async { downloadList(BLOCKLIST_URL) }
                            val warnlist = async { downloadList(WARNLIST_URL) }
                            blocklist.await() to warnlist.await()
                        }
                    if (downloadedBlocklist == null && downloadedWarnlist == null) {
                        return@withLock if (cached.blocklist.isNotEmpty()) {
                            AiContentFilterRefreshResult.Success(cached.toStatus(lastUpdated))
                        } else {
                            AiContentFilterRefreshResult.Unavailable
                        }
                    }

                    val parsedBlocklist = downloadedBlocklist?.let(::parseChannelList)?.takeIf { it.isNotEmpty() }
                    val parsedWarnlist = downloadedWarnlist?.let(::parseChannelList)?.takeIf { it.isNotEmpty() }
                    val storedBlocklist = parsedBlocklist?.takeIf { writeAtomic(blocklistFile, downloadedBlocklist.orEmpty()) }
                    val storedWarnlist = parsedWarnlist?.takeIf { writeAtomic(warnlistFile, downloadedWarnlist.orEmpty()) }
                    if (storedBlocklist == null && storedWarnlist == null) {
                        return@withLock if (cached.blocklist.isNotEmpty()) {
                            AiContentFilterRefreshResult.Success(cached.toStatus(lastUpdated))
                        } else {
                            AiContentFilterRefreshResult.Unavailable
                        }
                    }

                    val refreshed =
                        AiChannelLists(
                            blocklist = storedBlocklist ?: cached.blocklist,
                            warnlist = storedWarnlist ?: cached.warnlist,
                        )
                    cachedLists = refreshed
                    val updatedAt = System.currentTimeMillis()
                    context.dataStore.edit { preferences ->
                        preferences[AiContentFilterLastUpdatedKey] = updatedAt
                    }
                    refreshed
                        .toStatus(updatedAt)
                        .also { status ->
                            cacheStatus.value = status
                        }.let(AiContentFilterRefreshResult::Success)
                }
            }

        private fun readCachedLists(): AiChannelLists =
            AiChannelLists(
                blocklist = readAtomic(blocklistFile)?.let(::parseChannelList).orEmpty(),
                warnlist = readAtomic(warnlistFile)?.let(::parseChannelList).orEmpty(),
            )

        private fun readAtomic(file: AtomicFile): String? =
            try {
                if (!file.baseFile.exists()) return null
                file.readFully().toString(Charsets.UTF_8)
            } catch (exception: IOException) {
                Timber.w(exception, "Failed to read AI content filter cache")
                null
            }

        private fun writeAtomic(
            file: AtomicFile,
            value: String,
        ): Boolean =
            try {
                cacheDirectory.mkdirs()
                val output = file.startWrite()
                try {
                    output.write(value.toByteArray(Charsets.UTF_8))
                    file.finishWrite(output)
                    true
                } catch (exception: IOException) {
                    file.failWrite(output)
                    throw exception
                }
            } catch (exception: IOException) {
                Timber.w(exception, "Failed to store AI content filter cache")
                false
            }

        private suspend fun downloadList(url: String): String? =
            suspendCancellableCoroutine { continuation ->
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .get()
                        .build()
                val call = httpClient.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            exception: IOException,
                        ) {
                            if (!call.isCanceled()) {
                                Timber.w(exception, "Failed to update AI content filter lists")
                            }
                            continuation.resume(null)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            val result =
                                try {
                                    response.use(::readResponse)
                                } catch (exception: IOException) {
                                    Timber.w(exception, "Failed to read AI content filter response")
                                    null
                                }
                            continuation.resume(result)
                        }
                    },
                )
            }

        private fun readResponse(response: Response): String? {
            if (!response.isSuccessful) {
                Timber.w("AI content filter request failed with HTTP %d", response.code)
                return null
            }
            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > MAX_RESPONSE_BYTES) {
                Timber.w("AI content filter response exceeded the size limit")
                return null
            }

            return body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                var totalBytes = 0
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    totalBytes += bytesRead
                    if (totalBytes > MAX_RESPONSE_BYTES) {
                        Timber.w("AI content filter response exceeded the size limit")
                        return null
                    }
                    output.write(buffer, 0, bytesRead)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }

        private fun parseChannelList(raw: String): Set<String> =
            raw
                .lineSequence()
                .map(String::trim)
                .filter { line -> line.isNotEmpty() && !line.startsWith('!') }
                .mapNotNull(::normalizeChannelKey)
                .toSet()

        private fun AiChannelLists.toStatus(lastUpdatedEpochMillis: Long) =
            AiContentFilterStatus(
                blocklistCount = blocklist.size,
                warnlistCount = warnlist.size,
                lastUpdatedEpochMillis = lastUpdatedEpochMillis,
            )

        companion object {
            private const val CACHE_DIRECTORY_NAME = "ai_content_filter"
            private const val BLOCKLIST_FILE_NAME = "aislist_blocklist.txt"
            private const val WARNLIST_FILE_NAME = "aislist_warnlist.txt"
            private const val BLOCKLIST_URL =
                "https://raw.githubusercontent.com/Override92/AiSList/main/AiSList/aislist_blocklist.txt"
            private const val WARNLIST_URL =
                "https://raw.githubusercontent.com/Override92/AiSList/main/AiSList/aislist_warnlist.txt"
            private const val USER_AGENT = "ArchiveTune-AiContentFilter"
            private const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024
            private const val BUFFER_SIZE_BYTES = 8 * 1024
            private const val NETWORK_TIMEOUT_SECONDS = 30L
            private const val NETWORK_CALL_TIMEOUT_SECONDS = 65L
            private const val REFRESH_INTERVAL_MILLIS = 4 * 60 * 60 * 1000L
        }
    }

internal fun normalizeChannelKey(value: String): String? =
    value
        .trim()
        .removePrefix("@")
        .asSequence()
        .filter(Char::isLetterOrDigit)
        .joinToString(separator = "")
        .lowercase()
        .takeIf { it.isNotBlank() }
