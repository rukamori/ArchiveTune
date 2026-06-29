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

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.NewsItem
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.NewsUiState
import moe.rukamori.archivetune.viewmodels.NewsViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton

@Composable
fun NewsScreen(
    navController: NavController,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is NewsUiState.Success) {
            viewModel.markAllRead()
        }
    }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "newsTopBar",
            ) { searching ->
                if (searching) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { viewModel.searchQuery.value = it },
                                onSearch = { isSearchActive = false },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(text = stringResource(R.string.news_search_placeholder))
                                },
                                leadingIcon = {
                                    IconButton(
                                        onClick = {
                                            viewModel.searchQuery.value = ""
                                            isSearchActive = false
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.arrow_back),
                                            contentDescription = stringResource(R.string.back_button_desc),
                                        )
                                    }
                                },
                                trailingIcon =
                                    if (searchQuery.isNotEmpty()) {
                                        {
                                            IconButton(
                                                onClick = { viewModel.searchQuery.value = "" },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.close),
                                                    contentDescription = stringResource(R.string.close),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                    ) {}
                } else {
                    LargeFlexibleTopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.news),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        navigationIcon = {
                            AppIconButton(
                                onClick = navController::navigateUp,
                                onLongClick = navController::backToMain,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back),
                                    contentDescription = stringResource(R.string.back_button_desc),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            IconButton(onClick = { viewModel.fetchNews() }) {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = stringResource(R.string.news_retry),
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            },
            modifier = Modifier.fillMaxSize(),
            label = "newsContent",
        ) { state ->
            when (state) {
                is NewsUiState.Loading -> {
                    NewsLoadingState(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is NewsUiState.Error -> {
                    NewsErrorState(
                        message = state.message,
                        onRetry = viewModel::fetchNews,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is NewsUiState.Empty -> {
                    NewsEmptyState(
                        isSearching = searchQuery.isNotBlank(),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is NewsUiState.Success -> {
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(
                                    LocalPlayerAwareWindowInsets.current.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                ),
                    ) {
                        val horizontalPadding =
                            if (maxWidth > 840.dp) {
                                (maxWidth - 760.dp) / 2
                            } else {
                                16.dp
                            }

                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding =
                                PaddingValues(
                                    top = innerPadding.calculateTopPadding() + 12.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                                    start = horizontalPadding,
                                    end = horizontalPadding,
                                ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item(
                                key = "news_header",
                                contentType = "news_header",
                            ) {
                                NewsListHeader(
                                    itemCount = state.items.size,
                                    isSearching = searchQuery.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            itemsIndexed(
                                items = state.items,
                                key = { _, item -> item.stableKey },
                                contentType = { _, item ->
                                    when {
                                        item.imageUrls.size > 1 -> "news_image_carousel"
                                        item.imageUrls.isNotEmpty() -> "news_image"
                                        else -> "news_text"
                                    }
                                },
                            ) { _, item ->
                                NewsCard(
                                    item = item,
                                    onNavigateToArticle = {
                                        navController.navigate("view_news/${Uri.encode(item.id)}")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsListHeader(
    itemCount: Int,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.newspaper),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (isSearching) {
                                R.string.news_search_results_title
                            } else {
                                R.string.news_tooltip_title
                            },
                        ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.news_article_count, itemCount, itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    onNavigateToArticle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasImages = item.imageUrls.isNotEmpty()
    val hasId = item.id.isNotEmpty()
    var fullImageUrl by remember { mutableStateOf<String?>(null) }

    ElevatedCard(
        onClick = { if (hasId) onNavigateToArticle() },
        enabled = hasId,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = 2.dp,
                pressedElevation = 6.dp,
                focusedElevation = 4.dp,
                hoveredElevation = 4.dp,
            ),
        modifier = modifier,
    ) {
        Column {
            if (hasImages) {
                NewsImageCarousel(
                    imageUrls = item.imageUrls,
                    title = item.title,
                    onImageClick = { url -> fullImageUrl = url },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(if (item.imageUrls.size > 1) 252.dp else 220.dp),
                )
            }

            Column(
                modifier =
                    Modifier.padding(
                        start = 20.dp,
                        top = if (hasImages) 6.dp else 20.dp,
                        end = 20.dp,
                        bottom = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NewsMetaRow(item = item)

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hasId) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(
                            onClick = onNavigateToArticle,
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text(text = stringResource(R.string.more))
                        }
                    }
                }
            }
        }
    }

    if (fullImageUrl != null) {
        FullImageViewerDialog(
            imageUrl = fullImageUrl!!,
            onDismiss = { fullImageUrl = null },
        )
    }
}

@Composable
private fun NewsMetaRow(
    item: NewsItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.important) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = stringResource(R.string.news_important_badge),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            val formattedDate =
                remember(item.timestamp) {
                    if (item.timestamp == 0L) {
                        ""
                    } else {
                        DateTimeFormatter.ofPattern("d MMM yyyy").format(
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(item.timestamp), ZoneId.systemDefault()),
                        )
                    }
                }
            Text(
                text =
                    stringResource(
                        R.string.news_author_on_date,
                        item.author,
                        formattedDate,
                    ),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun NewsImageCarousel(
    imageUrls: List<String>,
    title: String,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.size == 1) {
        Box(
            modifier =
                modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(role = Role.Image) { onImageClick(imageUrls.first()) },
        ) {
            NewsAsyncImage(
                imageUrl = imageUrls.first(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
            NewsImageScrim()
        }
        return
    }

    val carouselState = rememberCarouselState { imageUrls.size }
    val currentImageIndex by remember(carouselState, imageUrls.size) {
        derivedStateOf {
            carouselState.currentItem.coerceIn(0, imageUrls.lastIndex)
        }
    }

    Box(modifier = modifier) {
        HorizontalCenteredHeroCarousel(
            state = carouselState,
            maxItemWidth = 336.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clickable(role = Role.Image) { onImageClick(imageUrls[index]) },
            ) {
                NewsAsyncImage(
                    imageUrl = imageUrls[index],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                NewsImageScrim()
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewsCarouselIndicator(
                imageCount = imageUrls.size,
                currentIndex = currentImageIndex,
            )
        }
    }
}

@Composable
private fun FullImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        val context = LocalContext.current
        val model =
            remember(context, imageUrl) {
                ImageRequest
                    .Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build()
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NewsAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val model =
        remember(context, imageUrl) {
            ImageRequest
                .Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build()
        }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun NewsImageScrim(modifier: Modifier = Modifier) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val brush =
        remember(surfaceColor) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        surfaceColor.copy(alpha = 0.78f),
                    ),
                startY = 80f,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(brush),
    )
}

@Composable
private fun NewsCarouselIndicator(
    imageCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(imageCount) { index ->
                val selected = index == currentIndex
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 18.dp else 7.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "newsImageDotWidth",
                )

                Surface(
                    shape = CircleShape,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    modifier =
                        Modifier
                            .width(dotWidth)
                            .height(7.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun NewsLoadingState(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
                Text(
                    text = stringResource(R.string.news_loading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NewsEmptyState(
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    NewsStatePanel(
        icon = R.drawable.newspaper,
        title =
            stringResource(
                if (isSearching) R.string.news_no_results_title else R.string.news_empty_title,
            ),
        description =
            stringResource(
                if (isSearching) R.string.news_no_results_desc else R.string.news_empty_desc,
            ),
        iconColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun NewsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NewsStatePanel(
        icon = R.drawable.info,
        title = stringResource(R.string.news_error_title),
        description = stringResource(R.string.news_error_desc),
        iconColor = MaterialTheme.colorScheme.error,
        modifier = modifier,
        supportingText = message.takeIf { it.isNotBlank() },
        action = {
            ElevatedButton(
                onClick = onRetry,
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text(text = stringResource(R.string.news_retry))
            }
        },
    )
}

@Composable
private fun NewsStatePanel(
    icon: Int,
    title: String,
    description: String,
    iconColor: Color,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.14f),
                    contentColor = iconColor,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }

                if (action != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    action()
                }
            }
        }
    }
}
