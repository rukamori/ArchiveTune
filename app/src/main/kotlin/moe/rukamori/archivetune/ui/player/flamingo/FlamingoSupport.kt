/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Player design ported from Flamingo (yos.music.player) by shouryadixit — GPLv3,
 * https://github.com/shouryadixitisverycool/Flamingo
 * All layout constants, drawing code and visual behaviour below originate from
 * Flamingo's ui/widgets/basic + ui/widgets/effects + ui/theme sources.
 */

package moe.rukamori.archivetune.ui.player.flamingo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.applyCanvas
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.utils.PreferenceStore
import kotlin.math.sqrt

@Composable
@NonRestartableComposable
fun FlamingoWrapper(content: @Composable () -> Unit) =
    content()

@Composable
fun Modifier.overlayEffect(): Modifier = this.drawWithCache {
    val overlayPaint = androidx.compose.ui.graphics.Paint().apply {
        blendMode = BlendMode.Plus
    }
    val rect = Rect(0f, 0f, size.width, size.height)

    onDrawWithContent {
        val canvas = this.drawContext.canvas

        canvas.saveLayer(rect, overlayPaint)

        drawContent()

        canvas.restore()
    }
}

enum class ShadowType(val blur: Dp, val offsetY: Float, val offsetX: Float, val areaWeight: Float) {
    Large(24.dp, 0.08f, 0f, 0.94f),
    Medium(28.dp, 0.08f, 0f, 0.94f),
    Small(24.dp, 0.11f, 0f, 0.94f),
}

@Composable
fun Modifier.dropShadow(
    shape: Shape,
    shadowAlpha: Float,
    shadowType: ShadowType,
    overlay: Boolean = false,
): Modifier = if (shadowAlpha == 0f) this else this.drawWithCache {
    val color = Color(0xFF000000).copy(alpha = shadowAlpha)

    val height = size.height
    val width = size.width

    val shadowSize = Size(width * shadowType.areaWeight, height * shadowType.areaWeight)
    val shadowOutline = shape.createOutline(shadowSize, layoutDirection, this)

    val paint = androidx.compose.ui.graphics.Paint().apply {
        this.color = color
        if (overlay) {
            this.blendMode = BlendMode.Overlay
        }
    }

    val blurPx = shadowType.blur.toPx()

    val offsetX = shadowType.offsetX * width + (width * (1f - shadowType.areaWeight)) / 2
    val offsetY = shadowType.offsetY * height + (height * (1f - shadowType.areaWeight)) / 2

    onDrawBehind {
        if (blurPx > 0) {
            paint.asFrameworkPaint().apply {
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
        }

        val canvas = this.drawContext.canvas
        canvas.save()
        canvas.translate(
            offsetX,
            offsetY,
        )
        canvas.drawOutline(shadowOutline, paint)
        canvas.restore()
    }
}

fun FlamingoSmoothCornerShape(
    roundSize: Dp,
    n: Float = 0.5f,
    mSize: Size? = null,
) = FlamingoSmoothCornerShape(CornerSize(roundSize), n, mSize)

fun FlamingoSmoothCornerShape(
    corner: CornerSize,
    n: Float = 0.5f,
    mSize: Size? = null,
) = FlamingoSmoothCornerShape(corner, corner, corner, corner, n, mSize)

fun FlamingoSmoothCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    n: Float = 0.5f,
    mSize: Size? = null,
) = FlamingoSmoothCornerShape(
    topStart = CornerSize(topStart),
    topEnd = CornerSize(topEnd),
    bottomEnd = CornerSize(bottomEnd),
    bottomStart = CornerSize(bottomStart),
    n = n,
    mSize = mSize,
)

