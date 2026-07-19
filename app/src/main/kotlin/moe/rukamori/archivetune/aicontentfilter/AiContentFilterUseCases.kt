/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.aicontentfilter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import javax.inject.Inject

class ObserveAiContentFilterUseCase
    @Inject
    constructor(
        private val repository: AiContentFilterRepository,
    ) {
        operator fun invoke(): Flow<Pair<AiContentFilterSettings, AiContentFilterStatus>> =
            combine(repository.observeSettings(), repository.observeStatus(), ::Pair)
    }

class UpdateAiContentFilterSettingsUseCase
    @Inject
    constructor(
        private val repository: AiContentFilterRepository,
    ) {
        suspend fun setEnabled(enabled: Boolean) {
            repository.setEnabled(enabled)
        }

        suspend fun setIncludeModerateConfidence(enabled: Boolean) {
            repository.setIncludeModerateConfidence(enabled)
        }
    }

class RefreshAiContentFilterUseCase
    @Inject
    constructor(
        private val repository: AiContentFilterRepository,
    ) {
        suspend operator fun invoke(force: Boolean): AiContentFilterRefreshResult = repository.refreshIfStale(force)
    }

class LoadAiContentFilterPolicyUseCase
    @Inject
    constructor(
        private val repository: AiContentFilterRepository,
    ) {
        suspend operator fun invoke(): AiContentFilterPolicy {
            val settings = repository.getSettings()
            if (!settings.enabled) return AiContentFilterPolicy.Disabled

            repository.refreshIfStale()
            val lists = repository.loadLists()
            val blockedKeys =
                if (settings.includeModerateConfidence) {
                    lists.blocklist + lists.warnlist
                } else {
                    lists.blocklist
                }
            return AiContentFilterPolicy(
                enabled = true,
                blockedChannelKeys = blockedKeys,
            )
        }
    }

class FilterAiContentUseCase
    @Inject
    constructor() {
        operator fun <T : YTItem> invoke(
            items: List<T>,
            policy: AiContentFilterPolicy,
        ): List<T> {
            if (!policy.enabled || policy.blockedChannelKeys.isEmpty()) return items
            return items.filterNot { item -> item.matches(policy.blockedChannelKeys) }
        }

        private fun YTItem.matches(blockedChannelKeys: Set<String>): Boolean = creatorKeys().any(blockedChannelKeys::contains)

        private fun YTItem.creatorKeys(): Sequence<String> =
            when (this) {
                is SongItem -> {
                    artists.asSequence().flatMap { artist -> artist.keys() }
                }

                is AlbumItem -> {
                    artists.orEmpty().asSequence().flatMap { artist -> artist.keys() }
                }

                is PlaylistItem -> {
                    author?.keys().orEmpty()
                }

                is ArtistItem -> {
                    sequenceOf(title, id, channelId)
                        .filterNotNull()
                        .mapNotNull(::normalizeChannelKey)
                }
            }

        private fun Artist.keys(): Sequence<String> =
            sequenceOf(name, id)
                .filterNotNull()
                .mapNotNull(::normalizeChannelKey)
    }
