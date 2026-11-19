/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import moe.rukamori.archivetune.MainActivity

class MusicRecognitionCaptureActivity : ComponentActivity() {
    private val listenNow: Boolean
        get() = intent.getBooleanExtra(ExtraListenNow, false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                requestOverlayPermission()
            } else {
                finishFlow(started = false)
            }
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingRecognition()
            } else {
                finishFlow(started = false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            beginPermissionFlow()
        }
    }

    private fun beginPermissionFlow() {
        val missingPermissions =
            buildList {
                if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    add(Manifest.permission.RECORD_AUDIO)
                }
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

        if (missingPermissions.isEmpty()) {
            requestOverlayPermission()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingRecognition()
        } else {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }

    private fun startFloatingRecognition() {
        ContextCompat.startForegroundService(
            this,
            FloatingMusicRecognitionService.showIntent(this, listen = listenNow),
        )
        finishFlow(started = true)
    }

    private fun finishFlow(started: Boolean) {
        if (!started && listenNow) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_MUSIC_RECOGNITION
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
        setResult(if (started) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val ExtraListenNow = "floating_recognition_listen_now"

        fun intent(
            context: Context,
            listenNow: Boolean,
        ): Intent =
            Intent(context, MusicRecognitionCaptureActivity::class.java).apply {
                putExtra(ExtraListenNow, listenNow)
            }
    }
}
