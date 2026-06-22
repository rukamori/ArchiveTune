/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import androidx.compose.runtime.Immutable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface TogetherClientEvent {
    data class Welcome(
        val welcome: ServerWelcome,
    ) : TogetherClientEvent

    data class RoomState(
        val state: TogetherRoomState,
    ) : TogetherClientEvent

    data class JoinDecision(
        val decision: moe.rukamori.archivetune.together.JoinDecision,
    ) : TogetherClientEvent

    data class HostTransferred(
        val transfer: moe.rukamori.archivetune.together.HostTransferred,
    ) : TogetherClientEvent

    data class ControlRequested(
        val request: ControlRequest,
    ) : TogetherClientEvent

    data class AddTrackRequested(
        val request: AddTrackRequest,
    ) : TogetherClientEvent

    data class ServerIssue(
        val message: String,
        val code: String? = null,
    ) : TogetherClientEvent

    data class Error(
        val message: String,
        val throwable: Throwable? = null,
    ) : TogetherClientEvent

    data class HeartbeatPong(
        val pong: moe.rukamori.archivetune.together.HeartbeatPong,
        val receivedAtElapsedRealtimeMs: Long,
    ) : TogetherClientEvent

    data object Disconnected : TogetherClientEvent
}

@Immutable
sealed class TogetherClientState {
    data object Idle : TogetherClientState()

    data class Connecting(
        val joinInfo: TogetherJoinInfo,
    ) : TogetherClientState()

    data class Connected(
        val session: TogetherJoinInfo,
    ) : TogetherClientState()

    data class ConnectingRemote(
        val wsUrl: String,
        val sessionId: String,
    ) : TogetherClientState()

    data class ConnectedRemote(
        val wsUrl: String,
        val sessionId: String,
    ) : TogetherClientState()
}

