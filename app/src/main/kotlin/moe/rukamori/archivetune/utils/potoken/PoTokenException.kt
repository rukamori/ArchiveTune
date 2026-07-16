/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils.potoken

/**
 * Thrown when BotGuard PoToken generation fails in a recoverable way
 * (e.g. timeout, network error). Callers should fall back to unauthenticated playback.
 */
class PoTokenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when the system WebView is fundamentally broken (e.g. throws JavaScript SyntaxErrors).
 * Once this is thrown the generator marks itself as permanently unavailable for the process lifetime.
 */
class BrokenWebViewException(
    message: String,
) : Exception(message)

/**
 * Classifies a JavaScript error string into the appropriate exception type.
 */
fun classifyJsError(error: String): Exception =
    if (error.contains("SyntaxError")) {
        BrokenWebViewException(error)
    } else {
        PoTokenException(error)
    }
