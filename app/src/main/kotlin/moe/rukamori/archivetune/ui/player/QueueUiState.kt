/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.models.MediaMetadata

@Immutable
sealed interface QueueScreenState {
    data object Loading : QueueScreenState
    data class Success(val model: QueueUiModel) : QueueScreenState
    data object Empty : QueueScreenState
    data class Error(@param:StringRes val message: Int, val previous: QueueUiModel? = null) : QueueScreenState
}

@Immutable
data class QueueEntryUi(
    val id: String,
    val title: String,
    val artists: String,
    val artworkUrl: String?,
    val duration: String,
    val current: Boolean,
    val recommended: Boolean,
    val selected: Boolean = false,
)

@Immutable
data class QueueChipUi(val id: String, val title: String, val selected: Boolean)

@Immutable
data class QueueEntries(val values: List<QueueEntryUi>)

@Immutable
data class QueueChips(val values: List<QueueChipUi>)

@Immutable
data class QueueUiModel(
    val title: String?,
    val entries: QueueEntries,
    val duration: String,
    val currentId: String?,
    val playing: Boolean,
    val liked: Boolean,
    val shuffle: Boolean,
    val repeatMode: Int,
    val locked: Boolean,
    val guest: Boolean,
    val canControlPlayback: Boolean,
    val episodeQueue: Boolean,
    val autoplay: Boolean,
    val autoplayAvailable: Boolean,
    val loadingRecommendations: Boolean,
    val chips: QueueChips,
    val recommendationsFailed: Boolean,
    val selectionCount: Int = 0,
    val overflowOpen: Boolean = false,
    val playlistDialog: Boolean = false,
    val createPlaylistDialog: Boolean = false,
    val savingPlaylist: Boolean = false,
    val playlistName: String = "",
)

@Immutable
data class QueueChromeUi(
    val showSleepTimer: Boolean = false,
    val sleepTimerValue: Float = 30f,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerTimeLeft: Long = 0L,
)

sealed interface QueueAction {
    data class Play(val id: String) : QueueAction
    data class Select(val id: String) : QueueAction
    data class Remove(val id: String) : QueueAction
    data class Menu(val id: String) : QueueAction
    data class PlayNext(val id: String) : QueueAction
    data class SelectChip(val id: String) : QueueAction
    data class Drag(val from: String, val to: String) : QueueAction
    data class CreatePlaylist(val name: String) : QueueAction
    data class PlaylistName(val value: String) : QueueAction
    data object FinishDrag : QueueAction
    data object ToggleShuffle : QueueAction
    data object ToggleRepeat : QueueAction
    data object ToggleAutoplay : QueueAction
    data object ToggleLock : QueueAction
    data object ToggleLike : QueueAction
    data object ToggleOverflow : QueueAction
    data object Clear : QueueAction
    data object ClearSelection : QueueAction
    data object SelectAll : QueueAction
    data object RemoveSelection : QueueAction
    data object AddToPlaylist : QueueAction
    data object NewPlaylist : QueueAction
    data object DismissPlaylist : QueueAction
    data object PlaylistAdded : QueueAction
    data object Retry : QueueAction
}

sealed interface QueueEvent {
    data class Menu(val id: String, val metadata: MediaMetadata, val canPlayNext: Boolean) : QueueEvent
    data class Message(@param:StringRes val resource: Int, val arguments: List<Any> = emptyList()) : QueueEvent
    data class Removed(val count: Int, val title: String?, val token: Long) : QueueEvent
}
