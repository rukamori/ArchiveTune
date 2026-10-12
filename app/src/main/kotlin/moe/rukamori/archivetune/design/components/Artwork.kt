/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette

/**
 * アルバムアート。
 *
 * 新UIの主役はこのアートワークで、他の要素はこれを邪魔しない方向に整える。
 * 読み込み中・URLなし・読み込み失敗のいずれも **[placeholder] の面が残る**ようにして、
 * レイアウトが跳ねないようにしている（ダイミーではなく、実データの未到達状態）。
 *
 * @param url アートワークの URL。`null` ならプレースホルダのみ。
 * @param cornerRadius 角丸。既定は [appleMetrics] のアートワーク角丸。
 */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = appleMetrics().artworkCorner.dp,
) {
    val palette = applePalette()
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(palette.skeleton),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = palette.labelTertiary,
                modifier = Modifier.fillMaxWidth(0.45f),
            )
        }
    }
}
