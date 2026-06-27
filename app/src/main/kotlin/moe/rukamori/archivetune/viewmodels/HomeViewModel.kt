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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.ExplorePage
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

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        val database: MusicDatabase,
        val syncUtils: SyncUtils,
        private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase,
    ) : ViewModel() {
        val isRefreshing = MutableStateFlow(false)
        val isLoading = MutableStateFlow(false)
        private val isInitialLoadComplete = MutableStateFlow(false)

        private val quickPicksMode =
            context.dataStore.data
                .map {
                    it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
                }.distinctUntilChanged()

        val quickPicks = MutableStateFlow<List<Song>?>(null)
        val speedDialItems = MutableStateFlow<List<LocalItem>>(emptyList())
        val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
        val keepListening = MutableStateFlow<List<LocalItem>?>(null)
        val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
        val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
        val homePage = MutableStateFlow<HomePage?>(null)
        val explorePage = MutableStateFlow<ExplorePage?>(null)
        val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
        private val previousHomePage = MutableStateFlow<HomePage?>(null)

        val recentActivity = MutableStateFlow<List<YTItem>?>(null)
        val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

        val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
        val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

        // Account display info
        val accountName = MutableStateFlow("")
        val accountImageUrl = MutableStateFlow<String?>(null)
        val isAccountLoading = MutableStateFlow(true)
        val isAccountLoggedIn = MutableStateFlow(false)
        val accountChannelsState = MutableStateFlow<AccountChannelsState>(AccountChannelsState.Empty)

        // Track last processed cookie to avoid unnecessary updates
        private var lastProcessedCookie: String? = null

        // Track if we're currently processing account data
        private var isProcessingAccountData = false
        private var wasLoggedIn = false

        private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? =
            chips?.filterNot {
                it.title.contains("podcasts", ignoreCase = true)
            }

        private fun List<Song>.toQuickPickSample(): List<Song> = distinctBy { it.id }.shuffled().take(20)

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
            allLocalItems.value =
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
                pins.mapNotNull { pin ->
                    when (pin.type.value) {
                        SpeedDialPinType.SONG.value -> songsById[pin.id]
                        SpeedDialPinType.ALBUM.value -> albumsById[pin.id]
                        SpeedDialPinType.ARTIST.value -> artistsById[pin.id]
                        SpeedDialPinType.PLAYLIST.value -> playlistsById[pin.id]
                        else -> null
                    }
                }
        }

        private suspend fun load() {
            if (isLoading.value) return
            isLoading.value = true

            try {
                supervisorScope {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                    launch { loadSpeedDialItems() }
                    launch {
                        forgottenFavorites.value =
                            database
                                .forgottenFavorites()
                                .first()
                                .shuffled()
                                .take(20)
                    }

                    launch {
                        val keepListeningSongs =
                            database
                                .mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                                .first()
                                .shuffled()
                                .take(10)
                        val keepListeningAlbums =
                            database
                                .mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                                .first()
                                .filter { it.album.thumbnailUrl != null }
                                .shuffled()
                                .take(5)
                        val keepListeningArtists =
                            database
                                .mostPlayedArtists(fromTimeStamp)
                                .first()
                                .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                                .shuffled()
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
                                                section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                                            },
                                    )
                            }.onFailure { reportException(it) }
                    }

                    launch {
                        YouTube
                            .explore()
                            .onSuccess { page ->
                                val artists: MutableMap<Int, String> = mutableMapOf()
                                val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                                database.allArtistsByPlayTime().first().let { list ->
                                    var favIndex = 0
                                    for ((artistsIndex, artist) in list.withIndex()) {
                                        artists[artistsIndex] = artist.id
                                        if (artist.artist.bookmarkedAt != null) {
                                            favouriteArtists[favIndex] = artist.id
                                            favIndex++
                                        }
                                    }
                                }
                                explorePage.value =
                                    page.copy(
                                        newReleaseAlbums =
                                            page.newReleaseAlbums
                                                .sortedBy { album ->
                                                    val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                                    val firstArtistKey =
                                                        artistIds.firstNotNullOfOrNull { artistId ->
                                                            if (artistId in favouriteArtists.values) {
                                                                favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                                            } else {
                                                                artists.entries.firstOrNull { it.value == artistId }?.key
                                                            }
                                                        } ?: Int.MAX_VALUE
                                                    firstArtistKey
                                                }.filterExplicit(hideExplicit),
                                    )
                            }.onFailure { reportException(it) }
                    }
                }

                updateAllLocalItems()

                viewModelScope.launch(Dispatchers.IO) {
                    loadSimilarRecommendations()
                }

                allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value
                        ?.sections
                        ?.flatMap { it.items }
                        .orEmpty()

                isInitialLoadComplete.value = true
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isLoading.value = false
            }
        }

        private suspend fun loadSimilarRecommendations() {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

            val artistRecommendations =
                database
                    .mostPlayedArtists(fromTimeStamp, limit = 10)
                    .first()
                    .filter { it.artist.isYouTubeArtist }
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
                                    .shuffled()
                                    .ifEmpty { return@mapNotNull null },
                        )
                    }

            similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value
                    ?.sections
                    ?.flatMap { it.items }
                    .orEmpty()
        }

        private fun clearAccountData() {
            accountName.value = ""
            accountImageUrl.value = null
            accountPlaylists.value = null
            accountChannelsState.value = AccountChannelsState.Empty
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
            accountName.value = ""
            accountImageUrl.value = null
            accountChannelsState.value = AccountChannelsState.Loading

            try {
                YouTube
                    .accountInfo()
                    .onSuccess { info ->
                        accountName.value = info.name
                        accountImageUrl.value = info.thumbnailUrl
                    }.onFailure { error ->
                        Timber.w(error, "Failed to fetch account info")
                    }

                YouTube
                    .accountChannels()
                    .onSuccess { channels ->
                        accountChannelsState.value = channels
                            .map { it.toUiModel() }
                            .takeIf { it.size > 1 }
                            ?.let { AccountChannelsState.Success(AccountChannelCollection(it)) }
                            ?: AccountChannelsState.Empty
                    }.onFailure { error ->
                        Timber.w(error, "Failed to fetch account channels")
                        reportException(error)
                        accountChannelsState.value = AccountChannelsState.Error(error.message.orEmpty())
                    }
            } catch (e: CancellationException) {
                accountChannelsState.value = AccountChannelsState.Empty
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching account info")
                reportException(e)
                accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
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

        private val _isLoadingMore = MutableStateFlow(false)

        fun loadMoreYouTubeItems(continuation: String?) {
            if (continuation == null || _isLoadingMore.value) return
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)

            viewModelScope.launch(Dispatchers.IO) {
                _isLoadingMore.value = true
                val nextSections =
                    YouTube.home(continuation).getOrNull() ?: run {
                        _isLoadingMore.value = false
                        return@launch
                    }

                homePage.value =
                    nextSections.copy(
                        chips = homePage.value?.chips,
                        sections =
                            (homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                                section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                            },
                    )
                _isLoadingMore.value = false
            }
        }

        fun toggleChip(chip: HomePage.Chip?) {
            if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
                homePage.value = previousHomePage.value
                previousHomePage.value = null
                selectedChip.value = null
                return
            }

            if (selectedChip.value == null) {
                previousHomePage.value = homePage.value
            }

            viewModelScope.launch(Dispatchers.IO) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch

                homePage.value =
                    nextSections.copy(
                        chips = homePage.value?.chips,
                        sections =
                            nextSections.sections.map { section ->
                                section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                            },
                    )
                selectedChip.value = chip
            }
        }

        fun refresh() {
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

        fun refreshAccountData() {
            viewModelScope.launch(Dispatchers.IO) {
                if (isProcessingAccountData) return@launch

                isProcessingAccountData = true
                isAccountLoading.value = true
                try {
                    val cookie = context.dataStore.get(InnerTubeCookieKey, "")
                    val loggedIn = hasYouTubeLoginCookie(cookie)
                    isAccountLoggedIn.value = loggedIn

                    if (loggedIn && prepareYouTubeAccount(cookie)) {
                        supervisorScope {
                            launch { refreshAccountIdentity() }
                            launch { refreshAccountPlaylistsInternal() }
                        }
                    } else {
                        clearAccountData()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error refreshing account data")
                    clearAccountData()
                } finally {
                    isAccountLoading.value = false
                    isProcessingAccountData = false
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
                    accountChannelsState.value = AccountChannelsState.Loading

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
                    accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
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
                        if (isProcessingAccountData) return@collect

                        lastProcessedCookie = cookie
                        isProcessingAccountData = true
                        isAccountLoading.value = true

                        try {
                            val isLoggedIn = hasYouTubeLoginCookie(cookie)
                            val loginTransition = isLoggedIn && !wasLoggedIn
                            wasLoggedIn = isLoggedIn
                            isAccountLoggedIn.value = isLoggedIn

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
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing cookie change")
                            clearAccountData()
                            isAccountLoggedIn.value = false
                        } finally {
                            isAccountLoading.value = false
                            isProcessingAccountData = false
                        }
                    }
            }
        }
    }
