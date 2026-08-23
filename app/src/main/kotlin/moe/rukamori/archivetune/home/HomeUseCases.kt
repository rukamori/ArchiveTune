/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.home

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.supervisorScope
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.innertube.models.SongItem
import java.util.Locale
import javax.inject.Inject

class ObserveHomePresentationPreferencesUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        operator fun invoke(): Flow<HomePresentationPreferences> =
            combine(
                repository.showCategoryChips,
                repository.quickPicksDisplayMode,
                repository.quickPicksMode,
                repository.showTonalBackdrop,
            ) { showCategoryChips, quickPicksDisplayMode, quickPicksMode, showTonalBackdrop ->
                HomePresentationPreferences(
                    showCategoryChips = showCategoryChips,
                    quickPicksDisplayMode = quickPicksDisplayMode,
                    quickPicksMode = quickPicksMode,
                    showTonalBackdrop = showTonalBackdrop,
                )
            }
    }

@Immutable
data class HomePresentationPreferences(
    val showCategoryChips: Boolean,
    val quickPicksDisplayMode: QuickPicksDisplayMode,
    val quickPicksMode: QuickPicks,
    val showTonalBackdrop: Boolean,
)

@Immutable
data class QuickPickSeed(
    val id: String,
    val title: String,
    val artistNames: ImmutableList<String>,
    val artistIds: ImmutableList<String>,
) {
    val primaryArtistKey: String
        get() = artistIds.firstOrNull() ?: artistNames.firstOrNull()?.lowercase(Locale.ROOT) ?: id

    val searchQuery: String
        get() = listOf(title, artistNames.firstOrNull()).filterNotNull().filter(String::isNotBlank).joinToString(" ")
}

class LoadPersonalizedQuickPicksUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        suspend operator fun invoke(
            excludedSongIds: Set<String>,
            limit: Int = DEFAULT_RESULT_LIMIT,
        ): Result<List<SongItem>> =
            runCatching {
                val seeds = repository.loadQuickPickSeeds(SEED_LIMIT)
                if (seeds.isEmpty()) return@runCatching emptyList()

                val relatedResults =
                    supervisorScope {
                        seeds.map { seed ->
                            async { repository.loadRelatedSongs(seed) }
                        }.awaitAll()
                    }
                relatedResults.firstNotNullOfOrNull { result ->
                    result.exceptionOrNull() as? CancellationException
                }?.let { cancellation -> throw cancellation }
                val successfulResults = relatedResults.mapNotNull { result -> result.getOrNull() }
                if (successfulResults.isEmpty()) {
                    relatedResults.firstNotNullOfOrNull { result -> result.exceptionOrNull() }?.let { throwable -> throw throwable }
                    return@runCatching emptyList()
                }

                val seedSongIds = seeds.mapTo(mutableSetOf(), QuickPickSeed::id)
                val seedArtistIds = seeds.flatMapTo(mutableSetOf(), QuickPickSeed::artistIds)
                val librarySongIds = repository.loadLibrarySongIds()
                val candidates = LinkedHashMap<String, QuickPickCandidate>()
                successfulResults.forEachIndexed { seedIndex, songs ->
                    songs.distinctBy(SongItem::id).take(RELATED_SONG_LIMIT).forEachIndexed { songIndex, song ->
                        if (song.id !in seedSongIds && song.id !in librarySongIds) {
                            val artistAffinity =
                                if (song.artists.any { artist -> artist.id != null && artist.id in seedArtistIds }) {
                                    ARTIST_AFFINITY_SCORE
                                } else {
                                    0
                                }
                            val seedWeight = (seeds.size - seedIndex) * SEED_WEIGHT
                            val positionWeight = RELATED_SONG_LIMIT - songIndex
                            val candidate =
                                candidates.getOrPut(song.id) {
                                    QuickPickCandidate(
                                        song = song,
                                        firstSeenIndex = candidates.size,
                                    )
                                }
                            candidate.sourceCount += 1
                            candidate.score += seedWeight + positionWeight + artistAffinity
                        }
                    }
                }

                val rankedSongs =
                    candidates.values
                        .sortedWith(
                            compareByDescending<QuickPickCandidate> { candidate -> candidate.sourceCount }
                                .thenByDescending { candidate -> candidate.score }
                                .thenBy { candidate -> candidate.firstSeenIndex },
                        ).map { candidate -> candidate.song }
                val unseenSongs = rankedSongs.filterNot { song -> song.id in excludedSongIds }
                val previousSongs = rankedSongs.filter { song -> song.id in excludedSongIds }
                val rotationPool = unseenSongs.take(limit * ROTATION_POOL_MULTIPLIER).shuffled()
                (rotationPool + previousSongs.shuffled()).take(limit)
            }

        private data class QuickPickCandidate(
            val song: SongItem,
            val firstSeenIndex: Int,
            var sourceCount: Int = 0,
            var score: Int = 0,
        )

        private companion object {
            const val SEED_LIMIT = 6
            const val RELATED_SONG_LIMIT = 30
            const val DEFAULT_RESULT_LIMIT = 20
            const val ROTATION_POOL_MULTIPLIER = 3
            const val SEED_WEIGHT = 100
            const val ARTIST_AFFINITY_SCORE = 80
        }
    }
