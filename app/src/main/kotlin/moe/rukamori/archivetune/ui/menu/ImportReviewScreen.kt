/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.ImportSource
import moe.rukamori.archivetune.models.ImportedSongResult
import moe.rukamori.archivetune.playlistimport.ImportReplacementSearcher
import moe.rukamori.archivetune.playlistimport.buildImportSongQuery

@Composable
fun ImportReviewScreen(
    results: List<ImportedSongResult>,
    localLibrary: List<Song>,
    onCancel: () -> Unit,
    onConfirm: (List<ImportedSongResult>) -> Unit,
) {
    val reviewItems = remember(results) { results.map(::ImportReviewItemState).toMutableStateList() }
    val replacementSearcher = remember { ImportReplacementSearcher() }
    val coroutineScope = rememberCoroutineScope()
    val noResultsMessage = stringResource(R.string.import_no_search_results)

    var filter by remember { mutableStateOf(ImportReviewFilter.ALL) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<Song>()) }
    var candidateSource by remember { mutableStateOf<ImportReplacementSource?>(null) }
    var searchingSource by remember { mutableStateOf<ImportReplacementSource?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }

    fun closeEditor() {
        editingIndex = null
        query = ""
        candidates = emptyList()
        candidateSource = null
        searchingSource = null
        searchMessage = null
    }

    fun openEditor(index: Int) {
        editingIndex = index
        query = buildImportSongQuery(reviewItems[index].result.originalSong)
        candidates = emptyList()
        candidateSource = null
        searchingSource = null
        searchMessage = null
    }

    fun searchLocal(index: Int) {
        val requestedQuery = query
        searchingSource = ImportReplacementSource.LOCAL
        candidateSource = ImportReplacementSource.LOCAL
        candidates = emptyList()
        searchMessage = null
        coroutineScope.launch {
            val matches =
                withContext(Dispatchers.Default) {
                    replacementSearcher.searchLocal(
                        query = requestedQuery,
                        localLibrary = localLibrary,
                    )
                }
            if (editingIndex == index && query == requestedQuery) {
                candidates = matches
                searchMessage = noResultsMessage.takeIf { matches.isEmpty() }
                searchingSource = null
            }
        }
    }

    fun searchYouTube(index: Int) {
        val requestedQuery = query
        searchingSource = ImportReplacementSource.YOUTUBE
        candidateSource = ImportReplacementSource.YOUTUBE
        candidates = emptyList()
        searchMessage = null
        coroutineScope.launch {
            val matches = replacementSearcher.searchYouTube(requestedQuery)
            if (editingIndex == index && query == requestedQuery) {
                candidates = matches
                searchMessage = noResultsMessage.takeIf { matches.isEmpty() }
                searchingSource = null
            }
        }
    }

    val activeItems = reviewItems.filterNot { it.skipped }
    val localCount = activeItems.count { it.result.source == ImportSource.LOCAL }
    val youtubeCount = activeItems.count { it.result.source == ImportSource.YOUTUBE }
    val unresolvedCount = activeItems.count { !it.result.isResolved }
    val resolvedCount = activeItems.count { it.result.isResolved }
    val skippedCount = reviewItems.count { it.skipped }
    val visibleIndices =
        reviewItems.indices.filter { index ->
            val item = reviewItems[index]
            when (filter) {
                ImportReviewFilter.ALL -> true
                ImportReviewFilter.MATCHED -> !item.skipped && item.result.isResolved
                ImportReviewFilter.UNRESOLVED -> !item.skipped && !item.result.isResolved
            }
        }

    BackHandler(onBack = onCancel)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.import_review_title)) },
                        navigationIcon = {
                            IconButton(onClick = onCancel) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(android.R.string.cancel),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onCancel) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            Button(
                                onClick = {
                                    onConfirm(
                                        reviewItems
                                            .filter { !it.skipped && it.result.isResolved }
                                            .map { it.result },
                                    )
                                },
                                enabled = resolvedCount > 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.import_confirm_save,
                                        resolvedCount,
                                        reviewItems.size,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { contentPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.import_review_summary,
                                localCount,
                                youtubeCount,
                                unresolvedCount,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (skippedCount > 0) {
                        Text(
                            text = stringResource(R.string.import_review_skipped, skippedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ImportReviewFilter.entries.forEach { itemFilter ->
                            FilterChip(
                                selected = filter == itemFilter,
                                onClick = { filter = itemFilter },
                                label = { Text(stringResource(itemFilter.labelRes)) },
                            )
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(visibleIndices, key = { it }) { index ->
                            val item = reviewItems[index]
                            ImportReviewItem(
                                item = item,
                                isEditing = editingIndex == index,
                                query = query,
                                onQueryChange = {
                                    query = it
                                    candidates = emptyList()
                                    searchMessage = null
                                },
                                candidates = candidates,
                                candidateSource = candidateSource,
                                searchingSource = searchingSource,
                                searchMessage = searchMessage,
                                onEdit = { openEditor(index) },
                                onCancelEdit = ::closeEditor,
                                onSearchLocal = { searchLocal(index) },
                                onSearchYouTube = { searchYouTube(index) },
                                onCandidateSelected = { candidate ->
                                    val source =
                                        when (candidateSource) {
                                            ImportReplacementSource.LOCAL -> ImportSource.LOCAL
                                            ImportReplacementSource.YOUTUBE -> ImportSource.YOUTUBE
                                            null -> return@ImportReviewItem
                                        }
                                    reviewItems[index] =
                                        item.copy(
                                            result =
                                                item.result.copy(
                                                    resolvedId = candidate.id,
                                                    resolvedSong = candidate,
                                                    source = source,
                                                ),
                                        )
                                    closeEditor()
                                },
                                onToggleSkipped = {
                                    reviewItems[index] = item.copy(skipped = !item.skipped)
                                    if (!item.skipped && editingIndex == index) closeEditor()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportReviewItem(
    item: ImportReviewItemState,
    isEditing: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    candidates: List<Song>,
    candidateSource: ImportReplacementSource?,
    searchingSource: ImportReplacementSource?,
    searchMessage: String?,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSearchLocal: () -> Unit,
    onSearchYouTube: () -> Unit,
    onCandidateSelected: (Song) -> Unit,
    onToggleSkipped: () -> Unit,
) {
    val result = item.result
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.alpha(if (item.skipped) 0.6f else 1f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(result.source.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = result.source.statusColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (!item.skipped) {
                    TextButton(onClick = onEdit) {
                        Text(stringResource(R.string.import_edit_match))
                    }
                }
                TextButton(onClick = onToggleSkipped) {
                    Text(
                        stringResource(
                            if (item.skipped) R.string.import_include_song else R.string.import_skip_song,
                        ),
                    )
                }
            }
            Text(
                text = result.originalSong.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.originalSong.artistText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            result.resolvedSong?.let { resolvedSong ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        stringResource(
                            R.string.import_matched_to,
                            resolvedSong.title,
                            resolvedSong.artistText,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isEditing && !item.skipped) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.import_search_query)) },
                    singleLine = true,
                    enabled = searchingSource == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onSearchLocal,
                        enabled = query.isNotBlank() && searchingSource == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        SearchButtonContent(
                            loading = searchingSource == ImportReplacementSource.LOCAL,
                            text = stringResource(R.string.import_search_local),
                        )
                    }
                    Button(
                        onClick = onSearchYouTube,
                        enabled = query.isNotBlank() && searchingSource == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        SearchButtonContent(
                            loading = searchingSource == ImportReplacementSource.YOUTUBE,
                            text = stringResource(R.string.import_search_youtube),
                        )
                    }
                }
                searchMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                candidates.forEach { candidate ->
                    ImportCandidateItem(
                        song = candidate,
                        showArtwork = candidateSource == ImportReplacementSource.YOUTUBE,
                        selected =
                            candidate.id == result.resolvedId &&
                                ((candidateSource == ImportReplacementSource.LOCAL && result.source == ImportSource.LOCAL) ||
                                    (candidateSource == ImportReplacementSource.YOUTUBE && result.source == ImportSource.YOUTUBE)),
                        onClick = { onCandidateSelected(candidate) },
                    )
                }
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.import_cancel_edit))
                }
            }
        }
    }
}

@Composable
private fun SearchButtonContent(
    loading: Boolean,
    text: String,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(text)
}

@Composable
private fun ImportCandidateItem(
    song: Song,
    showArtwork: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArtwork) {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!song.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private data class ImportReviewItemState(
    val result: ImportedSongResult,
    val skipped: Boolean = false,
)

private enum class ImportReviewFilter(
    val labelRes: Int,
) {
    ALL(R.string.import_filter_all),
    MATCHED(R.string.import_filter_matched),
    UNRESOLVED(R.string.import_filter_unresolved),
}

private enum class ImportReplacementSource {
    LOCAL,
    YOUTUBE,
}

private val ImportSource.labelRes: Int
    get() =
        when (this) {
            ImportSource.LOCAL -> R.string.import_source_local
            ImportSource.YOUTUBE -> R.string.import_source_youtube
            ImportSource.UNRESOLVED -> R.string.import_source_unresolved
        }

private val ImportSource.statusColor: Color
    @Composable
    get() =
        when (this) {
            ImportSource.LOCAL -> MaterialTheme.colorScheme.primary
            ImportSource.YOUTUBE -> MaterialTheme.colorScheme.tertiary
            ImportSource.UNRESOLVED -> MaterialTheme.colorScheme.error
        }

private val Song.artistText: String
    get() = artists.joinToString(" · ") { it.name }.ifBlank { "—" }
