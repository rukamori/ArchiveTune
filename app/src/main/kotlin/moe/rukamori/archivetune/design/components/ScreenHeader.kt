/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 画面いちばん上の大きな見出し。
 *
 * 余白を取り敢えず広く取り、文字を大きくする。枠線や下線は足さない。
 * Apple Music 同様、スクロールに追従して縮む挙動は各画面側で持つ
 * （ここでは静的な見出しに徹し、遷移先の装飾と混ざらないようにしている）。
 *
 * @param subtitle 見出しの下に置く補足。`null` なら描画しない。
 * @param trailing 右端に置く操作（検索・絞り込み等）。
 */
@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.gutter.dp,
                    top = metrics.gutter.dp,
                    bottom = metrics.md.dp,
                ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                style = type.largeTitle,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(metrics.xs.dp))
                Text(
                    text = subtitle,
                    style = type.subhead,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(metrics.sm.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

/**
 * 一覧中のセクション見出し。
 *
 * 大きな見出しの下に置く単位。区切り線は引かず、余白だけで節目を作る。
 *
 * @param action 右端に置く「すべて表示」等。`actionLabel` を指定したときだけ描画する。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.gutter.dp,
                    top = metrics.section.dp,
                    bottom = metrics.sm.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = type.title2,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(metrics.sm.dp))
            Text(
                text = actionLabel,
                style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = palette.accent,
                maxLines = 1,
            )
        }
    }
}
