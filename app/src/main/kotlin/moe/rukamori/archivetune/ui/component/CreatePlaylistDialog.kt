/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.viewmodels.CreatePlaylistEvent
import moe.rukamori.archivetune.viewmodels.CreatePlaylistScreenState
import moe.rukamori.archivetune.viewmodels.CreatePlaylistUiData
import moe.rukamori.archivetune.viewmodels.CreatePlaylistViewModel

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    viewModel: CreatePlaylistViewModel = hiltViewModel(),
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismiss =
        remember(viewModel) {
            {
                viewModel.close()
                currentOnDismiss()
            }
        }
    val updateName = remember(viewModel) { viewModel::updateName }
    val updateSyncRequested = remember(viewModel) { viewModel::updateSyncRequested }
    val submit = remember(viewModel) { viewModel::submit }

    LaunchedEffect(initialTextFieldValue, allowSyncing, viewModel) {
        viewModel.open(
            initialName = initialTextFieldValue.orEmpty(),
            allowSyncing = allowSyncing,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == CreatePlaylistEvent.Created) {
                viewModel.close()
                currentOnDismiss()
            }
        }
    }

    val placeholderData =
        remember(initialTextFieldValue, allowSyncing) {
            CreatePlaylistUiData(
                name = initialTextFieldValue.orEmpty(),
                allowSyncing = allowSyncing,
                isSignedIn = false,
                isSyncEnabled = false,
                syncRequested = false,
                isSubmitting = false,
            )
        }

    val (data, isLoading, errorMessageResId) =
        when (val state = screenState) {
            CreatePlaylistScreenState.Loading -> Triple(placeholderData, true, null)
            is CreatePlaylistScreenState.Success -> Triple(state.data, false, null)
            CreatePlaylistScreenState.Empty -> Triple(placeholderData.copy(name = ""), false, null)
            is CreatePlaylistScreenState.Error -> Triple(state.data, false, state.messageResId)
        }

    CreatePlaylistDialogContent(
        data = data,
        isLoading = isLoading,
        errorMessageResId = errorMessageResId,
        onNameChange = updateName,
        onSyncRequestedChange = updateSyncRequested,
        onSubmit = submit,
        onDismiss = dismiss,
    )
}

@Composable
private fun CreatePlaylistDialogContent(
    data: CreatePlaylistUiData,
    isLoading: Boolean,
    @StringRes errorMessageResId: Int?,
    onNameChange: (String) -> Unit,
    onSyncRequestedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inputEnabled = !isLoading && !data.isSubmitting
    val canSubmit = !isLoading && data.canSubmit

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = !data.isSubmitting,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                ) {
                    PlaylistCoverBadge(
                        name = data.name,
                        syncRequested = data.syncRequested,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.create_playlist),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    PlaylistNameField(
                        name = data.name,
                        enabled = inputEnabled,
                        isError = errorMessageResId != null,
                        canSubmit = canSubmit,
                        onNameChange = onNameChange,
                        onSubmit = onSubmit,
                    )
                    if (data.allowSyncing) {
                        Spacer(Modifier.height(20.dp))
                        PlaylistDestinationSelector(
                            data = data,
                            enabled = inputEnabled,
                            onSyncRequestedChange = onSyncRequestedChange,
                        )
                        PlaylistDestinationNote(
                            data = data,
                            isLoading = isLoading,
                        )
                    }
                    PlaylistErrorMessage(errorMessageResId = errorMessageResId)
                    Spacer(Modifier.height(28.dp))
                    PlaylistDialogFooter(
                        canSubmit = canSubmit,
                        isSubmitting = data.isSubmitting,
                        onDismiss = onDismiss,
                        onSubmit = onSubmit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverBadge(
    name: String,
    syncRequested: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val initial =
        remember(name) {
            name.trim().takeIf(String::isNotEmpty)?.let { trimmed ->
                String(Character.toChars(trimmed.codePointAt(0))).uppercase()
            }
        }
    val hasName = initial != null
    val morph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie4Sided) }
    val morphProgress by animateFloatAsState(
        targetValue = if (hasName) 1f else 0f,
        animationSpec = if (animationsDisabled) motion.fastEffectsSpec() else motion.defaultSpatialSpec(),
        label = "playlistCoverMorph",
    )
    val rotation by animateFloatAsState(
        targetValue = if (hasName && !animationsDisabled) 45f else 0f,
        animationSpec = motion.slowSpatialSpec(),
        label = "playlistCoverRotation",
    )
    val containerColor by animateColorAsState(
        targetValue = if (hasName) colors.primary else colors.secondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "playlistCoverContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (hasName) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "playlistCoverContent",
    )
    val entrance = remember { Animatable(if (animationsDisabled) 1f else 0.6f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) entrance.snapTo(1f) else entrance.animateTo(1f, motion.slowSpatialSpec())
    }

    Box(
        modifier =
            Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation
                        shape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f))
                        clip = true
                    }
                    .background(containerColor),
        )
        AnimatedContent(
            targetState = initial,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f)) using
                    SizeTransform(clip = false)
            },
            contentAlignment = Alignment.Center,
            label = "playlistCoverGlyph",
            modifier = Modifier.align(Alignment.Center),
        ) { glyph ->
            if (glyph != null) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = contentColor,
                    maxLines = 1,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.playlist_add),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = syncRequested,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.tertiary,
                contentColor = colors.onTertiary,
                border = BorderStroke(3.dp, colors.surfaceContainerHigh),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameField(
    name: String,
    enabled: Boolean,
    isError: Boolean,
    canSubmit: Boolean,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(focusRequester) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(text = stringResource(R.string.playlist_name)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = name.isNotEmpty() && enabled,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                IconButton(onClick = { onNameChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.clear),
                    )
                }
            }
        },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = MaterialTheme.shapes.large,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
    )
}

