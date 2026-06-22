/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TogetherProtocolVersion: Int = 1

@Serializable
sealed interface TogetherMessage

@Serializable
@SerialName("client_hello")
@Immutable
data class ClientHello(
    val protocolVersion: Int,
    val sessionId: String,
    val sessionKey: String,
    val clientId: String,
    val displayName: String,
) : TogetherMessage

@Serializable
@SerialName("server_welcome")
@Immutable
data class ServerWelcome(
    val protocolVersion: Int,
    val sessionId: String,
    val participantId: String,
    val role: ServerRole,
    val isPending: Boolean,
    val settings: TogetherRoomSettings,
) : TogetherMessage

@Serializable
@SerialName("server_error")
@Immutable
data class ServerError(
    val sessionId: String?,
    val message: String,
    val code: String? = null,
) : TogetherMessage

@Serializable
@SerialName("room_state")
@Immutable
data class RoomStateMessage(
    val state: TogetherRoomState,
) : TogetherMessage

@Serializable
@SerialName("control_request")
@Immutable
data class ControlRequest(
    val sessionId: String,
    val participantId: String,
    val action: ControlAction,
) : TogetherMessage

@Serializable
@SerialName("add_track_request")
@Immutable
data class AddTrackRequest(
    val sessionId: String,
    val participantId: String,
    val track: TogetherTrack,
    val mode: AddTrackMode,
) : TogetherMessage

@Serializable
@SerialName("join_decision")
@Immutable
data class JoinDecision(
    val sessionId: String,
    val participantId: String,
    val approved: Boolean,
) : TogetherMessage

@Serializable
@SerialName("join_request")
@Immutable
data class JoinRequest(
    val sessionId: String,
    val participant: TogetherParticipant,
) : TogetherMessage

@Serializable
@SerialName("participant_joined")
@Immutable
data class ParticipantJoined(
    val sessionId: String,
    val participant: TogetherParticipant,
) : TogetherMessage

@Serializable
@SerialName("participant_left")
@Immutable
data class ParticipantLeft(
    val sessionId: String,
    val participantId: String,
    val reason: String? = null,
) : TogetherMessage

@Serializable
@SerialName("host_transfer")
@Immutable
data class HostTransfer(
    val sessionId: String,
    val participantId: String,
) : TogetherMessage

@Serializable
@SerialName("host_transferred")
@Immutable
data class HostTransferred(
    val sessionId: String,
    val participantId: String,
) : TogetherMessage

@Serializable
@SerialName("heartbeat_ping")
@Immutable
data class HeartbeatPing(
    val sessionId: String,
    val pingId: Long,
    val clientElapsedRealtimeMs: Long,
) : TogetherMessage

@Serializable
@SerialName("heartbeat_pong")
@Immutable
data class HeartbeatPong(
    val sessionId: String,
    val pingId: Long,
    val clientElapsedRealtimeMs: Long,
    val serverElapsedRealtimeMs: Long,
) : TogetherMessage

@Serializable
@SerialName("client_leave")
@Immutable
data class ClientLeave(
    val sessionId: String,
    val participantId: String,
) : TogetherMessage

@Serializable
@SerialName("kick")
@Immutable
data class KickParticipant(
    val sessionId: String,
    val participantId: String,
    val reason: String? = null,
) : TogetherMessage

@Serializable
@SerialName("ban")
@Immutable
data class BanParticipant(
    val sessionId: String,
    val participantId: String,
    val reason: String? = null,
) : TogetherMessage

@Serializable
enum class ServerRole {
    HOST,
    GUEST,
}

@Serializable
enum class AddTrackMode {
    PLAY_NEXT,
    ADD_TO_QUEUE,
}

@Serializable
sealed interface ControlAction {
    @Serializable
    @SerialName("play")
    data object Play : ControlAction

    @Serializable
    @SerialName("pause")
    data object Pause : ControlAction

    @Serializable
    @SerialName("seek_to")
    data class SeekTo(
        val positionMs: Long,
    ) : ControlAction

    @Serializable
    @SerialName("skip_next")
    data object SkipNext : ControlAction

    @Serializable
    @SerialName("skip_previous")
    data object SkipPrevious : ControlAction

    @Serializable
    @SerialName("seek_to_index")
    data class SeekToIndex(
        val index: Int,
        val positionMs: Long = 0L,
    ) : ControlAction

    @Serializable
    @SerialName("seek_to_track")
    data class SeekToTrack(
        val trackId: String,
        val positionMs: Long = 0L,
    ) : ControlAction

    @Serializable
    @SerialName("set_repeat_mode")
    data class SetRepeatMode(
        val repeatMode: Int,
    ) : ControlAction

    @Serializable
    @SerialName("set_shuffle_enabled")
    data class SetShuffleEnabled(
        val shuffleEnabled: Boolean,
    ) : ControlAction
}
