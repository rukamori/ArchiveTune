/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * 再生キュー。
 *
 * 再生エンジン側の [androidx.media3.exoplayer.source.Timeline.Window] を
 * そのまま見せるため、ViewModel を持たない。並べ替え・削除は追加で入れる。
 *
 * 行をタップすると、そのウィンドウの**最初のピリオド**へ移る。
 * ウィンドウ番号ではなく [androidx.media3.Timeline.Window.firstPeriodIndex] を
 * 使うのが正（既存のキュー画面と同じ扱い）。
 *
 * @param onBack 戻る矢印。プレイヤーなど上位の画面へ戻す。
 */
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = metrics.gutter.dp - metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
        }

        LargeTitleHeader(
            title = stringResource(R.string.queue),
            subtitle = queueTitle,
        )

        if (queueWindows.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.queue),
                message = stringResource(R.string.no_track_playing),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentPadding =
                    PaddingValues(
                        bottom = metrics.gutter.dp,
                    ),
            ) {
                itemsIndexed(
                    items = queueWindows,
                    key = { _, window -> window.uid.toString() },
                ) { index, window ->
                    val metadata = window.mediaItem.metadata
                    val song =
                        metadata ?: run {
                            // メタデータが無い項目（外部スコープ等）でも
                            // 行は潰さず、識別に使える文字だけ出す。
                            MediaMetadata(
                                id = window.mediaItem.mediaId,
                                title = window.mediaItem.mediaId,
                                artists = emptyList(),
                                duration = 0,
                            )
                        }

                    SongRow(
                        song = song,
                        isPlaying = index == currentWindowIndex,
                        onClick = {
                            if (index == currentWindowIndex) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            }
                        },
                    )
                }
            }
        }
    }
}
