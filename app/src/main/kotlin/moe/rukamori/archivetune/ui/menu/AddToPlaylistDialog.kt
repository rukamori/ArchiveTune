/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
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

@StringRes
private fun AddToPlaylistSortOption.labelRes(): Int =
    when (this) {
        AddToPlaylistSortOption.RECENTLY_MODIFIED -> R.string.sort_by_last_updated
        AddToPlaylistSortOption.RECENTLY_CREATED -> R.string.sort_by_create_date
        AddToPlaylistSortOption.MOST_PLAYED -> R.string.sort_by_most_played
    }

@DrawableRes
private fun AddToPlaylistSortOption.iconRes(): Int =
    when (this) {
        AddToPlaylistSortOption.RECENTLY_MODIFIED -> R.drawable.update
        AddToPlaylistSortOption.RECENTLY_CREATED -> R.drawable.calendar_today
        AddToPlaylistSortOption.MOST_PLAYED -> R.drawable.trending_up
    }

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
    val selectedPlaylists =
        remember(selectedPlaylistIds, availablePlaylists) {
            val playlistsById = availablePlaylists.associateBy { it.id }
            selectedPlaylistIds.mapNotNull(playlistsById::get)
        }

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

    fun togglePlaylist(playlistId: String) {
        selectedPlaylistIds =
            if (playlistId in selectedPlaylistIds) {
                selectedPlaylistIds - playlistId
            } else {
                selectedPlaylistIds + playlistId
            }
    }

    fun addToSelectedPlaylists() {
        val selectedPlaylistIdsSnapshot = selectedPlaylistIds
        isAddingToPlaylist = true
        coroutineScope.launch {
            val currentSongIds =
                withContext(Dispatchers.IO) {
                    songIds ?: onGetSong()
                }

            if (currentSongIds.isEmpty()) {
                isAddingToPlaylist = false
                onDismiss()
                return@launch
            }
            songIds = currentSongIds

            val targetPlaylists = availablePlaylists.filter { it.id in selectedPlaylistIdsSnapshot }
            if (targetPlaylists.isEmpty()) {
                isAddingToPlaylist = false
                onDismiss()
                return@launch
            }

            val (withDuplicates, duplicatesMap, successfullyAddedPlaylistIds) =
                withContext(Dispatchers.IO) {
                    val tempDuplicatesMap = mutableMapOf<String, List<String>>()
                    val addedPlaylistIds = mutableSetOf<String>()

                    val (playlistsWithDups, playlistsWithoutDups) =
                        targetPlaylists.partition { playlist ->
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
                targetPlaylists
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
    }

    fun resolveDuplicates(skipDuplicates: Boolean) {
        val currentSongIds = songIds
        val targetPlaylists = playlistsWithDuplicates
        val duplicatesByPlaylist = duplicateSongsMap
        if (currentSongIds != null) {
            coroutineScope.launch(Dispatchers.IO) {
                var totalAdded = 0
                val names = mutableListOf<String>()
                targetPlaylists.forEach { playlist ->
                    val songsToAdd =
                        if (skipDuplicates) {
                            val duplicatesForThisPlaylist = duplicatesByPlaylist[playlist.id].orEmpty()
                            currentSongIds.filter { it !in duplicatesForThisPlaylist }
                        } else {
                            currentSongIds
                        }
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
        }
        showDuplicateDialog = false
        onDismiss()
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
        val listState = rememberLazyListState()
        val motion = MaterialTheme.motionScheme
        val colors = MaterialTheme.colorScheme
        val animationsDisabled = LocalAnimationsDisabled.current

        AddToPlaylistFrame(
            onDismissRequest = onDismiss,
            listState = listState,
            header = {
                AddToPlaylistHeader(
                    title = stringResource(R.string.add_to_playlist),
                    supporting =
                        if (selectedPlaylistIds.isEmpty()) {
                            stringResource(R.string.add_to_playlist_hint)
                        } else {
                            pluralStringResource(
                                R.plurals.n_selected,
                                selectedPlaylistIds.size,
                                selectedPlaylistIds.size,
                            )
                        },
                    icon = R.drawable.playlist_add,
                    badgeCount = selectedPlaylistIds.size,
                    badgeActive = selectedPlaylistIds.isNotEmpty(),
                    searchActive = showSearchField,
                    onSearchActiveChange = { active ->
                        showSearchField = active
                        if (!active) searchQuery = ""
                    },
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            },
            controls = { wide ->
                AddToPlaylistSortOptions(
                    selected = sortOption,
                    onSelect = { sortOption = it },
                    wrap = wide,
                )
                AddToPlaylistActionTile(
                    title = stringResource(R.string.create_playlist),
                    supporting = stringResource(R.string.add_to_playlist_create_supporting),
                    icon = R.drawable.add,
                    badgePolygon = MaterialShapes.Cookie4Sided,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    badgeColor = colors.tertiary,
                    badgeContentColor = colors.onTertiary,
                    onClick = { showCreatePlaylistDialog = true },
                    enabled = !isAddingToPlaylist,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            footer = {
                AddToPlaylistSelectionTray(
                    selectedPlaylists = selectedPlaylists,
                    enabled = !isAddingToPlaylist,
                    onRemove = { togglePlaylist(it.id) },
                    onClear = { selectedPlaylistIds = emptySet() },
                )
                AddToPlaylistActions(
                    selectedCount = selectedPlaylistIds.size,
                    isAdding = isAddingToPlaylist,
                    onCancel = onDismiss,
                    onConfirm = { addToSelectedPlaylists() },
                )
            },
        ) {
            item(key = "add_to_playlist_section", contentType = "section") {
                AddToPlaylistSectionLabel(
                    title = stringResource(R.string.playlists),
                    count = playlists.size,
                )
            }

            if (playlists.isEmpty()) {
                item(key = "add_to_playlist_empty", contentType = "empty") {
                    AddToPlaylistEmptyState(
                        searching = searchQuery.isNotBlank(),
                        modifier =
                            if (animationsDisabled) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec(),
                                )
                            },
                    )
                }
            } else {
                itemsIndexed(
                    items = playlists,
                    key = { _, playlist -> playlist.id },
                    contentType = { _, _ -> "playlist" },
                ) { index, playlist ->
                    val selected = playlist.id in selectedPlaylistIds

                    SegmentedListItem(
                        selected = selected,
                        onClick = { togglePlaylist(playlist.id) },
                        enabled = !isAddingToPlaylist,
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = playlists.size),
                        colors =
                            ListItemDefaults.segmentedColors(
                                containerColor = colors.surfaceContainerLow,
                                selectedContainerColor = colors.secondaryContainer,
                            ),
                        modifier =
                            if (animationsDisabled) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec(),
                                )
                            },
                        leadingContent = {
                            PlaylistDestinationArtwork(playlist = playlist, selected = selected)
                        },
                        supportingContent = {
                            PlaylistDestinationSupporting(playlist = playlist)
                        },
                        trailingContent = {
                            PlaylistSelectionIndicator(selected = selected)
                        },
                    ) {
                        Text(
                            text = playlist.playlist.name,
                            style =
                                if (selected) {
                                    MaterialTheme.typography.titleMediumEmphasized
                                } else {
                                    MaterialTheme.typography.titleMedium
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
        AddToPlaylistDuplicatesDialog(
            playlists = playlistsWithDuplicates,
            duplicateSongsMap = duplicateSongsMap,
            onSkipDuplicates = { resolveDuplicates(skipDuplicates = true) },
            onAddAnyway = { resolveDuplicates(skipDuplicates = false) },
            onDismiss = { showDuplicateDialog = false },
        )
    }
}

@Composable
private fun AddToPlaylistSortOptions(
    selected: AddToPlaylistSortOption,
    onSelect: (AddToPlaylistSortOption) -> Unit,
    wrap: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.add_to_playlist_sort_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        val options: @Composable () -> Unit = {
            AddToPlaylistSortOption.entries.forEach { option ->
                val checked = option == selected
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onSelect(option) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    Icon(
                        painter = painterResource(option.iconRes()),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(option.labelRes()),
                        maxLines = 1,
                    )
                }
            }
        }

        if (wrap) {
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options()
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options()
            }
        }
    }
}

@Composable
private fun AddToPlaylistSelectionTray(
    selectedPlaylists: List<Playlist>,
    enabled: Boolean,
    onRemove: (Playlist) -> Unit,
    onClear: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    AnimatedVisibility(
        visible = selectedPlaylists.isNotEmpty(),
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = selectedPlaylists,
                    key = { it.id },
                    contentType = { "selected_playlist" },
                ) { playlist ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(playlist) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = playlist.playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 144.dp),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.add_to_playlist_remove_selection),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier =
                            if (animationsDisabled) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec(),
                                )
                            },
                    )
                }
            }

            TextButton(
                onClick = onClear,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.clear))
            }
        }
    }
}

