/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.automotive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.design.AppleTheme
import moe.rukamori.archivetune.screens.SettingsScreen

/**
 * Entry point for the automotive in-app preferences screen.
 *
 * The legacy `AndroidAutoSettingsRoute` was removed together with the old
 * ArchiveTune settings UI; [SettingsScreen] now serves the same purpose.
 */
@AndroidEntryPoint
class AutomotiveSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets.systemBars) {
                AppleTheme {
                    SettingsScreen(onBack = ::finish)
                }
            }
        }
    }
}

