package moe.rukamori.archivetune.sources

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class SourceHttpClient @Inject constructor() {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

    suspend fun document(url: HttpUrl): JsonObject = try {
        json.parseToJsonElement(text(Request.Builder().url(url).header("Accept", "application/json").build()).let {
            if (it.status !in 200..299) throw SourceException(SourceProblem.UNAVAILABLE)
            it.body
        }) as? JsonObject
            ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
    } catch (failure: kotlinx.serialization.SerializationException) {
        throw SourceException(SourceProblem.INVALID_RESPONSE, failure)
    }

    suspend fun text(request: Request): HttpReply = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(SourceException(SourceProblem.UNAVAILABLE, e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val reply = response.use {
                        val body = it.body ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
                        val source = body.source()
                        if (source.request(MAX_BODY_BYTES + 1)) throw SourceException(SourceProblem.INVALID_RESPONSE)
                        HttpReply(it.code, source.readUtf8(), it.headers.toMultimap().mapValues { entry -> entry.value.joinToString(", ") })
                    }
                    if (continuation.isActive) continuation.resume(reply)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }

    data class HttpReply(val status: Int, val body: String, val headers: Map<String, String>)

    companion object {
        private const val MAX_BODY_BYTES = 4L * 1024 * 1024
        fun address(raw: String): HttpUrl = raw.trim().toHttpUrlOrNull()
            ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
            ?: throw SourceException(SourceProblem.INVALID_ADDRESS)
    }
}
