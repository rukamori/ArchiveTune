package moe.rukamori.archivetune.sources

import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import java.util.concurrent.atomic.AtomicReference

class SourceDownloader(
    private val request: DownloadRequest,
    private val create: (DownloadRequest) -> Pair<Downloader, SourceSelection?>,
    private val records: SourceDownloadRepository,
    private val removeContent: (String) -> Unit,
) : Downloader {
    private val workerLock = Any()
    private val active = AtomicReference<Downloader?>()
    @Volatile private var cancelled = false
    @Volatile private var worker: Thread? = null

    override fun download(progressListener: Downloader.ProgressListener?) {
        synchronized(workerLock) { worker = Thread.currentThread() }
        try {
            if (cancelled) throw InterruptedException()
            val (downloader, selection) = create(request)
            active.set(downloader)
            if (cancelled) {
                downloader.cancel()
                throw InterruptedException()
            }
            downloader.download(progressListener)
            if (selection != null) records.write(request.id, SourceDownloadRecord(selection = selection, complete = true))
        } finally {
            active.set(null)
            synchronized(workerLock) { worker = null }
        }
    }

    override fun cancel() {
        cancelled = true
        active.get()?.cancel()
        synchronized(workerLock) { worker?.interrupt() }
    }

    override fun remove() {
        removeContent(request.id)
    }
}
