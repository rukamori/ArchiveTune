package moe.rukamori.archivetune.sources

import android.media.MediaCodecList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolvedAudioStream
import moe.rukamori.archivetune.playback.stream.StreamSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveExternalSourceUseCase @Inject constructor(
    private val settings: SourceSettingsRepository,
    private val providers: SourceProviderRepository,
    private val matcher: TrackMatchingUseCase,
    private val tracks: SourceTrackRepository,
    private val streams: SourceStreamRepository,
) {
    private val mutableHealth = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val health = mutableHealth.asStateFlow()

    suspend fun check(source: SourceConfiguration) = withContext(Dispatchers.IO) {
        try {
            providers.check(source)
            report(source.id, null)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            report(source.id, (failure as? SourceException)?.problem ?: SourceProblem.UNAVAILABLE)
        }
    }

    suspend operator fun invoke(request: AudioStreamRequest): ResolvedAudioStream? = withContext(Dispatchers.IO) {
        if (!request.allowExternal || request.pinnedFormatId != null) return@withContext null
        val sources = settings.settings.first().sources.filter { it.enabled && it.kind != SourceKind.YOUTUBE }
        if (sources.isEmpty()) return@withContext null
        val target = request.identity ?: tracks.identity(request.mediaId) ?: return@withContext null
        if (target.excluded || target.artist.isBlank()) return@withContext null
        val lossless = request.quality == AudioQuality.HIGHEST || (request.quality == AudioQuality.AUTO && !request.networkMetered)
        val low = request.quality == AudioQuality.LOW || (request.quality == AudioQuality.AUTO && request.networkMetered)
        withTimeoutOrNull(30_000) {
            for (source in sources) {
                try {
                    val selection = withTimeoutOrNull(18_000) {
                        val candidates = withContext(Dispatchers.Default) {
                            matcher.ranked(target, providers.search(source, target))
                        }
                        if (candidates.isEmpty()) throw SourceException(SourceProblem.NO_MATCH)
                        var failure: SourceException = SourceException(SourceProblem.NO_MATCH)
                        for (candidate in candidates.take(6)) {
                            try {
                                val audio = streams.inspect(providers.stream(source, candidate, lossless, low))
                                if ((!lossless && audio.lossless) || (low && (audio.bitrate <= 0 || audio.bitrate > 128_000))) {
                                    throw SourceException(SourceProblem.UNSUPPORTED)
                                }
                                if (!supported(audio, request.forCast)) throw SourceException(SourceProblem.UNSUPPORTED)
                                val representation = listOf(source.id, candidate.moduleId.orEmpty(), candidate.id, audio.codec,
                                    audio.transport.name, audio.rendition, audio.bitrate.toString(), audio.sampleRate.toString(), audio.bitDepth.toString()).joinToString("\u0000")
                                return@withTimeoutOrNull SourceSelection(source, candidate, audio, "ext:${request.mediaId}:${representation.sourceHash()}")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = error as? SourceException ?: SourceException(SourceProblem.INVALID_RESPONSE, error)
                            }
                        }
                        throw failure
                    } ?: throw SourceException(SourceProblem.TIMED_OUT)
                    report(source.id, null)
                    return@withTimeoutOrNull selection.toResolved(request, target)
                } catch (failure: TimeoutCancellationException) {
                    throw failure
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    report(source.id, (failure as? SourceException)?.problem ?: SourceProblem.INVALID_RESPONSE)
                }
            }
            null
        }
    }

    suspend fun refresh(selection: SourceSelection, request: AudioStreamRequest): ResolvedAudioStream = withContext(Dispatchers.IO) {
        val audio = streams.inspect(providers.stream(selection.source, selection.candidate, selection.audio.lossless, selection.audio.bitrate in 1..128_000))
        if (audio.codec != selection.audio.codec || audio.transport != selection.audio.transport ||
            audio.rendition != selection.audio.rendition || audio.bitrate != selection.audio.bitrate ||
            audio.sampleRate != selection.audio.sampleRate || audio.bitDepth != selection.audio.bitDepth ||
            (audio.contentLength > 0 && selection.audio.contentLength > 0 && audio.contentLength != selection.audio.contentLength)) {
            throw SourceException(SourceProblem.INVALID_RESPONSE)
        }
        selection.copy(audio = audio).toResolved(request, selection.candidate.identity)
    }

    private fun SourceSelection.toResolved(request: AudioStreamRequest, target: TrackIdentity) = ResolvedAudioStream(
        url = audio.url, requestHeaders = audio.headers, formatId = -1, mimeType = audio.mimeType, codecs = audio.codec,
        bitrate = audio.bitrate, sampleRate = audio.sampleRate, contentLength = audio.contentLength,
        expiresAtMs = audio.expiresAtMs, authFingerprint = request.authState.streamCacheFingerprint,
        source = StreamSource.EXTERNAL, title = target.title, durationSeconds = candidate.identity.durationSeconds,
        external = this,
    )

    private fun report(id: String, problem: SourceProblem?) {
        mutableHealth.update { it + (id to SourceHealth(problem, checked = true)) }
    }

    private fun supported(audio: SourceAudio, forCast: Boolean): Boolean {
        if (forCast) return audio.transport == StreamTransport.PROGRESSIVE && audio.headers.isEmpty() && audio.mimeType in setOf("audio/mpeg", "audio/mp4", "audio/flac", "audio/ogg")
        if (audio.mimeType in setOf("audio/mpeg", "audio/mp4", "audio/flac", "audio/ogg", "audio/wav")) return true
        val mime = audio.mimeType.takeIf(String::isNotBlank) ?: return audio.transport != StreamTransport.PROGRESSIVE
        return MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codec -> !codec.isEncoder && codec.supportedTypes.any { it.equals(mime, true) } }
    }
}
