/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.search

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.repository.SearchDiscoveryRepository
import javax.inject.Inject

class LoadSearchDiscoveryUseCase
    @Inject
    constructor(
        private val repository: SearchDiscoveryRepository,
    ) {
        suspend operator fun invoke(): Result<SearchDiscoveryUiModel> =
            repository.loadDiscovery().map { data ->
                val chartItems = data.chartSections.flatMap { section -> section.items }

                SearchDiscoveryUiModel(
                    moodAndGenres = ImmutableList.copyOf(data.moodAndGenres),
                    suggestedSongs =
                        ImmutableList.copyOf(
                            data
                                .suggestedSongs
                                .distinctBy { item -> item.id }
                                .take(MaxDiscoveryItems),
                        ),
                    trendingAlbums =
                        ImmutableList.copyOf(
                            (
                                chartItems.filterIsInstance<AlbumItem>() +
                                    data.newReleaseAlbums +
                                    data.searchedAlbums
                            ).distinctBy { item -> item.id }.take(MaxDiscoveryItems),
                        ),
                    suggestedArtists =
                        ImmutableList.copyOf(
                            data
                                .suggestedArtists
                                .distinctBy { item -> item.id }
                                .take(MaxDiscoveryItems),
                        ),
                )
            }

        private companion object {
            const val MaxDiscoveryItems = 12
        }
    }

@Immutable
data class SearchDiscoveryUiModel(
    val moodAndGenres: ImmutableList<MoodAndGenres.Item>,
    val suggestedSongs: ImmutableList<SongItem>,
    val trendingAlbums: ImmutableList<AlbumItem>,
    val suggestedArtists: ImmutableList<ArtistItem>,
) {
    val isEmpty: Boolean
        get() = moodAndGenres.isEmpty() && suggestedSongs.isEmpty() && trendingAlbums.isEmpty() && suggestedArtists.isEmpty()
}
