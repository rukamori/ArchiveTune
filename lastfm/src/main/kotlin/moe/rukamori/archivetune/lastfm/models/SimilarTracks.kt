/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm.models

import kotlinx.serialization.Serializable

@Serializable
data class SimilarTracksResponse(
    val similartracks: SimilarTracksContainer? = null,
) {
    @Serializable
    data class SimilarTracksContainer(
        val track: List<SimilarTrackItem> = emptyList(),
    )

    @Serializable
    data class SimilarTrackItem(
        val name: String = "",
        val artist: SimilarTrackArtist = SimilarTrackArtist(),
    )

    @Serializable
    data class SimilarTrackArtist(
        val name: String = "",
    )
}
