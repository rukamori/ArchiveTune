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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 詳細画面の先頭（大アート＋題名＋再生ボタン）。
 *
 * アルバム・アーティスト・プレイリスト・検索結果が同じ形の冒頭を持つ。
 * 戻る矢印を先頭に置き、その下にアートを中央寄せする。Apple の詳細画面が
 * タイトルを見出しの下に落としている形に合わせている。
 *
 * @param subtitle アーティスト名や件数など。`null` なら描画しない。
 * @param metadata 年や総再生時間など、副次行。`null` なら描画しない。
 * @param onPlay 中央の再生ボタン。`null` なら出さない（検索結果など）。
 * @param artworkShape アートの形。アーティストは正円を渡す。
 */
@Composable
fun DetailHero(
    title: String,
    thumbnailUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: String? = null,
    onPlay: (() -> Unit)? = null,
    artworkShape: Shape = RoundedCornerShape(12.dp),
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Column(modifier = modifier.fillMaxWidth()) {
        BackButton(onClick = onBack)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.gutter.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                url = thumbnailUrl,
                contentDescription = title,
                modifier =
                    Modifier
                        .size(208.dp)
                        .clip(artworkShape),
            )

            Spacer(Modifier.width(metrics.md.dp))

            Text(
                text = title,
                style = type.title1,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = metrics.md.dp),
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = palette.labelSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = metrics.xs.dp),
                )
            }
            if (metadata != null) {
                Text(
                    text = metadata,
                    style = type.caption1,
                    color = palette.labelTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (onPlay != null) {
                PlayButton(onClick = onPlay, modifier = Modifier.padding(top = metrics.md.dp))
            }

            Spacer(Modifier.width(metrics.block.dp))
        }
    }
}

/**
 * 再生ボタン。
 *
 * アクセント色の塗りに白い再生アイコン。Material の FAB は影と色が大きく、
 * 面が二重になるので塗りとアイコンだけで作っている。
 */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.play),
) {
    val palette = applePalette()
    val type = appleType()

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
