/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.storage

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.datasource.cache.Cache
import coil3.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.GitHubContributorsEtagKey
import moe.rukamori.archivetune.constants.GitHubContributorsJsonKey
import moe.rukamori.archivetune.constants.GitHubContributorsLastCheckedAtKey
import moe.rukamori.archivetune.constants.StorageFolderDisplayNameKey
import moe.rukamori.archivetune.constants.StorageFolderIdKey
import moe.rukamori.archivetune.constants.StorageFolderPathKey
import moe.rukamori.archivetune.constants.StorageFolderTreeUriKey
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.utils.ArtworkStorage
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import java.io.File
import javax.inject.Inject

enum class StorageFolderKind(
    val defaultDirectoryName: String,
) {
    SONG_CACHE(defaultDirectoryName = "exoplayer"),
    DOWNLOADS(defaultDirectoryName = "download"),
    IMAGE_CACHE(defaultDirectoryName = "coil"),
    CANVAS_CACHE(defaultDirectoryName = "canvas"),
    ARTWORK_CACHE(defaultDirectoryName = "artwork"),
}

@Immutable
data class StorageFolderSelection(
    val selectedOption: StorageLocationOption,
    val options: StorageLocationOptions,
)

@Immutable
data class StorageLocationOptions(
    private val values: List<StorageLocationOption>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): StorageLocationOption = values[index]

    fun firstOrNull(predicate: (StorageLocationOption) -> Boolean): StorageLocationOption? =
        values.firstOrNull(predicate)

    fun forEach(action: (StorageLocationOption) -> Unit) {
        values.forEach(action)
    }
}

@Immutable
data class StorageLocationOption(
    val id: String,
    val kind: StorageLocationKind,
    val volumeLabel: String?,
    val rootPath: String,
    val availableBytes: Long,
    val isSelected: Boolean,
)

enum class StorageLocationKind {
    INTERNAL,
    REMOVABLE,
}

@Immutable
data class StorageMigrationProgress(
    val phase: StorageMigrationPhase,
    val percent: Int,
)

enum class StorageMigrationPhase {
    CACHE,
    DOWNLOADS,
}

sealed interface StorageFolderUpdateResult {
    data object Success : StorageFolderUpdateResult
    data object InvalidTree : StorageFolderUpdateResult
    data object UnsupportedProvider : StorageFolderUpdateResult
    data object NotWritable : StorageFolderUpdateResult
}

enum class StorageCacheKind {
    SONGS,
    DOWNLOADS,
    IMAGES,
    CANVAS,
}

sealed interface StorageCacheClearResult {
    data object Success : StorageCacheClearResult
    data object Failed : StorageCacheClearResult
}

object StorageRestartScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var restartJob: Job? = null

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(AppRestartDelayMillis)
            appContext.restartApp()
        }
    }
}

class ObserveStorageFoldersUseCase
@Inject
constructor(
    private val repository: StorageLocationRepository,
) {
    operator fun invoke(): Flow<StorageFolderSelection> = repository.selection
}

class SetStorageFolderUseCase
@Inject
constructor(
    private val repository: StorageLocationRepository,
) {
    suspend operator fun invoke(
        optionId: String,
        onProgress: suspend (StorageMigrationProgress) -> Unit,
    ): StorageFolderUpdateResult =
        repository.setStorageLocationAndMoveCache(optionId, onProgress)
}

class ClearStorageCacheUseCase
@Inject
constructor(
    private val repository: StorageLocationRepository,
) {
    suspend operator fun invoke(kind: StorageCacheKind): StorageCacheClearResult =
        repository.clearCache(kind)
}

class StorageLocationRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache,
    private val downloadUtil: DownloadUtil,
) {
    val selection: Flow<StorageFolderSelection> =
        context.dataStore.data.map { preferences ->
            preferences.selectionFor(context.storageLocationOptions(preferences))
        }

    suspend fun clearCache(kind: StorageCacheKind): StorageCacheClearResult =
        withContext(Dispatchers.IO) {
            try {
                val cleared = when (kind) {
                    StorageCacheKind.SONGS -> clearReleasedMediaCache(playerCache, StorageFolderKind.SONG_CACHE)
                    StorageCacheKind.DOWNLOADS -> clearDownloads()
                    StorageCacheKind.IMAGES -> clearImageCache()
                    StorageCacheKind.CANVAS -> clearCanvasCache()
                }
                if (cleared) StorageCacheClearResult.Success else StorageCacheClearResult.Failed
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                StorageCacheClearResult.Failed
            }
        }

    suspend fun setStorageLocationAndMoveCache(
        optionId: String,
        onProgress: suspend (StorageMigrationProgress) -> Unit,
    ): StorageFolderUpdateResult = withContext(Dispatchers.IO) {
        val preferencesSnapshot = context.dataStore.data.first()
        val previousUri = preferencesSnapshot[StorageFolderTreeUriKey]
        val options = context.storageLocationOptions(preferencesSnapshot)
        val selectedOption = options.firstOrNull { option -> option.id == optionId }
            ?: return@withContext StorageFolderUpdateResult.UnsupportedProvider
        if (selectedOption.kind == StorageLocationKind.INTERNAL) {
            return@withContext resetFolderAndMoveCache(onProgress)
        }

        val targetRoot = File(selectedOption.rootPath).canonicalFile
        if (!targetRoot.ensureStorageRoot()) return@withContext StorageFolderUpdateResult.NotWritable
        val movedCache = moveAllCacheDirectories(preferencesSnapshot) { kind ->
            targetRoot.resolve(kind.defaultDirectoryName)
        }.withProgress(onProgress)
        if (!movedCache) {
            return@withContext StorageFolderUpdateResult.NotWritable
        }
        context.dataStore.edit { preferences ->
            preferences[StorageFolderIdKey] = selectedOption.id
            preferences[StorageFolderPathKey] = targetRoot.canonicalPath
            preferences[StorageFolderDisplayNameKey] = selectedOption.id
            preferences.remove(StorageFolderTreeUriKey)
        }
        context.storageLocationPreferences()
            .edit()
            .putString(StorageRootPathMirrorKey, targetRoot.canonicalPath)
            .apply()
        releasePersistedPermission(previousUri, replacementUri = null)
        StorageRestartScheduler.schedule(context)
        StorageFolderUpdateResult.Success
    }

    suspend fun resetFolderAndMoveCache(
        onProgress: suspend (StorageMigrationProgress) -> Unit,
    ): StorageFolderUpdateResult = withContext(Dispatchers.IO) {
        val preferencesSnapshot = context.dataStore.data.first()
        val previousUri = preferencesSnapshot[StorageFolderTreeUriKey]
        val movedCache = moveAllCacheDirectories(preferencesSnapshot) { kind ->
            context.defaultCacheDirectory(kind)
        }.withProgress(onProgress)
        if (!movedCache) {
            return@withContext StorageFolderUpdateResult.NotWritable
        }
        context.dataStore.edit { preferences ->
            preferences.remove(StorageFolderIdKey)
            preferences.remove(StorageFolderTreeUriKey)
            preferences.remove(StorageFolderPathKey)
            preferences.remove(StorageFolderDisplayNameKey)
        }
        context.storageLocationPreferences()
            .edit()
            .remove(StorageRootPathMirrorKey)
            .apply()
        releasePersistedPermission(previousUri, replacementUri = null)
        StorageRestartScheduler.schedule(context)
        StorageFolderUpdateResult.Success
    }

    private fun Preferences.selectionFor(
        options: StorageLocationOptions,
    ): StorageFolderSelection {
        val configuredId = this[StorageFolderIdKey]?.takeIf(String::isNotBlank)
        val selectedOption = options.firstOrNull { option -> option.id == configuredId }
            ?: options.firstOrNull { option -> option.isSelected }
            ?: options.firstOrNull { option -> option.kind == StorageLocationKind.INTERNAL }
            ?: StorageLocationOption(
                id = InternalStorageOptionId,
                kind = StorageLocationKind.INTERNAL,
                volumeLabel = null,
                rootPath = context.defaultStorageRootDirectory().absolutePath,
                availableBytes = context.defaultStorageRootDirectory().usableSpace,
                isSelected = true,
            )
        return StorageFolderSelection(
            selectedOption = selectedOption,
            options = options,
        )
    }

    private fun releasePersistedPermission(previousUri: String?, replacementUri: String?) {
        if (previousUri.isNullOrBlank() || previousUri == replacementUri) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                previousUri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun moveAllCacheDirectories(
        preferences: Preferences,
        targetDirectory: (StorageFolderKind) -> File,
    ): StorageMigrationPlan =
        StorageMigrationPlan(
            cacheDirectories = StorageFolderKind.entries
                .filterNot { kind -> kind == StorageFolderKind.DOWNLOADS }
                .map { kind ->
                    StorageDirectoryMove(
                        source = activeCacheDirectory(preferences, kind),
                        target = targetDirectory(kind),
                    )
                },
            downloadDirectories = listOf(
                StorageDirectoryMove(
                    source = activeCacheDirectory(preferences, StorageFolderKind.DOWNLOADS),
                    target = targetDirectory(StorageFolderKind.DOWNLOADS),
                ),
            ),
            cacheFiles = listOf(
                StorageFileMove(
                    source = context.filesDir.resolve(CanvasArtworkCacheFileName),
                    target = targetDirectory(StorageFolderKind.CANVAS_CACHE).resolve(CanvasArtworkCacheFileName),
                ),
                StorageFileMove(
                    source = context.filesDir.resolve(SavedArtworkCacheFileName),
                    target = targetDirectory(StorageFolderKind.ARTWORK_CACHE).resolve(SavedArtworkCacheFileName),
                ),
            ),
        )

    private fun activeCacheDirectory(preferences: Preferences, kind: StorageFolderKind): File {
        val configuredPath = preferences[StorageFolderPathKey]?.takeIf(String::isNotBlank)
        val configuredDirectory = configuredPath?.let(::File)?.resolve(kind.defaultDirectoryName)
        return configuredDirectory ?: context.defaultCacheDirectory(kind)
    }

    private fun releaseCachesForMigration() {
        runCatching { playerCache.release() }
        runCatching { downloadCache.release() }
    }

    private fun clearReleasedMediaCache(
        cache: Cache,
        folderKind: StorageFolderKind,
    ): Boolean {
        runCatching {
            cache.keys.toList().forEach(cache::removeResource)
        }
        val released = runCatching { cache.release() }.isSuccess
        return released && clearCacheDirectory(folderKind)
    }

    private fun clearDownloads(): Boolean =
        runCatching {
            downloadUtil.downloadManager.removeAllDownloads()
        }.isSuccess

    private fun clearCanvasCache(): Boolean {
        val memoryAndIndexCleared = CanvasArtworkPlaybackCache.clearAndPersist()
        return memoryAndIndexCleared && clearCacheDirectory(StorageFolderKind.CANVAS_CACHE)
    }

    private suspend fun clearImageCache(): Boolean {
        val imageLoader = context.imageLoader
        val diskCacheCleared = runCatching {
            val diskCache = imageLoader.diskCache
                ?: return@runCatching clearCacheDirectory(StorageFolderKind.IMAGE_CACHE)
            imageLoader.memoryCache?.clear()
            diskCache.clear()
            diskCache.size == 0L
        }.getOrDefault(false)
        val artworkCacheCleared = clearCacheDirectory(StorageFolderKind.ARTWORK_CACHE)
        val artworkCleared = ArtworkStorage.clear(context)
        val cacheCleared = diskCacheCleared && artworkCacheCleared && artworkCleared
        val contributorCacheCleared = if (cacheCleared) clearGitHubContributorCache() else false
        return cacheCleared && contributorCacheCleared
    }

    private suspend fun clearGitHubContributorCache(): Boolean =
        runCatching {
            context.dataStore.edit { preferences ->
                preferences.remove(GitHubContributorsEtagKey)
                preferences.remove(GitHubContributorsJsonKey)
                preferences.remove(GitHubContributorsLastCheckedAtKey)
            }
        }.isSuccess

    private fun clearCacheDirectory(kind: StorageFolderKind): Boolean {
        val directory = cacheDirectory(context, kind)
        val parent = directory.parentFile ?: return false
        val trashDirectory = parent.resolve("${directory.name}.delete-${System.currentTimeMillis()}")
        val cleared = runCatching {
            if (!directory.exists()) return@runCatching true
            if (directory.renameTo(trashDirectory)) {
                trashDirectory.deleteTreeSafely(deleteRoot = true)
            } else {
                directory.deleteTreeSafely(deleteRoot = false)
            }
        }.getOrDefault(false)
        return cleared && directory.ensureWritableDirectory() && directory.isDirectoryEmpty()
    }

    private suspend fun StorageMigrationPlan.withProgress(
        onProgress: suspend (StorageMigrationProgress) -> Unit,
    ): Boolean =
        try {
            releaseCachesForMigration()
            moveMigrationPhase(
                phase = StorageMigrationPhase.CACHE,
                directories = cacheDirectories,
                files = cacheFiles,
                onProgress = onProgress,
            )
            moveMigrationPhase(
                phase = StorageMigrationPhase.DOWNLOADS,
                directories = downloadDirectories,
                files = emptyList(),
                onProgress = onProgress,
            )
            true
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            false
        }

    private suspend fun moveMigrationPhase(
        phase: StorageMigrationPhase,
        directories: List<StorageDirectoryMove>,
        files: List<StorageFileMove>,
        onProgress: suspend (StorageMigrationProgress) -> Unit,
    ) {
        val totalBytes = directories.sumOf { move -> move.source.migrationByteCount(move.target) } +
            files.sumOf { move -> move.source.migrationByteCount(move.target) }
        val progressReporter = StorageProgressReporter(
            phase = phase,
            totalBytes = totalBytes,
            onProgress = onProgress,
        )
        progressReporter.emit(0L)
        val buffer = ByteArray(StorageCopyBufferSizeBytes)
        var movedBytes = 0L
        directories.forEach { move ->
            movedBytes = moveCacheDirectory(
                source = move.source,
                target = move.target,
                movedBytes = movedBytes,
                progressReporter = progressReporter,
                buffer = buffer,
            )
        }
        files.forEach { move ->
            movedBytes = moveFile(
                source = move.source,
                target = move.target,
                movedBytes = movedBytes,
                progressReporter = progressReporter,
                buffer = buffer,
            )
        }
        progressReporter.emit(totalBytes)
    }

    private suspend fun moveCacheDirectory(
        source: File,
        target: File,
        movedBytes: Long,
        progressReporter: StorageProgressReporter,
        buffer: ByteArray,
    ): Long {
        val canonicalSource = source.canonicalFile
        val canonicalTarget = target.canonicalFile
        var currentMovedBytes = movedBytes
        if (canonicalSource == canonicalTarget) {
            canonicalTarget.ensureWritableDirectory()
            return currentMovedBytes
        }
        if (!canonicalSource.exists()) {
            canonicalTarget.ensureWritableDirectory()
            return currentMovedBytes
        }
        canonicalTarget.deleteRecursively()
        canonicalTarget.parentFile?.mkdirs()
        canonicalSource.walkTopDown()
            .filter { file -> file.isDirectory }
            .forEach { directory ->
                canonicalTarget
                    .resolve(directory.relativeTo(canonicalSource).path)
                    .mkdirs()
            }
        canonicalSource.walkTopDown()
            .filter { file -> file.isFile }
            .forEach { file ->
                currentMovedBytes = copyFileWithProgress(
                    source = file,
                    target = canonicalTarget.resolve(file.relativeTo(canonicalSource).path),
                    movedBytes = currentMovedBytes,
                    progressReporter = progressReporter,
                    buffer = buffer,
                )
            }
        canonicalSource.deleteRecursively()
        return currentMovedBytes
    }

    private suspend fun moveFile(
        source: File,
        target: File,
        movedBytes: Long,
        progressReporter: StorageProgressReporter,
        buffer: ByteArray,
    ): Long {
        val canonicalSource = source.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (canonicalSource == canonicalTarget || !canonicalSource.exists()) return movedBytes
        val currentMovedBytes = copyFileWithProgress(
            source = canonicalSource,
            target = canonicalTarget,
            movedBytes = movedBytes,
            progressReporter = progressReporter,
            buffer = buffer,
        )
        canonicalSource.delete()
        return currentMovedBytes
    }

    companion object {
        fun cacheDirectory(context: Context, kind: StorageFolderKind): File {
            val configuredPath = context.storageLocationPreferences()
                .getString(StorageRootPathMirrorKey, null)
                ?.takeIf(String::isNotBlank)
                ?: PreferenceStore.get(StorageFolderPathKey)?.takeIf(String::isNotBlank)
            val configuredRootDirectory = configuredPath?.let { path ->
                context.allowedConfiguredStorageRoot(path)
            }
            val configuredDirectory = configuredRootDirectory?.resolve(kind.defaultDirectoryName)
            return configuredDirectory
                ?.takeIf { it.ensureWritableDirectory() }
                ?: context.defaultCacheDirectory(kind)
        }

        fun cacheFile(context: Context, kind: StorageFolderKind, fileName: String): File =
            cacheDirectory(context, kind).resolve(fileName)
    }
}

private fun Context.defaultCacheDirectory(kind: StorageFolderKind): File =
    when (kind) {
        StorageFolderKind.IMAGE_CACHE -> cacheDir.resolve(kind.defaultDirectoryName)
        else -> filesDir.resolve(kind.defaultDirectoryName)
    }

private fun Context.defaultStorageRootDirectory(): File =
    filesDir

private fun Context.restartApp() {
    packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
    }
    kotlin.system.exitProcess(0)
}

