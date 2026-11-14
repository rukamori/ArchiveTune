/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.together.TogetherLink
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.MusicTogetherActivityLogItemUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherActivityLogUiModels
import moe.rukamori.archivetune.viewmodels.MusicTogetherDialogUiState
import moe.rukamori.archivetune.viewmodels.MusicTogetherEffect
import moe.rukamori.archivetune.viewmodels.MusicTogetherHostUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherJoinUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherParticipantUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherPhase
import moe.rukamori.archivetune.viewmodels.MusicTogetherPlaybackUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherScreenState
import moe.rukamori.archivetune.viewmodels.MusicTogetherSessionShareUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherStatusUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherTrackUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherViewModel
import moe.rukamori.archivetune.ui.component.IconButton as AtIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTogetherScreen(
    navController: NavController,
    viewModel: MusicTogetherViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val scrollBehavior = appBarScrollBehavior()
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useSupportingPane = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    LaunchedEffect(playerConnection?.service) {
        viewModel.attachService(playerConnection?.service)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MusicTogetherEffect.CopyText -> {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText(context.getString(effect.labelResId), effect.value),
                    )
                }

                is MusicTogetherEffect.ShareText -> {
                    val share =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, effect.value)
                        }
                    context.startActivity(Intent.createChooser(share, null))
                }

                is MusicTogetherEffect.ToastMessage -> {
                    Toast.makeText(context, effect.messageResId, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val model = (screenState as? MusicTogetherScreenState.Success)?.model
    if (model != null) {
        MusicTogetherDialogs(model = model, viewModel = viewModel)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.music_together),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    AtIconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        modifier =
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(40.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            val stateFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val stateFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = screenState,
                contentKey = { it::class },
                transitionSpec = { fadeIn(stateFadeIn) togetherWith fadeOut(stateFadeOut) },
                label = "music_together_state",
            ) { state ->
                when (state) {
                    MusicTogetherScreenState.Loading -> {
                        LoadingContent()
                    }

                    MusicTogetherScreenState.Empty -> {
                        CenteredStatus(
                            iconResId = R.drawable.multi_user,
                            shape = MaterialShapes.Cookie9Sided,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.together_idle),
                            body = stringResource(R.string.together_activity_log_empty_body),
                        )
                    }

                    is MusicTogetherScreenState.Error -> {
                        CenteredStatus(
                            iconResId = R.drawable.error,
                            shape = MaterialShapes.Cookie4Sided,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            title = stringResource(R.string.together_error_state),
                            body = stringResource(state.messageResId),
                        )
                    }

                    is MusicTogetherScreenState.Success -> {
                        MusicTogetherContent(
                            model = state.model,
                            useSupportingPane = useSupportingPane,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicTogetherContent(
    model: MusicTogetherUiModel,
    useSupportingPane: Boolean,
    viewModel: MusicTogetherViewModel,
) {
    val reducedMotion = rememberReducedMotion()
    val showRoomEmptyStates = useSupportingPane || model.status.phase != MusicTogetherPhase.Idle
    if (useSupportingPane) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = TogetherSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .widthIn(max = 720.dp),
                contentPadding = PaddingValues(bottom = SettingsDimensions.ScreenBottomPadding),
            ) {
                sessionSection(model = model, viewModel = viewModel, reducedMotion = reducedMotion)
            }
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = SettingsDimensions.ScreenBottomPadding),
            ) {
                roomSection(model = model, viewModel = viewModel, showEmptyStates = showRoomEmptyStates)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = TogetherSpacing.sm,
                    end = TogetherSpacing.sm,
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            sessionSection(model = model, viewModel = viewModel, reducedMotion = reducedMotion)
            roomSection(model = model, viewModel = viewModel, showEmptyStates = showRoomEmptyStates)
        }
    }
}

private fun LazyListScope.sessionSection(
    model: MusicTogetherUiModel,
    viewModel: MusicTogetherViewModel,
    reducedMotion: Boolean,
) {
    val phase = model.status.phase
    item(key = "hero", contentType = "hero") {
        SessionHero(
            status = model.status,
            reducedMotion = reducedMotion,
            onLeave = viewModel::leaveSession,
            modifier = itemMotion().padding(top = TogetherSpacing.xs),
        )
    }
    val share = model.sessionShare
    if (share != null) {
        item(key = "invite", contentType = "invite") {
            InviteTicket(
                share = share,
                onCopy = viewModel::copySessionValue,
                onShare = viewModel::shareSessionValue,
                modifier = itemMotion().padding(top = TogetherSpacing.sm),
            )
        }
    }
    if (model.playback.hasRoom) {
        item(key = "stage", contentType = "stage") {
            ListeningStage(
                playback = model.playback,
                reducedMotion = reducedMotion,
                modifier = itemMotion().padding(top = TogetherSpacing.sm),
            )
        }
    }
    if (phase == MusicTogetherPhase.Idle || phase == MusicTogetherPhase.Error) {
        item(key = "setup", contentType = "setup") {
            SetupPanel(
                host = model.host,
                join = model.join,
                viewModel = viewModel,
                modifier = itemMotion().padding(top = TogetherSpacing.md),
            )
        }
    } else if (model.status.active && model.host.visible) {
        item(key = "rules_header", contentType = "section_header") {
            SectionHeader(
                iconResId = R.drawable.lock,
                title = stringResource(R.string.together_room_rules),
                subtitle = stringResource(R.string.together_room_rules_live),
                glyphShape = MaterialShapes.Clover4Leaf,
                modifier = itemMotion(),
            )
        }
        item(key = "rules", contentType = "rules") {
            RoomRulesGroup(host = model.host, viewModel = viewModel, modifier = itemMotion())
        }
    }
}

private fun LazyListScope.roomSection(
    model: MusicTogetherUiModel,
    viewModel: MusicTogetherViewModel,
    showEmptyStates: Boolean,
) {
    val participants = model.participants
    if (!participants.isEmpty || showEmptyStates) {
        item(key = "listeners_header", contentType = "section_header") {
            SectionHeader(
                iconResId = R.drawable.multi_user,
                title = stringResource(R.string.together_participants),
                glyphShape = MaterialShapes.Cookie6Sided,
                trailing = {
                    if (model.status.active) {
                        CountBadge(text = stringResource(R.string.together_connected_count, model.status.connectedCount))
                    }
                },
                modifier = itemMotion(),
            )
        }
        if (participants.isEmpty) {
            item(key = "listeners_empty", contentType = "empty") {
                RoomEmptyState(
                    iconResId = R.drawable.person,
                    titleResId = R.string.together_participants_empty,
                    bodyResId = R.string.together_participants_empty_body,
                    modifier = itemMotion(),
                )
            }
        } else {
            items(
                count = participants.size,
                key = { index -> "participant:${participants[index].id}" },
                contentType = { "participant" },
            ) { index ->
                ParticipantRow(
                    participant = participants[index],
                    shape = segmentShape(index = index, count = participants.size),
                    viewModel = viewModel,
                    modifier = itemMotion().padding(top = if (index == 0) 0.dp else TogetherSpacing.segmentGap),
                )
            }
        }
    }

    val activityLog = model.activityLog
    if (!activityLog.isEmpty || showEmptyStates) {
        item(key = "activity_header", contentType = "section_header") {
            SectionHeader(
                iconResId = R.drawable.history,
                title = stringResource(R.string.together_activity_log),
                subtitle = stringResource(R.string.together_activity_log_subtitle),
                glyphShape = MaterialShapes.Sunny,
                modifier = itemMotion(),
            )
        }
        if (activityLog.isEmpty) {
            item(key = "activity_empty", contentType = "empty") {
                RoomEmptyState(
                    iconResId = R.drawable.history,
                    titleResId = R.string.together_activity_log_empty,
                    bodyResId = R.string.together_activity_log_empty_body,
                    modifier = itemMotion(),
                )
            }
        } else {
            activityTimeline(activityLog)
        }
    }
}

private fun LazyListScope.activityTimeline(activityLog: MusicTogetherActivityLogUiModels) {
    items(
        count = activityLog.size,
        key = { index -> "activity:${activityLog[index].id}" },
        contentType = { "activity" },
    ) { index ->
        TimelineRow(
            item = activityLog[index],
            latest = index == 0,
            last = index == activityLog.size - 1,
            modifier = itemMotion(),
        )
    }
}

@Composable
private fun LazyItemScope.itemMotion(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

@Composable
private fun SessionHero(
    status: MusicTogetherStatusUiModel,
    reducedMotion: Boolean,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val effects = motion.defaultEffectsSpec<Color>()
    val labelFadeIn = motion.defaultEffectsSpec<Float>()
    val labelFadeOut = motion.fastEffectsSpec<Float>()
    val labelSlide = motion.defaultSpatialSpec<IntOffset>()
    val container by animateColorAsState(
        targetValue =
            when (status.phase) {
                MusicTogetherPhase.Idle -> colors.surfaceContainerHigh
                MusicTogetherPhase.Connecting -> colors.secondaryContainer
                MusicTogetherPhase.Live -> colors.primaryContainer
                MusicTogetherPhase.WaitingApproval -> colors.tertiaryContainer
                MusicTogetherPhase.Error -> colors.errorContainer
            },
        animationSpec = effects,
        label = "hero_container",
    )
    val content by animateColorAsState(
        targetValue =
            when (status.phase) {
                MusicTogetherPhase.Idle -> colors.onSurface
                MusicTogetherPhase.Connecting -> colors.onSecondaryContainer
                MusicTogetherPhase.Live -> colors.onPrimaryContainer
                MusicTogetherPhase.WaitingApproval -> colors.onTertiaryContainer
                MusicTogetherPhase.Error -> colors.onErrorContainer
            },
        animationSpec = effects,
        label = "hero_content",
    )
    val accent by animateColorAsState(
        targetValue =
            when (status.phase) {
                MusicTogetherPhase.Idle -> colors.secondary
                MusicTogetherPhase.Connecting -> colors.secondary
                MusicTogetherPhase.Live -> colors.primary
                MusicTogetherPhase.WaitingApproval -> colors.tertiary
                MusicTogetherPhase.Error -> colors.error
            },
        animationSpec = effects,
        label = "hero_accent",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TogetherRadius.hero),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(TogetherSpacing.lg)
                    .animateContentSize(animationSpec = motion.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.lg),
            ) {
                SessionOrb(
                    phase = status.phase,
                    accent = accent,
                    onAccent = container,
                    reducedMotion = reducedMotion,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xxs),
                ) {
                    Text(
                        text = stringResource(status.titleResId),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                    )
                    AnimatedContent(
                        targetState = status.stateLabelResId,
                        transitionSpec = {
                            (fadeIn(labelFadeIn) + slideInVertically(labelSlide) { height -> height / 3 }) togetherWith
                                fadeOut(labelFadeOut) using SizeTransform(clip = false)
                        },
                        label = "hero_state_label",
                    ) { labelResId ->
                        Text(
                            text = stringResource(labelResId),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    if (status.active) {
                        Text(
                            text = stringResource(R.string.together_connected_count, status.connectedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = status.errorMessage != null,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(labelFadeIn),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(labelFadeOut),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(TogetherRadius.inner),
                    color = colors.surface.copy(alpha = 0.6f),
                    contentColor = colors.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(TogetherSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.error),
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = status.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            val transport = status.onlineTransport
            if (transport != null || status.canLeave) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
                ) {
                    if (transport != null) {
                        TransportBadge(online = transport, tint = accent)
                    }
                    Spacer(Modifier.weight(1f))
                    if (status.canLeave) {
                        Button(
                            onClick = onLeave,
                            shapes = ButtonDefaults.shapes(),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = content,
                                    contentColor = container,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.leave),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.leave))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionOrb(
    phase: MusicTogetherPhase,
    accent: Color,
    onAccent: Color,
    reducedMotion: Boolean,
    size: Dp = 88.dp,
) {
    val targetShape =
        when (phase) {
            MusicTogetherPhase.Idle -> MaterialShapes.Cookie9Sided
            MusicTogetherPhase.Connecting -> MaterialShapes.SoftBurst
            MusicTogetherPhase.Live -> MaterialShapes.Sunny
            MusicTogetherPhase.WaitingApproval -> MaterialShapes.Clover4Leaf
            MusicTogetherPhase.Error -> MaterialShapes.Cookie4Sided
        }
    val morphProgress = rememberShapeMorph(targetShape)
    val spins = !reducedMotion && phase != MusicTogetherPhase.Error && phase != MusicTogetherPhase.Connecting
    val rotation = if (spins) rememberContinuousRotation(durationMillis = 18_000) else 0f
    val glyphFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val glyphFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val glyphScale = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (phase == MusicTogetherPhase.Live && !reducedMotion) {
            LiveHalo(color = accent)
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation }
                    .clip(EditorialMorphShape(morphProgress.morph, morphProgress.progress))
                    .background(accent),
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(glyphFadeIn) + scaleIn(glyphScale, initialScale = 0.6f)) togetherWith
                    (fadeOut(glyphFadeOut) + scaleOut(glyphScale, targetScale = 0.6f))
            },
            label = "orb_glyph",
        ) { targetPhase ->
            if (targetPhase == MusicTogetherPhase.Connecting) {
                LoadingIndicator(
                    color = onAccent,
                    modifier = Modifier.size(size * 0.56f),
                )
            } else {
                Icon(
                    painter =
                        painterResource(
                            when (targetPhase) {
                                MusicTogetherPhase.Idle -> R.drawable.multi_user
                                MusicTogetherPhase.Live -> R.drawable.fire
                                MusicTogetherPhase.WaitingApproval -> R.drawable.timer
                                MusicTogetherPhase.Error -> R.drawable.error
                                MusicTogetherPhase.Connecting -> R.drawable.sync
                            },
                        ),
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(size * 0.4f),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.LiveHalo(color: Color) {
    val transition = rememberInfiniteTransition(label = "live_halo")
    repeat(2) { ring ->
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2_400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(ring * 1_200),
                ),
            label = "halo_ring_$ring",
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val scale = 1f + pulse * 0.45f
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - pulse) * 0.35f
                    }.clip(CircleShape)
                    .background(color),
        )
    }
}

@Composable
private fun TransportBadge(
    online: Boolean,
    tint: Color,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = TogetherSpacing.sm, end = TogetherSpacing.md, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(if (online) R.drawable.language else R.drawable.wifi_proxy),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(if (online) R.string.together_online else R.string.together_lan),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

@Composable
private fun InviteTicket(
    share: MusicTogetherSessionShareUiModel,
    onCopy: (Int, String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TogetherRadius.card),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
    ) {
        Column {
            Column(
                modifier = Modifier.padding(start = TogetherSpacing.lg, end = TogetherSpacing.lg, top = TogetherSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
                ) {
                    Icon(
                        painter = painterResource(if (share.isCode) R.drawable.language else R.drawable.link),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(share.labelResId),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
                if (share.isCode) {
                    Text(
                        text = share.value,
                        style =
                            MaterialTheme.typography.displaySmallEmphasized.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 6.sp,
                            ),
                        maxLines = share.maxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = share.value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = share.maxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val addressHint = share.addressHint
                if (addressHint != null) {
                    Row(
                        modifier = Modifier.padding(top = TogetherSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.wifi_proxy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.together_local_address),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(alpha = 0.72f),
                        )
                        Text(
                            text = addressHint,
                            style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            TicketPerforation(
                notchColor = colors.surface,
                lineColor = colors.onSecondaryContainer.copy(alpha = 0.32f),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = TogetherSpacing.md, end = TogetherSpacing.md, bottom = TogetherSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
            ) {
                Button(
                    onClick = { onCopy(share.labelResId, share.value) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(if (share.isCode) R.string.together_copy_code else R.string.copy_link),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = { onShare(share.value) },
                    shapes = ButtonDefaults.shapes(),
                    border = BorderStroke(1.dp, colors.onSecondaryContainer.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSecondaryContainer),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.share),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketPerforation(
    notchColor: Color,
    lineColor: Color,
) {
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TogetherSpacing.lg),
    ) {
        val radius = size.height / 2f
        drawCircle(color = notchColor, radius = radius, center = Offset(0f, radius))
        drawCircle(color = notchColor, radius = radius, center = Offset(size.width, radius))
        drawLine(
            color = lineColor,
            start = Offset(radius * 1.8f, radius),
            end = Offset(size.width - radius * 1.8f, radius),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx())),
        )
    }
}

@Composable
private fun ListeningStage(
    playback: MusicTogetherPlaybackUiModel,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val titleFadeIn = motion.defaultEffectsSpec<Float>()
    val titleFadeOut = motion.fastEffectsSpec<Float>()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TogetherRadius.card),
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(TogetherSpacing.lg)
                    .animateContentSize(animationSpec = motion.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
            ) {
                SpinningArtwork(
                    thumbnailUrl = playback.thumbnailUrl,
                    trackKey = playback.trackId,
                    playing = playback.isPlaying,
                    reducedMotion = reducedMotion,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xxs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
                    ) {
                        EqualizerGlyph(
                            playing = playback.isPlaying && !reducedMotion,
                            color = colors.primary,
                        )
                        Text(
                            text = stringResource(R.string.together_current_playback),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AnimatedContent(
                        targetState = playback.title,
                        transitionSpec = {
                            fadeIn(titleFadeIn) togetherWith fadeOut(titleFadeOut) using SizeTransform(clip = false)
                        },
                        label = "stage_title",
                    ) { title ->
                        Text(
                            text = title ?: stringResource(R.string.together_playback_empty),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = if (title == null) colors.onSurfaceVariant else colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (playback.artists != null) {
                        Text(
                            text = playback.artists,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (playback.durationMs > 0L) {
                RoomProgress(playback = playback, reducedMotion = reducedMotion)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
            ) {
                FactChip(
                    iconResId = if (playback.isPlaying) R.drawable.play else R.drawable.pause,
                    label = stringResource(playback.playbackStateResId),
                    highlighted = playback.isPlaying,
                )
                FactChip(
                    iconResId = R.drawable.queue_music,
                    label = playback.currentIndexLabel ?: stringResource(R.string.together_queue_count, playback.queueSize),
                    highlighted = false,
                )
                FactChip(
                    iconResId = if (playback.shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                    label =
                        stringResource(
                            if (playback.shuffleEnabled) R.string.together_shuffle_on else R.string.together_shuffle_off,
                        ),
                    highlighted = playback.shuffleEnabled,
                )
                FactChip(
                    iconResId =
                        when (playback.repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> R.drawable.repeat
                        },
                    label =
                        stringResource(
                            when (playback.repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> R.string.repeat_mode_off
                            },
                        ),
                    highlighted = playback.repeatMode != Player.REPEAT_MODE_OFF,
                )
            }

            if (!playback.upNext.isEmpty) {
                Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.widget_up_next),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.together_queue_count, playback.queueSize),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    for (index in 0 until playback.upNext.size) {
                        UpNextRow(track = playback.upNext[index])
                    }
                }
            }
        }
    }
}

@Composable
private fun SpinningArtwork(
    thumbnailUrl: String?,
    trackKey: String?,
    playing: Boolean,
    reducedMotion: Boolean,
    size: Dp = 112.dp,
) {
    val colors = MaterialTheme.colorScheme
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing, reducedMotion) {
        if (playing && !reducedMotion) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 20_000, easing = LinearEasing),
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.92f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "artwork_scale",
    )
    val artworkShape = MaterialShapes.Cookie12Sided.toShape()
    val haloShape = MaterialShapes.Sunny.toShape()
    val artworkFadeIn = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val artworkFadeOut = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = -rotation.value * 0.5f }
                    .clip(haloShape)
                    .background(colors.primary.copy(alpha = 0.14f)),
        )
        Box(
            modifier =
                Modifier
                    .size(size * 0.84f)
                    .graphicsLayer {
                        rotationZ = rotation.value
                        scaleX = scale
                        scaleY = scale
                    }.clip(artworkShape)
                    .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AnimatedContent(
                    targetState = thumbnailUrl,
                    contentKey = { trackKey ?: it },
                    transitionSpec = { fadeIn(artworkFadeIn) togetherWith fadeOut(artworkFadeOut) },
                    label = "artwork",
                ) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.32f),
                )
            }
        }
    }
}

@Composable
private fun RoomProgress(
    playback: MusicTogetherPlaybackUiModel,
    reducedMotion: Boolean,
) {
    val positionMs =
        rememberRoomPositionMs(
            trackId = playback.trackId,
            snapshotMs = playback.positionMs,
            playing = playback.isPlaying,
        )
    val fraction = (positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "room_progress",
    )
    val waving = playback.isPlaying && !reducedMotion
    Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xxs)) {
        LinearWavyProgressIndicator(
            progress = { animatedFraction },
            amplitude = { if (waving) 1f else 0f },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = makeTimeString(positionMs.coerceAtMost(playback.durationMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = makeTimeString(playback.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberRoomPositionMs(
    trackId: String?,
    snapshotMs: Long,
    playing: Boolean,
): Long {
    val playerConnection = LocalPlayerConnection.current
    var position by remember(trackId) { mutableLongStateOf(snapshotMs) }
    LaunchedEffect(trackId, snapshotMs, playing, playerConnection) {
        val player = playerConnection?.player
        while (isActive) {
            val localPosition =
                player
                    ?.takeIf { trackId != null && it.currentMediaItem?.mediaId == trackId }
                    ?.currentPosition
            position = (localPosition ?: snapshotMs).coerceAtLeast(0L)
            if (!playing) break
            delay(RoomPositionTickMs)
        }
    }
    return position
}

@Composable
private fun EqualizerGlyph(
    playing: Boolean,
    color: Color,
) {
    val heights =
        if (playing) {
            val transition = rememberInfiniteTransition(label = "equalizer")
            List(3) { bar ->
                transition
                    .animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(durationMillis = 420 + bar * 140),
                                repeatMode = RepeatMode.Reverse,
                                initialStartOffset = StartOffset(bar * 110),
                            ),
                        label = "equalizer_bar_$bar",
                    ).value
            }
        } else {
            listOf(0.35f, 0.6f, 0.45f)
        }
    Canvas(modifier = Modifier.size(width = 16.dp, height = 14.dp)) {
        val barWidth = size.width / 5f
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * barWidth * 2f, y = size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun FactChip(
    @DrawableRes iconResId: Int,
    label: String,
    highlighted: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (highlighted) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "fact_container",
    )
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = if (highlighted) colors.primary else colors.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = TogetherSpacing.sm, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpNextRow(track: MusicTogetherTrackUiModel) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(TogetherRadius.thumb))
                    .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (track.thumbnailUrl != null) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artists != null) {
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class SetupMode {
    Host,
    Join,
}

@Composable
private fun SetupPanel(
    host: MusicTogetherHostUiModel,
    join: MusicTogetherJoinUiModel,
    viewModel: MusicTogetherViewModel,
    modifier: Modifier = Modifier,
) {
    val canHost = host.visible
    val canJoin = !join.disabled
    var selected by rememberSaveable { mutableStateOf(SetupMode.Host) }
    val mode =
        when {
            canHost && canJoin -> selected
            canHost -> SetupMode.Host
            else -> SetupMode.Join
        }
    val motion = MaterialTheme.motionScheme
    val slideSpec = motion.defaultSpatialSpec<IntOffset>()
    val fadeInSpec = motion.defaultEffectsSpec<Float>()
    val fadeOutSpec = motion.fastEffectsSpec<Float>()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
    ) {
        if (canHost && canJoin) {
            ConnectedChoice(
                leading = ChoiceOption(R.drawable.fire, R.string.together_host_section),
                trailing = ChoiceOption(R.drawable.join, R.string.together_join_section),
                leadingSelected = mode == SetupMode.Host,
                enabled = true,
                height = 56.dp,
                onSelect = { leading -> selected = if (leading) SetupMode.Host else SetupMode.Join },
            )
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(slideSpec) { width -> direction * width / 4 } + fadeIn(fadeInSpec)) togetherWith
                    (slideOutHorizontally(slideSpec) { width -> -direction * width / 4 } + fadeOut(fadeOutSpec)) using
                    SizeTransform(clip = false)
            },
            label = "setup_mode",
        ) { targetMode ->
            when (targetMode) {
                SetupMode.Host -> HostSetup(host = host, viewModel = viewModel)
                SetupMode.Join -> JoinSetup(join = join, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun HostSetup(
    host: MusicTogetherHostUiModel,
    viewModel: MusicTogetherViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.md)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
        ) {
            TransportTile(
                selected = !host.onlineMode,
                iconResId = R.drawable.wifi_proxy,
                titleResId = R.string.together_lan,
                descriptionResId = R.string.together_lan_description,
                restingShape = MaterialShapes.Circle,
                selectedShape = MaterialShapes.Cookie7Sided,
                onClick = { viewModel.setHostModeOnline(false) },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
            TransportTile(
                selected = host.onlineMode,
                iconResId = R.drawable.language,
                titleResId = R.string.together_online,
                descriptionResId = R.string.together_online_description,
                restingShape = MaterialShapes.Circle,
                selectedShape = MaterialShapes.Sunny,
                onClick = { viewModel.setHostModeOnline(true) },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.segmentGap)) {
            IdentityRow(
                displayName = host.displayName,
                shape = segmentShape(index = 0, count = if (host.onlineMode) 1 else 2),
                onClick = viewModel::openDisplayNameDialog,
            )
            AnimatedVisibility(
                visible = !host.onlineMode,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                ValueRow(
                    iconResId = R.drawable.link,
                    title = stringResource(R.string.together_port),
                    value = host.port.toString(),
                    monospace = true,
                    shape = segmentShape(index = 1, count = 2),
                    onClick = viewModel::openPortDialog,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.xs)) {
            Text(
                text = stringResource(R.string.together_room_rules),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(start = TogetherSpacing.xxs)
                        .semantics { heading() },
            )
            RoomRulesGroup(host = host, viewModel = viewModel)
        }

        ProminentButton(
            enabled = host.startEnabled,
            loading = host.loading,
            iconResId = R.drawable.fire,
            label = stringResource(if (host.loading) R.string.loading else R.string.start_session),
            onClick = viewModel::startSession,
        )
    }
}

@Composable
private fun TransportTile(
    selected: Boolean,
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    @StringRes descriptionResId: Int,
    restingShape: RoundedPolygon,
    selectedShape: RoundedPolygon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tile_container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tile_content",
    )
    val glyph by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tile_glyph",
    )
    val glyphContent by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tile_glyph_content",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) TogetherRadius.card else TogetherRadius.inner,
        animationSpec = motion.defaultSpatialSpec(),
        label = "tile_corner",
    )
    val morph = remember(restingShape, selectedShape) { Morph(restingShape, selectedShape) }
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.slowSpatialSpec(),
        label = "tile_morph",
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(TogetherSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(EditorialMorphShape(morph, morphProgress))
                        .background(glyph),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null,
                    tint = glyphContent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(titleResId),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = stringResource(descriptionResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun IdentityRow(
    displayName: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val trimmed = displayName.trim()
    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TogetherSpacing.md, vertical = TogetherSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            InitialAvatar(
                name = trimmed,
                shape = MaterialShapes.Sunny,
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                size = 44.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.together_display_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = trimmed.ifEmpty { stringResource(R.string.together_display_name_placeholder) },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (trimmed.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ValueRow(
    @DrawableRes iconResId: Int,
    title: String,
    value: String,
    monospace: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TogetherSpacing.md, vertical = TogetherSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            ShapedGlyph(
                iconResId = iconResId,
                shape = MaterialShapes.Cookie4Sided,
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                size = 44.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style =
                        if (monospace) {
                            MaterialTheme.typography.titleMediumEmphasized.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            MaterialTheme.typography.titleMediumEmphasized
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RoomRulesGroup(
    host: MusicTogetherHostUiModel,
    viewModel: MusicTogetherViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TogetherSpacing.segmentGap),
    ) {
        RuleRow(
            iconResId = R.drawable.playlist_add,
            titleResId = R.string.together_allow_guests_add,
            checked = host.allowGuestsToAddTracks,
            shape = segmentShape(index = 0, count = 3),
            onCheckedChange = viewModel::setAllowGuestsToAddTracks,
        )
        RuleRow(
            iconResId = R.drawable.play,
            titleResId = R.string.together_allow_guests_control,
            checked = host.allowGuestsToControlPlayback,
            shape = segmentShape(index = 1, count = 3),
            onCheckedChange = viewModel::setAllowGuestsToControlPlayback,
        )
        RuleRow(
            iconResId = R.drawable.lock,
            titleResId = R.string.together_require_approval,
            checked = host.requireHostApprovalToJoin,
            shape = segmentShape(index = 2, count = 3),
            onCheckedChange = viewModel::setRequireHostApprovalToJoin,
        )
    }
}

@Composable
private fun RuleRow(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val glyphContainer by animateColorAsState(
        targetValue = if (checked) colors.primaryContainer else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "rule_glyph",
    )
    val glyphContent by animateColorAsState(
        targetValue = if (checked) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "rule_glyph_content",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainerLow)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(horizontal = TogetherSpacing.md, vertical = TogetherSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(if (checked) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                    .background(glyphContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = glyphContent,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent =
                if (checked) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun JoinSetup(
    join: MusicTogetherJoinUiModel,
    viewModel: MusicTogetherViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val input = join.input.trim()
    val inputEnabled = !join.disabled && !join.joining && !join.joined && !join.waitingApproval
    val showInvalid = !join.onlineMode && input.isNotEmpty() && TogetherLink.decode(input) == null

    Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.md)) {
        ConnectedChoice(
            leading = ChoiceOption(R.drawable.link, R.string.together_join_link),
            trailing = ChoiceOption(R.drawable.language, R.string.together_join_code),
            leadingSelected = !join.onlineMode,
            enabled = !join.disabled,
            height = 48.dp,
            onSelect = { leading -> viewModel.setJoinModeOnline(!leading) },
        )

        Surface(
            onClick = viewModel::openJoinDialog,
            enabled = inputEnabled,
            shape = RoundedCornerShape(TogetherRadius.inner),
            color = colors.surfaceContainerLow,
            border =
                BorderStroke(
                    width = if (showInvalid) 2.dp else 1.dp,
                    color = if (showInvalid) colors.error else colors.outlineVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(TogetherSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
            ) {
                ShapedGlyph(
                    iconResId = R.drawable.input,
                    shape = MaterialShapes.Pill,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    size = 44.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.join_session),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showInvalid) colors.error else colors.onSurfaceVariant,
                    )
                    Text(
                        text = input.ifEmpty { stringResource(join.hintResId) },
                        style =
                            if (input.isEmpty()) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                            },
                        color = if (input.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = showInvalid,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Text(
                text = stringResource(R.string.invalid_link),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = TogetherSpacing.md),
            )
        }

        ProminentButton(
            enabled = join.canJoin,
            loading = join.joining,
            iconResId = R.drawable.join,
            label = stringResource(if (join.joining) R.string.connecting else R.string.join),
            onClick = viewModel::joinSession,
        )
    }
}

private data class ChoiceOption(
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int,
)

@Composable
private fun ConnectedChoice(
    leading: ChoiceOption,
    trailing: ChoiceOption,
    leadingSelected: Boolean,
    enabled: Boolean,
    height: Dp,
    onSelect: (leading: Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        listOf(true, false).forEach { isLeading ->
            val option = if (isLeading) leading else trailing
            val checked = isLeading == leadingSelected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(isLeading) },
                enabled = enabled,
                shapes =
                    if (isLeading) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(height),
            ) {
                Icon(
                    painter = painterResource(option.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(option.labelResId),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProminentButton(
    enabled: Boolean,
    loading: Boolean,
    @DrawableRes iconResId: Int,
    label: String,
    onClick: () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = height),
    ) {
        if (loading) {
            LoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height) + 8.dp),
            )
        } else {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
        }
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(
            text = label,
            style = ButtonDefaults.textStyleFor(height),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(
    @DrawableRes iconResId: Int,
    title: String,
    glyphShape: RoundedPolygon,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = TogetherSpacing.xxs, top = TogetherSpacing.xl, bottom = TogetherSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
    ) {
        ShapedGlyph(
            iconResId = iconResId,
            shape = glyphShape,
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            size = 36.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun CountBadge(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLargeEmphasized,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = TogetherSpacing.sm, vertical = 6.dp),
        )
    }
}

@Composable
private fun ParticipantRow(
    participant: MusicTogetherParticipantUiModel,
    shape: Shape,
    viewModel: MusicTogetherViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val roleLabel =
        stringResource(
            when {
                participant.pending -> R.string.together_pending_approval
                !participant.connected -> R.string.together_participant_disconnected
                participant.host -> R.string.together_role_host
                else -> R.string.together_role_guest
            },
        )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = if (participant.pending) colors.tertiaryContainer.copy(alpha = 0.45f) else colors.surfaceContainerLow,
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 72.dp)
                    .padding(start = TogetherSpacing.md, end = TogetherSpacing.xs, top = TogetherSpacing.sm, bottom = TogetherSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            ParticipantAvatar(participant = participant)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
                ) {
                    Text(
                        text = participant.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (participant.isSelf) {
                        Surface(
                            shape = CircleShape,
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.together_you),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = TogetherSpacing.xs, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (participant.pending) colors.tertiary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ParticipantActions(participant = participant, viewModel = viewModel)
        }
    }
}

@Composable
private fun ParticipantAvatar(participant: MusicTogetherParticipantUiModel) {
    val colors = MaterialTheme.colorScheme
    val (shape, container, content) =
        when {
            participant.host -> Triple(MaterialShapes.Sunny, colors.primaryContainer, colors.onPrimaryContainer)
            participant.pending -> Triple(MaterialShapes.Clover4Leaf, colors.tertiaryContainer, colors.onTertiaryContainer)
            else -> Triple(MaterialShapes.Circle, colors.secondaryContainer, colors.onSecondaryContainer)
        }
    Box(modifier = Modifier.size(48.dp)) {
        InitialAvatar(
            name = participant.name,
            shape = shape,
            containerColor = container,
            contentColor = content,
            size = 48.dp,
            modifier = Modifier.graphicsLayer { alpha = if (participant.connected || participant.pending) 1f else 0.55f },
        )
        if (!participant.pending) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(14.dp)
                        .border(width = 2.dp, color = colors.surfaceContainerLow, shape = CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(if (participant.connected) colors.primary else colors.outline),
            )
        }
    }
}

@Composable
private fun ParticipantActions(
    participant: MusicTogetherParticipantUiModel,
    viewModel: MusicTogetherViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val hasMenu = participant.showTransferHostAction || participant.showModerationActions
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (participant.showApproveActions) {
            FilledTonalIconButton(
                onClick = { viewModel.approveParticipant(participant.id) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = stringResource(R.string.together_approve),
                )
            }
            IconButton(
                onClick = { viewModel.rejectParticipant(participant.id) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.together_reject),
                )
            }
        }
        if (hasMenu) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { expanded = true },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.together_participant_actions, participant.name),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(TogetherRadius.inner),
                ) {
                    if (participant.showTransferHostAction) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.together_transfer_host)) },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.sync), contentDescription = null)
                            },
                            onClick = {
                                expanded = false
                                viewModel.requestTransferHost(participant.id)
                            },
                        )
                    }
                    if (participant.showModerationActions) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.together_kick)) },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.kick), contentDescription = null)
                            },
                            onClick = {
                                expanded = false
                                viewModel.requestKickParticipant(participant.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.together_ban)) },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.block), contentDescription = null)
                            },
                            colors = MenuDefaults.itemColors(textColor = colors.error, leadingIconColor = colors.error),
                            onClick = {
                                expanded = false
                                viewModel.requestBanParticipant(participant.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    item: MusicTogetherActivityLogItemUiModel,
    latest: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val railColor = colors.outlineVariant
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(start = TogetherSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        if (!last) {
                            val x = size.width / 2f
                            drawLine(
                                color = railColor,
                                start = Offset(x, 36.dp.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    },
            contentAlignment = Alignment.TopCenter,
        ) {
            ShapedGlyph(
                iconResId = item.iconResId,
                shape = if (latest) MaterialShapes.Cookie6Sided else MaterialShapes.Circle,
                containerColor = if (latest) colors.primaryContainer else colors.surfaceContainerHighest,
                contentColor = if (latest) colors.onPrimaryContainer else colors.onSurfaceVariant,
                size = 36.dp,
                iconSize = 18.dp,
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = 6.dp, bottom = if (last) 0.dp else TogetherSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = activityMessage(item),
                style = if (latest) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            Text(
                text = item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun activityMessage(item: MusicTogetherActivityLogItemUiModel): String =
    when (item.args.size) {
        0 -> stringResource(item.messageResId)
        1 -> stringResource(item.messageResId, item.args[0])
        2 -> stringResource(item.messageResId, item.args[0], item.args[1])
        else -> stringResource(item.messageResId)
    }

@Composable
private fun RoomEmptyState(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TogetherRadius.inner),
        color = colors.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(TogetherSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.md),
        ) {
            Box(modifier = Modifier.size(width = 64.dp, height = 48.dp)) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                            .clip(MaterialShapes.Clover4Leaf.toShape())
                            .background(colors.tertiaryContainer),
                )
                ShapedGlyph(
                    iconResId = iconResId,
                    shape = MaterialShapes.Cookie9Sided,
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    size = 44.dp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleResId),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                Text(
                    text = stringResource(bodyResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShapedGlyph(
    @DrawableRes iconResId: Int,
    shape: RoundedPolygon,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    iconSize: Dp = size * 0.5f,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape.toShape())
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun InitialAvatar(
    name: String,
    shape: RoundedPolygon,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val initial =
        remember(name) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                String(Character.toChars(trimmed.codePointAt(0))).uppercase()
            }
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape.toShape())
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

@Composable
private fun CenteredStatus(
    @DrawableRes iconResId: Int,
    shape: RoundedPolygon,
    containerColor: Color,
    contentColor: Color,
    title: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(TogetherSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapedGlyph(
            iconResId = iconResId,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            size = 96.dp,
        )
        Spacer(Modifier.height(TogetherSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TogetherSpacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MusicTogetherDialogs(
    model: MusicTogetherUiModel,
    viewModel: MusicTogetherViewModel,
) {
    if (model.showWelcomeDialog) {
        WelcomeDialog(
            dontShowAgain = model.welcomeDontShowAgain,
            onDontShowAgainChange = viewModel::setWelcomeDontShowAgain,
            onGotIt = viewModel::confirmWelcomeDialog,
            onDismiss = viewModel::dismissWelcomeDialog,
        )
    }

    when (val dialog = model.dialog) {
        MusicTogetherDialogUiState.None -> {
            Unit
        }

        is MusicTogetherDialogUiState.DisplayName -> {
            TextFieldDialog(
                title = { Text(text = stringResource(R.string.together_display_name)) },
                initialTextFieldValue = TextFieldValue(dialog.initialValue),
                placeholder = { Text(text = stringResource(R.string.together_display_name_placeholder)) },
                isInputValid = { it.trim().isNotBlank() },
                onDone = viewModel::submitDisplayName,
                onDismiss = viewModel::dismissDialog,
            )
        }

        is MusicTogetherDialogUiState.Port -> {
            TextFieldDialog(
                title = { Text(text = stringResource(R.string.together_port)) },
                initialTextFieldValue = TextFieldValue(dialog.initialValue),
                placeholder = { Text(text = dialog.initialValue) },
                isInputValid = { it.trim().toIntOrNull() in 1..65535 },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                onDone = viewModel::submitPort,
                onDismiss = viewModel::dismissDialog,
            )
        }

        is MusicTogetherDialogUiState.Join -> {
            TextFieldDialog(
                title = { Text(text = stringResource(R.string.join_session)) },
                initialTextFieldValue = TextFieldValue(dialog.initialValue),
                placeholder = { Text(text = stringResource(dialog.placeholderResId)) },
                singleLine = false,
                maxLines = 8,
                isInputValid = {
                    if (dialog.onlineMode) {
                        it.trim().isNotBlank()
                    } else {
                        TogetherLink.decode(it) != null
                    }
                },
                onDone = viewModel::submitJoinInput,
                onDismiss = viewModel::dismissDialog,
            )
        }

        is MusicTogetherDialogUiState.KickParticipant -> {
            ConfirmParticipantDialog(
                iconResId = R.drawable.kick,
                titleResId = R.string.together_kick,
                bodyResId = R.string.together_kick_confirm,
                participantName = dialog.participantName,
                confirmResId = R.string.together_kick,
                destructive = true,
                onConfirm = { viewModel.confirmKickParticipant(dialog.participantId) },
                onDismiss = viewModel::dismissDialog,
            )
        }

        is MusicTogetherDialogUiState.BanParticipant -> {
            ConfirmParticipantDialog(
                iconResId = R.drawable.block,
                titleResId = R.string.together_ban,
                bodyResId = R.string.together_ban_confirm,
                participantName = dialog.participantName,
                confirmResId = R.string.together_ban,
                destructive = true,
                onConfirm = { viewModel.confirmBanParticipant(dialog.participantId) },
                onDismiss = viewModel::dismissDialog,
            )
        }

        is MusicTogetherDialogUiState.TransferHost -> {
            ConfirmParticipantDialog(
                iconResId = R.drawable.sync,
                titleResId = R.string.together_transfer_host,
                bodyResId = R.string.together_transfer_host_confirm,
                participantName = dialog.participantName,
                confirmResId = R.string.together_transfer_host,
                destructive = false,
                onConfirm = { viewModel.confirmTransferHost(dialog.participantId) },
                onDismiss = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun WelcomeDialog(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onGotIt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(TogetherRadius.hero),
        containerColor = colors.surfaceContainerHigh,
        icon = { WelcomeEmblem() },
        title = {
            Text(
                text = stringResource(R.string.together_welcome_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.md)) {
                Text(
                    text = stringResource(R.string.together_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(TogetherSpacing.segmentGap)) {
                    WelcomeStep(
                        iconResId = R.drawable.fire,
                        shape = MaterialShapes.Sunny,
                        titleResId = R.string.together_welcome_host_title,
                        bodyResId = R.string.together_welcome_host_body,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        segment = segmentShape(index = 0, count = 3),
                    )
                    WelcomeStep(
                        iconResId = R.drawable.link,
                        shape = MaterialShapes.Cookie7Sided,
                        titleResId = R.string.together_welcome_join_title,
                        bodyResId = R.string.together_welcome_join_body,
                        containerColor = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer,
                        segment = segmentShape(index = 1, count = 3),
                    )
                    WelcomeStep(
                        iconResId = R.drawable.lock,
                        shape = MaterialShapes.Clover4Leaf,
                        titleResId = R.string.together_welcome_permissions_title,
                        bodyResId = R.string.together_welcome_permissions_body,
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer,
                        segment = segmentShape(index = 2, count = 3),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TogetherRadius.inner))
                            .toggleable(
                                value = dontShowAgain,
                                role = Role.Checkbox,
                                onValueChange = onDontShowAgainChange,
                            ).padding(vertical = TogetherSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.xs),
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = TogetherSpacing.xs),
                    )
                    Text(
                        text = stringResource(R.string.together_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGotIt,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.got_it))
            }
        },
    )
}

@Composable
private fun WelcomeEmblem() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(width = 112.dp, height = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        ShapedGlyph(
            iconResId = R.drawable.link,
            shape = MaterialShapes.Clover4Leaf,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
            size = 44.dp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        ShapedGlyph(
            iconResId = R.drawable.lock,
            shape = MaterialShapes.Cookie7Sided,
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            size = 44.dp,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        ShapedGlyph(
            iconResId = R.drawable.multi_user,
            shape = MaterialShapes.Sunny,
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            size = 64.dp,
        )
    }
}

@Composable
private fun WelcomeStep(
    @DrawableRes iconResId: Int,
    shape: RoundedPolygon,
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
    containerColor: Color,
    contentColor: Color,
    segment: Shape,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(segment)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(TogetherSpacing.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(TogetherSpacing.sm),
    ) {
        ShapedGlyph(
            iconResId = iconResId,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            size = 36.dp,
            iconSize = 18.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            Text(
                text = stringResource(bodyResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmParticipantDialog(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
    participantName: String,
    @StringRes confirmResId: Int,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(TogetherRadius.hero),
        containerColor = colors.surfaceContainerHigh,
        icon = {
            ShapedGlyph(
                iconResId = iconResId,
                shape = if (destructive) MaterialShapes.Cookie4Sided else MaterialShapes.Sunny,
                containerColor = if (destructive) colors.errorContainer else colors.primaryContainer,
                contentColor = if (destructive) colors.onErrorContainer else colors.onPrimaryContainer,
                size = 56.dp,
                iconSize = 26.dp,
            )
        },
        title = {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(bodyResId, participantName),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(confirmResId))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
    )
}

private class ShapeMorphState(
    val morph: Morph,
    val progress: Float,
)

@Composable
private fun rememberShapeMorph(target: RoundedPolygon): ShapeMorphState {
    var shapes by remember { mutableStateOf(target to target) }
    val progress = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(target) {
        if (shapes.second !== target) {
            shapes = shapes.second to target
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }
    val morph = remember(shapes) { Morph(shapes.first, shapes.second) }
    return ShapeMorphState(morph = morph, progress = progress.value)
}

@Composable
private fun rememberContinuousRotation(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "orb_rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "orb_rotation_value",
    )
    return rotation
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private fun segmentShape(
    index: Int,
    count: Int,
): Shape {
    val top = if (index == 0) TogetherRadius.card else TogetherRadius.segmentInner
    val bottom = if (index == count - 1) TogetherRadius.card else TogetherRadius.segmentInner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private const val RoomPositionTickMs = 500L

private object TogetherSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val segmentGap = 2.dp
}

private object TogetherRadius {
    val hero = 36.dp
    val card = 28.dp
    val inner = 20.dp
    val thumb = 12.dp
    val segmentInner = 6.dp
}
