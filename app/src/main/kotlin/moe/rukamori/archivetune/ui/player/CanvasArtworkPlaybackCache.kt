/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.utils.StreamClientUtils
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
    private const val PLAYBACK_MAX_VIDEO_EDGE_PX = 1_920
    private const val PLAYBACK_MAX_VIDEO_PIXEL_AREA = 1_920L * 1_920L

    private val map = LinkedHashMap<String, CanvasCacheEntry>(DEFAULT_MAX_SIZE_MEGABYTES, 0.75f, true)

    @Volatile private var maxSizeBytes = DEFAULT_MAX_SIZE_MEGABYTES.toLong() * CACHE_SIZE_BYTES_PER_MEGABYTE

    @Volatile private var cacheDirectory: File? = null

    @Volatile private var cacheFile: File? = null

    private val persistScope = CoroutineScope(Dispatchers.IO)
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

    fun init(context: Context) {
        val directory = StorageLocationRepository.cacheDirectory(context, StorageFolderKind.CANVAS_CACHE)
        cacheDirectory = directory
        cacheFile = directory.resolve(PERSIST_FILE)
        loadFromDisk()
    }

    @Synchronized
    fun get(mediaId: String): CanvasArtwork? {
        if (maxSizeBytes == 0L || mediaId.isBlank()) return null
        val entry = map[mediaId] ?: return null
        val directory = cacheDirectory ?: return null
        val playable = entry.toPlayableArtwork(directory)
        if (playable == null) {
            entry.deleteFiles(directory)
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

            val current = synchronized(this@CanvasArtworkPlaybackCache) { map[mediaId] }
            val regularDownloadUrl = artwork.downloadableRegularUrl()
            val verticalDownloadUrl = artwork.downloadableVerticalUrl()
            val regularCacheResult =
                cacheCanvasVideo(
                    directory = directory,
                    mediaId = mediaId,
                    variant = CanvasVideoVariant.Regular,
                    url = regularDownloadUrl,
                    currentFileName = current?.regularFileName,
                )
            val interimArtwork = artwork.withoutRejectedCanvasUrls(regularCacheResult.rejectedUrl)
            persistEntry(
                directory = directory,
                entry =
                    CanvasCacheEntry(
                        mediaId = mediaId,
                        artwork = interimArtwork,
                        regularFileName = regularCacheResult.fileName,
                        verticalFileName = current?.verticalFileName,
                        createdAtMs = current?.createdAtMs ?: System.currentTimeMillis(),
                        lastAccessedAtMs = System.currentTimeMillis(),
                    ),
            )
            val verticalCacheResult =
                cacheCanvasVideo(
                    directory = directory,
                    mediaId = mediaId,
                    variant = CanvasVideoVariant.Vertical,
                    url = verticalDownloadUrl,
                    currentFileName = current?.verticalFileName,
                )

            val playableArtwork =
                artwork.withoutRejectedCanvasUrls(
                    regularCacheResult.rejectedUrl,
                    verticalCacheResult.rejectedUrl,
                )
            val now = System.currentTimeMillis()
            val entry =
                CanvasCacheEntry(
                    mediaId = mediaId,
                    artwork = playableArtwork,
                    regularFileName = regularCacheResult.fileName,
                    verticalFileName = verticalCacheResult.fileName,
                    createdAtMs = current?.createdAtMs ?: now,
                    lastAccessedAtMs = now,
                )

            if (regularCacheResult.fileName == null && verticalCacheResult.fileName == null) {
                Timber.tag(CanvasCacheLogTag).d("Canvas artwork resolved without downloadable video for %s", mediaId)
            }

            persistEntry(directory = directory, entry = entry)

            entry.toPlayableArtwork(directory) ?: playableArtwork
        }

    @Synchronized
    fun byteSize(): Long {
        val directory = cacheDirectory ?: return 0L
        return map.values.sumOf { entry -> entry.byteSize(directory) }
    }

    @Synchronized
    fun clear() {
        clearFilesLocked()
        map.clear()
        schedulePersist()
    }

    fun clearAndPersist(): Boolean {
        synchronized(this) {
            clearFilesLocked()
            map.clear()
            persistJob?.cancel()
        }
        return writeToDisk()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSizeBytes = value.toCanvasCacheLimitBytes()
        val directory = cacheDirectory
        if (maxSizeBytes == 0L) {
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

    private fun persistEntry(
        directory: File,
        entry: CanvasCacheEntry,
    ) {
        synchronized(this@CanvasArtworkPlaybackCache) {
            map[entry.mediaId] = entry
            trimLocked(directory)
            schedulePersist()
        }
    }

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
        url: String?,
        currentFileName: String?,
    ): CanvasVideoCacheResult {
        currentFileName
            ?.let(directory::resolve)
            ?.takeIf { file -> file.isPlayableCanvasVideoFile() }
            ?.let { return CanvasVideoCacheResult(fileName = currentFileName) }

        currentFileName
            ?.let(directory::resolve)
            ?.takeIf { file -> file.exists() }
            ?.let { file -> runCatching { file.delete() } }

        if (url.isNullOrBlank()) return CanvasVideoCacheResult(fileName = null)

        val fileName = canvasFileName(mediaId, variant, url)
        val target = directory.resolve(fileName)
        if (target.exists()) {
            if (target.isPlayableCanvasVideoFile()) {
                return CanvasVideoCacheResult(fileName = fileName)
            }
            runCatching { target.delete() }
            return CanvasVideoCacheResult(fileName = null, rejectedUrl = url)
        }

        val partial = directory.resolve("$fileName.part")
        return try {
            downloadToFile(url = url, target = partial)
            if (partial.length() <= 0L) throw IOException("Downloaded empty canvas video")
            partial.requirePlayableCanvasVideo(url)
            if (target.exists() && !target.delete()) throw IOException("Failed to replace existing canvas video")
            if (!partial.renameTo(target)) throw IOException("Failed to commit canvas video")
            CanvasVideoCacheResult(fileName = fileName)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val rejectedUrl = (error as? UnsupportedCanvasVideoException)?.url
            Timber.w(error, "Failed to cache canvas video")
            runCatching { partial.delete() }
            val currentFallback =
                currentFileName
                    ?.takeIf { directory.resolve(it).isPlayableCanvasVideoFile() }
            CanvasVideoCacheResult(fileName = currentFallback, rejectedUrl = rejectedUrl)
        }
    }

    private suspend fun downloadToFile(
        url: String,
        target: File,
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
        existingBytes: Long,
    ) {
        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .header("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        val request = requestBuilder.build()
        val callClient = if (request.url.isYouTubeMediaHost()) streamClient else directClient
        callClient.newCall(request).execute().use { response ->
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
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
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
            ?.filter { file -> file.isFile && file.name.endsWith(".mp4") && file.name !in activeFiles }
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
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
            totalBytes -= entryBytes
        }
    }

    private fun clearFilesLocked() {
        val directory = cacheDirectory ?: return
        map.values.forEach { entry ->
            runCatching { entry.regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { entry.verticalFileName?.let { directory.resolve(it).delete() } }
        }
        directory
            .listFiles()
            ?.filter { file -> file.isFile && (file.name.endsWith(".mp4") || file.name.endsWith(".part")) }
            ?.forEach { file -> runCatching { file.delete() } }
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

    private fun File.requirePlayableCanvasVideo(url: String) {
        val size = readCanvasVideoSize()
            ?: throw UnsupportedCanvasVideoException(url, "Canvas video metadata is unreadable")
        if (!size.isWithinPlaybackBounds) {
            throw UnsupportedCanvasVideoException(
                url = url,
                reason = "Canvas video exceeds playback-safe bounds: ${size.width}x${size.height}",
            )
        }
    }

    private fun File.isPlayableCanvasVideoFile(): Boolean {
        if (!isUsableFile()) return false
        val size = readCanvasVideoSize() ?: return false
        return size.isWithinPlaybackBounds
    }

    private fun File.readCanvasVideoSize(): CanvasVideoSize? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (width <= 0 || height <= 0) return null
            if (rotation == 90 || rotation == 270) {
                CanvasVideoSize(width = height, height = width)
            } else {
                CanvasVideoSize(width = width, height = height)
            }
        } catch (error: Throwable) {
            Timber.w(error, "Failed to read canvas video metadata")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun CanvasArtwork.withoutRejectedCanvasUrls(vararg rejectedUrls: String?): CanvasArtwork {
        val rejected =
            rejectedUrls
                .mapNotNull { value -> value?.trim()?.takeIf { it.isNotBlank() } }
                .toSet()
        if (rejected.isEmpty()) return this

        fun String?.unlessRejected(): String? =
            this?.takeUnless { value -> value.trim() in rejected }

        return copy(
            animated = animated.unlessRejected(),
            videoUrl = videoUrl.unlessRejected(),
            animatedVertical = animatedVertical.unlessRejected(),
            videoUrlVertical = videoUrlVertical.unlessRejected(),
        )
    }

    private data class CanvasVideoSize(
        val width: Int,
        val height: Int,
    ) {
        val isWithinPlaybackBounds: Boolean
            get() =
                width <= PLAYBACK_MAX_VIDEO_EDGE_PX &&
                    height <= PLAYBACK_MAX_VIDEO_EDGE_PX &&
                    width.toLong() * height.toLong() <= PLAYBACK_MAX_VIDEO_PIXEL_AREA
    }

    private data class CanvasVideoCacheResult(
        val fileName: String?,
        val rejectedUrl: String? = null,
    )

    private class UnsupportedCanvasVideoException(
        val url: String,
        reason: String,
    ) : IOException(reason)

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
                .sumOf { fileName ->
                    directory
                        .resolve(fileName)
                        .takeIf { file -> file.isUsableFile() }
                        ?.length()
                        ?: 0L
                }

        fun deleteFiles(directory: File) {
            runCatching { regularFileName?.let { directory.resolve(it).delete() } }
            runCatching { verticalFileName?.let { directory.resolve(it).delete() } }
        }

        fun toPlayableArtwork(directory: File): CanvasArtwork? {
            val regularUri =
                regularFileName
                    ?.let(directory::resolve)
                    ?.takeIf { file -> file.isPlayableCanvasVideoFile() }
                    ?.let { file -> Uri.fromFile(file).toString() }
            val verticalUri =
                verticalFileName
                    ?.let(directory::resolve)
                    ?.takeIf { file -> file.isPlayableCanvasVideoFile() }
                    ?.let { file -> Uri.fromFile(file).toString() }
            if (regularUri == null && verticalUri == null) return null
            return artwork.copy(
                animated = regularUri,
                videoUrl = regularUri,
                animatedVertical = verticalUri,
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
