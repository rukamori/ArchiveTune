/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(UnstableApi::class)

package moe.rukamori.archivetune.cast

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class DefaultCastPlaybackRepository(
    context: Context,
) : CastPlaybackRepository {
    private val appContext = context.applicationContext
    private val localMediaServer = LocalCastMediaServer(appContext)
    private val mutableScreenState =
        MutableStateFlow<CastScreenState>(
            CastScreenState.Success(
                CastUiState(
                    isAvailable = true,
                    isConnected = false,
                    device = null,
                    volume = 1f,
                ),
            ),
        )
    private var castContext: CastContext? = null
    private var listenerRegistered = false

    override val screenState: StateFlow<CastScreenState> = mutableScreenState

    override fun createPlayer(
        context: Context,
        localPlayer: ExoPlayer,
        mediaItemResolver: CastMediaItemResolver,
    ): Player {
        val contextResult = castContext(context)
        if (contextResult == null) {
            mutableScreenState.value = CastScreenState.Empty
            return localPlayer
        }
        registerSessionListener(contextResult)
        val converter =
            GmsCastMediaItemConverter(
                mediaItemResolver = mediaItemResolver,
                localMediaServer = localMediaServer,
            )
        val remotePlayer =
            RemoteCastPlayer
                .Builder(context.applicationContext)
                .setMediaItemConverter(converter)
                .build()
        return CastPlayer
            .Builder(context.applicationContext)
            .setLocalPlayer(localPlayer)
            .setRemotePlayer(remotePlayer)
            .build()
    }

    override fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    override fun setVolume(volume: Float) {
        castContext?.sessionManager?.currentCastSession?.let { session ->
            runCatching { session.setVolume(volume.coerceIn(0f, 1f).toDouble()) }
                .onFailure { Timber.tag("Cast").w(it, "Unable to set Cast volume") }
        }
    }

    private fun castContext(context: Context): CastContext? =
        castContext ?: runCatching {
            CastContext.getSharedInstance(context.applicationContext)
        }.onFailure {
            Timber.tag("Cast").w(it, "Unable to initialize CastContext")
        }.getOrNull()?.also { castContext = it }

    private fun registerSessionListener(context: CastContext) {
        if (listenerRegistered) return
        context.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        listenerRegistered = true
        updateState(context.sessionManager.currentCastSession)
    }

    private val sessionListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) = updateState(session)

            override fun onSessionStarted(
                session: CastSession,
                sessionId: String,
            ) = updateState(session)

            override fun onSessionStartFailed(
                session: CastSession,
                error: Int,
            ) {
                localMediaServer.stop()
                updateState(null)
            }

            override fun onSessionEnding(session: CastSession) = updateState(session)

            override fun onSessionEnded(
                session: CastSession,
                error: Int,
            ) {
                localMediaServer.stop()
                updateState(null)
            }

            override fun onSessionResuming(
                session: CastSession,
                sessionId: String,
            ) = updateState(session)

            override fun onSessionResumed(
                session: CastSession,
                wasSuspended: Boolean,
            ) = updateState(session)

            override fun onSessionResumeFailed(
                session: CastSession,
                error: Int,
            ) {
                localMediaServer.stop()
                updateState(null)
            }

            override fun onSessionSuspended(
                session: CastSession,
                reason: Int,
            ) = updateState(session)
        }

    private fun updateState(session: CastSession?) {
        val device = session?.castDevice
        mutableScreenState.value =
            CastScreenState.Success(
                CastUiState(
                    isAvailable = true,
                    isConnected = session?.isConnected == true,
                    device =
                        device?.friendlyName?.let { name ->
                            CastDeviceUiModel(
                                id = device.deviceId ?: name,
                                name = name,
                            )
                        },
                    volume = session?.volume?.toFloat()?.coerceIn(0f, 1f) ?: 1f,
                ),
            )
    }
}

private class GmsCastMediaItemConverter(
    private val mediaItemResolver: CastMediaItemResolver,
    private val localMediaServer: LocalCastMediaServer,
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val castMediaItem = mediaItem.resolveForReceiver()
        return delegate.toMediaQueueItem(castMediaItem)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)

    private fun MediaItem.resolveForReceiver(): MediaItem {
        val uri = localConfiguration?.uri ?: return this
        if (uri.isHttpUrl()) return this
        if (uri.isLocalFileUrl()) {
            localMediaServer.prepare(this)?.let { return it }
        }
        val resolved = mediaItemResolver.resolveForCast(this)
        val resolvedUri = resolved.localConfiguration?.uri
        return if (resolvedUri != null && !resolvedUri.isHttpUrl() && resolvedUri.isLocalFileUrl()) {
            localMediaServer.prepare(resolved) ?: resolved
        } else {
            resolved
        }
    }

    private fun Uri.isHttpUrl(): Boolean {
        val normalizedScheme = scheme?.lowercase(Locale.US)
        return normalizedScheme == "http" || normalizedScheme == "https"
    }

    private fun Uri.isLocalFileUrl(): Boolean {
        val normalizedScheme = scheme?.lowercase(Locale.US)
        return normalizedScheme == "content" ||
            normalizedScheme == "file" ||
            normalizedScheme == "android.resource"
    }
}

