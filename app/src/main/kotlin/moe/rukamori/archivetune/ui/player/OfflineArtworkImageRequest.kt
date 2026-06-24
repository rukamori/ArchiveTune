/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun rememberOfflineArtworkImageRequest(imageUrl: String?): ImageRequest? {
    val context = LocalContext.current
    val (forceCpuRendering) =
        rememberPreference(
            key = booleanPreferencesKey("force_cpu_rendering"),
            defaultValue = true,
        )
    return remember(context, imageUrl, forceCpuRendering) {
        imageUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { url ->
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .apply { if (forceCpuRendering) allowHardware(false) }
                    .build()
            }
    }
}
