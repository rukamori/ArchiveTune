/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.MusicTogetherActivityLogItemUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherActivityLogUiModels
import moe.rukamori.archivetune.viewmodels.MusicTogetherDialogUiState
import moe.rukamori.archivetune.viewmodels.MusicTogetherEffect
import moe.rukamori.archivetune.viewmodels.MusicTogetherHostUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherJoinUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherParticipantUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherParticipantUiModels
import moe.rukamori.archivetune.viewmodels.MusicTogetherPlaybackUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherScreenState
import moe.rukamori.archivetune.viewmodels.MusicTogetherSessionShareUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherStatusUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherUiModel
import moe.rukamori.archivetune.viewmodels.MusicTogetherViewModel
import moe.rukamori.archivetune.ui.component.IconButton as AtIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTogetherScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: MusicTogetherViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
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
                title = { Text(stringResource(R.string.music_together)) },
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
            when (val state = screenState) {
                MusicTogetherScreenState.Loading -> LoadingContent()
                MusicTogetherScreenState.Empty -> EmptyContent()
                is MusicTogetherScreenState.Error -> ErrorContent(messageResId = state.messageResId)
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

@Composable
private fun MusicTogetherContent(
    model: MusicTogetherUiModel,
    useSupportingPane: Boolean,
    viewModel: MusicTogetherViewModel,
) {
    if (useSupportingPane) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = MusicTogetherSpacing.md, vertical = MusicTogetherSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.md),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .widthIn(max = 720.dp),
                contentPadding = PaddingValues(bottom = MusicTogetherSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                item(contentType = "status") {
                    StatusCard(
                        status = model.status,
                        sessionShare = model.sessionShare,
                        onCopy = viewModel::copySessionValue,
                        onShare = viewModel::shareSessionValue,
                        onLeave = viewModel::leaveSession,
                    )
                }
                item(contentType = "playback") {
                    PlaybackCard(playback = model.playback)
                }
                if (model.host.visible) {
                    item(contentType = "host") {
                        HostControlsCard(host = model.host, viewModel = viewModel)
                    }
                }
                item(contentType = "join") {
                    JoinControlsCard(join = model.join, viewModel = viewModel)
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                ParticipantsCard(
                    participants = model.participants,
                    viewModel = viewModel,
                    modifier =
                        if (model.participants.isEmpty) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .weight(0.45f)
                        },
                    fillAvailableHeight = !model.participants.isEmpty,
                )
                ActivityLogCard(
                    activityLog = model.activityLog,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    fillAvailableHeight = true,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = MusicTogetherSpacing.sm,
                    top = MusicTogetherSpacing.xs,
                    end = MusicTogetherSpacing.sm,
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        ) {
            item(contentType = "status") {
                StatusCard(
                    status = model.status,
                    sessionShare = model.sessionShare,
                    onCopy = viewModel::copySessionValue,
                    onShare = viewModel::shareSessionValue,
                    onLeave = viewModel::leaveSession,
                )
            }
            item(contentType = "playback") {
                PlaybackCard(playback = model.playback)
            }
            if (model.host.visible) {
                item(contentType = "host") {
                    HostControlsCard(host = model.host, viewModel = viewModel)
                }
            }
            item(contentType = "join") {
                JoinControlsCard(join = model.join, viewModel = viewModel)
            }
            item(contentType = "participants") {
                ParticipantsCard(participants = model.participants, viewModel = viewModel)
            }
            item(contentType = "activity") {
                ActivityLogCard(activityLog = model.activityLog)
            }
        }
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
        MusicTogetherDialogUiState.None -> Unit
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
                        moe.rukamori.archivetune.together.TogetherLink.decode(it) != null
                    }
                },
                onDone = viewModel::submitJoinInput,
                onDismiss = viewModel::dismissDialog,
            )
        }

        is MusicTogetherDialogUiState.KickParticipant -> {
            ConfirmParticipantDialog(
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
private fun StatusCard(
    status: MusicTogetherStatusUiModel,
    sessionShare: MusicTogetherSessionShareUiModel?,
    onCopy: (Int, String) -> Unit,
    onShare: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val accent =
        when {
            status.error -> MaterialTheme.colorScheme.error
            status.active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                AccentIcon(iconResId = status.iconResId, accent = accent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(status.titleResId),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Text(
                        text = stringResource(status.stateLabelResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (status.active) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent),
                    )
                }
                if (status.canLeave) {
                    FilledTonalButton(
                        onClick = onLeave,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.leave),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(MusicTogetherSpacing.xs))
                        Text(text = stringResource(R.string.leave))
                    }
                }
            }

            if (status.errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = status.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(MusicTogetherSpacing.sm),
                    )
                }
            }

            if (sessionShare != null) {
                SessionShareCard(sessionShare = sessionShare, onCopy = onCopy, onShare = onShare)
            }
        }
    }
}

