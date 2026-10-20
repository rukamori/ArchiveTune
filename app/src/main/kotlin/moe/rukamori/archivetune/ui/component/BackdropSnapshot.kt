/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import moe.rukamori.archivetune.utils.ImageBlurUtils

// Longest side of a stored snapshot. Keeps read-back and stack blur cheap on the
// older (API < 31) devices this is for; the result is blurred anyway.
private const val SnapshotMaxSize = 480
private const val IdlePollMs = 100L

/**
 * Holds a downscaled, stack-blurred snapshot of backdrop content. Used to fake a
 * backdrop blur for the floating bars and the overflow menu below API 31, where
 * RenderEffect/Haze cannot blur live content.
 */
class BackdropSnapshot(
    internal val blurRadius: Float,
    internal val intervalMs: Long,
) {
    /** Gate for the capture loop; consumers toggle it (e.g. only while a menu is open). */
    val active = mutableStateOf(false)

    var image: ImageBitmap? by mutableStateOf(null)
        internal set
}

@Composable
fun rememberBackdropSnapshot(
    blurRadius: Float,
    captureIntervalMs: Long = 400L,
): BackdropSnapshot = remember { BackdropSnapshot(blurRadius, captureIntervalMs) }

/**
 * Records this node's content into a graphics layer and periodically reads it back,
 * downscales it and stack-blurs it into [holder]. No-op on API >= 31 so the modern
 * Haze/RenderEffect path keeps full fidelity and zero overhead.
 */
@Composable
fun Modifier.backdropSnapshotSource(holder: BackdropSnapshot): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return this

    val layer = rememberGraphicsLayer()
    LaunchedEffect(holder, layer) {
        while (isActive) {
            if (holder.active.value) {
                val status = holder.capture(layer)
                Log.d(TAG, "backdrop capture (${holder.blurRadius}px): $status")
                delay(holder.intervalMs)
            } else {
                delay(IdlePollMs)
            }
        }
    }
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}

private const val TAG = "BackdropSnap"

private suspend fun BackdropSnapshot.capture(layer: GraphicsLayer): String {
    val snapshot =
        try {
            layer.toImageBitmap()
        } catch (e: Exception) {
            return "toImageBitmap failed: $e"
        }
    if (snapshot.width == 0 || snapshot.height == 0) {
        return "empty bitmap ${snapshot.width}x${snapshot.height}"
    }
    return withContext(Dispatchers.Default) {
        val src = snapshot.asAndroidBitmap()
        // toImageBitmap() yields a HARDWARE bitmap, which forbids pixel access;
        // materialize a software copy before downscaling/stack-blurring.
        val software =
            if (src.config == Bitmap.Config.HARDWARE) {
                src.copy(Bitmap.Config.ARGB_8888, false)
                    ?: return@withContext "hardware->software copy failed"
            } else {
                src
            }
        val maxDim = max(software.width, software.height)
        val small =
            if (maxDim > SnapshotMaxSize) {
                val f = SnapshotMaxSize.toFloat() / maxDim
                Bitmap.createScaledBitmap(
                    software,
                    (software.width * f).toInt().coerceAtLeast(1),
                    (software.height * f).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                software
            }
        // Sample the downscaled capture so the log shows whether real content was captured.
        val sample = IntArray(4)
        val n = minOf(small.width, sample.size)
        small.getPixels(sample, 0, n, 0, 0, n, 1)
        val alphas = sample.joinToString(",") { ((it ushr 24) and 0xFF).toString() }
        val blurred = ImageBlurUtils.blur(small, blurRadius).asImageBitmap()
        image = blurred
        "ok ${small.width}x${small.height} (src=${src.config}) alpha[0..$n]=[$alphas] -> ${blurred.width}x${blurred.height}"
    }
}
