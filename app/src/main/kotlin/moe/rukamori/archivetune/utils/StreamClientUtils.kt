/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.YouTubeClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.Locale

/**
 * Shared utility for resolving the correct User-Agent and Origin/Referer headers
 * based on the stream client query parameters embedded in YouTube stream URLs.
 *
 * This centralizes the logic that was previously duplicated across:
 * - MusicService OkHttp interceptor
 * - DownloadUtil OkHttp interceptor
 */
object StreamClientUtils {
    data class StreamRequestProfile(
        val requestedClientName: String,
        val requestedClientVersion: String,
        val resolvedClientFamily: String,
        val resolvedClientVersion: String,
        val userAgent: String,
        val origin: String?,
        val referer: String?,
        val requiresPlaybackProbeRanges: Boolean,
    ) {
        val clientKey: String
            get() = normalizeClientKey("$resolvedClientFamily@$resolvedClientVersion")

        val variantLabel: String
            get() = "$resolvedClientFamily@$resolvedClientVersion"
    }

    /**
     * Resolve the correct User-Agent for a YouTube media request based on
     * the `c` query parameter from the stream URL.
     *
     * @param clientParam  the value of the `c` query parameter (e.g. "WEB_REMIX", "IOS", "ANDROID_VR")
     * @return the appropriate User-Agent string
     */
    fun resolveUserAgent(clientParam: String): String = resolveRequestProfile(clientParam = clientParam).userAgent

    /**
     * Data class holding Origin and Referer header values (nullable when not required).
     */
    data class OriginReferer(
        val origin: String?,
        val referer: String?,
    )

    /**
     * Determine the correct Origin and Referer for a YouTube media request.
     * Web-type clients need YouTube Music origin; TV clients need YouTube origin.
     * Other clients (native app clients) do not need these headers.
     *
     * @param clientParam  the value of the `c` query parameter
     * @return [OriginReferer] with appropriate values, or null fields if not needed
     */
    fun resolveOriginReferer(clientParam: String): OriginReferer =
        resolveRequestProfile(clientParam = clientParam).let { OriginReferer(it.origin, it.referer) }

    fun resolveRequestProfile(url: String): StreamRequestProfile = resolveRequestProfile(url.toHttpUrlOrNull())

    fun resolveRequestProfile(url: HttpUrl?): StreamRequestProfile =
        resolveRequestProfile(
            clientParam = url?.queryParameter("c"),
            clientVersion = url?.queryParameter("cver"),
        )

    fun resolveRequestProfile(
        clientParam: String?,
        clientVersion: String? = null,
    ): StreamRequestProfile {
        val requestedClientName = clientParam.normalizedOrEmpty()
        val requestedClientVersion = clientVersion.normalizedOrEmpty()
        val client = resolveClient(requestedClientName, requestedClientVersion)
        val originReferer = resolveOriginReferer(client)

        return StreamRequestProfile(
            requestedClientName = requestedClientName.ifEmpty { client.clientName },
            requestedClientVersion = requestedClientVersion.ifEmpty { client.clientVersion },
            resolvedClientFamily = client.clientName,
            resolvedClientVersion = client.clientVersion,
            userAgent = client.userAgent,
            origin = originReferer.origin,
            referer = originReferer.referer,
            requiresPlaybackProbeRanges = isWebLikeClient(client),
        )
    }

    fun applyRequestProfile(
        requestBuilder: Request.Builder,
        requestProfile: StreamRequestProfile,
    ): Request.Builder {
        requestBuilder.header("User-Agent", requestProfile.userAgent)
        if (requestProfile.origin != null) {
            requestBuilder.header("Origin", requestProfile.origin)
        } else {
            requestBuilder.removeHeader("Origin")
        }
        if (requestProfile.referer != null) {
            requestBuilder.header("Referer", requestProfile.referer)
        } else {
            requestBuilder.removeHeader("Referer")
        }
        return requestBuilder
    }

    /**
     * Check whether the given client parameter represents a web-type client
     * that requires poToken for playback requests.
     */
    fun isWebClient(clientParam: String): Boolean = resolveRequestProfile(clientParam = clientParam).requiresPlaybackProbeRanges

    fun isWebClient(requestProfile: StreamRequestProfile): Boolean = requestProfile.requiresPlaybackProbeRanges

    internal fun buildClientKey(client: YouTubeClient): String = normalizeClientKey("${client.clientName}@${client.clientVersion}")

