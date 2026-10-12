/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全画面プレイヤーの背景に敷く、アートワーク由来の2色グラデーション。
 *
 * Apple Music と同じく、ジャケットの色から上だけを抜き出して下端へ落とす。
 * 上がジャケットの色、下がほぼ黒なので、文字（曲名・操作系）は常に暗い面に乗る。
 */
data class ArtworkBackdrop(
    val top: Color,
    val bottom: Color,
)

private val DefaultBackdrop =
    ArtworkBackdrop(
        top = Color(0xFF2C2C2E),
        bottom = Color(0xFF000000),
    )

/**
 * 履歴を保持する固定サイズのキャッシュ。
 *
 * 同じ曲をプレイヤーで開くたびに再デコード・再解析を避けさせるためのもの。
 * 解析はアート1枚あたり数ミリ秒だが、シーク中に裏で回ると無駄になる。
 */
private val backdropCache =
    object : LinkedHashMap<String, ArtworkBackdrop>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkBackdrop>): Boolean =
            size > 24
    }

/**
 * 再生中の曲に対応する背景色を返す。
 *
 * 初回は既定色（灰→黒）を返し、解析が終わってから置き換える。
 * その間もレイアウトは崩れない（高さが変わらない）。
 *
 * @param mediaId キャッシュのキー。曲が変わるときだけ取り直す。
 * @param thumbnailUrl 解析対象。`null` なら解析せず既定色のまま。
 */
@Composable
fun rememberArtworkBackdrop(
    mediaId: String?,
    thumbnailUrl: String?,
): ArtworkBackdrop {
    val context = LocalContext.current
    var backdrop by remember(mediaId) { mutableStateOf(backdropCache[mediaId] ?: DefaultBackdrop) }

    LaunchedEffect(mediaId, thumbnailUrl) {
        if (mediaId == null) {
            backdrop = DefaultBackdrop
            return@LaunchedEffect
        }
        backdropCache[mediaId]?.let { cached ->
            backdrop = cached
            return@LaunchedEffect
        }
        if (thumbnailUrl.isNullOrBlank()) return@LaunchedEffect

        val extracted =
            runCatching { extractBackdrop(context, thumbnailUrl) }
                .getOrNull()
                ?.takeIf { it.top != it.bottom } ?: return@LaunchedEffect

        backdropCache[mediaId] = extracted
        backdrop = extracted
    }

    return backdrop
}

/** アートワークを縮小デコードしてパレットを取り、上・下2色へ落とす。 */
private suspend fun extractBackdrop(
    context: Context,
    url: String,
): ArtworkBackdrop? {
    val request =
        ImageRequest
            .Builder(context)
            .data(url)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .size(IMAGE_SIZE, IMAGE_SIZE)
            .allowHardware(false)
            .build()

    val result =
        runCatching {
            withContext(Dispatchers.IO) { context.imageLoader.execute(request) }
        }.getOrNull() ?: return null

    val bitmap = result.image?.toBitmap() ?: return null

    val palette =
        runCatching {
            withContext(Dispatchers.Default) {
                Palette
                    .from(bitmap)
                    .maximumColorCount(MAX_COLOR_COUNT)
                    .resizeBitmapArea(BITMAP_AREA)
                    .generate()
            }
        }.getOrNull() ?: return null

    val swatch =
        palette.vibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
            ?: palette.darkVibrantSwatch
            ?: return null

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(swatch.rgb, hsv)

    // 彩度が低すぎる（ほぼ無彩色の写真）と差がつかないので下限を置く。
    // 彩度が高いと黒に落ちたときに濁るので上限も切る。
    val saturation = (hsv[1] * 1.12f).coerceIn(0.24f, 0.92f)
    // 明度を上げすぎると白っぽくなって文字が読めない。上は中間寄りに留める。
    val topValue = (hsv[2] * 0.62f + 0.24f).coerceIn(0.34f, 0.78f)
    val bottomValue = (hsv[2] * 0.16f + 0.045f).coerceIn(0.03f, 0.20f)

    val top = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation, topValue)))
    val bottom =
        Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(hsv[0], (saturation * 0.80f).coerceAtLeast(0.14f), bottomValue),
            ),
        )

    if (abs(topValue - bottomValue) < 0.10f) return null
    return ArtworkBackdrop(top = top, bottom = bottom)
}

private const val IMAGE_SIZE = 160
private const val MAX_COLOR_COUNT = 16
private const val BITMAP_AREA = 4096
