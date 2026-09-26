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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val NotificationArtworkSizePx = 1080
private const val LegacyMediaMetadataBitmapMaxSizeDp = 320

class CoilBitmapLoader(
    context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    private val applicationContext = context.applicationContext
    private val maximumArtworkDimensionPx = context.resolveMaximumArtworkDimensionPx()

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.Default) {
            require(data.isNotEmpty()) { "Empty image data" }
            val bitmap =
                checkNotNull(decodeSampledBitmap(data, maximumArtworkDimensionPx)) {
                    "Could not decode image data"
                }
            ensureActive()
            bitmap
                .scaleToNotificationArtwork(maximumArtworkDimensionPx)
                .toOwnedMediaSessionBitmap()
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            val request =
                ImageRequest
                    .Builder(applicationContext)
                    .data(uri)
                    .allowHardware(false)
                    .size(maximumArtworkDimensionPx, maximumArtworkDimensionPx)
                    .build()

            when (val result = applicationContext.imageLoader.execute(request)) {
                is SuccessResult -> withContext(Dispatchers.Default) {
                    ensureActive()
                    result.image
                        .toBitmap()
                        .scaleToNotificationArtwork(maximumArtworkDimensionPx)
                        .toOwnedMediaSessionBitmap()
                }

                is ErrorResult -> throw result.throwable
            }
        }
}

private fun decodeSampledBitmap(
    data: ByteArray,
    maximumDimensionPx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
    while (largestDimension / sampleSize / 2 >= maximumDimensionPx) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeByteArray(
        data,
        0,
        data.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

private fun Bitmap.scaleToNotificationArtwork(maximumDimensionPx: Int): Bitmap {
    check(!isRecycled) { "Artwork bitmap has been recycled" }
    check(width > 0 && height > 0) { "Invalid artwork dimensions" }
    if (width <= maximumDimensionPx && height <= maximumDimensionPx) return this

    val scale =
        minOf(
            maximumDimensionPx.toFloat() / width.toFloat(),
            maximumDimensionPx.toFloat() / height.toFloat(),
        )
    val targetWidth = (width * scale).roundToInt().coerceIn(1, maximumDimensionPx)
    val targetHeight = (height * scale).roundToInt().coerceIn(1, maximumDimensionPx)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun Bitmap.toOwnedMediaSessionBitmap(): Bitmap {
    check(!isRecycled) { "Artwork bitmap has been recycled" }
    return checkNotNull(copy(Bitmap.Config.ARGB_8888, false)) {
        "Could not copy artwork bitmap"
    }
}

@Suppress("DiscouragedApi")
private fun Context.resolveMaximumArtworkDimensionPx(): Int {
    val dimensionResourceId =
        resources.getIdentifier(
            "config_mediaMetadataBitmapMaxSize",
            "dimen",
            "android",
        )
    val frameworkLimitPx =
        if (dimensionResourceId != 0) {
            resources.getDimensionPixelSize(dimensionResourceId)
        } else {
            (LegacyMediaMetadataBitmapMaxSizeDp * resources.displayMetrics.density).roundToInt()
        }
    val pixelLimitResourceId =
        resources.getIdentifier(
            "config_maxBitmapSizePx",
            "integer",
            "android",
        )
    val pixelLimitPx =
        if (pixelLimitResourceId != 0) {
            resources.getInteger(pixelLimitResourceId).takeIf { it > 0 } ?: frameworkLimitPx
        } else {
            frameworkLimitPx
        }
    return minOf(NotificationArtworkSizePx, frameworkLimitPx, pixelLimitPx).coerceAtLeast(1)
}
