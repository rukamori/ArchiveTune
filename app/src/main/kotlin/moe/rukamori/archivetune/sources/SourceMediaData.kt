package moe.rukamori.archivetune.sources

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadRequest
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.models.MediaMetadata
import okhttp3.OkHttpClient

fun MediaMetadata.sourceIdentity(): TrackIdentity = TrackIdentity(title, artists.joinToString(", ") { it.name }, album?.title,
    duration.takeIf { it > 0 }, explicit, isMusicVideo || isPodcast)

fun SourceSelection.mediaItem(original: MediaItem): MediaItem = original.buildUpon()
    .setUri(audio.url).setMimeType(audio.mediaMimeType.takeIf(String::isNotBlank))
    .setCustomCacheKey(if (audio.transport == StreamTransport.PROGRESSIVE) cacheKey else null).build()

fun SourceSelection.downloadRequest(original: DownloadRequest): DownloadRequest = DownloadRequest.Builder(original.id, audio.url.toUri())
    .setMimeType(audio.mediaMimeType.takeIf(String::isNotBlank))
    .setCustomCacheKey(if (audio.transport == StreamTransport.PROGRESSIVE) cacheKey else null)
    .setData(original.data).build()

fun SourceSelection.cacheFactory(cache: Cache, client: OkHttpClient?, upstreamCache: Cache? = null, readOnly: Boolean = false, networkFactory: DataSource.Factory? = null): CacheDataSource.Factory {
    val keys = CacheKeyFactory { spec ->
        if (audio.transport == StreamTransport.PROGRESSIVE) cacheKey else "$cacheKey:${spec.uri.toString().sourceHash()}"
    }
    val network = networkFactory ?: client?.let { OkHttpDataSource.Factory(it).setDefaultRequestProperties(audio.headers) }
    val upstream = if (upstreamCache != null) CacheDataSource.Factory().setCache(upstreamCache).setCacheKeyFactory(keys)
        .setUpstreamDataSourceFactory(network) else network
    return CacheDataSource.Factory().setCache(cache).setCacheKeyFactory(keys).setUpstreamDataSourceFactory(upstream)
        .apply { if (client == null || readOnly) setCacheWriteDataSinkFactory(null) }
}

fun SourceSelection.format(mediaId: String): FormatEntity = FormatEntity(
    id = mediaId, itag = -1, mimeType = audio.mimeType, codecs = audio.codec, bitrate = audio.bitrate,
    sampleRate = audio.sampleRate, contentLength = audio.contentLength, loudnessDb = null, playbackUrl = null,
    sourceName = source.name, bitDepth = audio.bitDepth,
)
