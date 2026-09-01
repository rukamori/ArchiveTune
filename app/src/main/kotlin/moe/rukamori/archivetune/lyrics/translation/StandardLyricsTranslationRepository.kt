/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class StandardLyricsTranslationException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

@Singleton
class StandardLyricsTranslationRepository
    @Inject
    constructor() {
        private val client =
            HttpClient(OkHttp) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = RequestTimeoutMillis
                    connectTimeoutMillis = ConnectTimeoutMillis
                    socketTimeoutMillis = RequestTimeoutMillis
                }
                engine {
                    config {
                        retryOnConnectionFailure(true)
                    }
                }
            }

        suspend fun translate(
            text: String,
            targetLanguageCode: String,
        ): String =
            withContext(Dispatchers.IO) {
                val response =
                    client.submitForm(
                        url = TranslationEndpoint,
                        formParameters =
                            parameters {
                                append("client", AndroidClientId)
                                append("dt", "t")
                                append("ie", "UTF-8")
                                append("oe", "UTF-8")
                                append("sl", "auto")
                                append("tl", targetLanguageCode)
                                append("q", text)
                            },
                    ) {
                        header(HttpHeaders.UserAgent, UserAgent)
                    }
                val responseBody = response.bodyAsText()
                if (response.status.value !in 200..299) {
                    throw StandardLyricsTranslationException(
                        "Translation service returned HTTP ${response.status.value}",
                    )
                }
                parseTranslatedText(responseBody)
            }

        private fun parseTranslatedText(responseBody: String): String {
            val translatedSegments =
                try {
                    JSONArray(responseBody).optJSONArray(0)
                } catch (exception: Exception) {
                    throw StandardLyricsTranslationException("Translation service returned malformed data", exception)
                } ?: throw StandardLyricsTranslationException("Translation service returned no translated text")

            val translatedText =
                buildString {
                    for (index in 0 until translatedSegments.length()) {
                        val segment = translatedSegments.optJSONArray(index) ?: continue
                        append(segment.optString(0))
                    }
                }
            if (translatedText.isBlank()) {
                throw StandardLyricsTranslationException("Translation service returned empty translated text")
            }
            return translatedText
        }

        private companion object {
            const val TranslationEndpoint = "https://translate.googleapis.com/translate_a/single"
            const val AndroidClientId = "at"
            const val UserAgent = "ArchiveTune Android"
            const val ConnectTimeoutMillis = 15_000L
            const val RequestTimeoutMillis = 30_000L
        }
    }
