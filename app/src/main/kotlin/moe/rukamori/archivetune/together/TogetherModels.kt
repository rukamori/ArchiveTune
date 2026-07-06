/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class TogetherTrack(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val durationSec: Int = -1,
    val thumbnailUrl: String? = null,
)

@Serializable
@Immutable
data class TogetherParticipant(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val isPending: Boolean = false,
    val isConnected: Boolean = true,
)

@Serializable
@Immutable
data class TogetherRoomSettings(
    val allowGuestsToAddTracks: Boolean = true,
    val allowGuestsToControlPlayback: Boolean = false,
    val requireHostApprovalToJoin: Boolean = false,
)

@Serializable
@Immutable
data class TogetherRoomState(
    val sessionId: String,
    val hostId: String,
    val participants: List<TogetherParticipant> = emptyList(),
    val settings: TogetherRoomSettings = TogetherRoomSettings(),
    val queue: List<TogetherTrack> = emptyList(),
    val queueHash: String = "",
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val sentAtElapsedRealtimeMs: Long = 0L,
)

@Immutable
sealed class TogetherRole {
    data object Host : TogetherRole()

    data object Guest : TogetherRole()
}

@Immutable
sealed class TogetherSessionState {
    data object Idle : TogetherSessionState()

    data class Hosting(
        val sessionId: String,
        val joinLink: String,
        val localAddressHint: String?,
        val port: Int,
        val settings: TogetherRoomSettings,
        val roomState: TogetherRoomState?,
    ) : TogetherSessionState()

    data class HostingOnline(
        val sessionId: String,
        val code: String,
        val settings: TogetherRoomSettings,
        val roomState: TogetherRoomState?,
    ) : TogetherSessionState()

    data class Joining(
        val joinLink: String,
    ) : TogetherSessionState()

    data class JoiningOnline(
        val code: String,
    ) : TogetherSessionState()

    data class Joined(
        val role: TogetherRole,
        val sessionId: String,
        val selfParticipantId: String,
        val roomState: TogetherRoomState,
    ) : TogetherSessionState()

    data class Error(
        val message: String,
        val recoverable: Boolean = true,
    ) : TogetherSessionState()
}

val TogetherSessionState.isConnectedToSession: Boolean
    get() =
        when (this) {
            is TogetherSessionState.Hosting -> roomState != null

            is TogetherSessionState.HostingOnline -> roomState != null

            is TogetherSessionState.Joined -> true

            TogetherSessionState.Idle,
            is TogetherSessionState.Joining,
            is TogetherSessionState.JoiningOnline,
            is TogetherSessionState.Error,
            -> false
        }
