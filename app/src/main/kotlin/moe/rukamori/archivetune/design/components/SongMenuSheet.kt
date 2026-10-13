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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * 曲のアクションを並べるボトムシート（Apple Music の曲メニューに相当）。
 *
 * 上端に丸みを持たせ、つかみ棒の下に曲の顔（アート・曲名・アーティスト）を置き、
 * その下へ操作を並べる。行は細い線で仕切り、押した行だけが反応する。
 *
 * 引数が `null` の項目は行ごと描画しない（取得できない情報に出口を作らない）。
 *
 * @param song 対象の曲。ヘッダ表示のみに使う。
 * @param onDismiss シートを閉じる。行を実行したあとも呼ぶ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    song: MediaMetadata,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onPlayRadio: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
) {
    val palette = applePalette()
    val metrics = appleMetrics()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = Color.Transparent,
        contentColor = palette.onSurface,
        dragHandle = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Spacer(
                    Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .background(palette.labelTertiary, RoundedCornerShape(3.dp)),
                )
            }
        },
    ) {
        // 地はガラスにする。シートは別のウィンドウに出るため背後の捕捉は渡さず、
        // 面の不透明度だけで透けるようにして、下の画面がうっすら残るようにする。
        GlassSurface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            hazeState = null,
        ) {
            Column {
                MenuHeader(song = song)

                Spacer(Modifier.height(metrics.xs.dp))

                onPlayNext?.let { action ->
                    MenuRow(
                        icon = R.drawable.playlist_play,
                        label = stringResource(R.string.play_next),
                        onClick = { action(); onDismiss() },
                    )
                }
                onAddToQueue?.let { action ->
                    MenuRow(
                        icon = R.drawable.playlist_add,
                        label = stringResource(R.string.add_to_queue),
                        onClick = { action(); onDismiss() },
                    )
                }
                onPlayRadio?.let { action ->
                    MenuRow(
                        icon = R.drawable.radio,
                        label = stringResource(R.string.start_radio),
                        onClick = { action(); onDismiss() },
                    )
                }

                MenuDivider()

                onOpenArtist?.let { action ->
                    MenuRow(
                        icon = R.drawable.artist,
                        label = stringResource(R.string.artists),
                        onClick = { action(); onDismiss() },
                    )
                }
                onOpenAlbum?.let { action ->
                    MenuRow(
                        icon = R.drawable.album,
                        label = stringResource(R.string.albums),
                        onClick = { action(); onDismiss() },
                    )
                }

                Spacer(Modifier.height(metrics.sm.dp))
            }
        }
    }
}

/** シート先頭の曲の顔。アート・曲名・アーティストを横並びで示す。 */
@Composable
private fun MenuHeader(song: MediaMetadata) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.gutter.dp, vertical = metrics.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = song.thumbnailUrl,
            contentDescription = song.title,
            modifier = Modifier.size(48.dp),
        )

        Spacer(Modifier.width(metrics.md.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = type.headline,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artistNames = song.artists.joinToString(", ") { it.name }
            if (artistNames.isNotEmpty()) {
                Text(
                    text = artistNames,
                    style = type.subhead,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** シート内の1行。アイコンとラベルだけの、装飾のない操作行。 */
@Composable
private fun MenuRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(horizontal = metrics.gutter.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(metrics.md.dp))
        Text(
            text = label,
            style = type.body,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行と行のあいだを仕切る細い線。全面を覆わず、左右を詰めた線にする。 */
@Composable
private fun MenuDivider() {
    val palette = applePalette()
    val metrics = appleMetrics()

    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.gutter.dp, vertical = metrics.xs.dp)
            .height(1.dp)
            .background(palette.separator),
    )
}
