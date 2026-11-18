/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIBRARY = "TOGGLE_LIBRARY"
    const val ACTION_TOGGLE_START_RADIO = "TOGGLE_START_RADIO"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_TOGGLE_DOWNLOAD = "TOGGLE_DOWNLOAD"
    const val ACTION_TOGGLE_SLEEP_TIMER = "TOGGLE_SLEEP_TIMER"
    const val ACTION_CLOSE_PLAYBACK = "CLOSE_PLAYBACK"
    const val ACTION_SEEK_BACK_INCREMENT = "SEEK_BACK_INCREMENT"
    const val ACTION_SEEK_FORWARD_INCREMENT = "SEEK_FORWARD_INCREMENT"
    val CommandToggleLibrary = SessionCommand(ACTION_TOGGLE_LIBRARY, Bundle.EMPTY)
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleStartRadio = SessionCommand(ACTION_TOGGLE_START_RADIO, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandToggleDownload = SessionCommand(ACTION_TOGGLE_DOWNLOAD, Bundle.EMPTY)
    val CommandToggleSleepTimer = SessionCommand(ACTION_TOGGLE_SLEEP_TIMER, Bundle.EMPTY)
    val CommandClosePlayback = SessionCommand(ACTION_CLOSE_PLAYBACK, Bundle.EMPTY)
    val CommandSeekBackIncrement = SessionCommand(ACTION_SEEK_BACK_INCREMENT, Bundle.EMPTY)
    val CommandSeekForwardIncrement = SessionCommand(ACTION_SEEK_FORWARD_INCREMENT, Bundle.EMPTY)
}
