/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.downloads

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import coil3.map.Mapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DownloadedArtworkRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val updateMutex = Mutex()
        private val snapshotLock = Any()

        @Volatile private var cachedSnapshot: ArtworkSnapshot? = null

        private val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        fun coilMapper(): Mapper<String, Any> =
            Mapper { data, _ ->
                resolveLocalFile(data)
            }

        suspend fun cache(
            mediaId: String,
            sourceUrls: Collection<String?>,
        ): Boolean =
            withContext(Dispatchers.IO) {
                if (mediaId.isBlank()) return@withContext false
                val downloadCandidates =
                    sourceUrls
                        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                        .distinct()
                if (downloadCandidates.isEmpty()) return@withContext false

                updateMutex.withLock {
                    val directory = artworkDirectory().apply { mkdirs() }
                    val currentSnapshot = snapshot()
                    val currentEntry = currentSnapshot.entriesByMediaId[mediaId]
                    val destination = directory.resolve(currentEntry?.fileName ?: mediaId.toArtworkFileName())
                    val wasAlreadyCached = destination.isUsableArtworkFile()
                    val aliases =
                        buildSet {
                            currentEntry?.sourceUrls.orEmpty().forEach(::add)
                            downloadCandidates.mapNotNullTo(this, ::normalizeArtworkUrl)
                            normalizeArtworkUrl(youtubeThumbnailAlias(mediaId))?.let(::add)
                        }

                    if (!wasAlreadyCached) {
                        val downloaded =
                            downloadCandidates.any { sourceUrl ->
                                downloadArtwork(sourceUrl, destination)
                            }
                        if (!downloaded) return@withLock false
                    }

                    val updatedEntry =
                        DownloadedArtworkEntry(
                            mediaId = mediaId,
                            fileName = destination.name,
                            sourceUrls = aliases.toList(),
                        )
                    val updatedEntries = currentSnapshot.entriesByMediaId + (mediaId to updatedEntry)
                    updateSnapshot(updatedEntries)
                    if (!persistIndex(updatedEntries.values)) {
                        if (!wasAlreadyCached) destination.delete()
                        updateSnapshot(currentSnapshot.entriesByMediaId)
                        return@withLock false
                    }
                    true
                }
            }

        suspend fun retainForDownloads(mediaIds: Set<String>) =
            withContext(Dispatchers.IO) {
                updateMutex.withLock {
                    val currentSnapshot = snapshot()
                    val removedEntries = currentSnapshot.entriesByMediaId.filterKeys { it !in mediaIds }
                    if (removedEntries.isEmpty()) return@withLock

                    val directory = artworkDirectory()
                    removedEntries.values.forEach { entry ->
                        directory.resolve(entry.fileName).delete()
                    }
                    val retainedEntries = currentSnapshot.entriesByMediaId - removedEntries.keys
                    updateSnapshot(retainedEntries)
                    persistIndex(retainedEntries.values)
                }
            }

        suspend fun remove(mediaId: String) =
            withContext(Dispatchers.IO) {
                updateMutex.withLock {
                    val currentSnapshot = snapshot()
                    val entry = currentSnapshot.entriesByMediaId[mediaId] ?: return@withLock
                    artworkDirectory().resolve(entry.fileName).delete()
                    val updatedEntries = currentSnapshot.entriesByMediaId - mediaId
                    updateSnapshot(updatedEntries)
                    persistIndex(updatedEntries.values)
                }
            }

        fun invalidateMemoryIndex() {
            synchronized(snapshotLock) {
                cachedSnapshot = null
            }
        }

        private fun resolveLocalFile(sourceUrl: String): File? {
            val normalizedUrl = normalizeArtworkUrl(sourceUrl) ?: return null
            val fileName = snapshot().fileNameBySourceUrl[normalizedUrl] ?: return null
            return artworkDirectory().resolve(fileName).takeIf { it.isUsableArtworkFile() }
        }

        private fun snapshot(): ArtworkSnapshot {
            cachedSnapshot?.let { return it }
            return synchronized(snapshotLock) {
                cachedSnapshot ?: loadSnapshot().also { cachedSnapshot = it }
            }
        }

        private fun loadSnapshot(): ArtworkSnapshot {
            val indexFile = indexFile()
            if (!indexFile.exists()) return ArtworkSnapshot.EMPTY
            return try {
                val index = json.decodeFromString<DownloadedArtworkIndex>(indexFile.readText())
                val directory = artworkDirectory()
                index.entries
                    .filter { entry ->
                        entry.mediaId.isNotBlank() &&
                            entry.fileName.isNotBlank() &&
                            directory.resolve(entry.fileName).isUsableArtworkFile()
                    }.associateBy(DownloadedArtworkEntry::mediaId)
                    .toSnapshot()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Timber.w(exception, "Failed to load downloaded artwork index")
                ArtworkSnapshot.EMPTY
            }
        }

        private fun updateSnapshot(entriesByMediaId: Map<String, DownloadedArtworkEntry>) {
            synchronized(snapshotLock) {
                cachedSnapshot = entriesByMediaId.toSnapshot()
            }
        }

        private fun Map<String, DownloadedArtworkEntry>.toSnapshot(): ArtworkSnapshot =
            ArtworkSnapshot(
                entriesByMediaId = this,
                fileNameBySourceUrl =
                    values
                        .flatMap { entry ->
                            entry.sourceUrls.mapNotNull { sourceUrl ->
                                normalizeArtworkUrl(sourceUrl)?.let { it to entry.fileName }
                            }
                        }.toMap(),
            )

        private fun persistIndex(entries: Collection<DownloadedArtworkEntry>): Boolean {
            val atomicFile = AtomicFile(indexFile())
            var output: FileOutputStream? = null
            return try {
                atomicFile.baseFile.parentFile?.mkdirs()
                output = atomicFile.startWrite()
                output.write(json.encodeToString(DownloadedArtworkIndex(entries.toList())).toByteArray())
                atomicFile.finishWrite(output)
                output = null
                true
            } catch (exception: IOException) {
                output?.let(atomicFile::failWrite)
                Timber.w(exception, "Failed to persist downloaded artwork index")
                false
            }
        }

        private suspend fun downloadArtwork(
            sourceUrl: String,
            destination: File,
        ): Boolean =
            suspendCancellableCoroutine { continuation ->
                val request =
                    try {
                        Request
                            .Builder()
                            .url(sourceUrl)
                            .header("User-Agent", ARTWORK_USER_AGENT)
                            .get()
                            .build()
                    } catch (exception: IllegalArgumentException) {
                        Timber.w(exception, "Invalid downloaded artwork URL")
                        continuation.resume(false)
                        return@suspendCancellableCoroutine
                    }
                val call = httpClient.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            exception: IOException,
                        ) {
                            if (!call.isCanceled()) {
                                Timber.w(exception, "Failed to download artwork for offline playback")
                            }
                            if (continuation.isActive) continuation.resume(false)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            val result =
                                try {
                                    response.use { writeArtworkResponse(it, destination) }
                                } catch (exception: IOException) {
                                    Timber.w(exception, "Failed to store downloaded artwork")
                                    false
                                }
                            if (continuation.isActive) {
                                continuation.resume(result)
                            } else if (result) {
                                destination.delete()
                            }
                        }
                    },
                )
            }

        private fun writeArtworkResponse(
            response: Response,
            destination: File,
        ): Boolean {
            if (!response.isSuccessful) {
                Timber.w("Downloaded artwork request failed with HTTP %d", response.code)
                return false
            }
            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > MAX_ARTWORK_BYTES) {
                Timber.w("Downloaded artwork exceeded the size limit")
                return false
            }
            val contentType = body.contentType()
            if (contentType != null && contentType.type != "image") {
                Timber.w("Downloaded artwork response was not an image")
                return false
            }

            destination.parentFile?.mkdirs()
            val atomicFile = AtomicFile(destination)
            var output: FileOutputStream? = null
            try {
                output = atomicFile.startWrite()
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
                    var totalBytes = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        totalBytes += bytesRead
                        if (totalBytes > MAX_ARTWORK_BYTES) {
                            throw ArtworkSizeLimitExceededException()
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
                atomicFile.finishWrite(output)
                output = null
            } catch (exception: ArtworkSizeLimitExceededException) {
                output?.let(atomicFile::failWrite)
                Timber.w("Downloaded artwork exceeded the size limit")
                return false
            } catch (exception: IOException) {
                output?.let(atomicFile::failWrite)
                throw exception
            }

            if (!destination.isDecodableImage()) {
                destination.delete()
                Timber.w("Downloaded artwork could not be decoded")
                return false
            }
            return true
        }

        private fun artworkDirectory(): File =
            StorageLocationRepository.cacheDirectory(context, StorageFolderKind.ARTWORK_CACHE)

        private fun indexFile(): File = artworkDirectory().resolve(INDEX_FILE_NAME)

        private fun String.toArtworkFileName(): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) } + ARTWORK_FILE_SUFFIX
        }

        private fun File.isUsableArtworkFile(): Boolean = isFile && length() > 0L

        private fun File.isDecodableImage(): Boolean {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            return options.outWidth > 0 && options.outHeight > 0
        }

        private fun normalizeArtworkUrl(sourceUrl: String): String? {
            val httpUrl = sourceUrl.trim().toHttpUrlOrNull() ?: return null
            val host = httpUrl.host.lowercase(Locale.US)
            if (httpUrl.scheme != "http" && httpUrl.scheme != "https") return null

            if (host.endsWith("ytimg.com")) {
                val pathSegments = httpUrl.pathSegments
                val videoSegmentIndex = pathSegments.indexOf("vi")
                if (videoSegmentIndex >= 0 && videoSegmentIndex + 1 < pathSegments.size) {
                    return "${httpUrl.scheme}://$host/vi/${pathSegments[videoSegmentIndex + 1]}"
                }
            }

            val withoutQuery =
                httpUrl
                    .newBuilder()
                    .query(null)
                    .fragment(null)
                    .build()
                    .toString()
            return if (host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com")) {
                withoutQuery.replace(GOOGLE_IMAGE_SIZE_SUFFIX_REGEX, "")
            } else {
                withoutQuery
            }
        }

        private fun youtubeThumbnailAlias(mediaId: String): String =
            "https://i.ytimg.com/vi/$mediaId/hqdefault.jpg"

        private companion object {
            const val INDEX_FILE_NAME = "downloaded_artwork_index.json"
            const val ARTWORK_FILE_SUFFIX = ".artwork"
            const val ARTWORK_USER_AGENT = "ArchiveTune Android"
            const val NETWORK_TIMEOUT_SECONDS = 30L
            const val MAX_ARTWORK_BYTES = 10L * 1024L * 1024L
            const val COPY_BUFFER_SIZE_BYTES = 32 * 1024
            val GOOGLE_IMAGE_SIZE_SUFFIX_REGEX = Regex("=(?:w\\d+-h\\d+|s\\d+)[^/?]*$")
        }
    }

@Serializable
private data class DownloadedArtworkIndex(
    val entries: List<DownloadedArtworkEntry> = emptyList(),
)

@Serializable
private data class DownloadedArtworkEntry(
    val mediaId: String,
    val fileName: String,
    val sourceUrls: List<String>,
)

private data class ArtworkSnapshot(
    val entriesByMediaId: Map<String, DownloadedArtworkEntry>,
    val fileNameBySourceUrl: Map<String, String>,
) {
    companion object {
        val EMPTY = ArtworkSnapshot(emptyMap(), emptyMap())
    }
}

private class ArtworkSizeLimitExceededException : IOException()
