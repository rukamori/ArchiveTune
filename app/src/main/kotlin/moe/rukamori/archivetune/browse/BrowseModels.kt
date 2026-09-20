/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.browse

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.YTItem

sealed interface BrowseScreenState {
    data object Loading : BrowseScreenState

    @Immutable
    data class Success(
        val uiState: BrowseUiState,
    ) : BrowseScreenState

    @Immutable
    data class Empty(
        val title: String?,
        @StringRes val fallbackTitleResId: Int?,
    ) : BrowseScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : BrowseScreenState
}

@Immutable
data class BrowseUiState(
    val title: String?,
    @StringRes val fallbackTitleResId: Int?,
    val items: ImmutableList<YTItem>,
    val isLoadingMore: Boolean,
    val canLoadMore: Boolean,
)

sealed interface BrowseAction {
    data object Retry : BrowseAction

    data object LoadMore : BrowseAction

    data class OpenItem(
        val itemId: String,
    ) : BrowseAction

    data class OpenItemMenu(
        val itemId: String,
    ) : BrowseAction
}

sealed interface BrowseEvent {
    data class OpenAlbum(
        val browseId: String,
    ) : BrowseEvent

    data class OpenPlaylist(
        val playlistId: String,
    ) : BrowseEvent

    data class OpenArtist(
        val browseId: String,
    ) : BrowseEvent

    data class OpenPodcast(
        val browseId: String,
    ) : BrowseEvent

    @Immutable
    data class PlayEpisode(
        val episode: EpisodeItem,
    ) : BrowseEvent

    @Immutable
    data class ShowItemMenu(
        val item: YTItem,
    ) : BrowseEvent

    data class ShowMessage(
        @StringRes val messageResId: Int,
    ) : BrowseEvent
}
