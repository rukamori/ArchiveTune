/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.discord

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private sealed class GatewayFrame {
    data class Text(
        val text: String,
    ) : GatewayFrame()

    data class Close(
        val code: Int,
        val reason: String,
    ) : GatewayFrame()
}

class GatewayClient {
    companion object {
        private const val TAG = "GatewayClient"
    }

    @Volatile private var httpClient: OkHttpClient? = null

    @Volatile private var wsSession: WebSocket? = null

    @Volatile private var processingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var helloTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingChannel = Channel<GatewayFrame>(Channel.UNLIMITED)

    private var lastAck = true
    private var lastHeartbeatAt = 0L
    private var ping = -1

    private var sessionState: GatewaySessionState? = null
    private var liveSeq = 0
    private var token = ""

    @Volatile private var closed = false

    @Volatile private var readyReceived = false
    private val connectionGeneration = AtomicLong(0L)

    @Volatile private var activeConnectionGeneration: Long = 0L

    var onReady: ((GatewayReadyEvent) -> Unit)? = null
    var onClose: ((GatewayCloseInfo) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onDebug: ((String) -> Unit)? = null

    val latency: Int get() = ping

    fun isConnected(): Boolean = wsSession != null && !closed && processingJob?.isActive == true

    fun isReady(): Boolean = isConnected() && readyReceived

    suspend fun connect(accessToken: String) {
        if (wsSession != null) throw IllegalStateException("GatewayClient already connected")

        token = "Bearer $accessToken"
        sessionState = null
        liveSeq = 0
        closed = false
        readyReceived = false
        lastAck = true

        val url = "${GatewayDefaults.GATEWAY_URL}/?v=${GatewayDefaults.GATEWAY_VERSION}&encoding=json"
        val ready = CompletableDeferred<Unit>()

        debug("connecting $url")

        httpClient =
            OkHttpClient
                .Builder()
                .build()

        val generation = connectionGeneration.incrementAndGet()
        activeConnectionGeneration = generation

        val request =
            Request
                .Builder()
                .url(url)
                .build()

        wsSession =
            httpClient!!.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        response.close()
                        if (!isActiveGeneration(generation)) {
                            webSocket.close(1000, "stale")
                            return
                        }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        if (!isActiveGeneration(generation)) return
                        publishFrame(GatewayFrame.Text(text))
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!isActiveGeneration(generation)) return
                        publishFrame(GatewayFrame.Close(code, reason))
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        if (!isActiveGeneration(generation)) return
                        publishFrame(GatewayFrame.Close(code, reason))
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (!isActiveGeneration(generation)) return
                        response?.close()
                        onError?.invoke(t)
                        publishFrame(
                            GatewayFrame.Close(
                                response?.code ?: 4000,
                                t.message ?: "failure",
                            ),
                        )
                    }
                },
            )

        helloTimerJob =
            scope.launch {
                delay(GatewayDefaults.HELLO_TIMEOUT_MS)
                debug("HELLO timeout")
                forceClose(4009, "HELLO timeout")
                ready.completeExceptionally(Exception("HELLO timeout"))
            }

        processingJob =
            scope.launch {
                try {
                    for (frame in incomingChannel) {
                        when (frame) {
                            is GatewayFrame.Text -> {
                                handleMessage(frame.text, ready)
                            }

                            is GatewayFrame.Close -> {
                                handleClose(frame.reason, frame.code)
                                if (!ready.isCompleted) {
                                    ready.completeExceptionally(
                                        Exception("Gateway closed before ready"),
                                    )
                                }
                                return@launch
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!ready.isCompleted) ready.completeExceptionally(e)
                    onError?.invoke(e)
                }
            }

        ready.await()
    }

    fun sendPresenceUpdate(presenceJson: JSONObject): Boolean = sendFrame(GatewayOp.PRESENCE_UPDATE, presenceJson, requireReady = true)

    fun disconnect() {
        shutdownTransport(
            closeCode = 1000,
            closeReason = "Client disconnect",
            cancelProcessing = true,
        )
    }

    private fun send(
        op: Int,
        d: Any?,
    ): Boolean = sendFrame(op, d, requireReady = false)

    private fun sendFrame(
        op: Int,
        d: Any?,
        requireReady: Boolean,
    ): Boolean {
        if (closed || wsSession == null || processingJob?.isActive != true) {
            debug("sendFrame skipped: not connected op=$op")
            return false
        }
        if (requireReady && !readyReceived) {
            debug("sendFrame skipped: gateway not ready op=$op")
            return false
        }

        val session = wsSession ?: return false
        return try {
            val sent = session.send(buildJsonString(op, d))
            if (!sent) {
                debug("sendFrame failed: WebSocket.send returned false op=$op")
            }
            sent
        } catch (e: Exception) {
            onError?.invoke(e)
            false
        }
    }

    private fun buildJsonString(
        op: Int,
        d: Any?,
    ): String {
        val json = JSONObject()
        json.put("op", op)
        when (d) {
            is JSONObject -> json.put("d", d)
            is JSONArray -> json.put("d", d)
            is Map<*, *> -> json.put("d", JSONObject(d as Map<*, *>))
            is Int -> json.put("d", d)
            is String -> json.put("d", d)
            is Boolean -> json.put("d", d)
            null -> json.put("d", JSONObject.NULL)
            else -> json.put("d", d.toString())
        }
        return json.toString()
    }

    private fun handleMessage(
        raw: String,
        ready: CompletableDeferred<Unit>,
    ) {
        try {
            val json = JSONObject(raw)
            val op = json.getInt("op")
            val d = json.opt("d")
            val s = if (json.has("s") && !json.isNull("s")) json.getInt("s") else null
            val t = json.optString("t", null)

            if (s != null && s > liveSeq) {
                liveSeq = s
                touchSession(seq = s)
            }

            when (op) {
                GatewayOp.HELLO -> {
                    helloTimerJob?.cancel()
                    helloTimerJob = null
                    val dObj = d as JSONObject
                    val interval = dObj.getInt("heartbeat_interval")
                    startHeartbeat(interval.toLong())
                    debug("HELLO received, heartbeat_interval=${interval}ms")
                    sendIdentify()
                }

                GatewayOp.HEARTBEAT_ACK -> {
                    lastAck = true
                    ping = (System.currentTimeMillis() - lastHeartbeatAt).toInt()
                    debug("heartbeat ack (${ping}ms)")
                }

                GatewayOp.HEARTBEAT -> {
                    debug("received server heartbeat")
                    sendHeartbeat(force = true)
                }

                GatewayOp.RECONNECT -> {
                    debug("server requested RECONNECT")
                    forceClose(4000, "server reconnect")
                }

                GatewayOp.INVALID_SESSION -> {
                    val resumable = if (d is Boolean) d else false
                    debug("INVALID_SESSION resumable=$resumable")
                    if (!resumable) {
                        sessionState = null
                        touchSession(sessionId = null, resumeGatewayUrl = null, seq = 0)
                    }
                    forceClose(if (resumable) 4000 else 1000, "invalid session")
                }

                GatewayOp.DISPATCH -> {
                    handleDispatch(t ?: "", d, s, ready)
                }
            }
        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }

    private fun handleDispatch(
        t: String,
        d: Any?,
        s: Int?,
        ready: CompletableDeferred<Unit>,
    ) {
        when (t) {
            "READY" -> {
                val obj = d as JSONObject
                val re = GatewayReadyEvent.fromJson(obj)
                debug("READY: user=${re.user.username} (${re.user.id}) session=${re.sessionId}")
                sessionState = GatewaySessionState(re.sessionId, liveSeq, re.resumeGatewayUrl)
                touchSession(re.sessionId, liveSeq, re.resumeGatewayUrl)
                readyReceived = true
                ready.complete(Unit)
                onReady?.invoke(re)
            }

            "RESUMED" -> {
                debug("RESUMED: session restored, seq=$liveSeq")
                touchSession(
                    sessionState?.sessionId,
                    liveSeq,
                    sessionState?.resumeGatewayUrl,
                )
                readyReceived = true
                ready.complete(Unit)
            }

            else -> {
                debug("dispatch $t")
            }
        }
    }

    private fun sendIdentify() {
        val capabilities = GatewayCapabilitiesFlags.FLAGS
        val intents = IntentsFlags.FLAGS

        val capsBitfield = capabilities.values.reduce { a, b -> a or b }
        val intentsBitfield = intents.values.reduce { a, b -> a or b }

        val d = JSONObject()
        d.put("capabilities", capsBitfield)
        d.put("intents", intentsBitfield)
        d.put("token", token)
        val properties = JSONObject()
        properties.put("os", "Android")
        properties.put("browser", "ArchiveTune")
        properties.put("device", "Android")
        properties.put("browser_user_agent", "ArchiveTune")
        properties.put("browser_version", "1.0")
        properties.put("client_version", "1.0")
        properties.put("client_build_number", 1)
        properties.put("native_build_number", 1)
        properties.put("release_channel", "unknown")
        d.put("properties", properties)
        debug("sending IDENTIFY")
        send(GatewayOp.IDENTIFY, d)
    }

    private fun startHeartbeat(intervalMs: Long) {
        stopHeartbeat()
        debug("heartbeat every ${intervalMs}ms")
        val firstDelay = (intervalMs * Random.nextDouble()).toLong()
        heartbeatJob =
            scope.launch {
                delay(firstDelay)
                if (wsSession != null) {
                    sendHeartbeat()
                    while (isActive) {
                        delay(intervalMs)
                        sendHeartbeat()
                    }
                }
            }
    }

    private fun sendHeartbeat(force: Boolean = false) {
        if (!force && !lastAck) {
            debug("zombie connection; closing 4009")
            forceClose(4009, "heartbeat ack missed")
            return
        }
        lastAck = false
        lastHeartbeatAt = System.currentTimeMillis()
        val seq = if (liveSeq > 0) liveSeq else null
        send(GatewayOp.HEARTBEAT, seq)
        debug("heartbeat dispatched seq=$seq")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun touchSession(
        sessionId: String? = null,
        seq: Int = liveSeq,
        resumeGatewayUrl: String? = null,
    ) {
    }

    private fun forceClose(
        code: Int,
        reason: String,
    ) {
        val session = wsSession
        if (session == null || !session.close(code, reason)) {
            publishFrame(GatewayFrame.Close(code, reason))
        }
    }

    private fun handleClose(
        reason: String,
        code: Int,
    ) {
        if (closed) return
        val fatal = NON_RESUMABLE_CLOSE_CODES.contains(code)
        val snapshot = sessionState?.copy(seq = liveSeq)
        if (fatal) sessionState = null

        shutdownTransport(
            closeCode = null,
            closeReason = null,
            cancelProcessing = false,
        )

        onClose?.invoke(
            GatewayCloseInfo(
                code = code,
                reason = reason,
                resumable = !fatal && snapshot != null,
                session = snapshot,
            ),
        )
        debug("close code=$code reason=$reason resumable=${!fatal && snapshot != null}")
    }

    private fun shutdownTransport(
        closeCode: Int?,
        closeReason: String?,
        cancelProcessing: Boolean,
    ) {
        closed = true
        readyReceived = false
        stopHeartbeat()
        helloTimerJob?.cancel()
        helloTimerJob = null
        activeConnectionGeneration = connectionGeneration.incrementAndGet()

        val session = wsSession
        wsSession = null
        val client = httpClient
        httpClient = null

        if (cancelProcessing) {
            processingJob?.cancel()
            processingJob = null
        }

        if (closeCode != null) {
            runCatching { session?.close(closeCode, closeReason ?: "") }
        }
        runCatching { client?.dispatcher?.executorService?.shutdown() }
    }

    private fun isActiveGeneration(generation: Long): Boolean = generation == activeConnectionGeneration

    private fun publishFrame(frame: GatewayFrame) {
        val result = incomingChannel.trySend(frame)
        if (result.isFailure) {
            result.exceptionOrNull()?.let { onError?.invoke(it) }
        }
    }

    private fun debug(msg: String) {
        Timber.tag(TAG).v(msg)
        onDebug?.invoke(msg)
    }
}
