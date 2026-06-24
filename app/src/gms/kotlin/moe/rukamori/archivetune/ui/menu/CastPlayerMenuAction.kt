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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
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
            onRouteClick = remember(routePickerViewModel, viewModel) {
                { routeId: String ->
                    routePickerViewModel.selectRoute(routeId)
                    viewModel.hideRoutePicker()
                }
            },
            onDisconnect = remember(viewModel) {
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
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CastRoutePickerHeader(onDismissRequest = onDismissRequest)

            castState.device?.takeIf { castState.isConnected }?.let { device ->
                CastConnectedDeviceStatus(
                    deviceName = device.name,
                    onDisconnect = onDisconnect,
                )
            }

            CastRoutePickerContent(
                screenState = screenState,
                onRouteClick = onRouteClick,
            )
        }
    }
}

@Composable
private fun CastRoutePickerHeader(onDismissRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(R.string.cast_devices),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDismissRequest) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close_dialog),
            )
        }
    }
}

@Composable
private fun CastConnectedDeviceStatus(
    deviceName: String,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.cast_connected_to, deviceName),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onDisconnect) {
            Text(text = stringResource(R.string.cast_disconnect))
        }
    }
}

@Composable
private fun CastRoutePickerContent(
    screenState: CastRoutePickerScreenState,
    onRouteClick: (String) -> Unit,
) {
    when (screenState) {
        CastRoutePickerScreenState.Loading -> CastRoutePickerLoading()
        CastRoutePickerScreenState.Empty -> CastRoutePickerEmpty()
        is CastRoutePickerScreenState.Error ->
            Text(
                text = stringResource(screenState.messageResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

        is CastRoutePickerScreenState.Success ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(
                    items = screenState.routes,
                    key = { _, route -> route.id },
                    contentType = { _, _ -> "cast_route_device" },
                ) { index, route ->
                    val routeClick = remember(route.id, onRouteClick) { { onRouteClick(route.id) } }
                    CastRouteRow(
                        route = route,
                        index = index,
                        count = screenState.routes.size,
                        onClick = routeClick,
                    )
                }
            }
    }
}

@Composable
private fun CastRoutePickerLoading() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.cast_searching_devices),
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CastRoutePickerEmpty() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
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
        )
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        leadingContent = {
            Icon(
                painter =
                    painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                tint =
                    if (route.selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.size(26.dp),
            )
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
