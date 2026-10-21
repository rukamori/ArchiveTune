/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaveformRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val downloadUtil: DownloadUtil,
    ) {
        suspend fun getOrGenerate(
            mediaId: String,
            durationMs: Int,
        ): ByteArray =
            withContext(Dispatchers.IO) {
                val directory = context.cacheDir.resolve(WaveformCacheDirectory)
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Unable to create waveform cache directory")
                }
                val cacheFile = directory.resolve(mediaId.toWaveformCacheName())
                if (cacheFile.isFile && cacheFile.length() == AudioWaveformAnalyzer.BinCount.toLong()) {
                    return@withContext cacheFile.readBytes()
                }

                val waveform = downloadUtil.generateWaveform(mediaId, durationMs)
                val temporaryFile = directory.resolve("${cacheFile.name}.tmp")
                try {
                    temporaryFile.writeBytes(waveform)
                    if (!temporaryFile.renameTo(cacheFile)) {
                        throw IOException("Unable to save generated waveform")
                    }
                } finally {
                    if (temporaryFile.exists() && !temporaryFile.delete()) {
                        Timber.w("Unable to remove temporary waveform data for %s", mediaId)
                    }
                }
                waveform
            }

        private companion object {
            const val WaveformCacheDirectory = "song-waveforms-v3"
        }
    }

private fun String.toWaveformCacheName(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
