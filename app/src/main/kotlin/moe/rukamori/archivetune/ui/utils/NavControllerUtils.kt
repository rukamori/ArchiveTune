/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import android.net.Uri
import androidx.navigation.NavController
import moe.rukamori.archivetune.innertube.models.PODCAST_SHOW_BROWSE_PREFIX
import moe.rukamori.archivetune.ui.screens.Screens

internal fun NavController.navigateToArtist(artistId: String?) {
    navigateToMediaDetail(artistId, "artist")
}

internal fun NavController.navigateToAlbum(albumId: String?) {
    navigateToMediaDetail(albumId, "album")
}

private fun NavController.navigateToMediaDetail(id: String?, destination: String) {
    if (id.isNullOrBlank()) return
    val route = if (id.startsWith(PODCAST_SHOW_BROWSE_PREFIX)) "podcast" else destination
    navigate("$route/${Uri.encode(id)}")
}

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
