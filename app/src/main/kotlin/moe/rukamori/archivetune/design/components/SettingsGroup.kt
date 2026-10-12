/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 設定画面のひとまとまり（見出し付きカード）。
 *
 * 枠線を外側で1本だけ引く代わりに [GlassSurface] を地に使い、行の間は
 * 水平のヘアラインだけで区切る。見出しはカードの外・上に置き、
 * カードの中に見出しを持ち込まない（Apple の設定と同様の階層）。
 *
 * @param title カード上の見出し。`null` なら見出し行を潰す。
 * @param content 行を並べる。区切り線は各行の [SettingsRow.showDivider] で制る。
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = type.footnote,
                color = palette.labelSecondary,
                modifier =
                    Modifier.padding(
                        start = metrics.gutter.dp,
                        end = metrics.gutter.dp,
                        top = metrics.section.dp,
                        bottom = metrics.xs.dp,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(metrics.cardCorner.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * 設定の1行。
 *
 * タイトル＋任意の補足、右に値・スイッチ・矢印。
 * 行全体が操作対象で、右端の部品だけを触る必要はない。
 *
 * @param value 右端に表示する現在値（「オート」等）。`trailing` より先に置く。
 * @param showDivider 行の上にヘアラインを引く。カード先頭では `false` にする。
 * @param onClick `null` なら行は押せない（表示専用になる）。
 * @param trailing 右端の部品（スイッチ等）。指定時も行のタップは効く。
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = (metrics.gutter + metrics.sm).dp)
                        .height(1.dp)
                        .background(palette.separator),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onClick() }
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        horizontal = metrics.gutter.dp,
                        vertical = metrics.md.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = type.body,
                    color = palette.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = type.footnote,
                        color = palette.labelSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (value != null) {
                Spacer(Modifier.width(metrics.sm.dp))
                Text(
                    text = value,
                    style = type.subhead,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (trailing != null) {
                Spacer(Modifier.width(metrics.sm.dp))
                trailing()
            }

            if (onClick != null && trailing == null) {
                Spacer(Modifier.width(metrics.xs.dp))
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.chevron_right),
                    contentDescription = null,
                    tint = palette.labelTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
