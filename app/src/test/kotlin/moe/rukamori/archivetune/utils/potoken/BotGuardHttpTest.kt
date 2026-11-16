/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.potoken

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

class BotGuardHttpTest {
    @Test
    fun followsRedirectBetweenTokenChallengeHosts() =
        runBlocking {
            val google =
                FakeGoogle { request ->
                    when (request.url.toString()) {
                        CHALLENGE_PAGE_URL -> redirect(request, "https://www.youtube.com/?hl=en&bounce=1")
                        "https://www.youtube.com/?hl=en&bounce=1" -> ok(request, "challenge page")
                        else -> status(request, 404)
                    }
                }

            assertEquals("challenge page", google.botGuardHttp().fetch(CHALLENGE_PAGE_URL))
        }

    @Test
    fun stopsAtConsentInterstitialInsteadOfBouncingUntilTheHttpClientGivesUp() =
        runBlocking {
            val google =
                FakeGoogle { request ->
                    if (request.url.host == "www.youtube.com") {
                        redirect(request, "https://consent.youtube.com/m?continue=$CHALLENGE_PAGE_URL&cbrd=1")
                    } else {
                        redirect(request, CHALLENGE_PAGE_URL)
                    }
                }

            val failure = google.fetchFailure(CHALLENGE_PAGE_URL)

            assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("consent.youtube.com"))
            assertEquals(1, google.requests.size)
        }

    @Test
    fun givesUpOnRedirectLoopBetweenTokenChallengeHostsAfterBoundedHops() =
        runBlocking {
            val google =
                FakeGoogle { request ->
                    val hop = request.url.queryParameter("hop")?.toInt() ?: 0
                    redirect(request, "https://www.youtube.com/?hl=en&hop=${hop + 1}")
                }

            google.fetchFailure(CHALLENGE_PAGE_URL)

            assertEquals(BotGuardHttp.MAX_REDIRECTS + 1, google.requests.size)
        }

    @Test
    fun declaresConsentChoiceOnlyToYouTube() =
        runBlocking {
            val google = FakeGoogle { request -> ok(request, "ok") }
            val http = google.botGuardHttp()

            http.fetch(CHALLENGE_PAGE_URL)
            http.fetch(INTERPRETER_URL)

            assertEquals("SOCS=CAE=", google.requests[0].header("Cookie"))
            assertNull(google.requests[1].header("Cookie"))
        }

    @Test
    fun doesNotReplayTokenRequestBodyToRedirectTarget() =
        runBlocking {
            val google =
                FakeGoogle { request ->
                    if (request.method == "POST") redirect(request, "https://www.youtube.com/api/jnn/v1/Other", code = 307) else ok(request, "ok")
                }

            google.fetchFailure(GENERATE_IT_URL, jsonBody = "[\"key\",\"response\"]")

            assertEquals(1, google.requests.size)
        }

    @Test
    fun refusesUntrustedTokenChallengeHost() =
        runBlocking {
            val google = FakeGoogle { request -> ok(request, "script") }

            google.fetchFailure("https://scripts.example.com/js/th/interpreter.js")

            assertEquals(0, google.requests.size)
        }

    @Test
    fun reportsRateLimitAsTokenFailure() =
        runBlocking {
            val google = FakeGoogle { request -> status(request, 429) }

            val failure = google.fetchFailure(CHALLENGE_PAGE_URL)

            assertEquals("BotGuard HTTP 429", failure.message)
        }

    private class FakeGoogle(
        private val respond: (Request) -> Response,
    ) {
        val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

        private val client =
            OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    requests += chain.request()
                    respond(chain.request())
                }.build()

        fun botGuardHttp() = BotGuardHttp(client, USER_AGENT)

        suspend fun fetchFailure(
            url: String,
            jsonBody: String? = null,
        ): PoTokenException {
            try {
                botGuardHttp().fetch(url, jsonBody)
            } catch (failure: PoTokenException) {
                return failure
            }
            fail("Expected the token challenge request to fail")
            throw AssertionError()
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 Test"
        const val CHALLENGE_PAGE_URL = "https://www.youtube.com/?hl=en"
        const val INTERPRETER_URL = "https://www.google.com/js/th/interpreter.js"
        const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"

        fun ok(
            request: Request,
            body: String,
        ): Response = status(request, 200, body)

        fun redirect(
            request: Request,
            location: String,
            code: Int = 302,
        ): Response =
            response(request, code, "")
                .newBuilder()
                .header("Location", location)
                .build()

        fun status(
            request: Request,
            code: Int,
            body: String = "",
        ): Response = response(request, code, body)

        fun response(
            request: Request,
            code: Int,
            body: String,
        ): Response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status $code")
                .body(body.toResponseBody())
                .build()
    }
}
