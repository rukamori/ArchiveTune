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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetState

@Immutable
data class TitleArtistSpec(
    /** Typography for the song title line. */
    val titleStyle: TextStyle,
    /** Typography for the artist line. */
    val artistStyle: TextStyle,
    /** Weight applied on top of [titleStyle]. */
    val titleFontWeight: FontWeight = FontWeight.Bold,
    /** Optional weight applied on top of [artistStyle]. Null keeps the style's own weight. */
    val artistFontWeight: FontWeight? = null,
    /** Horizontal text alignment for both lines. */
    val textAlign: TextAlign = TextAlign.Start,
    /** When true, the column centers its children horizontally (used by V9). */
    val centered: Boolean = false,
    /** Alpha applied to the artist foreground color (V9 dims the artist line to 0.72). */
    val artistColorAlpha: Float = 1f,
    /** Vertical gap between the title and artist lines. */
    val spacing: Int = 6,
    /** When true, each artist is individually tappable (navigates to that artist). */
    val perArtistClickable: Boolean = true,
    /** When true, long-pressing the title/artist copies the text to the clipboard. */
    val enableCopy: Boolean = true,
    /** When true, the title fades in/out as the current track changes. */
    val animateTitleOnTrackChange: Boolean = true,
) {
    companion object {
        /** Classic styles V1–V4 and V6. */
        @Composable
        fun classic(): TitleArtistSpec = TitleArtistSpec(
            titleStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            // Classic artist line historically uses titleMedium at a fixed 16.sp.
            artistStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            titleFontWeight = FontWeight.Bold,
        )

        /** Shared by V7 and V8 (left-aligned, same typography as classic). */
        @Composable
        fun v8(): TitleArtistSpec = TitleArtistSpec(
            titleStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            artistStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            titleFontWeight = FontWeight.Bold,
        )

        /** Center-aligned V9 with a larger title and dimmed, semi-bold artist line. */
        @Composable
        fun v9(): TitleArtistSpec = TitleArtistSpec(
            titleStyle = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            artistStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            titleFontWeight = FontWeight.Bold,
            artistFontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            centered = true,
            artistColorAlpha = 0.72f,
        )
    }
}

@Immutable
class PlayerTitleActions(
    val onTitleClick: () -> Unit,
    val onArtistClick: (artistId: String) -> Unit,
    val onCopyTitle: () -> Unit,
    val onCopyArtist: () -> Unit,
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
    return remember(mediaMetadata, navController, state, context, clipboardManager, artistLine) {
        PlayerTitleActions(
            onTitleClick = {
                mediaMetadata.album?.let { album ->
                    // NOTE: This intentionally preserves the current (pre-fix) behavior so the
                    // extraction commit is behavior-preserving. A follow-up commit replaces this
                    // single line with `state.collapseSoft()` to fix the collapse-on-title bug
                    // (snapTo skips animation AND does not update the sheet anchor, leaving
                    // isCollapsed/isExpanded out of sync).
                    state.snapTo(state.collapsedBound)
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
            onCopyArtist = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("Copied Artist", artistLine)
                )
                Toast.makeText(context, "Copied Artist", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
fun PlayerTitleArtist(
    mediaMetadata: MediaMetadata,
    foreground: Color,
    spec: TitleArtistSpec,
    actions: PlayerTitleActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (spec.centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        TitleLine(
            title = mediaMetadata.title,
            foreground = foreground,
            spec = spec,
            actions = actions,
        )

        Spacer(Modifier.height(spec.spacing.dp))

        ArtistLine(
            mediaMetadata = mediaMetadata,
            foreground = foreground,
            spec = spec,
            actions = actions,
        )
    }
}

@Composable
private fun TitleLine(
    title: String,
    foreground: Color,
    spec: TitleArtistSpec,
    actions: PlayerTitleActions,
) {
    val clickModifier = Modifier
        .fillMaxWidth()
        .basicMarquee()
        .combinedClickable(
            enabled = true,
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = actions.onTitleClick,
            onLongClick = if (spec.enableCopy) actions.onCopyTitle else null,
        )

    if (spec.animateTitleOnTrackChange) {
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "player_title",
        ) { animatedTitle ->
            androidx.compose.material3.Text(
                text = animatedTitle,
                style = spec.titleStyle,
                fontWeight = spec.titleFontWeight,
                color = foreground,
                textAlign = spec.textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = clickModifier,
            )
        }
    } else {
        androidx.compose.material3.Text(
            text = title,
            style = spec.titleStyle,
            fontWeight = spec.titleFontWeight,
            color = foreground,
            textAlign = spec.textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = clickModifier,
        )
    }
}

@Composable
private fun ArtistLine(
    mediaMetadata: MediaMetadata,
    foreground: Color,
    spec: TitleArtistSpec,
    actions: PlayerTitleActions,
) {
    val artistColor = foreground.copy(alpha = spec.artistColorAlpha)
    val artistStyle =
        if (spec.artistFontWeight != null) spec.artistStyle.copy(fontWeight = spec.artistFontWeight)
        else spec.artistStyle

    if (spec.perArtistClickable) {
        // Each artist is individually tappable. The annotated string carries the artist id per
        // span; a pointerInput captures the tap offset so we can resolve which artist was hit.
        val annotatedString = buildAnnotatedString {
            mediaMetadata.artists.forEachIndexed { index, artist ->
                val annotation = artist.id.orEmpty()
                pushStringAnnotation(tag = "artist", annotation = annotation)
                withStyle(SpanStyle(color = artistColor)) {
                    append(artist.name)
                }
                pop()
                if (index != mediaMetadata.artists.lastIndex) append(", ")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
                .padding(end = 12.dp)
        ) {
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            var clickOffset by remember { mutableStateOf<Offset?>(null) }
            androidx.compose.material3.Text(
                text = annotatedString,
                style = artistStyle.copy(color = artistColor),
                textAlign = spec.textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull()?.position?.let { clickOffset = it }
                            }
                        }
                    }
                    .combinedClickable(
                        enabled = true,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            val tapPosition = clickOffset
                            val layout = layoutResult
                            if (tapPosition != null && layout != null) {
                                val offset = layout.getOffsetForPosition(tapPosition)
                                annotatedString.getStringAnnotations(offset, offset)
                                    .firstOrNull()
                                    ?.let { actions.onArtistClick(it.item) }
                            }
                        },
                        onLongClick = if (spec.enableCopy) actions.onCopyArtist else null,
                    ),
            )
        }
    } else {
        // Fallback: single tap target that navigates to the first artist.
        val firstArtistId = mediaMetadata.artists.firstOrNull()?.id.orEmpty()
        val artistLine = remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(", ") { it.name }
        }
        androidx.compose.material3.Text(
            text = artistLine,
            style = artistStyle,
            color = artistColor,
            textAlign = spec.textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
                .combinedClickable(
                    enabled = true,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { actions.onArtistClick(firstArtistId) },
                    onLongClick = if (spec.enableCopy) actions.onCopyArtist else null,
                ),
        )
    }
}
