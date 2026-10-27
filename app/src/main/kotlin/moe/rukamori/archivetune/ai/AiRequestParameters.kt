/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

object AiRequestParameters {
    private val reservedNames = setOf("model", "messages", "stream", "n")

    fun normalize(value: String): String {
        if (value.isBlank()) return ""
        require(value.length <= 16_384) { "Request parameters are too large" }
        val parameters = Json.parseToJsonElement(value) as? JsonObject
            ?: throw IllegalArgumentException("Request parameters must be a JSON object")
        require(parameters.keys.none { it.isBlank() || it in reservedNames }) {
            "Request parameters cannot override model, messages, stream, or n"
        }
        for (name in listOf("max_tokens", "max_completion_tokens")) {
            val parameter = parameters[name] ?: continue
            if (parameter == JsonNull) continue
            val primitive = parameter as? JsonPrimitive
            require(primitive != null && !primitive.isString && primitive.content.toLongOrNull()?.let { it > 0 } == true) {
                "$name must be a positive integer or null"
            }
        }
        require(parameters["max_tokens"].let { it == null || it == JsonNull } ||
            parameters["max_completion_tokens"].let { it == null || it == JsonNull }) {
            "Use only one token limit parameter"
        }
        for ((name, maximum) in listOf("temperature" to 2.0, "top_p" to 1.0)) {
            val parameter = parameters[name] ?: continue
            if (parameter == JsonNull) continue
            val primitive = parameter as? JsonPrimitive
            val number = primitive?.takeUnless { it.isString }?.content?.toDoubleOrNull()
            require(number != null && number.isFinite() && number in 0.0..maximum) {
                "$name is outside its supported range"
            }
        }
        return parameters.toString()
    }

    internal fun applyTo(body: JSONObject, value: String) {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return
        val parameters = JSONObject(normalized)
        if (parameters.has("max_tokens") && !parameters.isNull("max_tokens")) body.remove("max_completion_tokens")
        if (parameters.has("max_completion_tokens") && !parameters.isNull("max_completion_tokens")) body.remove("max_tokens")
        parameters.keys().forEach { name ->
            if (parameters.isNull(name)) body.remove(name) else body.put(name, parameters.get(name))
        }
    }
}
