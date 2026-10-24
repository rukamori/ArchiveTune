package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.domain.player.ModulePlaybackIndicator
import moe.rukamori.archivetune.domain.player.ObserveModulePlaybackSourceUseCase
import moe.rukamori.archivetune.domain.player.PlaybackSourceTarget
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.isLocalMediaId

@Immutable
enum class PlaybackSourceKind { LOCAL, PODCAST, YOUTUBE, MODULE }

@Immutable
data class ModuleSourcePillUiModel(
    val moduleId: String?,
    val moduleName: String?,
    val loading: Boolean = false,
    val kind: PlaybackSourceKind = PlaybackSourceKind.YOUTUBE,
)

@Immutable
sealed interface ModuleSourcePillState {
    data object Loading : ModuleSourcePillState
    data object Empty : ModuleSourcePillState
    data object Error : ModuleSourcePillState
    data class Success(val model: ModuleSourcePillUiModel) : ModuleSourcePillState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ModulePlaybackSourceViewModel @Inject constructor(observe: ObserveModulePlaybackSourceUseCase) : ViewModel() {
    private val connection = MutableStateFlow<PlayerConnection?>(null)
    val state = connection.flatMapLatest { player ->
        combine(
            observe(player?.mediaMetadata?.map { metadata ->
                metadata?.let { PlaybackSourceTarget(it.id, it.id.isLocalMediaId(), it.isPodcast) }
            } ?: kotlinx.coroutines.flow.flowOf(null)),
            player?.playbackState ?: kotlinx.coroutines.flow.flowOf(Player.STATE_IDLE),
        ) { source, playbackState ->
            when {
                source == ModulePlaybackIndicator.Hidden -> ModuleSourcePillState.Empty
                source == ModulePlaybackIndicator.Loading -> ModuleSourcePillState.Loading
                source is ModulePlaybackIndicator.Module -> ModuleSourcePillState.Success(
                    ModuleSourcePillUiModel(source.source.id, source.source.name, loading = playbackState == Player.STATE_BUFFERING,
                        kind = PlaybackSourceKind.MODULE))
                playbackState == Player.STATE_BUFFERING -> ModuleSourcePillState.Loading
                else -> ModuleSourcePillState.Success(ModuleSourcePillUiModel(null, null, kind = when (source) {
                    ModulePlaybackIndicator.Local -> PlaybackSourceKind.LOCAL
                    ModulePlaybackIndicator.Podcast -> PlaybackSourceKind.PODCAST
                    else -> PlaybackSourceKind.YOUTUBE
                }))
            }
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(ModuleSourcePillState.Error)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuleSourcePillState.Empty)

    fun bind(player: PlayerConnection) { connection.value = player }
    fun unbind(player: PlayerConnection) { if (connection.value === player) connection.value = null }
}
