/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.drawToBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.LyricsShareImageOptions
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

object ComposeToImage {
    private tailrec fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        val config = bitmap.config
        if (config != Bitmap.Config.HARDWARE && config != null) return bitmap
        return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: bitmap
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pixelCopyViewBitmap(view: View): Bitmap? {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
        val activity = view.context.findActivity() ?: return null
        val window = activity.window ?: return null

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect =
            Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height,
            )

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copyResult =
            suspendCancellableCoroutine { cont ->
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    { result -> cont.resume(result) },
                    Handler(Looper.getMainLooper()),
                )
            }
        return if (copyResult == PixelCopy.SUCCESS) bitmap else null
    }

    suspend fun captureViewBitmap(
        view: View,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        backgroundColor: Int? = null,
    ): Bitmap {
        val fallbackBitmap =
            runCatching {
                view.drawToBitmap()
            }.getOrElse {
                val w = view.width.coerceAtLeast(1)
                val h = view.height.coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    backgroundColor?.let { Canvas(bmp).drawColor(it) }
                }
            }

        val original =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopyViewBitmap(view) ?: fallbackBitmap
            } else {
                fallbackBitmap
            }
        val needsScale =
            (targetWidth != null && targetWidth > 0 && targetWidth != original.width) ||
                (targetHeight != null && targetHeight > 0 && targetHeight != original.height)
        val base =
            if (needsScale) {
                val safeOriginal = ensureSoftwareBitmap(original)
                val tw = targetWidth ?: original.width
                val th = targetHeight ?: (original.height * tw / original.width)
                ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeOriginal, tw, th, true))
            } else {
                ensureSoftwareBitmap(original)
            }
        if (backgroundColor != null) {
            val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(backgroundColor)
            c.drawBitmap(base, 0f, 0f, null)
            return out
        }
        return base
    }

    fun cropBitmap(
        source: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val safeLeft = left.coerceIn(0, safeSource.width.coerceAtLeast(1) - 1)
        val safeTop = top.coerceIn(0, safeSource.height.coerceAtLeast(1) - 1)
        val safeWidth = width.coerceIn(1, safeSource.width - safeLeft)
        val safeHeight = height.coerceIn(1, safeSource.height - safeTop)
        return ensureSoftwareBitmap(Bitmap.createBitmap(safeSource, safeLeft, safeTop, safeWidth, safeHeight))
    }

    fun fitBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        backgroundColor: Int,
    ): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundColor)

        val scale =
            minOf(
                targetWidth.toFloat() / safeSource.width.coerceAtLeast(1),
                targetHeight.toFloat() / safeSource.height.coerceAtLeast(1),
            )
        val scaledW = (safeSource.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (safeSource.height * scale).toInt().coerceAtLeast(1)
        val scaled =
            if (scaledW != safeSource.width || scaledH != safeSource.height) {
                ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeSource, scaledW, scaledH, true))
            } else {
                safeSource
            }

        val dx = ((targetWidth - scaled.width) / 2f)
        val dy = ((targetHeight - scaled.height) / 2f)
        canvas.drawBitmap(scaled, dx, dy, null)
        return out
    }

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        glassStyle: moe.rukamori.archivetune.ui.component.LyricsGlassStyle? = null,
        shareOptions: LyricsShareImageOptions = LyricsShareImageOptions(),
    ): Bitmap =
        withContext(Dispatchers.Default) {
            val style = glassStyle ?: moe.rukamori.archivetune.ui.component.LyricsGlassStyle.FrostedDark
            val canvasWidth = width.coerceAtLeast(1)
            val canvasHeight = height.coerceAtLeast(1)
            val baseSize = minOf(canvasWidth, canvasHeight)
            val bitmap = createBitmap(canvasWidth, canvasHeight)
            val canvas = Canvas(bitmap)

            val mainTextColor =
                textColor
                    ?: style.textColor.let {
                        ((it.alpha * 255).toInt() shl 24) or
                            ((it.red * 255).toInt() shl 16) or
                            ((it.green * 255).toInt() shl 8) or
                            (it.blue * 255).toInt()
                    }
            val secondaryTxtColor =
                secondaryTextColor
                    ?: style.secondaryTextColor.let {
                        ((it.alpha * 255).toInt() shl 24) or
                            ((it.red * 255).toInt() shl 16) or
                            ((it.green * 255).toInt() shl 8) or
                            (it.blue * 255).toInt()
                    }
            val bgColor = backgroundColor ?: 0xFF121212.toInt()

            var coverArtBitmap: Bitmap? = null
            if (coverArtUrl != null) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverArtUrl)
                            .size(max(canvasWidth, canvasHeight))
                            .allowHardware(false)
                            .build()
                    val result = imageLoader.execute(request)
                    coverArtBitmap = result.image?.toBitmap()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            val fittedArt =
                coverArtBitmap?.let {
                    fitBitmap(
                        source = it,
                        targetWidth = canvasWidth,
                        targetHeight = canvasHeight,
                        backgroundColor = bgColor,
                    )
                }

            if (fittedArt != null) {
                val blurredBackground = blurBitmap(fittedArt, shareOptions.sanitizedBlurRadius)
                canvas.drawBitmap(blurredBackground, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                canvas.drawColor(bgColor)
            }

            val dimPaint =
                Paint().apply {
                    color =
                        android.graphics.Color.argb(
                            ((style.backgroundDimAlpha * shareOptions.sanitizedDimAmount).coerceIn(0f, 0.95f) * 255).toInt(),
                            0,
                            0,
                            0,
                        )
                    isAntiAlias = true
                }
            canvas.drawRect(RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat()), dimPaint)

            val glassMargin = baseSize * 0.045f
            val glassLeft = glassMargin
            val glassTop = glassMargin
            val glassRight = canvasWidth - glassMargin
            val glassBottom = canvasHeight - glassMargin
            val glassWidth = glassRight - glassLeft
            val glassHeight = glassBottom - glassTop
            val glassCornerRadius = baseSize * 0.05f

            val glassRect = RectF(glassLeft, glassTop, glassRight, glassBottom)
            val glassPath =
                Path().apply {
                    addRoundRect(glassRect, glassCornerRadius, glassCornerRadius, Path.Direction.CW)
                }

            if (fittedArt != null) {
                val frostedCrop = blurBitmap(fittedArt, (shareOptions.sanitizedBlurRadius + 10f).coerceIn(8f, 48f))
                canvas.withClip(glassPath) {
                    drawBitmap(frostedCrop, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }

            val glassBgPaint =
                Paint().apply {
                    color =
                        style.surfaceTint.let {
                            android.graphics.Color.argb(
                                (style.surfaceAlpha * 255).toInt(),
                                (it.red * 255).toInt(),
                                (it.green * 255).toInt(),
                                (it.blue * 255).toInt(),
                            )
                        }
                    isAntiAlias = true
                }
            canvas.drawRoundRect(glassRect, glassCornerRadius, glassCornerRadius, glassBgPaint)

            val overlayPaint =
                Paint().apply {
                    color =
                        style.overlayColor.let {
                            android.graphics.Color.argb(
                                (style.overlayAlpha * 255).toInt(),
                                (it.red * 255).toInt(),
                                (it.green * 255).toInt(),
                                (it.blue * 255).toInt(),
                            )
                        }
                    isAntiAlias = true
                }
            canvas.drawRoundRect(glassRect, glassCornerRadius, glassCornerRadius, overlayPaint)

            val borderPaint =
                Paint().apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    color = android.graphics.Color.argb(25, 255, 255, 255)
                    isAntiAlias = true
                }
            canvas.drawRoundRect(glassRect, glassCornerRadius, glassCornerRadius, borderPaint)

            val contentPadding = minOf(glassWidth, glassHeight) * 0.08f
            val contentLeft = glassLeft + contentPadding
            val contentTop = glassTop + contentPadding
            val contentRight = glassRight - contentPadding

            val imageCornerRadius = baseSize * 0.035f
            val coverSize = minOf(glassWidth * 0.18f, glassHeight * 0.15f)
            val topRowGap = baseSize * 0.035f

            val titlePaint =
                TextPaint().apply {
                    color = mainTextColor
                    textSize = baseSize * 0.038f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                    letterSpacing = -0.02f
                }
            val artistPaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = baseSize * 0.028f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }

            val showingArtwork = shareOptions.showArtwork && coverArtBitmap != null
            if (showingArtwork) {
                val rect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
                val path =
                    Path().apply {
                        addRoundRect(rect, imageCornerRadius, imageCornerRadius, Path.Direction.CW)
                    }
                canvas.withClip(path) {
                    drawBitmap(coverArtBitmap ?: return@withClip, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                val artBorderPaint =
                    Paint().apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = 1f
                        color = android.graphics.Color.argb(38, 255, 255, 255)
                        isAntiAlias = true
                    }
                canvas.drawRoundRect(rect, imageCornerRadius, imageCornerRadius, artBorderPaint)
            }

            val textMaxWidth =
                if (showingArtwork) {
                    (contentRight - contentLeft - coverSize - topRowGap).toInt()
                } else {
                    (contentRight - contentLeft).toInt()
                }
            val textStartX = if (showingArtwork) contentLeft + coverSize + topRowGap else contentLeft
            val headerAlignment = if (showingArtwork) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER

            val titleLayout =
                StaticLayout.Builder
                    .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth)
                    .setAlignment(headerAlignment)
                    .setMaxLines(1)
                    .build()
            val artistLayout =
                StaticLayout.Builder
                    .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth)
                    .setAlignment(headerAlignment)
                    .setMaxLines(1)
                    .build()

            val topBlockHeight = if (showingArtwork) coverSize else (titleLayout.height + artistLayout.height + 6f)
            val imageCenter = contentTop + topBlockHeight / 2f
            val textBlockHeight = titleLayout.height + artistLayout.height + 6f
            val textBlockY = imageCenter - textBlockHeight / 2f

            canvas.withTranslation(textStartX, textBlockY) {
                titleLayout.draw(this)
                translate(0f, titleLayout.height.toFloat() + 6f)
                artistLayout.draw(this)
            }

            val lyricsPaint =
                TextPaint().apply {
                    color = mainTextColor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    letterSpacing = -0.01f
                }

            val lyricsMaxWidth = (glassWidth * 0.85f).toInt()
            val logoBlockHeight = (baseSize * 0.08f).toInt()
            val headerBottom = if (showingArtwork) contentTop + coverSize else (textBlockY + textBlockHeight)
            val lyricsTop = headerBottom + baseSize * 0.045f
            val lyricsBottom = glassBottom - (logoBlockHeight + contentPadding)
            val availableLyricsHeight = lyricsBottom - lyricsTop

            var lyricsTextSize = baseSize * 0.055f
            var lyricsLayout: StaticLayout
            do {
                lyricsPaint.textSize = lyricsTextSize
                lyricsLayout =
                    StaticLayout.Builder
                        .obtain(
                            lyrics,
                            0,
                            lyrics.length,
                            lyricsPaint,
                            lyricsMaxWidth,
                        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .setLineSpacing(8f, 1.35f)
                        .setMaxLines(10)
                        .build()
                if (lyricsLayout.height > availableLyricsHeight) {
                    lyricsTextSize -= 2f
                } else {
                    break
                }
            } while (lyricsTextSize > 22f)

            val lyricsYOffset = lyricsTop + (availableLyricsHeight - lyricsLayout.height) / 2f
            canvas.withTranslation(glassLeft + (glassWidth - lyricsMaxWidth) / 2f, lyricsYOffset) {
                lyricsLayout.draw(this)
            }

            AppLogo(
                context = context,
                canvas = canvas,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                padding = contentLeft,
                bottomPadding = glassBottom - contentPadding,
                circleColor = secondaryTxtColor,
                logoTint = if (style.isDark) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor = secondaryTxtColor,
            )

            return@withContext bitmap
        }

    private fun blurBitmap(
        source: Bitmap,
        radius: Float,
    ): Bitmap {
        val safe = ensureSoftwareBitmap(source)
        val safeRadius = radius.coerceIn(0f, 48f)
        if (safeRadius <= 0.5f) return safe
        val maxDimension = max(safe.width, safe.height)
        if (maxDimension <= 720 || safeRadius < 8f) {
            return stackBlur(safe, safeRadius.roundToInt().coerceAtLeast(1))
        }

        val scale = 720f / maxDimension.toFloat()
        val scaledWidth = (safe.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (safe.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = ensureSoftwareBitmap(Bitmap.createScaledBitmap(safe, scaledWidth, scaledHeight, true))
        val blurred = stackBlur(scaled, (safeRadius * scale).roundToInt().coerceAtLeast(1))
        return ensureSoftwareBitmap(Bitmap.createScaledBitmap(blurred, safe.width, safe.height, true))
    }

    private fun stackBlur(
        source: Bitmap,
        radius: Int,
    ): Bitmap {
        val bitmap = ensureSoftwareBitmap(source.copy(Bitmap.Config.ARGB_8888, true))
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val wm = width - 1
        val hm = height - 1
        val div = radius + radius + 1
        val red = IntArray(width * height)
        val green = IntArray(width * height)
        val blue = IntArray(width * height)
        val vMin = IntArray(max(width, height))
        val divSum = ((div + 1) shr 1).let { it * it }
        val divTable = IntArray(256 * divSum) { it / divSum }
        val stack = Array(div) { IntArray(3) }

        var yi = 0
        var yw = 0
        for (y in 0 until height) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0

            for (i in -radius..radius) {
                val p = pixels[yi + i.coerceIn(0, wm)]
                val sir = stack[i + radius]
                sir[0] = p shr 16 and 0xFF
                sir[1] = p shr 8 and 0xFF
                sir[2] = p and 0xFF
                val rbs = radius + 1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }

            var stackPointer = radius
            for (x in 0 until width) {
                red[yi] = divTable[rsum]
                green[yi] = divTable[gsum]
                blue[yi] = divTable[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                val stackStart = (stackPointer - radius + div) % div
                val sir = stack[stackStart]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vMin[x] = (x + radius + 1).coerceAtMost(wm)
                }
                val p = pixels[yw + vMin[x]]
                sir[0] = p shr 16 and 0xFF
                sir[1] = p shr 8 and 0xFF
                sir[2] = p and 0xFF

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackPointer = (stackPointer + 1) % div
                val nextSir = stack[stackPointer]

                routsum += nextSir[0]
                goutsum += nextSir[1]
                boutsum += nextSir[2]

                rinsum -= nextSir[0]
                ginsum -= nextSir[1]
                binsum -= nextSir[2]
                yi++
            }
            yw += width
        }

        for (x in 0 until width) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var yp = -radius * width

            for (i in -radius..radius) {
                val yiIndex = max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = red[yiIndex]
                sir[1] = green[yiIndex]
                sir[2] = blue[yiIndex]
                val rbs = radius + 1 - kotlin.math.abs(i)
                rsum += red[yiIndex] * rbs
                gsum += green[yiIndex] * rbs
                bsum += blue[yiIndex] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) yp += width
            }

            var yiIndex = x
            var stackPointer = radius
            for (y in 0 until height) {
                pixels[yiIndex] =
                    pixels[yiIndex] and -0x1000000 or
                    (divTable[rsum] shl 16) or
                    (divTable[gsum] shl 8) or
                    divTable[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                val stackStart = (stackPointer - radius + div) % div
                val sir = stack[stackStart]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vMin[y] = ((y + radius + 1).coerceAtMost(hm)) * width
                }
                val p = x + vMin[y]
                sir[0] = red[p]
                sir[1] = green[p]
                sir[2] = blue[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackPointer = (stackPointer + 1) % div
                val nextSir = stack[stackPointer]

                routsum += nextSir[0]
                goutsum += nextSir[1]
                boutsum += nextSir[2]

                rinsum -= nextSir[0]
                ginsum -= nextSir[1]
                binsum -= nextSir[2]

                yiIndex += width
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun AppLogo(
        context: Context,
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        padding: Float,
        bottomPadding: Float = canvasHeight - padding,
        circleColor: Int,
        logoTint: Int,
        textColor: Int,
    ) {
        val baseSize = minOf(canvasWidth, canvasHeight).toFloat()
        val logoSize = (baseSize * 0.045f).toInt()

        val rawLogo = context.getDrawable(R.drawable.small_icon)?.toBitmap(logoSize, logoSize)
        val logo =
            rawLogo?.let { source ->
                val colored = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvasLogo = Canvas(colored)
                val paint =
                    Paint().apply {
                        colorFilter = PorterDuffColorFilter(logoTint, PorterDuff.Mode.SRC_IN)
                        isAntiAlias = true
                    }
                canvasLogo.drawBitmap(source, 0f, 0f, paint)
                colored
            }

        val appName = context.getString(R.string.app_name)
        val appNamePaint =
            TextPaint().apply {
                color = textColor
                textSize = baseSize * 0.028f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isAntiAlias = true
                letterSpacing = 0.02f
            }

        val circleRadius = logoSize * 0.55f
        val circleX = padding + circleRadius
        val circleY = bottomPadding - circleRadius
        val logoX = circleX - logoSize / 2f
        val logoY = circleY - logoSize / 2f
        val textX = padding + circleRadius * 2 + 10f
        val textY = circleY + appNamePaint.textSize * 0.3f

        val circlePaint =
            Paint().apply {
                color = circleColor
                isAntiAlias = true
                style = Paint.Style.FILL
            }
        canvas.drawCircle(circleX, circleY, circleRadius, circlePaint)

        logo?.let {
            canvas.drawBitmap(it, logoX, logoY, null)
        }

        canvas.drawText(appName, textX, textY, appNamePaint)
    }

    private fun formatStoryTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    suspend fun createStoryImage(
        context: Context,
        coverArtUrl: String?,
        title: String,
        artist: String,
        album: String? = null,
        statsLabel: String? = null,
        isObsession: Boolean = false,
        currentPositionMs: Long? = null,
        durationMs: Long? = null,
        isPlaying: Boolean = true,
        width: Int = 1080,
        height: Int = 1920,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Fetch artwork
            val coverBitmap: Bitmap? =
                if (!coverArtUrl.isNullOrBlank()) {
                    runCatching {
                        val loader = ImageLoader(context)
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(coverArtUrl)
                                .allowHardware(false)
                                .build()
                        loader.execute(request).image?.toBitmap()
                    }.getOrNull()
                } else {
                    null
                }

            // 2. Draw background (ambient dynamic blur)
            if (coverBitmap != null) {
                val blurred = stackBlur(Bitmap.createScaledBitmap(coverBitmap, 140, 140, true), 24)
                val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 140 }
                val bgRect = Rect(0, 0, width, height)
                canvas.drawBitmap(blurred, null, bgRect, bgPaint)

                // Dark multi-stop gradient overlay
                val overlayGradient =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        height.toFloat(),
                        intArrayOf(
                            android.graphics.Color.argb(200, 15, 15, 22),
                            android.graphics.Color.argb(235, 10, 10, 15),
                            android.graphics.Color.argb(255, 0, 0, 0),
                        ),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                val overlayPaint = Paint().apply { shader = overlayGradient }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            } else {
                val gradient =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        height.toFloat(),
                        intArrayOf(
                            android.graphics.Color.rgb(32, 32, 48),
                            android.graphics.Color.rgb(18, 18, 24),
                            android.graphics.Color.rgb(0, 0, 0),
                        ),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                val bgPaint = Paint().apply { shader = gradient }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }

            // 3. Top Branding: ArchiveTune Logo + "NOW PLAYING" badge
            AppLogo(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                canvasHeight = height,
                padding = 90f,
                bottomPadding = 180f,
                circleColor = android.graphics.Color.argb(50, 255, 255, 255),
                logoTint = android.graphics.Color.WHITE,
                textColor = android.graphics.Color.argb(220, 255, 255, 255),
            )

            val tagText = if (isPlaying) "NOW PLAYING" else "ARCHIVETUNE"
            val tagTextPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(160, 255, 255, 255)
                    textSize = width * 0.026f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    letterSpacing = 0.15f
                    textAlign = Paint.Align.RIGHT
                }
            canvas.drawText(tagText, width - 90f, 160f, tagTextPaint)

            // 4. Center Album Artwork Card (with rounded corners & ambient shadow)
            val artSize = (width * 0.76f).toInt()
            val artX = (width - artSize) / 2f
            val artY = (height * 0.22f)

            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val artRect = RectF(artX, artY, artX + artSize, artY + artSize)
            val cornerRadius = 52f

            if (coverBitmap != null) {
                val shadowPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(90, 0, 0, 0)
                        setShadowLayer(40f, 0f, 20f, android.graphics.Color.argb(140, 0, 0, 0))
                    }
                canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, shadowPaint)

                val roundedArt = Bitmap.createBitmap(artSize, artSize, Bitmap.Config.ARGB_8888)
                val artCanvas = Canvas(roundedArt)
                val clipPath =
                    Path().apply {
                        addRoundRect(RectF(0f, 0f, artSize.toFloat(), artSize.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)
                    }
                artCanvas.clipPath(clipPath)
                artCanvas.drawBitmap(
                    Bitmap.createScaledBitmap(coverBitmap, artSize, artSize, true),
                    0f,
                    0f,
                    artPaint,
                )
                canvas.drawBitmap(roundedArt, artX, artY, null)
            } else {
                val placeholderPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(40, 255, 255, 255)
                    }
                canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, placeholderPaint)
            }

            // 5. Song Title & Artist Text
            val textYStart = artY + artSize + 85f
            val titlePaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = width * 0.054f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
            val safeTitle = if (title.length > 32) title.take(30) + "…" else title
            canvas.drawText(safeTitle, width / 2f, textYStart, titlePaint)

            val artistSubtitle = if (!album.isNullOrBlank()) "$artist • $album" else artist
            val safeArtist = if (artistSubtitle.length > 40) artistSubtitle.take(38) + "…" else artistSubtitle
            val artistPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(195, 255, 255, 255)
                    textSize = width * 0.036f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
            canvas.drawText(safeArtist, width / 2f, textYStart + 60f, artistPaint)

            // 6. Live Playback Timeline / Progress Bar
            var currentY = textYStart + 110f
            if (durationMs != null && durationMs > 0) {
                val posMs = (currentPositionMs ?: 0L).coerceIn(0L, durationMs)
                val progressFraction = (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                val timelineMargin = width * 0.12f
                val timelineWidth = width - (timelineMargin * 2)
                val timelineHeight = 12f
                val timelineY = currentY + 30f

                // Track Background
                val trackPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(45, 255, 255, 255)
                        style = Paint.Style.FILL
                    }
                val trackRect = RectF(timelineMargin, timelineY, timelineMargin + timelineWidth, timelineY + timelineHeight)
                canvas.drawRoundRect(trackRect, 6f, 6f, trackPaint)

                // Filled Progress with Accent Color
                val filledWidth = timelineWidth * progressFraction
                if (filledWidth > 0) {
                    val progressPaint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            style = Paint.Style.FILL
                        }
                    val filledRect = RectF(timelineMargin, timelineY, timelineMargin + filledWidth, timelineY + timelineHeight)
                    canvas.drawRoundRect(filledRect, 6f, 6f, progressPaint)

                    val thumbPaint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            style = Paint.Style.FILL
                        }
                    canvas.drawCircle(timelineMargin + filledWidth, timelineY + (timelineHeight / 2f), 10f, thumbPaint)
                }

                // Time labels
                val timePaint =
                    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(150, 255, 255, 255)
                        textSize = width * 0.028f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }
                val posText = formatStoryTime(posMs)
                val durText = formatStoryTime(durationMs)

                canvas.drawText(posText, timelineMargin, timelineY + 42f, timePaint)
                timePaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(durText, width - timelineMargin, timelineY + 42f, timePaint)

                currentY = timelineY + 65f
            }

            // 7. Stats / Obsession Badge (if present)
            if (!statsLabel.isNullOrBlank()) {
                val badgePaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color =
                            if (isObsession) {
                                android.graphics.Color.argb(60, 255, 87, 34)
                            } else {
                                android.graphics.Color.argb(40, 255, 255, 255)
                            }
                        style = Paint.Style.FILL
                    }
                val badgeTextPaint =
                    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isObsession) android.graphics.Color.rgb(255, 138, 101) else android.graphics.Color.WHITE
                        textSize = width * 0.034f
                        typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }

                val textWidth = badgeTextPaint.measureText(statsLabel)
                val badgeWidth = textWidth + 80f
                val badgeHeight = 64f
                val badgeX = (width - badgeWidth) / 2f
                val badgeY = currentY + 10f
                val badgeRect = RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight)

                canvas.drawRoundRect(badgeRect, 32f, 32f, badgePaint)
                canvas.drawText(statsLabel, width / 2f, badgeY + 44f, badgeTextPaint)
            }

            // 8. Bottom Watermark
            val footerPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(100, 255, 255, 255)
                    textSize = width * 0.026f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                    letterSpacing = 0.12f
                }
            canvas.drawText("LISTENED ON ARCHIVETUNE", width / 2f, height - 100f, footerPaint)

            bitmap
        }

    fun saveBitmapAsFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Uri {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ArchiveTune")
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile,
            )
        }
    }
}
