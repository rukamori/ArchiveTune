/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playlistimport.ImportReplacementSearcher
import moe.rukamori.archivetune.playlistimport.buildImportSongQuery

@Composable
fun SongSourceReplacementDialog(
    currentSong: Song,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Song) -> Unit,
) {
    val replacementSearcher = remember { ImportReplacementSearcher() }
    val database = LocalDatabase.current
    val localLibrary by
        database
            .importSongCandidates()
            .collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val noResultsMessage = stringResource(R.string.import_no_search_results)

    var query by remember(currentSong.id) { mutableStateOf(buildImportSongQuery(currentSong)) }
    var candidates by remember(currentSong.id) { mutableStateOf(emptyList<Song>()) }
    var candidateSource by remember(currentSong.id) { mutableStateOf<ReplacementSource?>(null) }
    var searchingSource by remember(currentSong.id) { mutableStateOf<ReplacementSource?>(null) }
    var selectedSong by remember(currentSong.id) { mutableStateOf<Song?>(null) }
    var searchMessage by remember(currentSong.id) { mutableStateOf<String?>(null) }

    fun searchLocal() {
        val requestedQuery = query
        searchingSource = ReplacementSource.LOCAL
        candidateSource = ReplacementSource.LOCAL
        candidates = emptyList()
        selectedSong = null
        searchMessage = null
        coroutineScope.launch {
            val matches =
                withContext(Dispatchers.Default) {
                    replacementSearcher.searchLocal(
                        query = requestedQuery,
                        localLibrary = localLibrary.filter { candidate -> candidate.song.isLocal },
                    )
                }
            if (query == requestedQuery) {
                candidates = matches
                searchMessage = noResultsMessage.takeIf { matches.isEmpty() }
                searchingSource = null
            }
        }
    }

    fun searchYouTube() {
        val requestedQuery = query
        searchingSource = ReplacementSource.YOUTUBE
        candidateSource = ReplacementSource.YOUTUBE
        candidates = emptyList()
        selectedSong = null
        searchMessage = null
        coroutineScope.launch {
            val matches = replacementSearcher.searchYouTube(requestedQuery)
            if (query == requestedQuery) {
                candidates = matches
                searchMessage = noResultsMessage.takeIf { matches.isEmpty() }
                searchingSource = null
            }
        }
    }

    BackHandler(enabled = !isSaving, onBack = onDismiss)
    Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.change_song_source)) },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !isSaving,
                            ) {
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
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isSaving,
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            Button(
                                onClick = { selectedSong?.let(onConfirm) },
                                enabled =
                                    selectedSong != null &&
                                        selectedSong?.id != currentSong.id &&
                                        !isSaving,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.replace_song))
                            }
                        }
                    }
                },
            ) { contentPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currentSong.artistText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            candidates = emptyList()
                            selectedSong = null
                            searchMessage = null
                        },
                        label = { Text(stringResource(R.string.import_search_query)) },
                        singleLine = true,
                        enabled = searchingSource == null && !isSaving,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = ::searchLocal,
                            enabled = query.isNotBlank() && searchingSource == null && !isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            SearchButtonContent(
                                loading = searchingSource == ReplacementSource.LOCAL,
                                text = stringResource(R.string.import_search_local),
                            )
                        }
                        Button(
                            onClick = ::searchYouTube,
                            enabled = query.isNotBlank() && searchingSource == null && !isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            SearchButtonContent(
                                loading = searchingSource == ReplacementSource.YOUTUBE,
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
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                top = 4.dp,
                                bottom = 16.dp,
                            ),
                    ) {
                        items(
                            items = candidates,
                            key = { candidate -> candidate.id },
                        ) { candidate ->
                            ImportCandidateItem(
                                song = candidate,
                                showArtwork = candidateSource == ReplacementSource.YOUTUBE,
                                selected = selectedSong?.id == candidate.id,
                                onClick = {
                                    if (!isSaving) selectedSong = candidate
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ReplacementSource {
    LOCAL,
    YOUTUBE,
}

private val Song.artistText: String
    get() = artists.joinToString(" · ") { it.name }.ifBlank { "—" }
