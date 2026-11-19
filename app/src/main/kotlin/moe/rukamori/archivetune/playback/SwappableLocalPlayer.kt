/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber
import java.lang.reflect.Field

@OptIn(UnstableApi::class)
internal class SwappableLocalPlayer(
    initial: ExoPlayer,
) : ForwardingPlayer(initial) {
    private val trackedListeners = mutableListOf<Player.Listener>()

    val exoPlayer: ExoPlayer
        get() = wrappedPlayer as ExoPlayer

    val canSwap: Boolean
        get() = PLAYER_FIELD != null

    override fun addListener(listener: Player.Listener) {
        if (trackedListeners.none { it === listener }) trackedListeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        trackedListeners.removeAll { it === listener }
        super.removeListener(listener)
    }

    fun swapTo(next: ExoPlayer): Boolean {
        val field = PLAYER_FIELD ?: return false
        val previous = exoPlayer
        if (previous === next) return true
        val before = PlayerSnapshot.of(previous)
        val listeners = trackedListeners.toList()
        listeners.forEach { super.removeListener(it) }
        val swapped =
            runCatching { field.set(this, next) }
                .onFailure { Timber.tag(TAG).e(it, "Unable to swap local playback player") }
                .isSuccess
        listeners.forEach { super.addListener(it) }
        if (!swapped || wrappedPlayer !== next) return false
        dispatchSwap(listeners, before)
        return true
    }

    private fun dispatchSwap(
        listeners: List<Player.Listener>,
        before: PlayerSnapshot,
    ) {
        val after = PlayerSnapshot.of(exoPlayer)
        val flags =
            FlagSet
                .Builder()
                .addAll(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                ).addIf(Player.EVENT_PLAYBACK_STATE_CHANGED, before.playbackState != after.playbackState)
                .addIf(Player.EVENT_PLAY_WHEN_READY_CHANGED, before.playWhenReady != after.playWhenReady)
                .addIf(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED, before.suppressionReason != after.suppressionReason)
                .addIf(Player.EVENT_IS_PLAYING_CHANGED, before.isPlaying != after.isPlaying)
                .addIf(Player.EVENT_IS_LOADING_CHANGED, before.isLoading != after.isLoading)
                .addIf(Player.EVENT_REPEAT_MODE_CHANGED, before.repeatMode != after.repeatMode)
                .addIf(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, before.shuffleModeEnabled != after.shuffleModeEnabled)
                .addIf(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, before.playbackParameters != after.playbackParameters)
                .addIf(Player.EVENT_AUDIO_SESSION_ID, before.audioSessionId != after.audioSessionId)
                .build()
        val events = Player.Events(flags)
        val player = exoPlayer
        val mediaItem = player.currentMediaItem
        val timeline = player.currentTimeline
        val tracks = player.currentTracks
        val metadata = player.mediaMetadata
        val commands = availableCommands
        val transitionReason =
            if (before.position.mediaItemIndex == after.position.mediaItemIndex && before.mediaId == mediaItem?.mediaId) {
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            } else {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            }
        listeners.forEach { listener ->
            listener.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            listener.onMediaItemTransition(mediaItem, transitionReason)
            listener.onPositionDiscontinuity(before.position, after.position, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
            listener.onTracksChanged(tracks)
            listener.onMediaMetadataChanged(metadata)
            listener.onAvailableCommandsChanged(commands)
            if (flags.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) listener.onPlaybackStateChanged(after.playbackState)
            if (flags.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                listener.onPlayWhenReadyChanged(after.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
            if (flags.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) {
                listener.onPlaybackSuppressionReasonChanged(after.suppressionReason)
            }
            if (flags.contains(Player.EVENT_IS_PLAYING_CHANGED)) listener.onIsPlayingChanged(after.isPlaying)
            if (flags.contains(Player.EVENT_IS_LOADING_CHANGED)) listener.onIsLoadingChanged(after.isLoading)
            if (flags.contains(Player.EVENT_REPEAT_MODE_CHANGED)) listener.onRepeatModeChanged(after.repeatMode)
            if (flags.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                listener.onShuffleModeEnabledChanged(after.shuffleModeEnabled)
            }
            if (flags.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                listener.onPlaybackParametersChanged(after.playbackParameters)
            }
            if (flags.contains(Player.EVENT_AUDIO_SESSION_ID)) listener.onAudioSessionIdChanged(after.audioSessionId)
            listener.onEvents(this, events)
        }
    }

    private class PlayerSnapshot(
        val playbackState: Int,
        val playWhenReady: Boolean,
        val suppressionReason: Int,
        val isPlaying: Boolean,
        val isLoading: Boolean,
        val repeatMode: Int,
        val shuffleModeEnabled: Boolean,
        val playbackParameters: PlaybackParameters,
        val audioSessionId: Int,
        val mediaId: String?,
        val position: Player.PositionInfo,
    ) {
        companion object {
            fun of(player: ExoPlayer): PlayerSnapshot =
                PlayerSnapshot(
                    playbackState = player.playbackState,
                    playWhenReady = player.playWhenReady,
                    suppressionReason = player.playbackSuppressionReason,
                    isPlaying = player.isPlaying,
                    isLoading = player.isLoading,
                    repeatMode = player.repeatMode,
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    playbackParameters = player.playbackParameters,
                    audioSessionId = player.audioSessionId,
                    mediaId = player.currentMediaItem?.mediaId,
                    position = positionInfo(player),
                )

            private fun positionInfo(player: ExoPlayer): Player.PositionInfo {
                val timeline = player.currentTimeline
                val mediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                val periodIndex = player.currentPeriodIndex.coerceAtLeast(0)
                val hasWindow = !timeline.isEmpty && mediaItemIndex < timeline.windowCount
                val hasPeriod = !timeline.isEmpty && periodIndex < timeline.periodCount
                return Player.PositionInfo(
                    if (hasWindow) timeline.getWindow(mediaItemIndex, Timeline.Window()).uid else null,
                    mediaItemIndex,
                    player.currentMediaItem ?: MediaItem.EMPTY,
                    if (hasPeriod) timeline.getPeriod(periodIndex, Timeline.Period(), true).uid else null,
                    periodIndex,
                    player.currentPosition,
                    player.contentPosition,
                    player.currentAdGroupIndex,
                    player.currentAdIndexInAdGroup,
                )
            }
        }
    }

    private companion object {
        const val TAG = "SwappableLocalPlayer"

        val PLAYER_FIELD: Field? =
            runCatching {
                ForwardingPlayer::class.java.getDeclaredField("player").apply { isAccessible = true }
            }.onFailure { Timber.tag(TAG).e(it, "ForwardingPlayer delegate field is unavailable") }
                .getOrNull()
    }
}