@Composable
private fun AddToPlaylistActions(
    selectedCount: Int,
    isAdding: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
            Text(
                text = stringResource(android.R.string.cancel),
                maxLines = 1,
            )
        }

        Button(
            enabled = selectedCount > 0 && !isAdding,
            onClick = onConfirm,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            modifier = Modifier.heightIn(min = buttonHeight),
        ) {
            AnimatedContent(
                targetState = isAdding,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec(), initialScale = 0.7f) + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f) + fadeOut(motion.fastEffectsSpec()))
                },
                contentAlignment = Alignment.Center,
                label = "addToPlaylistConfirm",
            ) { adding ->
                if (adding) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.done),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                        )
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                        Text(
                            text =
                                if (selectedCount > 1) {
                                    stringResource(R.string.add_to_n_playlists, selectedCount)
                                } else {
                                    stringResource(R.string.add)
                                },
                            style = ButtonDefaults.textStyleFor(buttonHeight),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDuplicatesDialog(
    playlists: List<Playlist>,
    duplicateSongsMap: Map<String, List<String>>,
    onSkipDuplicates: () -> Unit,
    onAddAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val badgeShape = MaterialShapes.Cookie9Sided.toShape()
    val totalDuplicates =
        remember(duplicateSongsMap) {
            duplicateSongsMap.values
                .flatten()
                .distinct()
                .size
        }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(badgeShape)
                            .background(colors.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.library_add_check),
                        contentDescription = null,
                        tint = colors.onTertiaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.duplicates),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text =
                            if (totalDuplicates == 1) {
                                stringResource(R.string.duplicates_description_single)
                            } else {
                                stringResource(R.string.duplicates_description_multiple, totalDuplicates)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                if (playlists.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = colors.surfaceContainerLow,
                    ) {
                        Column {
                            playlists.forEachIndexed { index, playlist ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = colors.outlineVariant,
                                    )
                                }
                                val duplicateCount = duplicateSongsMap[playlist.id]?.size ?: 0
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PlaylistDestinationArtwork(playlist = playlist, selected = false)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.playlist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text =
                                                pluralStringResource(
                                                    R.plurals.add_to_playlist_n_duplicates,
                                                    duplicateCount,
                                                    duplicateCount,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSkipDuplicates,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.skip_duplicates))
                    }
                    FilledTonalButton(
                        onClick = onAddAnyway,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_anyway))
                    }
                    TextButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}
