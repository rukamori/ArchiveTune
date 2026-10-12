/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * 再生時間を `3:45` / `1:02:03` 形式へ整形する。
 *
 * `String.format` を使わないのは、既定ロケールによって数字の字種が変わり得るため。
 * 再生時間は常にラテン数字で出すほうが一貫して読める。
 *
 * 0秒・負値（未取得）は `null` を返し、呼び出し側に表示させない。
 */
fun formatDuration(totalSeconds: Int): String? {
    if (totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}

/**
 * 一覧に並べる楽曲1行。
 *
 * 枠線で仕切らず、余白と文字の階層だけで行を区切る。
 * 再生中の行は [isPlaying] によりタイトルをアクセント色へ変える（色と字重が唯一の合図）。
 *
 * @param index 1始まりの通し番号。`null` なら番号欄を潰さない。
 * @param onMoreClick 指定すると右端にメニューボタンを出す。指定しなければ出さない。
 */
@Composable
fun SongRow(
    song: MediaMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isPlaying: Boolean = false,
    onMoreClick: (() -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val duration = formatDuration(song.duration)
    val artistNames = song.artists.joinToString(", ") { it.name }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.xs.dp + 4.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = type.footnote,
                    color = palette.labelTertiary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(metrics.xs.dp))
        }

        Artwork(
            url = song.thumbnailUrl,
            contentDescription = song.title,
            modifier = Modifier.size(46.dp),
        )

        Spacer(Modifier.width(metrics.md.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = song.title,
                style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = if (isPlaying) palette.accent else palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artistNames.isNotEmpty()) {
                Text(
                    text = artistNames,
                    style = type.footnote,
                    color = if (isPlaying) palette.accent.copy(alpha = 0.75f) else palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (duration != null) {
            Spacer(Modifier.width(metrics.sm.dp))
            Text(
                text = duration,
                style = type.footnote,
                color = palette.labelTertiary,
                maxLines = 1,
            )
        }

        if (onMoreClick != null) {
            MoreButton(onClick = onMoreClick)
        }
    }
}

/** 右端の「…」ボタン。行全体のタップとは別に消費する。 */
@Composable
private fun MoreButton(onClick: () -> Unit) {
    val palette = applePalette()
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