private fun Context.storageLocationPreferences() =
    getSharedPreferences(StorageLocationPreferencesName, Context.MODE_PRIVATE)

private fun Context.allowedConfiguredStorageRoot(path: String): File? {
    val configuredRoot = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
    val appRoot = defaultStorageRootDirectory().canonicalFile
    if (configuredRoot == appRoot) return appRoot
    return getExternalFilesDirs(null)
        .filterNotNull()
        .map { directory ->
            directory
                .resolve(ExternalStorageRootDirectoryName)
                .canonicalFile
        }
        .firstOrNull { root -> root == configuredRoot }
}

private fun Context.storageLocationOptions(preferences: Preferences): StorageLocationOptions {
    val selectedPath = preferences[StorageFolderPathKey]?.takeIf(String::isNotBlank)
    val appRoot = defaultStorageRootDirectory().canonicalFile
    val internalOption = appRoot.toStorageLocationOption(
        id = InternalStorageOptionId,
        kind = StorageLocationKind.INTERNAL,
        volumeLabel = null,
        selectedPath = selectedPath,
    )
    val externalOptions = getExternalFilesDirs(null)
        .filterNotNull()
        .mapIndexedNotNull { index, directory ->
            if (directory.isPrimaryExternalStorage()) return@mapIndexedNotNull null
            directory
                .resolve(ExternalStorageRootDirectoryName)
                .canonicalFile
                .takeIf { it.ensureWritableDirectory() }
                ?.toStorageLocationOption(
                    id = ExternalStorageOptionIdPrefix + directory.storageVolumeRootPath().orEmpty().ifBlank { index.toString() },
                    kind = StorageLocationKind.REMOVABLE,
                    volumeLabel = directory.storageVolumeLabel(),
                    selectedPath = selectedPath,
                )
        }
        .distinctBy { option -> option.rootPath }
    return StorageLocationOptions(listOf(internalOption) + externalOptions)
}

