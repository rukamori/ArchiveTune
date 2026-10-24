package moe.rukamori.archivetune.domain.player

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import moe.rukamori.archivetune.modules.ModulePlaybackSource
import moe.rukamori.archivetune.modules.ModulePlaybackSourceRepository
import moe.rukamori.archivetune.modules.ModuleRepository

sealed interface ModulePlaybackIndicator {
    data object Hidden : ModulePlaybackIndicator
    data object Loading : ModulePlaybackIndicator
    data object Local : ModulePlaybackIndicator
    data object Podcast : ModulePlaybackIndicator
    data object YouTube : ModulePlaybackIndicator
    data class Module(val source: ModulePlaybackSource) : ModulePlaybackIndicator
}

data class PlaybackSourceTarget(val mediaId: String, val isLocal: Boolean, val isPodcast: Boolean)

class ObserveModulePlaybackSourceUseCase @Inject constructor(
    private val modules: ModuleRepository,
    private val playback: ModulePlaybackSourceRepository,
) {
    operator fun invoke(target: Flow<PlaybackSourceTarget?>): Flow<ModulePlaybackIndicator> = combine(
        flow { modules.snapshot(); emitAll(modules.state.filterNotNull()) },
        playback.sources,
        target,
    ) { registry, sources, id ->
        modulePlaybackIndicator(registry.enabled && registry.modules.any { it.enabled }, id, sources)
    }
}

internal fun modulePlaybackIndicator(
    enabled: Boolean,
    target: PlaybackSourceTarget?,
    sources: Map<String, ModulePlaybackSource?>,
): ModulePlaybackIndicator = when {
    target == null -> ModulePlaybackIndicator.Hidden
    target.isPodcast -> ModulePlaybackIndicator.Podcast
    target.isLocal -> ModulePlaybackIndicator.Local
    sources[target.mediaId] != null -> ModulePlaybackIndicator.Module(checkNotNull(sources[target.mediaId]))
    enabled && !sources.containsKey(target.mediaId) -> ModulePlaybackIndicator.Loading
    else -> ModulePlaybackIndicator.YouTube
}
