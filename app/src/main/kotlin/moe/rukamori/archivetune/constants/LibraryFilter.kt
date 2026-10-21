/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

enum class LibraryFilter {
    SONGS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    PODCASTS,
    SPOTIFY,
    LOCAL,
    LIBRARY,
}

val DefaultLibraryFilterOrder =
    listOf(
        LibraryFilter.LIBRARY,
        LibraryFilter.LOCAL,
        LibraryFilter.PLAYLISTS,
        LibraryFilter.PODCASTS,
        LibraryFilter.SPOTIFY,
        LibraryFilter.SONGS,
        LibraryFilter.ARTISTS,
        LibraryFilter.ALBUMS,
    )

val DefaultLibraryFilterOrderPreference =
    DefaultLibraryFilterOrder.joinToString(",") { it.name }

fun String.toLibraryFilterOrder(): List<LibraryFilter> {
    val savedOrder =
        split(",")
            .mapNotNull { savedFilter ->
                DefaultLibraryFilterOrder.firstOrNull { it.name == savedFilter.trim() }
            }.distinct().toMutableList()

    if (LibraryFilter.LOCAL !in savedOrder) {
        val libraryIndex = savedOrder.indexOf(LibraryFilter.LIBRARY)
        if (libraryIndex != -1) {
            savedOrder.add(libraryIndex + 1, LibraryFilter.LOCAL)
        } else {
            savedOrder.add(0, LibraryFilter.LOCAL)
        }
    } else {
        val oldDefaultWithLocalAtEnd =
            listOf(
                LibraryFilter.LIBRARY,
                LibraryFilter.PLAYLISTS,
                LibraryFilter.PODCASTS,
                LibraryFilter.SPOTIFY,
                LibraryFilter.SONGS,
                LibraryFilter.ARTISTS,
                LibraryFilter.ALBUMS,
                LibraryFilter.LOCAL,
            )
        if (savedOrder == oldDefaultWithLocalAtEnd) {
            savedOrder.remove(LibraryFilter.LOCAL)
            savedOrder.add(1, LibraryFilter.LOCAL)
        }
    }

    val missing = DefaultLibraryFilterOrder.filterNot(savedOrder::contains)
    savedOrder.addAll(missing)
    return savedOrder
}

fun List<LibraryFilter>.toLibraryFilterPreference(): String {
    val orderedFilters = distinct()
    return (orderedFilters + DefaultLibraryFilterOrder.filterNot(orderedFilters::contains))
        .joinToString(",") { it.name }
}
