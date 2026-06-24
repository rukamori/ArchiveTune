/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cast

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow

fun interface CastMediaItemResolver {
    fun resolveForCast(mediaItem: MediaItem): MediaItem
}

interface CastPlaybackRepository {
    val screenState: StateFlow<CastScreenState>

    fun createPlayer(
        context: Context,
        localPlayer: ExoPlayer,
        mediaItemResolver: CastMediaItemResolver,
    ): Player

    fun disconnect()

    fun setVolume(volume: Float)
}
