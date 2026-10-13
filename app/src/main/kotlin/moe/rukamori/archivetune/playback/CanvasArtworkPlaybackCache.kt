/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.canvas.CanvasNetworkAccess
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

object CanvasArtworkPlaybackCache {
    private const val DEFAULT_MAX_SIZE_MEGABYTES = 256
    private const val PERSIST_FILE = "canvas_artwork_cache.json"
    private const val PERSIST_DEBOUNCE_MS = 2_000L
    private const val DOWNLOAD_BUFFER_SIZE_BYTES = 64 * 1024
    private const val DOWNLOAD_MAX_ATTEMPTS = 4
    private const val DOWNLOAD_RETRY_DELAY_MS = 750L
    private const val CACHE_SIZE_BYTES_PER_MEGABYTE = 1024L * 1024L

    private val map = LinkedHashMap<String, CanvasCacheEntry>(DEFAULT_MAX_SIZE_MEGABYTES, 0.75f, true)
    private val cacheJobs = LinkedHashMap<String, Job>()
    private val activeCalls = LinkedHashMap<Call, Job>()
    private val pendingFiles = LinkedHashMap<String, Job>()

    @Volatile private var maxSizeBytes = DEFAULT_MAX_SIZE_MEGABYTES.toLong() * CACHE_SIZE_BYTES_PER_MEGABYTE

    @Volatile private var cacheDirectory: File? = null

    @Volatile private var cacheFile: File? = null

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val directClient: OkHttpClient by lazy {
        canvasClient(proxy = null)
    }

    private val streamClient: OkHttpClient by lazy {
        canvasClient(proxy = YouTube.streamOkHttpProxy)
    }