private class LocalCastMediaServer(
    private val context: Context,
) {
    private val servedItems = ConcurrentHashMap<String, ServedItem>()
    private var engine: EmbeddedServer<*, *>? = null
    private var port: Int = 0
    private var hostAddress: String? = null

    fun prepare(mediaItem: MediaItem): MediaItem? {
        val uri = mediaItem.localConfiguration?.uri ?: return null
        val host = ensureStarted() ?: return null
        val token = UUID.randomUUID().toString()
        val mimeType = mediaItem.localConfiguration?.mimeType ?: context.contentResolver.getType(uri)
        servedItems[token] = ServedItem(uri = uri, mimeType = mimeType)
        return mediaItem
            .buildUpon()
            .setUri("http://$host:$port/cast/local/$token".toUri())
            .setMimeType(mimeType)
            .build()
    }

    fun stop() {
        servedItems.clear()
        val currentEngine = engine ?: return
        engine = null
        runCatching { currentEngine.stop(1000, 2000) }
            .onFailure { Timber.tag("Cast").w(it, "Unable to stop local Cast media server") }
    }

    private fun ensureStarted(): String? {
        hostAddress?.let { return it }
        val address = lanAddress() ?: return null
        val selectedPort = randomFreePort()
        val startedEngine =
            runCatching {
                embeddedServer(CIO, port = selectedPort, host = "0.0.0.0") {
                    routing {
                        get("/cast/local/{token}") {
                            val token = call.parameters["token"]
                            val item = token?.let(servedItems::get)
                            if (item == null) {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            val source = openSource(item.uri)
                            if (source == null) {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            source.use { nonNullSource ->
                                val range = parseRange(call.request.header(HttpHeaders.Range), nonNullSource.length)
                                val start = range?.first ?: 0L
                                val end = range?.second ?: nonNullSource.length?.minus(1L)
                                val status = if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent
                                val contentLength = end?.let { it - start + 1L }
                                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                nonNullSource.length?.let { call.response.header(HttpHeaders.ContentLength, (contentLength ?: it).toString()) }
                                if (range != null && end != null && nonNullSource.length != null) {
                                    call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/${nonNullSource.length}")
                                }
                                call.respondOutputStream(
                                    contentType = ContentType.parse(item.mimeType ?: "application/octet-stream"),
                                    status = status,
                                ) {
                                    nonNullSource.input.skipFully(start)
                                    nonNullSource.input.copyToBounded(this, contentLength)
                                }
                            }
                        }
                    }
                }.also { it.start(wait = false) }
            }.onFailure {
                Timber.tag("Cast").w(it, "Unable to start local Cast media server")
            }.getOrNull() ?: return null
        engine = startedEngine
        port = selectedPort
        hostAddress = address
        return address
    }

    private fun openSource(uri: Uri): OpenSource? {
        val normalizedScheme = uri.scheme?.lowercase(Locale.US)
        return when (normalizedScheme) {
            "file" -> {
                val file = File(uri.path ?: return null).takeIf { it.isFile } ?: return null
                OpenSource(input = file.inputStream(), length = file.length())
            }

            "content", "android.resource" -> {
                val input = context.contentResolver.openInputStream(uri) ?: return null
                OpenSource(input = input, length = contentLength(uri))
            }

            else -> null
        }
    }

    private fun contentLength(uri: Uri): Long? =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it >= 0L }
            }
        }.getOrNull()

    private fun parseRange(
        header: String?,
        length: Long?,
    ): Pair<Long, Long?>? {
        if (header.isNullOrBlank() || !header.startsWith("bytes=")) return null
        val range = header.removePrefix("bytes=").substringBefore(",")
        val startText = range.substringBefore("-")
        val endText = range.substringAfter("-", "")
        val rawStart = startText.toLongOrNull() ?: return null
        val start = max(0L, rawStart)
        val end =
            endText
                .toLongOrNull()
                ?.let { requestedEnd -> length?.let { min(requestedEnd, it - 1L) } ?: requestedEnd }
        if (end != null && end < start) return null
        return start to end
    }

    private fun lanAddress(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface -> networkInterface.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .sortedByDescending { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .firstOrNull()
    }

    private fun randomFreePort(): Int =
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }

    private data class ServedItem(
        val uri: Uri,
        val mimeType: String?,
    )

    private data class OpenSource(
        val input: InputStream,
        val length: Long?,
    ) : AutoCloseable {
        override fun close() = input.close()
    }

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped <= 0L) {
                if (read() == -1) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun InputStream.copyToBounded(
        output: java.io.OutputStream,
        limit: Long?,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = limit
        while (remaining == null || remaining > 0L) {
            val readLimit = remaining?.let { min(buffer.size.toLong(), it).toInt() } ?: buffer.size
            val read = read(buffer, 0, readLimit)
            if (read < 0) return
            output.write(buffer, 0, read)
            remaining = remaining?.minus(read.toLong())
        }
    }
}
