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
                maxTokens = 32,
            ).trim()
        if (!response.equals("OK", ignoreCase = true)) {
            throw AiServiceException("AI API returned an unexpected test response")
        }
    }

    suspend fun translateLines(
        config: AiServiceConfig,
        targetLanguage: String,
        lines: List<String>,
        formatName: String,
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        val payload = JSONArray()
        lines.forEach { payload.put(it) }
        val response =
            complete(
                config = config,
                systemPrompt =
                    """
                    You are an expert song lyrics translator.
                    Translate each input string into $targetLanguage with natural, accurate lyric phrasing.
                    Preserve meaning, tone, profanity level, names, repeated hooks, and line-level intent.
                    Do not add timestamps, IDs, XML, markdown, explanations, or extra lines.
                    Return only a JSON array of strings with exactly ${lines.size} items in the same order.
                    The caller will reconstruct the $formatName lyrics container separately.
                    """.trimIndent(),
                userPrompt = payload.toString(),
                temperature = 0.15,
                maxTokens = 8192,
            )
        val array = extractJsonArray(response)
        require(array.length() == lines.size) { "AI response changed the lyric segment count" }
        return List(array.length()) { index -> array.optString(index) }
    }

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
            AiProvider.CHATGPT -> fetchOpenAiModels(config.apiKey)
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
    ): String {
        val messages =
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt))
        val body =
            JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
                .toString()
        val response =
            client.post(endpoint.trim()) {
                header("Authorization", "Bearer ${apiKey.trim()}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(response.status.value, raw)
        val json = JSONObject(raw)
        val content =
            json
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
        return content ?: throw AiServiceException("AI API returned an empty response")
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
            AiProvider.CUSTOM -> throw AiServiceException("No AI model configured")
            AiProvider.NONE -> throw AiServiceException("AI provider is disabled")
        }

    private suspend fun fetchOpenAiModels(apiKey: String): List<AiModelOption> {
        val response =
            client.get(OpenAiModelsEndpoint) {
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
                ?: raw.take(240).ifBlank { "HTTP $status" }
        return AiServiceException("AI API failed ($status): $message")
    }
}