class TogetherClient(
    private val externalScope: CoroutineScope,
    clientId: String = UUID.randomUUID().toString(),
    private val bearerToken: String? = null,
) {
    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    pingInterval(25, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
            install(WebSockets) {
                pingIntervalMillis = 25_000
            }
        }

    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow<TogetherClientState>(TogetherClientState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<TogetherClientEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private var session: WebSocketSession? = null
    private var loopJob: Job? = null
    private var selfParticipantId: String? = null
    private val clientId = clientId.trim().ifBlank { UUID.randomUUID().toString() }.take(64)
    private val normalizedBearerToken: String? = bearerToken?.trim()?.takeIf { it.isNotBlank() }

    fun connect(
        joinInfo: TogetherJoinInfo,
        displayName: String,
    ) {
        scope.launch {
            disconnect()
            _state.value = TogetherClientState.Connecting(joinInfo)

            val wsUrl = joinInfo.toWebSocketUrl()
            val urls = listOfNotNull(wsUrl, alternateWebSocketSchemeOrNull(wsUrl)).distinct()

            val token = normalizedBearerToken

            var lastError: Throwable? = null
            for (candidate in urls) {
                try {
                    client.webSocket(
                        urlString = candidate,
                        request = {
                            if (token != null) header("Authorization", "Bearer $token")
                        },
                    ) {
                        session = this
                        val hello =
                            ClientHello(
                                protocolVersion = TogetherProtocolVersion,
                                sessionId = joinInfo.sessionId,
                                sessionKey = joinInfo.sessionKey,
                                clientId = clientId,
                                displayName = displayName.trim(),
                            )
                        send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello))
                        _state.value = TogetherClientState.Connected(joinInfo)
                        runLoop(this, joinInfo.sessionId)
                    }
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            _events.tryEmit(TogetherClientEvent.Error(connectionFailureMessage(lastError), lastError))
            _state.value = TogetherClientState.Idle
        }
    }

    fun connect(
        wsUrl: String,
        sessionId: String,
        sessionKey: String,
        displayName: String,
    ) {
        scope.launch {
            disconnect()
            _state.value = TogetherClientState.ConnectingRemote(wsUrl = wsUrl, sessionId = sessionId)

            val urls = listOfNotNull(wsUrl.trim(), alternateWebSocketSchemeOrNull(wsUrl.trim())).distinct()

            val token = normalizedBearerToken

            var lastError: Throwable? = null
            for (candidate in urls) {
                try {
                    client.webSocket(
                        urlString = candidate,
                        request = {
                            if (token != null) header("Authorization", "Bearer $token")
                        },
                    ) {
                        session = this
                        val hello =
                            ClientHello(
                                protocolVersion = TogetherProtocolVersion,
                                sessionId = sessionId,
                                sessionKey = sessionKey,
                                clientId = clientId,
                                displayName = displayName.trim().ifBlank { "Guest" },
                            )
                        send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello))
                        _state.value = TogetherClientState.ConnectedRemote(wsUrl = candidate, sessionId = sessionId)
                        runLoop(this, sessionId)
                    }
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            _events.tryEmit(TogetherClientEvent.Error(connectionFailureMessage(lastError), lastError))
            _state.value = TogetherClientState.Idle
        }
    }

    private fun alternateWebSocketSchemeOrNull(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("ws://") -> "wss://${trimmed.removePrefix("ws://")}"
            trimmed.startsWith("wss://") -> "ws://${trimmed.removePrefix("wss://")}"
            else -> null
        }
    }

    private fun connectionFailureMessage(t: Throwable?): String {
        val root = generateSequence(t) { it.cause }.lastOrNull()
        val raw = root?.message?.trim().orEmpty()
        val reason =
            when (root) {
                is java.net.UnknownHostException -> {
                    "Server not found"
                }

                is java.net.ConnectException -> {
                    "Connection refused"
                }

                is java.net.SocketTimeoutException -> {
                    "Connection timed out"
                }

                is javax.net.ssl.SSLHandshakeException -> {
                    "Secure connection failed"
                }

                is IllegalArgumentException -> {
                    if (raw.contains("ws", ignoreCase = true) &&
                        raw.contains("scheme", ignoreCase = true)
                    ) {
                        "Invalid server websocket URL"
                    } else {
                        null
                    }
                }

                else -> {
                    null
                }
            }

        val detail = reason ?: raw.takeIf { it.isNotBlank() }
        return if (detail == null) "Connection failed" else "Connection failed: $detail"
    }

    suspend fun disconnect() {
        loopJob?.cancel()
        loopJob?.cancelAndJoin()
        loopJob = null
        runCatching { session?.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnect")) }
        session = null
        selfParticipantId = null
        _state.value = TogetherClientState.Idle
    }

    fun requestControl(
        sessionId: String,
        action: ControlAction,
    ) {
        val pid = selfParticipantId ?: return
        scope.launch {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    ControlRequest(sessionId = sessionId, participantId = pid, action = action),
                ),
            )
        }
    }

    fun requestAddTrack(
        sessionId: String,
        track: TogetherTrack,
        mode: AddTrackMode,
    ) {
        val pid = selfParticipantId ?: return
        scope.launch {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    AddTrackRequest(sessionId = sessionId, participantId = pid, track = track, mode = mode),
                ),
            )
        }
    }

    fun sendRoomState(state: TogetherRoomState) {
        scope.launch {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    RoomStateMessage(state),
                ),
            )
        }
    }

    fun transferHostOwnership(
        sessionId: String,
        participantId: String,
    ) {
        scope.launch {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    HostTransfer(sessionId = sessionId, participantId = participantId),
                ),
            )
        }
    }

    fun sendHeartbeat(
        sessionId: String,
        pingId: Long,
        clientElapsedRealtimeMs: Long,
    ) {
        scope.launch {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    HeartbeatPing(
                        sessionId = sessionId,
                        pingId = pingId,
                        clientElapsedRealtimeMs = clientElapsedRealtimeMs,
                    ),
                ),
            )
        }
    }

    private suspend fun runLoop(
        session: WebSocketSession,
        sessionId: String,
    ) {
        loopJob =
            scope.launch {
                try {
                    while (true) {
                        val frame =
                            try {
                                session.incoming.receive()
                            } catch (_: ClosedReceiveChannelException) {
                                break
                            }

                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val message =
                            runCatching { TogetherJson.json.decodeFromString(TogetherMessage.serializer(), text) }
                                .getOrElse {
                                    _events.tryEmit(TogetherClientEvent.Error("Failed to decode message", it))
                                    continue
                                }

                        when (message) {
                            is ServerWelcome -> {
                                if (message.sessionId == sessionId) {
                                    selfParticipantId = message.participantId
                                    _events.tryEmit(TogetherClientEvent.Welcome(message))
                                }
                            }

                            is RoomStateMessage -> {
                                if (message.state.sessionId == sessionId) {
                                    _events.tryEmit(TogetherClientEvent.RoomState(message.state))
                                }
                            }

                            is moe.rukamori.archivetune.together.JoinDecision -> {
                                if (message.sessionId == sessionId && message.participantId == selfParticipantId) {
                                    _events.tryEmit(TogetherClientEvent.JoinDecision(message))
                                }
                            }

                            is moe.rukamori.archivetune.together.HostTransferred -> {
                                if (message.sessionId == sessionId) {
                                    _events.tryEmit(TogetherClientEvent.HostTransferred(message))
                                }
                            }

                            is KickParticipant -> {
                                if (message.sessionId == sessionId && message.participantId == selfParticipantId) {
                                    val detail =
                                        message.reason
                                            ?.trim()
                                            .orEmpty()
                                            .ifBlank { "Kicked" }
                                    _events.tryEmit(TogetherClientEvent.Error(detail, null))
                                    break
                                }
                            }

                            is BanParticipant -> {
                                if (message.sessionId == sessionId && message.participantId == selfParticipantId) {
                                    val detail =
                                        message.reason
                                            ?.trim()
                                            .orEmpty()
                                            .ifBlank { "Banned" }
                                    _events.tryEmit(TogetherClientEvent.Error(detail, null))
                                    break
                                }
                            }

                            is HeartbeatPong -> {
                                if (message.sessionId == sessionId) {
                                    _events.tryEmit(
                                        TogetherClientEvent.HeartbeatPong(
                                            pong = message,
                                            receivedAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                        ),
                                    )
                                }
                            }

                            is ControlRequest -> {
                                if (message.sessionId == sessionId) {
                                    _events.tryEmit(TogetherClientEvent.ControlRequested(message))
                                }
                            }

                            is AddTrackRequest -> {
                                if (message.sessionId == sessionId) {
                                    _events.tryEmit(TogetherClientEvent.AddTrackRequested(message))
                                }
                            }

                            is ServerError -> {
                                _events.tryEmit(TogetherClientEvent.ServerIssue(message = message.message, code = message.code))
                            }

                            else -> {
                                Unit
                            }
                        }
                    }
                } catch (t: Throwable) {
                    _events.tryEmit(TogetherClientEvent.Error("Connection loop failed", t))
                } finally {
                    _events.tryEmit(TogetherClientEvent.Disconnected)
                    _state.value = TogetherClientState.Idle
                }
            }
        loopJob?.join()
    }
}
