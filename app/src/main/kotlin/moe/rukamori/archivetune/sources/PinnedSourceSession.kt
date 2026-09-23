package moe.rukamori.archivetune.sources

import androidx.core.net.toUri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolveAudioStreamUseCase
import okhttp3.OkHttpClient
import java.util.concurrent.locks.ReentrantLock

class PinnedSourceSession(
    private val original: SourceSelection,
    private val request: AudioStreamRequest,
    private val resolver: ResolveAudioStreamUseCase,
) {
    private val lock = ReentrantLock()
    private var current = original

    fun dataSourceFactory(client: OkHttpClient): DataSource.Factory = ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec ->
        lock.lockInterruptibly()
        try {
            val isRoot = spec.uri.toString() == original.audio.url || spec.uri.toString() == current.audio.url
            if (isRoot && current.audio.expiresAtMs <= System.currentTimeMillis() + 60_000) {
                current = checkNotNull(resolver.refreshExternalBlocking(current, request).external)
            }
            spec.buildUpon()
                .setUri(if (isRoot) current.audio.url.toUri() else spec.uri)
                .setHttpRequestHeaders(spec.httpRequestHeaders + current.audio.headers)
                .build()
        } finally {
            lock.unlock()
        }
    }
}
