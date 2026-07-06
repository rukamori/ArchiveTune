/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.constants.QuickPicksKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomePresentationPreferences
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.home.ObserveHomePresentationPreferencesUseCase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import timber.log.Timber
import javax.inject.Inject

sealed interface AccountChannelsState {
    data object Loading : AccountChannelsState

    data class Success(
        val channels: AccountChannelCollection,
    ) : AccountChannelsState

    data object Empty : AccountChannelsState

    data class Error(
        val message: String,
    ) : AccountChannelsState
}

@Immutable
data class AccountChannelCollection(
    val items: List<AccountChannelUiModel>,
)

@Immutable
data class AccountChannelUiModel(
    val name: String,
    val byline: String,
    val channelHandle: String,
    val thumbnailUrl: String?,
    val dataSyncId: String,
    val isSelected: Boolean,
)

private data class HomeLocalContent(
    val quickPicks: List<Song>,
    val speedDialItems: List<LocalItem>,
    val forgottenFavorites: List<Song>,
    val keepListening: List<LocalItem>,
)

private data class HomeRemoteContent(
    val homePage: HomePage?,
    val similarRecommendations: List<SimilarRecommendation>,
    val accountPlaylists: List<PlaylistItem>,
    val accountName: String,
    val accountImageUrl: String?,
)

private data class HomeContent(
    val local: HomeLocalContent,
    val remote: HomeRemoteContent,
    val selectedChip: HomePage.Chip?,
) {
    val hasContent: Boolean
        get() =
            local.quickPicks.isNotEmpty() ||
                local.speedDialItems.isNotEmpty() ||
                local.forgottenFavorites.isNotEmpty() ||
                local.keepListening.isNotEmpty() ||
                remote.similarRecommendations.isNotEmpty() ||
                remote.accountPlaylists.isNotEmpty() ||
                remote.homePage?.sections?.any { it.items.isNotEmpty() } == true
}

