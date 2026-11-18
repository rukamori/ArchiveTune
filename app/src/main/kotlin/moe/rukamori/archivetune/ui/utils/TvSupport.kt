/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.RippleThemeConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalIsTvDevice = staticCompositionLocalOf { false }

val TvRippleThemeConfiguration =
    RippleThemeConfiguration(
        focus =
            RippleThemeConfiguration.Focus.InsetRing(
                outerStrokeInset = 0.dp,
                outerStrokeWidth = 2.dp,
                innerStrokeInset = 1.dp,
                innerStrokeWidth = 3.dp,
            ),
    )

@Composable
@ReadOnlyComposable
fun tvFocusIndicationOrNull(): Indication? = if (LocalIsTvDevice.current) LocalIndication.current else null

@Suppress("DEPRECATION")
fun Context.isTvDevice(): Boolean {
    val isTelevisionUiMode =
        (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    return isTelevisionUiMode ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}