@Composable
private fun SessionShareCard(
    sessionShare: MusicTogetherSessionShareUiModel,
    onCopy: (Int, String) -> Unit,
    onShare: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
        ) {
            Text(
                text = stringResource(sessionShare.labelResId),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = sessionShare.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = sessionShare.maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs)) {
                FilledTonalButton(
                    onClick = { onCopy(sessionShare.labelResId, sessionShare.value) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(MusicTogetherSpacing.xs))
                    Text(text = stringResource(R.string.copy_link))
                }
                TextButton(
                    onClick = { onShare(sessionShare.value) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(MusicTogetherSpacing.xs))
                    Text(text = stringResource(R.string.share))
                }
            }
        }
    }
}

@Composable
private fun PlaybackCard(playback: MusicTogetherPlaybackUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                AccentIcon(iconResId = R.drawable.music_note, accent = MaterialTheme.colorScheme.tertiary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_current_playback),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = playback.title ?: stringResource(R.string.together_playback_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playback.artists != null) {
                        Text(
                            text = playback.artists,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
            ) {
                InfoPill(text = stringResource(playback.playbackStateResId))
                InfoPill(text = stringResource(R.string.together_queue_count, playback.queueSize))
                if (playback.currentIndexLabel != null) {
                    InfoPill(text = playback.currentIndexLabel)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
            ) {
                InfoPill(
                    text =
                        if (playback.shuffleEnabled) {
                            stringResource(R.string.together_shuffle_on)
                        } else {
                            stringResource(R.string.together_shuffle_off)
                        },
                )
                InfoPill(text = stringResource(R.string.together_repeat_mode, playback.repeatMode))
            }
        }
    }
}

@Composable
private fun HostControlsCard(
    host: MusicTogetherHostUiModel,
    viewModel: MusicTogetherViewModel,
) {
    SectionCard(
        iconResId = R.drawable.fire,
        titleResId = R.string.together_host_section,
        subtitleResId = R.string.together_display_name,
        accent = MaterialTheme.colorScheme.primary,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !host.onlineMode,
                onClick = { viewModel.setHostModeOnline(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
            ) {
                Text(text = stringResource(R.string.together_lan))
            }
            SegmentedButton(
                selected = host.onlineMode,
                onClick = { viewModel.setHostModeOnline(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
            ) {
                Text(text = stringResource(R.string.together_online))
            }
        }
        SettingsRow(
            iconResId = R.drawable.person,
            titleResId = R.string.together_display_name,
            subtitle = host.displayName,
            onClick = viewModel::openDisplayNameDialog,
        )
        if (!host.onlineMode) {
            SettingsRow(
                iconResId = R.drawable.link,
                titleResId = R.string.together_port,
                subtitle = host.port.toString(),
                onClick = viewModel::openPortDialog,
            )
        }
        ToggleRow(
            iconResId = R.drawable.playlist_add,
            titleResId = R.string.together_allow_guests_add,
            checked = host.allowGuestsToAddTracks,
            onCheckedChange = viewModel::setAllowGuestsToAddTracks,
        )
        ToggleRow(
            iconResId = R.drawable.play,
            titleResId = R.string.together_allow_guests_control,
            checked = host.allowGuestsToControlPlayback,
            onCheckedChange = viewModel::setAllowGuestsToControlPlayback,
        )
        ToggleRow(
            iconResId = R.drawable.lock,
            titleResId = R.string.together_require_approval,
            checked = host.requireHostApprovalToJoin,
            onCheckedChange = viewModel::setRequireHostApprovalToJoin,
        )
        Button(
            enabled = host.startEnabled,
            onClick = viewModel::startSession,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            if (host.loading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.loading))
            } else {
                Icon(
                    painter = painterResource(R.drawable.fire),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.start_session))
            }
        }
    }
}

