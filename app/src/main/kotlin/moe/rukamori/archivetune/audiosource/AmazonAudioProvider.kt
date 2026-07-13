/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Amazon Music source via a public instance (e.g. the Monochrome community's `amz.*` host).
 *
 * IMPORTANT — best-effort only. The Amazon instance is ASIN-based and exposes NO search endpoint,
 * and it is gated behind Cloudflare Turnstile. That means:
 *   - A YouTube-sourced track cannot be mapped to an Amazon ASIN automatically (there is no public
 *     song -> ASIN lookup), so [resolveByMetadata] returns null in normal use.
 *   - Requests require either a configured `bypass_token` (set by the instance operator) or a
 *     Turnstile JWT obtained interactively in a browser.
 * The provider is wired into the framework so it is ready if/when an ASIN source or working bypass
 * token is available, but it will typically fall through to the next configured source.
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AudioSourceType
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AmazonAudioProvider {
    const val DEFAULT_INSTANCE = "https://amz.geeked.wtf"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    /**
     * Attempts to resolve a stream from track metadata. Amazon has no search endpoint, so unless a
     * song -> ASIN mapping exists this returns null and the caller falls through to the next source.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun resolveByMetadata(
        title: String,
        artists: List<String>,
        durationMs: Long?,
        instanceBaseUrl: String = DEFAULT_INSTANCE,
        bypassToken: String? = null,
    ): DirectStream? {
        Timber
            .tag("AmazonAudio")
            .d("Amazon has no search endpoint; cannot map \"%s\" to an ASIN. Skipping.", title)
        return null
    }

    /**
     * Resolves a decrypted stream for a known Amazon ASIN. The instance's `/decrypt` endpoint
     * returns already-decrypted audio bytes (server-side Widevine), so the returned URI is directly
     * playable. Returns null if the instance rejects the request (e.g. Turnstile challenge / 428).
     */
    suspend fun resolveByAsin(
        asin: String,
        quality: String = "HIGH",
        instanceBaseUrl: String = DEFAULT_INSTANCE,
        bypassToken: String? = null,
        turnstileJwt: String? = null,
    ): DirectStream? =
        withContext(Dispatchers.IO) {
            val base = instanceBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_INSTANCE }
            val streamUrl =
                buildString {
                    append("$base/api/stream/$asin/decrypt?quality=$quality")
                    if (!bypassToken.isNullOrBlank()) append("&bypass_token=$bypassToken")
                }
            // Probe with a HEAD-like GET to detect a Turnstile/challenge before handing the URL to
            // the player. If the probe is rejected we skip Amazon for this track.
            val probe =
                Request
                    .Builder()
                    .url(streamUrl)
                    .apply { if (!turnstileJwt.isNullOrBlank()) header("X-Turnstile-JWT", turnstileJwt) }
                    .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                    .get()
                    .build()
            runCatching {
                client.newCall(probe).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag("AmazonAudio").w("Amazon stream rejected: %d", response.code)
                        return@use null
                    }
                    val contentLength = response.body?.contentLength()?.takeIf { it > 0 }
                    DirectStream(
                        uri = streamUrl,
                        mimeType = "audio/flac",
                        codecs = "flac",
                        contentLength = contentLength,
                        label = "Amazon Music $quality",
                        source = AudioSourceType.AMAZON,
                    )
                }
            }.getOrElse {
                Timber.tag("AmazonAudio").w(it, "Amazon resolution error")
                null
            }
        }
}
