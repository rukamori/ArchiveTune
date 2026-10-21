/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class LocalMediaIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun indexMissingAudio(scanConfig: LocalSongScanConfig? = null) {
        withContext(Dispatchers.IO) {
            val indexed = indexedPaths()
            val missing = findAudioFiles(scanConfig).filter { it.absolutePath !in indexed }.map(File::getAbsolutePath)
            if (missing.isEmpty()) return@withContext
            currentCoroutineContext().ensureActive()
            val failed = scan(missing)
            if (failed > 0) Timber.tag(LogTag).w("MediaStore did not index %d of %d audio files", failed, missing.size)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun indexedPaths(): Set<String> {
        val paths = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                cursor.getString(dataIndex)?.let(paths::add)
            }
        }
        return paths
    }

    @Suppress("DEPRECATION")
    private suspend fun findAudioFiles(scanConfig: LocalSongScanConfig? = null): List<File> {
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val config = scanConfig ?: LocalSongScanConfig()
        val includedFolders = config.sanitizedIncludedFolders
        val targetDirs =
            if (includedFolders.isNotEmpty()) {
                includedFolders
                    .map { File(root, it).canonicalFile }
                    .filter { it.toPath().startsWith(root.toPath()) && it.exists() && it.isDirectory }
                    .distinct()
            } else {
                listOf(root)
            }
        if (includedFolders.isNotEmpty() && targetDirs.isEmpty()) return emptyList()

        val files = linkedMapOf<String, File>()
        for (targetDir in targetDirs) {
            targetDir.walkTopDown()
                .onEnter { directory ->
                    (directory == targetDir || !directory.name.startsWith('.')) &&
                        config.shouldScanFolder(directory.relativeTo(root).invariantSeparatorsPath) &&
                        !File(directory, NoMediaFileName).exists()
                }
                .forEach { file ->
                    currentCoroutineContext().ensureActive()
                    if (file.isFile && file.extension.lowercase(Locale.ROOT) in IndexableExtensions && file.length() > 0L) {
                        files.putIfAbsent(file.canonicalPath, file)
                    }
                }
        }
        return files.values.toList()
    }

    private suspend fun scan(paths: List<String>): Int = suspendCancellableCoroutine { continuation ->
        val remaining = AtomicInteger(paths.size)
        val failed = AtomicInteger()
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { path, uri ->
            if (uri == null) {
                failed.incrementAndGet()
                Timber.tag(LogTag).w("Unable to index audio file: %s", path)
            }
            if (remaining.decrementAndGet() == 0 && continuation.isActive) continuation.resume(failed.get())
        }
    }

    private companion object {
        const val LogTag = "LocalMediaIndexer"
        const val NoMediaFileName = ".nomedia"
        val IndexableExtensions = setOf("aac", "amr", "flac", "m4a", "m4b", "mp3", "oga", "ogg", "opus", "wav", "3ga")
    }
}
