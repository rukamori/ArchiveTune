/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasVideo
import moe.rukamori.archivetune.domain.player.ObserveImmersivePlayerUseCase
import moe.rukamori.archivetune.playback.ImmersivePlayerData
import moe.rukamori.archivetune.playback.ImmersivePlayerRepository
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.immersive.ImmersivePlayerAction
import moe.rukamori.archivetune.ui.player.immersive.ImmersivePlayerEvent
import moe.rukamori.archivetune.ui.player.immersive.ImmersivePlayerScreenState
import moe.rukamori.archivetune.ui.player.immersive.ImmersivePlayerUiModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ImmersivePlayerViewModel @Inject constructor(
    private val repository: ImmersivePlayerRepository,
    private val observePlayer: ObserveImmersivePlayerUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ImmersivePlayerScreenState>(ImmersivePlayerScreenState.Loading)
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<ImmersivePlayerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var latestData: ImmersivePlayerData? = null
    private var latestCanvas: ResolvedCanvas? = null

    init {
        viewModelScope.launch {
            observePlayer().collectLatest { data ->
                latestData = data
                if (data == null) {
                    latestCanvas = null
                    mutableState.value = ImmersivePlayerScreenState.Empty
                } else {
                    publish(data)
                }
            }
        }
        viewModelScope.launch {
            combine(
                observePlayer().distinctUntilChangedBy { it?.metadata?.id },
                observePlayer.canvasPolicy,
                observePlayer.canvasRevision,
                observePlayer.spotifyConnected,
            ) { data, policy, _, _ -> data to policy }
                .collectLatest { (data, policy) ->
                    if (data == null || !policy.ready || !policy.configuration.enabled) {
                        latestCanvas = null
                        data?.let(::publish)
                        return@collectLatest
                    }
                    try {
                        latestCanvas =
                            observePlayer
                                .loadCanvas(observePlayer.requestFor(data), policy)
                                ?.let { canvas -> ResolvedCanvas(data.metadata.id, canvas) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        latestCanvas = null
                        Timber.w(error, "Immersive player canvas resolution failed")
                    }
                    latestData?.takeIf { it.metadata.id == data.metadata.id }?.let(::publish)
                }
        }
        viewModelScope.launch {
            repository.observeProgress().collect { progress ->
                val current = mutableState.value
                val model =
                    when (current) {
                        is ImmersivePlayerScreenState.Success -> current.model
                        is ImmersivePlayerScreenState.Error -> current.previous
                        else -> null
                    } ?: return@collect
                val updated =
                    model.copy(
                        positionMs = model.seekPositionMs ?: progress.positionMs,
                        durationMs = progress.durationMs.takeIf { it > 0L } ?: model.durationMs,
                        volume = progress.volume,
                    )
                mutableState.value =
                    if (current is ImmersivePlayerScreenState.Error) {
                        current.copy(previous = updated)
                    } else {
                        ImmersivePlayerScreenState.Success(updated)
                    }
            }
        }
    }

    fun bind(playerConnection: PlayerConnection) {
        repository.attach(playerConnection)
    }

    fun unbind(playerConnection: PlayerConnection) {
        repository.detach(playerConnection)
    }

    fun onAction(action: ImmersivePlayerAction) {
        when (action) {
            is ImmersivePlayerAction.CanvasFrameCaptured -> updateModel {
                if (it.mediaId == action.mediaId && it.canvas == action.canvas) {
                    it.copy(canvasFrame = action.frame)
                } else {
                    it
                }
            }
            ImmersivePlayerAction.TogglePlayPause -> repository.togglePlayPause()
            ImmersivePlayerAction.SkipPrevious -> repository.skipPrevious()
            ImmersivePlayerAction.SkipNext -> repository.skipNext()
            is ImmersivePlayerAction.PreviewSeek -> updateModel { it.copy(seekPositionMs = action.positionMs) }
            ImmersivePlayerAction.CommitSeek -> {
                val target = currentModel()?.seekPositionMs ?: return
                repository.seekTo(target)
                updateModel { it.copy(positionMs = target, seekPositionMs = null) }
            }
            is ImmersivePlayerAction.ChangeVolume -> repository.setVolume(action.fraction)
            ImmersivePlayerAction.ToggleLike -> repository.toggleLike()
            ImmersivePlayerAction.OpenAlbum -> {
                currentModel()?.albumId?.let { eventChannel.trySend(ImmersivePlayerEvent.OpenAlbum(it)) }
            }
            ImmersivePlayerAction.OpenArtists -> {
                val artists = currentModel()?.artists.orEmpty().filter { !it.id.isNullOrBlank() }.distinctBy { it.id }
                if (artists.size > 1) {
                    updateModel { it.copy(showArtistDialog = true) }
                } else {
                    artists.singleOrNull()?.id?.let { eventChannel.trySend(ImmersivePlayerEvent.OpenArtist(it)) }
                }
            }
            ImmersivePlayerAction.DismissArtistDialog -> updateModel { it.copy(showArtistDialog = false) }
            is ImmersivePlayerAction.OpenArtist -> {
                if (currentModel()?.artists?.any { it.id == action.id } == true) {
                    updateModel { it.copy(showArtistDialog = false) }
                    eventChannel.trySend(ImmersivePlayerEvent.OpenArtist(action.id))
                }
            }
            ImmersivePlayerAction.OpenMenu -> eventChannel.trySend(ImmersivePlayerEvent.OpenMenu)
        }
    }

    private fun publish(data: ImmersivePlayerData) {
        val old = currentModel()
        val canvas = latestCanvas?.takeIf { it.mediaId == data.metadata.id }?.video
        val mapped = observePlayer.map(data, canvas)
        val model =
            if (old?.mediaId == mapped.mediaId) {
                mapped.copy(
                    positionMs = old.positionMs,
                    durationMs = old.durationMs,
                    volume = old.volume,
                    seekPositionMs = old.seekPositionMs,
                    showArtistDialog = old.showArtistDialog && old.artists == mapped.artists,
                    canvasFrame = old.canvasFrame.takeIf { old.canvas == mapped.canvas },
                )
            } else {
                mapped
            }
        mutableState.value =
            if (data.hasPlaybackError) {
                ImmersivePlayerScreenState.Error(R.string.playback_error_unknown, model)
            } else {
                ImmersivePlayerScreenState.Success(model)
            }
    }

    private fun currentModel(): ImmersivePlayerUiModel? =
        when (val current = mutableState.value) {
            is ImmersivePlayerScreenState.Success -> current.model
            is ImmersivePlayerScreenState.Error -> current.previous
            else -> null
        }

    private inline fun updateModel(transform: (ImmersivePlayerUiModel) -> ImmersivePlayerUiModel) {
        when (val current = mutableState.value) {
            is ImmersivePlayerScreenState.Success -> mutableState.value = current.copy(model = transform(current.model))
            is ImmersivePlayerScreenState.Error -> current.previous?.let { mutableState.value = current.copy(previous = transform(it)) }
            else -> Unit
        }
    }

    private data class ResolvedCanvas(
        val mediaId: String,
        val video: CanvasVideo,
    )
}
