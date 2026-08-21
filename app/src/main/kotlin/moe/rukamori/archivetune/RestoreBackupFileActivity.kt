/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.menu.LoadingScreen
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel

@AndroidEntryPoint
class RestoreBackupFileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, R.string.restore_file_not_found, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            ArchiveTuneTheme {
                RestoreBackupFileScreen(
                    uri = uri,
                    onNavigateBack = { finish() },
                )
            }
        }
    }
}

private sealed class BackupScreenState {
    data object Validating : BackupScreenState()

    data class Ready(
        val availableCategories: Set<BackupCategory>,
    ) : BackupScreenState()

    data class Error(
        val message: String,
    ) : BackupScreenState()

    data object Restoring : BackupScreenState()
}

@Composable
private fun RestoreBackupFileScreen(
    uri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var screenState by remember { mutableStateOf<BackupScreenState>(BackupScreenState.Validating) }
    var selectedCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsStateWithLifecycle()

    LaunchedEffect(uri) {
        val result = viewModel.validateBackup(context, uri)
        if (result.isValid) {
            selectedCategories = result.availableCategories
            screenState = BackupScreenState.Ready(availableCategories = result.availableCategories)
            showRestoreDialog = true
        } else {
            screenState = BackupScreenState.Error(result.errorMessage ?: context.getString(R.string.restore_corrupted))
        }
    }

    when (val state = screenState) {
        is BackupScreenState.Validating -> {
            DefaultDialog(
                onDismiss = onNavigateBack,
                title = { Text(stringResource(R.string.restore_in_progress)) },
                buttons = {
                    TextButton(onClick = onNavigateBack, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            ) {
                Text(stringResource(R.string.restore_step_verifying))
            }
        }

        is BackupScreenState.Error -> {
            DefaultDialog(
                onDismiss = onNavigateBack,
                icon = { Icon(painterResource(R.drawable.error), null) },
                title = { Text(stringResource(R.string.restore_failed)) },
                buttons = {
                    TextButton(onClick = onNavigateBack, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is BackupScreenState.Ready -> {
            if (showRestoreDialog) {
                RestoreOptionsDialog(
                    title = stringResource(R.string.restore_options_title),
                    confirmLabel = stringResource(R.string.action_restore),
                    availableCategories = state.availableCategories,
                    selected = selectedCategories,
                    onSelectionChanged = { selectedCategories = it },
                    onConfirm = {
                        showRestoreDialog = false
                        screenState = BackupScreenState.Restoring
                        viewModel.restore(context, uri, selectedCategories)
                    },
                    onDismiss = onNavigateBack,
                )
            }
        }

        is BackupScreenState.Restoring -> {
            LoadingScreen(
                isVisible = true,
                value = backupRestoreProgress?.percent ?: 0,
                title = backupRestoreProgress?.title,
                stepText = backupRestoreProgress?.step,
                indeterminate = backupRestoreProgress?.indeterminate ?: true,
            )
        }
    }
}

@Composable
private fun RestoreOptionsDialog(
    title: String,
    confirmLabel: String,
    availableCategories: Set<BackupCategory>,
    selected: Set<BackupCategory>,
    onSelectionChanged: (Set<BackupCategory>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                enabled = selected.isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        BackupCategory.entries.forEach { category ->
            val isChecked = category in selected
            val isAvailable = category in availableCategories
            val labelRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library
                    BackupCategory.ACCOUNT -> R.string.backup_category_account
                    BackupCategory.SETTINGS -> R.string.backup_category_settings
                }
            val descRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                    BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                    BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                }
            val iconRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.drawable.library_music
                    BackupCategory.ACCOUNT -> R.drawable.account
                    BackupCategory.SETTINGS -> R.drawable.settings
                }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                onClick = {
                    if (!isAvailable) return@Surface
                    onSelectionChanged(if (isChecked) selected - category else selected + category)
                },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.large)
                                .then(
                                    if (isAvailable) {
                                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                    } else {
                                        Modifier
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint =
                                if (isAvailable) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color =
                                if (isAvailable) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (isAvailable) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (!isAvailable) return@Checkbox
                            onSelectionChanged(if (checked) selected + category else selected - category)
                        },
                        enabled = isAvailable,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
