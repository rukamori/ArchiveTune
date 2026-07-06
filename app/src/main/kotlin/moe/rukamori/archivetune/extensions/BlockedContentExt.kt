/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.extensions

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult

fun <T : YTItem> List<T>.filterBlockedArtists(blockedArtistIds: Set<String>): List<T> {
    if (blockedArtistIds.isEmpty()) return this

    return filter { item ->
        when (item) {
            is ArtistItem -> item.id !in blockedArtistIds
            is SongItem -> item.artists.none { it.id in blockedArtistIds }
            is AlbumItem -> item.artists.orEmpty().none { it.id in blockedArtistIds }
            is PlaylistItem -> item.author?.id !in blockedArtistIds
        }
    }
}

fun BrowseResult.filterBlockedArtists(blockedArtistIds: Set<String>): BrowseResult {
    if (blockedArtistIds.isEmpty()) return this

    return copy(
        items =
            items.mapNotNull { section ->
                section.copy(
                    items =
                        section.items
                            .filterBlockedArtists(blockedArtistIds)
                            .ifEmpty { return@mapNotNull null },
                )
            },
    )
}
