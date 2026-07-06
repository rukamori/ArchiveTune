/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.extensions

import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Song

fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(
    fromIndex: Int,
    toIndex: Int,
): MutableList<T> {
    add(toIndex, removeAt(fromIndex))
    return this
}

fun <T : Any> List<T>.mergeNearbyElements(
    key: (T) -> Any = { it },
    merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T> {
    if (isEmpty()) return emptyList()

    val mergedList = mutableListOf<T>()
    var currentItem = this[0]

    for (i in 1 until size) {
        val nextItem = this[i]
        if (key(currentItem) == key(nextItem)) {
            currentItem = merge(currentItem, nextItem)
        } else {
            mergedList.add(currentItem)
            currentItem = nextItem
        }
    }
    mergedList.add(currentItem)

    return mergedList
}

// Extension function to filter explicit content for local Song entities
fun List<Song>.filterExplicit(enabled: Boolean = true) =
    filter { song ->
        song.artists.none { it.blockedAt != null } && (!enabled || !song.song.explicit)
    }

// Extension function to filter explicit content for local Album entities
fun List<Album>.filterExplicitAlbums(enabled: Boolean = true) =
    filter { album ->
        album.artists.none { it.blockedAt != null } && (!enabled || !album.album.explicit)
    }

fun List<Song>.filterBlockedArtists() = filter { song -> song.artists.none { it.blockedAt != null } }

// No-op for local songs: local Song entities do not contain video metadata to filter reliably
fun List<Song>.filterVideo(enabled: Boolean = true) = this
