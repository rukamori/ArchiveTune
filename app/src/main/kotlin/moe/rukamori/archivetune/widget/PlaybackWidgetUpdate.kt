/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.ComponentName
import android.content.Context
import moe.rukamori.archivetune.playback.MusicService

internal suspend fun requestPlaybackWidgetUpdate(context: Context) {
    val serviceIntent = android.content.Intent(context, MusicService::class.java)
    val connection =
        object : android.content.ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: android.os.IBinder?,
            ) {
                val service = (binder as? MusicService.MusicBinder)?.service
                service?.updateWidget()
                runCatching { context.unbindService(this) }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

    runCatching {
        context.bindService(serviceIntent, connection, 0)
    }
}
