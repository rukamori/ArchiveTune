/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.YouTube
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class PlaylistCoverRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        suspend fun getPlaylist(playlistId: String): PlaylistEntity? =
            withContext(Dispatchers.IO) {
                database.playlist(playlistId).first()?.playlist
            }

        suspend fun setLocalCover(
            playlist: PlaylistEntity,
            uri: Uri,
        ) = withContext(Dispatchers.IO) {
            persistReadPermission(uri)
            try {
                val previous = updateThumbnail(playlist.id, uri.toString())
                previous.thumbnailUrl
                    ?.takeIf { it != uri.toString() }
                    ?.let(Uri::parse)
                    ?.takeIf { it.scheme == "content" }
                    ?.let(::releaseReadPermission)
            } catch (throwable: Throwable) {
                releaseReadPermission(uri)
                throw throwable
            }
        }

        suspend fun setRemoteCover(
            playlist: PlaylistEntity,
            uri: Uri,
        ) = withContext(Dispatchers.IO) {
            persistReadPermission(uri)
            try {
                val image = decodeUploadImage(uri)
                val remoteCoverUrl =
                    YouTube
                        .uploadCustomPlaylistCover(
                            playlistId = requireNotNull(playlist.browseId),
                            image = image,
                        ).getOrThrow()
                val previous = updateThumbnail(playlist.id, remoteCoverUrl)
                previous.thumbnailUrl
                    ?.let(Uri::parse)
                    ?.takeIf { it.scheme == "content" }
                    ?.let(::releaseReadPermission)
            } finally {
                releaseReadPermission(uri)
            }
        }

        suspend fun removeLocalCover(playlist: PlaylistEntity) =
            withContext(Dispatchers.IO) {
                val previous = updateThumbnail(playlist.id, null)
                previous.thumbnailUrl
                    ?.let(Uri::parse)
                    ?.takeIf { it.scheme == "content" }
                    ?.let(::releaseReadPermission)
            }

        suspend fun removeRemoteCover(playlist: PlaylistEntity) =
            withContext(Dispatchers.IO) {
                val remoteCoverUrl =
                    YouTube
                        .removeCustomPlaylistCover(requireNotNull(playlist.browseId))
                        .getOrThrow()
                val previous = updateThumbnail(playlist.id, remoteCoverUrl)
                previous.thumbnailUrl
                    ?.let(Uri::parse)
                    ?.takeIf { it.scheme == "content" }
                    ?.let(::releaseReadPermission)
            }

        private suspend fun updateThumbnail(
            playlistId: String,
            thumbnailUrl: String?,
        ): PlaylistEntity {
            val current =
                requireNotNull(database.playlist(playlistId).first()?.playlist) {
                    "Playlist $playlistId no longer exists"
                }
            database.withTransaction {
                update(
                    current.copy(
                        thumbnailUrl = thumbnailUrl,
                        lastUpdateTime = LocalDateTime.now(),
                    ),
                )
            }
            return current
        }

        private fun persistReadPermission(uri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        private fun releaseReadPermission(uri: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        private fun decodeUploadImage(uri: Uri): ByteArray {
            val decoded =
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "Selected playlist cover is not a valid image" }

                    var sampleSize = 1
                    while (
                        options.outWidth.toLong() / sampleSize *
                        (options.outHeight.toLong() / sampleSize) > MAX_DECODE_PIXELS
                    ) {
                        sampleSize *= 2
                    }

                    options.inJustDecodeBounds = false
                    options.inSampleSize = sampleSize
                    val bitmap =
                        BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                            ?: throw IllegalArgumentException("Selected playlist cover could not be decoded")
                    rotateFromExif(bitmap, descriptor.fileDescriptor)
                } ?: throw IllegalArgumentException("Selected playlist cover could not be opened")

            val squareSize = min(decoded.width, decoded.height)
            val cropped =
                Bitmap.createBitmap(
                    decoded,
                    (decoded.width - squareSize) / 2,
                    (decoded.height - squareSize) / 2,
                    squareSize,
                    squareSize,
                )
            if (cropped !== decoded) decoded.recycle()

            val uploadBitmap =
                if (cropped.width > UPLOAD_DIMENSION_PX) {
                    Bitmap.createScaledBitmap(cropped, UPLOAD_DIMENSION_PX, UPLOAD_DIMENSION_PX, true).also {
                        if (it !== cropped) cropped.recycle()
                    }
                } else {
                    cropped
                }

            return try {
                compressWithinLimit(uploadBitmap)
            } finally {
                uploadBitmap.recycle()
            }
        }

        private fun rotateFromExif(
            bitmap: Bitmap,
            fileDescriptor: java.io.FileDescriptor,
        ): Bitmap {
            val rotation =
                when (readExifOrientation(fileDescriptor)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            if (rotation == 0f) return bitmap

            return Bitmap
                .createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postRotate(rotation) },
                    true,
                ).also { rotated ->
                    if (rotated !== bitmap) bitmap.recycle()
                }
        }

        private fun readExifOrientation(fileDescriptor: java.io.FileDescriptor): Int =
            runCatching {
                ExifInterface(fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        private fun compressWithinLimit(bitmap: Bitmap): ByteArray {
            val output = ByteArrayOutputStream()
            var quality = INITIAL_JPEG_QUALITY
            do {
                output.reset()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                quality -= JPEG_QUALITY_STEP
            } while (output.size() > MAX_UPLOAD_BYTES && quality >= MIN_JPEG_QUALITY)

            require(output.size() <= MAX_UPLOAD_BYTES) { "Selected playlist cover is too large" }
            return output.toByteArray()
        }

        private companion object {
            const val UPLOAD_DIMENSION_PX = 1080
            const val MAX_UPLOAD_BYTES = 2 * 1024 * 1024
            const val MAX_DECODE_PIXELS = 8_000_000L
            const val INITIAL_JPEG_QUALITY = 92
            const val MIN_JPEG_QUALITY = 60
            const val JPEG_QUALITY_STEP = 8
        }
    }
