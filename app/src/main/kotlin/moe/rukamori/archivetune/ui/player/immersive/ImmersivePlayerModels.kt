/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.immersive

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import moe.rukamori.archivetune.canvas.CanvasVideo
import moe.rukamori.archivetune.models.ActiveOutputDevice

@Immutable
sealed interface ImmersivePlayerScreenState {
    data object Loading : ImmersivePlayerScreenState
    data class Success(val model: ImmersivePlayerUiModel) : ImmersivePlayerScreenState
    data object Empty : ImmersivePlayerScreenState
    data class Error(
        @StringRes val messageRes: Int,
        val previous: ImmersivePlayerUiModel?,
    ) : ImmersivePlayerScreenState
}

@Immutable
data class ImmersivePlayerUiModel(
    val mediaId: String,
    val title: String,
    val artists: List<ImmersivePlayerArtist>,
    val albumId: String?,
    val sourceTitle: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val isLiked: Boolean,
    val formatDetails: String,
    val outputDevice: ActiveOutputDevice,
    val canvas: CanvasVideo?,
    val canvasFrame: ImageBitmap? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0f,
    val seekPositionMs: Long? = null,
    val showArtistDialog: Boolean = false,
)

@Immutable
data class ImmersivePlayerArtist(
    val id: String?,
    val name: String,
)

sealed interface ImmersivePlayerAction {
    data class CanvasFrameCaptured(
        val mediaId: String,
        val canvas: CanvasVideo,
        val frame: ImageBitmap?,
    ) : ImmersivePlayerAction
    data object TogglePlayPause : ImmersivePlayerAction
    data object SkipPrevious : ImmersivePlayerAction
    data object SkipNext : ImmersivePlayerAction
    data class PreviewSeek(val positionMs: Long) : ImmersivePlayerAction
    data object CommitSeek : ImmersivePlayerAction
    data class ChangeVolume(val fraction: Float) : ImmersivePlayerAction
    data object ToggleLike : ImmersivePlayerAction
    data object OpenAlbum : ImmersivePlayerAction
    data object OpenArtists : ImmersivePlayerAction
    data object DismissArtistDialog : ImmersivePlayerAction
    data class OpenArtist(val id: String) : ImmersivePlayerAction
    data object OpenMenu : ImmersivePlayerAction
}

sealed interface ImmersivePlayerEvent {
    data class OpenAlbum(val id: String) : ImmersivePlayerEvent
    data class OpenArtist(val id: String) : ImmersivePlayerEvent
    data object OpenMenu : ImmersivePlayerEvent
}
