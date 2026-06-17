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

/**
 * Centralized *behavior* for the title/artist block shared by every player design style.
 *
 * This intentionally contains **no UI** — each player style keeps rendering its own `Text`s,
 * layout, typography and decorations. Only the tap/long-press behavior lives here, so:
 *  - the historical inconsistency (title used `snapTo`, artist used `collapseSoft`) has a single
 *    source of truth and is fixed in one place, and
 *  - the behavior can no longer drift between styles.
 *
 * Adding a new player style does **not** require touching this file: just call
 * [rememberPlayerTitleActions] and wire the callbacks into whatever UI the style draws.
 */
@Immutable
class PlayerTitleActions(
    /** Navigate to the current song's album (no-op if the song has no album). */
    val onTitleClick: () -> Unit,
    /** Navigate to a specific artist by id (no-op for blank ids). */
    val onArtistClick: (artistId: String) -> Unit,
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

    return remember(mediaMetadata, navController, state, artistLine) {
        PlayerTitleActions(
            onTitleClick = {
                mediaMetadata.album?.let { album ->
                    // collapseSoft animates the sheet AND updates its internal anchor, keeping
                    // isCollapsed/isExpanded in sync. (Previously this used snapTo, which jumped
                    // without animation and left the anchor stale, so tapping the title from the
                    // home screen failed to collapse the player reliably.)
                    state.collapseSoft()
                    navController.navigate("album/${album.id}")
                }
            },
            onArtistClick = { artistId ->
                if (artistId.isNotBlank()) {
                    state.collapseSoft()
                    navController.navigate("artist/$artistId")
                }
            },
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
