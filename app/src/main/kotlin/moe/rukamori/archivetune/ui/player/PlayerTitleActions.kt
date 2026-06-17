/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetState

@Immutable
class PlayerTitleActions(
    /** Navigate to the current song's album (no-op if the song has no album). */
    val onTitleClick: () -> Unit,
    /** Navigate to a specific artist by id (no-op for blank ids). */
    val onArtistClick: (artistId: String) -> Unit,
    /** Navigate to the first artist, if any. Convenience for styles without per-artist hit testing. */
    val onFirstArtistClick: () -> Unit,
    /** Copy the song title to the clipboard and show a toast. */
    val onCopyTitle: () -> Unit,
    /** Copy the comma-joined artist names to the clipboard and show a toast. */
    val onCopyArtists: () -> Unit,
)

@Composable
fun rememberPlayerTitleActions(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
): PlayerTitleActions {
    val context = LocalContext.current
    val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val artistLine = remember(mediaMetadata.artists) {
        mediaMetadata.artists.joinToString(", ") { it.name }
    }
    val firstArtistId = mediaMetadata.artists.firstOrNull()?.id

    return remember(mediaMetadata, navController, state, context, clipboardManager, artistLine, firstArtistId) {
        val navigateToArtist: (String) -> Unit = { artistId ->
            if (artistId.isNotBlank()) {
                state.collapseSoft()
                navController.navigate("artist/$artistId")
            }
        }
        PlayerTitleActions(
            onTitleClick = {
                mediaMetadata.album?.let { album ->
                    state.collapseSoft()
                    navController.navigate("album/${album.id}")
                }
            },
            onArtistClick = navigateToArtist,
            onFirstArtistClick = { firstArtistId?.let(navigateToArtist) },
            onCopyTitle = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Copied Title", mediaMetadata.title)
                )
                Toast.makeText(context, "Copied Title", Toast.LENGTH_SHORT).show()
            },
            onCopyArtists = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Copied Artist", artistLine)
                )
                Toast.makeText(context, "Copied Artist", Toast.LENGTH_SHORT).show()
            },
        )
    }
}
