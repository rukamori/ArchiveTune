package moe.rukamori.archivetune.ui.player

import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.ModulePlaybackSourceViewModel
import moe.rukamori.archivetune.viewmodels.ModuleSourcePillState
import moe.rukamori.archivetune.viewmodels.ModuleSourcePillUiModel
import moe.rukamori.archivetune.viewmodels.PlaybackSourceKind

internal val LocalModuleSourcePill = staticCompositionLocalOf<ModuleSourcePillUiModel?> { null }
private val LocalOpenModuleSourceSettings = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun ModuleSourcePillProvider(
    navController: NavController,
    onNavigate: () -> Unit,
    viewModel: ModulePlaybackSourceViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val connection = LocalPlayerConnection.current
    DisposableEffect(connection, viewModel) {
        connection?.let(viewModel::bind)
        onDispose { connection?.let(viewModel::unbind) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val model = when (val current = state) {
        is ModuleSourcePillState.Success -> current.model
        ModuleSourcePillState.Loading -> ModuleSourcePillUiModel(null, null, loading = true)
        else -> null
    }
    val navigate = remember(model, navController, onNavigate) {
        {
            val route = model?.moduleId?.let { "settings/modules?moduleId=${Uri.encode(it)}" }
                ?: "settings/player"
            navController.navigate(route) { launchSingleTop = true }
            onNavigate()
        }
    }
    CompositionLocalProvider(LocalModuleSourcePill provides model, LocalOpenModuleSourceSettings provides navigate, content = content)
}

@Composable
internal fun ModuleSourcePill(
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    val model = LocalModuleSourcePill.current ?: return
    val openSettings = LocalOpenModuleSourceSettings.current
    val label = if (model.loading) stringResource(R.string.loading) else when (model.kind) {
        PlaybackSourceKind.LOCAL -> stringResource(R.string.playback_source_local)
        PlaybackSourceKind.PODCAST -> stringResource(R.string.podcast)
        PlaybackSourceKind.YOUTUBE -> stringResource(R.string.module_source_youtube)
        PlaybackSourceKind.MODULE -> model.moduleName.orEmpty()
    }
    val actionLabel = stringResource(R.string.module_source_open_settings,
        model.moduleName ?: stringResource(R.string.player))
    Box(
        modifier = modifier.widthIn(max = 196.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {},
                onLongClickLabel = actionLabel,
                onLongClick = openSettings,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = foreground.copy(alpha = 0.10f), contentColor = foreground) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Icon(
                    painterResource(when {
                        model.loading -> R.drawable.graphic_eq
                        model.kind == PlaybackSourceKind.LOCAL -> R.drawable.music_note
                        model.kind == PlaybackSourceKind.PODCAST -> R.drawable.mic
                        model.kind == PlaybackSourceKind.MODULE -> R.drawable.extension
                        else -> R.drawable.youtube_music
                    }),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}
