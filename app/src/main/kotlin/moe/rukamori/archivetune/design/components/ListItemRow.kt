/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 一一覧の縦組み1行（アーティスト・アルバム・プレイリスト等）。
 *
 * 曲は [SongRow] の専用レイアウトを使うため、ここでは「丸／角のアート＋
 * タイトル＋補足」だけを持つ汎用行にする。区切り線は引かず、
 * 行の高さと左の余白だけで区切る（Apple の一覧と同じ）。
 *
 * @param circularArtwork アーティスト等は正円、それ以外は角丸の正方形。
 * @param trailing 右端の部品（プレイ数など）。`null` なら詰めない。
 */
@Composable
fun ListItemRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailUrl: String? = null,
    artworkSize: Dp = 46.dp,
    circularArtwork: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.sm.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(artworkSize)
                    .clip(if (circularArtwork) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(metrics.sm.dp)),
        )

        Spacer(Modifier.width(metrics.md.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.body,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(metrics.sm.dp))
            Box(contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
