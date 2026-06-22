/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.search

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist as YouTubeArtist
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
                    moodAndGenres = data.moodAndGenres,
                    trendingSongs =
                        data
                            .topSongs
                            .filterNot { song -> song.song.isLocal }
                            .map { song -> song.toSongItem() }
                            .distinctBy { item -> item.id }
                            .take(MaxTrendingItems),
                    trendingAlbums =
                        (
                            chartItems.filterIsInstance<AlbumItem>() +
                                data.newReleaseAlbums +
                                data.searchedAlbums
                        ).distinctBy { item -> item.id }.take(MaxTrendingItems),
                    trendingArtists =
                        data
                            .topArtists
                            .filter { artist -> artist.artist.isYouTubeArtist }
                            .map { artist -> artist.toArtistItem() }
                            .distinctBy { item -> item.id }
                            .take(MaxTrendingItems),
                )
            }

        private companion object {
            const val MaxTrendingItems = 12
        }
    }

private fun Song.toSongItem(): SongItem =
    SongItem(
        id = id,
        title = title,
        artists =
            artists.map { artist ->
                YouTubeArtist(
                    name = artist.name,
                    id = artist.id,
                )
            },
        album =
            album?.let { album ->
                Album(
                    name = album.title,
                    id = album.id,
                )
            } ?: song.albumId?.let { albumId ->
                Album(
                    name = song.albumName.orEmpty(),
                    id = albumId,
                )
            },
        duration = song.duration.takeIf { duration -> duration > 0 },
        thumbnail = song.thumbnailUrl.orEmpty(),
        explicit = song.explicit,
    )

private fun Artist.toArtistItem(): ArtistItem =
    ArtistItem(
        id = id,
        title = title,
        thumbnail = thumbnailUrl,
        channelId = artist.channelId,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

@Immutable
data class SearchDiscoveryUiModel(
    val moodAndGenres: List<MoodAndGenres.Item>,
    val trendingSongs: List<SongItem>,
    val trendingAlbums: List<AlbumItem>,
    val trendingArtists: List<ArtistItem>,
) {
    val isEmpty: Boolean
        get() = moodAndGenres.isEmpty() && trendingSongs.isEmpty() && trendingAlbums.isEmpty() && trendingArtists.isEmpty()
}
