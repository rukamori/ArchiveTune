/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiException
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiFailureKind
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GvsPoTokenVerifierTest {
    @Test
    fun replacesMinterWhoseTokensYouTubeRejectsBeyondTheUncheckedPrefix() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = setOf(2L))
            val googleVideo = FakeGoogleVideo(acceptedTokens = { minter.acceptedVideoTokens() })
            val verifier = verifier(minter, googleVideo)

            val stream = verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            assertEquals("video-2", stream.url.queryParameter("pot"))
            assertEquals(listOf(1L), minter.replacedGenerations)
        }

    @Test
    fun trustsAnAcceptedMinterForLaterStreamsWithoutAnotherRequest() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = setOf(1L))
            val googleVideo = FakeGoogleVideo(acceptedTokens = { minter.acceptedVideoTokens() })
            val verifier = verifier(minter, googleVideo)

            verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)
            val secondStream = verifier.verify(OTHER_STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            assertEquals("video-1", secondStream.url.queryParameter("pot"))
            assertEquals(1, googleVideo.requests.size)
        }

    @Test
    fun replacesTrustedMinterAfterYouTubeRejectsItsStreamMidPlayback() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = setOf(1L))
            val googleVideo = FakeGoogleVideo(acceptedTokens = { minter.acceptedVideoTokens() })
            val verifier = verifier(minter, googleVideo)
            val playingStream = verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            minter.goodGenerations = setOf(2L)
            verifier.reportRejected(playingStream.minterGeneration, playingStream.binding)
            val retriedStream = verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            assertEquals("video-2", retriedStream.url.queryParameter("pot"))
            assertEquals(listOf(1L), minter.replacedGenerations)
        }

    @Test
    fun keepsMinterWhenTheTokenCheckCannotReachYouTube() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = setOf(1L))
            val googleVideo = FakeGoogleVideo(acceptedTokens = { minter.acceptedVideoTokens() }, reachable = false)
            val verifier = verifier(minter, googleVideo)

            val stream = verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)
            googleVideo.reachable = true
            verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            assertEquals("video-1", stream.url.queryParameter("pot"))
            assertEquals(emptyList<Long>(), minter.replacedGenerations)
            assertEquals(2, googleVideo.requests.size)
        }

    @Test
    fun shortStreamInsideTheUncheckedPrefixDoesNotVouchForItsMinter() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = setOf(2L))
            val googleVideo = FakeGoogleVideo(acceptedTokens = { minter.acceptedVideoTokens() })
            val verifier = verifier(minter, googleVideo)

            val shortStream = verifier.verify(STREAM_URL, SHORT_CONTENT_LENGTH, minter::mint)
            val songStream = verifier.verify(OTHER_STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            assertEquals("video-1", shortStream.url.queryParameter("pot"))
            assertEquals("video-2", songStream.url.queryParameter("pot"))
        }

    @Test
    fun checksTokensBeyondTheUncheckedPrefixWhenContentLengthIsUnknown() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = setOf(2L))
            val googleVideo = FakeGoogleVideo(acceptedTokens = { minter.acceptedVideoTokens() })
            val verifier = verifier(minter, googleVideo)

            val stream = verifier.verify(STREAM_URL, UNKNOWN_CONTENT_LENGTH, minter::mint)

            assertEquals("video-2", stream.url.queryParameter("pot"))
            assertTrue(googleVideo.requests.all { it.first >= UNCHECKED_PREFIX_BYTES })
        }

    @Test
    fun usesSessionBoundTokenWhenYouTubeOnlyAcceptsSessionBinding() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = emptySet())
            val googleVideo = FakeGoogleVideo(acceptedTokens = { setOf("session-1") })
            val verifier = verifier(minter, googleVideo)

            val stream = verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint)

            assertEquals("session-1", stream.url.queryParameter("pot"))
            assertEquals(GvsPoTokenBinding.SESSION, stream.binding)
            assertEquals(emptyList<Long>(), minter.replacedGenerations)
        }

    @Test
    fun failsAsRejectedTokenAfterBoundedMinterReplacements() =
        runBlocking {
            val minter = FakeMinter(goodGenerations = emptySet())
            val googleVideo = FakeGoogleVideo(acceptedTokens = { emptySet() })
            val verifier = verifier(minter, googleVideo)

            val failure =
                runCatching { verifier.verify(STREAM_URL, SONG_CONTENT_LENGTH, minter::mint) }
                    .exceptionOrNull() as YoutubeiException

            assertEquals(YoutubeiFailureKind.PO_TOKEN, failure.kind)
            assertEquals(403, failure.httpStatus)
            assertEquals(listOf(1L, 2L, 3L), minter.replacedGenerations)
        }

    private fun verifier(
        minter: FakeMinter,
        googleVideo: FakeGoogleVideo,
    ) = GvsPoTokenVerifier(
        maxMinterAttempts = 3,
        replaceMinter = minter::replace,
        probeStatus = googleVideo::status,
    )

    private class FakeMinter(
        var goodGenerations: Set<Long>,
    ) {
        var generation = 1L
            private set
        val replacedGenerations = mutableListOf<Long>()

        fun mint(): MintedGvsPoTokens =
            MintedGvsPoTokens(
                videoToken = "video-$generation",
                sessionToken = "session-$generation",
                minterGeneration = generation,
            )

        fun replace(rejectedGeneration: Long) {
            if (rejectedGeneration != generation) return
            replacedGenerations += rejectedGeneration
            generation += 1
        }

        fun acceptedVideoTokens(): Set<String> = goodGenerations.mapTo(mutableSetOf()) { "video-$it" }
    }

    private class FakeGoogleVideo(
        private val acceptedTokens: () -> Set<String>,
        var reachable: Boolean = true,
    ) {
        val requests = mutableListOf<LongRange>()

        fun status(
            url: HttpUrl,
            byteRange: LongRange,
            requestHeaders: Map<String, String>,
        ): Int? {
            requests += byteRange
            if (!reachable) return null
            if (byteRange.first < 0L) return 416
            if (byteRange.last < UNCHECKED_PREFIX_BYTES) return 206
            return if (url.queryParameter("pot") in acceptedTokens()) 206 else 403
        }
    }

    private companion object {
        const val UNCHECKED_PREFIX_BYTES = 1_000_000L
        const val SONG_CONTENT_LENGTH = 4_905_656L
        const val SHORT_CONTENT_LENGTH = 900_000L
        const val UNKNOWN_CONTENT_LENGTH = 0L
        val STREAM_URL: HttpUrl =
            "https://rr8---sn-example.googlevideo.com/videoplayback?expire=1&itag=251&n=abc&pot=video-1".toHttpUrl()
        val OTHER_STREAM_URL: HttpUrl =
            "https://rr2---sn-example.googlevideo.com/videoplayback?expire=1&itag=251&n=def&pot=video-1".toHttpUrl()
    }
}
