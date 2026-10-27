/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.potoken

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

private val json = Json { ignoreUnknownKeys = true }

internal data class PageChallenge(
    val configuration: JSONObject,
    val challenge: JSONObject,
)

internal fun parsePageChallenge(html: String): PageChallenge {
    val configuration = JSONObject()
    Regex("ytcfg\\.set\\s*\\(\\s*(?=\\{)").findAll(html).forEach { match ->
        val value = parsePageObject(extractPageObject(html, match.range.last + 1))
        value.keys().forEach { key -> configuration.put(key, value.get(key)) }
    }
    if (configuration.optString("EVENT_ID").isBlank()) {
        throw PoTokenException("Attestation page is missing EVENT_ID")
    }
    val attestationStart = Regex("window\\.ytAtN\\s*\\(\\s*(?=\\{)").find(html)
        ?: throw PoTokenException("Attestation page is missing its challenge")
    val attestation = parsePageObject(extractPageObject(html, attestationStart.range.last + 1))
    val response = when (val value = attestation.get("R")) {
        is JSONObject -> value
        is String -> parsePageObject(value)
        else -> throw PoTokenException("Invalid page attestation response")
    }
    val challenge = response.getJSONObject("bgChallenge")
    if (challenge.optString("program").isBlank() || challenge.optString("globalName").isBlank()) {
        throw PoTokenException("Incomplete page attestation challenge")
    }
    return PageChallenge(configuration, challenge)
}

private fun parsePageObject(source: String): JSONObject {
    val normalized = StringBuilder(source.length)
    var quote: Char? = null
    var index = 0
    while (index < source.length) {
        val character = source[index]
        if (quote != null) {
            if (character == '\\') {
                val escaped = source.getOrNull(index + 1)
                    ?: throw PoTokenException("Incomplete attestation string escape")
                if (escaped == 'x') {
                    val first = source.getOrNull(index + 2)
                    val second = source.getOrNull(index + 3)
                    if (first == null || second == null ||
                        first.digitToIntOrNull(16) == null || second.digitToIntOrNull(16) == null
                    ) {
                        throw PoTokenException("Invalid attestation hexadecimal escape")
                    }
                    normalized.append("\\u00").append(first).append(second)
                    index += 4
                } else {
                    normalized.append(character).append(escaped)
                    index += 2
                }
                continue
            }
            if (character == quote) quote = null
        } else {
            when (character) {
                '\'', '"' -> quote = character
                ',' -> {
                    var next = index + 1
                    while (next < source.length && source[next].isWhitespace()) next++
                    if (source.getOrNull(next) == '}' || source.getOrNull(next) == ']') {
                        index++
                        continue
                    }
                }
            }
        }
        normalized.append(character)
        index++
    }
    if (quote != null) throw PoTokenException("Unterminated attestation string")
    return JSONObject(normalized.toString())
}

private fun extractPageObject(source: String, offset: Int): String {
    var start = offset
    while (start < source.length && source[start].isWhitespace()) start++
    if (source.getOrNull(start) != '{') throw PoTokenException("Invalid attestation page object")
    var depth = 0
    var quote: Char? = null
    var escaped = false
    for (index in start until source.length) {
        val character = source[index]
        if (quote != null) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> quote = null
            }
        } else {
            when (character) {
                '\'', '"' -> quote = character
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
    }
    throw PoTokenException("Unterminated attestation page object")
}

/**
 * Parses the raw response from YouTube's `api/jnn/v1/GenerateIT` endpoint.
 *
 * Returns a pair of:
 * - A JavaScript `Uint8Array(...)` string representation of the integrity token
 * - The token's lifetime in seconds
 */
fun parseIntegrityToken(rawResponse: String): Pair<String, Long> {
    val arr = json.parseToJsonElement(rawResponse).jsonArray
    val tokenU8 = base64ToJsUint8Array(arr[0].jsonPrimitive.content)
    val lifetimeSeconds = arr[1].jsonPrimitive.long
    return tokenU8 to lifetimeSeconds
}

/**
 * Converts a plain-string identifier to a JavaScript `Uint8Array(...)` literal.
 */
fun stringToJsUint8Array(identifier: String): String {
    val bytes = identifier.toByteArray(Charsets.UTF_8)
    return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
}

/**
 * Converts a comma-separated byte list (output of `Uint8Array.toString()` in JS)
 * to the YouTube-specific base64 encoding used for PoTokens.
 */
fun commaSeparatedBytesToBase64(commaBytes: String): String =
    commaBytes
        .split(",")
        .map { it.trim().toInt().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace('+', '-')
        .replace('/', '_')

// --- internal helpers ---

private fun base64ToJsUint8Array(base64: String): String {
    val bytes = base64ToByteArray(base64)
    return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
}

private fun base64ToByteArray(base64: String): ByteArray {
    val normalised =
        base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
    return (
        normalised.decodeBase64()
            ?: throw PoTokenException("Cannot decode attestation token")
    ).toByteArray()
}
