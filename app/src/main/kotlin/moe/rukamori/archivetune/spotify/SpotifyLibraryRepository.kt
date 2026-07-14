/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifyAccountAvatarUrlKey
import moe.rukamori.archivetune.constants.SpotifyAccountNameKey
import moe.rukamori.archivetune.constants.SpotifyLibraryPlaylistsCacheKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.clearWebAuthSession
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
        val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val tokenRefreshMutex = Mutex()

        suspend fun restoreCachedPlaylists() {
            withContext(Dispatchers.IO) {
                if (_playlists.value.isNotEmpty()) return@withContext
                val cached =
                    context.dataStore.data
                        .first()[SpotifyLibraryPlaylistsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyPlaylist.serializer()),
                        cached,
                    )
                }.onSuccess { playlists ->
                    _playlists.value = playlists
                }.onFailure { error ->
                    reportException(error)
                    context.dataStore.edit { prefs ->
                        prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    }
                }
            }
        }

        suspend fun restoreSession(): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                val prefs = context.dataStore.data.first()
                val token = prefs[SpotifyAccessTokenKey].orEmpty()
                val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                val accountName = prefs[SpotifyAccountNameKey].orEmpty()
                val avatarUrl = prefs[SpotifyAccountAvatarUrlKey]

                if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                    Spotify.accessToken = token
                    return@withContext SpotifyAccountSession(
                        isAuthenticated = true,
                        accountName = accountName,
                        accountAvatarUrl = avatarUrl,
                    )
                }

                val spDc = prefs[SpotifySpDcKey].orEmpty()
                if (spDc.isBlank()) return@withContext SpotifyAccountSession()

                refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty())
                    .fold(
                        onSuccess = {
                            val refreshed = context.dataStore.data.first()
                            SpotifyAccountSession(
                                isAuthenticated = true,
                                accountName = refreshed[SpotifyAccountNameKey].orEmpty(),
                                accountAvatarUrl = refreshed[SpotifyAccountAvatarUrlKey],
                            )
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            reportException(it)
                            SpotifyAccountSession()
                        },
                    )
            }

        suspend fun connectWithCookies(
            spDc: String,
            spKey: String,
        ): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                var credentialsChanged = false
                context.dataStore.edit { prefs ->
                    credentialsChanged =
                        prefs[SpotifySpDcKey] != spDc || prefs[SpotifySpKeyKey].orEmpty() != spKey
                    prefs[SpotifySpDcKey] = spDc
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    if (spKey.isNotBlank()) {
                        prefs[SpotifySpKeyKey] = spKey
                    } else {
                        prefs.remove(SpotifySpKeyKey)
                    }
                    if (credentialsChanged) {
                        prefs.remove(SpotifyAccessTokenKey)
                        prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    }
                }
                if (credentialsChanged) Spotify.accessToken = null
                _playlists.value = emptyList()
                _errorMessage.value = null
                refreshAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                val prefs = context.dataStore.data.first()
                SpotifyAccountSession(
                    isAuthenticated = true,
                    accountName = prefs[SpotifyAccountNameKey].orEmpty(),
                    accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
                )
            }

        suspend fun logout() {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { prefs ->
                    prefs.remove(SpotifySpDcKey)
                    prefs.remove(SpotifySpKeyKey)
                    prefs.remove(SpotifyAccessTokenKey)
                    prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    prefs.remove(SpotifyAccountNameKey)
                    prefs.remove(SpotifyAccountAvatarUrlKey)
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                }
                _playlists.value = emptyList()
                _errorMessage.value = null
                Spotify.accessToken = null
                runCatching { clearWebAuthSession(context) }
                    .onFailure(::reportException)
            }
        }

        suspend fun refreshPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                _isRefreshing.value = true
                _errorMessage.value = null
                try {
                    ensureAuthenticated()
                    refreshProfile()
                    val loaded = fetchAllPlaylists()
                    _playlists.value = loaded
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyLibraryPlaylistsCacheKey] =
                            spotifyCacheJson.encodeToString(
                                ListSerializer(SpotifyPlaylist.serializer()),
                                loaded,
                            )
                    }
                    loaded
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportException(error)
                    _errorMessage.value = error.message
                    _playlists.value
                } finally {
                    _isRefreshing.value = false
                }
            }

        suspend fun playlist(playlistId: String): SpotifyPlaylist =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.playlist(playlistId).getOrThrow()
                }
            }

        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val tracks = ArrayList<SpotifyTrack>()
                var offset = 0
                val limit = 50

                while (true) {
                    val page =
                        spotifyCallWithTokenRetry {
                            Spotify
                                .playlistTracks(
                                    playlistId = playlistId,
                                    limit = limit,
                                    offset = offset,
                                ).getOrThrow()
                        }
                    if (page.items.isEmpty()) break
                    val pageTracks = page.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
                    tracks += pageTracks
                    offset += page.items.size
                    if (offset >= page.total || page.items.size < limit) break
                }

                tracks
            }

        private suspend fun ensureAuthenticated() {
            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                throw IllegalStateException(context.getString(R.string.spotify_not_connected))
            }
            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
        }

        private suspend fun refreshAccessToken(
            spDc: String,
            spKey: String,
            rejectedAccessToken: String? = null,
        ): Result<Unit> =
            try {
                tokenRefreshMutex.withLock {
                    val prefs = context.dataStore.data.first()
                    val storedAccessToken = prefs[SpotifyAccessTokenKey].orEmpty()
                    val storedExpiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                    val credentialsMatch =
                        prefs[SpotifySpDcKey].orEmpty() == spDc &&
                            prefs[SpotifySpKeyKey].orEmpty() == spKey
                    val canReuseStoredToken =
                        credentialsMatch &&
                            storedAccessToken.isNotBlank() &&
                            storedExpiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS &&
                            (rejectedAccessToken == null || storedAccessToken != rejectedAccessToken)

                    if (canReuseStoredToken) {
                        Spotify.accessToken = storedAccessToken
                        return@withLock Result.success(Unit)
                    }

                    val token = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                    Spotify.accessToken = token.accessToken
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyAccessTokenKey] = token.accessToken
                        prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
                    }
                    refreshProfile()
                    Result.success(Unit)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

        private suspend fun refreshProfile() {
            Spotify
                .me()
                .onSuccess { user ->
                    context.dataStore.edit { prefs ->
                        prefs[SpotifyAccountNameKey] = user.displayName.orEmpty()
                        user.images
                            .firstOrNull()
                            ?.url
                            ?.let { prefs[SpotifyAccountAvatarUrlKey] = it }
                            ?: prefs.remove(SpotifyAccountAvatarUrlKey)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }

        private suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
            val playlists = ArrayList<SpotifyPlaylist>()
            var offset = 0
            val limit = 50

            while (true) {
                val page =
                    spotifyCallWithTokenRetry {
                        Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                    }
                if (page.items.isEmpty()) break
                playlists +=
                    page.items.map { playlist ->
                        if (playlist.tracks?.total != null) {
                            playlist
                        } else {
                            playlistTrackCount(playlist.id)
                                ?.let { playlist.copy(tracks = SpotifyPlaylistTracksRef(total = it)) }
                                ?: playlist
                        }
                    }
                offset += page.items.size
                if (offset >= page.total || page.items.size < limit) break
            }

            return playlists
        }

        private suspend fun playlistTrackCount(playlistId: String): Int? =
            try {
                spotifyCallWithTokenRetry {
                    Spotify
                        .playlistTracks(
                            playlistId = playlistId,
                            limit = 1,
                            offset = 0,
                        ).getOrThrow()
                }.total
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportException(error)
                null
            }

        private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T =
            Spotify.accessToken.let { rejectedAccessToken ->
                runCatching { block() }
                    .getOrElse { error ->
                        if ((error as? Spotify.SpotifyException)?.statusCode != 401) throw error
                        val prefs = context.dataStore.data.first()
                        val spDc = prefs[SpotifySpDcKey].orEmpty()
                        if (spDc.isBlank()) throw error
                        refreshAccessToken(
                            spDc = spDc,
                            spKey = prefs[SpotifySpKeyKey].orEmpty(),
                            rejectedAccessToken = rejectedAccessToken,
                        ).getOrThrow()
                        block()
                    }
            }

        companion object {
            private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
            private val spotifyCacheJson =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        }
    }

data class SpotifyAccountSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)
