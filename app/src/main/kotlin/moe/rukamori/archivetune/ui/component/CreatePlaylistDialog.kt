/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
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

    when (val state = screenState) {
        CreatePlaylistScreenState.Loading -> {
            CreatePlaylistDialogContent(
                data =
                    CreatePlaylistUiData(
                        name = initialTextFieldValue.orEmpty(),
                        allowSyncing = allowSyncing,
                        isSignedIn = false,
                        isSyncEnabled = false,
                        syncRequested = false,
                        isSubmitting = false,
                    ),
                isLoading = true,
                errorMessageResId = null,
                onNameChange = updateName,
                onSyncRequestedChange = updateSyncRequested,
                onSubmit = submit,
                onDismiss = dismiss,
            )
        }

        is CreatePlaylistScreenState.Success -> {
            CreatePlaylistDialogContent(
                data = state.data,
                isLoading = false,
                errorMessageResId = null,
                onNameChange = updateName,
                onSyncRequestedChange = updateSyncRequested,
                onSubmit = submit,
                onDismiss = dismiss,
            )
        }

        CreatePlaylistScreenState.Empty -> {
            CreatePlaylistDialogContent(
                data =
                    CreatePlaylistUiData(
                        name = "",
                        allowSyncing = allowSyncing,
                        isSignedIn = false,
                        isSyncEnabled = false,
                        syncRequested = false,
                        isSubmitting = false,
                    ),
                isLoading = false,
                errorMessageResId = null,
                onNameChange = updateName,
                onSyncRequestedChange = updateSyncRequested,
                onSubmit = submit,
                onDismiss = dismiss,
            )
        }

        is CreatePlaylistScreenState.Error -> {
            CreatePlaylistDialogContent(
                data = state.data,
                isLoading = false,
                errorMessageResId = state.messageResId,
                onNameChange = updateName,
                onSyncRequestedChange = updateSyncRequested,
                onSubmit = submit,
                onDismiss = dismiss,
            )
        }
    }
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
    val onDone = remember(onSubmit) { { _: String -> onSubmit() } }

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.playlist_add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        placeholder = { Text(text = stringResource(R.string.playlist_name)) },
        textFieldValue = data.name,
        onTextFieldValueChange = onNameChange,
        isInputValid = { value -> value.trim().isNotEmpty() },
        enabled = !isLoading && !data.isSubmitting,
        dismissOnDone = false,
        onDismiss = onDismiss,
        onDone = onDone,
        extraContent = {
            if (data.allowSyncing) {
                SyncPlaylistSection(
                    data = data,
                    enabled = !isLoading && !data.isSubmitting,
                    onSyncRequestedChange = onSyncRequestedChange,
                )
            }
            if (errorMessageResId != null) {
                Text(
                    text = stringResource(errorMessageResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

@Composable
private fun SyncPlaylistSection(
    data: CreatePlaylistUiData,
    enabled: Boolean,
    onSyncRequestedChange: (Boolean) -> Unit,
) {
    val syncDescriptionResId =
        when {
            !data.isSignedIn -> R.string.not_logged_in_youtube
            !data.isSyncEnabled -> R.string.sync_disabled
            else -> R.string.allows_for_sync_witch_youtube
        }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                tint =
                    if (data.syncRequested) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.sync_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(syncDescriptionResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = data.syncRequested,
                onCheckedChange = onSyncRequestedChange,
                enabled = enabled,
            )
        }
    }
}
