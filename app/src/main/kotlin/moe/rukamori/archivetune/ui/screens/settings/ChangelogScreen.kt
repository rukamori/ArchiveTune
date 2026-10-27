/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ChangelogReleaseCollection
import moe.rukamori.archivetune.viewmodels.ChangelogReleaseUiModel
import moe.rukamori.archivetune.viewmodels.ChangelogScreenState
import moe.rukamori.archivetune.viewmodels.ChangelogViewModel

@Composable
fun ChangelogScreen(
    navController: NavController,
    viewModel: ChangelogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onSelectRelease = remember(viewModel) { viewModel::selectRelease }
    val onLoad = remember(viewModel) { viewModel::load }
    val onNavigateUp: () -> Unit = remember(navController) { { navController.navigateUp() } }
    val onNavigateHome = remember(navController) { { navController.backToMain() } }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    ChangelogScreenContent(
        state = state,
        onSelectRelease = onSelectRelease,
        onRetry = onLoad,
        onNavigateUp = onNavigateUp,
        onNavigateHome = onNavigateHome,
    )
}

@Composable
private fun ChangelogScreenContent(
    state: ChangelogScreenState,
    onSelectRelease: (String) -> Unit,
    onRetry: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val model =
        when (state) {
            is ChangelogScreenState.Success -> state.model
            is ChangelogScreenState.Error -> state.lastKnownModel
            ChangelogScreenState.Loading, ChangelogScreenState.Empty -> null
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.changelog)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp, onLongClick = onNavigateHome) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back_button_desc),
                            )
                        }
                    },
                )
                if (model != null) {
                    ChangelogVersionSelector(
                        releases = model.releases,
                        selectedIndex = model.selectedIndex,
                        onSelectRelease = onSelectRelease,
                        modifier = Modifier.windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal),
                        ),
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                model != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state is ChangelogScreenState.Error) {
                            ChangelogError(
                                messageRes = state.messageRes,
                                onRetry = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val release = model.selectedRelease
                        key(release.tag) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(
                                    start = SettingsDimensions.ScreenHorizontalPadding,
                                    top = 8.dp,
                                    end = SettingsDimensions.ScreenHorizontalPadding,
                                    bottom = SettingsDimensions.ScreenBottomPadding,
                                ),
                            ) {
                                item(key = release.tag, contentType = "release_notes") {
                                    ReleaseCard(release = release)
                                }
                            }
                        }
                    }
                }
                state is ChangelogScreenState.Loading -> CircularWavyProgressIndicator()
                state is ChangelogScreenState.Error -> ChangelogError(state.messageRes, onRetry)
                state is ChangelogScreenState.Empty -> {
                    Text(
                        text = stringResource(R.string.no_releases),
                        modifier = Modifier.padding(SettingsDimensions.ScreenHorizontalPadding),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogVersionSelector(
    releases: ChangelogReleaseCollection,
    selectedIndex: Int,
    onSelectRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val singlePressedShape = MaterialTheme.shapes.medium
    val singleShapes = remember(singlePressedShape) {
        ToggleButtonShapes(CircleShape, singlePressedShape, CircleShape)
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
    ) {
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val minimumButtonWidth = maxOf(96.dp, (maxWidth - gap * (releases.size - 1)) / releases.size)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(
                count = releases.size,
                key = { index -> releases[index].tag },
                contentType = { "release_version" },
            ) { index ->
                val release = releases[index]
                val checked = selectedIndex == index
                ToggleButton(
                    checked = checked,
                    onCheckedChange = remember(onSelectRelease, release.tag) {
                        { _: Boolean -> onSelectRelease(release.tag) }
                    },
                    modifier = Modifier
                        .widthIn(min = minimumButtonWidth)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.RadioButton },
                    shapes = when {
                        releases.size == 1 -> singleShapes
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == releases.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    if (checked) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = release.version, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun ChangelogError(
    @StringRes messageRes: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Composable
private fun ReleaseCard(release: ChangelogReleaseUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            release.publishedDate?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val notes = release.notes
            if (notes != null) {
                MarkdownText(
                    markdown = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = stringResource(R.string.release_notes_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
