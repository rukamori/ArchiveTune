/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject

class GetSpotifyLikedSongsUseCase
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
    ) {
        suspend operator fun invoke(): List<SpotifyLikedSong> =
            repository
                .likedSongs()
                .map(SpotifyTrack::toSpotifyLikedSong)
    }

data class SpotifyLikedSong(
    val id: String,
    val title: String,
    val artistNames: ImmutableList<String>,
    val thumbnailUrl: String?,
    val durationMs: Int,
    val isExplicit: Boolean,
) {
    fun toPlaybackTrack(): SpotifyTrack =
        SpotifyTrack(
            id = id,
            name = title,
            artists = artistNames.map { artistName -> SpotifySimpleArtist(name = artistName) },
            album =
                thumbnailUrl?.let { url ->
                    SpotifySimpleAlbum(images = listOf(SpotifyImage(url = url)))
                },
            durationMs = durationMs,
            explicit = isExplicit,
        )
}

private fun SpotifyTrack.toSpotifyLikedSong(): SpotifyLikedSong =
    SpotifyLikedSong(
        id = id,
        title = name,
        artistNames = ImmutableList.copyOf(artists.map { artist -> artist.name }),
        thumbnailUrl = SpotifyMapper.getTrackThumbnail(this),
        durationMs = durationMs,
        isExplicit = explicit,
    )