private fun File.ensureWritableDirectory(): Boolean =
    runCatching {
        if (exists() && !isDirectory) return@runCatching false
        if (!exists() && !mkdirs()) return@runCatching false
        val probe = File(this, ".archivetune-storage-probe")
        probe.writeText("ok")
        probe.delete()
    }.isSuccess

private fun File.ensureStorageRoot(): Boolean =
    ensureWritableDirectory() &&
        StorageFolderKind.entries.all { kind ->
            resolve(kind.defaultDirectoryName).ensureWritableDirectory()
        }

private suspend fun copyFileWithProgress(
    source: File,
    target: File,
    movedBytes: Long,
    progressReporter: StorageProgressReporter,
    buffer: ByteArray,
): Long {
    currentCoroutineContext().ensureActive()
    target.parentFile?.mkdirs()
    var currentMovedBytes = movedBytes
    source.inputStream().use { inputStream ->
        target.outputStream().use { outputStream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = inputStream.read(buffer)
                if (read < 0) return@use
                outputStream.write(buffer, 0, read)
                currentMovedBytes += read
                progressReporter.emit(currentMovedBytes)
            }
        }
    }
    return currentMovedBytes
}

private fun File.migrationByteCount(target: File): Long {
    val canonicalSource = canonicalFile
    val canonicalTarget = target.canonicalFile
    if (canonicalSource == canonicalTarget || !canonicalSource.exists()) return 0L
    if (canonicalSource.isFile) return canonicalSource.length()
    return canonicalSource
        .walkTopDown()
        .filter { file -> file.isFile }
        .sumOf { file -> file.length() }
}

