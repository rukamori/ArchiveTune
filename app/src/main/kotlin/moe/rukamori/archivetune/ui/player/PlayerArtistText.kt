/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import moe.rukamori.archivetune.models.MediaMetadata

@Composable
fun ClickableArtists(
    artists: List<MediaMetadata.Artist>,
    onArtistClick: (artistId: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val annotatedString = buildAnnotatedString {
        artists.forEachIndexed { index, artist ->
            pushStringAnnotation(tag = "artist", annotation = artist.id.orEmpty())
            append(artist.name)
            pop()
            if (index != artists.lastIndex) append(", ")
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var clickOffset by remember { mutableStateOf<Offset?>(null) }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult = it },
        modifier = modifier
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
                            ?.let { onArtistClick(it.item) }
                    }
                },
                onLongClick = onLongClick,
            ),
    )
}
