/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from Flamingo (yos.music.player) ui/widgets/effects/YosFloatingLightView.kt — GPLv3,
 * https://github.com/shouryadixitisverycool/Flamingo
 *
 * The color behind the artwork: the cover is center-cropped, downscaled,
 * saturated x3, darkened with two overlay fills, then stack-blurred (radius 25).
 * With the background effect (moving blur) enabled the backdrop runs the
 * KenBurnsView motion Flamingo ships (RandomTransitionGenerator rect math,
 * MIN_RECT_FACTOR 0.75, AccelerateDecelerateInterpolator) ported to Compose,
 * at this fork's tuned transition pace; the frame loop freezes while paused.
 * When leaving the lyrics page an identical copy is laid over it with a
 * 0x33000000 Overlay tint fading to alpha 0.618.
 */

package moe.rukamori.archivetune.ui.player.flamingo

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object FlamingoPage {
    const val Album = "Album"
    const val PlayingList = "PlayingList"
    const val Lyric = "Lyric"
}

private const val FlamingoKenBurnsMinRectFactor = 0.75f
private const val FlamingoKenBurnsMinTransitionMs = 3_500f
private const val FlamingoKenBurnsMaxTransitionMs = 11_000f
private const val FlamingoKenBurnsWriteMinIntervalMs = 15L

@Composable
fun FlamingoFloatingLight(
    modifier: Modifier,
    albumUrl: () -> String?,
    isPlaying: () -> Boolean,
    nowPage: () -> String,
    backgroundEffect: Boolean,
    motionSuspended: () -> Boolean = { false },
) {
    val context = LocalContext.current

    val processedBitmap by produceState<android.graphics.Bitmap?>(null, albumUrl(), backgroundEffect) {
        val url = albumUrl()
        if (url.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val source: Bitmap = result.image.toBitmap()
                    val compressed = FlamingoBitmapResolver.bitmapCompress(
                        source,
                        px = if (backgroundEffect) 96 else 32,
                    )
                    flamingoImageResolve(compressed)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    FlamingoWrapper {
        val lossEffect = remember("FlamingoFloatingLight_lossEffect") {
            androidx.compose.runtime.derivedStateOf {
                nowPage() != FlamingoPage.Lyric
            }
        }

        val useBackground = remember("FlamingoFloatingLight_useBackground") {
            androidx.compose.runtime.derivedStateOf {
                albumUrl() == null
            }
        }

        val bitmap = processedBitmap

        val baseBlackBackground = Modifier.drawWithCache {
            onDrawBehind {
                if (useBackground.value) {
                    drawRect(Color.Black)
                }
            }
        }

        if (bitmap != null) {
            if (backgroundEffect) {
                FlamingoKenBurnsImage(
                    bitmap = bitmap,
                    isPlaying = isPlaying,
                    motionSuspended = motionSuspended,
                    modifier = modifier.then(baseBlackBackground),
                )
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.then(baseBlackBackground),
                )
            }
        } else {
            Box(
                modifier = modifier.drawWithCache {
                    onDrawBehind {
                        drawRect(Color.Black)
                    }
                },
            )
        }

        FlamingoWrapper {
            val alpha = animateFloatAsState(
                targetValue = if (lossEffect.value) 0.618f else 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.Overlay),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = alpha.value
                        },
                )
            }
        }
    }
}

private class FlamingoKenBurnsRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float = left + width / 2f
    val centerY: Float = top + height / 2f
}

private class FlamingoKenBurnsTransition(
    val src: FlamingoKenBurnsRect,
    val dst: FlamingoKenBurnsRect,
    val durationMs: Float,
) {
    private val widthDiff = dst.width - src.width
    private val heightDiff = dst.height - src.height
    private val centerXDiff = dst.centerX - src.centerX
    private val centerYDiff = dst.centerY - src.centerY

    fun interpolate(elapsedMs: Float): FlamingoKenBurnsRect {
        val fraction = (elapsedMs / durationMs).coerceAtMost(1f)
        val progress = ((1f - cos(PI * fraction)) / 2f).toFloat()
        val width = src.width + progress * widthDiff
        val height = src.height + progress * heightDiff
        val centerX = src.centerX + progress * centerXDiff
        val centerY = src.centerY + progress * centerYDiff
        return FlamingoKenBurnsRect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            width = width,
            height = height,
        )
    }
}

private class FlamingoKenBurnsState {
    var drawableW: Float = 0f
    var drawableH: Float = 0f
    var transition: FlamingoKenBurnsTransition? = null
    var elapsedMs: Float = 0f
}

private fun flamingoKenBurnsDurationMs(random: Random): Float =
    FlamingoKenBurnsMinTransitionMs +
        random.nextFloat() * (FlamingoKenBurnsMaxTransitionMs - FlamingoKenBurnsMinTransitionMs)

