/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import moe.rukamori.archivetune.R

class MusicRecognitionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (MusicRecognitionRuntimeState.state != BackgroundRecognitionState.Idle) {
            startService(BackgroundMusicRecognitionService.cancelIntent(this))
            return
        }

        launchAndCollapse(
            Intent(this, MusicRecognitionTileActionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    private fun launchAndCollapse(launchIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            val recognitionState = MusicRecognitionRuntimeState.state
            state =
                if (recognitionState == BackgroundRecognitionState.Idle) {
                    Tile.STATE_INACTIVE
                } else {
                    Tile.STATE_ACTIVE
                }
            label = getString(R.string.music_recognition)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle =
                    when (recognitionState) {
                        BackgroundRecognitionState.Idle ->
                            getString(R.string.music_recognition_tap_to_listen)
                        BackgroundRecognitionState.Listening ->
                            getString(R.string.music_recognition_listening)
                        BackgroundRecognitionState.Processing ->
                            getString(R.string.music_recognition_processing)
                    }
            }
            icon = Icon.createWithResource(this@MusicRecognitionTileService, R.drawable.mic)
            updateTile()
        }
    }
}
