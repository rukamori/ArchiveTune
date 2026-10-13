/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 画面いちばん上の大きな見出し。
 *
 * 余白を取り敢えず広く取り、文字を大きくする。枠線や下線は足さない。
 * スクロールで画面外に出たあとの受け皿は [PinnedTitleBar] で、
 * どちらを使うかは各画面が決める（ここでは静的な見出しに徹し、
 * 遷移先の装飾と混ざらないようにしている）。
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
 * 大タイトルが画面外に出たあとに、上部へ差し込む小さな見出し。
 *
 * Apple Music のナビバーに相当する。地色と下端のヘアラインだけで示し、
 * 枠や影は足さない。表示・非表示は高さと不透明度で滑らかに移る。
 *
 * 一覧の**上へ重ねて**使う（一覧の item に置くと二重にスクロールする）。
 * 使いたい値は [rememberTitleCollapse] で取る。
 *
 * @param visible [rememberTitleCollapse] の結果をそのまま渡す。
 * @param trailing 右端に置く操作。`null` なら右側は空けるだけ。
 */
@Composable
fun PinnedTitleBar(
    title: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val windowInsets = LocalPlayerAwareWindowInsets.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(170)) + expandVertically(tween(170)),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    .background(palette.background),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = metrics.gutter.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = type.headline,
                    color = palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) {
                    Spacer(Modifier.width(metrics.sm.dp))
                    trailing()
                }
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.separator),
            )
        }
    }
}

/**
 * 大タイトルが画面外に出たかを数える。
 *
 * 先頭 item（= 大タイトル）が [threshold] だけ潜ったら `true` を返す。
 * 一覧が無い状態（読み込み・空・エラー）では常に `false` になるよう、
 * 呼び出し側はその状態でこの関数を使わないか、結果を合わせて扱う。
 *
 * @param state 大タイトルを先頭 item に持つ一覧の状態。
 */
@Composable
fun rememberTitleCollapse(
    state: LazyListState,
    threshold: Dp = 32.dp,
): Boolean {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }.toInt()
    return remember(state, thresholdPx) {
        derivedStateOf {
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > thresholdPx
        }
    }.value
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
