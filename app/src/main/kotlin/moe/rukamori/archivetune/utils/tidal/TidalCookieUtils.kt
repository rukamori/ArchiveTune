/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package moe.rukamori.archivetune.utils.tidal

import java.net.URLDecoder

fun normalizeTidalCookieInput(input: String): String? {
    val trimmedInput =
        input
            .trim()
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .trim()
            .trim(';')
    if (trimmedInput.isBlank()) return null

    return mergeTidalCookieInputs(listOf(trimmedInput))
}

fun isTidalCookieConfigured(value: String): Boolean = extractTidalRefreshToken(value) != null

fun mergeTidalCookieInputs(inputs: Iterable<String>): String? {
    val candidates =
        inputs
            .flatMap(::tidalAuthCandidates)
            .filter { it.isNotBlank() }

    val refreshToken = candidates.firstNotNullOfOrNull(::extractTidalRefreshToken)
    val accessToken = candidates.firstNotNullOfOrNull(::extractTidalAccessToken)

    if (refreshToken.isNullOrBlank() && accessToken.isNullOrBlank()) {
        return null
    }

    val cookies = linkedMapOf<String, String>()
    candidates
        .flatMap(::parseCookiePairs)
        .forEach { pair ->
            cookies[pair.name] = pair.value
        }

    val ordered = linkedMapOf<String, String>()
    refreshToken?.let { ordered["refresh_token"] = it }
    accessToken?.let { ordered["access_token"] = it }
    cookies.forEach { (name, value) ->
        ordered.putIfAbsent(name, value)
    }

    return ordered.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}

fun extractTidalRefreshToken(input: String): String? {
    tidalAuthCandidates(input).forEach { candidate ->
        RefreshTokenRegexes.forEach { regex ->
            regex.find(candidate)?.groupValues?.getOrNull(1)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }
    }

    return input.trim()
        .takeIf { value ->
            value.length >= 24 &&
                !value.contains(';') &&
                !value.contains('=') &&
                !value.contains('\n') &&
                !value.contains('\r') &&
                !value.contains('{') &&
                !value.contains(' ') &&
                value.count { it == '.' } != 2
        }
}

fun extractTidalAccessToken(input: String): String? {
    tidalAuthCandidates(input).forEach { candidate ->
        AccessTokenRegexes.forEach { regex ->
            regex.find(candidate)?.groupValues?.getOrNull(1)?.trim()?.trim('"')?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }
        JwtTokenRegex.find(candidate)?.value?.takeIf { it.isNotBlank() }?.let {
            return it
        }
    }

    return input.trim()
        .takeIf { value ->
            value.length >= 80 &&
                value.count { it == '.' } == 2 &&
                !value.contains(';') &&
                !value.contains('=') &&
                !value.contains('\n') &&
                !value.contains('\r') &&
                !value.contains('{') &&
                !value.contains(' ')
        }
}

private data class TidalCookiePair(
    val name: String,
    val value: String,
)

private fun tidalAuthCandidates(input: String): List<String> {
    val trimmed =
        input
            .trim()
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .trim()
            .trim(';')
    if (trimmed.isBlank()) return emptyList()

    return listOfNotNull(
        trimmed,
        trimmed.replace("\\\"", "\"").replace("\\\\", "\\"),
        trimmed.replace("\\u0022", "\""),
        runCatching { URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }.getOrNull(),
    ).distinct()
}

private fun parseCookiePairs(input: String): List<TidalCookiePair> {
    val header =
        input
            .trim()
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .trim()
            .trim(';')
    if (header.isBlank()) return emptyList()

    return header
        .split(';', '\n', '\r')
        .mapNotNull { part ->
            val trimmed = part.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0) return@mapNotNull null

            val name = trimmed.substring(0, separator).trim()
            val value =
                trimmed
                    .substring(separator + 1)
                    .trim()
                    .trim('"')

            TidalCookiePair(
                name = name,
                value = value,
            ).takeIf {
                it.name.matches(CookieNameRegex) &&
                    it.name.lowercase() !in CookieAttributeNames &&
                    it.value.isNotBlank()
            }
        }
}

private val RefreshTokenRegexes =
    listOf(
        Regex(""""refresh_token"\s*:\s*"([^"]+)""""),
        Regex(""""refreshToken"\s*:\s*"([^"]+)""""),
        Regex("""(?:^|[?&\s;])refresh_token=([^&;\s]+)"""),
        Regex("""(?:^|[?&\s;])refreshToken=([^&;\s]+)"""),
    )

private val AccessTokenRegexes =
    listOf(
        Regex(""""access_token"\s*:\s*"([^"]+)""""),
        Regex(""""accessToken"\s*:\s*"([^"]+)""""),
        Regex("""(?:^|[?&\s;])access_token=([^&;\s]+)"""),
        Regex("""(?:^|[?&\s;])accessToken=([^&;\s]+)"""),
    )

private val JwtTokenRegex = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
private val CookieNameRegex = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
private val CookieAttributeNames =
    setOf(
        "domain",
        "expires",
        "max-age",
        "path",
        "priority",
        "samesite",
        "secure",
        "httponly",
    )