private fun flamingoKenBurnsRandomRect(
    random: Random,
    drawableW: Float,
    drawableH: Float,
    viewportW: Float,
    viewportH: Float,
): FlamingoKenBurnsRect {
    val drawableRatio = drawableW / drawableH
    val viewportRatio = viewportW / viewportH
    val maxCropW: Float
    val maxCropH: Float
    if (drawableRatio > viewportRatio) {
        maxCropH = drawableH
        maxCropW = (drawableH / viewportH) * viewportW
    } else {
        maxCropW = drawableW
        maxCropH = (drawableW / viewportW) * viewportH
    }
    val randomFloat = (random.nextFloat() * 100f).roundToInt() / 100f
    val factor = FlamingoKenBurnsMinRectFactor + (1f - FlamingoKenBurnsMinRectFactor) * randomFloat
    val width = factor * maxCropW
    val height = factor * maxCropH
    val widthDiff = (drawableW - width).toInt()
    val heightDiff = (drawableH - height).toInt()
    val left = if (widthDiff > 0) random.nextInt(widthDiff).toFloat() else 0f
    val top = if (heightDiff > 0) random.nextInt(heightDiff).toFloat() else 0f
    return FlamingoKenBurnsRect(left, top, width, height)
}

@Composable
private fun FlamingoKenBurnsImage(
    bitmap: Bitmap,
    isPlaying: () -> Boolean,
    motionSuspended: () -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val kenBurns = remember { FlamingoKenBurnsState() }
    val random = remember { Random(System.currentTimeMillis()) }
    var currentRect by remember { mutableStateOf<FlamingoKenBurnsRect?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()

        LaunchedEffect(bitmap, viewportW, viewportH) {
            if (viewportW <= 0f || viewportH <= 0f) return@LaunchedEffect
            val drawableW = bitmap.width.toFloat()
            val drawableH = bitmap.height.toFloat()
            val dimsChanged = kenBurns.drawableW != drawableW || kenBurns.drawableH != drawableH
            val src = if (dimsChanged) {
                flamingoKenBurnsRandomRect(random, drawableW, drawableH, viewportW, viewportH)
            } else {
                kenBurns.transition?.dst
                    ?: flamingoKenBurnsRandomRect(random, drawableW, drawableH, viewportW, viewportH)
            }
            kenBurns.drawableW = drawableW
            kenBurns.drawableH = drawableH
            kenBurns.transition = FlamingoKenBurnsTransition(
                src = src,
                dst = flamingoKenBurnsRandomRect(random, drawableW, drawableH, viewportW, viewportH),
                durationMs = flamingoKenBurnsDurationMs(random),
            )
            kenBurns.elapsedMs = 0f
            currentRect = kenBurns.transition?.interpolate(0f)
            var lastFrameMs = 0L
            var lastWriteMs = 0L
            while (true) {
                if (!isPlaying() || motionSuspended()) {
                    snapshotFlow { isPlaying() && !motionSuspended() }.first { it }
                    lastFrameMs = 0L
                    lastWriteMs = 0L
                    continue
                }
                withFrameNanos { nanos ->
                    val nowMs = nanos / 1_000_000L
                    if (lastFrameMs != 0L) {
                        kenBurns.elapsedMs += nowMs - lastFrameMs
                        val transition = kenBurns.transition
                        if (transition != null) {
                            var transitionRolledOver = false
                            if (kenBurns.elapsedMs >= transition.durationMs) {
                                kenBurns.transition = FlamingoKenBurnsTransition(
                                    src = transition.dst,
                                    dst = flamingoKenBurnsRandomRect(
                                        random,
                                        kenBurns.drawableW,
                                        kenBurns.drawableH,
                                        viewportW,
                                        viewportH,
                                    ),
                                    durationMs = flamingoKenBurnsDurationMs(random),
                                )
                                kenBurns.elapsedMs = 0f
                                transitionRolledOver = true
                            }
                            if (transitionRolledOver ||
                                lastWriteMs == 0L ||
                                nowMs - lastWriteMs >= FlamingoKenBurnsWriteMinIntervalMs
                            ) {
                                currentRect = kenBurns.transition?.interpolate(kenBurns.elapsedMs)
                                lastWriteMs = nowMs
                            }
                        }
                    }
                    lastFrameMs = nowMs
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = currentRect ?: return@Canvas
            val drawableW = kenBurns.drawableW
            val drawableH = kenBurns.drawableH
            if (drawableW <= 0f || drawableH <= 0f || rect.width <= 0f || rect.height <= 0f) {
                return@Canvas
            }
            val widthScale = drawableW / rect.width
            val heightScale = drawableH / rect.height
            val rectToDrawableScale = min(widthScale, heightScale)
            val vpWidthScale = size.width / rect.width
            val vpHeightScale = size.height / rect.height
            val rectToViewportScale = min(vpWidthScale, vpHeightScale)
            val totalScale = rectToDrawableScale * rectToViewportScale
            val translX = totalScale * (drawableW / 2f - rect.left)
            val translY = totalScale * (drawableH / 2f - rect.top)
            withTransform({
                translate(translX, translY)
                scale(totalScale, totalScale, pivot = Offset.Zero)
                translate(-drawableW / 2f, -drawableH / 2f)
            }) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(bitmap.width, bitmap.height),
                )
            }
        }
    }
}
