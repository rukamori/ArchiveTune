/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

internal class PlaybackStreamRecoveryTracker(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    private var currentMediaId: String? = null
    private var attemptCount: Int = 0
    private var lastAttemptTimestamp: Long = 0L

    fun registerRetryAttempt(
        mediaId: String,
        maxAttemptsForCall: Int = maxAttempts,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (currentMediaId != mediaId) {
            currentMediaId = mediaId
            attemptCount = 1
            lastAttemptTimestamp = now
            return true
        }

        if (now - lastAttemptTimestamp > RETRY_RESET_WINDOW_MS) {
            attemptCount = 1
            lastAttemptTimestamp = now
            return true
        }

        if (attemptCount >= maxAttemptsForCall) {
            return false
        }

        attemptCount++
        lastAttemptTimestamp = now
        return true
    }

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && currentMediaId == mediaId) {
            reset()
        }
    }

    fun onMediaItemChanged(currentMediaId: String?) {
        if (this.currentMediaId != currentMediaId) {
            reset()
        }
    }

    private fun reset() {
        currentMediaId = null
        attemptCount = 0
        lastAttemptTimestamp = 0L
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 2
        private const val RETRY_RESET_WINDOW_MS = 30_000L
    }
}
