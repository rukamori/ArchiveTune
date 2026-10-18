/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.models.toMediaMetadata
import timber.log.Timber
import java.time.LocalDateTime
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyDiscoverRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        fun getCurrentWeekPlaylistName(): String {
            return context.getString(R.string.discover_weekly)
        }

        suspend fun getOrCreateWeeklyPlaylist(): Playlist =
            withContext(Dispatchers.IO) {
                val playlistName = getCurrentWeekPlaylistName()
                val existingPlaylists = database.playlists(PlaylistSortType.CREATE_DATE, descending = true).first()
                val existing = existingPlaylists.firstOrNull { it.playlist.name == playlistName }
                if (existing != null) {
                    existing
                } else {
                    val newPlaylistEntity =
                        PlaylistEntity(
                            name = playlistName,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                        )
                    database.withTransaction {
                        insert(newPlaylistEntity)
                    }
                    Playlist(
                        playlist = newPlaylistEntity,
                        songCount = 0,
                        songThumbnails = emptyList(),
                    )
                }
            }

        suspend fun getHybridSeedSongs(): List<Song> =
            withContext(Dispatchers.IO) {
                val topSongs = database.songs(SongSortType.PLAY_TIME, descending = true).first().take(30)
                val recentSongs = database.songs(SongSortType.CREATE_DATE, descending = true).first().take(20)

                val combined =
                    buildList {
                        addAll(topSongs.take(15))
                        addAll(recentSongs.take(10))
                        addAll(topSongs.drop(15))
                        addAll(recentSongs.drop(10))
                    }.distinctBy { it.id }

                combined.ifEmpty {
                    database.songs(SongSortType.CREATE_DATE, descending = true).first().take(20)
                }
            }

        suspend fun fetchDiscoverCards(limit: Int = 30): List<SongItem> =
            withContext(Dispatchers.IO) {
                val candidateSongs = mutableMapOf<String, SongItem>()
                val seeds = getHybridSeedSongs()

                if (seeds.isNotEmpty()) {
                    val sampleSeeds = seeds.shuffled().take(4)
                    for (seed in sampleSeeds) {
                        if (candidateSongs.size >= limit) break
                        runCatching {
                            val nextResult = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()
                            val items = nextResult?.items.orEmpty()
                            for (item in items) {
                                if (candidateSongs.size >= limit) break
                                if (item.id != seed.id && item.isOfficialTrack()) {
                                    candidateSongs[item.id] = item
                                }
                            }
                        }.onFailure { e -> Timber.w(e, "Failed fetching YTM radio for seed ${seed.id}") }
                    }

                    if (LastFM.isInitialized() && candidateSongs.size < limit) {
                        val lastFmSeed = sampleSeeds.firstOrNull()
                        if (lastFmSeed != null && lastFmSeed.artists.isNotEmpty()) {
                            val artistName = lastFmSeed.artists.first().name
                            val trackTitle = lastFmSeed.song.title
                            runCatching {
                                val similarRes = LastFM.getSimilarTracks(artistName, trackTitle, limit = 10).getOrNull()
                                val similarItems = similarRes?.similartracks?.track.orEmpty()

                                for (sim in similarItems) {
                                    if (candidateSongs.size >= limit) break
                                    val query = "${sim.artist.name} ${sim.name}".trim()
                                    if (query.isNotEmpty()) {
                                        val searchRes = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                                        val match = searchRes?.items?.filterIsInstance<SongItem>()?.firstOrNull { it.isOfficialTrack() }
                                        if (match != null) {
                                            candidateSongs[match.id] = match
                                        }
                                    }
                                }
                            }.onFailure { e -> Timber.w(e, "Failed Last.fm getSimilar enrichment") }
                        }
                    }
                }

                if (candidateSongs.size < limit) {
                    runCatching {
                        val query = "Hits ${Year.now().value}"
                        val searchRes = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        val popularItems = searchRes?.items.orEmpty().filterIsInstance<SongItem>()
                        for (item in popularItems) {
                            if (candidateSongs.size >= limit) break
                            if (item.isOfficialTrack()) {
                                candidateSongs[item.id] = item
                            }
                        }
                    }.onFailure { e -> Timber.w(e, "Failed fallback YTM search") }
                }

                candidateSongs.values.shuffled().take(limit)
            }

        /**
         * Validates whether a candidate song is an official discography release.
         */
        private fun SongItem.isOfficialTrack(): Boolean {
            val validDuration = duration == null || duration in 40..600
            val lowerTitle = title.lowercase()
            val currentAlbum = album
            val lowerAlbum = currentAlbum?.name?.lowercase().orEmpty()

            val unreleasedKeywords = listOf(
                "unreleased", "rmx", "remix", "leak", "leaked", "prod.", "prod by",
                "prod ", "edit", "slowed", "reverb", "acapella", "instrumental",
                "bootleg", "snippet", "type beat", "8d audio", "tiktok", "nightcore",
                "bass boosted", "fanmade", "unofficial", "cover", "mashup"
            )

            val containsUnreleasedKeyword = unreleasedKeywords.any { kw ->
                lowerTitle.contains(kw) || lowerAlbum.contains(kw)
            }

            return validDuration && !containsUnreleasedKeyword
        }



        /**
         * Saves a swiped card into the current week's Discover Playlist.
         */
        suspend fun saveCardToWeeklyPlaylist(song: SongItem, toggleLiked: Boolean = false) =
            withContext(Dispatchers.IO) {
                val weeklyPlaylist = getOrCreateWeeklyPlaylist()
                val media = song.toMediaMetadata()
                database.insert(media)
                database.addSongToPlaylist(weeklyPlaylist, listOf(song.id))

                if (toggleLiked) {
                    val entity = media.toSongEntity()
                    database.query {
                        update(entity.localToggleLike())
                    }
                }
            }
    }
