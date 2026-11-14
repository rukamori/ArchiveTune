/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.awaitCancellation
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.mediainfo.MediaInfoDetail
import moe.rukamori.archivetune.mediainfo.MediaInfoEvent
import moe.rukamori.archivetune.mediainfo.MediaInfoNumbers
import moe.rukamori.archivetune.mediainfo.MediaInfoQuickFact
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoTab
import moe.rukamori.archivetune.mediainfo.MediaInfoUiModel
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.viewmodels.MediaInfoViewModel
import kotlin.math.roundToInt

@Composable
fun ShowMediaInfo(videoId: String) {
    val viewModel: MediaInfoViewModel = hiltViewModel()
    val context = LocalContext.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentState = state

    LaunchedEffect(videoId, viewModel) {
        viewModel.open(videoId, playerConnection?.player?.volume)
        try {
            awaitCancellation()
        } finally {
            viewModel.release(videoId)
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, context, bottomSheetPageState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is MediaInfoEvent.Copy -> copyToClipboard(context, event.value)
                    is MediaInfoEvent.Share -> shareMediaLink(context, event.url)
                    MediaInfoEvent.Close -> bottomSheetPageState.dismiss()
                }
            }
        }
    }
    MediaInfoContent(
        state = if (currentState is MediaInfoState.Success && currentState.data.videoId != videoId) {
            MediaInfoState.Loading
        } else {
            currentState
        },
        selectedTab = selectedTab,
        onSelectTab = remember(viewModel) { viewModel::selectTab },
        onCopy = remember(viewModel) { viewModel::copy },
        onCopyId = remember(viewModel) { viewModel::copyId },
        onShare = remember(viewModel) { viewModel::share },
        onRetry = remember(viewModel) { viewModel::retry },
        onClose = remember(viewModel) { viewModel::close },
    )
}

