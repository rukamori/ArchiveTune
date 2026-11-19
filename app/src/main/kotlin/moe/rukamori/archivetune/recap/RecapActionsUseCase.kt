/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.recap

import android.graphics.Typeface
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.PlayerConnection
import javax.inject.Inject

sealed interface RecapMenu {
    data class SongMenu(val song: Song) : RecapMenu
    data class ArtistMenu(val artist: Artist) : RecapMenu
    data class AlbumMenu(val album: Album) : RecapMenu
}

class RecapActionsUseCase @Inject constructor(
    private val repository: RecapRepository,
    private val shares: RecapShareRepository,
) {
    internal fun attach(connection: PlayerConnection) = repository.attach(connection)
    internal fun detach(connection: PlayerConnection) = repository.detach(connection)
    internal fun releaseMute() = repository.releaseMute()
    internal fun toggleMute() = repository.toggleMute()
    internal fun playback() = repository.playback()

    internal suspend fun share(data: RecapData, chapter: RecapChapter, font: Typeface) =
        shares.export(data, if (data.month == null && chapter in data.chapters) chapter else RecapChapter.SUMMARY, font)

    internal suspend fun play(data: RecapData, songId: String?, title: String) {
        val songs = eligibleSongs(data)
        val index = if (songId == null) 0 else songs.indexOfFirst { it.id == songId }.also { check(it >= 0) }
        repository.play(songs, index, title, toggleCurrent = songId != null)
    }

    internal suspend fun startSoundtrack(data: RecapData, title: String) {
        repository.startSoundtrack(eligibleSongs(data), title)
    }

    private suspend fun eligibleSongs(data: RecapData): List<Song> {
        val songs = repository.songs(data.songs.map { it.id }).filter {
            !it.song.isPodcast && it.artists.none { artist -> artist.blockedAt != null }
        }
        check(songs.isNotEmpty())
        return songs
    }

    internal suspend fun menu(entry: RecapEntry): RecapMenu = when (entry.kind) {
        RecapEntryKind.SONG -> RecapMenu.SongMenu(checkNotNull(repository.song(entry.id)))
        RecapEntryKind.ARTIST -> RecapMenu.ArtistMenu(checkNotNull(repository.artist(entry.id)))
        RecapEntryKind.ALBUM -> RecapMenu.AlbumMenu(checkNotNull(repository.album(entry.id)))
    }
}