private data class HomeStateInputs(
    val content: HomeContent,
    val preferences: HomePresentationPreferences,
    val isLoading: Boolean,
    val isInitialLoadComplete: Boolean,
    val loadError: Int?,
) {
    fun toScreenState(
        isRefreshing: Boolean,
        isLoadingMore: Boolean,
    ): HomeScreenState {
        if (!content.hasContent) {
            if (loadError != null && isInitialLoadComplete) {
                return HomeScreenState.Error(loadError)
            }
            if (isLoading || !isInitialLoadComplete) {
                return HomeScreenState.Loading
            }
            return HomeScreenState.Empty
        }

        return HomeScreenState.Success(
            HomeUiState(
                quickPicks = ImmutableList.copyOf(content.local.quickPicks),
                speedDialItems = ImmutableList.copyOf(content.local.speedDialItems),
                forgottenFavorites = ImmutableList.copyOf(content.local.forgottenFavorites),
                keepListening = ImmutableList.copyOf(content.local.keepListening),
                similarRecommendations = ImmutableList.copyOf(content.remote.similarRecommendations),
                accountPlaylists = ImmutableList.copyOf(content.remote.accountPlaylists),
                homePage = content.remote.homePage,
                selectedChip = content.selectedChip,
                accountName = content.remote.accountName,
                accountImageUrl = content.remote.accountImageUrl,
                quickPicksDisplayMode = preferences.quickPicksDisplayMode,
                showCategoryChips = preferences.showCategoryChips,
                showTonalBackdrop = preferences.showTonalBackdrop,
                isRefreshing = isRefreshing,
                isLoadingMore = isLoadingMore,
            ),
        )
    }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
        private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase,
        observeHomePresentationPreferences: ObserveHomePresentationPreferencesUseCase,
    ) : ViewModel() {
        private val isRefreshing = MutableStateFlow(false)
        private val isLoading = MutableStateFlow(false)
        private val isInitialLoadComplete = MutableStateFlow(false)
        private val loadError = MutableStateFlow<Int?>(null)
        private val isLoadingMore = MutableStateFlow(false)

        private val quickPicksMode =
            context.dataStore.data
                .map {
                    it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
                }.distinctUntilChanged()

        private val quickPicks = MutableStateFlow<List<Song>?>(null)
        private val speedDialItems = MutableStateFlow<List<LocalItem>>(emptyList())
        private val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
        private val keepListening = MutableStateFlow<List<LocalItem>?>(null)
        private val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
        private val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
        private val homePage = MutableStateFlow<HomePage?>(null)
        private val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
        private val previousHomePage = MutableStateFlow<HomePage?>(null)

        private val _allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
        val allLocalItems: StateFlow<List<LocalItem>> = _allLocalItems.asStateFlow()
        private val _allYtItems = MutableStateFlow<List<YTItem>>(emptyList())
        val allYtItems: StateFlow<List<YTItem>> = _allYtItems.asStateFlow()

        private val _accountName = MutableStateFlow("")
        val accountName: StateFlow<String> = _accountName.asStateFlow()
        private val _accountImageUrl = MutableStateFlow<String?>(null)
        val accountImageUrl: StateFlow<String?> = _accountImageUrl.asStateFlow()
        private val _accountChannelsState = MutableStateFlow<AccountChannelsState>(AccountChannelsState.Empty)
        val accountChannelsState: StateFlow<AccountChannelsState> = _accountChannelsState.asStateFlow()

        private val presentationPreferences = observeHomePresentationPreferences()

        private val localContent =
            combine(
                quickPicks,
                speedDialItems,
                forgottenFavorites,
                keepListening,
            ) { quickPicks, speedDialItems, forgottenFavorites, keepListening ->
                HomeLocalContent(
                    quickPicks = quickPicks.orEmpty(),
                    speedDialItems = speedDialItems,
                    forgottenFavorites = forgottenFavorites.orEmpty(),
                    keepListening = keepListening.orEmpty(),
                )
            }

        private val remoteContent =
            combine(
                homePage,
                similarRecommendations,
                accountPlaylists,
                accountName,
                accountImageUrl,
            ) { homePage, similarRecommendations, accountPlaylists, accountName, accountImageUrl ->
                HomeRemoteContent(
                    homePage = homePage,
                    similarRecommendations = similarRecommendations.orEmpty(),
                    accountPlaylists = accountPlaylists.orEmpty(),
                    accountName = accountName,
                    accountImageUrl = accountImageUrl,
                )
            }

        private val homeContent =
            combine(
                localContent,
                remoteContent,
                selectedChip,
            ) { localContent, remoteContent, selectedChip ->
                HomeContent(
                    local = localContent,
                    remote = remoteContent,
                    selectedChip = selectedChip,
                )
            }

        val screenState: StateFlow<HomeScreenState> =
            combine(
                homeContent,
                presentationPreferences,
                isLoading,
                isInitialLoadComplete,
                loadError,
            ) { content, preferences, isLoading, isInitialLoadComplete, loadError ->
                HomeStateInputs(
                    content = content,
                    preferences = preferences,
                    isLoading = isLoading,
                    isInitialLoadComplete = isInitialLoadComplete,
                    loadError = loadError,
                )
            }.combine(
                combine(isRefreshing, isLoadingMore) { isRefreshing, isLoadingMore ->
                    isRefreshing to isLoadingMore
                },
            ) { inputs, loadingState ->
                inputs.toScreenState(
                    isRefreshing = loadingState.first,
                    isLoadingMore = loadingState.second,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeScreenState.Loading,
            )

        private var wasLoggedIn = false
        private var chipLoadJob: Job? = null

        private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? =
            chips?.filterNot {
                it.title.contains("podcasts", ignoreCase = true)
            }

        private fun List<Song>.toQuickPickSample(): List<Song> =
            filter { song -> song.artists.none { it.blockedAt != null } }
                .distinctBy { it.id }
                .shuffled()
                .take(20)

        private fun List<Song>.hasSameSongIdsAs(other: List<Song>): Boolean {
            if (size != other.size) return false

            val ids = HashSet<String>(size)
            for (song in this) {
                ids += song.id
            }
            for (song in other) {
                if (!ids.remove(song.id)) return false
            }
            return ids.isEmpty()
        }

        private fun Flow<List<Song>>.distinctUntilSongIdsChanged(): Flow<List<Song>> =
            distinctUntilChanged { old, new -> old.hasSameSongIdsAs(new) }

        private fun updateAllLocalItems() {
            _allLocalItems.value =
                (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                    .filter { it is Song || it is Album }
        }

        private suspend fun quickPicksWithFallback(primary: List<Song>): List<Song> {
            val primaryPicks = primary.toQuickPickSample()
            if (primaryPicks.isNotEmpty()) return primaryPicks

            val recentPicks = database.recentSongs(limit = 60).first().toQuickPickSample()
            if (recentPicks.isNotEmpty()) return recentPicks

            return database.allSongs().first().toQuickPickSample()
        }

        private fun lastListenQuickPicksFlow(): Flow<List<Song>> =
            database
                .lastEventSongId()
                .distinctUntilChanged()
                .flatMapLatest { lastSongId ->
                    flow {
                        if (!lastSongId.isNullOrBlank() && database.hasRelatedSongs(lastSongId)) {
                            val relatedSongs = database.getRelatedSongs(lastSongId).first().toQuickPickSample()
                            if (relatedSongs.isNotEmpty()) {
                                emit(relatedSongs)
                                return@flow
                            }
                        }

                        emitAll(
                            database
                                .quickPicks()
                                .distinctUntilSongIdsChanged()
                                .map { songs -> quickPicksWithFallback(songs) },
                        )
                    }
                }

        private fun observeQuickPicks() {
            viewModelScope.launch(Dispatchers.IO) {
                quickPicksMode
                    .flatMapLatest { mode ->
                        when (mode) {
                            QuickPicks.QUICK_PICKS -> {
                                database
                                    .quickPicks()
                                    .distinctUntilSongIdsChanged()
                                    .map { songs -> quickPicksWithFallback(songs) }
                            }

                            QuickPicks.LAST_LISTEN -> {
                                lastListenQuickPicksFlow()
                            }

                            QuickPicks.DONT_SHOW -> {
                                flowOf(null)
                            }
                        }
                    }.catch { throwable ->
                        reportException(throwable)
                        emit(quickPicksWithFallback(emptyList()))
                    }.collect { picks ->
                        quickPicks.value = picks
                        updateAllLocalItems()
                    }
            }
        }

        private suspend fun refreshQuickPicks() {
            val picks =
                when (quickPicksMode.first()) {
                    QuickPicks.QUICK_PICKS -> {
                        quickPicksWithFallback(database.quickPicks().first())
                    }

                    QuickPicks.LAST_LISTEN -> {
                        lastListenQuickPicksFlow().first()
                    }

                    QuickPicks.DONT_SHOW -> {
                        null
                    }
                }
            quickPicks.value = picks
            updateAllLocalItems()
        }

        private suspend fun loadSpeedDialItems() {
            val pins = parseSpeedDialPins(context.dataStore.get(SpeedDialSongIdsKey, ""))
            if (pins.isEmpty()) {
                speedDialItems.value = emptyList()
                return
            }
            val songIds = pins.filter { it.type == SpeedDialPinType.SONG }.map { it.id }
            val albumIds = pins.filter { it.type == SpeedDialPinType.ALBUM }.map { it.id }
            val artistIds = pins.filter { it.type == SpeedDialPinType.ARTIST }.map { it.id }
            val playlistIds = pins.filter { it.type == SpeedDialPinType.PLAYLIST }.map { it.id }

            val songsById = database.getSongsByIds(songIds).associateBy { it.id }
            val albumsById = albumIds.mapNotNull { id -> database.album(id).first() }.associateBy { it.id }
            val artistsById = artistIds.mapNotNull { id -> database.artist(id).first() }.associateBy { it.id }
            val playlistsById = playlistIds.mapNotNull { id -> database.getPlaylistById(id) }.associateBy { it.id }

            speedDialItems.value =
                pins
                    .mapNotNull { pin ->
                        when (pin.type.value) {
                            SpeedDialPinType.SONG.value -> songsById[pin.id]
                            SpeedDialPinType.ALBUM.value -> albumsById[pin.id]
                            SpeedDialPinType.ARTIST.value -> artistsById[pin.id]
                            SpeedDialPinType.PLAYLIST.value -> playlistsById[pin.id]
                            else -> null
                        }
                    }.filter { item ->
                        when (item) {
                            is Song -> item.artists.none { it.blockedAt != null }
                            is Album -> item.artists.none { it.blockedAt != null }
                            is Artist -> item.artist.blockedAt == null
                            else -> true
                        }
                    }
        }

        private suspend fun load() {
            if (isLoading.value) return
            isLoading.value = true
            loadError.value = null

            try {
                supervisorScope {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                    launch { loadSpeedDialItems() }
                    launch {
                        forgottenFavorites.value =
                            database
                                .forgottenFavorites()
                                .first()
                                .filter { song -> song.artists.none { it.blockedAt != null } }
                                .shuffled()
                                .take(20)
                    }

                    launch {
                        val keepListeningSongs =
                            database
                                .mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                                .first()
                                .filter { song -> song.artists.none { it.blockedAt != null } }
                                .shuffled()
                                .take(10)
                        val keepListeningAlbums =
                            database
                                .mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                                .first()
                                .filter { it.album.thumbnailUrl != null && it.artists.none { artist -> artist.blockedAt != null } }
                                .shuffled()
                                .take(5)
                        val keepListeningArtists =
                            database
                                .mostPlayedArtists(fromTimeStamp)
                                .first()
                                .filter {
                                    it.artist.blockedAt == null &&
                                        it.artist.isYouTubeArtist &&
                                        it.artist.thumbnailUrl != null
                                }.shuffled()
                                .take(5)
                        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                    }

                    launch {
                        YouTube
                            .home()
                            .onSuccess { page ->
                                homePage.value =
                                    page.copy(
                                        chips = filterHomeChips(page.chips),
                                        sections =
                                            page.sections.map { section ->
                                                section.copy(
                                                    items =
                                                        section.items
                                                            .filterExplicit(hideExplicit)
                                                            .filterVideo(hideVideo)
                                                            .filterBlockedArtists(blockedArtistIds),
                                                )
                                            },
                                    )
                            }.onFailure {
                                reportException(it)
                                loadError.value = R.string.error_unknown
                            }
                    }
                }

                updateAllLocalItems()

                viewModelScope.launch(Dispatchers.IO) {
                    loadSimilarRecommendations()
                }

                _allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value
                        ?.sections
                        ?.flatMap { it.items }
                        .orEmpty()

                isInitialLoadComplete.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                loadError.value = R.string.error_unknown
            } finally {
                isInitialLoadComplete.value = true
                isLoading.value = false
            }
        }

        private suspend fun loadSimilarRecommendations() {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val blockedArtistIds = database.getBlockedArtistIds().toSet()
            val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

            val artistRecommendations =
                database
                    .mostPlayedArtists(fromTimeStamp, limit = 10)
                    .first()
                    .filter { it.artist.blockedAt == null && it.artist.isYouTubeArtist }
                    .shuffled()
                    .take(3)
                    .mapNotNull {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(it.id).onSuccess { page ->
                            items +=
                                page.sections
                                    .getOrNull(page.sections.size - 2)
                                    ?.items
                                    .orEmpty()
                            items +=
                                page.sections
                                    .lastOrNull()
                                    ?.items
                                    .orEmpty()
                        }
                        SimilarRecommendation(
                            title = it,
                            items =
                                items
                                    .filterExplicit(hideExplicit)
                                    .filterVideo(hideVideo)
                                    .filterBlockedArtists(blockedArtistIds)
                                    .shuffled()
                                    .ifEmpty { return@mapNotNull null },
                        )
                    }

            val songRecommendations =
                database
                    .mostPlayedSongs(fromTimeStamp, limit = 10)
                    .first()
                    .filter { it.album != null }
                    .shuffled()
                    .take(2)
                    .mapNotNull { song ->
                        val endpoint =
                            YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                                ?: return@mapNotNull null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                        SimilarRecommendation(
                            title = song,
                            items =
                                (
                                    page.songs.shuffled().take(8) +
                                        page.albums.shuffled().take(4) +
                                        page.artists.shuffled().take(4) +
                                        page.playlists.shuffled().take(4)
                                ).filterExplicit(hideExplicit)
                                    .filterVideo(hideVideo)
                                    .filterBlockedArtists(blockedArtistIds)
                                    .shuffled()
                                    .ifEmpty { return@mapNotNull null },
                        )
                    }

            similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

            _allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value
                    ?.sections
                    ?.flatMap { it.items }
                    .orEmpty()
        }

        private fun clearAccountData() {
            _accountName.value = ""
            _accountImageUrl.value = null
            accountPlaylists.value = null
            _accountChannelsState.value = AccountChannelsState.Empty
        }

        private fun prepareYouTubeAccount(cookie: String): Boolean =
            try {
                YouTube.cookie = cookie
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to set YouTube cookie")
                false
            }

        private suspend fun refreshAccountIdentity() {
            _accountName.value = ""
            _accountImageUrl.value = null
            _accountChannelsState.value = AccountChannelsState.Loading

            try {
                YouTube
                    .accountInfo()
                    .onSuccess { info ->
                        _accountName.value = info.name
                        _accountImageUrl.value = info.thumbnailUrl
                    }.onFailure { error ->
                        Timber.w(error, "Failed to fetch account info")
                    }

                YouTube
                    .accountChannels()
                    .onSuccess { channels ->
                        _accountChannelsState.value = channels
                            .map { it.toUiModel() }
                            .takeIf { it.size > 1 }
                            ?.let { AccountChannelsState.Success(AccountChannelCollection(it)) }
                            ?: AccountChannelsState.Empty
                    }.onFailure { error ->
                        Timber.w(error, "Failed to fetch account channels")
                        reportException(error)
                        _accountChannelsState.value = AccountChannelsState.Error(error.message.orEmpty())
                    }
            } catch (e: CancellationException) {
                _accountChannelsState.value = AccountChannelsState.Empty
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching account info")
                reportException(e)
                _accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
            }
        }

        private fun AccountChannel.toUiModel(): AccountChannelUiModel =
            AccountChannelUiModel(
                name = name,
                byline = byline.orEmpty(),
                channelHandle = channelHandle.orEmpty(),
                thumbnailUrl = thumbnailUrl,
                dataSyncId = dataSyncId,
                isSelected = isSelected,
            )

        private suspend fun refreshAccountPlaylistsInternal() {
            try {
                YouTube
                    .library("FEmusic_liked_playlists")
                    .completed()
                    .onSuccess {
                        val lists =
                            it.items.filterIsInstance<PlaylistItem>().filterNot { playlist ->
                                playlist.id == "SE"
                            }
                        accountPlaylists.value = lists
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.w(error, "Failed to fetch account playlists")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching account playlists")
            }
        }

        private fun loadMoreYouTubeItems(continuation: String?) {
            if (continuation == null || isLoadingMore.value) return
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)

            viewModelScope.launch(Dispatchers.IO) {
                isLoadingMore.value = true
                try {
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val nextSections = YouTube.home(continuation).getOrNull() ?: return@launch
                    homePage.value =
                        nextSections.copy(
                            chips = homePage.value?.chips,
                            sections =
                                (homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                                    section.copy(
                                        items =
                                            section.items
                                                .filterExplicit(hideExplicit)
                                                .filterVideo(hideVideo)
                                                .filterBlockedArtists(blockedArtistIds),
                                    )
                                },
                        )
                } finally {
                    isLoadingMore.value = false
                }
            }
        }

        private fun toggleChip(chip: HomePage.Chip?) {
            chipLoadJob?.cancel()
            if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
                homePage.value = previousHomePage.value
                previousHomePage.value = null
                selectedChip.value = null
                return
            }

            if (selectedChip.value == null) {
                previousHomePage.value = homePage.value
            }

            chipLoadJob =
                viewModelScope.launch(Dispatchers.IO) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch

                    homePage.value =
                        nextSections.copy(
                            chips = homePage.value?.chips,
                            sections =
                                nextSections.sections.map { section ->
                                    section.copy(
                                        items =
                                            section.items
                                                .filterExplicit(hideExplicit)
                                                .filterVideo(hideVideo)
                                                .filterBlockedArtists(blockedArtistIds),
                                    )
                                },
                        )
                    selectedChip.value = chip
                }
        }

        fun onAction(action: HomeAction) {
            when (action) {
                HomeAction.Refresh -> refresh()
                is HomeAction.SelectChip -> toggleChip(action.chip)
                is HomeAction.LoadMore -> loadMoreYouTubeItems(action.continuation)
            }
        }

        private fun refresh() {
            if (isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                isRefreshing.value = true
                try {
                    supervisorScope {
                        launch { load() }
                        launch { refreshQuickPicks() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    isRefreshing.value = false
                }
            }
        }

        fun switchToAccount(
            account: SavedAccount,
            forceSyncOnSwitch: Boolean,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val authState = switchSavedYouTubeAccount(account).getOrThrow()

                    if (forceSyncOnSwitch && account.ytmSync && authState.hasLoginCookie) {
                        syncUtils.performFullSync(authoritative = true)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error switching account")
                    reportException(e)
                }
            }
        }

        fun switchToAccountChannel(
            channel: AccountChannelUiModel,
            forceSyncOnSwitch: Boolean,
        ) {
            if (channel.dataSyncId.isBlank()) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    _accountChannelsState.value = AccountChannelsState.Loading

                    context.dataStore.edit { preferences ->
                        preferences[DataSyncIdKey] = channel.dataSyncId
                        preferences[AccountNameKey] = channel.name
                        preferences[AccountChannelHandleKey] = channel.channelHandle
                        if (channel.byline.contains("@")) {
                            preferences[AccountEmailKey] = channel.byline
                        }
                    }

                    val authState =
                        context.dataStore.data
                            .first()
                            .toPlaybackAuthState()
                    YouTube.authState = authState

                    supervisorScope {
                        launch { refreshAccountIdentity() }
                        launch { refreshAccountPlaylistsInternal() }
                    }

                    if (forceSyncOnSwitch && context.dataStore.get(YtmSyncKey, true) && authState.hasLoginCookie) {
                        syncUtils.performFullSync(authoritative = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error switching account channel")
                    reportException(e)
                    _accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
                }
            }
        }

        init {
            observeQuickPicks()

            viewModelScope.launch(Dispatchers.IO) {
                load()
            }

            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.data
                    .map { it[SpeedDialSongIdsKey].orEmpty() }
                    .distinctUntilChanged()
                    .collect {
                        loadSpeedDialItems()
                    }
            }

            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(3000)

                syncUtils.cleanupDuplicatePlaylists()
            }

            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .collect { cookie ->
                        try {
                            val isLoggedIn = hasYouTubeLoginCookie(cookie)
                            val loginTransition = isLoggedIn && !wasLoggedIn
                            wasLoggedIn = isLoggedIn

                            if (isLoggedIn && cookie != null && cookie.isNotEmpty()) {
                                if (!prepareYouTubeAccount(cookie)) {
                                    clearAccountData()
                                    return@collect
                                }

                                supervisorScope {
                                    kotlinx.coroutines.delay(100)
                                    launch { refreshAccountIdentity() }
                                    launch { refreshAccountPlaylistsInternal() }
                                }

                                if (loginTransition) {
                                    launch {
                                        try {
                                            if (context.dataStore.get(YtmSyncKey, true)) {
                                                syncUtils.performFullSync()
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error during login-triggered sync")
                                            reportException(e)
                                        }
                                    }
                                }
                            } else {
                                clearAccountData()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing cookie change")
                            clearAccountData()
                        }
                    }
            }
        }
    }