@Composable
private fun MediaInfoContent(
    state: MediaInfoState<MediaInfoUiModel>,
    selectedTab: MediaInfoTab,
    onSelectTab: (MediaInfoTab) -> Unit,
    onCopy: (String) -> Unit,
    onCopyId: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val model = (state as? MediaInfoState.Success)?.data
    if (model == null) {
        MediaInfoPlaceholder(state = state, onRetry = onRetry, onClose = onClose)
        return
    }
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "Stage", contentType = "Stage") {
            MediaInfoStage(
                title = model.title,
                subtitle = model.subtitle ?: stringResource(R.string.unknown),
                artworkModel = model.artwork,
                isLoading = model.metadata is MediaInfoState.Loading,
                onClose = onClose,
            )
        }

        item(key = "Actions", contentType = "Actions") {
            MediaInfoActions(onShare = onShare, onCopyId = onCopyId)
        }

        if (model.quickFacts.isNotEmpty()) {
            item(key = "Specs", contentType = "Specs") {
                MediaInfoSpecStrip(facts = model.quickFacts, onCopy = onCopy)
            }
        }

        item(key = "Tabs", contentType = "Tabs") {
            MediaInfoTabSelector(selectedTab = selectedTab, onSelectTab = onSelectTab)
        }

        item(key = "SelectedContent", contentType = "SelectedContent") {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    if (animationsDisabled) {
                        fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                    } else {
                        (
                            slideInHorizontally(motion.defaultSpatialSpec()) { it / 5 * direction } +
                                fadeIn(motion.defaultEffectsSpec())
                        ) togetherWith (
                            slideOutHorizontally(motion.fastSpatialSpec()) { -it / 5 * direction } +
                                fadeOut(motion.fastEffectsSpec())
                        ) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                    }
                },
                label = "mediaInfoTab",
            ) { tab ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (tab) {
                        MediaInfoTab.Information -> {
                            MediaInfoDetailGroup(items = model.overview, onCopy = onCopy)
                            when (val description = model.descriptionState) {
                                is MediaInfoState.Success -> MediaInfoLinerNotes(
                                    videoId = model.videoId,
                                    body = description.data,
                                    onCopy = onCopy,
                                )
                                else -> MediaInfoStatusPanel(
                                    title = stringResource(R.string.description),
                                    state = description,
                                    onRetry = onRetry,
                                )
                            }
                        }

                        MediaInfoTab.Details -> {
                            when (val details = model.technicalDetails) {
                                is MediaInfoState.Success -> MediaInfoDetailGroup(items = details.data, onCopy = onCopy)
                                else -> MediaInfoStatusPanel(
                                    title = stringResource(R.string.details),
                                    state = details,
                                    onRetry = onRetry,
                                )
                            }
                        }

                        MediaInfoTab.Numbers -> {
                            if (model.statistics is MediaInfoState.Success) {
                                MediaInfoNumbersBoard(numbers = model.numbers)
                            } else {
                                MediaInfoStatusPanel(
                                    title = stringResource(R.string.numbers),
                                    state = model.statistics,
                                    onRetry = onRetry,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAmbientRotation(durationMillis: Int): () -> Float {
    if (LocalAnimationsDisabled.current) return { 0f }
    val transition = rememberInfiniteTransition(label = "mediaInfoAmbient")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "mediaInfoAmbientRotation",
    )
    return { rotation }
}

@Composable
private fun MediaInfoStage(
    title: String,
    subtitle: String,
    artworkModel: String?,
    isLoading: Boolean,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val haloShape = remember { MaterialShapes.SoftBurst.toShape() }
    val artworkShape = remember { MaterialShapes.Cookie9Sided.toShape() }
    val rotation = rememberAmbientRotation(durationMillis = 36_000)
    val entrance = remember { Animatable(if (animationsDisabled) 1f else 0.82f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) entrance.snapTo(1f) else entrance.animateTo(1f, motion.slowSpatialSpec())
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(212.dp)
                        .graphicsLayer {
                            scaleX = entrance.value
                            scaleY = entrance.value
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotation() }
                            .clip(haloShape)
                            .background(colors.primaryContainer),
                )
                MediaInfoArtwork(
                    artworkModel = artworkModel,
                    shape = artworkShape,
                    modifier = Modifier.size(172.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
            ) {
                LinearWavyProgressIndicator(
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .width(112.dp),
                )
            }
        }
        FilledTonalIconButton(
            onClick = onClose,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

@Composable
private fun MediaInfoArtwork(
    artworkModel: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        if (artworkModel != null) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun MediaInfoActions(
    onShare: () -> Unit,
    onCopyId: () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onShare,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = height),
        ) {
            Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.share),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            onClick = onCopyId,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = height),
        ) {
            Icon(
                painter = painterResource(R.drawable.copy),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.media_info_copy_id),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaInfoSpecStrip(
    facts: ImmutableList<MediaInfoQuickFact>,
    onCopy: (String) -> Unit,
) {
    val copyText = stringResource(R.string.copy)
    val outer = 20.dp
    val inner = 6.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
    ) {
        facts.forEachIndexed { index, fact ->
            val shape =
                RoundedCornerShape(
                    topStart = if (index == 0) outer else inner,
                    bottomStart = if (index == 0) outer else inner,
                    topEnd = if (index == facts.lastIndex) outer else inner,
                    bottomEnd = if (index == facts.lastIndex) outer else inner,
                )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(onClickLabel = copyText) { onCopy(fact.text) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(fact.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = fact.text,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

@DrawableRes
private fun MediaInfoTab.iconRes(): Int =
    when (this) {
        MediaInfoTab.Information -> R.drawable.info
        MediaInfoTab.Details -> R.drawable.tune
        MediaInfoTab.Numbers -> R.drawable.stats
    }

@Composable
private fun MediaInfoTabSelector(
    selectedTab: MediaInfoTab,
    onSelectTab: (MediaInfoTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val tabs = MediaInfoTab.entries
    val selectedIndex = tabs.indexOf(selectedTab)

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = motion.defaultSpatialSpec(),
            label = "mediaInfoTabIndicator",
        )
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.primary),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .selectableGroup(),
        ) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                val contentColor by animateColorAsState(
                    targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    animationSpec = motion.defaultEffectsSpec(),
                    label = "mediaInfoTabContent",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelectTab(tab) },
                            )
                            .padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes()),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(tab.labelRes),
                        style = if (selected) {
                            MaterialTheme.typography.labelLargeEmphasized
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@DrawableRes
private fun detailIconRes(@StringRes labelRes: Int): Int =
    when (labelRes) {
        R.string.song_title -> R.drawable.music_note
        R.string.song_artists -> R.drawable.person
        R.string.media_id -> R.drawable.link
        R.string.media_info_itag -> R.drawable.tune
        R.string.mime_type -> R.drawable.graphic_eq
        R.string.codecs -> R.drawable.equalizer
        R.string.bitrate -> R.drawable.waves
        R.string.sample_rate -> R.drawable.speed
        R.string.loudness -> R.drawable.trending_up
        R.string.file_size -> R.drawable.storage
        R.string.volume -> R.drawable.volume_up
        else -> R.drawable.info
    }

@Composable
private fun MediaInfoDetailGroup(
    items: ImmutableList<MediaInfoDetail>,
    onCopy: (String) -> Unit,
) {
    val copyText = stringResource(R.string.copy)
    val unknownText = stringResource(R.string.unknown)
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEachIndexed { index, item ->
            val value = item.value
            SegmentedListItem(
                onClick = { value?.let(onCopy) },
                enabled = value != null,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.secondaryContainer),
                    ) {
                        Icon(
                            painter = painterResource(detailIconRes(item.labelRes)),
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                overlineContent = { Text(text = stringResource(item.labelRes)) },
                trailingContent = {
                    if (value != null) {
                        Icon(
                            painter = painterResource(R.drawable.copy),
                            contentDescription = copyText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            ) {
                Text(
                    text = value ?: unknownText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value != null) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaInfoLinerNotes(
    videoId: String,
    body: String,
    onCopy: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var expanded by rememberSaveable(videoId) { mutableStateOf(false) }
    var overflowing by remember(body) { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(motion.defaultSpatialSpec())
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 4.dp, height = 20.dp)
                            .clip(CircleShape)
                            .background(colors.tertiary),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.description),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = colors.onSurface,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { heading() },
                )
                IconButton(onClick = { onCopy(body) }) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = stringResource(R.string.copy),
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result -> if (!expanded) overflowing = result.hasVisualOverflow },
                modifier = Modifier.padding(end = 12.dp, top = 4.dp),
            )
            if (expanded || overflowing) {
                TextButton(
                    onClick = { expanded = !expanded },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) R.string.media_info_show_less else R.string.media_info_show_more,
                        ),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(
                        painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoNumbersBoard(numbers: MediaInfoNumbers) {
    val colors = MaterialTheme.colorScheme
    val unknownText = stringResource(R.string.unknown)
    val badgeShape = remember { MaterialShapes.Cookie4Sided.toShape() }
    val sunnyShape = remember { MaterialShapes.Sunny.toShape() }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 40.dp, y = (-40).dp)
                            .size(156.dp)
                            .clip(sunnyShape)
                            .background(colors.primary.copy(alpha = 0.14f)),
                )
                Column(modifier = Modifier.padding(24.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(badgeShape)
                                .background(colors.primary),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.stats),
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = numbers.views ?: unknownText,
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.views),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        MediaInfoSentimentPanel(
            likes = numbers.likes ?: unknownText,
            dislikes = numbers.dislikes ?: unknownText,
            likeRatio = numbers.likeRatio,
        )

        numbers.subscribers?.let { subscribers ->
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.tertiary),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            tint = colors.onTertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.subscribers),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = subscribers,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSentimentPanel(
    likes: String,
    dislikes: String,
    likeRatio: Float?,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val animatedRatio = remember { Animatable(if (animationsDisabled) likeRatio ?: 0f else 0f) }

    LaunchedEffect(likeRatio, animationsDisabled) {
        val target = likeRatio ?: return@LaunchedEffect
        if (animationsDisabled) animatedRatio.snapTo(target) else animatedRatio.animateTo(target, motion.slowEffectsSpec())
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MediaInfoSentimentValue(
                    label = stringResource(R.string.likes),
                    value = likes,
                    iconRes = R.drawable.favorite,
                    accentColor = colors.primary,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f),
                )
                MediaInfoSentimentValue(
                    label = stringResource(R.string.dislikes),
                    value = dislikes,
                    iconRes = null,
                    accentColor = colors.onSurfaceVariant,
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f),
                )
            }
            if (likeRatio != null) {
                Spacer(Modifier.height(18.dp))
                LinearWavyProgressIndicator(
                    progress = { animatedRatio.value.coerceIn(0f, 1f) },
                    color = colors.primary,
                    trackColor = colors.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.media_info_like_ratio, (likeRatio * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MediaInfoSentimentValue(
    label: String,
    value: String,
    @DrawableRes iconRes: Int?,
    accentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaInfoStatusPanel(
    title: String,
    state: MediaInfoState<*>,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val glyphShape = remember { MaterialShapes.Cookie9Sided.toShape() }
    val message = stringResource(
        when (state) {
            MediaInfoState.Loading -> R.string.please_wait
            is MediaInfoState.Error -> R.string.media_info_load_error
            MediaInfoState.Empty, is MediaInfoState.Success -> R.string.media_info_empty
        },
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            if (state is MediaInfoState.Loading) {
                ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
            } else {
                val isError = state is MediaInfoState.Error
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(glyphShape)
                            .background(if (isError) colors.errorContainer else colors.secondaryContainer),
                ) {
                    Icon(
                        painter = painterResource(if (isError) R.drawable.error else R.drawable.info),
                        contentDescription = null,
                        tint = if (isError) colors.onErrorContainer else colors.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (state is MediaInfoState.Error) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun MediaInfoPlaceholder(
    state: MediaInfoState<MediaInfoUiModel>,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haloShape = remember { MaterialShapes.SoftBurst.toShape() }
    val rotation = rememberAmbientRotation(durationMillis = 12_000)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (state is MediaInfoState.Loading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(212.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = rotation() }
                                .clip(haloShape)
                                .background(colors.surfaceContainerHigh),
                    )
                    ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.please_wait),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = Modifier.padding(top = 56.dp, bottom = 24.dp)) {
                MediaInfoStatusPanel(
                    title = stringResource(R.string.information),
                    state = state,
                    onRetry = onRetry,
                )
            }
        }
        FilledTonalIconButton(
            onClick = onClose,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close),
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
