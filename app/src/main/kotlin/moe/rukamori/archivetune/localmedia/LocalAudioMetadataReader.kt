/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

data class LocalAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
)

class LocalAudioMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun read(
        uri: Uri,
        dateModifiedSeconds: Long,
        sizeBytes: Long,
    ): LocalAudioMetadata = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val retriever = MediaMetadataRetriever()
        var metadata = LocalAudioMetadata()
        try {
            retriever.setDataSource(context, uri)
            metadata = LocalAudioMetadata(
                title = retriever.readTag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.readTag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = retriever.readTag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.readTag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                trackNumber = parseNumber(retriever.readTag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                discNumber = parseNumber(retriever.readTag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                year = parseYear(retriever.readTag(MediaMetadataRetriever.METADATA_KEY_YEAR))
                    ?: parseYear(retriever.readTag(MediaMetadataRetriever.METADATA_KEY_DATE)),
                durationSeconds = retriever.readTag(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.div(1_000L)
                    ?.takeIf { it <= Int.MAX_VALUE }
                    ?.toInt(),
            )
            currentCoroutineContext().ensureActive()
            metadata = metadata.copy(thumbnailUrl = readArtwork(retriever, uri, dateModifiedSeconds, sizeBytes))
        } catch (error: IOException) {
            Timber.w(error, "Unable to read local audio metadata for %s", uri)
        } catch (error: RuntimeException) {
            if (error is SecurityException || error is kotlinx.coroutines.CancellationException) throw error
            Timber.w(error, "Unsupported or corrupt local audio metadata for %s", uri)
        } finally {
            try {
                retriever.release()
            } catch (error: IOException) {
                Timber.w(error, "Unable to release local audio metadata reader")
            } catch (error: RuntimeException) {
                if (error is SecurityException || error is kotlinx.coroutines.CancellationException) throw error
                Timber.w(error, "Unable to release local audio metadata reader")
            }
        }
        currentCoroutineContext().ensureActive()
        metadata
    }

    private fun MediaMetadataRetriever.readTag(key: Int): String? =
        try {
            extractMetadata(key)
                ?.trim { it.isWhitespace() || it == '\u0000' }
                ?.takeIf { it.isNotEmpty() && it.lowercase(Locale.ROOT) !in UnknownValues }
        } catch (error: RuntimeException) {
            if (error is SecurityException || error is kotlinx.coroutines.CancellationException) throw error
            Timber.w(error, "Unable to read local audio tag %d", key)
            null
        }

    private suspend fun readArtwork(
        retriever: MediaMetadataRetriever,
        uri: Uri,
        dateModifiedSeconds: Long,
        sizeBytes: Long,
    ): String? {
        val hash = UUID.nameUUIDFromBytes("$uri|$dateModifiedSeconds|$sizeBytes".toByteArray(StandardCharsets.UTF_8))
            .toString().replace("-", "")
        val directory = File(context.filesDir, "local_music_artwork")
        val file = File(directory, "${hash}_1024.jpg")
        if (!file.isFile || file.length() == 0L) {
            val bytes = retriever.embeddedPicture ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw IOException("Embedded artwork has invalid dimensions")
            }
            var sampleSize = 1
            val largestDimension = maxOf(options.outWidth, options.outHeight)
            while (largestDimension / sampleSize > MaxArtworkDimension) sampleSize *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            currentCoroutineContext().ensureActive()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Unable to decode embedded artwork")
            try {
                currentCoroutineContext().ensureActive()
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException("Unable to create local artwork directory")
                }
                val output = AtomicFile(file)
                val stream = output.startWrite()
                try {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                        throw IOException("Unable to encode embedded artwork")
                    }
                    currentCoroutineContext().ensureActive()
                    output.finishWrite(stream)
                } catch (error: Throwable) {
                    output.failWrite(stream)
                    throw error
                }
            } finally {
                bitmap.recycle()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file).toString()
    }

    private fun parseNumber(value: String?): Int? =
        value?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private fun parseYear(value: String?): Int? =
        value?.let { YearPattern.find(it)?.value?.toIntOrNull() }?.takeIf { it in 1..9999 }

    private companion object {
        const val MaxArtworkDimension = 1024
        val YearPattern = Regex("(?<!\\d)\\d{4}(?!\\d)")
        val UnknownValues = setOf("<unknown>", "unknown", "unknown artist", "unknown album", "unknown title")
    }
}
