/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.downloads.DownloadedArtworkRepository
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolveAudioStreamUseCase
import moe.rukamori.archivetune.playback.stream.ResolvedAudioStream
import moe.rukamori.archivetune.playback.stream.StreamPurpose
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.enumPreference
import moe.rukamori.archivetune.utils.isLowDataModeActive
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        val database: MusicDatabase,
        val databaseProvider: DatabaseProvider,
        @DownloadCache val downloadCache: Cache,
        @PlayerCache val playerCache: Cache,
        private val downloadedArtworkRepository: DownloadedArtworkRepository,
        private val resolveAudioStream: ResolveAudioStreamUseCase,
    ) {
        private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
        private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val downloadExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS)
        private val artworkJobs = mutableMapOf<String, Job>()
        private val downloadPreloadLock = Any()

        private var downloadPreloadTargetId: String? = null
        private var downloadPreloadJob: Job? = null

        private val mediaOkHttpClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = MAX_DOWNLOAD_HTTP_REQUESTS
                        maxRequestsPerHost = MAX_DOWNLOAD_HTTP_REQUESTS
                    },
                ).connectionPool(
                    ConnectionPool(
                        MAX_IDLE_DOWNLOAD_CONNECTIONS,
                        DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES,
                        TimeUnit.MINUTES,
                    ),
                ).addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    val requestBuilder = request.newBuilder()
                    if (request.header("User-Agent") == null) {
                        requestBuilder.header("User-Agent", requestProfile.userAgent)
                    }
                    if (request.header("Origin") == null && requestProfile.origin != null) {
                        requestBuilder.header("Origin", requestProfile.origin)
                    }
                    if (request.header("Referer") == null && requestProfile.referer != null) {
                        requestBuilder.header("Referer", requestProfile.referer)
                    }
                    val response = chain.proceed(requestBuilder.build())
                    if (response.code in STREAM_REFRESH_RESPONSE_CODES) {
                        invalidateResolvedStreamUrl(request.url.toString())
                    }
                    response
                }.build()
        }

        val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

        private val dataSourceFactory =
            ResolvingDataSource.Factory(
                OkHttpDataSource.Factory(mediaOkHttpClient),
            ) { dataSpec ->
                val mediaId = dataSpec.key ?: error("No media id")
                val request = createDownloadStreamRequest(mediaId)
                val pinnedFormatId = request.pinnedFormatId
                val resolved =
                    resolveAudioStream.resolveBlocking(request)
                if (pinnedFormatId != null && resolved.formatId > 0 && resolved.formatId != pinnedFormatId) {
                    downloadCache.removeResource(mediaId)
                }
                if (resolved.formatId > 0) {
                    downloadCache.applyContentMetadataMutations(
                        mediaId,
                        ContentMetadataMutations().set(DOWNLOAD_FORMAT_ID_METADATA_KEY, resolved.formatId.toLong()),
                    )
                }
                persistPlaybackMetadata(mediaId, resolved)
                dataSpec.withResolvedStream(resolved)
            }

        val downloadNotificationHelper =
            DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

        val downloadManager: DownloadManager =
            DownloadManager(
                context,
                DefaultDownloadIndex(databaseProvider),
                DefaultDownloaderFactory(
                    CacheDataSource
                        .Factory()
                        .setCache(downloadCache)
                        .setUpstreamDataSourceFactory(dataSourceFactory)
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                .setCache(downloadCache)
                                .setBufferSize(DOWNLOAD_WRITE_BUFFER_SIZE),
                        ).setFlags(FLAG_IGNORE_CACHE_ON_ERROR),
                    downloadExecutor,
                ),
            ).apply {
                maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
                addListener(
                    object : DownloadManager.Listener {
                        override fun onInitialized(downloadManager: DownloadManager) {
                            refreshActiveDownloadSnapshots()
                            updateNextDownloadPreload(downloadManager)
                        }

                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?,
                        ) {
                            downloads.update { map ->
                                map.toMutableMap().apply {
                                    set(download.request.id, download.toProgressSnapshot())
                                }
                            }
                            if (download.state == Download.STATE_COMPLETED) {
                                scheduleDownloadedArtwork(download.request.id)
                            }
                            updateNextDownloadPreload(downloadManager)
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download,
                        ) {
                            downloads.update { map -> map - download.request.id }
                            cancelDownloadedArtworkJob(download.request.id)
                            downloadScope.launch {
                                downloadedArtworkRepository.remove(download.request.id)
                            }
                            updateNextDownloadPreload(downloadManager)
                        }
                    },
                )
            }

        init {
            downloadScope.launch {
                val result = mutableMapOf<String, Download>()
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        result[cursor.download.request.id] = cursor.download.toProgressSnapshot()
                    }
                }
                downloads.update { current ->
                    result.apply { putAll(current) }
                }
                downloadedArtworkRepository.retainForDownloads(result.keys)
                for (download in result.values) {
                    if (download.state == Download.STATE_COMPLETED) {
                        scheduleDownloadedArtwork(download.request.id).join()
                    }
                }
            }
            downloadScope.launch {
                while (isActive) {
                    delay(DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS)
                    refreshActiveDownloadSnapshots()
                }
            }
        }

        private fun refreshActiveDownloadSnapshots() {
            val activeDownloads = downloadManager.currentDownloads
            if (activeDownloads.isEmpty()) return
            downloads.update { current ->
                current.toMutableMap().apply {
                    activeDownloads.forEach { download ->
                        set(download.request.id, download.toProgressSnapshot())
                    }
                }
            }
        }

        private fun invalidateResolvedStreamUrl(url: String) {
            resolveAudioStream.invalidateUrl(url)
        }

        private fun Download.toProgressSnapshot(): Download {
            val progressSnapshot =
                DownloadProgress().apply {
                    bytesDownloaded = this@toProgressSnapshot.bytesDownloaded
                    percentDownloaded = this@toProgressSnapshot.percentDownloaded
                }
            return Download(
                request,
                state,
                startTimeMs,
                updateTimeMs,
                contentLength,
                stopReason,
                failureReason,
                progressSnapshot,
            )
        }

        fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

        private fun resolveDownloadAudioQuality(lowDataModeActive: Boolean): AudioQuality =
            if (lowDataModeActive) AudioQuality.LOW else audioQuality

        private fun createDownloadStreamRequest(mediaId: String): AudioStreamRequest {
            val lowDataModeActive = context.isLowDataModeActive()
            val hasCachedContent = downloadCache.getCachedSpans(mediaId).isNotEmpty()
            val requiresSongMetadata = database.getSongByIdBlocking(mediaId) == null
            val pinnedFormatId =
                downloadCache
                    .getContentMetadata(mediaId)
                    .get(DOWNLOAD_FORMAT_ID_METADATA_KEY, -1L)
                    .takeIf { it > 0L }
                    ?.toInt()
                    ?: if (hasCachedContent) {
                        database.getFormatByIdBlocking(mediaId)?.itag?.takeIf { it > 0 }
                    } else {
                        null
                    }
            return AudioStreamRequest(
                mediaId = mediaId,
                quality = resolveDownloadAudioQuality(lowDataModeActive),
                networkMetered = lowDataModeActive,
                purpose = StreamPurpose.DOWNLOAD,
                authState = YouTube.currentPlaybackAuthState(),
                pinnedFormatId = pinnedFormatId,
                requiresSongMetadata = requiresSongMetadata,
            )
        }

        private fun updateNextDownloadPreload(downloadManager: DownloadManager) {
            val currentDownloads = downloadManager.currentDownloads
            val activeDownloadCount =
                currentDownloads.count { download ->
                    download.state == Download.STATE_DOWNLOADING ||
                        download.state == Download.STATE_RESTARTING
                }
            val nextTargetId =
                if (activeDownloadCount >= MAX_PARALLEL_DOWNLOADS) {
                    currentDownloads
                        .firstOrNull { download -> download.state == Download.STATE_QUEUED }
                        ?.request
                        ?.id
                } else {
                    null
                }
            val jobs =
                synchronized(downloadPreloadLock) {
                    if (downloadPreloadTargetId == nextTargetId) return

                    val previousJob = downloadPreloadJob
                    val nextJob =
                        nextTargetId?.let { mediaId ->
                            downloadScope.launch(start = CoroutineStart.LAZY) {
                                try {
                                    val request = createDownloadStreamRequest(mediaId)
                                    if (resolveAudioStream.peek(request) == null) {
                                        resolveAudioStream.preload(request)
                                    }
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    Timber.w(exception, "Failed to preload queued download stream for %s", mediaId)
                                } finally {
                                    val runningJob = currentCoroutineContext()[Job]
                                    synchronized(downloadPreloadLock) {
                                        if (downloadPreloadJob === runningJob) {
                                            downloadPreloadJob = null
                                        }
                                    }
                                }
                            }
                        }

                    downloadPreloadTargetId = nextTargetId
                    downloadPreloadJob = nextJob
                    previousJob to nextJob
                }
            jobs.first?.cancel()
            jobs.second?.start()
        }

        private fun persistPlaybackMetadata(
            mediaId: String,
            resolved: ResolvedAudioStream,
        ) {
            downloadScope.launch {
                try {
                    val artworkUrls =
                        database.withTransaction {
                            val existingFormat = getFormatByIdBlocking(mediaId)
                            upsert(
                                FormatEntity(
                                    id = mediaId,
                                    itag = resolved.formatId.takeIf { it > 0 } ?: existingFormat?.itag ?: -1,
                                    mimeType = resolved.mimeType.ifBlank { existingFormat?.mimeType.orEmpty() },
                                    codecs = resolved.codecs.ifBlank { existingFormat?.codecs.orEmpty() },
                                    bitrate = resolved.bitrate.takeIf { it > 0 } ?: existingFormat?.bitrate ?: 0,
                                    sampleRate = resolved.sampleRate ?: existingFormat?.sampleRate,
                                    contentLength =
                                        resolved.contentLength.takeIf { it > 0L }
                                            ?: existingFormat?.contentLength
                                            ?: 0L,
                                    loudnessDb = resolved.loudnessDb ?: existingFormat?.loudnessDb,
                                    perceptualLoudnessDb =
                                        resolved.perceptualLoudnessDb ?: existingFormat?.perceptualLoudnessDb,
                                    playbackUrl = resolved.playbackTrackingUrl ?: existingFormat?.playbackUrl,
                                ),
                            )

                        val now = LocalDateTime.now()
                        val existing = getSongByIdBlocking(mediaId)?.song
                        val resolvedThumbnailUrl =
                            resolved.thumbnailUrl?.takeIf { it.isNotBlank() }

                        val updatedSong =
                            if (existing != null) {
                                existing.copy(
                                    thumbnailUrl = existing.thumbnailUrl?.takeIf { it.isNotBlank() } ?: resolvedThumbnailUrl,
                                    dateDownload = existing.dateDownload ?: now,
                                )
                            } else {
                                SongEntity(
                                    id = mediaId,
                                    title = resolved.title ?: mediaId,
                                    duration = resolved.durationSeconds ?: 0,
                                    thumbnailUrl = resolvedThumbnailUrl,
                                    dateDownload = now,
                                )
                            }

                            upsert(updatedSong)
                            listOf(updatedSong.thumbnailUrl, resolvedThumbnailUrl)
                        }
                    scheduleDownloadedArtwork(mediaId, artworkUrls)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Timber.w(exception, "Failed to persist download metadata")
                }
            }
        }

        private fun DataSpec.withResolvedStream(resolved: ResolvedAudioStream): DataSpec =
            buildUpon()
                .setUri(resolved.url.toUri())
                .setHttpRequestHeaders(httpRequestHeaders + resolved.requestHeaders)
                .build()

        private fun scheduleDownloadedArtwork(
            mediaId: String,
            knownSourceUrls: Collection<String?> = emptyList(),
        ): Job =
            synchronized(artworkJobs) {
                val currentJob = artworkJobs[mediaId]
                if (currentJob?.isActive == true && knownSourceUrls.isEmpty()) return@synchronized currentJob
                currentJob?.cancel()

                val job =
                    downloadScope.launch(start = CoroutineStart.LAZY) {
                        try {
                            val databaseThumbnailUrl =
                                database.withTransaction {
                                    getSongByIdBlocking(mediaId)?.song?.thumbnailUrl
                                }
                            downloadedArtworkRepository.cache(
                                mediaId = mediaId,
                                sourceUrls = knownSourceUrls + databaseThumbnailUrl,
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            Timber.w(exception, "Failed to cache downloaded artwork")
                        } finally {
                            val runningJob = currentCoroutineContext()[Job]
                            synchronized(artworkJobs) {
                                if (artworkJobs[mediaId] === runningJob) {
                                    artworkJobs.remove(mediaId)
                                }
                            }
                        }
                    }
                artworkJobs[mediaId] = job
                job.start()
                job
            }

        private fun cancelDownloadedArtworkJob(mediaId: String) {
            synchronized(artworkJobs) {
                artworkJobs.remove(mediaId)?.cancel()
            }
        }

        companion object {
            private const val DOWNLOAD_FORMAT_ID_METADATA_KEY = "archivetune_download_format_id"
            private const val MAX_PARALLEL_DOWNLOADS = 3
            private const val MAX_IDLE_DOWNLOAD_CONNECTIONS = 12
            private const val MAX_DOWNLOAD_HTTP_REQUESTS = MAX_PARALLEL_DOWNLOADS
            private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 90L
            private const val DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS = 1_000L
            private const val DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES = 5L
            private const val DOWNLOAD_WRITE_BUFFER_SIZE = 256 * 1024
            private val STREAM_REFRESH_RESPONSE_CODES = setOf(403, 404, 410, 416)
        }
    }