@Stable
class FlamingoSmoothCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val n: Float = 0.5f,
    private val mSize: Size? = null,
) : CornerBasedShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomStart = bottomStart,
    bottomEnd = bottomEnd,
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ) = if (topStart + topEnd + bottomEnd + bottomStart == 0f) Outline.Rectangle(size.toRect())
    else Outline.Generic(
        Path().apply {
            val width = mSize?.width ?: size.width
            val height = mSize?.height ?: size.height
            val (aa1, bb1, cc1, dd1, ee1, ff1, gg1) = calcCoordinates(topStart)
            val (aa2, bb2, cc2, dd2, ee2, ff2, gg2) = calcCoordinates(bottomStart)
            val (aa3, bb3, cc3, dd3, ee3, ff3, gg3) = calcCoordinates(bottomEnd)
            val (aa4, bb4, cc4, dd4, ee4, ff4, gg4) = calcCoordinates(topEnd)

            moveTo(aa1, 0f)
            if (topStart != 0f) {
                cubicTo(bb1, 0f, cc1, 0f, dd1, ee1)
                if (n != 1f) {
                    cubicTo(gg1, ff1, ff1, gg1, ee1, dd1)
                }
                cubicTo(0f, cc1, 0f, bb1, 0f, aa1)
            }
            lineTo(0f, height - aa2)
            if (bottomStart != 0f) {
                cubicTo(0f, height - bb2, 0f, height - cc2, ee2, height - dd2)
                if (n != 1f) {
                    cubicTo(ff2, height - gg2, gg2, height - ff2, dd2, height - ee2)
                }
                cubicTo(cc2, height, bb2, height, aa2, height)
            }
            lineTo(width - aa3, height)
            if (bottomEnd != 0f) {
                cubicTo(width - bb3, height, width - cc3, height, width - dd3, height - ee3)
                if (n != 1f) {
                    cubicTo(
                        width - gg3,
                        height - ff3,
                        width - ff3,
                        height - gg3,
                        width - ee3,
                        height - dd3,
                    )
                }
                cubicTo(width, height - cc3, width, height - bb3, width, height - aa3)
            }
            lineTo(width, aa4)
            if (topEnd != 0f) {
                cubicTo(width, bb4, width, cc4, width - ee4, dd4)
                if (n != 1f) {
                    cubicTo(width - ff4, gg4, width - gg4, ff4, width - dd4, ee4)
                }
                cubicTo(width - cc4, 0f, width - bb4, 0f, width - aa4, 0f)
            }
            lineTo(aa1, 0f)
            close()
        },
    )

    private fun calcCoordinates(rad: Float): FloatArray {
        val x = n * rad * 2f / 3f
        val sq = sqrt(1f + n * n)
        val p1 = sq * x
        val p2 = n * x / sq
        val p3 = p2 * n
        val p4 = p2 + rad / sq
        val p5 = p4 * (1f - n)
        val p7 = p5 + p1 * 4f
        val p8 = p5 + p1 * 2f
        val p9 = p5 + p1
        val p10 = p5 + p3
        val dx = rad / sq * (1f - n)
        val d = sqrt(rad * rad - dx * dx / 2f)
        val handle = (rad - d) / dx * sqrt(2f) * 4f / 3f
        val p11 = rad / sq * n
        val p12 = rad / sq
        val p13 = p2 + p11 * handle
        val p14 = p10 - p12 * handle
        return floatArrayOf(p7, p8, p9, p10, p2, p13, p14)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ) = FlamingoSmoothCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
    )
}

private operator fun FloatArray.component6(): Float = this[5]
private operator fun FloatArray.component7(): Float = this[6]

@Stable
enum class ImageQuality {
    RAW, LOW, HIGH
}

private fun getSizeFromQuality(quality: ImageQuality): Int {
    return when (quality) {
        ImageQuality.RAW -> 0
        ImageQuality.LOW -> 128
        ImageQuality.HIGH -> 400
    }
}

@Composable
private fun flamingoImageRequest(
    url: Any?,
    imageQuality: ImageQuality,
): ImageRequest? {
    if (url == null) return null
    val context = LocalContext.current
    return remember(url, imageQuality) {
        ImageRequest
            .Builder(context)
            .data(url)
            .crossfade(true)
            .allowHardware(true)
            .apply {
                if (imageQuality != ImageQuality.RAW) {
                    val px = getSizeFromQuality(imageQuality)
                    size(px, px)
                }
            }
            .build()
    }
}

@Composable
fun ShadowImageWithCache(
    dataLambda: () -> Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shadowAlpha: Float = 0.23f,
    shadowType: ShadowType = ShadowType.Large,
    shadowOverlay: Boolean = false,
    cornerRadius: Dp = 8.dp,
    imageQuality: ImageQuality,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
) = FlamingoWrapper {
    val shape = FlamingoSmoothCornerShape(cornerRadius)
    val url = dataLambda()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .dropShadow(shape, shadowAlpha, shadowType, shadowOverlay)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
                this.shape = shape
            },
    ) {
        val request = flamingoImageRequest(url, imageQuality)
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription.toString(),
                contentScale = ContentScale.Crop,

                modifier = Modifier
                    .fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
            )
        }

        overlayContent?.invoke(this)
    }
}

@Stable
object FlamingoHaptics {
    private fun hapticsEnabled(): Boolean =
        PreferenceStore.get(EnableHapticFeedbackKey) ?: true

