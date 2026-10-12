/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * アート中心のカード（アルバム・アーティスト・プレイリスト・動画）。
 *
 * 横並びのカルーセル用。正方形のアートを主役にし、タイトルはその下で
 * 静かに添えるだけ。枠線・影・面は付けない。
 * Apple Music の行と違い、カード自体を枠で囲むと一覧が重くなるため。
 *
 * @param size アートの一辺。カルーセルのピッチもこれで決まる。
 * @param subtitle アーティスト名や件数など。`null` なら1行ぶん潰す。
 * @param cornerRadius アートの丸み。`0.dp` なら正方形のまま。
 */
@Composable
fun MediaCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    subtitle: String? = null,
    size: Dp = 148.dp,
    cornerRadius: Dp? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Column(
        modifier =
            modifier
                .width(size)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
    ) {
        Artwork(
            url = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(size),
            cornerRadius = cornerRadius ?: metrics.artworkCorner.dp,
        )

        Spacer(Modifier.height(metrics.xs.dp))

        Text(
            text = title,
            style = type.subhead,
            color = palette.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = metrics.xs.dp),
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = metrics.xs.dp, top = 1.dp),
            )
        }
    }
}
