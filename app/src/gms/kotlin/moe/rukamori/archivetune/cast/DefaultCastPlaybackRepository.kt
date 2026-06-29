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
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import com.google.android.gms.cast.MediaMetadata as CastMetadata

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
        }.getOrNull()
            ?.also { castContext = it }

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

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
        try {
            delegate.toMediaItem(mediaQueueItem)
        } catch (error: RuntimeException) {
            Timber.tag("Cast").w(error, "Falling back to manual Cast media item conversion")
            mediaQueueItem.toFallbackMediaItem()
        }

    private fun MediaItem.resolveForReceiver(): MediaItem {
        val uri = localConfiguration?.uri ?: return this
        if (uri.isHttpUrl()) return this
        if (uri.isLocalFileUrl()) {
            localMediaServer.prepare(this)?.let { return it }
        }
        localMediaServer.prepare(this, mediaItemResolver)?.let { return it }
        return mediaItemResolver.resolveForCast(this)
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

    private fun MediaQueueItem.toFallbackMediaItem(): MediaItem {
        val mediaInfo = media
        val mediaId =
            mediaInfo?.contentId?.trim().takeUnless { it.isNullOrEmpty() }
                ?: mediaInfo?.contentUrl?.trim().takeUnless { it.isNullOrEmpty() }
                ?: itemId.toString()
        return MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setUri(mediaInfo.resolveFallbackUri(mediaId))
            .setMimeType(mediaInfo?.contentType?.trim()?.takeIf { it.isNotEmpty() })
            .setTag(mediaInfo?.toAppMediaMetadata(mediaId))
            .setMediaMetadata(mediaInfo.toMedia3Metadata(mediaId))
            .build()
    }

    private fun MediaInfo?.resolveFallbackUri(mediaId: String): Uri {
        val contentId = this?.contentId?.trim()
        if (!contentId.isNullOrEmpty()) return contentId.toUri()
        val contentUrl = this?.contentUrl?.trim()
        if (!contentUrl.isNullOrEmpty()) return contentUrl.toUri()
        return mediaId.toUri()
    }

    private fun MediaInfo?.toMedia3Metadata(mediaId: String): androidx.media3.common.MediaMetadata {
        val castMetadata = this?.metadata
        val title = castMetadata?.stringValue(CastMetadata.KEY_TITLE) ?: mediaId
        val subtitle = castMetadata?.stringValue(CastMetadata.KEY_SUBTITLE)
        val artist = castMetadata?.stringValue(CastMetadata.KEY_ARTIST) ?: subtitle
        return androidx.media3.common.MediaMetadata
            .Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(artist)
            .setAlbumArtist(castMetadata?.stringValue(CastMetadata.KEY_ALBUM_ARTIST))
            .setAlbumTitle(castMetadata?.stringValue(CastMetadata.KEY_ALBUM_TITLE))
            .setComposer(castMetadata?.stringValue(CastMetadata.KEY_COMPOSER))
            .setArtworkUri(castMetadata?.images?.firstOrNull()?.url)
            .apply {
                if (castMetadata?.containsKey(CastMetadata.KEY_DISC_NUMBER) == true) {
                    setDiscNumber(castMetadata.getInt(CastMetadata.KEY_DISC_NUMBER))
                }
                if (castMetadata?.containsKey(CastMetadata.KEY_TRACK_NUMBER) == true) {
                    setTrackNumber(castMetadata.getInt(CastMetadata.KEY_TRACK_NUMBER))
                }
            }.setIsPlayable(true)
            .build()
    }

    private fun MediaInfo?.toAppMediaMetadata(mediaId: String): moe.rukamori.archivetune.models.MediaMetadata {
        val castMetadata = this?.metadata
        val title = castMetadata?.stringValue(CastMetadata.KEY_TITLE) ?: mediaId
        val artistText =
            castMetadata?.stringValue(CastMetadata.KEY_ARTIST)
                ?: castMetadata?.stringValue(CastMetadata.KEY_SUBTITLE)
        val albumTitle = castMetadata?.stringValue(CastMetadata.KEY_ALBUM_TITLE)
        return moe.rukamori.archivetune.models.MediaMetadata(
            id = mediaId,
            title = title,
            artists =
                artistText
                    ?.split(",")
                    ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                    ?.map {
                        moe.rukamori.archivetune.models.MediaMetadata
                            .Artist(id = null, name = it)
                    }.orEmpty(),
            duration = -1,
            thumbnailUrl =
                castMetadata
                    ?.images
                    ?.firstOrNull()
                    ?.url
                    ?.toString(),
            album =
                albumTitle?.let {
                    moe.rukamori.archivetune.models.MediaMetadata.Album(
                        id = it,
                        title = it,
                    )
                },
        )
    }

    private fun CastMetadata.stringValue(key: String): String? =
        if (containsKey(key)) {
            getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
}

private class LocalCastMediaServer(
    private val context: Context,
) {
    private companion object {
        const val HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416
        const val REMOTE_CONNECT_TIMEOUT_MS = 15_000
        const val REMOTE_READ_TIMEOUT_MS = 45_000
        const val DEFAULT_AUDIO_MIME_TYPE = "audio/mp4"
        const val DEFAULT_IMAGE_MIME_TYPE = "image/jpeg"
    }

    private val servedItems = ConcurrentHashMap<String, ServedItem>()
    private val servedArtwork = ConcurrentHashMap<String, ServedAsset>()
    private var engine: EmbeddedServer<*, *>? = null
    private var port: Int = 0
    private var hostAddress: String? = null

    fun prepare(
        mediaItem: MediaItem,
        mediaItemResolver: CastMediaItemResolver? = null,
    ): MediaItem? {
        val uri = mediaItem.localConfiguration?.uri ?: return null
        val host = ensureStarted() ?: return null
        val token = UUID.randomUUID().toString()
        val mimeType = mediaItem.localConfiguration?.mimeType ?: mimeType(uri, DEFAULT_AUDIO_MIME_TYPE)
        servedItems[token] =
            ServedItem(
                mediaItem = mediaItem,
                uri = uri,
                mimeType = mimeType,
                mediaItemResolver = mediaItemResolver,
            )
        return mediaItem
            .withReceiverArtwork(host)
            .buildUpon()
            .setUri("http://$host:$port/cast/local/$token".toUri())
            .setMimeType(mimeType)
            .build()
    }

    fun stop() {
        servedItems.clear()
        servedArtwork.clear()
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
                            val source = openSource(item, call.request.header(HttpHeaders.Range))
                            if (source == null) {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            call.respondSource(source)
                        }
                        get("/cast/artwork/{token}") {
                            val token = call.parameters["token"]
                            val artwork = token?.let(servedArtwork::get)
                            if (artwork == null) {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            val source = openSource(artwork, call.request.header(HttpHeaders.Range))
                            if (source == null) {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            call.respondSource(source)
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

    private fun MediaItem.withReceiverArtwork(host: String): MediaItem {
        val artworkUri = mediaMetadata.artworkUri ?: return this
        if (!artworkUri.isLocalFileUrl()) return this
        val token = UUID.randomUUID().toString()
        val artworkMimeType = mimeType(artworkUri, DEFAULT_IMAGE_MIME_TYPE)
        servedArtwork[token] = ServedAsset(uri = artworkUri, mimeType = artworkMimeType)
        return buildUpon()
            .setMediaMetadata(
                mediaMetadata
                    .buildUpon()
                    .setArtworkUri("http://$host:$port/cast/artwork/$token".toUri())
                    .build(),
            ).build()
    }

    private suspend fun ApplicationCall.respondSource(source: OpenSource) {
        if (source.status == HttpStatusCode.RequestedRangeNotSatisfiable) {
            source.contentRange?.let { response.header(HttpHeaders.ContentRange, it) }
            source.close()
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }
        source.use { nonNullSource ->
            response.header(HttpHeaders.AcceptRanges, "bytes")
            nonNullSource.length?.let { response.header(HttpHeaders.ContentLength, it.toString()) }
            nonNullSource.contentRange?.let { response.header(HttpHeaders.ContentRange, it) }
            respondOutputStream(
                contentType = ContentType.parse(nonNullSource.mimeType),
                status = nonNullSource.status,
            ) {
                nonNullSource.input.copyTo(this)
            }
        }
    }

    private fun openSource(
        item: ServedItem,
        rangeHeader: String?,
    ): OpenSource? {
        val asset = ServedAsset(uri = item.uri, mimeType = item.mimeType)
        if (item.mediaItemResolver == null) return openSource(asset, rangeHeader)
        val normalizedScheme = item.uri.scheme?.lowercase(Locale.US)
        return when (normalizedScheme) {
            "file", "content", "android.resource" -> openSource(asset, rangeHeader)
            else -> openResolvedRemoteSource(item, rangeHeader)
        }
    }

    private fun openSource(
        asset: ServedAsset,
        rangeHeader: String?,
    ): OpenSource? {
        val uri = asset.uri
        val normalizedScheme = uri.scheme?.lowercase(Locale.US)
        return when (normalizedScheme) {
            "file" -> openLocalFileSource(uri, asset.mimeType, rangeHeader)
            "content", "android.resource" -> openContentSource(uri, asset.mimeType, rangeHeader)
            else -> null
        }
    }

    private fun openLocalFileSource(
        uri: Uri,
        mimeType: String,
        rangeHeader: String?,
    ): OpenSource? {
        val file = File(uri.path ?: return null).takeIf { it.isFile } ?: return null
        val length = file.length()
        val range = parseRange(rangeHeader, length)
        if (range == ParsedRange.Unsatisfiable) {
            return OpenSource.unsatisfiable(length)
        }
        val validRange = range as? ParsedRange.Valid
        val start = validRange?.start ?: 0L
        val end = validRange?.end ?: (length - 1L).coerceAtLeast(-1L)
        val contentLength = if (end >= start) end - start + 1L else 0L
        return OpenSource(
            input = file.inputStream().apply { skipFully(start) },
            length = contentLength,
            mimeType = mimeType,
            status = if (validRange == null) HttpStatusCode.OK else HttpStatusCode.PartialContent,
            contentRange = validRange?.let { "bytes $start-$end/$length" },
        )
    }

    private fun openContentSource(
        uri: Uri,
        mimeType: String,
        rangeHeader: String?,
    ): OpenSource? {
        val length = contentLength(uri)
        val range = parseRange(rangeHeader, length)
        if (range == ParsedRange.Unsatisfiable) {
            return OpenSource.unsatisfiable(length)
        }
        val validRange = range as? ParsedRange.Valid
        val start = validRange?.start ?: 0L
        val end = validRange?.end ?: length?.minus(1L)
        val contentLength = end?.let { it - start + 1L }
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.skipFully(start)
        return OpenSource(
            input = BoundedInputStream(input, contentLength),
            length = contentLength ?: length,
            mimeType = mimeType,
            status = if (validRange == null) HttpStatusCode.OK else HttpStatusCode.PartialContent,
            contentRange = if (validRange != null && end != null && length != null) "bytes $start-$end/$length" else null,
        )
    }

    private fun openResolvedRemoteSource(
        item: ServedItem,
        rangeHeader: String?,
    ): OpenSource? {
        val resolved =
            item.resolvedMediaItem ?: item.mediaItemResolver?.resolveForCast(item.mediaItem)?.also { item.resolvedMediaItem = it }
        val resolvedUri = resolved?.localConfiguration?.uri ?: return null
        if (!resolvedUri.isHttpUrl()) {
            val resolvedItem =
                item.copy(
                    uri = resolvedUri,
                    mimeType = resolved.localConfiguration?.mimeType ?: item.mimeType,
                    mediaItemResolver = null,
                )
            return openSource(resolvedItem, rangeHeader)
        }
        val connection =
            (URL(resolvedUri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
                readTimeout = REMOTE_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ArchiveTune Cast")
                rangeHeader?.let { setRequestProperty(HttpHeaders.Range, it) }
            }
        return runCatching {
            val responseCode = connection.responseCode
            if (responseCode == HTTP_REQUESTED_RANGE_NOT_SATISFIABLE) {
                return@runCatching OpenSource.unsatisfiable(null, connection::disconnect)
            }
            if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.disconnect()
                return@runCatching null
            }
            val responseLength = connection.getHeaderFieldLong(HttpHeaders.ContentLength, -1L).takeIf { it >= 0L }
            OpenSource(
                input = connection.inputStream,
                length = responseLength,
                mimeType = connection.contentType?.substringBefore(";") ?: resolved.localConfiguration?.mimeType ?: item.mimeType,
                status =
                    if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                        HttpStatusCode.PartialContent
                    } else {
                        HttpStatusCode.OK
                    },
                contentRange = connection.getHeaderField(HttpHeaders.ContentRange),
                onClose = connection::disconnect,
            )
        }.getOrElse {
            connection.disconnect()
            Timber.tag("Cast").w(it, "Unable to open remote Cast stream proxy")
            null
        }
    }

    private fun contentLength(uri: Uri): Long? =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it >= 0L }
            }
        }.getOrNull()

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

    private fun mimeType(
        uri: Uri,
        fallback: String,
    ): String =
        runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.substringBefore(";")
            ?: uri.path
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?.lowercase(Locale.US)
                ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
            ?: fallback

    private fun parseRange(
        header: String?,
        length: Long?,
    ): ParsedRange? {
        if (header.isNullOrBlank() || !header.startsWith("bytes=")) return null
        val range = header.removePrefix("bytes=").substringBefore(",")
        val startText = range.substringBefore("-")
        val endText = range.substringAfter("-", "")
        val start =
            if (startText.isBlank()) {
                val suffixLength = endText.toLongOrNull()?.takeIf { it > 0L } ?: return null
                val knownLength = length ?: return null
                (knownLength - suffixLength).coerceAtLeast(0L)
            } else {
                max(0L, startText.toLongOrNull() ?: return null)
            }
        val end =
            if (startText.isBlank()) {
                length?.minus(1L)
            } else {
                endText
                    .toLongOrNull()
                    ?.let { requestedEnd -> length?.let { min(requestedEnd, it - 1L) } ?: requestedEnd }
                    ?: length?.minus(1L)
            }
        if (length != null && start >= length) return ParsedRange.Unsatisfiable
        if (end != null && end < start) return ParsedRange.Unsatisfiable
        return ParsedRange.Valid(start, end)
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
        val mediaItem: MediaItem,
        val uri: Uri,
        val mimeType: String,
        val mediaItemResolver: CastMediaItemResolver?,
        @Volatile var resolvedMediaItem: MediaItem? = null,
    )

    private data class ServedAsset(
        val uri: Uri,
        val mimeType: String,
    )

    private sealed interface ParsedRange {
        data class Valid(
            val start: Long,
            val end: Long?,
        ) : ParsedRange

        data object Unsatisfiable : ParsedRange
    }

    private data class OpenSource(
        val input: InputStream,
        val length: Long?,
        val mimeType: String,
        val status: HttpStatusCode,
        val contentRange: String?,
        val onClose: () -> Unit = {},
    ) : AutoCloseable {
        override fun close() {
            runCatching { input.close() }
            onClose()
        }

        companion object {
            fun unsatisfiable(
                length: Long?,
                onClose: () -> Unit = {},
            ) = OpenSource(
                input = ByteArrayInputStream(ByteArray(0)),
                length = 0L,
                mimeType = "audio/mp4",
                status = HttpStatusCode.RequestedRangeNotSatisfiable,
                contentRange = length?.let { "bytes */$it" },
                onClose = onClose,
            )
        }
    }

    private class BoundedInputStream(
        private val source: InputStream,
        private var remaining: Long?,
    ) : InputStream() {
        override fun read(): Int {
            val nonNullRemaining = remaining ?: return source.read()
            if (nonNullRemaining <= 0L) return -1
            val value = source.read()
            if (value >= 0) remaining = nonNullRemaining - 1L
            return value
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            val nonNullRemaining = remaining ?: return source.read(buffer, offset, length)
            if (nonNullRemaining <= 0L) return -1
            val boundedLength = min(length.toLong(), nonNullRemaining).toInt()
            val read = source.read(buffer, offset, boundedLength)
            if (read > 0) remaining = nonNullRemaining - read
            return read
        }

        override fun close() = source.close()
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
}
