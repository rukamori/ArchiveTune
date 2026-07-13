/**
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Tidal account login via the OAuth 2.0 Authorization Code + PKCE flow (with a Bearer-capture
 * fallback), plus subscription (HiFi/Premium) detection and account-based stream resolution.
 * Signing in unlocks the account playback path; the legacy device-code grant has been removed in
 * favour of the in-app WebView login (see [buildPkceChallenge]/[exchangePkceCode]).
 */

package moe.rukamori.archivetune.tidal

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiosource.DirectStream
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object TidalAccountManager {
    // Well-known public Tidal "TV/device" OAuth client used by open-source Tidal tooling for the
    // device authorization grant. These are not secret user credentials.
    private const val CLIENT_ID = "zU4XHVVkc2tDPo4t"
    private const val CLIENT_SECRET = "VJKhDFqJPqvsPVNBV6ukXTJmwlvbttP7wlMlrc72se4="

    // PKCE web-login client (used by open-source Tidal tooling for the authorization-code + PKCE
    // flow). Unlike the device client, this yields a durable refresh token and can unlock HiRes.
    private const val PKCE_CLIENT_ID = "6BDSRdpK9hqEBTgU"
    private const val PKCE_CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
    private const val PKCE_AUTHORIZE_ENDPOINT = "https://login.tidal.com/authorize"
    const val PKCE_REDIRECT_URI = "https://tidal.com/android/login/auth"

    // Values for [TidalAuthFlowKey], telling the refresh path which client/behaviour applies.
    const val FLOW_OAUTH = "oauth"
    const val FLOW_PKCE = "pkce"
    const val FLOW_WEBCAPTURE = "webcapture"

    private const val TOKEN_ENDPOINT = "https://auth.tidal.com/v1/oauth2/token"
    private const val API_BASE = "https://api.tidal.com/v1"
    private const val SCOPE = "r_usr+w_usr+w_sub"
    private const val COUNTRY_CODE = "US"

    private val client =
        OkHttpClient
            .Builder()
            .dns(TidalDns) // DoH fallback so login works on ISPs that DNS-block tidal.com
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    /** Result of a successful token exchange. [expiresAtMillis] is an absolute epoch time. */
    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val userId: Long?,
        val username: String?,
        val countryCode: String? = null,
    )

    /** Subscription tier resolved from the Tidal API. */
    enum class Subscription {
        UNKNOWN,
        PREMIUM,
        FREE,
    }

    /**
     * Exchanges a stored refresh token for a fresh access token via the OAuth `refresh_token`
     * grant. Tidal typically does not return a new refresh_token here, so callers should keep the
     * existing one when [TokenResult.refreshToken] is null. Returns null on failure (expired /
     * revoked / offline), signalling the caller to fall back to the public instances.
     *
     * [flow] selects which OAuth client the refresh runs against: a PKCE session must refresh with
     * the PKCE client, a device session with the device client. A [FLOW_WEBCAPTURE] session has no
     * refresh token at all, so refresh is impossible and returns null immediately (the caller then
     * prompts a re-login).
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        flow: String = FLOW_OAUTH,
    ): TokenResult? =
        withContext(Dispatchers.IO) {
            if (flow == FLOW_WEBCAPTURE) {
                Timber.tag("TidalAccount").w("web-capture session has no refresh token; re-login required")
                return@withContext null
            }
            val clientId = if (flow == FLOW_PKCE) PKCE_CLIENT_ID else CLIENT_ID
            val clientSecret = if (flow == FLOW_PKCE) PKCE_CLIENT_SECRET else CLIENT_SECRET
            val body =
                FormBody
                    .Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .add("scope", SCOPE)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        Timber.tag("TidalAccount").w("token refresh failed: %d", response.code)
                        return@use null
                    }
                    val json = JSONObject(payload)
                    val user = json.optJSONObject("user")
                    TokenResult(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token").ifBlank { null },
                        expiresAtMillis =
                            System.currentTimeMillis() + (json.optLong("expires_in", 3600L) * 1000L),
                        userId = user?.optLong("userId")?.takeIf { it > 0 },
                        username = user?.optString("username")?.ifBlank { null },
                        countryCode = user?.optString("countryCode")?.ifBlank { null },
                    )
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "token refresh error")
                null
            }
        }

    // ---------------------------------------------------------------------------------------------
    // PKCE web login (primary WebView flow) + Bearer capture (fallback).
    // ---------------------------------------------------------------------------------------------

    /**
     * A generated PKCE challenge. [authUrl] is the URL to load in the login WebView; [verifier] and
     * [uniqueKey] must be kept and passed to [exchangePkceCode] once the redirect returns a code.
     */
    data class PkceChallenge(
        val verifier: String,
        val challenge: String,
        val uniqueKey: String,
        val authUrl: String,
    )

    /** URL-safe, unpadded base64 (RFC 7636) of [bytes]. */
    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /**
     * Builds a fresh PKCE challenge (S256) and the corresponding Tidal authorize URL. The verifier
     * is a high-entropy URL-safe random string; the challenge is its SHA-256, base64url-encoded.
     */
    fun buildPkceChallenge(): PkceChallenge {
        val random = SecureRandom()
        val verifierBytes = ByteArray(64).also { random.nextBytes(it) }
        val verifier = base64UrlNoPad(verifierBytes)
        val challenge =
            base64UrlNoPad(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val uniqueKeyBytes = ByteArray(8).also { random.nextBytes(it) }
        val uniqueKey = uniqueKeyBytes.joinToString("") { "%02x".format(it) }

        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val authUrl =
            buildString {
                append(PKCE_AUTHORIZE_ENDPOINT)
                append("?response_type=code")
                append("&redirect_uri=").append(enc(PKCE_REDIRECT_URI))
                append("&client_id=").append(PKCE_CLIENT_ID)
                append("&lang=EN")
                append("&appMode=android")
                append("&client_unique_key=").append(uniqueKey)
                append("&code_challenge=").append(challenge)
                append("&code_challenge_method=S256")
                append("&restrict_signup=true")
            }
        return PkceChallenge(verifier, challenge, uniqueKey, authUrl)
    }

    /**
     * Exchanges a PKCE authorization [code] (extracted from the redirect) for access + refresh
     * tokens. Returns null on failure. The resulting [TokenResult] carries the durable refresh
     * token, so the session survives long-term via [refreshAccessToken] with [FLOW_PKCE].
     */
    suspend fun exchangePkceCode(
        code: String,
        verifier: String,
        uniqueKey: String,
    ): TokenResult? =
        withContext(Dispatchers.IO) {
            val body =
                FormBody
                    .Builder()
                    .add("code", code)
                    .add("client_id", PKCE_CLIENT_ID)
                    .add("client_secret", PKCE_CLIENT_SECRET)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", PKCE_REDIRECT_URI)
                    .add("scope", SCOPE)
                    .add("code_verifier", verifier)
                    .add("client_unique_key", uniqueKey)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        Timber.tag("TidalAccount").w("PKCE code exchange failed: %d %s", response.code, payload.take(200))
                        return@use null
                    }
                    val json = JSONObject(payload)
                    val user = json.optJSONObject("user")
                    TokenResult(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token").ifBlank { null },
                        expiresAtMillis =
                            System.currentTimeMillis() + (json.optLong("expires_in", 3600L) * 1000L),
                        userId = user?.optLong("userId")?.takeIf { it > 0 },
                        username = user?.optString("username")?.ifBlank { null },
                        countryCode = user?.optString("countryCode")?.ifBlank { null },
                    )
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "PKCE code exchange error")
                null
            }
        }

    /**
     * Validates a Bearer access token captured from the Tidal web player and resolves the account
     * identity (userId + countryCode) via the `/sessions` endpoint. Used by the WebView fallback
     * path when PKCE is unavailable. The returned [TokenResult] has no refresh token (web-player
     * tokens are short-lived); the session expiry is set conservatively so the app re-validates
     * before it goes stale. Returns null if the token is invalid.
     */
    suspend fun buildSessionFromBearer(accessToken: String): TokenResult? =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("$API_BASE/sessions")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        Timber.tag("TidalAccount").w("bearer session validation failed: %d", response.code)
                        return@use null
                    }
                    val json = JSONObject(payload)
                    TokenResult(
                        accessToken = accessToken,
                        refreshToken = null,
                        // Web-player tokens are short-lived; assume ~1h and let the app re-validate.
                        expiresAtMillis = System.currentTimeMillis() + 3600L * 1000L,
                        userId = json.optLong("userId").takeIf { it > 0 },
                        username = json.optString("username").ifBlank { null },
                        countryCode = json.optString("countryCode").ifBlank { null },
                    )
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "bearer session validation error")
                null
            }
        }

    /**
     * Resolves the subscription tier for the signed-in account. Any paid tier (HIFI, PREMIUM,
     * PREMIUM_PLUS, HIFI_PLUS, etc.) maps to [Subscription.PREMIUM]; an explicit free tier maps to
     * [Subscription.FREE]; anything indeterminate is [Subscription.UNKNOWN]. Runs on IO.
     */
    suspend fun fetchSubscription(
        accessToken: String,
        userId: Long,
    ): Subscription =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("$API_BASE/users/$userId/subscription?countryCode=$COUNTRY_CODE")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Timber.tag("TidalAccount").w("subscription lookup failed: %d", response.code)
                        return@use Subscription.UNKNOWN
                    }
                    val json = JSONObject(payload)
                    val type =
                        json
                            .optJSONObject("subscription")
                            ?.optString("type")
                            ?.uppercase()
                            .orEmpty()
                    when {
                        type.isBlank() -> Subscription.UNKNOWN
                        type.contains("FREE") -> Subscription.FREE
                        // HIFI, HIFI_PLUS, PREMIUM, PREMIUM_PLUS, etc.
                        else -> Subscription.PREMIUM
                    }
                }
            }.getOrElse {
                Timber.tag("TidalAccount").w(it, "subscription lookup error")
                Subscription.UNKNOWN
            }
        }

    /**
     * Resolves a directly-playable stream for the given metadata using the signed-in user's own
     * Tidal account (official API), rather than a public instance. Returns null if the account
     * cannot stream the track (not found, no entitlement, or an unexpected manifest type), so the
     * caller can fall back to the public instances and then YouTube.
     *
     * [audioQuality] is a Tidal API quality string: "LOW", "HIGH", "LOSSLESS" or "HI_RES_LOSSLESS".
     */
    /**
     * Thrown when the official API rejects the access token (HTTP 401). Signals the caller to
     * refresh the token via the stored refresh token and retry once, instead of silently falling
     * back to the (preview-only) public instances.
     */
    class TidalUnauthorizedException : Exception("TIDAL access token rejected (401)")

    suspend fun resolveDirectStream(
        accessToken: String,
        title: String,
        artists: List<String>,
        durationMs: Long?,
        audioQuality: String,
        cacheDir: File,
        countryCode: String = COUNTRY_CODE,
    ): DirectStream? =
        withContext(Dispatchers.IO) {
            val country = countryCode.ifBlank { COUNTRY_CODE }
            val trackId = searchTrackId(accessToken, title, artists, durationMs, country) ?: return@withContext null
            resolvePlaybackInfo(accessToken, trackId, audioQuality, durationMs, cacheDir)
        }

    /** Searches the official API for the best-matching track id. */
    private fun searchTrackId(
        accessToken: String,
        title: String,
        artists: List<String>,
        durationMs: Long?,
        countryCode: String = COUNTRY_CODE,
    ): String? {
        val primaryArtist = artists.firstOrNull().orEmpty()
        val query = URLEncoder.encode("$title $primaryArtist".trim(), "UTF-8")
        val request =
            Request
                .Builder()
                .url("$API_BASE/search/tracks?query=$query&limit=15&countryCode=$countryCode")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw TidalUnauthorizedException()
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) return@use null
                val items = JSONObject(payload).optJSONArray("items") ?: return@use null

                var bestId: String? = null
                var bestScore = Int.MIN_VALUE
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optLong("id").takeIf { it > 0 }?.toString() ?: continue
                    var score = 0
                    val candTitle = item.optString("title")
                    if (candTitle.equals(title, ignoreCase = true)) {
                        score += 50
                    } else if (candTitle.contains(title, ignoreCase = true) ||
                        title.contains(candTitle, ignoreCase = true)
                    ) {
                        score += 25
                    }
                    val candArtists =
                        item.optJSONArray("artists")?.let { arr ->
                            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                        }.orEmpty()
                    if (primaryArtist.isNotBlank() &&
                        candArtists.any { it.contains(primaryArtist, ignoreCase = true) }
                    ) {
                        score += 30
                    }
                    val candDurationMs = item.optLong("duration").takeIf { it > 0 }?.times(1000L)
                    if (durationMs != null && candDurationMs != null &&
                        abs(candDurationMs - durationMs) <= 5000L
                    ) {
                        score += 20
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
                // Require at least a title or artist hit to avoid false matches.
                if (bestScore >= 40) bestId else null
            }
        }.getOrElse {
            if (it is TidalUnauthorizedException) throw it
            Timber.tag("TidalAccount").w(it, "account track search error")
            null
        }
    }

    /**
     * Fetches playback info for a track from the official API, then delegates manifest handling to
     * [TidalAudioProvider.resolveAccountManifest] so both BTS (direct URL) and DASH (segmented
     * lossless/HiRes) manifests produce a playable stream. Previously this only accepted the BTS
     * manifest, so lossless/HiRes — which Tidal returns as DASH — always fell through, which is why
     * the signed-in account path never actually played.
     */
    private fun resolvePlaybackInfo(
        accessToken: String,
        trackId: String,
        audioQuality: String,
        durationMs: Long?,
        cacheDir: File,
    ): DirectStream? {
        val url =
            "$API_BASE/tracks/$trackId/playbackinfopostpaywall" +
                "?audioquality=$audioQuality&playbackmode=STREAM&assetpresentation=FULL"
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw TidalUnauthorizedException()
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    Timber.tag("TidalAccount").w("playbackinfo failed: %d", response.code)
                    return@use null
                }
                val json = JSONObject(payload)
                // Never serve a preview clip from the account path (we requested FULL, but be safe).
                if (json.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    Timber.tag("TidalAccount").w("playbackinfo returned PREVIEW; skipping account stream")
                    return@use null
                }
                val manifestB64 = json.optString("manifest").takeIf { it.isNotBlank() } ?: return@use null
                val manifestMime = json.optString("manifestMimeType").ifBlank { null }
                TidalAudioProvider.resolveAccountManifest(
                    manifestB64 = manifestB64,
                    declaredMimeType = manifestMime,
                    trackId = trackId,
                    quality = audioQuality,
                    durationMs = durationMs,
                    cacheDir = cacheDir,
                )
            }
        }.getOrElse {
            if (it is TidalUnauthorizedException) throw it
            Timber.tag("TidalAccount").w(it, "playbackinfo error")
            null
        }
    }
}
