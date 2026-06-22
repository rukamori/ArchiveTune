/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface TogetherServerEvent {
    data class JoinRequested(
        val participant: TogetherParticipant,
    ) : TogetherServerEvent

    data class ParticipantJoined(
        val participant: TogetherParticipant,
    ) : TogetherServerEvent

    data class ParticipantLeft(
        val participantId: String,
        val reason: String?,
    ) : TogetherServerEvent

    data class ControlRequested(
        val request: ControlRequest,
    ) : TogetherServerEvent

    data class AddTrackRequested(
        val request: AddTrackRequest,
    ) : TogetherServerEvent

    data class RoomStateReceived(
        val state: TogetherRoomState,
    ) : TogetherServerEvent

    data class HostTransferred(
        val participantId: String,
    ) : TogetherServerEvent

    data class Error(
        val message: String,
        val throwable: Throwable? = null,
    ) : TogetherServerEvent
}

class TogetherServer(
    private val scope: CoroutineScope,
    val sessionId: String,
    private val sessionKey: String,
    private val hostDisplayName: String,
    initialSettings: TogetherRoomSettings,
    private val hostParticipantId: String = "host",
) {
    private val mutex = Mutex()
    private var settings: TogetherRoomSettings = initialSettings
    private var engine: EmbeddedServer<*, *>? = null
    private var authorityParticipantId: String? = null

    @Volatile
    private var lastParticipants: List<TogetherParticipant> = emptyList()

    private data class Client(
        val participantId: String,
        val clientId: String,
        val name: String,
        val session: WebSocketSession,
        var pending: Boolean,
    )

    private val clients = ConcurrentHashMap<String, Client>()

    var onEvent: ((TogetherServerEvent) -> Unit)? = null

    fun currentParticipants(): List<TogetherParticipant> = lastParticipants

    suspend fun currentSettings(): TogetherRoomSettings = mutex.withLock { settings }

    suspend fun start(port: Int) {
        mutex.withLock {
            if (engine != null) return
            engine =
                embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    install(WebSockets)
                    routing {
                        webSocket("/together") {
                            handleClient()
                        }
                    }
                }.also { it.start(wait = false) }
        }
    }

    suspend fun stop() {
        val eng =
            mutex.withLock {
                val e = engine ?: return
                engine = null
                e
            }

        clients.values.forEach { client ->
            runCatching { client.session.close(CloseReason(CloseReason.Codes.NORMAL, "Session ended")) }
        }
        clients.clear()

        runCatching { eng.stop(1000, 2000) }
    }

    suspend fun updateSettings(newSettings: TogetherRoomSettings) {
        mutex.withLock {
            settings = newSettings
        }
    }

    suspend fun transferHostOwnership(participantId: String) {
        val target = clients[participantId] ?: return
        if (target.pending) return
        mutex.withLock {
            authorityParticipantId = participantId
        }
        broadcastHostTransferred(participantId)
        onEvent?.invoke(TogetherServerEvent.HostTransferred(participantId))
    }

    suspend fun approveParticipant(
        participantId: String,
        approved: Boolean,
    ) {
        val client = clients[participantId] ?: return
        if (!client.pending) return

        if (!approved) {
            runCatching {
                client.session.send(
                    TogetherJson.json.encodeToString(
                        TogetherMessage.serializer(),
                        JoinDecision(sessionId = sessionId, participantId = participantId, approved = false),
                    ),
                )
            }
            runCatching { client.session.close(CloseReason(CloseReason.Codes.NORMAL, "Not approved")) }
            clients.remove(participantId)
            onEvent?.invoke(TogetherServerEvent.ParticipantLeft(participantId, "Not approved"))
            return
        }

        client.pending = false
        runCatching {
            client.session.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    JoinDecision(sessionId = sessionId, participantId = participantId, approved = true),
                ),
            )
        }
        onEvent?.invoke(
            TogetherServerEvent.ParticipantJoined(
                TogetherParticipant(
                    id = participantId,
                    name = client.name,
                    isHost = false,
                    isPending = false,
                    isConnected = true,
                ),
            ),
        )
    }

    suspend fun broadcastRoomState(state: TogetherRoomState) {
        val snapshot =
            mutex.withLock {
                settings to authorityParticipantId
            }
        val snapshotSettings = snapshot.first
        val activeHostId = snapshot.second ?: hostParticipantId
        val host =
            TogetherParticipant(
                id = hostParticipantId,
                name = hostDisplayName,
                isHost = activeHostId == hostParticipantId,
                isPending = false,
                isConnected = true,
            )

        val participantList =
            buildList {
                add(host)
                addAll(
                    clients.values
                        .sortedBy { it.name.lowercase() }
                        .map {
                            TogetherParticipant(
                                id = it.participantId,
                                name = it.name,
                                isHost = it.participantId == activeHostId,
                                isPending = it.pending,
                                isConnected = true,
                            )
                        },
                )
            }
        lastParticipants = participantList

        val baseState =
            state.copy(
                hostId = activeHostId,
                participants = participantList,
                settings = snapshotSettings,
            )

        clients.values.forEach { client ->
            val safeState =
                if (client.pending) {
                    baseState.copy(
                        queue = emptyList(),
                        queueHash = "",
                        currentIndex = 0,
                        isPlaying = false,
                        positionMs = 0L,
                    )
                } else {
                    baseState
                }

            runCatching {
                client.session.send(
                    TogetherJson.json.encodeToString(
                        TogetherMessage.serializer(),
                        RoomStateMessage(safeState),
                    ),
                )
            }
        }
    }

    private suspend fun broadcastHostTransferred(participantId: String) {
        val message =
            TogetherJson.json.encodeToString(
                TogetherMessage.serializer(),
                HostTransferred(sessionId = sessionId, participantId = participantId),
            )
        clients.values.forEach { client ->
            runCatching { client.session.send(message) }
        }
    }

    private suspend fun sendToAuthority(message: TogetherMessage): Boolean {
        val authorityId = mutex.withLock { authorityParticipantId } ?: return false
        val authority = clients[authorityId] ?: return false
        if (authority.pending) return false
        runCatching {
            authority.session.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    message,
                ),
            )
        }.getOrElse {
            return false
        }
        return true
    }

    private suspend fun WebSocketSession.handleClient() {
        val helloText =
            try {
                incoming.receive() as? Frame.Text
            } catch (_: ClosedReceiveChannelException) {
                null
            }

        val hello =
            runCatching {
                val txt = helloText?.readText().orEmpty()
                val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), txt)
                decoded as? ClientHello
            }.getOrNull()

        if (hello == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Handshake required"))
            return
        }

        if (hello.protocolVersion != TogetherProtocolVersion) {
            send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    ServerError(sessionId = hello.sessionId, message = "Unsupported protocol version"),
                ),
            )
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unsupported protocol"))
            return
        }

        if (hello.sessionId != sessionId || hello.sessionKey != sessionKey) {
            send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    ServerError(sessionId = hello.sessionId, message = "Invalid session"),
                ),
            )
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid session"))
            return
        }

        val participantId = UUID.randomUUID().toString()
        val isPending = mutex.withLock { settings.requireHostApprovalToJoin }

        val client =
            Client(
                participantId = participantId,
                clientId = hello.clientId,
                name = hello.displayName.trim().ifBlank { "Guest" },
                session = this,
                pending = isPending,
            )

        clients[participantId] = client

        val welcome =
            ServerWelcome(
                protocolVersion = TogetherProtocolVersion,
                sessionId = sessionId,
                participantId = participantId,
                role = ServerRole.GUEST,
                isPending = isPending,
                settings = mutex.withLock { settings },
            )

        send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), welcome))

        val participant =
            TogetherParticipant(
                id = participantId,
                name = client.name,
                isHost = false,
                isPending = isPending,
                isConnected = true,
            )

        onEvent?.invoke(
            if (isPending) TogetherServerEvent.JoinRequested(participant) else TogetherServerEvent.ParticipantJoined(participant),
        )

        try {
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val message =
                    runCatching { TogetherJson.json.decodeFromString(TogetherMessage.serializer(), text) }
                        .getOrElse {
                            onEvent?.invoke(TogetherServerEvent.Error("Failed to decode message", it))
                            continue
                        }

                when (message) {
                    is RoomStateMessage -> {
                        val isAuthority = mutex.withLock { authorityParticipantId == participantId }
                        if (isAuthority && message.state.sessionId == sessionId) {
                            val roomState = message.state.copy(hostId = participantId)
                            broadcastRoomState(roomState)
                            onEvent?.invoke(TogetherServerEvent.RoomStateReceived(roomState))
                        }
                    }

                    is HeartbeatPing -> {
                        send(
                            TogetherJson.json.encodeToString(
                                TogetherMessage.serializer(),
                                HeartbeatPong(
                                    sessionId = sessionId,
                                    pingId = message.pingId,
                                    clientElapsedRealtimeMs = message.clientElapsedRealtimeMs,
                                    serverElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                ),
                            ),
                        )
                    }

                    is ControlRequest -> {
                        if (!client.pending) {
                            if (!sendToAuthority(message)) {
                                onEvent?.invoke(TogetherServerEvent.ControlRequested(message))
                            }
                        }
                    }

                    is AddTrackRequest -> {
                        if (!client.pending) {
                            if (!sendToAuthority(message)) {
                                onEvent?.invoke(TogetherServerEvent.AddTrackRequested(message))
                            }
                        }
                    }

                    is ClientLeave -> {
                        if (message.participantId == participantId) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Left"))
                            break
                        }
                    }

                    is HostTransfer -> {
                        val canTransfer = mutex.withLock { authorityParticipantId == participantId }
                        if (canTransfer && message.sessionId == sessionId) {
                            transferHostOwnership(message.participantId)
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        } catch (t: Throwable) {
            onEvent?.invoke(TogetherServerEvent.Error("Client loop failed", t))
        } finally {
            clients.remove(participantId)
            onEvent?.invoke(TogetherServerEvent.ParticipantLeft(participantId, "Disconnected"))
            runCatching { close() }
        }
    }
}
