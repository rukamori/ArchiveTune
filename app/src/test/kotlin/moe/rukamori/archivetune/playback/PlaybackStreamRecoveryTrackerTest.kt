/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamRecoveryTrackerTest {
    @Test
    fun allowsUpToDefaultMaxAttemptsForSameMedia() {
        val tracker = PlaybackStreamRecoveryTracker(maxAttempts = 2)

        assertTrue(tracker.registerRetryAttempt("song-1"))
        assertTrue(tracker.registerRetryAttempt("song-1"))
        assertFalse(tracker.registerRetryAttempt("song-1"))
    }

    @Test
    fun resetsAttemptsWhenPlaybackRecovers() {
        val tracker = PlaybackStreamRecoveryTracker(maxAttempts = 2)

        assertTrue(tracker.registerRetryAttempt("song-1"))
        assertTrue(tracker.registerRetryAttempt("song-1"))
        assertFalse(tracker.registerRetryAttempt("song-1"))

        tracker.onPlaybackRecovered("song-1")

        assertTrue(tracker.registerRetryAttempt("song-1"))
    }

    @Test
    fun resetsAttemptsWhenMediaItemChanges() {
        val tracker = PlaybackStreamRecoveryTracker(maxAttempts = 2)

        assertTrue(tracker.registerRetryAttempt("song-1"))
        assertTrue(tracker.registerRetryAttempt("song-1"))
        assertFalse(tracker.registerRetryAttempt("song-1"))

        tracker.onMediaItemChanged("song-2")

        assertTrue(tracker.registerRetryAttempt("song-2"))
        assertTrue(tracker.registerRetryAttempt("song-2"))
        assertFalse(tracker.registerRetryAttempt("song-2"))
    }
}
