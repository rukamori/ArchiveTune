/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.utils.dataStore
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

        suspend fun loadQuickPickSeeds(limit: Int): List<Song> {
            val fromTimestamp = System.currentTimeMillis() - QUICK_PICKS_HISTORY_WINDOW_MS
            val mostPlayed =
                database
                    .mostPlayedSongs(fromTimestamp, limit = limit * 3)
                    .first()
                    .filter { song -> song.isYouTubeRecommendationSeed() }
            val candidates =
                mostPlayed.ifEmpty {
                    database
                        .recentSongs(limit = limit * 6)
                        .first()
                        .filter { song -> song.isYouTubeRecommendationSeed() }
                }
            return candidates
                .distinctBy { song -> song.artists.firstOrNull()?.id ?: song.id }
                .take(limit)
        }

        suspend fun loadRelatedSongs(seedSongId: String): Result<List<SongItem>> {
            val nextPage =
                YouTube.next(WatchEndpoint(videoId = seedSongId)).getOrElse { throwable ->
                    return Result.failure(throwable)
                }
            val relatedEndpoint = nextPage.relatedEndpoint ?: return Result.success(emptyList())
            return YouTube.related(relatedEndpoint).map { page -> page.songs }
        }

        private fun Song.isYouTubeRecommendationSeed(): Boolean = !song.isLocal && id.length == YOUTUBE_VIDEO_ID_LENGTH

        private companion object {
            const val QUICK_PICKS_HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
            const val YOUTUBE_VIDEO_ID_LENGTH = 11
        }
    }
