/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiException
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiFailureKind
import okhttp3.HttpUrl

enum class GvsPoTokenBinding {
    VIDEO,
    SESSION,
}

internal data class MintedGvsPoTokens(
    val videoToken: String,
    val sessionToken: String?,
    val minterGeneration: Long,
)

internal data class VerifiedGvsStream(
    val url: HttpUrl,
    val binding: GvsPoTokenBinding,
    val minterGeneration: Long,
)

internal class GvsPoTokenVerifier(
    private val maxMinterAttempts: Int,
    private val replaceMinter: suspend (rejectedGeneration: Long) -> Unit,
    private val probeStatus: suspend (url: HttpUrl, byteRange: LongRange, requestHeaders: Map<String, String>) -> Int?,
) {
    @Volatile
    private var acceptedMinter: AcceptedMinter? = null

    suspend fun verify(
        streamUrl: HttpUrl,
        contentLength: Long,
        mint: suspend () -> MintedGvsPoTokens,
        requestHeaders: Map<String, String> = emptyMap(),
    ): VerifiedGvsStream {
        val probeRange = checkedByteRange(contentLength)
        repeat(maxMinterAttempts) {
            val tokens = mint()
            val candidates = tokens.candidates()
            acceptedMinter
                ?.takeIf { it.generation == tokens.minterGeneration }
                ?.let { accepted -> candidates.firstOrNull { it.binding == accepted.binding } }
                ?.let { return streamUrl.verifiedWith(it, tokens) }
            if (contentLength in 1L..UNCHECKED_PREFIX_BYTES) {
                return streamUrl.verifiedWith(candidates.first(), tokens)
            }
            for (candidate in candidates) {
                val stream = streamUrl.verifiedWith(candidate, tokens)
                when (probeStatus(stream.url, probeRange, requestHeaders)) {
                    in 200..299 -> {
                        acceptedMinter = AcceptedMinter(tokens.minterGeneration, candidate.binding)
                        return stream
                    }
                    HTTP_FORBIDDEN -> Unit
                    else -> return stream
                }
            }
            replaceMinter(tokens.minterGeneration)
        }
        throw YoutubeiException(
            kind = YoutubeiFailureKind.PO_TOKEN,
            message = "YouTube rejected playback tokens from $maxMinterAttempts token generators",
            httpStatus = HTTP_FORBIDDEN,
        )
    }

    fun reportRejected(
        minterGeneration: Long,
        binding: GvsPoTokenBinding,
    ) {
        val rejected = AcceptedMinter(minterGeneration, binding)
        synchronized(this) {
            if (acceptedMinter == rejected) acceptedMinter = null
        }
    }

    private fun checkedByteRange(contentLength: Long): LongRange {
        val firstByte = if (contentLength > UNCHECKED_PREFIX_BYTES) contentLength - 1L else UNCHECKED_PREFIX_BYTES
        return firstByte..firstByte
    }

    private fun HttpUrl.verifiedWith(
        candidate: TokenCandidate,
        tokens: MintedGvsPoTokens,
    ): VerifiedGvsStream =
        VerifiedGvsStream(
            url = newBuilder().setQueryParameter("pot", candidate.token).build(),
            binding = candidate.binding,
            minterGeneration = tokens.minterGeneration,
        )

    private fun MintedGvsPoTokens.candidates(): List<TokenCandidate> =
        listOfNotNull(
            TokenCandidate(GvsPoTokenBinding.VIDEO, videoToken),
            sessionToken?.let { TokenCandidate(GvsPoTokenBinding.SESSION, it) },
        )

    private data class TokenCandidate(
        val binding: GvsPoTokenBinding,
        val token: String,
    )

    private data class AcceptedMinter(
        val generation: Long,
        val binding: GvsPoTokenBinding,
    )

    private companion object {
        const val UNCHECKED_PREFIX_BYTES = 1_000_000L
        const val HTTP_FORBIDDEN = 403
    }
}