    private fun vibrator(context: Context): Vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun click(context: Context) {
        if (!hapticsEnabled()) return
        val vibrator = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(
                    VibrationEffect.EFFECT_CLICK,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    fun longClick(context: Context) {
        if (!hapticsEnabled()) return
        val vibrator = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(
                    VibrationEffect.EFFECT_HEAVY_CLICK,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    fun doubleClick(context: Context) {
        if (!hapticsEnabled()) return
        val vibrator = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(
                    VibrationEffect.EFFECT_DOUBLE_CLICK,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
}

@Stable
object FlamingoBitmapResolver {
    fun bitmapCompress(bitmap: Bitmap, px: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var compressedBitmap = bitmap

        val size = minOf(originalWidth, originalHeight)
        val xOffset = (originalWidth - size) / 2
        val yOffset = (originalHeight - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        if (size > px) {
            val scaleFactor = size / px
            val scaledSize = size / scaleFactor
            compressedBitmap = Bitmap.createScaledBitmap(squareBitmap, scaledSize, scaledSize, true)
        }

        val config = Bitmap.Config.RGB_565
        return compressedBitmap.copy(config, false)
    }

    fun blurBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) {
            return bitmap
        }

        val mutableBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val bitmapWidth = mutableBitmap.width
        val bitmapHeight = mutableBitmap.height
        val bitmapSize = bitmapWidth * bitmapHeight
        val pixelArray = IntArray(bitmapSize)
        val redArray = IntArray(bitmapSize)
        val greenArray = IntArray(bitmapSize)
        val blueArray = IntArray(bitmapSize)
        val minimumCoordinateArray = IntArray(maxOf(bitmapWidth, bitmapHeight))
        val division = radius + radius + 1
        val divisionSum = (division + 1) shr 1
        val divisionLookup = IntArray(256 * divisionSum * divisionSum)
        val stack = Array(division) {
            IntArray(3)
        }

        for (divisionIndex in divisionLookup.indices) {
            divisionLookup[divisionIndex] = divisionIndex / (divisionSum * divisionSum)
        }

        mutableBitmap.getPixels(pixelArray, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)

        var yCoordinate = 0

        while (yCoordinate < bitmapHeight) {
            var incomingRed = 0
            var incomingGreen = 0
            var incomingBlue = 0
            var outgoingRed = 0
            var outgoingGreen = 0
            var outgoingBlue = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var stackIndex = 0

            while (stackIndex < division) {
                val xCoordinate = minOf(bitmapWidth - 1, maxOf(stackIndex - radius, 0))
                val pixel = pixelArray[yCoordinate * bitmapWidth + xCoordinate]
                val stackColor = stack[stackIndex]
                stackColor[0] = pixel shr 16 and 0xFF
                stackColor[1] = pixel shr 8 and 0xFF
                stackColor[2] = pixel and 0xFF
                val stackWeight = radius + 1 - kotlin.math.abs(stackIndex - radius)

                redSum += stackColor[0] * stackWeight
                greenSum += stackColor[1] * stackWeight
                blueSum += stackColor[2] * stackWeight

                if (stackIndex <= radius) {
                    outgoingRed += stackColor[0]
                    outgoingGreen += stackColor[1]
                    outgoingBlue += stackColor[2]
                } else {
                    incomingRed += stackColor[0]
                    incomingGreen += stackColor[1]
                    incomingBlue += stackColor[2]
                }
                stackIndex++
            }

            var xCoordinate = 0
            var currentStackIndex = radius

            while (xCoordinate < bitmapWidth) {
                redArray[yCoordinate * bitmapWidth + xCoordinate] = divisionLookup[redSum]
                greenArray[yCoordinate * bitmapWidth + xCoordinate] = divisionLookup[greenSum]
                blueArray[yCoordinate * bitmapWidth + xCoordinate] = divisionLookup[blueSum]

                redSum -= outgoingRed
                greenSum -= outgoingGreen
                blueSum -= outgoingBlue

                var stackStart = currentStackIndex - radius + division
                while (stackStart >= division) {
                    stackStart -= division
                }
                val outgoingStack = stack[stackStart]
                outgoingRed -= outgoingStack[0]
                outgoingGreen -= outgoingStack[1]
                outgoingBlue -= outgoingStack[2]

                if (yCoordinate == 0) {
                    minimumCoordinateArray[xCoordinate] = minOf(xCoordinate + radius + 1, bitmapWidth - 1)
                }

                val nextPixel = pixelArray[yCoordinate * bitmapWidth + minimumCoordinateArray[xCoordinate]]
                outgoingStack[0] = nextPixel shr 16 and 0xFF
                outgoingStack[1] = nextPixel shr 8 and 0xFF
                outgoingStack[2] = nextPixel and 0xFF

                incomingRed += outgoingStack[0]
                incomingGreen += outgoingStack[1]
                incomingBlue += outgoingStack[2]

                redSum += incomingRed
                greenSum += incomingGreen
                blueSum += incomingBlue

                currentStackIndex++
                if (currentStackIndex >= division) {
                    currentStackIndex = 0
                }
                val incomingStack = stack[currentStackIndex]
                outgoingRed += incomingStack[0]
                outgoingGreen += incomingStack[1]
                outgoingBlue += incomingStack[2]
                incomingRed -= incomingStack[0]
                incomingGreen -= incomingStack[1]
                incomingBlue -= incomingStack[2]
                xCoordinate++
            }
            yCoordinate++
        }

        var xCoordinate2 = 0

        while (xCoordinate2 < bitmapWidth) {
            var incomingRed = 0
            var incomingGreen = 0
            var incomingBlue = 0
            var outgoingRed = 0
            var outgoingGreen = 0
            var outgoingBlue = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var stackIndex = 0

            while (stackIndex < division) {
                val yOffset = minOf(bitmapHeight - 1, maxOf(stackIndex - radius, 0)) * bitmapWidth
                val stackColor = stack[stackIndex]
                stackColor[0] = redArray[yOffset + xCoordinate2]
                stackColor[1] = greenArray[yOffset + xCoordinate2]
                stackColor[2] = blueArray[yOffset + xCoordinate2]
                val stackWeight = radius + 1 - kotlin.math.abs(stackIndex - radius)

                redSum += redArray[yOffset + xCoordinate2] * stackWeight
                greenSum += greenArray[yOffset + xCoordinate2] * stackWeight
                blueSum += blueArray[yOffset + xCoordinate2] * stackWeight

                if (stackIndex <= radius) {
                    outgoingRed += stackColor[0]
                    outgoingGreen += stackColor[1]
                    outgoingBlue += stackColor[2]
                } else {
                    incomingRed += stackColor[0]
                    incomingGreen += stackColor[1]
                    incomingBlue += stackColor[2]
                }
                stackIndex++
            }

            var yCoordinate2 = 0
            var currentStackIndex = radius

            while (yCoordinate2 < bitmapHeight) {
                val originalPixel = pixelArray[yCoordinate2 * bitmapWidth + xCoordinate2]
                pixelArray[yCoordinate2 * bitmapWidth + xCoordinate2] = originalPixel and -0x1000000 or
                    (divisionLookup[redSum] shl 16) or
                    (divisionLookup[greenSum] shl 8) or
                    divisionLookup[blueSum]

                redSum -= outgoingRed
                greenSum -= outgoingGreen
                blueSum -= outgoingBlue

                var stackStart = currentStackIndex - radius + division
                while (stackStart >= division) {
                    stackStart -= division
                }
                val outgoingStack = stack[stackStart]
                outgoingRed -= outgoingStack[0]
                outgoingGreen -= outgoingStack[1]
                outgoingBlue -= outgoingStack[2]

                if (xCoordinate2 == 0) {
                    minimumCoordinateArray[yCoordinate2] = minOf(yCoordinate2 + radius + 1, bitmapHeight - 1) * bitmapWidth
                }

                val nextOffset = minimumCoordinateArray[yCoordinate2] + xCoordinate2
                outgoingStack[0] = redArray[nextOffset]
                outgoingStack[1] = greenArray[nextOffset]
                outgoingStack[2] = blueArray[nextOffset]

                incomingRed += outgoingStack[0]
                incomingGreen += outgoingStack[1]
                incomingBlue += outgoingStack[2]

                redSum += incomingRed
                greenSum += incomingGreen
                blueSum += incomingBlue

                currentStackIndex++
                if (currentStackIndex >= division) {
                    currentStackIndex = 0
                }
                val incomingStack = stack[currentStackIndex]
                outgoingRed += incomingStack[0]
                outgoingGreen += incomingStack[1]
                outgoingBlue += incomingStack[2]
                incomingRed -= incomingStack[0]
                incomingGreen -= incomingStack[1]
                incomingBlue -= incomingStack[2]
                yCoordinate2++
            }
            xCoordinate2++
        }

        mutableBitmap.setPixels(pixelArray, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
        return mutableBitmap
    }
}

fun flamingoImageResolve(image: Bitmap): Bitmap {
    val resizedBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
    resizedBitmap.applyCanvas {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true

        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(3f)

        paint.colorFilter = ColorMatrixColorFilter(saturationMatrix)
        drawBitmap(resizedBitmap, 0f, 0f, paint)

        drawColor((0x33000000).toInt(), PorterDuff.Mode.OVERLAY)
        drawColor((0x40000000).toInt())
    }
    return FlamingoBitmapResolver.blurBitmap(resizedBitmap, 25)
}
