/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.potoken

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal class BotGuardHttp(
    private val client: Call.Factory,
    private val userAgent: String,
) {
    suspend fun fetch(
        url: String,
        jsonBody: String? = null,
    ): String {
        var target =
            url.toHttpUrlOrNull()?.takeIf(::isTrusted)
                ?: throw PoTokenException("Unsupported BotGuard origin ${url.toHttpUrlOrNull()?.host ?: url}")
        repeat(MAX_REDIRECTS + 1) {
            when (val outcome = exchange(target, jsonBody)) {
                is Outcome.Body -> return outcome.text
                is Outcome.Redirect -> {
                    val location = outcome.location
                    if (jsonBody != null || !isTrusted(location)) {
                        throw PoTokenException("BotGuard request was redirected to ${location.host}")
                    }
                    target = location
                }
            }
        }
        throw PoTokenException("BotGuard request was redirected more than $MAX_REDIRECTS times")
    }

    private suspend fun exchange(
        url: HttpUrl,
        jsonBody: String?,
    ): Outcome =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request(url, jsonBody))
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val outcome = runCatching { response.use(::outcomeOf) }
                        if (continuation.isActive) continuation.resumeWith(outcome)
                    }
                },
            )
        }

    private fun outcomeOf(response: Response): Outcome {
        val location = response.header("Location")?.let(response.request.url::resolve)
        return when {
            response.isRedirect && location != null -> Outcome.Redirect(location)
            response.code == 200 -> Outcome.Body(response.body.string())
            else -> throw PoTokenException("BotGuard HTTP ${response.code}")
        }
    }

    private fun request(
        url: HttpUrl,
        jsonBody: String?,
    ): Request =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.7")
            .apply {
                if (url.host == YOUTUBE_DOMAIN || url.host.endsWith(".$YOUTUBE_DOMAIN")) {
                    header("Cookie", CONSENT_COOKIE)
                }
                if (jsonBody != null) {
                    header("Content-Type", "application/json+protobuf")
                    header("x-goog-api-key", API_KEY)
                    header("x-user-agent", "grpc-web-javascript/0.1")
                    post(jsonBody.toRequestBody())
                }
            }.build()

    private fun isTrusted(url: HttpUrl): Boolean = url.isHttps && url.host in TRUSTED_HOSTS

    private sealed interface Outcome {
        data class Body(
            val text: String,
        ) : Outcome

        data class Redirect(
            val location: HttpUrl,
        ) : Outcome
    }

    companion object {
        const val MAX_REDIRECTS = 5
        private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val YOUTUBE_DOMAIN = "youtube.com"
        private const val CONSENT_COOKIE = "SOCS=CAE="
        private val TRUSTED_HOSTS = setOf("www.youtube.com", "www.google.com", "www.gstatic.com")
    }
}
