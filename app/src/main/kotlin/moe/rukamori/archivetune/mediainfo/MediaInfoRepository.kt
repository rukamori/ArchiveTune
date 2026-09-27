/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.mediainfo

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube

class MediaInfoRepository @Inject constructor(private val database: MusicDatabase) {
    fun observeLocal(videoId: String): Flow<LocalMediaInfo> =
        combine(database.song(videoId), database.format(videoId)) { song, format ->
            LocalMediaInfo(
                title = song?.title,
                artists = song?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name },
                artwork = song?.thumbnailUrl,
                isLocal = song?.song?.isLocal == true,
                format = format?.let {
                    MediaInfoFormat(
                        itag = it.itag,
                        mimeType = it.mimeType,
                        codecs = it.codecs,
                        bitrate = it.bitrate,
                        sampleRate = it.sampleRate,
                        loudnessDb = it.loudnessDb,
                        contentLength = it.contentLength,
                    )
                },
            )
        }.flowOn(Dispatchers.IO)

    suspend fun metadata(videoId: String): MediaInfoMetadata = withContext(Dispatchers.IO) {
        val info = YouTube.getMediaMetadata(videoId)
        MediaInfoMetadata(
            title = info.title,
            author = info.author,
            artwork = info.authorThumbnail,
            description = info.description?.takeIf(String::isNotBlank),
            subscribers = info.subscribers,
        )
    }

    suspend fun statistics(videoId: String): MediaInfoStatistics = withContext(Dispatchers.IO) {
        val statistics = YouTube.getMediaStatistics(videoId)
        MediaInfoStatistics(
            views = statistics.viewCount,
            likes = statistics.likes,
            dislikes = statistics.dislikes,
        )
    }
}
