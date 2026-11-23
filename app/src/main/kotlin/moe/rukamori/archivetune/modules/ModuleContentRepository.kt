/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ModuleTrackDetailsResult(val module: InstalledModule, val details: ModuleTrackDetails)

data class ModuleCanvasResult(val module: InstalledModule, val canvas: ModuleCanvas)

@Singleton
class ModuleContentRepository @Inject constructor(
    private val repository: ModuleRepository,
    private val metadataRepository: ModuleTrackMetadataRepository,
) {
    suspend fun providers(capability: String): List<InstalledModule> {
        val registry = repository.snapshot()
        if (!registry.enabled) return emptyList()
        return registry.modules.filter { it.enabled && it.manifest.provides(capability) }
    }

    suspend fun available(capability: String): Boolean {
        var failure: Exception? = null
        for (module in providers(capability)) {
            try {
                repository.check(module)
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        return false
    }

    suspend fun song(id: String, title: String, artists: List<String>, album: String?, durationSeconds: Int): ModuleSong? {
        val requestedTitle = title.trim()
        val requestedArtists = artists.map(String::trim).filter(String::isNotEmpty)
        val requestedAlbum = album?.trim()?.takeIf(String::isNotEmpty)
        val stored = id.takeIf(String::isNotBlank)?.let { metadataRepository.get(it) }
        val metadata = when {
            stored == null -> ModuleMatchMetadata(requestedTitle, requestedArtists, requestedAlbum, durationSeconds,
                explicit = null, eligible = true, id = id)
            requestedTitle.isEmpty() || (requestedTitle == stored.title &&
                (requestedArtists.isEmpty() || requestedArtists.joinToString() == stored.artists.joinToString())) -> stored
            else -> stored.copy(title = requestedTitle, artists = requestedArtists.ifEmpty { stored.artists },
                artistIds = if (requestedArtists.isEmpty()) stored.artistIds else emptyList(), album = requestedAlbum ?: stored.album)
        }
        if (metadata.title.isBlank() || metadata.artists.none(String::isNotBlank)) return null
        return metadata.moduleSong(id)
    }

    suspend fun lyrics(module: InstalledModule, song: ModuleSong): List<ModuleLyrics> = call(module, LYRICS_TIMEOUT_MS) {
        repository.runtime.lyrics(module.engineId, module.source, module.origin, module.configuration, song)
    }

    suspend fun metadata(song: ModuleSong): ModuleTrackDetailsResult? {
        var failure: Exception? = null
        for (module in providers(ModuleCapabilities.METADATA)) {
            try {
                val details = call(module, METADATA_TIMEOUT_MS) {
                    repository.runtime.trackMetadata(module.engineId, module.source, module.origin, module.configuration, song)
                }
                if (details != null) return ModuleTrackDetailsResult(module, details)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        return null
    }

    suspend fun canvas(module: InstalledModule, song: ModuleSong): ModuleCanvas? = call(module, CANVAS_TIMEOUT_MS) {
        repository.runtime.canvas(module.engineId, module.source, module.origin, module.configuration, song)
    }

    private suspend fun <T> call(module: InstalledModule, timeoutMs: Long, block: suspend () -> T): T {
        ModuleRepository.validateConfiguration(module.manifest, module.configuration)
        var completed = false
        val value = withTimeoutOrNull(timeoutMs) { block().also { completed = true } }
        if (!completed) throw ModuleException(ModuleFailure.TIMEOUT, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "CONTENT_TIMEOUT")
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private companion object {
        const val LYRICS_TIMEOUT_MS = 8_000L
        const val METADATA_TIMEOUT_MS = 15_000L
        const val CANVAS_TIMEOUT_MS = 20_000L
    }
}
