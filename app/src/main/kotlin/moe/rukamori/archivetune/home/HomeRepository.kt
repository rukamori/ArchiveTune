/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.home

import android.content.Context
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.constants.QuickPicksKey
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.QuickPicksDisplayModeKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.utils.dataStore
import java.util.Locale
import javax.inject.Inject

class HomeRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val database: MusicDatabase,
    ) {
        val showCategoryChips: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[ShowHomeCategoryChipsKey] ?: true }
                .distinctUntilChanged()

        val quickPicksDisplayMode: Flow<QuickPicksDisplayMode> =
            context.dataStore.data
                .map { preferences ->
                    preferences[QuickPicksDisplayModeKey].toEnum(QuickPicksDisplayMode.CARD)
                }.distinctUntilChanged()

        val quickPicksMode: Flow<QuickPicks> =
            context.dataStore.data
                .map { preferences -> preferences[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS) }
                .distinctUntilChanged()

        val showTonalBackdrop: Flow<Boolean> =
            context.dataStore.data
                .map { preferences -> preferences[DisableBlurKey] != true }
                .distinctUntilChanged()

        suspend fun loadQuickPickSeeds(limit: Int): List<QuickPickSeed> {
            val mostPlayed =
                database
                    .mostPlayedSongs(fromTimeStamp = 0L, limit = limit * 3)
                    .first()
                    .filter { song -> song.isYouTubeRecommendationSeed() }
            val localCandidates =
                mostPlayed.ifEmpty {
                    database
                        .recentSongs(limit = limit * 6)
                        .first()
                        .filter { song -> song.isYouTubeRecommendationSeed() }
                }
            val localSeeds =
                localCandidates
                    .map { song -> song.toQuickPickSeed() }
                    .distinctBy(QuickPickSeed::primaryArtistKey)
                    .take(limit)
            if (localSeeds.size >= limit) return localSeeds

            val remoteSeeds = loadRemoteQuickPickSeeds(limit * REMOTE_HISTORY_LOOKUP_MULTIPLIER)
            return (localSeeds + remoteSeeds)
                .distinctBy(QuickPickSeed::id)
                .distinctBy(QuickPickSeed::primaryArtistKey)
                .take(limit)
        }

        suspend fun loadRelatedSongs(seed: QuickPickSeed): Result<List<SongItem>> {
            val nextResult = YouTube.next(WatchEndpoint(videoId = seed.id))
            nextResult.exceptionOrNull()?.let { throwable ->
                if (throwable is CancellationException) throw throwable
            }
            val nextPage = nextResult.getOrNull()
            val relatedSongs =
                nextPage
                    ?.relatedEndpoint
                    ?.let { endpoint -> YouTube.related(endpoint).getOrNullPreservingCancellation()?.songs }
                    .orEmpty()
            val radioSongs =
                (relatedSongs + nextPage?.items.orEmpty())
                    .validRecommendationsFor(seed.id)
            if (radioSongs.isNotEmpty()) return Result.success(radioSongs)

            val searchedSongs =
                YouTube
                    .search(
                        query = seed.searchQuery,
                        filter = YouTube.SearchFilter.FILTER_SONG,
                        useAccountContext = true,
                    ).getOrNullPreservingCancellation()
                    ?.items
                    .orEmpty()
                    .filterIsInstance<SongItem>()
                    .validRecommendationsFor(seed.id)
            if (searchedSongs.isNotEmpty()) return Result.success(searchedSongs)

            val cachedSongs = loadCachedRelatedSongs(seed.id)
            if (cachedSongs.isNotEmpty()) return Result.success(cachedSongs)

            return nextResult.fold(
                onSuccess = { Result.success(emptyList()) },
                onFailure = { throwable -> Result.failure(throwable) },
            )
        }

        suspend fun loadLibrarySongIds(): Set<String> = database.librarySongIds().toSet()

        private suspend fun loadRemoteQuickPickSeeds(limit: Int): List<QuickPickSeed> {
            val songs =
                YouTube
                    .musicHistory()
                    .getOrNullPreservingCancellation()
                    ?.sections
                    .orEmpty()
                    .flatMap { section -> section.songs }
                    .filter { song -> song.id.length == YOUTUBE_VIDEO_ID_LENGTH }
            val artistFrequency =
                songs
                    .flatMap { song -> song.artists.map { artist -> artist.recommendationKey } }
                    .groupingBy { artistKey -> artistKey }
                    .eachCount()
            return songs
                .distinctBy(SongItem::id)
                .sortedByDescending { song ->
                    song.artists.maxOfOrNull { artist -> artistFrequency[artist.recommendationKey].orZero() }.orZero()
                }.map { song -> song.toQuickPickSeed() }
                .take(limit)
        }

        private suspend fun loadCachedRelatedSongs(seedSongId: String): List<SongItem> =
            database
                .getRelatedSongs(seedSongId)
                .first()
                .filter { song -> song.isYouTubeRecommendationSeed() }
                .map { song -> song.toSongItem() }
                .validRecommendationsFor(seedSongId)

        private fun List<SongItem>.validRecommendationsFor(seedSongId: String): List<SongItem> =
            asSequence()
                .filter { song ->
                    song.id != seedSongId &&
                        song.id.length == YOUTUBE_VIDEO_ID_LENGTH &&
                        song.title.isNotBlank() &&
                        song.artists.isNotEmpty()
                }.distinctBy(SongItem::id)
                .toList()

        private fun Song.toQuickPickSeed(): QuickPickSeed =
            QuickPickSeed(
                id = id,
                title = title,
                artistNames = ImmutableList.copyOf(artists.map { artist -> artist.name }),
                artistIds = ImmutableList.copyOf(artists.map { artist -> artist.id }),
            )

        private fun SongItem.toQuickPickSeed(): QuickPickSeed =
            QuickPickSeed(
                id = id,
                title = title,
                artistNames = ImmutableList.copyOf(artists.map(Artist::name)),
                artistIds = ImmutableList.copyOf(artists.mapNotNull(Artist::id)),
            )

        private fun Song.toSongItem(): SongItem =
            SongItem(
                id = id,
                title = title,
                artists = artists.map { artist -> Artist(name = artist.name, id = artist.id) },
                album = album?.let { album -> Album(name = album.title, id = album.id) },
                duration = song.duration.takeIf { duration -> duration >= 0 },
                thumbnail = song.thumbnailUrl.orEmpty(),
                explicit = song.explicit,
                endpoint = WatchEndpoint(videoId = id),
            )

        private fun Song.isYouTubeRecommendationSeed(): Boolean = !song.isLocal && id.length == YOUTUBE_VIDEO_ID_LENGTH

        private val Artist.recommendationKey: String
            get() = id?.takeIf(String::isNotBlank) ?: name.lowercase(Locale.ROOT)

        private fun Int?.orZero(): Int = this ?: 0

        private fun <T> Result<T>.getOrNullPreservingCancellation(): T? {
            exceptionOrNull()?.let { throwable ->
                if (throwable is CancellationException) throw throwable
            }
            return getOrNull()
        }

        private companion object {
            const val YOUTUBE_VIDEO_ID_LENGTH = 11
            const val REMOTE_HISTORY_LOOKUP_MULTIPLIER = 6
        }
    }
