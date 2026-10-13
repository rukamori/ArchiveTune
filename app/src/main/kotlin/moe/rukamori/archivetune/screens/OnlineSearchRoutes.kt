/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens
import android.util.Base64

internal const val OnlineSearchResultRoute = "search/{encodedQuery}"
internal const val OnlineSearchResultRoutePrefix = "search/"
internal const val OnlineSearchResultArgument = "encodedQuery"

private const val EmptyOnlineSearchQuery = "~"
private val OnlineSearchQueryEncodingFlags =
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

internal fun onlineSearchResultRoute(query: String): String {
    val encodedQuery =
        if (query.isEmpty()) {
            EmptyOnlineSearchQuery
        } else {
            Base64.encodeToString(
                query.toByteArray(Charsets.UTF_8),
                OnlineSearchQueryEncodingFlags,
            )
        }

    return "$OnlineSearchResultRoutePrefix$encodedQuery"
}

internal fun decodeOnlineSearchQuery(encodedQuery: String): String =
    if (encodedQuery == EmptyOnlineSearchQuery) {
        ""
    } else {
        runCatching {
            String(
                Base64.decode(encodedQuery, OnlineSearchQueryEncodingFlags),
                Charsets.UTF_8,
            )
        }.getOrElse {
            encodedQuery
        }
    }
