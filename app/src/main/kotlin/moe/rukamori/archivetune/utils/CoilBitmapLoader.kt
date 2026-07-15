/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlin.math.roundToInt

internal const val NotificationArtworkSizePx = 1080

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                if (data.isEmpty()) {
                    throw IllegalArgumentException("Empty image data")
                }

                BitmapFactory.decodeByteArray(data, 0, data.size)?.also { bitmap ->
                    return@future bitmap
                }

                throw IllegalStateException("Could not decode image data")
            } catch (e: Exception) {
                reportException(e)
                return@future createBitmap(64, 64)
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            val attempts = 3
            for (attempt in 1..attempts) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(uri)
                            .allowHardware(false)
                            .size(NotificationArtworkSizePx, NotificationArtworkSizePx)
                            .build()

                    val result = context.imageLoader.execute(request)

                    when (result) {
                        is SuccessResult -> {
                            try {
                                val bitmap = result.image.toBitmap()
                                val scaled =
                                    if (bitmap.width <= 0 || bitmap.height <= 0) {
                                        null
                                    } else if (
                                        bitmap.width <= NotificationArtworkSizePx &&
                                        bitmap.height <= NotificationArtworkSizePx
                                    ) {
                                        bitmap
                                    } else {
                                        val scale =
                                            minOf(
                                                NotificationArtworkSizePx.toFloat() / bitmap.width.toFloat(),
                                                NotificationArtworkSizePx.toFloat() / bitmap.height.toFloat(),
                                            )
                                        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                                        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                                        Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                                    }

                                val mediaSessionBitmap = scaled?.toOwnedMediaSessionBitmap()
                                if (mediaSessionBitmap == null) {
                                    return@future createBitmap(64, 64)
                                }

                                return@future mediaSessionBitmap
                            } catch (e: Exception) {
                                reportException(e)
                            }
                        }

                        is ErrorResult -> {
                            result.throwable?.let { reportException(it) }
                        }
                    }
                } catch (e: Exception) {
                    reportException(e)
                }

                if (attempt < attempts) {
                    delay(250L * attempt)
                    continue
                }
            }
            createBitmap(64, 64)
        }
}

private fun Bitmap.toOwnedMediaSessionBitmap(): Bitmap? {
    if (isRecycled) return null
    return copy(Bitmap.Config.ARGB_8888, false)?.takeUnless(Bitmap::isRecycled)
}
