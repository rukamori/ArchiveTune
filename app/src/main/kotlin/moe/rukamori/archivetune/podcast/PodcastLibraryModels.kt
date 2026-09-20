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

sealed interface PodcastLibraryScreenState {
    data object Loading : PodcastLibraryScreenState

    @Immutable
    data class Success(
        val uiState: PodcastLibraryUiState,
    ) : PodcastLibraryScreenState

    data object Empty : PodcastLibraryScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : PodcastLibraryScreenState
}

@Immutable
data class PodcastLibraryUiState(
    val podcasts: ImmutableList<PodcastLibraryItemUiModel>,
)

@Immutable
data class PodcastLibraryItemUiModel(
    val browseId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val isSavedLocally: Boolean,
    val isSavedRemotely: Boolean,
)

sealed interface PodcastLibraryAction {
    data object Refresh : PodcastLibraryAction

    data class PlayPodcast(
        val browseId: String,
    ) : PodcastLibraryAction

    data class RemovePodcast(
        val browseId: String,
    ) : PodcastLibraryAction
}

sealed interface PodcastLibraryEvent {
    @Immutable
    data class Play(
        val request: PodcastPlaybackRequest,
    ) : PodcastLibraryEvent

    data class ShowMessage(
        @StringRes val messageResId: Int,
    ) : PodcastLibraryEvent
}
