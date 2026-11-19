/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.browse

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem

enum class GenreTab(@param:StringRes val titleRes: Int) {
    ALL(R.string.filter_all),
    SONGS(R.string.songs),
    PLAYLISTS(R.string.playlists),
    ALBUMS(R.string.albums),
    ;

    fun accepts(item: YTItem): Boolean = when (this) {
        ALL -> true
        SONGS -> item is SongItem
        PLAYLISTS -> item is PlaylistItem
        ALBUMS -> item is AlbumItem
    }
}

enum class GenreSource(@param:StringRes val titleRes: Int, val tab: GenreTab) {
    FEED(R.string.explore, GenreTab.ALL),
    SONGS(R.string.songs, GenreTab.SONGS),
    ALBUMS(R.string.albums, GenreTab.ALBUMS),
    FEATURED_PLAYLISTS(R.string.genre_featured_playlists, GenreTab.PLAYLISTS),
    COMMUNITY_PLAYLISTS(R.string.genre_community_playlists, GenreTab.PLAYLISTS),
}

@Immutable
data class GenreSection(
    val id: String,
    val title: String?,
    val items: ImmutableList<YTItem>,
    val endpoint: BrowseEndpoint? = null,
    val continuation: String? = null,
)

@Immutable
data class GenreSourceState(
    val source: GenreSource,
    val sections: ImmutableList<GenreSection> = ImmutableList.of(),
    val title: String? = null,
    val thumbnail: String? = null,
    val continuation: String? = null,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

@Immutable
data class GenreCollection(
    val sectionId: String,
    val items: ImmutableList<YTItem>,
    val continuations: ImmutableList<String>,
    val endpointLoaded: Boolean,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

@Immutable
data class GenreBrowseUiState(
    val genreTitle: String?,
    val sources: ImmutableList<GenreSourceState>,
    val selectedTab: GenreTab = GenreTab.ALL,
    val expandedSection: String? = null,
    val collections: ImmutableList<GenreCollection> = ImmutableList.of(),
) {
    val feed: GenreSourceState get() = sources.first { it.source == GenreSource.FEED }
    val title: String? get() = genreTitle ?: feed.title
    val section: GenreSection? get() = feed.sections.firstOrNull { it.id == expandedSection }
    val collection: GenreCollection? get() = collections.firstOrNull { it.sectionId == expandedSection }

    fun categoryItems(tab: GenreTab): List<YTItem> =
        (feed.sections.flatMap { it.items } + collections.flatMap { it.items } +
            sources.filter { it.source != GenreSource.FEED }.flatMap { it.sections }.flatMap { it.items })
            .filter(tab::accepts).distinctBy { it.genreKey() }

    fun additionalItems(tab: GenreTab): List<YTItem> {
        val editorialKeys = (feed.sections.flatMap { it.items } + collections.flatMap { it.items })
            .mapTo(HashSet()) { it.genreKey() }
        return sources.filter { it.source != GenreSource.FEED && it.source.tab == tab }
            .flatMap { it.sections }.flatMap { it.items }
            .filter { it.genreKey() !in editorialKeys }.distinctBy { it.genreKey() }
    }
}

sealed interface GenreBrowseScreenState {
    val data: GenreBrowseUiState

    data class Loading(override val data: GenreBrowseUiState) : GenreBrowseScreenState
    data class Success(override val data: GenreBrowseUiState) : GenreBrowseScreenState
    data class Empty(override val data: GenreBrowseUiState) : GenreBrowseScreenState
    data class Error(override val data: GenreBrowseUiState) : GenreBrowseScreenState
}

fun YTItem.genreKey(): String = "${this::class.simpleName}:$id"