@Composable
private fun JoinControlsCard(
    join: MusicTogetherJoinUiModel,
    viewModel: MusicTogetherViewModel,
) {
    SectionCard(
        iconResId = R.drawable.multi_user,
        titleResId = R.string.together_join_section,
        subtitleResId = R.string.join_session,
        accent = MaterialTheme.colorScheme.tertiary,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !join.onlineMode,
                enabled = !join.disabled,
                onClick = { viewModel.setJoinModeOnline(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
            ) {
                Text(text = stringResource(R.string.together_join_link))
            }
            SegmentedButton(
                selected = join.onlineMode,
                enabled = !join.disabled,
                onClick = { viewModel.setJoinModeOnline(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
            ) {
                Text(text = stringResource(R.string.together_join_code))
            }
        }
        SettingsRow(
            iconResId = R.drawable.input,
            titleResId = R.string.join_session,
            subtitle = join.input.trim().ifBlank { stringResource(join.hintResId) },
            subtitleMaxLines = 2,
            onClick = if (!join.disabled && !join.joining && !join.joined && !join.waitingApproval) viewModel::openJoinDialog else null,
        )
        FilledTonalButton(
            enabled = join.canJoin,
            onClick = viewModel::joinSession,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            when {
                join.joining -> {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(MusicTogetherSpacing.xs))
                    Text(text = stringResource(R.string.connecting))
                }

                join.waitingApproval -> {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(MusicTogetherSpacing.xs))
                    Text(text = stringResource(R.string.together_waiting_approval))
                }

                join.joined -> {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(MusicTogetherSpacing.xs))
                    Text(text = stringResource(R.string.joined))
                }

                else -> {
                    Icon(
                        painter = painterResource(R.drawable.join),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(MusicTogetherSpacing.xs))
                    Text(text = stringResource(R.string.join))
                }
            }
        }
    }
}

