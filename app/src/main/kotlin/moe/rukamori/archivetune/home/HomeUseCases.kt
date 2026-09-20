/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.home

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
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

data class PreparedCommunityHomePage(
    val homePage: HomePage,
    val communitySection: HomePage.Section?,
)

class PrepareCommunityHomePageUseCase
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val localizedCommunityLabel = context.getString(R.string.filter_community_playlists)
        private val localizedPlaylistLabel = context.getString(R.string.filter_playlists)

        operator fun invoke(
            homePage: HomePage,
            allowStructuralFallback: Boolean,
        ): PreparedCommunityHomePage {
            val candidates =
                homePage.sections.withIndex().filter { (_, section) ->
                    section.featuredCards.isNotEmpty() || section.isPlaylistShelf()
                }
            val selected =
                candidates.firstOrNull { (_, section) -> section.title.matchesCommunityLabel() }
                    ?: candidates
                        .filter { (_, section) -> allowStructuralFallback && section.endpoint == null }
                        .singleOrNull()
                    ?: return PreparedCommunityHomePage(homePage = homePage, communitySection = null)
            val communitySection = selected.value.toFeaturedCommunitySection()
            return PreparedCommunityHomePage(
                homePage = homePage.copy(sections = homePage.sections.filterIndexed { index, _ -> index != selected.index }),
                communitySection = communitySection,
            )
        }

        private fun HomePage.Section.isPlaylistShelf(): Boolean =
            items.size >= MINIMUM_COMMUNITY_PLAYLISTS && items.all { item -> item is PlaylistItem }

        private fun HomePage.Section.toFeaturedCommunitySection(): HomePage.Section {
            if (featuredCards.isNotEmpty()) return this
            val playlists = items.filterIsInstance<PlaylistItem>()
            return copy(
                featuredCards =
                    playlists.map { playlist ->
                        HomePage.Section.FeaturedCard(
                            id = playlist.id,
                            title = playlist.title,
                            subtitle =
                                listOfNotNull(playlist.author?.name, playlist.songCountText)
                                    .distinct()
                                    .joinToString(separator = " • ")
                                    .takeIf(String::isNotBlank),
                            thumbnail = playlist.thumbnail,
                            endpoint = null,
                            playEndpoint = playlist.playEndpoint,
                            shuffleEndpoint = playlist.shuffleEndpoint,
                            radioEndpoint = playlist.radioEndpoint,
                            itemIds = emptyList(),
                        )
                    },
            )
        }

        private fun String.matchesCommunityLabel(): Boolean {
            val normalizedTitle = lowercase(Locale.getDefault())
            if (normalizedTitle.contains(ENGLISH_COMMUNITY_TOKEN)) return true
            val titleTokens = normalizedTitle.tokens()
            val localizedCommunityTokens =
                localizedCommunityLabel
                    .replace(localizedPlaylistLabel, newValue = "", ignoreCase = true)
                    .lowercase(Locale.getDefault())
                    .tokens()
                    .ifEmpty { localizedCommunityLabel.lowercase(Locale.getDefault()).tokens() }
            return titleTokens.any { titleToken ->
                localizedCommunityTokens.any { labelToken ->
                    titleToken == labelToken || titleToken.commonPrefixWith(labelToken).length >= MINIMUM_SHARED_PREFIX_LENGTH
                }
            }
        }

        private fun String.tokens(): List<String> =
            split(TOKEN_SEPARATOR).filter { token -> token.length >= MINIMUM_TOKEN_LENGTH }

        private companion object {
            val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
            const val ENGLISH_COMMUNITY_TOKEN = "community"
            const val MINIMUM_COMMUNITY_PLAYLISTS = 2
            const val MINIMUM_SHARED_PREFIX_LENGTH = 6
            const val MINIMUM_TOKEN_LENGTH = 4
        }
    }

class LoadCommunityPlaylistPreviewsUseCase
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) {
        suspend operator fun invoke(section: HomePage.Section): HomePage.Section =
            supervisorScope {
                val semaphore = Semaphore(PREVIEW_LOAD_CONCURRENCY)
                val existingSongs = section.items.filterIsInstance<SongItem>().associateBy(SongItem::id)
                val previews =
                    section.featuredCards.map { card ->
                        async {
                            val currentPreview = card.itemIds.mapNotNull(existingSongs::get)
                            if (currentPreview.isNotEmpty()) {
                                card.id to currentPreview
                            } else {
                                val result =
                                    semaphore.withPermit {
                                        repository.loadPlaylistPreview(card.id, PREVIEW_SONG_COUNT)
                                    }
                                result.exceptionOrNull()?.let { throwable ->
                                    if (throwable is CancellationException) throw throwable
                                }
                                card.id to result.getOrDefault(emptyList())
                            }
                        }
                    }.awaitAll()
                        .toMap()
                val previewSongs = previews.values.flatten().distinctBy(SongItem::id)
                section.copy(
                    items = (section.items + previewSongs).distinctBy(YTItem::id),
                    featuredCards =
                        section.featuredCards.map { card ->
                            card.copy(itemIds = previews[card.id].orEmpty().map(SongItem::id))
                        },
                )
            }

        private companion object {
            const val PREVIEW_LOAD_CONCURRENCY = 3
            const val PREVIEW_SONG_COUNT = 3
        }
    }

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
