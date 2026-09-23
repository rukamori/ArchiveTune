package moe.rukamori.archivetune.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.toMediaMetadata
import javax.inject.Inject

class SourceTrackRepository @Inject constructor(private val database: MusicDatabase) {
    suspend fun identity(mediaId: String): TrackIdentity? = withContext(Dispatchers.IO) {
        database.getSongByIdBlocking(mediaId)?.let {
            return@withContext TrackIdentity(it.song.title, it.artists.joinToString(", ") { artist -> artist.name }, it.song.albumName,
                it.song.duration.takeIf { duration -> duration > 0 }, it.song.explicit,
                it.song.isLocal || it.song.isPodcast || it.song.isMusicVideo)
        }
        val song = YouTube.queue(listOf(mediaId)).getOrThrow().firstOrNull { it.id == mediaId } ?: return@withContext null
        val metadata = song.toMediaMetadata()
        database.withTransaction { insert(metadata) }
        metadata.sourceIdentity()
    }
}