    internal fun normalizeClientKey(clientKey: String?): String =
        clientKey
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)
            .orEmpty()

    /**
     * Patch the `cver` (client version) parameter in a stream URL to match the actual
     * client version we used, preventing version mismatch 403 errors.
     *
     * @param url           the original stream URL
     * @param clientVersion the client version string that was used for the player request
     * @return the patched URL, or the original URL if no patching was needed
     */
    fun patchClientVersion(
        url: String,
        clientVersion: String,
    ): String {
        if (!url.contains("cver=")) return url
        return url.replace(Regex("cver=[^&]+"), "cver=$clientVersion")
    }

    /**
     * Append a poToken to a stream URL as the `pot` query parameter.
     *
     * @param url      the stream URL
     * @param poToken  the token to append
     * @return the URL with the `pot` parameter appended
     */
    fun appendPoToken(
        url: String,
        poToken: String,
    ): String {
        if (url.contains("pot=")) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$poToken"
    }

    private fun resolveClient(
        requestedClientName: String,
        requestedClientVersion: String,
    ): YouTubeClient {
        val clientName = requestedClientName.uppercase(Locale.US)
        return when {
            clientName == "WEB_REMIX" -> {
                YouTubeClient.WEB_REMIX
            }

            clientName == "WEB" -> {
                YouTubeClient.WEB
            }

            clientName == "WEB_CREATOR" -> {
                YouTubeClient.WEB_CREATOR
            }

            clientName == "MWEB" -> {
                YouTubeClient.MWEB
            }

            clientName == "WEB_EMBEDDED_PLAYER" || clientName == "WEB_EMBEDDED" -> {
                YouTubeClient.WEB_EMBEDDED
            }

            clientName == "TVHTML5" -> {
                YouTubeClient.TVHTML5
            }

            clientName == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" || clientName == "TVHTML5_SIMPLY" -> {
                YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER
            }

            clientName == "IOS_MUSIC" -> {
                YouTubeClient.IOS_MUSIC
            }

            clientName.startsWith("IOS") -> {
                if (requestedClientVersion == YouTubeClient.IPADOS.clientVersion) {
                    YouTubeClient.IPADOS
                } else {
                    YouTubeClient.IOS
                }
            }

            clientName == "ANDROID_MUSIC" -> {
                YouTubeClient.ANDROID_MUSIC
            }

            clientName == "ANDROID_TESTSUITE" -> {
                YouTubeClient.ANDROID_TESTSUITE
            }

            clientName == "ANDROID_UNPLUGGED" -> {
                YouTubeClient.ANDROID_UNPLUGGED
            }

            clientName.startsWith("ANDROID_CREATOR") -> {
                YouTubeClient.ANDROID_CREATOR
            }

            clientName.startsWith("ANDROID_VR") -> {
                when (requestedClientVersion) {
                    YouTubeClient.ANDROID_VR_1_61_48.clientVersion -> YouTubeClient.ANDROID_VR_1_61_48
                    YouTubeClient.ANDROID_VR_1_43_32.clientVersion -> YouTubeClient.ANDROID_VR_1_43_32
                    else -> YouTubeClient.ANDROID_VR_NO_AUTH
                }
            }

            clientName.startsWith("ANDROID") -> {
                YouTubeClient.MOBILE
            }

            clientName.startsWith("VISIONOS") -> {
                YouTubeClient.VISIONOS
            }

            else -> {
                YouTubeClient.ANDROID_VR_NO_AUTH
            }
        }
    }

    private fun resolveOriginReferer(client: YouTubeClient): OriginReferer =
        when {
            isTvClient(client) -> {
                OriginReferer(YouTubeClient.ORIGIN_YOUTUBE, YouTubeClient.REFERER_YOUTUBE_TV)
            }

            isWebMusicClient(client) -> {
                OriginReferer(YouTubeClient.ORIGIN_YOUTUBE_MUSIC, YouTubeClient.REFERER_YOUTUBE_MUSIC)
            }

            else -> {
                OriginReferer(null, null)
            }
        }

    private fun isWebLikeClient(client: YouTubeClient): Boolean = isTvClient(client) || isWebMusicClient(client)

    private fun isTvClient(client: YouTubeClient): Boolean {
        val clientName = client.clientName.uppercase(Locale.US)
        return clientName == "TVHTML5" || clientName == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" || clientName == "TVHTML5_SIMPLY"
    }

    private fun isWebMusicClient(client: YouTubeClient): Boolean {
        val clientName = client.clientName.uppercase(Locale.US)
        return clientName == "WEB" ||
            clientName == "WEB_REMIX" ||
            clientName == "WEB_CREATOR" ||
            clientName == "MWEB" ||
            clientName == "WEB_EMBEDDED_PLAYER"
    }

    private fun String?.normalizedOrEmpty(): String = this?.trim().orEmpty()
}