@Composable
private fun PlaylistDestinationSelector(
    data: CreatePlaylistUiData,
    enabled: Boolean,
    onSyncRequestedChange: (Boolean) -> Unit,
) {
    val onlineAvailable = data.isSignedIn && data.isSyncEnabled
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PlaylistDestinationOption(
            checked = !data.syncRequested,
            enabled = enabled,
            iconRes = R.drawable.library_music,
            labelRes = R.string.playlist_source_local,
            leading = true,
            onClick = { onSyncRequestedChange(false) },
            modifier = Modifier.weight(1f),
        )
        PlaylistDestinationOption(
            checked = data.syncRequested,
            enabled = enabled && onlineAvailable,
            iconRes = R.drawable.playlist_online,
            labelRes = R.string.playlist_source_online,
            leading = false,
            onClick = { onSyncRequestedChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaylistDestinationOption(
    checked: Boolean,
    enabled: Boolean,
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    leading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onClick() },
        enabled = enabled,
        shapes =
            if (leading) {
                ButtonGroupDefaults.connectedLeadingButtonShapes()
            } else {
                ButtonGroupDefaults.connectedTrailingButtonShapes()
            },
        colors =
            ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = modifier.height(56.dp),
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.7f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f))
            },
            label = "playlistDestinationIcon",
        ) { isChecked ->
            Icon(
                painter = painterResource(if (isChecked) R.drawable.check else iconRes),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            text = stringResource(labelRes),
            style = if (checked) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class PlaylistDestinationStatus {
    None,
    SyncNote,
    NotSignedIn,
    SyncDisabled,
}

@Composable
private fun PlaylistDestinationNote(
    data: CreatePlaylistUiData,
    isLoading: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val status =
        when {
            isLoading -> PlaylistDestinationStatus.None
            data.syncRequested -> PlaylistDestinationStatus.SyncNote
            !data.isSignedIn -> PlaylistDestinationStatus.NotSignedIn
            !data.isSyncEnabled -> PlaylistDestinationStatus.SyncDisabled
            else -> PlaylistDestinationStatus.None
        }

    AnimatedContent(
        targetState = status,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        label = "playlistDestinationNote",
        modifier = Modifier.fillMaxWidth(),
    ) { target ->
        when (target) {
            PlaylistDestinationStatus.None -> Spacer(Modifier.fillMaxWidth())
            PlaylistDestinationStatus.SyncNote ->
                PlaylistMessageRow(
                    iconRes = R.drawable.sync,
                    text = stringResource(R.string.allows_for_sync_witch_youtube),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 12.dp),
                )
            PlaylistDestinationStatus.NotSignedIn ->
                PlaylistMessageRow(
                    iconRes = R.drawable.lock,
                    text = stringResource(R.string.not_logged_in_youtube),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            PlaylistDestinationStatus.SyncDisabled ->
                PlaylistMessageRow(
                    iconRes = R.drawable.info,
                    text = stringResource(R.string.sync_disabled),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
        }
    }
}

@Composable
private fun PlaylistErrorMessage(
    @StringRes errorMessageResId: Int?,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = errorMessageResId,
        transitionSpec = {
            fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motion.fastSpatialSpec() }
        },
        label = "playlistError",
        modifier = Modifier.fillMaxWidth(),
    ) { resId ->
        if (resId == null) {
            Spacer(Modifier.fillMaxWidth())
        } else {
            PlaylistMessageRow(
                iconRes = R.drawable.error,
                text = stringResource(resId),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun PlaylistMessageRow(
    @DrawableRes iconRes: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaylistDialogFooter(
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            onClick = onDismiss,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(text = stringResource(R.string.cancel))
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.height(48.dp),
        ) {
            AnimatedContent(
                targetState = isSubmitting,
                transitionSpec = {
                    (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec())) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()))
                },
                label = "playlistSubmitIcon",
            ) { submitting ->
                if (submitting) {
                    LoadingIndicator(
                        color = LocalContentColor.current,
                        modifier = Modifier.size(ButtonDefaults.IconSize + 4.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.create_playlist_confirm))
        }
    }
}