private fun File.deleteTreeSafely(deleteRoot: Boolean): Boolean =
    runCatching {
        if (!exists()) return@runCatching true
        val directories = ArrayList<File>()
        val stack = ArrayDeque<File>()
        var deleted = true
        stack.add(this)
        while (stack.isNotEmpty()) {
            val file = stack.removeLast()
            if (file.isDirectory) {
                directories += file
                file.listFiles()?.forEach { child -> stack.add(child) }
            } else {
                if (!runCatching { file.delete() || !file.exists() }.getOrDefault(false)) {
                    deleted = false
                }
            }
        }
        directories.asReversed().forEach { directory ->
            if (deleteRoot || directory != this) {
                if (!runCatching { directory.delete() || !directory.exists() }.getOrDefault(false)) {
                    deleted = false
                }
            }
        }
        deleted
    }.getOrDefault(false)

private fun File.isDirectoryEmpty(): Boolean =
    runCatching {
        val children = listFiles() ?: return@runCatching false
        children.isEmpty()
    }.getOrDefault(false)

private class StorageProgressReporter(
    private val phase: StorageMigrationPhase,
    private val totalBytes: Long,
    private val onProgress: suspend (StorageMigrationProgress) -> Unit,
) {
    private var lastPercent = -1

    suspend fun emit(movedBytes: Long) {
        currentCoroutineContext().ensureActive()
        val percent = if (totalBytes <= 0L) {
            100
        } else {
            ((movedBytes.coerceIn(0L, totalBytes) * 100L) / totalBytes).toInt()
        }
        if (percent == lastPercent) return
        lastPercent = percent
        onProgress(StorageMigrationProgress(phase = phase, percent = percent))
    }
}

