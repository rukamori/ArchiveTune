/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastScreenState
import moe.rukamori.archivetune.cast.CastUiState
import moe.rukamori.archivetune.cast.CastViewModel
import moe.rukamori.archivetune.ui.component.NewAction

@Composable
fun rememberCastPlayerMenuAction(): NewAction? {
    val viewModel: CastViewModel = viewModel()
    val routePickerViewModel: CastRoutePickerViewModel = viewModel()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isRoutePickerVisible by viewModel.isRoutePickerVisible.collectAsStateWithLifecycle()
    val routePickerState by routePickerViewModel.screenState.collectAsStateWithLifecycle()
    val castState = (screenState as? CastScreenState.Success)?.uiState ?: return null
    if (!castState.isAvailable) return null

    if (isRoutePickerVisible) {
        CastRoutePickerBottomSheet(
            castState = castState,
            screenState = routePickerState,
            onDismissRequest = viewModel::hideRoutePicker,
            onStartDiscovery = routePickerViewModel::startDiscovery,
            onStopDiscovery = routePickerViewModel::stopDiscovery,
            onRouteClick =
                remember(routePickerViewModel, viewModel) {
                    { routeId: String ->
                        if (routePickerViewModel.selectRoute(routeId)) {
                            viewModel.hideRoutePicker()
                        }
                    }
                },
            onDisconnect =
                remember(viewModel) {
                    {
                        viewModel.disconnect()
                        viewModel.hideRoutePicker()
                    }
                },
        )
    }

    val text = stringResource(R.string.cast)
    return NewAction(
        icon = {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        text = text,
        onClick = viewModel::showRoutePicker,
    )
}

@Composable
private fun CastRoutePickerBottomSheet(
    castState: CastUiState,
    screenState: CastRoutePickerScreenState,
    onDismissRequest: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onRouteClick: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(onStartDiscovery, onStopDiscovery) {
        onStartDiscovery()
        onDispose(onStopDiscovery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CastRoutePickerHeader(onDismissRequest = onDismissRequest)

                CastRoutePickerStatus(
                    castState = castState,
                    screenState = screenState,
                    onDisconnect = onDisconnect,
                )

                CastRoutePickerContent(
                    screenState = screenState,
                    onRouteClick = onRouteClick,
                )
            }
        }
    }
}

@Composable
private fun CastRoutePickerHeader(onDismissRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(12.dp)
                        .size(28.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.cast_devices),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.cast_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(onClick = onDismissRequest) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close_dialog),
            )
        }
    }
}

@Composable
private fun CastRoutePickerStatus(
    castState: CastUiState,
    screenState: CastRoutePickerScreenState,
    onDisconnect: () -> Unit,
) {
    val connectedDeviceName = castState.device?.name

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                tint =
                    if (castState.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text =
                        if (castState.isConnected && connectedDeviceName != null) {
                            stringResource(R.string.cast_connected_to, connectedDeviceName)
                        } else {
                            screenState.statusText()
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (castState.isConnected) {
                TextButton(onClick = onDisconnect) {
                    Text(text = stringResource(R.string.cast_disconnect))
                }
            }
        }
    }
}

@Composable
private fun CastRoutePickerContent(
    screenState: CastRoutePickerScreenState,
    onRouteClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.cast_available_devices),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        when (screenState) {
            CastRoutePickerScreenState.Loading -> CastRoutePickerLoading()
            CastRoutePickerScreenState.Empty -> CastRoutePickerEmpty()
            is CastRoutePickerScreenState.Error -> CastRoutePickerError(messageResId = screenState.messageResId)
            is CastRoutePickerScreenState.Success -> CastRoutePickerRouteList(screenState.routes, onRouteClick)
        }
    }
}

@Composable
private fun CastRoutePickerRouteList(
    routes: List<CastRouteUiModel>,
    onRouteClick: (String) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = routes,
            key = { _, route -> route.id },
            contentType = { _, _ -> "cast_route_device" },
        ) { index, route ->
            val routeClick = remember(route.id, onRouteClick) { { onRouteClick(route.id) } }
            CastRouteRow(
                route = route,
                index = index,
                count = routes.size,
                onClick = routeClick,
            )
        }
    }
}

@Composable
private fun CastRoutePickerError(messageResId: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(messageResId),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CastRoutePickerLoading() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 156.dp)
                    .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadingIndicator(modifier = Modifier.size(44.dp))
            Text(
                text = stringResource(R.string.cast_searching_devices),
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.cast_no_devices_desc),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CastRoutePickerEmpty() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 156.dp)
                    .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp),
            )
            Text(
                text = stringResource(R.string.cast_no_devices),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.cast_no_devices_desc),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CastRouteRow(
    route: CastRouteUiModel,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = route.selected,
        onClick = onClick,
        enabled = route.enabled && !route.connecting,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    if (route.selected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                contentColor =
                    if (route.selected) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Icon(
                    painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(22.dp),
                )
            }
        },
        trailingContent = {
            if (route.connecting) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (route.selected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text = route.supportingText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = route.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CastRouteUiModel.supportingText(): String =
    when {
        selected -> stringResource(R.string.together_connected)
        connecting -> stringResource(R.string.connecting)
        description != null -> description
        else -> stringResource(R.string.cast_available_device)
    }

@Composable
private fun CastRoutePickerScreenState.statusText(): String =
    when (this) {
        CastRoutePickerScreenState.Loading -> stringResource(R.string.cast_searching_devices)
        CastRoutePickerScreenState.Empty -> stringResource(R.string.cast_no_devices)
        is CastRoutePickerScreenState.Error -> stringResource(messageResId)
        is CastRoutePickerScreenState.Success -> stringResource(R.string.cast_available_device_count, routes.size)
    }
