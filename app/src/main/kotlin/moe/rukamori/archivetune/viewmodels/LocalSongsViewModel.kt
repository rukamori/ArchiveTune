/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LocalSongsSortDescendingKey
import moe.rukamori.archivetune.constants.LocalSongsSortTypeKey
import moe.rukamori.archivetune.db.LocalMusicIdentity
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.localmedia.LocalSongScanConfig
import moe.rukamori.archivetune.localmedia.LocalMusicAccess
import moe.rukamori.archivetune.localmedia.LocalMusicFolder
import moe.rukamori.archivetune.localmedia.LocalSongScanSummary
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalSongsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val scanLocalMusic: ScanLocalMusicUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _scanState = MutableStateFlow<LocalSongsScanState>(LocalSongsScanState.Empty)
    private var scanJob: Job? = null
    private var settingsJob: Job? = null
    private var accessJob: Job? = null
    private var settingsMutationJob: Job? = null
    private var initialScanRequested = false
    private val settingsValues = MutableStateFlow(SettingsValues())
    private val eventChannel = Channel<LocalSongSettingsEvent>(Channel.BUFFERED)
    val settingsEvents = eventChannel.receiveAsFlow()

    val settingsState = combine(settingsValues, _scanState, ::buildSettingsState).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LocalSongSettingsState.Loading,
    )

    var query by mutableStateOf(savedStateHandle.get<String>(QueryKey).orEmpty())
        private set

    private val reloadToken = MutableStateFlow(0)

    private val sort: Flow<LocalLibrarySort> =
        context.dataStore.data
            .map { preferences ->
                LocalLibrarySort(
                    type = preferences[LocalSongsSortTypeKey].toEnum(LocalSongSortType.MODIFIED),
                    descending = preferences[LocalSongsSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()

    val libraryState: StateFlow<LocalLibraryUiState> =
        reloadToken
            .flatMapLatest {
                combine(
                    database.localSongs(),
                    snapshotFlow { query },
                    savedStateHandle.getStateFlow(ViewKey, LocalLibraryView.SONGS.name),
                    savedStateHandle.getStateFlow<String?>(FocusKey, null),
                    sort,
                ) { songs, query, view, focus, sort ->
                    LocalLibraryInputs(songs, query, view.toEnum(LocalLibraryView.SONGS), focus, sort)
                }.mapLatest<LocalLibraryInputs, LocalLibraryUiState>(::buildLibraryState)
                    .flowOn(Dispatchers.Default)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        emit(LocalLibraryUiState.Error(R.string.local_library_load_failed))
                    }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                LocalLibraryUiState.Loading,
            )

    fun onLibraryAction(action: LocalLibraryAction) {
        when (action) {
            is LocalLibraryAction.QueryChanged -> {
                query = action.query
                savedStateHandle[QueryKey] = action.query
            }
            is LocalLibraryAction.SelectView -> savedStateHandle[ViewKey] = action.view.name
            is LocalLibraryAction.OpenAlbum -> savedStateHandle[FocusKey] = AlbumFocusPrefix + action.key
            is LocalLibraryAction.OpenArtist -> savedStateHandle[FocusKey] = ArtistFocusPrefix + action.id
            LocalLibraryAction.CloseFocus -> savedStateHandle[FocusKey] = null
            is LocalLibraryAction.SortTypeChanged -> PreferenceStore.launchEdit(context.dataStore) {
                this[LocalSongsSortTypeKey] = action.type.name
            }
            is LocalLibraryAction.SortDescendingChanged -> PreferenceStore.launchEdit(context.dataStore) {
                this[LocalSongsSortDescendingKey] = action.descending
            }
            LocalLibraryAction.Retry -> reloadToken.update { it + 1 }
        }
    }

    fun onLibraryOpened() {
        refreshSettings()
        if (!initialScanRequested) {
            startScan(automatic = true)
        }
    }

    fun refreshSettings() {
        if (settingsJob?.isActive != true) {
            settingsJob = viewModelScope.launch {
                try {
                    scanLocalMusic.configuration.collect { configuration ->
                        settingsValues.update { it.copy(configuration = configuration, configurationError = null) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportException(error)
                    settingsValues.update { it.copy(configurationError = R.string.local_song_settings_load_failed) }
                }
            }
        }
        accessJob?.cancel()
        accessJob = viewModelScope.launch {
            try {
                val access = scanLocalMusic.readAccess()
                settingsValues.update { it.copy(access = access, accessError = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                settingsValues.update { it.copy(accessError = R.string.local_song_settings_load_failed) }
            }
        }
    }

    fun onSettingsAction(action: LocalSongSettingsAction) {
        when (action) {
            LocalSongSettingsAction.Back -> eventChannel.trySend(LocalSongSettingsEvent.NavigateBack)
            LocalSongSettingsAction.Reload -> refreshSettings()
            LocalSongSettingsAction.Scan -> scanDevice()
            LocalSongSettingsAction.CancelScan -> {
                scanJob?.cancel()
                _scanState.value = LocalSongsScanState.Empty
            }
            LocalSongSettingsAction.OpenAppSettings -> eventChannel.trySend(LocalSongSettingsEvent.OpenAppSettings)
            is LocalSongSettingsAction.PickFolder -> {
                if (!canEdit()) return
                settingsValues.update { it.copy(pickingFolder = true) }
                eventChannel.trySend(LocalSongSettingsEvent.PickFolder(action.included, action.initialUri))
            }
            is LocalSongSettingsAction.FolderSelected -> {
                settingsValues.update { it.copy(pickingFolder = false) }
                action.uri?.let { uri ->
                    if (action.included) includeFolder(uri)
                    else editSettings { scanLocalMusic.excludeFolder(uri) }
                }
            }
            is LocalSongSettingsAction.RemoveFolder -> editSettings {
                scanLocalMusic.removeFolder(action.entry, action.included)
            }
            is LocalSongSettingsAction.DurationChanged -> {
                if (canEdit()) settingsValues.update { it.copy(durationDraft = action.seconds.coerceIn(0, ScanLocalMusicUseCase.MaximumMinimumDurationSeconds)) }
            }
            LocalSongSettingsAction.SaveDuration -> {
                val seconds = settingsValues.value.durationDraft ?: return
                editSettings { scanLocalMusic.setMinimumDuration(seconds) }
            }
            is LocalSongSettingsAction.PermissionResult -> {
                settingsValues.update { it.copy(requestingPermission = false) }
                if (action.granted) scanDevice() else refreshSettings()
            }
            LocalSongSettingsAction.ExternalActionFailed -> settingsValues.update {
                it.copy(pickingFolder = false, requestingPermission = false, errorMessage = R.string.local_song_settings_open_failed)
            }
        }
    }

    fun scanDevice() {
        refreshSettings()
        startScan(automatic = false)
    }

    private fun canEdit(): Boolean =
        scanJob?.isActive != true && settingsMutationJob?.isActive != true &&
            !settingsValues.value.pickingFolder && !settingsValues.value.requestingPermission

    private fun editSettings(update: suspend () -> LocalSongScanConfig) {
        if (!canEdit()) return
        settingsValues.update { it.copy(saving = true, errorMessage = null) }
        settingsMutationJob = viewModelScope.launch {
            try {
                val configuration = update()
                val access = scanLocalMusic.readAccess()
                settingsValues.update {
                    it.copy(configuration = configuration, access = access, durationDraft = null, saving = false,
                        configurationError = null, accessError = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                settingsValues.update {
                    it.copy(saving = false, durationDraft = null, errorMessage = R.string.local_song_settings_save_failed)
                }
            }
        }
    }

    fun includeFolder(uri: Uri) {
        editSettings { scanLocalMusic.includeFolder(uri) }
    }

    private fun startScan(automatic: Boolean) {
        if (!canEdit()) return
        initialScanRequested = true
        _scanState.value = LocalSongsScanState.Loading
        scanJob = viewModelScope.launch {
            try {
                val configuration = scanLocalMusic.currentConfiguration()
                val access = scanLocalMusic.readAccess()
                settingsValues.update {
                    it.copy(configuration = configuration, access = access, errorMessage = null,
                        configurationError = null, accessError = null)
                }
                if (configuration.requiresMediaPermission && !access.hasMediaPermission) {
                    if (!automatic) settingsValues.update { it.copy(requestingPermission = true) }
                    _scanState.value = LocalSongsScanState.Empty
                    if (!automatic) eventChannel.send(LocalSongSettingsEvent.RequestAudioPermission)
                    return@launch
                }
                _scanState.value = LocalSongsScanState.Success(scanLocalMusic(configuration))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                reportException(error)
                _scanState.value = LocalSongsScanState.Error(
                    when {
                        error !is SecurityException -> R.string.local_songs_scan_failed
                        settingsValues.value.configuration?.requiresMediaPermission == false -> R.string.local_song_settings_folder_access_needed
                        else -> R.string.local_songs_permission_body
                    },
                )
            }
        }
    }

    private fun buildSettingsState(values: SettingsValues, scan: LocalSongsScanState): LocalSongSettingsState {
        val configuration = values.configuration
        val access = values.access
        val errorMessage = values.configurationError ?: values.accessError ?: values.errorMessage ?: scan.errorMessageRes
        if (configuration == null || access == null) {
            return errorMessage?.let { LocalSongSettingsState.Error(null, it) } ?: LocalSongSettingsState.Loading
        }
        val model = LocalSongSettingsUiModel(
            minimumDurationSeconds = values.durationDraft ?: configuration.sanitizedMinimumDurationSeconds,
            includedFolders = folderModels(configuration.sanitizedIncludedFolders, access, included = true),
            excludedFolders = folderModels(configuration.sanitizedExcludedFolders, access, included = false),
            hasMediaPermission = access.hasMediaPermission,
            requiresMediaPermission = configuration.requiresMediaPermission,
            isScanning = scan.isScanning,
            isSaving = values.saving,
            canEdit = !scan.isScanning && !values.saving && !values.pickingFolder && !values.requestingPermission,
            summary = scan.lastSummary,
        )
        return when {
            errorMessage != null -> LocalSongSettingsState.Error(model, errorMessage)
            model.summary?.scannedSongs == 0 -> LocalSongSettingsState.Empty(model)
            else -> LocalSongSettingsState.Success(model)
        }
    }

    private fun folderModels(entries: Set<String>, access: LocalMusicAccess, included: Boolean): ImmutableList<LocalSongFolderUiModel> =
        ImmutableList.copyOf(entries.sortedWith(String.CASE_INSENSITIVE_ORDER).map { entry ->
            val folder = LocalMusicFolder.fromEntry(entry)
            LocalSongFolderUiModel(
                entry = entry,
                path = folder.path.ifEmpty { folder.volume.orEmpty() },
                volume = folder.volume,
                needsAccess = included && entry !in access.readableFolders,
            )
        })

    private data class SettingsValues(
        val configuration: LocalSongScanConfig? = null,
        val access: LocalMusicAccess? = null,
        val durationDraft: Int? = null,
        val saving: Boolean = false,
        val pickingFolder: Boolean = false,
        val requestingPermission: Boolean = false,
        @param:StringRes val configurationError: Int? = null,
        @param:StringRes val accessError: Int? = null,
        @param:StringRes val errorMessage: Int? = null,
    )
}

@Immutable
sealed interface LocalSongSettingsState {
    data object Loading : LocalSongSettingsState
    data class Success(val model: LocalSongSettingsUiModel) : LocalSongSettingsState
    data class Empty(val model: LocalSongSettingsUiModel) : LocalSongSettingsState
    data class Error(val model: LocalSongSettingsUiModel?, @param:StringRes val message: Int) : LocalSongSettingsState

    val data: LocalSongSettingsUiModel?
        get() = when (this) {
            Loading -> null
            is Success -> model
            is Empty -> model
            is Error -> model
        }
}

@Immutable
data class LocalSongSettingsUiModel(
    val minimumDurationSeconds: Int,
    val includedFolders: ImmutableList<LocalSongFolderUiModel>,
    val excludedFolders: ImmutableList<LocalSongFolderUiModel>,
    val hasMediaPermission: Boolean,
    val requiresMediaPermission: Boolean,
    val isScanning: Boolean,
    val isSaving: Boolean,
    val canEdit: Boolean,
    val summary: LocalSongScanSummary?,
)

@Immutable
data class LocalSongFolderUiModel(val entry: String, val path: String, val volume: String?, val needsAccess: Boolean)

sealed interface LocalSongSettingsAction {
    data object Back : LocalSongSettingsAction
    data object Reload : LocalSongSettingsAction
    data object Scan : LocalSongSettingsAction
    data object CancelScan : LocalSongSettingsAction
    data object OpenAppSettings : LocalSongSettingsAction
    data class PickFolder(val included: Boolean, val initialUri: Uri? = null) : LocalSongSettingsAction
    data class FolderSelected(val included: Boolean, val uri: Uri?) : LocalSongSettingsAction
    data class RemoveFolder(val entry: String, val included: Boolean) : LocalSongSettingsAction
    data class DurationChanged(val seconds: Int) : LocalSongSettingsAction
    data object SaveDuration : LocalSongSettingsAction
    data class PermissionResult(val granted: Boolean) : LocalSongSettingsAction
    data object ExternalActionFailed : LocalSongSettingsAction
}

sealed interface LocalSongSettingsEvent {
    data object NavigateBack : LocalSongSettingsEvent
    data object RequestAudioPermission : LocalSongSettingsEvent
    data object OpenAppSettings : LocalSongSettingsEvent
    data class PickFolder(val included: Boolean, val initialUri: Uri?) : LocalSongSettingsEvent
}

private sealed interface LocalSongsScanState {
    data object Loading : LocalSongsScanState

    @Immutable
    data class Success(val summary: LocalSongScanSummary) : LocalSongsScanState

    data object Empty : LocalSongsScanState

    @Immutable
    data class Error(@StringRes val messageRes: Int) : LocalSongsScanState

    val isScanning: Boolean get() = this is Loading
    val lastSummary: LocalSongScanSummary? get() = (this as? Success)?.summary
    val errorMessageRes: Int? get() = (this as? Error)?.messageRes
}

enum class LocalSongSortType {
    MODIFIED,
    NAME,
    ARTIST,
    ALBUM,
}

enum class LocalLibraryView {
    SONGS,
    ALBUMS,
    ARTISTS,
}

@Immutable
data class LocalLibrarySort(val type: LocalSongSortType, val descending: Boolean)

@Immutable
data class LocalAlbumGroup(
    val key: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val year: Int?,
    val songCount: Int,
    val durationSeconds: Long,
)

@Immutable
data class LocalArtistGroup(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val songCount: Int,
    val albumCount: Int,
    val durationSeconds: Long,
)

@Immutable
sealed interface LocalLibraryFocus {
    val key: String

    data class Album(val album: LocalAlbumGroup) : LocalLibraryFocus {
        override val key: String get() = AlbumFocusPrefix + album.key
    }

    data class Artist(val artist: LocalArtistGroup) : LocalLibraryFocus {
        override val key: String get() = ArtistFocusPrefix + artist.id
    }
}

@Immutable
data class LocalLibraryStats(
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val durationSeconds: Long,
    val artwork: List<String>,
)

@Immutable
sealed interface LocalLibraryUiState {
    data object Loading : LocalLibraryUiState

    data class Error(@param:StringRes val message: Int) : LocalLibraryUiState

    data class Ready(
        val stats: LocalLibraryStats,
        val localSongIds: Set<String>,
        val view: LocalLibraryView,
        val query: String,
        val sort: LocalLibrarySort,
        val focus: LocalLibraryFocus?,
        val songs: List<Song>,
        val albumTracks: Map<String, List<Song>>,
        val albums: List<LocalAlbumGroup>,
        val artists: List<LocalArtistGroup>,
    ) : LocalLibraryUiState
}

sealed interface LocalLibraryAction {
    data class QueryChanged(val query: String) : LocalLibraryAction
    data class SelectView(val view: LocalLibraryView) : LocalLibraryAction
    data class OpenAlbum(val key: String) : LocalLibraryAction
    data class OpenArtist(val id: String) : LocalLibraryAction
    data object CloseFocus : LocalLibraryAction
    data class SortTypeChanged(val type: LocalSongSortType) : LocalLibraryAction
    data class SortDescendingChanged(val descending: Boolean) : LocalLibraryAction
    data object Retry : LocalLibraryAction
}

private const val QueryKey = "local_library_query"
private const val ViewKey = "local_library_view"
private const val FocusKey = "local_library_focus"
private const val AlbumFocusPrefix = "album:"
private const val ArtistFocusPrefix = "artist:"
private const val HeroArtworkCount = 4

private data class LocalLibraryInputs(
    val songs: List<Song>,
    val query: String,
    val view: LocalLibraryView,
    val focus: String?,
    val sort: LocalLibrarySort,
)

private fun buildLibraryState(inputs: LocalLibraryInputs): LocalLibraryUiState.Ready {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    val allSongs = inputs.songs
    val allTracks = albumTracks(allSongs, collator)
    val allAlbums = groupAlbums(allTracks, collator)
    val allArtists = groupArtists(allSongs, collator)
    val normalizedQuery = inputs.query.trim()
    val searching = normalizedQuery.isNotEmpty()
    val filteredSongs = if (searching) allSongs.filter { it.matches(normalizedQuery) } else allSongs
    val focusEntry = inputs.focus
    val focus =
        when {
            focusEntry == null -> null
            focusEntry.startsWith(AlbumFocusPrefix) -> {
                val key = focusEntry.removePrefix(AlbumFocusPrefix)
                allAlbums.firstOrNull { it.key == key }?.let(LocalLibraryFocus::Album)
            }
            focusEntry.startsWith(ArtistFocusPrefix) -> {
                val id = focusEntry.removePrefix(ArtistFocusPrefix)
                allArtists.firstOrNull { it.id == id }?.let(LocalLibraryFocus::Artist)
            }
            else -> null
        }
    val visibleSongs =
        when (focus) {
            is LocalLibraryFocus.Album -> {
                val tracks = allTracks[focus.album.key].orEmpty()
                if (searching) tracks.filter { it.matches(normalizedQuery) } else tracks
            }
            is LocalLibraryFocus.Artist ->
                sortSongs(
                    filteredSongs.filter { song -> song.artists.any { it.id == focus.artist.id } },
                    inputs.sort,
                    collator,
                )
            null -> sortSongs(filteredSongs, inputs.sort, collator)
        }
    return LocalLibraryUiState.Ready(
        stats =
            LocalLibraryStats(
                songCount = allSongs.size,
                albumCount = allAlbums.size,
                artistCount = allArtists.size,
                durationSeconds = allSongs.sumOf { it.durationSeconds() },
                artwork =
                    allSongs
                        .sortedByDescending { it.song.dateModified ?: LocalDateTime.MIN }
                        .asSequence()
                        .mapNotNull { it.song.thumbnailUrl }
                        .distinct()
                        .take(HeroArtworkCount)
                        .toList(),
            ),
        localSongIds = allSongs.mapTo(HashSet(allSongs.size)) { it.id },
        view = inputs.view,
        query = inputs.query,
        sort = inputs.sort,
        focus = focus,
        songs = visibleSongs,
        albumTracks = allTracks,
        albums = if (searching) groupAlbums(albumTracks(filteredSongs, collator), collator) else allAlbums,
        artists = if (searching) groupArtists(filteredSongs, collator) else allArtists,
    )
}

private fun Song.matches(query: String): Boolean =
    song.title.contains(query, ignoreCase = true) ||
        song.albumName.orEmpty().contains(query, ignoreCase = true) ||
        artists.any { artist -> artist.name.contains(query, ignoreCase = true) }

private fun Song.durationSeconds(): Long = song.duration.coerceAtLeast(0).toLong()

private fun Song.localAlbumKey(): String? {
    val name = song.albumName?.takeIf { it.isNotBlank() } ?: return null
    return song.albumId?.takeIf { it.isNotBlank() }
        ?: "tag:${LocalMusicIdentity.normalize(name)}|${LocalMusicIdentity.normalize(song.albumArtist.orEmpty())}"
}

private fun sortSongs(songs: List<Song>, sort: LocalLibrarySort, collator: Collator): List<Song> {
    val sorted =
        when (sort.type) {
            LocalSongSortType.MODIFIED -> songs.sortedBy { it.song.dateModified ?: LocalDateTime.MIN }
            LocalSongSortType.NAME -> songs.sortedWith(compareBy(collator) { it.song.title })
            LocalSongSortType.ARTIST ->
                songs.sortedWith(compareBy(collator) { song -> song.artists.joinToString(separator = "") { it.name } })
            LocalSongSortType.ALBUM -> songs.sortedWith(compareBy(collator) { it.song.albumName.orEmpty() })
        }
    return if (sort.descending) sorted.asReversed() else sorted
}

private fun albumTracks(songs: List<Song>, collator: Collator): Map<String, List<Song>> {
    val trackOrder =
        compareBy<Song>(
            { it.song.discNumber ?: Int.MAX_VALUE },
            { it.song.trackNumber ?: Int.MAX_VALUE },
        ).thenBy(collator) { it.song.title }
    val tracks = LinkedHashMap<String, MutableList<Song>>()
    songs.forEach { song ->
        val key = song.localAlbumKey() ?: return@forEach
        tracks.getOrPut(key) { mutableListOf() }.add(song)
    }
    return tracks.mapValues { (_, items) -> items.sortedWith(trackOrder) }
}

private fun groupAlbums(tracks: Map<String, List<Song>>, collator: Collator): List<LocalAlbumGroup> =
    tracks
        .mapNotNull { (key, items) ->
            val first = items.firstOrNull() ?: return@mapNotNull null
            val title = first.song.albumName ?: return@mapNotNull null
            LocalAlbumGroup(
                key = key,
                title = title,
                artist =
                    items.firstNotNullOfOrNull { item -> item.song.albumArtist?.takeIf { it.isNotBlank() } }
                        ?: first.artists.firstOrNull()?.name,
                thumbnailUrl = items.firstNotNullOfOrNull { it.song.thumbnailUrl },
                year = items.firstNotNullOfOrNull { it.song.year },
                songCount = items.size,
                durationSeconds = items.sumOf { it.durationSeconds() },
            )
        }.sortedWith(compareBy(collator) { it.title })

private fun groupArtists(songs: List<Song>, collator: Collator): List<LocalArtistGroup> {
    val entities = LinkedHashMap<String, ArtistEntity>()
    val songsByArtist = HashMap<String, MutableList<Song>>()
    songs.forEach { song ->
        song.artists.forEach { artist ->
            entities.putIfAbsent(artist.id, artist)
            songsByArtist.getOrPut(artist.id) { mutableListOf() }.add(song)
        }
    }
    return entities.values
        .map { artist ->
            val items = songsByArtist.getValue(artist.id)
            LocalArtistGroup(
                id = artist.id,
                name = artist.name,
                thumbnailUrl = artist.thumbnailUrl ?: items.firstNotNullOfOrNull { it.song.thumbnailUrl },
                songCount = items.size,
                albumCount = items.mapNotNullTo(HashSet()) { it.localAlbumKey() }.size,
                durationSeconds = items.sumOf { it.durationSeconds() },
            )
        }.sortedWith(compareBy(collator) { it.name })
}
