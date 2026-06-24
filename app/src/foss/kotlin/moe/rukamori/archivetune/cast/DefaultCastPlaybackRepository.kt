/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cast

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DefaultCastPlaybackRepository(
    context: Context,
) : CastPlaybackRepository {
    override val screenState: StateFlow<CastScreenState> = MutableStateFlow(CastScreenState.Empty)

    override fun createPlayer(
        context: Context,
        localPlayer: ExoPlayer,
        mediaItemResolver: CastMediaItemResolver,
    ): Player = localPlayer

    override fun disconnect() = Unit

    override fun setVolume(volume: Float) = Unit
}