private data class StorageMigrationPlan(
    val cacheDirectories: List<StorageDirectoryMove>,
    val downloadDirectories: List<StorageDirectoryMove>,
    val cacheFiles: List<StorageFileMove>,
)

private data class StorageDirectoryMove(
    val source: File,
    val target: File,
)

private data class StorageFileMove(
    val source: File,
    val target: File,
)

private fun File.toStorageLocationOption(
    id: String,
    kind: StorageLocationKind,
    volumeLabel: String?,
    selectedPath: String?,
): StorageLocationOption =
    StorageLocationOption(
        id = id,
        kind = kind,
        volumeLabel = volumeLabel,
        rootPath = canonicalPath,
        availableBytes = usableSpace,
        isSelected = selectedPath?.let { configuredPath ->
            runCatching { File(configuredPath).canonicalPath == canonicalPath }.getOrDefault(false)
        } ?: (kind == StorageLocationKind.INTERNAL),
    )

private fun File.isPrimaryExternalStorage(): Boolean =
    storageVolumeRootPath() == "/storage/emulated/0"

private fun File.storageVolumeLabel(): String? =
    storageVolumeRootPath()
        ?.substringAfterLast('/')
        ?.takeIf { label -> label.isNotBlank() && label != "0" }

private fun File.storageVolumeRootPath(): String? {
    val path = runCatching { canonicalPath }.getOrDefault(absolutePath)
        .replace('\\', '/')
        .trimEnd('/')
    val segments = path.trim('/').split('/')
    return when {
        segments.size >= 3 && segments[0] == "mnt" && segments[1] == "media_rw" -> "/storage/${segments[2]}"
        segments.size >= 3 && segments[0] == "storage" && segments[1] == "emulated" -> "/storage/emulated/${segments[2]}"
        segments.size >= 2 && segments[0] == "storage" && segments[1].isNotBlank() -> "/storage/${segments[1]}"
        else -> null
    }
}

private const val StorageLocationPreferencesName = "storage_locations"
private const val StorageRootPathMirrorKey = "storage_root_path"
private const val InternalStorageOptionId = "internal"
private const val ExternalStorageOptionIdPrefix = "external:"
private const val ExternalStorageRootDirectoryName = "ArchiveTune"
private const val AppRestartDelayMillis = 3_000L
private const val StorageCopyBufferSizeBytes = 64 * 1024
private const val CanvasArtworkCacheFileName = "canvas_artwork_cache.json"
private const val SavedArtworkCacheFileName = "archivetune_saved_artworks.json"
