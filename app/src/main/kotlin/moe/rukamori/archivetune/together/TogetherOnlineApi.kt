/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Serializable
data class TogetherOnlineCreateSessionRequest(
    val hostDisplayName: String,
    val settings: TogetherRoomSettings,
)

@Serializable
data class TogetherOnlineCreateSessionResponse(
    val sessionId: String,
    val code: String,
    val hostKey: String,
    val guestKey: String,
    val wsUrl: String,
    val settings: TogetherRoomSettings,
)

@Serializable
data class TogetherOnlineResolveRequest(
    val code: String,
)

@Serializable
data class TogetherOnlineResolveResponse(
    val sessionId: String,
    val guestKey: String,
    val wsUrl: String,
    val settings: TogetherRoomSettings,
)

class TogetherOnlineApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class TogetherOnlineApi(
    private val baseUrl: String,
    private val bearerToken: String? = null,
) {
    private val v1BaseUrl: String =
        baseUrl
            .trimEnd('/')
            .let { if (it.endsWith("/v1")) it else "$it/v1" }

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 800,
        block: suspend () -> T,
    ): T {
        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (t: Throwable) {
                lastException = t
                if (attempt < maxAttempts && isRetryable(t)) {
                    delay(initialDelayMs * attempt)
                } else {
                    throw t
                }
            }
        }
        throw lastException!!
    }

    private fun isRetryable(t: Throwable): Boolean {
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        return root is java.net.SocketTimeoutException ||
            root is java.net.ConnectException ||
            root is java.io.IOException
    }

    @Serializable
    private data class TogetherOnlineErrorResponse(
        val ok: Boolean? = null,
        val error: String? = null,
        val code: String? = null,
    )

    private fun errorMessageFromResponse(
        status: Int,
        rawBody: String,
    ): String {
        val parsedError =
            runCatching { json.decodeFromString(TogetherOnlineErrorResponse.serializer(), rawBody) }
                .getOrNull()
        val specific = parsedError?.error?.trim()?.takeIf { it.isNotBlank() }
        if (specific != null) return specific
        return when (status) {
            400 -> "Bad request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Session not found"
            429 -> "Too many requests, please try again later"
            in 500..599 -> "Server error ($status)"
            else -> "Unexpected response ($status)"
        }
    }

    private fun normalizedBearerTokenOrNull(): String? = bearerToken?.trim()?.takeIf { it.isNotBlank() }

    suspend fun createSession(
        hostDisplayName: String,
        settings: TogetherRoomSettings,
    ): TogetherOnlineCreateSessionResponse =
        withRetry {
            val token = normalizedBearerTokenOrNull() ?: throw TogetherOnlineApiException("Together token is missing")
            val payload =
                json.encodeToString(
                    TogetherOnlineCreateSessionRequest.serializer(),
                    TogetherOnlineCreateSessionRequest(
                        hostDisplayName = hostDisplayName,
                        settings = settings,
                    ),
                )
            val resp =
                client.post("$v1BaseUrl/together/sessions") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            val status = resp.status.value
            val raw = resp.bodyAsText()
            if (status !in 200..299) throw TogetherOnlineApiException(errorMessageFromResponse(status, raw), statusCode = status)
            json.decodeFromString(TogetherOnlineCreateSessionResponse.serializer(), raw)
        }

    suspend fun resolveCode(code: String): TogetherOnlineResolveResponse =
        withRetry {
            val token = normalizedBearerTokenOrNull() ?: throw TogetherOnlineApiException("Together token is missing")
            val payload =
                json.encodeToString(
                    TogetherOnlineResolveRequest.serializer(),
                    TogetherOnlineResolveRequest(code = code.trim()),
                )
            val resp =
                client.post("$v1BaseUrl/together/sessions/resolve") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            val status = resp.status.value
            val raw = resp.bodyAsText()
            if (status !in 200..299) throw TogetherOnlineApiException(errorMessageFromResponse(status, raw), statusCode = status)
            json.decodeFromString(TogetherOnlineResolveResponse.serializer(), raw)
        }

    suspend fun endSession(
        sessionId: String,
        hostKey: String,
    ) {
        withRetry {
            val normalizedSessionId = sessionId.trim()
            val normalizedHostKey = hostKey.trim()
            require(normalizedSessionId.isNotBlank()) { "Session ID is required" }
            require(normalizedHostKey.isNotBlank()) { "Host key is required" }

            val response =
                client.post("$v1BaseUrl/together/sessions/$normalizedSessionId/end") {
                    header("Authorization", "Bearer $normalizedHostKey")
                }
            val status = response.status.value
            if (status !in 200..299 && status != 404) {
                val raw = response.bodyAsText()
                throw TogetherOnlineApiException(
                    errorMessageFromResponse(status, raw),
                    statusCode = status,
                )
            }
        }
    }
}
