package moe.rukamori.archivetune.sources

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.io.IOException
import java.security.MessageDigest

@Serializable
enum class SourceKind { ADDON, MODULE, JIOSAAVN, YOUTUBE }

@Serializable
@Immutable
data class SourceConfiguration(
    val id: String,
    val kind: SourceKind,
    val name: String,
    val url: String = "",
    val enabled: Boolean = true,
    val moduleCount: Int = 0,
)

@Serializable
data class SourceSettings(
    val version: Int = 1,
    val revision: Long = 0,
    val sources: List<SourceConfiguration> = listOf(
        SourceConfiguration("jiosaavn", SourceKind.JIOSAAVN, "JioSaavn", enabled = false),
        SourceConfiguration("youtube", SourceKind.YOUTUBE, "YouTube Music"),
    ),
)

@Serializable
@Immutable
data class TrackIdentity(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Int? = null,
    val explicit: Boolean? = null,
    val excluded: Boolean = false,
)

@Serializable
data class SourceCandidate(
    val id: String,
    val identity: TrackIdentity,
    val moduleId: String? = null,
    val directUrl: String? = null,
    val format: String? = null,
)

@Serializable
enum class StreamTransport { PROGRESSIVE, HLS, DASH }

@Serializable
data class SourceAudio(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val codec: String = "",
    val mimeType: String = "",
    val transport: StreamTransport = StreamTransport.PROGRESSIVE,
    val bitrate: Int = 0,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val contentLength: Long = -1,
    val expiresAtMs: Long = 0,
    val rendition: String = "",
) {
    val lossless: Boolean get() = codec.lowercase() in setOf("flac", "alac", "wav", "pcm", "aiff")
    val mediaMimeType: String get() = when (transport) {
        StreamTransport.HLS -> "application/x-mpegURL"
        StreamTransport.DASH -> "application/dash+xml"
        StreamTransport.PROGRESSIVE -> mimeType
    }
}

@Serializable
data class SourceSelection(
    val source: SourceConfiguration,
    val candidate: SourceCandidate,
    val audio: SourceAudio,
    val cacheKey: String,
)

enum class SourceProblem { INVALID_ADDRESS, DUPLICATE, UNAVAILABLE, INVALID_RESPONSE, UNSUPPORTED, NO_MATCH, MODULE_EXECUTION, STORAGE }

class SourceException(val problem: SourceProblem, cause: Throwable? = null) : IOException(problem.name, cause)

@Immutable
data class SourceHealth(val problem: SourceProblem? = null, val checked: Boolean = false)

internal fun String.sourceHash(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
