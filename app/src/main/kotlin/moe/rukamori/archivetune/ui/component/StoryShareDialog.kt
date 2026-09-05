/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.ComposeToImage

@Immutable
data class StoryShareData(
    val title: String,
    val artist: String,
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val currentPositionMs: Long? = null,
    val durationMs: Long? = null,
    val isPlaying: Boolean = true,
    val statsLabel: String? = null,
    val isObsession: Boolean = false,
)

private fun formatTimeline(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun StoryShareDialog(
    data: StoryShareData,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
            color = Color.Transparent,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.share_as_story),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    IconButton(
                        onClick = onDismiss,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }

                // 9:16 Story Card View Live Preview
                Box(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF222232),
                                            Color(0xFF14141C),
                                            Color(0xFF000000),
                                        ),
                                ),
                            ).padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Top Branding & Tag
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                                Text(
                                    text = "ArchiveTune",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    letterSpacing = 0.8.sp,
                                )
                            }
                            Text(
                                text = if (data.isPlaying) "NOW PLAYING" else "ARCHIVETUNE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                                letterSpacing = 1.2.sp,
                            )
                        }

                        // Center: Artwork, Details & Live Timeline
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(context)
                                        .data(data.thumbnailUrl)
                                        .allowHardware(false)
                                        .crossfade(true)
                                        .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(190.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .shadow(16.dp, RoundedCornerShape(20.dp)),
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = data.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = if (!data.album.isNullOrBlank()) "${data.artist} • ${data.album}" else data.artist
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Live Playback Timeline Bar
                            if (data.durationMs != null && data.durationMs > 0) {
                                val posMs = (data.currentPositionMs ?: 0L).coerceIn(0L, data.durationMs)
                                val progress = (posMs.toFloat() / data.durationMs.toFloat()).coerceIn(0f, 1f)

                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = 0.2f)),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth(progress)
                                                    .fillMaxHeight()
                                                    .background(Color.White),
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = formatTimeline(posMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.6f),
                                        )
                                        Text(
                                            text = formatTimeline(data.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }

                            if (data.statsLabel != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (data.isObsession) Color(0xFFFF5722).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Text(
                                        text = data.statsLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (data.isObsession) Color(0xFFFF8A65) else Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        // Bottom watermark note
                        Text(
                            text = "LISTENED ON ARCHIVETUNE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 1.sp,
                        )
                    }
                }

                // Bottom Share Action Buttons
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel), color = Color.White)
                    }
                    Button(
                        onClick = {
                            if (isSharing) return@Button
                            isSharing = true
                            coroutineScope.launch {
                                try {
                                    val bitmap =
                                        ComposeToImage.createStoryImage(
                                            context = context,
                                            coverArtUrl = data.thumbnailUrl,
                                            title = data.title,
                                            artist = data.artist,
                                            album = data.album,
                                            statsLabel = data.statsLabel,
                                            isObsession = data.isObsession,
                                            currentPositionMs = data.currentPositionMs,
                                            durationMs = data.durationMs,
                                            isPlaying = data.isPlaying,
                                        )
                                    val fileName = "story_${System.currentTimeMillis()}"
                                    val contentUri = ComposeToImage.saveBitmapAsFile(context, bitmap, fileName)
                                    val shareIntent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.share_story_card),
                                        ),
                                    )
                                    onDismiss()
                                } catch (_: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSharing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isSharing,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isSharing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(painterResource(R.drawable.share), contentDescription = null)
                            }
                            Text(stringResource(R.string.share_story_card))
                        }
                    }
                }
            }
        }
    }
}