    private fun canvasClient(proxy: Proxy?): OkHttpClient {
        return OkHttpClient
            .Builder()
            .apply {
                if (proxy != null) this.proxy(proxy)
            }.connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val source = chain.request().tag(CanvasSource::class.java)
                    ?: throw IOException("Canvas download has no provider identity")
                CanvasNetworkAccess.intercept(chain, source)
            }
            .addInterceptor { chain ->
                val request = chain.request()
                if (!request.url.isYouTubeMediaHost()) {
                    return@addInterceptor chain.proceed(
                        request
                            .newBuilder()
                            .header("User-Agent", CanvasDownloadUserAgent)
                            .build(),
                    )
                }
                val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                chain.proceed(
                    StreamClientUtils
                        .applyRequestProfile(
                            request.newBuilder(),
                            requestProfile,
                        ).build(),
                )
            }.build()
    }

    @Synchronized
    fun init(context: Context, limitMb: Int = DEFAULT_MAX_SIZE_MEGABYTES) {
        maxSizeBytes = limitMb.toCanvasCacheLimitBytes()
        val directory = StorageLocationRepository.cacheDirectory(context, StorageFolderKind.CANVAS_CACHE)
        cacheDirectory = directory
        cacheFile = directory.resolve(PERSIST_FILE)
        loadFromDisk()
        if (maxSizeBytes == 0L) clear()
    }

    @Synchronized
    fun get(
        mediaId: String,
        preferCachedOnly: Boolean = false,
    ): CanvasArtwork? {
        if (maxSizeBytes == 0L || mediaId.isBlank()) return null
        val entry = map[mediaId] ?: return null
        val playable =
            entry.toPlayableArtwork(
                directory = cacheDirectory ?: return null,
                preferCachedOnly = preferCachedOnly,
            )
        if (playable == null) {
            map.remove(mediaId)
            schedulePersist()
            return null
        }
        map[mediaId] = entry.copy(lastAccessedAtMs = System.currentTimeMillis())
        schedulePersist()
        return playable
    }

    suspend fun put(
        mediaId: String,
        artwork: CanvasArtwork,
    ): CanvasArtwork =
        withContext(Dispatchers.IO) {
            if (maxSizeBytes == 0L || mediaId.isBlank()) return@withContext artwork
            val directory = cacheDirectory ?: return@withContext artwork
            directory.mkdirs()

            val current =
                synchronized(this@CanvasArtworkPlaybackCache) {
                    val existing = map[mediaId]
                    if (existing != null && (existing.artwork.source != artwork.source ||
                            !existing.artwork.matchesSongIdentity(artwork.name.orEmpty(), artwork.artist.orEmpty()))) {
                        remove(mediaId)
                    }
                    map[mediaId]?.toPlayableArtwork(
                        directory = directory,
                        preferCachedOnly = false,
                    )
                }
            val artworkToCache = current ?: artwork
            cacheArtworkInBackground(
                directory = directory,
                mediaId = mediaId,
                artwork = artworkToCache,
            )

            artworkToCache
        }

    suspend fun replace(
        mediaId: String,
        artwork: CanvasArtwork,
    ): CanvasArtwork =
        withContext(Dispatchers.IO) {
            if (maxSizeBytes == 0L || mediaId.isBlank()) return@withContext artwork
            val directory = cacheDirectory ?: return@withContext artwork
            directory.mkdirs()

            val now = System.currentTimeMillis()
            synchronized(this@CanvasArtworkPlaybackCache) {
                remove(mediaId)
                map[mediaId] =
                    CanvasCacheEntry(
                        mediaId = mediaId,
                        artwork = artwork,
                        regularFileName = null,
                        verticalFileName = null,
                        createdAtMs = now,
                        lastAccessedAtMs = now,
                    )
                schedulePersist()
            }
            cacheArtworkInBackground(
                directory = directory,
                mediaId = mediaId,
                artwork = artwork,
            )
            artwork
        }

    private fun cacheArtworkInBackground(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        synchronized(this@CanvasArtworkPlaybackCache) {
            val source = artwork.source ?: throw IOException("Canvas artwork has no provider identity")
            CanvasNetworkAccess.check(source)
            cacheJobs[mediaId]
                ?.takeIf { job -> job.isActive }
                ?.let { return }
            cacheJobs[mediaId] =
                persistScope.launch {
                    try {
                        cacheArtworkVideos(
                            directory = directory,
                            mediaId = mediaId,
                            artwork = artwork,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.w(error, "Canvas background download failed")
                    } finally {
                        val completedJob = currentCoroutineContext()[Job]
                        synchronized(this@CanvasArtworkPlaybackCache) {
                            if (cacheJobs[mediaId] === completedJob) cacheJobs.remove(mediaId)
                            val abandonedFiles = pendingFiles.filterValues { it === completedJob }.keys.toList()
                            abandonedFiles.forEach { fileName ->
                                pendingFiles.remove(fileName)
                                if (map.values.none { it.regularFileName == fileName || it.verticalFileName == fileName }) {
                                    val file = directory.resolve(fileName)
                                    if (!file.delete() && file.exists()) Timber.w("Failed to remove unindexed canvas video")
                                }
                            }
                        }
                    }
                }
        }
    }

    private suspend fun cacheArtworkVideos(
        directory: File,
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        val source = artwork.source ?: throw IOException("Canvas artwork has no provider identity")
        CanvasNetworkAccess.check(source)
        val current = synchronized(this@CanvasArtworkPlaybackCache) { map[mediaId] }
        val regularUrl = artwork.downloadableRegularUrl()
        val verticalUrl = artwork.downloadableVerticalUrl()
        val sharedVideo = regularUrl != null && regularUrl == verticalUrl
        val regularFileName =
            cacheCanvasVideo(
                directory = directory,
                mediaId = mediaId,
                variant = CanvasVideoVariant.Regular,
                source = source,
                url = regularUrl,
                currentFileName = current?.regularFileName ?: current?.verticalFileName.takeIf { sharedVideo },
            )
        persistEntry(
            directory = directory,
            entry =
                CanvasCacheEntry(
                    mediaId = mediaId,
                    artwork = artwork,
                    regularFileName = regularFileName,
                    verticalFileName = current?.verticalFileName,
                    createdAtMs = current?.createdAtMs ?: System.currentTimeMillis(),
                    lastAccessedAtMs = System.currentTimeMillis(),
                ),
        )
        val verticalFileName = if (sharedVideo && regularFileName != null) {
            regularFileName
        } else {
            cacheCanvasVideo(
                directory = directory,
                mediaId = mediaId,
                variant = CanvasVideoVariant.Vertical,
                source = source,
                url = verticalUrl,
                currentFileName = current?.verticalFileName,
            )
        }

        val now = System.currentTimeMillis()
        val entry =
            CanvasCacheEntry(
                mediaId = mediaId,
                artwork = artwork,
                regularFileName = regularFileName,
                verticalFileName = verticalFileName,
                createdAtMs = current?.createdAtMs ?: now,
                lastAccessedAtMs = now,
            )

        if (regularFileName == null && verticalFileName == null) {
            Timber.tag(CanvasCacheLogTag).d("Canvas artwork resolved without downloadable video for %s", mediaId)
        }

        persistEntry(directory = directory, entry = entry)
    }

    @Synchronized
    fun byteSize(): Long {
        val directory = cacheDirectory ?: return 0L
        val files = directory.listFiles() ?: if (directory.exists()) {
            throw IOException("Cannot read the canvas cache directory")
        } else {
            return 0L
        }
        return files.sumOf { file ->
            if (file.isFile && (file.name.endsWith(".mp4") || file.name.endsWith(".part"))) file.length() else 0L
        }
    }

    @Synchronized
    fun clear() {
        cancelCacheJobsLocked()
        clearFilesLocked()
        map.clear()
        schedulePersist()
    }

    @Synchronized
    fun clearAndPersist(): Boolean = try {
        cancelCacheJobsLocked()
        persistJob?.cancel()
        clearFilesLocked()
        map.clear()
        writeToDisk()
    } catch (error: Exception) {
        Timber.e(error, "Failed to clear canvas cache")
        false
    }

    @Synchronized
    fun cancelDownloads() {
        cancelCacheJobsLocked()
    }

    @Synchronized
    fun remove(mediaId: String) {
        cacheJobs.remove(mediaId)?.let { job ->
            job.cancel()
            activeCalls.filterValues { it === job }.keys.forEach(Call::cancel)
        }
        val entry = map.remove(mediaId) ?: return
        val directory = cacheDirectory
        if (directory != null) {
            listOfNotNull(entry.regularFileName, entry.verticalFileName).distinct().forEach { fileName ->
                runCatching { directory.resolve(fileName).delete() }
            }
        }
        schedulePersist()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSizeBytes = value.toCanvasCacheLimitBytes()
        val directory = cacheDirectory
        if (maxSizeBytes == 0L) {
            cancelCacheJobsLocked()
            clearFilesLocked()
            map.clear()
            schedulePersist()
            return
        }
        if (directory != null) {
            trimLocked(directory)
        }
        schedulePersist()
    }

    @Synchronized
    private fun loadFromDisk() {
        val file = cacheFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText()
            if (raw.isBlank()) return
            val restored = decodeEntries(raw)
            map.clear()
            restored
                .filter { entry -> entry.mediaId.isNotBlank() }
                .forEach { entry -> map[entry.mediaId] = entry }
            cacheDirectory?.let(::trimLocked)
            Timber.d("Canvas cache restored: ${map.size} entries from disk")
        } catch (error: Exception) {
            Timber.e(error, "Failed to restore canvas cache from disk")
            runCatching { file.delete() }
        }
    }

    private fun decodeEntries(raw: String): List<CanvasCacheEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(CanvasCacheEntry.serializer()), raw)
        }.getOrElse {
            val legacy =
                json.decodeFromString(
                    kotlinx.serialization.builtins.MapSerializer(
                        String.serializer(),
                        CanvasArtwork.serializer(),
                    ),
                    raw,
                )
            val now = System.currentTimeMillis()
            legacy.map { (mediaId, artwork) ->
                CanvasCacheEntry(
                    mediaId = mediaId,
                    artwork = artwork,
                    regularFileName = null,
                    verticalFileName = null,
                    createdAtMs = now,
                    lastAccessedAtMs = now,
                )
            }
        }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob =
            persistScope.launch {
                delay(PERSIST_DEBOUNCE_MS)
                writeToDisk()
            }
    }

    private suspend fun persistEntry(
        directory: File,
        entry: CanvasCacheEntry,
    ) {
        val job = currentCoroutineContext()[Job]
        synchronized(this@CanvasArtworkPlaybackCache) {
            if (job?.isActive != true || cacheJobs[entry.mediaId] !== job) return
            CanvasNetworkAccess.check(entry.artwork.source)
            map[entry.mediaId] = entry
            entry.regularFileName?.let { pendingFiles.remove(it) }
            entry.verticalFileName?.let { pendingFiles.remove(it) }
            trimLocked(directory)
            schedulePersist()
        }
    }

    @Synchronized
    private fun writeToDisk(): Boolean {
        val file = cacheFile ?: return true
        return try {
            val snapshot: List<CanvasCacheEntry>
            synchronized(this@CanvasArtworkPlaybackCache) {
                snapshot = map.values.toList()
            }
            val raw = json.encodeToString(ListSerializer(CanvasCacheEntry.serializer()), snapshot)
            file.parentFile?.mkdirs()
            file.writeText(raw)
            true
        } catch (error: Exception) {
            Timber.e(error, "Failed to persist canvas cache to disk")
            false
        }
    }

    private suspend fun cacheCanvasVideo(
        directory: File,
        mediaId: String,
        variant: CanvasVideoVariant,
        source: CanvasSource,
        url: String?,
        currentFileName: String?,
    ): String? {
        currentFileName
            ?.takeIf { fileName ->
                directory
                    .resolve(fileName)
                    .takeIf(File::isUsableFile)
                    ?.takeIf(File::isValidCanvasVideo) != null
            }?.let { return it }
        if (url.isNullOrBlank()) return null
        val fileName = canvasFileName(mediaId, variant, url)
        val target = directory.resolve(fileName)
        if (target.isUsableFile()) {
            if (target.isValidCanvasVideo()) return fileName
            runCatching { target.delete() }
        }

        val partial = directory.resolve("$fileName.${java.util.UUID.randomUUID()}.part")
        return try {
            downloadToFile(url = url, target = partial, source = source)
            if (partial.length() <= 0L) throw IOException("Downloaded empty canvas video")
            if (!partial.isValidCanvasVideo()) throw IOException("Downloaded canvas is not a valid video")
            val job = currentCoroutineContext()[Job]
            synchronized(this@CanvasArtworkPlaybackCache) {
                if (job?.isActive != true || cacheJobs[mediaId] !== job) throw CancellationException("Canvas download superseded")
                CanvasNetworkAccess.check(source)
                if (partial.length() > maxSizeBytes) throw IOException("Canvas exceeds the cache limit")
                if (target.exists() && !target.delete()) throw IOException("Failed to replace existing canvas video")
                if (!partial.renameTo(target)) throw IOException("Failed to commit canvas video")
                pendingFiles[fileName] = job
            }
            fileName
        } catch (error: Exception) {
            if (!partial.delete() && partial.exists()) Timber.w("Failed to remove partial canvas download")
            if (error is CancellationException) throw error
            Timber.w(error, "Failed to cache canvas video")
            currentFileName
                ?.takeIf { fileName ->
                    directory
                        .resolve(fileName)
                        .takeIf(File::isUsableFile)
                        ?.takeIf(File::isValidCanvasVideo) != null
                }
        }
    }

    private suspend fun downloadToFile(
        url: String,
        target: File,
        source: CanvasSource,
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        target.parentFile?.mkdirs()
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < DOWNLOAD_MAX_ATTEMPTS) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                downloadToPartialFile(
                    url = url,
                    target = target,
                    source = source,
                    existingBytes = target.takeIf { file -> file.isFile }?.length()?.coerceAtLeast(0L) ?: 0L,
                )
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                attempt += 1
                if (attempt >= DOWNLOAD_MAX_ATTEMPTS) break
                Timber.w(error, "Canvas download interrupted, retrying")
                delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
            }
        }
        throw IOException("Canvas download failed after $DOWNLOAD_MAX_ATTEMPTS attempts", lastError)
    }

    private suspend fun downloadToPartialFile(
        url: String,
        target: File,
        source: CanvasSource,
        existingBytes: Long,
    ) {
        CanvasNetworkAccess.check(source)
        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .tag(CanvasSource::class.java, source)
                .header("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        val request = requestBuilder.build()
        val callClient = if (request.url.isYouTubeMediaHost()) streamClient else directClient
        val call = callClient.newCall(request)
        val job = currentCoroutineContext()[Job] ?: error("Canvas download requires a coroutine job")
        synchronized(this@CanvasArtworkPlaybackCache) {
            job.ensureActive()
            activeCalls[call] = job
        }
        try {
            call.execute().use { response ->
                if (existingBytes > 0L && response.code == 416) return
                if (!response.isSuccessful) throw IOException("Canvas request failed: HTTP ${response.code}")
                val append = existingBytes > 0L && response.code == 206
                if (existingBytes > 0L && !append) {
                    if (target.exists() && !target.delete()) throw IOException("Failed to restart canvas video download")
                }
                val body = response.body ?: throw IOException("Canvas response body is empty")
                val contentType =
                    body
                        .contentType()
                        ?.toString()
                        ?.lowercase(Locale.ROOT)
                        .orEmpty()
                if (
                    contentType.contains("mpegurl") ||
                    contentType.contains("m3u8") ||
                    contentType.startsWith("text/") ||
                    contentType.startsWith("image/") ||
                    contentType.contains("json")
                ) {
                    throw IOException("Canvas response is not a downloadable video: $contentType")
                }
                body.byteStream().use { input ->
                    FileOutputStream(target, append).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE_BYTES)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            CanvasNetworkAccess.checkTransfer(source)
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (target.length() + read > maxSizeBytes) throw IOException("Canvas exceeds the cache limit")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        } finally {
            synchronized(this@CanvasArtworkPlaybackCache) { activeCalls.remove(call) }
            call.cancel()
        }
    }

    private fun trimLocked(directory: File) {
        val activeFiles =
            map.values
                .flatMap { entry ->
                    listOfNotNull(entry.regularFileName, entry.verticalFileName)
                }.toSet()
        directory
            .listFiles()
            ?.filter { file -> file.isFile && file.name.endsWith(".mp4") && file.name !in activeFiles && file.name !in pendingFiles }
            ?.forEach { file -> runCatching { file.delete() } }
        trimToByteLimitLocked(directory)
    }

    private fun trimToByteLimitLocked(directory: File) {
        val limitBytes = maxSizeBytes
        if (limitBytes == Long.MAX_VALUE) return
        var totalBytes = map.values.sumOf { entry -> entry.byteSize(directory) }
        val iterator = map.entries.iterator()
        while (totalBytes > limitBytes && iterator.hasNext()) {
            val entry = iterator.next().value
            val entryBytes = entry.byteSize(directory)
            iterator.remove()
            listOfNotNull(entry.regularFileName, entry.verticalFileName).distinct().forEach { fileName ->
                runCatching { directory.resolve(fileName).delete() }
            }
            totalBytes -= entryBytes
        }
    }

    private fun clearFilesLocked() {
        val directory = cacheDirectory ?: return
        val files = directory.listFiles() ?: if (directory.exists()) {
            throw IOException("Cannot read the canvas cache directory")
        } else {
            return
        }
        var failed = false
        files.filter { it.isFile && (it.name.endsWith(".mp4") || it.name.endsWith(".part")) }
            .forEach { if (!it.delete() && it.exists()) failed = true }
        if (failed) throw IOException("Some canvas cache files could not be deleted")
    }

    private fun cancelCacheJobsLocked() {
        cacheJobs.values.forEach { job -> job.cancel() }
        activeCalls.keys.forEach(Call::cancel)
        cacheJobs.clear()
    }

    private fun canvasFileName(
        mediaId: String,
        variant: CanvasVideoVariant,
        url: String,
    ): String {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$mediaId|${variant.cacheKey}|$url".toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        return "${variant.cacheKey}-$digest.mp4"
    }

    private fun CanvasArtwork.downloadableRegularUrl(): String? = videoUrl.takeIfDownloadableVideo() ?: animated.takeIfDownloadableVideo()

    private fun CanvasArtwork.downloadableVerticalUrl(): String? =
        videoUrlVertical.takeIfDownloadableVideo() ?: animatedVertical.takeIfDownloadableVideo()

    private fun String?.takeIfDownloadableVideo(): String? =
        this
            ?.trim()
            ?.takeIf { value ->
                val normalized = value.lowercase(Locale.ROOT)
                value.isNotBlank() &&
                    !normalized.contains(".m3u8") &&
                    !normalized.contains("application/x-mpegurl") &&
                    (normalized.startsWith("http://") || normalized.startsWith("https://"))
            }

    @Serializable
    private data class CanvasCacheEntry(
        val mediaId: String,
        val artwork: CanvasArtwork,
        val regularFileName: String? = null,
        val verticalFileName: String? = null,
        val createdAtMs: Long,
        val lastAccessedAtMs: Long,
    ) {
        fun byteSize(directory: File): Long =
            listOfNotNull(regularFileName, verticalFileName)
                .distinct()
                .sumOf { fileName ->
                    directory
                        .resolve(fileName)
                        .takeIf { file -> file.isUsableFile() }
                        ?.length()
                        ?: 0L
                }

        fun toPlayableArtwork(
            directory: File,
            preferCachedOnly: Boolean,
        ): CanvasArtwork? {
            val regularUri =
                regularFileName
                    ?.let(directory::resolve)
                    ?.takeIf { file -> file.isUsableFile() && file.isValidCanvasVideo() }
                    ?.let { file -> Uri.fromFile(file).toString() }
            val verticalUri =
                verticalFileName
                    ?.let(directory::resolve)
                    ?.takeIf { file -> file.isUsableFile() && file.isValidCanvasVideo() }
                    ?.let { file -> Uri.fromFile(file).toString() }
            if (regularUri == null && verticalUri == null) return null
            return artwork.copy(
                static = if (preferCachedOnly) null else artwork.static,
                animated = if (preferCachedOnly) regularUri else artwork.animated.takeIfNotBlank() ?: regularUri,
                videoUrl = regularUri,
                animatedVertical =
                    if (preferCachedOnly) {
                        verticalUri
                    } else {
                        artwork.animatedVertical.takeIfNotBlank() ?: verticalUri
                    },
                videoUrlVertical = verticalUri,
            )
        }
    }

    private enum class CanvasVideoVariant(
        val cacheKey: String,
    ) {
        Regular(cacheKey = "regular"),
        Vertical(cacheKey = "vertical"),
    }
}

private fun okhttp3.HttpUrl.isYouTubeMediaHost(): Boolean {
    val normalizedHost = host.lowercase(Locale.ROOT)
    return normalizedHost.endsWith("googlevideo.com") ||
        normalizedHost.endsWith("googleusercontent.com") ||
        normalizedHost.endsWith("youtube.com") ||
        normalizedHost.endsWith("youtube-nocookie.com") ||
        normalizedHost.endsWith("ytimg.com")
}

private fun File.isUsableFile(): Boolean = isFile && length() > 0L

private fun File.isValidCanvasVideo(): Boolean {
    val extractor = MediaExtractor()
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return try {
        extractor.setDataSource(absolutePath)
        (0 until extractor.trackCount).any { index ->
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            mime.startsWith("video/") && codecList.findDecoderForFormat(format) != null
        }
    } catch (error: Throwable) {
        Timber.tag(CanvasCacheLogTag).w(error, "Failed to inspect cached canvas video")
        false
    } finally {
        extractor.release()
    }
}

private fun String?.takeIfNotBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun Int.toCanvasCacheLimitBytes(): Long =
    when {
        this < 0 -> {
            Long.MAX_VALUE
        }

        this == 0 -> {
            0L
        }

        else -> {
            toLong()
                .coerceAtMost(Long.MAX_VALUE / 1_024L / 1_024L)
                .coerceAtLeast(0L) * 1_024L * 1_024L
        }
    }

private const val CanvasDownloadUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
private const val CanvasCacheLogTag = "CanvasCache"
