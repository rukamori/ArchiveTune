/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.modules.ModuleDownloadRepository
import moe.rukamori.archivetune.modules.ModuleMediaStorage
import moe.rukamori.archivetune.modules.ResolveModuleAudioUseCase
import java.util.concurrent.ExecutionException

internal class ModuleSelectingDownloader(
    private val request: DownloadRequest,
    private val standard: DownloaderFactory,
    private val resolver: ResolveModuleAudioUseCase,
    private val bindings: ModuleDownloadRepository,
    private val storage: ModuleMediaStorage,
    private val owner: CoroutineScope,
) : Downloader {
    private val lock = Any()
    private var delegate: Downloader? = null
    private var worker: Thread? = null
    private var cancelled = false

    override fun download(progressListener: Downloader.ProgressListener?) {
        synchronized(lock) {
            if (cancelled) throw InterruptedException()
            worker = Thread.currentThread()
        }
        try {
            val selection = await {
                val pinned = bindings.find(request.id)
                val audio = if (pinned != null) withTimeout(15_000) { resolver.refresh(pinned) } else resolver.resolve(request.id)
                if (audio != null) { bindings.put(audio); storage.record(audio) }
                audio
            }
            val downloader = selection?.let { storage.downloader(request, it) } ?: standard.createDownloader(request)
            synchronized(lock) {
                if (cancelled) { downloader.cancel(); throw InterruptedException() }
                delegate = downloader
            }
            downloader.download(progressListener)
        } finally { synchronized(lock) { worker = null; delegate = null } }
    }

    override fun cancel() {
        synchronized(lock) { cancelled = true; delegate?.cancel(); worker?.interrupt() }
    }

    override fun remove() {
        val selection = await { bindings.find(request.id) }
        if (selection == null) standard.createDownloader(request).remove()
        else { storage.remove(selection); await { bindings.remove(request.id) } }
    }

    private fun <T> await(block: suspend () -> T): T {
        val future = owner.future { block() }
        return try { future.get() }
        catch (error: InterruptedException) { future.cancel(true); Thread.currentThread().interrupt(); throw error }
        catch (error: ExecutionException) { throw error.cause ?: error }
        catch (error: CancellationException) { future.cancel(true); throw InterruptedException().apply { initCause(error) } }
    }
}
