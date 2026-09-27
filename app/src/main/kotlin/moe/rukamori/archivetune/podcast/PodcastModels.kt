/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.podcast

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import moe.rukamori.archivetune.models.MediaMetadata

sealed interface PodcastScreenState {
    data object Loading : PodcastScreenState

    @Immutable
    data class Success(
        val uiState: PodcastUiState,
    ) : PodcastScreenState

    data object Empty : PodcastScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : PodcastScreenState
}

@Immutable
data class PodcastUiState(
    val browseId: String,
    val title: String,
    val author: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val episodes: ImmutableList<PodcastEpisodeUiModel>,
    val isSaved: Boolean,
    val isSavePending: Boolean,
    val isLoadingMore: Boolean,
    val canLoadMore: Boolean,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isFiltering: Boolean = false,
    val visibleEpisodes: ImmutableList<PodcastEpisodeUiModel> = episodes,
    @StringRes val paginationErrorResId: Int? = null,
)

@Immutable
data class PodcastEpisodeUiModel(
    val id: String,
    val title: String,
    val podcastTitle: String,
    val description: String?,
    val dateText: String?,
    val durationText: String?,
    val thumbnailUrl: String,
    val playbackMetadata: MediaMetadata,
    val isInLibrary: Boolean,
    val isLibraryPending: Boolean,
)

@Immutable
data class PodcastLibraryMembership(
    val isPodcastSaved: Boolean,
    val episodeIds: ImmutableSet<String>,
)

@Immutable
data class PodcastPlaybackRequest(
    val title: String,
    val items: ImmutableList<MediaMetadata>,
    val startIndex: Int,
)

sealed interface PodcastAction {
    data object Retry : PodcastAction

    data object LoadMore : PodcastAction

    data object OpenSearch : PodcastAction

    data object CloseSearch : PodcastAction

    data class SearchQueryChanged(val query: String) : PodcastAction

    data object PlayAll : PodcastAction

    data object TogglePodcastSave : PodcastAction

    data class PlayEpisode(
        val episodeId: String,
    ) : PodcastAction

    data class ToggleEpisodeLibrary(
        val episodeId: String,
    ) : PodcastAction
}

sealed interface PodcastEvent {
    @Immutable
    data class Play(
        val request: PodcastPlaybackRequest,
    ) : PodcastEvent

    data class ShowMessage(
        @StringRes val messageResId: Int,
    ) : PodcastEvent
}
