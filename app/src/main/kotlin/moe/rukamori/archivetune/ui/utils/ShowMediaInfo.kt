/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState

private enum class MediaInfoTab(
    @StringRes val labelRes: Int,
) {
    Information(R.string.information),
    Details(R.string.details),
    Numbers(R.string.numbers),
}

private data class MediaInfoQuickFact(
    @DrawableRes val iconRes: Int,
    val text: String,
)

private data class MediaInfoDetail(
    val label: String,
    val value: String,
    val multiline: Boolean = false,
)

private data class MediaInfoMetric(
    @StringRes val labelRes: Int,
    val value: String,
)

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank()) return

    val context = LocalContext.current
    val database = LocalDatabase.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current
    val song by database.song(videoId).collectAsState(initial = null)
    val currentFormat by database.format(videoId).collectAsState(initial = null)
    var info by remember(videoId) { mutableStateOf<MediaInfo?>(null) }
    var selectedTab by rememberSaveable(videoId) { mutableStateOf(MediaInfoTab.Information) }

    val unknownText = stringResource(R.string.unknown)
    val pleaseWaitText = stringResource(R.string.please_wait)
    val copyText = stringResource(R.string.copy)
    val shareText = stringResource(R.string.share)
    val closeText = stringResource(R.string.close)
    val songTitleLabel = stringResource(R.string.song_title)
    val songArtistsLabel = stringResource(R.string.song_artists)
    val mediaIdLabel = stringResource(R.string.media_id)
    val mimeTypeLabel = stringResource(R.string.mime_type)
    val codecsLabel = stringResource(R.string.codecs)
    val bitrateLabel = stringResource(R.string.bitrate)
    val sampleRateLabel = stringResource(R.string.sample_rate)
    val loudnessLabel = stringResource(R.string.loudness)
    val volumeLabel = stringResource(R.string.volume)
    val fileSizeLabel = stringResource(R.string.file_size)
    val descriptionLabel = stringResource(R.string.description)
    val detailsLabel = stringResource(R.string.details)
    val numbersLabel = stringResource(R.string.numbers)
    val informationLabel = stringResource(R.string.information)

    val mediaUrl = remember(videoId) { "https://music.youtube.com/watch?v=$videoId" }

    LaunchedEffect(videoId) {
        info = YouTube.getMediaInfo(videoId).getOrNull()
    }

    val heroTitle = song?.title ?: info?.title ?: videoId
    val heroSubtitle =
        song
            ?.artists
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?: info?.author
            ?: unknownText
    val artworkModel = song?.thumbnailUrl ?: info?.authorThumbnail
    val playbackVolume = playerConnection?.let { "${(it.player.volume * 100).toInt()}%" }

    val overviewDetails =
        buildList {
            add(MediaInfoDetail(label = songTitleLabel, value = song?.title ?: info?.title ?: unknownText))
            add(
                MediaInfoDetail(
                    label = songArtistsLabel,
                    value =
                        song
                            ?.artists
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString { it.name }
                            ?: info?.author
                            ?: unknownText,
                ),
            )
            add(MediaInfoDetail(label = mediaIdLabel, value = videoId))
        }

    val technicalDetails =
        buildList {
            currentFormat?.itag?.toString()?.let { add(MediaInfoDetail(label = "Itag", value = it)) }
            currentFormat
                ?.mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoDetail(label = mimeTypeLabel, value = it)) }
            currentFormat
                ?.codecs
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoDetail(label = codecsLabel, value = it)) }
            currentFormat
                ?.bitrate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoDetail(label = bitrateLabel, value = "${it / 1000} Kbps")) }
            currentFormat
                ?.sampleRate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoDetail(label = sampleRateLabel, value = "$it Hz")) }
            currentFormat?.loudnessDb?.let { add(MediaInfoDetail(label = loudnessLabel, value = "$it dB")) }
            playbackVolume?.let { add(MediaInfoDetail(label = volumeLabel, value = it)) }
            currentFormat
                ?.contentLength
                ?.takeIf { it > 0 }
                ?.let {
                    add(
                        MediaInfoDetail(
                            label = fileSizeLabel,
                            value = Formatter.formatShortFileSize(context, it),
                        ),
                    )
                }
        }

    val quickFacts =
        buildList {
            currentFormat
                ?.mimeType
                ?.substringBefore(';')
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.graphic_eq, text = it)) }
            currentFormat
                ?.bitrate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.waves, text = "${it / 1000} Kbps")) }
            currentFormat
                ?.contentLength
                ?.takeIf { it > 0 }
                ?.let {
                    add(
                        MediaInfoQuickFact(
                            iconRes = R.drawable.storage,
                            text = Formatter.formatShortFileSize(context, it),
                        ),
                    )
                }
            info
                ?.subscribers
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.person, text = it)) }
        }

    val metrics =
        if (info != null) {
            listOf(
                MediaInfoMetric(R.string.subscribers, info?.subscribers ?: unknownText),
                MediaInfoMetric(R.string.views, info?.viewCount?.let(::numberFormatter) ?: unknownText),
                MediaInfoMetric(R.string.likes, info?.like?.let(::numberFormatter) ?: unknownText),
                MediaInfoMetric(R.string.dislikes, info?.dislike?.let(::numberFormatter) ?: unknownText),
            )
        } else {
            emptyList()
        }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(contentType = "Hero") {
            MediaInfoHeroCard(
                title = heroTitle,
                subtitle = heroSubtitle,
                artworkModel = artworkModel,
                sectionLabel = informationLabel,
                isLoading = info == null,
                loadingText = pleaseWaitText,
                closeText = closeText,
                onClose = bottomSheetPageState::dismiss,
            )
        }

        item(contentType = "Actions") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = { copyToClipboard(context, videoId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = copyText)
                }

                OutlinedButton(
                    onClick = { shareMediaLink(context, mediaUrl) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = shareText)
                }
            }
        }

        if (quickFacts.isNotEmpty()) {
            item(contentType = "QuickFacts") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    quickFacts.forEach { fact ->
                        AssistChip(
                            onClick = { copyToClipboard(context, fact.text) },
                            label = {
                                Text(
                                    text = fact.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(fact.iconRes),
                                    contentDescription = null,
                                )
                            },
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                    }
                }
            }
        }

        item(contentType = "Tabs") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MediaInfoTab.entries.forEachIndexed { index, tab ->
                    val checked = selectedTab == tab
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = {
                            if (!checked) {
                                selectedTab = tab
                            }
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                MediaInfoTab.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        colors =
                            ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item(contentType = "SelectedContent") {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mediaInfoTab",
            ) { tab ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                ) {
                    when (tab) {
                        MediaInfoTab.Information -> {
                            MediaInfoDetailCard(
                                items = overviewDetails,
                                copyContentDescription = copyText,
                                onCopy = { copyToClipboard(context, it) },
                            )

                            if (info == null) {
                                MediaInfoPendingCard(
                                    title = descriptionLabel,
                                    message = pleaseWaitText,
                                )
                            } else {
                                MediaInfoNarrativeCard(
                                    title = descriptionLabel,
                                    body = info?.description?.takeIf { it.isNotBlank() } ?: unknownText,
                                    copyText = copyText,
                                    onCopy = {
                                        info
                                            ?.description
                                            ?.takeIf { value -> value.isNotBlank() }
                                            ?.let { copyToClipboard(context, it) }
                                    },
                                )
                            }
                        }

                        MediaInfoTab.Details -> {
                            if (technicalDetails.isEmpty()) {
                                MediaInfoPendingCard(
                                    title = detailsLabel,
                                    message = pleaseWaitText,
                                )
                            } else {
                                MediaInfoDetailCard(
                                    items = technicalDetails,
                                    copyContentDescription = copyText,
                                    onCopy = { copyToClipboard(context, it) },
                                )
                            }
                        }

                        MediaInfoTab.Numbers -> {
                            if (metrics.isEmpty()) {
                                MediaInfoPendingCard(
                                    title = numbersLabel,
                                    message = pleaseWaitText,
                                )
                            } else {
                                MediaInfoMetricsGrid(metrics = metrics)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoHeroCard(
    title: String,
    subtitle: String,
    artworkModel: String?,
    sectionLabel: String,
    isLoading: Boolean,
    loadingText: String,
    closeText: String,
    onClose: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = loadingText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = closeText,
                )
            }
        }
    }
}

@Composable
private fun MediaInfoDetailCard(
    items: List<MediaInfoDetail>,
    copyContentDescription: String,
    onCopy: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                ListItem(
                    overlineContent = {
                        Text(text = item.label)
                    },
                    headlineContent = {
                        if (item.multiline) {
                            Text(text = item.value)
                        } else {
                            Text(
                                text = item.value,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.copy),
                            contentDescription = copyContentDescription,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onCopy(item.value) },
                )

                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoNarrativeCard(
    title: String,
    body: String,
    copyText: String,
    onCopy: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = onCopy) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = copyText)
                }
            }

            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaInfoMetricsGrid(metrics: List<MediaInfoMetric>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowMetrics.forEach { metric ->
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        colors =
                            CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                        ) {
                            Text(
                                text = stringResource(metric.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = metric.value,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaInfoPendingCard(
    title: String,
    message: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(40.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun copyToClipboard(
    context: Context,
    value: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", value))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun shareMediaLink(
    context: Context,
    mediaUrl: String,
) {
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mediaUrl)
        }
    context.startActivity(Intent.createChooser(shareIntent, null))
}
