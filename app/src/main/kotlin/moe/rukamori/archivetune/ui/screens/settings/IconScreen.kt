/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AppIconUiModel
import moe.rukamori.archivetune.viewmodels.IconScreenState
import moe.rukamori.archivetune.viewmodels.IconScreenUiModel
import moe.rukamori.archivetune.viewmodels.IconViewModel

@Composable
fun IconScreen(
    navController: NavController,
    viewModel: IconViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigateUp: () -> Unit =
        remember(navController) {
            {
                navController.navigateUp()
                Unit
            }
        }
    val navigateHome: () -> Unit =
        remember(navController) {
            {
                navController.backToMain()
            }
        }
    val retry = remember(viewModel) { { viewModel.retry() } }
    val selectIcon =
        remember(viewModel) {
            { iconId: String -> viewModel.selectIcon(iconId) }
        }

    IconScreenContent(
        state = state,
        onNavigateUp = navigateUp,
        onNavigateHome = navigateHome,
        onRetry = retry,
        onSelectIcon = selectIcon,
    )
}

@Composable
private fun IconScreenContent(
    state: IconScreenState,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onRetry: () -> Unit,
    onSelectIcon: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_icon))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        onLongClick = onNavigateHome,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when (state) {
            IconScreenState.Loading -> {
                IconScreenLoading(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .playerAwareInsets(),
                )
            }

            IconScreenState.Empty -> {
                IconScreenMessage(
                    message = stringResource(R.string.app_icon_empty),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .playerAwareInsets(),
                )
            }

            is IconScreenState.Error -> {
                IconScreenMessage(
                    message = stringResource(state.messageResId),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .playerAwareInsets(),
                    action = onRetry,
                )
            }

            is IconScreenState.Success -> {
                AppIconList(
                    model = state.model,
                    contentPadding =
                        PaddingValues(
                            start = SettingsDimensions.ScreenHorizontalPadding,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            end = SettingsDimensions.ScreenHorizontalPadding,
                            bottom = SettingsDimensions.ScreenBottomPadding,
                        ),
                    onSelectIcon = onSelectIcon,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .playerAwareInsets(),
                )
            }
        }
    }
}

@Composable
private fun AppIconList(
    model: IconScreenUiModel,
    contentPadding: PaddingValues,
    onSelectIcon: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.selectableGroup(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(
            count = model.icons.size,
            key = { index -> model.icons[index].id },
            contentType = { AppIconContentType },
        ) { index ->
            val icon = model.icons[index]
            AppIconRow(
                icon = icon,
                index = index,
                count = model.icons.size,
                enabled = model.selectionInProgressId == null,
                isApplying = model.selectionInProgressId == icon.id,
                onClick = onSelectIcon,
            )
        }
    }
}

@Composable
private fun AppIconRow(
    icon: AppIconUiModel,
    index: Int,
    count: Int,
    enabled: Boolean,
    isApplying: Boolean,
    onClick: (String) -> Unit,
) {
    val name =
        icon.nameResId?.let { nameResId ->
            stringResource(nameResId)
        } ?: icon.name.orEmpty()
    val author = icon.author
    val select = remember(icon.id, onClick) { { onClick(icon.id) } }

    SegmentedListItem(
        selected = icon.isSelected,
        onClick = select,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .widthIn(max = IconListMaxWidth)
                .fillMaxWidth(),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = {
            AppIconPreview(icon = icon)
        },
        supportingContent =
            if (author != null) {
                {
                    Text(
                        text = stringResource(R.string.app_icon_by_author, author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        trailingContent = {
            if (isApplying) {
                LoadingIndicator(modifier = Modifier.size(32.dp))
            } else {
                RadioButton(
                    selected = icon.isSelected,
                    onClick = null,
                    enabled = enabled,
                )
            }
        },
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun AppIconPreview(icon: AppIconUiModel) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Image(
            painter = painterResource(icon.previewDrawableResId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp),
        )
    }
}

@Composable
private fun IconScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun IconScreenMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.ScreenHorizontalPadding,
                vertical = 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            FilledTonalButton(
                onClick = action,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun Modifier.playerAwareInsets(): Modifier =
    windowInsetsPadding(
        LocalPlayerAwareWindowInsets.current.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    )

private const val AppIconContentType = "app_icon"
private val IconListMaxWidth = 720.dp
