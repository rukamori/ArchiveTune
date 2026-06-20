/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.PlaylistListItem
import moe.rukamori.archivetune.utils.rememberPreference
import java.time.LocalDateTime
import java.util.Locale

private fun preferredAddTargetPlaylist(
    current: Playlist,
    candidate: Playlist,
): Playlist {
    val bySongCount = candidate.songCount.compareTo(current.songCount)
    if (bySongCount != 0) {
        return if (bySongCount > 0) candidate else current
    }

    val byLastUpdated = compareNullableDates(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
    if (byLastUpdated != 0) {
        return if (byLastUpdated > 0) candidate else current
    }

    val byCreatedAt = compareNullableDates(candidate.playlist.createdAt, current.playlist.createdAt)
    if (byCreatedAt != 0) {
        return if (byCreatedAt > 0) candidate else current
    }

    if (candidate.playlist.isEditable != current.playlist.isEditable) {
        return if (candidate.playlist.isEditable) candidate else current
    }

    return if (candidate.id < current.id) candidate else current
}

internal fun playlistsForAddToPlaylist(playlists: List<Playlist>): List<Playlist> =
    playlists
        .asSequence()
        .filter { it.playlist.isEditable || it.playlist.browseId != null }
        .groupBy { it.playlist.browseId ?: it.id }
        .values
        .map { candidates ->
            candidates.reduce(::preferredAddTargetPlaylist)
        }.toList()

internal enum class AddToPlaylistSortOption {
    RECENTLY_MODIFIED,
    RECENTLY_CREATED,
    MOST_PLAYED,
}

internal fun visiblePlaylistsForAddToPlaylist(
    playlists: List<Playlist>,
    sortOption: AddToPlaylistSortOption,
    query: String,
    playlistPlayCounts: Map<String, Long> = emptyMap(),
): List<Playlist> {
    val normalizedQuery = query.trim()
    val filteredPlaylists =
        playlistsForAddToPlaylist(playlists).filter { playlist ->
            normalizedQuery.isBlank() ||
                playlist.playlist.name.contains(normalizedQuery, ignoreCase = true)
        }

    return filteredPlaylists.sortedWith { first, second ->
        when (sortOption) {
            AddToPlaylistSortOption.RECENTLY_MODIFIED -> {
                compareNullableDates(
                    second.playlist.lastUpdateTime ?: second.playlist.createdAt,
                    first.playlist.lastUpdateTime ?: first.playlist.createdAt,
                )
            }

            AddToPlaylistSortOption.RECENTLY_CREATED -> {
                compareNullableDates(second.playlist.createdAt, first.playlist.createdAt)
            }

            AddToPlaylistSortOption.MOST_PLAYED -> {
                compareValues(
                    playlistPlayCounts[second.id] ?: 0L,
                    playlistPlayCounts[first.id] ?: 0L,
                ).takeIf { it != 0 }
                    ?: compareNullableDates(
                        second.playlist.lastUpdateTime ?: second.playlist.createdAt,
                        first.playlist.lastUpdateTime ?: first.playlist.createdAt,
                    )
            }
        }.takeIf { it != 0 }
            ?: compareValues(
                first.playlist.name.lowercase(Locale.getDefault()),
                second.playlist.name.lowercase(Locale.getDefault()),
            )
    }
}

private fun compareNullableDates(
    first: LocalDateTime?,
    second: LocalDateTime?,
): Int {
    if (first == second) return 0
    if (first == null) return -1
    if (second == null) return 1
    return first.compareTo(second)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    onGetSong: suspend () -> List<String>,
    onDismiss: () -> Unit,
    onAddComplete: ((songCount: Int, playlistNames: List<String>) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val allPlaylists by database.playlistsByCreateDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistPlayCounts by database.playlistPlayCounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { hasYouTubeLoginCookie(innerTubeCookie) }
    var sortOption by rememberSaveable { mutableStateOf(AddToPlaylistSortOption.RECENTLY_CREATED) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchField by rememberSaveable { mutableStateOf(false) }
    val availablePlaylists =
        remember(allPlaylists) {
            playlistsForAddToPlaylist(allPlaylists)
        }
    val playlists =
        remember(availablePlaylists, sortOption, searchQuery, playlistPlayCounts) {
            visiblePlaylistsForAddToPlaylist(
                playlists = availablePlaylists,
                sortOption = sortOption,
                query = searchQuery,
                playlistPlayCounts = playlistPlayCounts.associate { it.playlistId to it.playCount },
            )
        }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var playlistsWithDuplicates by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var duplicateSongsMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var selectedPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAddingToPlaylist by remember { mutableStateOf(false) }

    suspend fun addSongsToPlaylistSafely(
        playlist: Playlist,
        requestedSongIds: List<String>,
    ): Int {
        if (requestedSongIds.isEmpty()) return 0

        val browseId = playlist.playlist.browseId
        if (isLoggedIn && browseId != null) {
            val acceptedSongEntries = mutableListOf<Pair<String, String?>>()
            requestedSongIds.forEach { songId ->
                var remoteAdded = false
                var addedSetVideoId: String? = null
                for (attempt in 0 until 3) {
                    val result = YouTube.addToPlaylist(browseId, songId)
                    if (result.isSuccess) {
                        remoteAdded = true
                        addedSetVideoId = result.getOrNull()
                        break
                    }
                    if (attempt < 2) delay(250)
                }
                if (remoteAdded) {
                    acceptedSongEntries += songId to addedSetVideoId
                }
            }
            if (acceptedSongEntries.isNotEmpty()) {
                database.addSongEntriesToPlaylist(playlist, acceptedSongEntries)
            }
            return acceptedSongEntries.size
        }

        database.addSongToPlaylist(playlist, requestedSongIds)
        return requestedSongIds.size
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            songIds = null
            selectedPlaylistIds = emptySet()
            isAddingToPlaylist = false
            showDuplicateDialog = false
            playlistsWithDuplicates = emptyList()
            duplicateSongsMap = emptyMap()
            searchQuery = ""
            showSearchField = false
            sortOption = AddToPlaylistSortOption.RECENTLY_CREATED
        }
    }

    LaunchedEffect(availablePlaylists) {
        if (selectedPlaylistIds.isEmpty()) return@LaunchedEffect

        val availableIds = availablePlaylists.mapTo(linkedSetOf()) { it.id }
        val nextSelection = selectedPlaylistIds.filterTo(linkedSetOf()) { it in availableIds }
        if (nextSelection != selectedPlaylistIds) {
            selectedPlaylistIds = nextSelection
        }
    }

    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .imePadding()
                        .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .heightIn(max = maxHeight),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.playlist_add),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.add_to_playlist),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (selectedPlaylistIds.isNotEmpty()) {
                                        Text(
                                            text =
                                                pluralStringResource(
                                                    R.plurals.n_selected,
                                                    selectedPlaylistIds.size,
                                                    selectedPlaylistIds.size,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (showSearchField) {
                                            showSearchField = false
                                            searchQuery = ""
                                        } else {
                                            showSearchField = true
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter =
                                            painterResource(
                                                if (showSearchField) R.drawable.close else R.drawable.search,
                                            ),
                                        contentDescription =
                                            stringResource(
                                                if (showSearchField) R.string.close else R.string.search,
                                            ),
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showSearchField) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.search)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.search),
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.close),
                                                    contentDescription = stringResource(R.string.close),
                                                )
                                            }
                                        }
                                    },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            imeAction = ImeAction.Search,
                                        ),
                                    colors =
                                        TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                        ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, end = 8.dp),
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            item(contentType = "sort") {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AddToPlaylistSortChip(
                                        label = stringResource(R.string.sort_by_last_updated),
                                        selected = sortOption == AddToPlaylistSortOption.RECENTLY_MODIFIED,
                                        onClick = { sortOption = AddToPlaylistSortOption.RECENTLY_MODIFIED },
                                    )
                                    AddToPlaylistSortChip(
                                        label = stringResource(R.string.sort_by_create_date),
                                        selected = sortOption == AddToPlaylistSortOption.RECENTLY_CREATED,
                                        onClick = { sortOption = AddToPlaylistSortOption.RECENTLY_CREATED },
                                    )
                                    AddToPlaylistSortChip(
                                        label = stringResource(R.string.sort_by_most_played),
                                        selected = sortOption == AddToPlaylistSortOption.MOST_PLAYED,
                                        onClick = { sortOption = AddToPlaylistSortOption.MOST_PLAYED },
                                    )
                                }
                            }

                            item(contentType = "create") {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = stringResource(R.string.create_playlist),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier =
                                                Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.add),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { showCreatePlaylistDialog = true }
                                            .padding(horizontal = 8.dp),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }

                            item(contentType = "playlistDivider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }

                            if (playlists.isNotEmpty()) {
                                items(
                                    items = playlists,
                                    key = { it.id },
                                    contentType = { "playlist" },
                                ) { playlist ->
                                    val isSelected = selectedPlaylistIds.contains(playlist.id)
                                    val rowBackground by animateColorAsState(
                                        targetValue =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            } else {
                                                Color.Transparent
                                            },
                                        animationSpec = tween(durationMillis = 180),
                                        label = "rowBackground",
                                    )

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(rowBackground)
                                                .clickable(enabled = !isAddingToPlaylist) {
                                                    selectedPlaylistIds =
                                                        if (isSelected) {
                                                            selectedPlaylistIds - playlist.id
                                                        } else {
                                                            selectedPlaylistIds + playlist.id
                                                        }
                                                },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PlaylistListItem(
                                            playlist = playlist,
                                            modifier = Modifier.weight(1f),
                                        )

                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            modifier = Modifier.padding(end = 12.dp),
                                        )
                                    }
                                }
                            } else {
                                item(contentType = "empty") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 96.dp)
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                    ) {
                                        Text(
                                            text =
                                                if (searchQuery.isBlank()) {
                                                    stringResource(R.string.no_playlists_yet)
                                                } else {
                                                    stringResource(R.string.no_matching_playlists)
                                                },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        FlowRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(android.R.string.cancel))
                            }

                            Button(
                                enabled = selectedPlaylistIds.isNotEmpty() && !isAddingToPlaylist,
                                onClick = {
                                    val selectedPlaylistIdsSnapshot = selectedPlaylistIds
                                    isAddingToPlaylist = true
                                    coroutineScope.launch {
                                        val currentSongIds =
                                            withContext(Dispatchers.IO) {
                                                songIds ?: onGetSong()
                                            }

                                        if (currentSongIds.isNullOrEmpty()) {
                                            isAddingToPlaylist = false
                                            onDismiss()
                                            return@launch
                                        }
                                        songIds = currentSongIds

                                        val selectedPlaylists = availablePlaylists.filter { it.id in selectedPlaylistIdsSnapshot }
                                        if (selectedPlaylists.isEmpty()) {
                                            isAddingToPlaylist = false
                                            onDismiss()
                                            return@launch
                                        }

                                        val (withDuplicates, duplicatesMap, successfullyAddedPlaylistIds) =
                                            withContext(Dispatchers.IO) {
                                                val tempDuplicatesMap = mutableMapOf<String, List<String>>()
                                                val addedPlaylistIds = mutableSetOf<String>()

                                                val (playlistsWithDups, playlistsWithoutDups) =
                                                    selectedPlaylists.partition { playlist ->
                                                        val dups = database.playlistDuplicates(playlist.id, currentSongIds)
                                                        if (dups.isNotEmpty()) {
                                                            tempDuplicatesMap[playlist.id] = dups
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    }

                                                playlistsWithoutDups.forEach { playlist ->
                                                    val addedCount = addSongsToPlaylistSafely(playlist, currentSongIds)
                                                    if (addedCount > 0) {
                                                        addedPlaylistIds += playlist.id
                                                    }
                                                }
                                                Triple(playlistsWithDups, tempDuplicatesMap, addedPlaylistIds)
                                            }

                                        isAddingToPlaylist = false

                                        val addedPlaylistNames =
                                            selectedPlaylists
                                                .filter { successfullyAddedPlaylistIds.contains(it.id) }
                                                .map { it.playlist.name }
                                        if (addedPlaylistNames.isNotEmpty()) {
                                            onAddComplete?.invoke(currentSongIds.size, addedPlaylistNames)
                                        }

                                        if (withDuplicates.isNotEmpty()) {
                                            playlistsWithDuplicates = withDuplicates
                                            duplicateSongsMap = duplicatesMap
                                            showDuplicateDialog = true
                                        }
                                        onDismiss()
                                    }
                                },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                if (isAddingToPlaylist) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.done),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(
                                        text =
                                            if (selectedPlaylistIds.size > 1) {
                                                stringResource(R.string.add_to_n_playlists, selectedPlaylistIds.size)
                                            } else {
                                                stringResource(R.string.add)
                                            },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
        )
    }

    if (showDuplicateDialog) {
        val totalDuplicates =
            duplicateSongsMap.values
                .flatten()
                .distinct()
                .size
        DefaultDialog(
            title = { Text(stringResource(R.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            var totalAdded = 0
                            val names = mutableListOf<String>()
                            playlistsWithDuplicates.forEach { playlist ->
                                val duplicatesForThisPlaylist = duplicateSongsMap[playlist.id] ?: emptyList()
                                val songsToAdd = songIds!!.filter { it !in duplicatesForThisPlaylist }
                                val addedCount = addSongsToPlaylistSafely(playlist, songsToAdd)
                                if (addedCount > 0) {
                                    totalAdded += addedCount
                                    names += playlist.playlist.name
                                }
                            }
                            if (totalAdded > 0) {
                                withContext(Dispatchers.Main) {
                                    onAddComplete?.invoke(totalAdded, names)
                                }
                            }
                        }
                        showDuplicateDialog = false
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.skip_duplicates))
                }

                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            var totalAdded = 0
                            val names = mutableListOf<String>()
                            playlistsWithDuplicates.forEach { playlist ->
                                val addedCount = addSongsToPlaylistSafely(playlist, songIds!!)
                                if (addedCount > 0) {
                                    totalAdded += addedCount
                                    names += playlist.playlist.name
                                }
                            }
                            if (totalAdded > 0) {
                                withContext(Dispatchers.Main) {
                                    onAddComplete?.invoke(totalAdded, names)
                                }
                            }
                        }
                        showDuplicateDialog = false
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.add_anyway))
                }

                TextButton(onClick = { showDuplicateDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            onDismiss = { showDuplicateDialog = false },
        ) {
            Text(
                text =
                    if (totalDuplicates == 1) {
                        stringResource(R.string.duplicates_description_single)
                    } else {
                        stringResource(R.string.duplicates_description_multiple, totalDuplicates)
                    },
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}

@Composable
private fun AddToPlaylistSortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 40.dp,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        },
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(16.dp),
        border = null,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    )
}