@Composable
private fun ParticipantsCard(
    participants: MusicTogetherParticipantUiModels,
    viewModel: MusicTogetherViewModel,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    SectionCard(
        iconResId = R.drawable.multi_user,
        titleResId = R.string.together_participants,
        subtitle = stringResource(R.string.together_connected_count, participants.size),
        accent = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    ) {
        if (participants.isEmpty) {
            EmptyPanel(
                iconResId = R.drawable.person,
                titleResId = R.string.together_participants_empty,
                bodyResId = R.string.together_participants_empty_body,
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillAvailableHeight) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.heightIn(max = 280.dp)
                            },
                        ),
            ) {
                items(
                    count = participants.size,
                    key = { index -> participants[index].id },
                    contentType = { "participant" },
                ) { index ->
                    ParticipantRow(participant = participants[index], viewModel = viewModel)
                    if (index < participants.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: MusicTogetherParticipantUiModel,
    viewModel: MusicTogetherViewModel,
) {
    ListItem(
        headlineContent = {
            Text(
                text = participant.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text =
                    stringResource(
                        when {
                            participant.host -> R.string.together_role_host
                            participant.pending -> R.string.together_pending_approval
                            else -> R.string.together_role_guest
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AccentIcon(
                iconResId = if (participant.host) R.drawable.fire else R.drawable.person,
                accent = if (participant.host) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                size = 40.dp,
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xxs)) {
                if (participant.showApproveActions) {
                    IconButton(onClick = { viewModel.approveParticipant(participant.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.together_approve),
                        )
                    }
                    IconButton(onClick = { viewModel.rejectParticipant(participant.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.together_reject),
                        )
                    }
                }
                if (participant.showTransferHostAction) {
                    IconButton(onClick = { viewModel.requestTransferHost(participant.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = stringResource(R.string.together_transfer_host),
                        )
                    }
                }
                if (participant.showModerationActions) {
                    IconButton(onClick = { viewModel.requestKickParticipant(participant.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.kick),
                            contentDescription = stringResource(R.string.together_kick),
                        )
                    }
                    IconButton(onClick = { viewModel.requestBanParticipant(participant.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.block),
                            contentDescription = stringResource(R.string.together_ban),
                        )
                    }
                }
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ActivityLogCard(
    activityLog: MusicTogetherActivityLogUiModels,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    SectionCard(
        iconResId = R.drawable.history,
        titleResId = R.string.together_activity_log,
        subtitleResId = R.string.together_activity_log_subtitle,
        accent = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    ) {
        if (activityLog.isEmpty) {
            EmptyPanel(
                iconResId = R.drawable.history,
                titleResId = R.string.together_activity_log_empty,
                bodyResId = R.string.together_activity_log_empty_body,
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillAvailableHeight) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.heightIn(max = 360.dp)
                            },
                        ),
                verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
            ) {
                items(
                    count = activityLog.size,
                    key = { index -> activityLog[index].id },
                    contentType = { "activity_log_item" },
                ) { index ->
                    ActivityLogRow(item = activityLog[index])
                }
            }
        }
    }
}

@Composable
private fun ActivityLogRow(item: MusicTogetherActivityLogItemUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(item.iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activityMessage(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SectionCard(
    @androidx.annotation.DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    @StringRes subtitleResId: Int? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                AccentIcon(iconResId = iconResId, accent = accent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titleResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitleText =
                        when {
                            subtitle != null -> subtitle
                            subtitleResId != null -> stringResource(subtitleResId)
                            else -> null
                        }
                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    @androidx.annotation.DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AccentIcon(iconResId = iconResId, accent = MaterialTheme.colorScheme.primary, size = 40.dp)
        },
        trailingContent = {
            if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ToggleRow(
    @androidx.annotation.DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingContent = {
            AccentIcon(iconResId = iconResId, accent = MaterialTheme.colorScheme.primary, size = 40.dp)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AccentIcon(
    @androidx.annotation.DrawableRes iconResId: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MusicTogetherSpacing.sm, vertical = MusicTogetherSpacing.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyPanel(
    @androidx.annotation.DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentIcon(iconResId = iconResId, accent = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(bodyResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyPanel(
            iconResId = R.drawable.multi_user,
            titleResId = R.string.together_idle,
            bodyResId = R.string.together_activity_log_empty_body,
        )
    }
}

@Composable
private fun ErrorContent(@StringRes messageResId: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyPanel(
            iconResId = R.drawable.error,
            titleResId = R.string.together_error_state,
            bodyResId = messageResId,
        )
    }
}

@Composable
private fun WelcomeDialog(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onGotIt: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                AccentIcon(iconResId = R.drawable.fire, accent = MaterialTheme.colorScheme.primary, size = 40.dp)
                Text(
                    text = stringResource(R.string.together_welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm)) {
                Text(
                    text = stringResource(R.string.together_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(MusicTogetherSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
                    ) {
                        InstructionRow(
                            iconResId = R.drawable.fire,
                            titleResId = R.string.together_welcome_host_title,
                            bodyResId = R.string.together_welcome_host_body,
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        InstructionRow(
                            iconResId = R.drawable.link,
                            titleResId = R.string.together_welcome_join_title,
                            bodyResId = R.string.together_welcome_join_body,
                            accent = MaterialTheme.colorScheme.tertiary,
                        )
                        InstructionRow(
                            iconResId = R.drawable.lock,
                            titleResId = R.string.together_welcome_permissions_title,
                            bodyResId = R.string.together_welcome_permissions_body,
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .toggleable(
                                value = dontShowAgain,
                                role = Role.Checkbox,
                                onValueChange = onDontShowAgainChange,
                            ).padding(vertical = MusicTogetherSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null,
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
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.got_it))
            }
        },
    )
}

@Composable
private fun InstructionRow(
    @androidx.annotation.DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
    ) {
        AccentIcon(iconResId = iconResId, accent = accent, size = 38.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xxs)) {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
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
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
    participantName: String,
    @StringRes confirmResId: Int,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = stringResource(titleResId)) },
        text = {
            Text(
                text = stringResource(bodyResId, participantName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

private object MusicTogetherSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
}
