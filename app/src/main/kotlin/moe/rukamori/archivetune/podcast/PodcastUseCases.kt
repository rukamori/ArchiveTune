/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.podcast

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import javax.inject.Inject

data class PodcastLoadResult(
    val uiState: PodcastUiState,
    val continuation: String?,
)

data class PodcastContinuationResult(
    val episodes: ImmutableList<PodcastEpisodeUiModel>,
    val continuation: String?,
)

class LoadPodcastUseCase
    @Inject
    constructor(
        private val repository: PodcastRepository,
    ) {
        suspend operator fun invoke(browseId: String): Result<PodcastLoadResult> {
            val validatedBrowseId = browseId.trim().takeIf(String::isNotBlank) ?: return Result.failure(IllegalArgumentException())
            return repository.loadPodcast(validatedBrowseId).map { page ->
                val episodes =
                    page.episodes
                        .asSequence()
                        .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.endpoint.videoId?.isNotBlank() == true }
                        .distinctBy(EpisodeItem::id)
                        .map { episode ->
                            episode.toUiModel(
                                fallbackPodcastTitle = page.podcast.title,
                                fallbackPodcastBrowseId = page.podcast.browseId,
                            )
                        }.toList()
                PodcastLoadResult(
                    uiState =
                        PodcastUiState(
                            browseId = page.podcast.browseId,
                            title = page.podcast.title,
                            author = page.podcast.author?.name,
                            description = page.description,
                            thumbnailUrl = page.podcast.thumbnail,
                            episodes = ImmutableList.copyOf(episodes),
                            isSaved = page.isSaved,
                            isSavePending = false,
                            isLoadingMore = false,
                            canLoadMore = !page.continuation.isNullOrBlank(),
                        ),
                    continuation = page.continuation?.takeIf(String::isNotBlank),
                )
            }
        }
    }

class LoadPodcastContinuationUseCase
    @Inject
    constructor(
        private val repository: PodcastRepository,
    ) {
        suspend operator fun invoke(
            continuation: String,
            podcastTitle: String,
            podcastBrowseId: String,
        ): Result<PodcastContinuationResult> {
            val validatedContinuation = continuation.trim().takeIf(String::isNotBlank) ?: return Result.failure(IllegalArgumentException())
            return repository.loadContinuation(validatedContinuation).map { page ->
                val episodes =
                    page.episodes
                        .asSequence()
                        .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.endpoint.videoId?.isNotBlank() == true }
                        .distinctBy(EpisodeItem::id)
                        .map { episode ->
                            episode.toUiModel(
                                fallbackPodcastTitle = podcastTitle,
                                fallbackPodcastBrowseId = podcastBrowseId,
                            )
                        }.toList()
                PodcastContinuationResult(
                    episodes = ImmutableList.copyOf(episodes),
                    continuation = page.continuation?.takeIf(String::isNotBlank),
                )
            }
        }
    }

private fun EpisodeItem.toUiModel(
    fallbackPodcastTitle: String,
    fallbackPodcastBrowseId: String,
): PodcastEpisodeUiModel {
    val resolvedPodcastTitle = podcast?.name?.takeIf(String::isNotBlank) ?: fallbackPodcastTitle
    val resolvedPodcastId = podcast?.id?.takeIf(String::isNotBlank) ?: fallbackPodcastBrowseId
    val metadata =
        toMediaMetadata().let { current ->
            current.copy(
                artists =
                    current.artists.ifEmpty {
                        listOf(MediaMetadata.Artist(id = resolvedPodcastId, name = resolvedPodcastTitle))
                    },
                album = current.album ?: MediaMetadata.Album(id = fallbackPodcastBrowseId, title = fallbackPodcastTitle),
            )
        }
    return PodcastEpisodeUiModel(
        id = id,
        title = title,
        podcastTitle = resolvedPodcastTitle,
        description = description,
        dateText = dateText,
        durationText = durationText,
        thumbnailUrl = thumbnail,
        playbackMetadata = metadata,
        isInLibrary = false,
        isLibraryPending = false,
    )
}

class ObservePodcastLibraryMembershipUseCase
    @Inject
    constructor(
        private val repository: PodcastRepository,
    ) {
        operator fun invoke(
            browseId: String,
            episodeIds: List<String>,
        ): Flow<PodcastLibraryMembership> = repository.observeLibraryMembership(browseId, episodeIds)
    }

class TogglePodcastSaveUseCase
    @Inject
    constructor(
        private val repository: PodcastRepository,
    ) {
        suspend operator fun invoke(
            browseId: String,
            save: Boolean,
        ): Result<Unit> = repository.setPodcastSaved(browseId, save)
    }

class ToggleEpisodeLibraryUseCase
    @Inject
    constructor(
        private val repository: PodcastRepository,
    ) {
        suspend operator fun invoke(
            metadata: MediaMetadata,
            addToLibrary: Boolean,
        ): Result<Unit> = repository.setEpisodeInLibrary(metadata, addToLibrary)
    }

class SearchPodcastEpisodesUseCase
    @Inject
    constructor() {
        private val whitespace = Regex("\\s+")

        suspend operator fun invoke(
            episodes: ImmutableList<PodcastEpisodeUiModel>,
            query: String,
        ): ImmutableList<PodcastEpisodeUiModel> =
            withContext(Dispatchers.Default) {
                val terms = query.trim().splitToSequence(whitespace).filter(String::isNotBlank).toList()
                if (terms.isEmpty()) return@withContext episodes
                ImmutableList.copyOf(
                    episodes.filter { episode ->
                        ensureActive()
                        terms.all { term ->
                            episode.title.contains(term, ignoreCase = true) ||
                                episode.description?.contains(term, ignoreCase = true) == true
                        }
                    },
                )
            }
    }
