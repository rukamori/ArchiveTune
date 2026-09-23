package moe.rukamori.archivetune.sources

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SourceStreamRepository @Inject constructor(private val http: SourceHttpClient) {
    suspend fun inspect(audio: SourceAudio): SourceAudio = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(SourceHttpClient.address(audio.url))
        audio.headers.forEach { (key, value) -> builder.header(key, value) }
        val call = http.client.newCall(builder.header("Range", "bytes=0-4095").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(SourceException(SourceProblem.UNAVAILABLE, e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!it.isSuccessful) throw SourceException(SourceProblem.UNAVAILABLE)
                        val body = it.body ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
                        val source = body.source()
                        source.request(4096)
                        val bytes = source.readByteArray(minOf(source.buffer.size, 4096))
                        if (bytes.isEmpty()) throw SourceException(SourceProblem.INVALID_RESPONSE)
                        val prefix = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                        val mime = it.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                        if (mime == "text/html" || prefix.startsWith("<!DOCTYPE html", true) || prefix.startsWith("<html", true) || prefix.startsWith('{')) {
                            throw SourceException(SourceProblem.INVALID_RESPONSE)
                        }
                        val transport = when {
                            prefix.startsWith("#EXTM3U") -> StreamTransport.HLS
                            prefix.contains("<MPD") || mime == "application/dash+xml" -> StreamTransport.DASH
                            else -> audio.transport
                        }
                        if (transport == StreamTransport.DASH && prefix.contains("ContentProtection")) throw SourceException(SourceProblem.UNSUPPORTED)
                        val codec = when {
                            prefix.startsWith("fLaC") || mime == "audio/flac" || prefix.contains("codecs=\"flac\"", true) -> "flac"
                            prefix.startsWith("RIFF") -> "pcm"
                            mime == "audio/mpeg" -> "mp3"
                            else -> audio.codec
                        }
                        val mediaMime = when {
                            codec == "flac" -> "audio/flac"
                            codec == "pcm" -> "audio/wav"
                            audio.mimeType.isNotBlank() -> audio.mimeType
                            mime.startsWith("audio/") -> mime
                            bytes.size > 8 && bytes.copyOfRange(4, 8).toString(Charsets.ISO_8859_1) == "ftyp" -> "audio/mp4"
                            prefix.startsWith("ID3") -> "audio/mpeg"
                            prefix.startsWith("OggS") -> "audio/ogg"
                            else -> ""
                        }
                        val length = if (transport != StreamTransport.PROGRESSIVE) -1L else
                            it.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                                ?: if (it.code == 200) body.contentLength() else audio.contentLength
                        audio.copy(codec = codec, mimeType = mediaMime, transport = transport, contentLength = length)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }
}
