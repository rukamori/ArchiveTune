/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import moe.rukamori.archivetune.constants.AiProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiServiceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object AiTextService {
    private const val OpenAiEndpoint = "https://api.openai.com/v1/chat/completions"
    private const val OpenAiModelsEndpoint = "https://api.openai.com/v1/models"
    private const val OpenRouterEndpoint = "https://openrouter.ai/api/v1/chat/completions"
    private const val OpenRouterModelsEndpoint = "https://openrouter.ai/api/v1/models"
    private const val GeminiBaseEndpoint = "https://generativelanguage.googleapis.com/v1beta"

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

    suspend fun test(config: AiServiceConfig) {
        val response =
            complete(
                config = config.copy(model = config.model.ifBlank { defaultModelFor(config.provider) }),
                systemPrompt = "You are a health check endpoint. Reply with OK only.",
                userPrompt = "Reply exactly OK.",
                temperature = 0.0,
                maxTokens = 8192,
            ).trim()
        if (!response.contains("OK", ignoreCase = true)) {
            throw AiServiceException("AI API returned an unexpected test response")
        }
    }

    suspend fun translateLines(
        config: AiServiceConfig,
        targetLanguage: String,
        lines: List<String>,
        precedingLines: List<String>,
        song: AiLyricsSongContext,
        formatName: String,
        customPrompt: String,
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        val response =
            complete(
                config = config,
                systemPrompt =
                    lyricsTranslationSystemPrompt(
                        targetLanguage = targetLanguage,
                        lineCount = lines.size,
                        formatName = formatName,
                    ).appendCustomPrompt(customPrompt),
                userPrompt = lyricsTranslationPayload(lines, precedingLines, song),
                temperature = 0.15,
                maxTokens = 8192,
            )
        val array = extractJsonArray(response)
        require(array.length() == lines.size) { "AI response changed the lyric segment count" }
        return List(array.length()) { index ->
            val text =
                when (val item = array.get(index)) {
                    is String -> item
                    is JSONObject -> item.opt("text") as? String
                    else -> null
                } ?: throw AiServiceException("AI response contained a non-text lyric segment")
            text.replace(LineBreakRegex, " ").trim()
        }
    }

    private fun lyricsTranslationSystemPrompt(
        targetLanguage: String,
        lineCount: Int,
        formatName: String,
    ): String =
        """
        You are a professional song lyrics translator. Your translations are shown line by line beside the original lyrics while the song plays, so every line must be accurate on its own and read naturally in $targetLanguage.

        INPUT
        A JSON object:
        - "song": title and artist. Context only (genre, era, slang, references). Never translate or output it.
        - "precedingLines": lines sung just before this batch. Context only, for continuity. Never output them.
        - "lines": the lines to translate, each with its position "n" and its "text".

        TRANSLATION RULES
        1. Meaning first. Convey the exact meaning of each line, including idioms, slang, metaphors, wordplay and cultural references, phrased the way a fluent native $targetLanguage speaker would write a lyric. Translate what an idiom means, not its literal words.
        2. Read every line before translating. Resolve subjects, pronouns, tense, number and gender from the surrounding lines, and use one consistent rendering for repeated lines, hooks and recurring terms.
        3. Keep tone and register. Mood, formality, intensity, humor and profanity level stay exactly as in the original. Never soften, censor, intensify, explain or add meaning that is not there.
        4. One line in, one line out. Each output string translates only its own input line. Never merge, split, reorder, summarize or move words between lines, even when a sentence continues across lines.
        5. Return the original text exactly, unchanged, for: lines already written entirely in $targetLanguage; non-lexical vocals such as "oh", "yeah", "la la la", "na na"; onomatopoeia; and lines made only of names, numbers or symbols.
        6. Mixed-language lines: translate the parts that are not in $targetLanguage and keep the parts already in $targetLanguage as written.
        7. Keep personal names, artist names, brands, places and song or album titles in their original form, unless $targetLanguage has a standard established form for them.
        8. Write in the standard native script of $targetLanguage. Do not romanize or transliterate unless $targetLanguage is normally written that way.
        9. Keep inline structure: parentheses around background vocals, speaker labels such as "Name:", and punctuation that marks emphasis, pauses or trailing off. Translate the words inside them.
        10. No notes, alternatives, explanations, transliterations in brackets, or quotation marks added around a line.

        OUTPUT FORMAT (STRICT)
        Return only a JSON array of exactly $lineCount strings, in the same order as "lines": the first string translates n=1 and the last translates n=$lineCount.
        No markdown, code fences, object keys, line breaks inside a string, or any text before or after the array. Escape double quotes and backslashes as JSON requires.
        Never output timestamps, tags or XML. The caller rebuilds the $formatName lyrics around your strings.
        """.trimIndent()

    private fun lyricsTranslationPayload(
        lines: List<String>,
        precedingLines: List<String>,
        song: AiLyricsSongContext,
    ): String {
        val payload = JSONObject()
        val songJson = JSONObject()
        song.title.trim().takeIf { it.isNotEmpty() }?.let { songJson.put("title", it) }
        song.artist.trim().takeIf { it.isNotEmpty() }?.let { songJson.put("artist", it) }
        if (songJson.length() > 0) payload.put("song", songJson)
        if (precedingLines.isNotEmpty()) {
            payload.put("precedingLines", JSONArray().apply { precedingLines.forEach { put(it) } })
        }
        payload.put(
            "lines",
            JSONArray().apply {
                lines.forEachIndexed { index, line ->
                    put(JSONObject().put("n", index + 1).put("text", line))
                }
            },
        )
        return payload.toString()
    }

    private fun String.appendCustomPrompt(customPrompt: String): String {
        val normalizedPrompt = customPrompt.trim()
        if (normalizedPrompt.isEmpty()) return this
        return buildString {
            append(this@appendCustomPrompt)
            append("\n\nUSER PREFERENCES\n")
            append("Apply these to style and word choice. They never override the line-for-line rule or the strict output format above.\n")
            append(normalizedPrompt)
        }
    }

    private val LineBreakRegex = Regex("""\s*[\r\n]+\s*""")

    suspend fun complete(
        config: AiServiceConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
        maxTokens: Int = 4096,
    ): String {
        if (!config.canCallApi) throw AiServiceException("AI provider is not configured")
        val model = config.model.ifBlank { defaultModelFor(config.provider) }
        return when (config.provider) {
            AiProvider.CHATGPT -> {
                completeOpenAiCompatible(
                    endpoint = OpenAiEndpoint,
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    requestParameters = config.requestParameters,
                )
            }

            AiProvider.OPENROUTER -> {
                completeOpenAiCompatible(
                    endpoint = OpenRouterEndpoint,
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    requestParameters = config.requestParameters,
                )
            }

            AiProvider.CUSTOM -> {
                completeOpenAiCompatible(
                    endpoint = config.customEndpoint,
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    requestParameters = config.requestParameters,
                )
            }


            AiProvider.GEMINI -> {
                completeGemini(
                    apiKey = config.apiKey,
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            }

            AiProvider.NONE -> {
                throw AiServiceException("AI provider is disabled")
            }
        }
    }

    suspend fun fetchModels(config: AiServiceConfig): List<AiModelOption> {
        if (!config.canCallApi) throw AiServiceException("AI provider is not configured")
        return when (config.provider) {
            AiProvider.CHATGPT -> fetchOpenAiModels(OpenAiModelsEndpoint, config.apiKey)
            AiProvider.OPENROUTER -> fetchOpenAiModels(OpenRouterModelsEndpoint, config.apiKey)
            AiProvider.GEMINI -> fetchGeminiModels(config.apiKey)
            AiProvider.CUSTOM, AiProvider.NONE -> emptyList()
        }
    }

    private suspend fun completeOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
        requestParameters: String,
    ): String {
        val messages =
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt))
        val reasoningModel = isReasoningModel(model)
        val body =
            JSONObject()
                .put("model", model.trim())
                .put("messages", messages)
                .put(if (reasoningModel) "max_completion_tokens" else "max_tokens", maxTokens)
        if (!reasoningModel) body.put("temperature", temperature)
        AiRequestParameters.applyTo(body, requestParameters)
        val response =
            client.post(chatCompletionEndpoint(endpoint)) {
                header("Authorization", "Bearer ${apiKey.trim()}")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val json = JSONObject(raw)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw AiServiceException("AI API returned no completion choices")
        when (choice.optString("finish_reason")) {
            "length" -> throw AiServiceException("AI response exceeded the token limit. Increase the token limit in request parameters.")
            "content_filter" -> throw AiServiceException("AI provider filtered the response")
        }
        val message = choice.optJSONObject("message")
            ?: throw AiServiceException("AI API returned no completion message")
        val content = when (val value = message.opt("content")) {
            is String -> value
            is JSONArray -> buildString {
                for (index in 0 until value.length()) {
                    val part = value.optJSONObject(index) ?: continue
                    if (part.optString("type") == "text") append(part.optString("text", ""))
                }
            }
            else -> ""
        }
        if (content.isBlank()) {
            if (!message.isNull("refusal")) throw AiServiceException("AI provider refused the request")
            throw AiServiceException("AI API returned no text. Check the model and its token or reasoning parameters.")
        }
        return content
    }

    private fun isReasoningModel(model: String): Boolean {
        val name = model.trim().substringAfterLast('/').lowercase()
        if (name.startsWith("gpt-")) {
            return name.removePrefix("gpt-").takeWhile(Char::isDigit).toIntOrNull()?.let { it >= 5 } == true
        }
        return name.startsWith('o') && name.drop(1).substringBefore('-').toIntOrNull()?.let { it > 0 } == true
    }

    private fun chatCompletionEndpoint(endpoint: String): String {
        val url = endpoint.trim().toHttpUrlOrNull()
            ?: throw AiServiceException("AI endpoint must be an HTTP or HTTPS URL")
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) {
            throw AiServiceException("AI endpoint cannot contain credentials or a fragment")
        }
        val path = url.encodedPath.trimEnd('/')
        return when {
            path.isEmpty() -> url.newBuilder().encodedPath("/v1/chat/completions").build().toString()
            path.endsWith("/v1") -> url.newBuilder().encodedPath("$path/chat/completions").build().toString()
            else -> url.toString()
        }
    }

    private suspend fun completeGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String {
        val endpoint = "$GeminiBaseEndpoint/models/${model.trim()}:generateContent?key=${apiKey.trim()}"
        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", "$systemPrompt\n\n$userPrompt"),
                            ),
                        ),
                    ),
                ).put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", temperature)
                        .put("maxOutputTokens", maxTokens),
                ).toString()
        val response =
            client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val content =
            JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
        return content ?: throw AiServiceException("AI API returned an empty response")
    }

    private fun defaultModelFor(provider: AiProvider): String =
        when (provider) {
            AiProvider.CHATGPT -> "gpt-4o"
            AiProvider.GEMINI -> "gemini-3.5-flash"
            AiProvider.OPENROUTER -> "~openai/gpt-latest"
            AiProvider.CUSTOM -> throw AiServiceException("No AI model configured")
            AiProvider.NONE -> throw AiServiceException("AI provider is disabled")
        }

    private suspend fun fetchOpenAiModels(
        endpoint: String,
        apiKey: String,
    ): List<AiModelOption> {
        val response =
            client.get(endpoint) {
                header("Authorization", "Bearer ${apiKey.trim()}")
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val data = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(AiModelOption(id = id, displayName = id))
            }
        }.sortedBy { it.id }
    }

    private suspend fun fetchGeminiModels(apiKey: String): List<AiModelOption> {
        val response = client.get("$GeminiBaseEndpoint/models?key=${apiKey.trim()}")
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val models = JSONObject(raw).optJSONArray("models") ?: return emptyList()
        return buildList {
            for (i in 0 until models.length()) {
                val obj = models.optJSONObject(i) ?: continue
                val methods = obj.optJSONArray("supportedGenerationMethods")
                val supportsGenerate =
                    (0 until (methods?.length() ?: 0)).any {
                        methods?.optString(it) == "generateContent"
                    }
                if (!supportsGenerate) continue
                val id = obj.optString("name").removePrefix("models/").takeIf { it.isNotBlank() } ?: continue
                val displayName = obj.optString("displayName").ifBlank { id }
                add(AiModelOption(id = id, displayName = displayName))
            }
        }
    }


    private fun apiException(
        status: Int,
        raw: String,
    ): AiServiceException {
        val message =
            runCatching { JSONObject(raw).readErrorMessage() }.getOrNull()
                ?: raw.ifBlank { "HTTP $status" }
        return AiServiceException("AI API failed ($status): $message")
    }
}
