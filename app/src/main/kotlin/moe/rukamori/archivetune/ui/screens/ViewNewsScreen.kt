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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ViewNewsUiState
import moe.rukamori.archivetune.viewmodels.ViewNewsViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton

@Composable
fun ViewNewsScreen(
    navController: NavController,
    viewModel: ViewNewsViewModel = hiltViewModel(),
) {
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    val newsItem = viewModel.newsItem
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = newsItem?.title ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMedium,
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
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = contentState,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            },
            modifier = Modifier.fillMaxSize(),
            label = "viewNewsContent",
        ) { state ->
            when (state) {
                is ViewNewsUiState.Loading -> {
                    ViewNewsLoadingState(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is ViewNewsUiState.Error -> {
                    ViewNewsErrorState(
                        message = state.message,
                        onRetry = viewModel::loadContent,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is ViewNewsUiState.Success -> {
                    ViewNewsArticleContent(
                        newsItem = newsItem,
                        content = state.content,
                        innerPadding = innerPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewNewsArticleContent(
    newsItem: NewsItem?,
    content: String,
    innerPadding: PaddingValues,
) {
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
        val horizontalPadding = if (maxWidth > 840.dp) (maxWidth - 760.dp) / 2 else 24.dp
        val imageUrls = newsItem?.imageUrls.orEmpty()
        var fullImageUrl by remember { mutableStateOf<String?>(null) }

        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 48.dp,
                ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (newsItem != null) {
                item(key = "article_meta", contentType = "meta") {
                    ViewNewsMetaRow(
                        item = newsItem,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPadding),
                    )
                }
            }

            if (imageUrls.isNotEmpty()) {
                item(key = "article_carousel", contentType = "carousel") {
                    ViewNewsCarousel(
                        imageUrls = imageUrls,
                        onImageClick = { url -> fullImageUrl = url },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                    )
                }
            }

            item(key = "article_content", contentType = "markdown") {
                MarkdownText(
                    markdown = content,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding),
                )
            }
        }

        if (fullImageUrl != null) {
            ViewNewsFullImageDialog(
                imageUrl = fullImageUrl!!,
                onDismiss = { fullImageUrl = null },
            )
        }
    }
}

@Composable
private fun ViewNewsCarousel(
    imageUrls: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.size == 1) {
        val context = LocalContext.current
        val model =
            remember(context, imageUrls.first()) {
                ImageRequest
                    .Builder(context)
                    .data(imageUrls.first())
                    .crossfade(true)
                    .build()
            }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                modifier
                    .padding(horizontal = 24.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .focusable()
                    .clickable(role = Role.Image) { onImageClick(imageUrls.first()) },
        )
        return
    }

    val carouselState = rememberCarouselState { imageUrls.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 320.dp,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier,
    ) { index ->
        val context = LocalContext.current
        val imageUrl = imageUrls[index]
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
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .focusable()
                    .clickable(role = Role.Image) { onImageClick(imageUrl) },
        )
    }
}

@Composable
private fun ViewNewsMetaRow(
    item: NewsItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.important) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(R.string.news_important_badge),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                border = null,
                shape = MaterialTheme.shapes.large,
            )
        }

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

        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = stringResource(R.string.news_author_on_date, item.author, formattedDate),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            border = null,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun ViewNewsFullImageDialog(
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
                decorFitsSystemWindows = false,
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
                    .background(Color.Black.copy(alpha = 0.96f))
                    .focusable()
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
private fun ViewNewsLoadingState(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                MaterialTheme.colorScheme.surfaceContainerHigh.let {
                    androidx.compose.material3.CardDefaults
                        .elevatedCardColors(containerColor = it)
                },
            elevation =
                androidx.compose.material3.CardDefaults
                    .elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = stringResource(R.string.news_loading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ViewNewsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(24.dp),
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                MaterialTheme.colorScheme.surfaceContainerHigh.let {
                    androidx.compose.material3.CardDefaults
                        .elevatedCardColors(containerColor = it)
                },
            elevation =
                androidx.compose.material3.CardDefaults
                    .elevatedCardElevation(defaultElevation = 6.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.info),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.news_error_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.news_error_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ElevatedButton(
                    onClick = onRetry,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.news_retry),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
