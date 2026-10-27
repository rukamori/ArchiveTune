/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.utils.isLowDataModeActive
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleMediaStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache,
    private val database: MusicDatabase,
    private val resolver: ResolveModuleAudioUseCase,
    private val bindings: ModuleDownloadRepository,
) {
    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = ModuleNetworkPolicy.client(streaming = true).newBuilder().retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val initial = chain.call().request().url
            val request = chain.request()
            chain.proceed(if (request.url.host != initial.host || request.url.port != initial.port || request.url.scheme != initial.scheme)
                request.newBuilder().removeHeader("Authorization").removeHeader("Cookie").removeHeader("Proxy-Authorization").build() else request)
        }.build()

    fun mediaSource(item: MediaItem, selection: ModuleAudioSelection, offline: Boolean): MediaSource {
        val providerItem = item.buildUpon().setUri(selection.stream.url).setMimeType(selection.stream.mimeType)
            .setCustomCacheKey(if (segmented(selection)) null else selection.cacheKey).build()
        return DefaultMediaSourceFactory(context).setDataSourceFactory(cacheFactory(selection, download = offline, offline = offline))
            .createMediaSource(providerItem)
    }

    fun downloader(request: DownloadRequest, selection: ModuleAudioSelection): Downloader {
        val providerRequest = DownloadRequest.Builder(request.id, android.net.Uri.parse(selection.stream.url))
            .setMimeType(selection.stream.mimeType).setData(request.data)
            .setCustomCacheKey(if (segmented(selection)) null else selection.cacheKey).build()
        return DefaultDownloaderFactory(cacheFactory(selection, download = true, offline = false), Executor { it.run() })
            .createDownloader(providerRequest)
    }

    fun remove(selection: ModuleAudioSelection) {
        downloadCache.keys.filter { it == selection.cacheKey || it.startsWith(selection.cacheKey + ":") }.forEach(downloadCache::removeResource)
    }

    suspend fun record(selection: ModuleAudioSelection) = withContext(Dispatchers.IO) {
        database.withTransaction {
            upsert(FormatEntity(selection.originalId, -1, selection.stream.mimeType, selection.stream.codecs,
                selection.stream.bitrate, selection.stream.sampleRate, selection.stream.contentLength, null, null, null,
                listOf(selection.module.manifest.name, selection.stream.upstreamProvider).filter(String::isNotBlank)
                    .distinct().joinToString(" \u00b7 "), selection.module.id, selection.stream.variant))
        }
    }

    private fun cacheFactory(selection: ModuleAudioSelection, download: Boolean, offline: Boolean): CacheDataSource.Factory {
        val session = StreamSession(selection, download && !offline)
        return CacheDataSource.Factory().setCache(if (download) downloadCache else playerCache)
            .setCacheKeyFactory(CacheKeyFactory { dataSpec ->
                if (!segmented(selection)) selection.cacheKey else {
                    if (session.isRoot(dataSpec.uri.toString())) session.manifestCacheKey(renew = !offline) else {
                        val hash = MessageDigest.getInstance("SHA-256").digest(dataSpec.uri.toString().toByteArray())
                            .joinToString("") { "%02x".format(it.toInt() and 255) }
                        "${selection.cacheKey}:$hash"
                    }
                }
            }).apply {
                if (offline) { setUpstreamDataSourceFactory(null); setCacheWriteDataSinkFactory(null) }
                else {
                    val permitted = selection.stream.allowedDomains.takeIf { it.isNotEmpty() }?.let { domains ->
                        val policy = ModuleNetworkPermissions(domains)
                        client.newBuilder().cookieJar(moduleStreamCookieJar(context, selection.module.id)).addNetworkInterceptor { chain ->
                            try { policy.requireAllowed(chain.request().url.toString()) }
                            catch (error: ModuleException) { throw java.io.IOException("Module network permission denied", error) }
                            chain.proceed(chain.request())
                        }.build()
                    } ?: client
                    val network = OkHttpDataSource.Factory(permitted)
                    setUpstreamDataSourceFactory(ResolvingDataSource.Factory(DataSource.Factory {
                        InlineManifestDataSource(network, session::manifest)
                    }, session::resolve))
                }
                if (!download && context.isLowDataModeActive()) setCacheWriteDataSinkFactory(null)
            }
    }

    private inner class StreamSession(private val initial: ModuleAudioSelection, private val persist: Boolean) {
        @Volatile private var current = initial
        private val lock = Any()
        private var refresh: CompletableFuture<ModuleAudioSelection>? = null
        private var consumers = 0

        fun isRoot(url: String): Boolean = url == initial.stream.url || url == current.stream.url

        fun manifest(url: String): ByteArray? = if (isRoot(url)) current.stream.inlineManifest?.toByteArray(Charsets.UTF_8) else null

        fun manifestCacheKey(renew: Boolean): String {
            val selected = if (renew && current.stream.inlineManifest != null && current.stream.expiresAtMs <= System.currentTimeMillis() + 5_000) renewed() else current
            val inline = selected.stream.inlineManifest ?: return "${initial.cacheKey}:manifest"
            val digest = MessageDigest.getInstance("SHA-256").digest(inline.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            return "${initial.cacheKey}:manifest:$digest"
        }

        fun resolve(spec: DataSpec): DataSpec {
            val root = isRoot(spec.uri.toString())
            val selected = if (root && current.stream.expiresAtMs <= System.currentTimeMillis() + 5_000) renewed() else current
            return spec.buildUpon().apply {
                if (root) setUri(selected.stream.url)
                setHttpRequestHeaders(spec.httpRequestHeaders + selected.stream.headers)
            }.build()
        }

        private fun renewed(): ModuleAudioSelection {
            val lease = synchronized(lock) {
                if (current.stream.expiresAtMs > System.currentTimeMillis() + 5_000) return current
                val future = refresh?.takeUnless { it.isCompletedExceptionally || it.isCancelled } ?: owner.future {
                    val next = withTimeout(15_000) { resolver.refresh(current) }
                    currentCoroutineContext().ensureActive()
                    if (persist) bindings.put(next)
                    currentCoroutineContext().ensureActive()
                    current = next
                    next
                }.also { refresh = it }
                consumers++
                future
            }
            return try { lease.get() }
            catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Module stream refresh interrupted").apply { initCause(error) }
            } catch (error: ExecutionException) {
                throw java.io.IOException("Module stream refresh failed", error.cause ?: error)
            } finally {
                synchronized(lock) {
                    consumers--
                    if (consumers == 0) {
                        if (!lease.isDone) lease.cancel(true)
                        if (refresh === lease) refresh = null
                    }
                }
            }
        }
    }

    private fun segmented(selection: ModuleAudioSelection): Boolean = selection.stream.mimeType.substringBefore(';').lowercase(java.util.Locale.ROOT) in
        listOf("application/x-mpegurl", "application/vnd.apple.mpegurl", "application/dash+xml")
}

private class InlineManifestDataSource(
    private val network: DataSource.Factory,
    private val manifest: (String) -> ByteArray?,
) : DataSource {
    private var active: DataSource? = null
    private val listeners = ArrayList<TransferListener>()
    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in listeners) { listeners.add(transferListener); active?.addTransferListener(transferListener) }
    }
    override fun open(dataSpec: DataSpec): Long {
        check(active == null)
        val source = manifest(dataSpec.uri.toString())?.let(::ByteArrayDataSource) ?: network.createDataSource()
        active = source
        listeners.forEach(source::addTransferListener)
        return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(active).read(buffer, offset, length)
    override fun getUri(): android.net.Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
    override fun close() { val source = active; active = null; source?.close() }
}
