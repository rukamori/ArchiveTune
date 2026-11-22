/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.isLocalMediaId
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ModuleMatchMetadata(
    val title: String,
    val artists: List<String>,
    val album: String?,
    val duration: Int,
    val explicit: Boolean?,
    val eligible: Boolean,
    val id: String = "",
    val artistIds: List<String?> = emptyList(),
    val albumId: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val thumbnailUrl: String? = null,
)

fun MediaMetadata.moduleMatchMetadata() = ModuleMatchMetadata(title, artists.map { it.name }, album?.title,
    duration, explicit.takeIf { it }, !id.isLocalMediaId() && !isPodcast && !isMusicVideo,
    id, artists.map { it.id }, album?.id, albumArtist, year, trackNumber, discNumber, thumbnailUrl)

internal fun ModuleMatchMetadata.moduleSong(originalId: String): ModuleSong = ModuleSong(
    id = id.ifBlank { originalId },
    title = title,
    artists = artists.mapIndexed { index, name -> ModuleSongArtist(name, artistIds.getOrNull(index)?.takeIf(String::isNotBlank)) },
    album = album?.takeIf(String::isNotBlank),
    albumId = albumId?.takeIf(String::isNotBlank),
    albumArtist = albumArtist?.takeIf(String::isNotBlank),
    durationSeconds = duration.takeIf { it > 0 },
    explicit = explicit,
    year = year?.takeIf { it > 0 },
    trackNumber = trackNumber?.takeIf { it > 0 },
    discNumber = discNumber?.takeIf { it > 0 },
    thumbnailUrl = thumbnailUrl?.takeIf(String::isNotBlank),
    query = ModuleTrackMatcher.query(this),
)

data class ModuleAudioSelection(
    val originalId: String,
    val module: InstalledModule,
    val trackId: String,
    val stream: ModuleStream,
    val cacheKey: String,
    val metadata: ModuleTrack? = null,
    val song: ModuleMatchMetadata? = null,
)

enum class ModuleCheckStatus { READY, CONFIGURATION, UNAVAILABLE, FAILED, NO_MATCH, RESOLVED, TIMEOUT }

enum class ModuleResolutionPhase { SEARCH, MATCH, STREAM }
enum class ModuleMatchRejection { NO_RESULTS, METADATA, TITLE, ARTIST, RECORDING, DURATION, EXPLICIT }

data class ModuleDiagnostic(
    val status: ModuleCheckStatus,
    val atMs: Long,
    val elapsedMs: Long,
    val failure: ModuleFailure? = null,
    val revision: String? = null,
    val phase: ModuleResolutionPhase? = null,
    val candidateCount: Int = 0,
    val rejection: ModuleMatchRejection? = null,
    val upstream: String = "",
    val variant: String = "",
    val code: String = "",
)

data class ModuleHealth(val check: ModuleDiagnostic?, val playback: ModuleDiagnostic?)

val InstalledModule.displayLabel: String get() = metadata?.name ?: manifest.displayName.ifBlank { manifest.name }

val InstalledModule.displayAuthor: String get() = metadata?.author ?: manifest.author

private val ModuleDiagnosticLabelPattern = Regex("[\\p{L}\\p{N} _().+/-]*")

fun moduleDiagnosticLabel(value: String): String = value.takeIf { it.length <= 96 && it.matches(ModuleDiagnosticLabelPattern) }.orEmpty()

@Singleton
class ModuleDiagnostics @Inject constructor() {
    private val mutableState = MutableStateFlow<Map<String, ModuleDiagnostic>>(emptyMap())
    private val mutableChecks = MutableStateFlow<Map<String, ModuleDiagnostic>>(emptyMap())
    val state = mutableState.asStateFlow()
    val checks = mutableChecks.asStateFlow()
    fun record(id: String, diagnostic: ModuleDiagnostic) {
        mutableState.update { it + (id to diagnostic) }
    }
    fun recordCheck(id: String, diagnostic: ModuleDiagnostic) {
        mutableChecks.update { it + (id to diagnostic) }
    }
}

class ModuleTrackMetadataRepository @Inject constructor(private val database: MusicDatabase) {
    suspend fun get(id: String): ModuleMatchMetadata? = withContext(Dispatchers.IO) {
        database.withTransaction {
            getSongByIdBlocking(id)?.let { item ->
                ModuleMatchMetadata(item.title, item.artists.map { it.name }, item.song.albumName, item.song.duration,
                    item.song.explicit.takeIf { it }, !item.song.isLocal && !item.song.isPodcast && !item.song.isMusicVideo,
                    item.song.id, item.artists.map { it.id }, item.song.albumId, item.song.albumArtist, item.song.year,
                    item.song.trackNumber, item.song.discNumber, item.song.thumbnailUrl)
            }
        }
    }
}

@Singleton
class ResolveModuleAudioUseCase @Inject constructor(
    private val repository: ModuleRepository,
    private val metadataRepository: ModuleTrackMetadataRepository,
    private val diagnostics: ModuleDiagnostics,
) {
    private val mutex = Mutex()
    private data class Entry(val moduleId: String, val moduleRevision: String, val metadata: ModuleMatchMetadata, val selection: ModuleAudioSelection?, val expiresAtMs: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    private val exclusions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun enabled(): Boolean = repository.state.value?.let { it.enabled && it.modules.any { module -> module.enabled } } == true

    fun peek(id: String, metadata: ModuleMatchMetadata?): ModuleAudioSelection? {
        val registry = repository.state.value ?: return null
        if (!registry.enabled) return null
        for (module in registry.modules.filter { it.enabled }) {
            if (excluded(id, module.id)) continue
            val entry = cache[cacheId(id, module.id)]?.takeIf {
                it.moduleRevision == module.revision && (metadata == null || it.metadata == metadata) && it.expiresAtMs > System.currentTimeMillis()
            } ?: return null
            if (entry.selection != null) return entry.selection
        }
        return null
    }

    suspend fun resolve(id: String, metadata: ModuleMatchMetadata? = null): ModuleAudioSelection? {
        val registry = try { repository.snapshot() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            diagnostics.record("registry", ModuleDiagnostic(ModuleCheckStatus.FAILED, System.currentTimeMillis(), 0, ModuleFailure.INVALID_CONFIGURATION))
            return null
        }
        if (!registry.enabled || registry.modules.none { it.enabled } || id.isLocalMediaId()) return null
        val target = metadata ?: metadataRepository.get(id) ?: return null
        if (!target.eligible || target.title.isBlank() || target.artists.none(String::isNotBlank)) return null
        return withTimeoutOrNull(30_000) {
            mutex.withLock {
                try {
                for (module in registry.modules.filter { it.enabled }) {
                    if (!stillCurrent(registry, module)) return@withTimeoutOrNull null
                    if (excluded(id, module.id)) continue
                    val key = cacheId(id, module.id)
                    val cached = cache[key]?.takeIf { it.moduleRevision == module.revision && it.metadata == target && it.expiresAtMs > System.currentTimeMillis() }
                    if (cached != null) {
                        if (cached.selection != null) return@withTimeoutOrNull cached.selection
                        continue
                    }
                    val started = System.currentTimeMillis()
                    var phase = ModuleResolutionPhase.SEARCH
                    var candidateCount = 0
                    try {
                        var completed = false
                        val candidate = withTimeoutOrNull(15_000) {
                            ModuleRepository.validateConfiguration(module.manifest, module.configuration)
                            val tracks = repository.runtime.search(engineId(module), module.source, module.origin,
                                ModuleTrackMatcher.query(target), module.configuration, target.moduleSong(id))
                            candidateCount = tracks.size
                            phase = ModuleResolutionPhase.MATCH
                            val evaluated = tracks.map { it to ModuleTrackMatcher.evaluate(target, it) }
                            val match = evaluated.filter { it.second.rejection == null }.maxByOrNull { it.second.score }?.first
                            val result = if (match == null) {
                                val rejection = evaluated.maxByOrNull { it.second.score }?.second?.rejection ?: ModuleMatchRejection.NO_RESULTS
                                diagnostics.record(module.id, ModuleDiagnostic(ModuleCheckStatus.NO_MATCH, System.currentTimeMillis(),
                                    System.currentTimeMillis() - started, revision = module.revision, phase = phase,
                                    candidateCount = candidateCount, rejection = rejection))
                                null
                            } else {
                                phase = ModuleResolutionPhase.STREAM
                                selection(id, module, match.id, match, target)
                            }
                            completed = true
                            result
                        }
                        if (!completed) diagnostics.record(module.id, ModuleDiagnostic(ModuleCheckStatus.TIMEOUT, System.currentTimeMillis(), System.currentTimeMillis() - started, ModuleFailure.TIMEOUT, module.revision, phase, candidateCount))
                        if (!stillCurrent(registry, module)) return@withTimeoutOrNull null
                        if (candidate != null) {
                            cache[key] = Entry(module.id, module.revision, target, candidate, candidate.stream.expiresAtMs - 60_000)
                            diagnostics.record(module.id, ModuleDiagnostic(ModuleCheckStatus.RESOLVED, System.currentTimeMillis(),
                                System.currentTimeMillis() - started, revision = module.revision, phase = phase, candidateCount = candidateCount,
                                upstream = candidate.stream.upstreamProvider, variant = candidate.stream.variant))
                            return@withTimeoutOrNull candidate
                        }
                        if (completed) cache[key] = Entry(module.id, module.revision, target, null, System.currentTimeMillis() + 30_000)
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        val failure = (error as? ModuleException)?.failure ?: ModuleFailure.EXECUTION
                        diagnostics.record(module.id, ModuleDiagnostic(when (failure) {
                            ModuleFailure.INVALID_CONFIGURATION -> ModuleCheckStatus.CONFIGURATION
                            ModuleFailure.NETWORK -> ModuleCheckStatus.UNAVAILABLE
                            ModuleFailure.TIMEOUT -> ModuleCheckStatus.TIMEOUT
                            else -> ModuleCheckStatus.FAILED
                        },
                            System.currentTimeMillis(), System.currentTimeMillis() - started, failure, module.revision, phase, candidateCount,
                            code = listOfNotNull((error as? ModuleException)?.stage?.name, (error as? ModuleException)?.diagnosticCode, error.javaClass.simpleName,
                                error.cause?.javaClass?.simpleName).joinToString("/")))
                        cache[key] = Entry(module.id, module.revision, target, null, System.currentTimeMillis() + 10_000)
                    }
                }
                    null
                } finally {
                    while (cache.size > 256) cache.keys.firstOrNull()?.let(cache::remove)
                }
            }
        }
    }

    suspend fun refresh(selection: ModuleAudioSelection): ModuleAudioSelection {
        if (selection.stream.expiresAtMs > System.currentTimeMillis() + 60_000) return selection
        val song = selection.song ?: metadataRepository.get(selection.originalId)
        val refreshed = this.selection(selection.originalId, selection.module, selection.trackId, selection.metadata, song)
        if (refreshed.cacheKey != selection.cacheKey ||
            (selection.stream.contentLength > 0 && refreshed.stream.contentLength > 0 && selection.stream.contentLength != refreshed.stream.contentLength)) {
            throw ModuleException(ModuleFailure.EXECUTION)
        }
        return refreshed
    }

    suspend fun handlePlaybackError(selection: ModuleAudioSelection, failure: ModulePlaybackFailure): ModulePlaybackDecision {
        val module = currentModule(selection) ?: return ModulePlaybackDecision.Default
        if ("onPlaybackError" !in module.manifest.capabilities) return ModulePlaybackDecision.Default
        val started = System.currentTimeMillis()
        val decision = try {
            withTimeoutOrNull(10_000) {
                repository.runtime.playbackError(engineId(module), module.source, module.origin, selection.trackId, failure,
                    module.configuration, selection.metadata, selection.song?.moduleSong(selection.originalId))
            } ?: ModulePlaybackDecision.Default
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            diagnostics.record(module.id, playbackDiagnostic(module, failure, started, "HANDLER_FAILED",
                listOfNotNull((error as? ModuleException)?.diagnosticCode, error.javaClass.simpleName).joinToString("/")))
            return ModulePlaybackDecision.Default
        }
        diagnostics.record(module.id, playbackDiagnostic(module, failure, started, when (decision) {
            ModulePlaybackDecision.Retry -> "RETRY"
            ModulePlaybackDecision.Fallback -> "FALLBACK"
            is ModulePlaybackDecision.Fail -> "FAIL"
            ModulePlaybackDecision.Default -> "DEFAULT"
        }))
        return decision
    }

    suspend fun retry(selection: ModuleAudioSelection, failure: ModulePlaybackFailure): ModuleAudioSelection? {
        val module = currentModule(selection) ?: return null
        val target = selection.song ?: return null
        val started = System.currentTimeMillis()
        var problem = "TIMEOUT"
        val fresh = (try {
            withTimeoutOrNull(15_000) {
                this@ResolveModuleAudioUseCase.selection(selection.originalId, module, selection.trackId, selection.metadata, target, failure)
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            problem = listOfNotNull((error as? ModuleException)?.diagnosticCode, error.javaClass.simpleName).joinToString("/")
            null
        }) ?: run {
            diagnostics.record(module.id, playbackDiagnostic(module, failure, started, "RETRY_FAILED", problem))
            return null
        }
        cache[cacheId(selection.originalId, module.id)] = Entry(module.id, module.revision, target, fresh, fresh.stream.expiresAtMs - 60_000)
        return fresh
    }

    fun exclude(id: String, moduleId: String) {
        val key = cacheId(id, moduleId)
        cache.remove(key)
        exclusions[key] = System.currentTimeMillis() + EXCLUSION_MS
        exclusions.entries.removeIf { it.value <= System.currentTimeMillis() }
        while (exclusions.size > 256) exclusions.keys.firstOrNull()?.let(exclusions::remove)
    }

    private fun excluded(id: String, moduleId: String): Boolean {
        val key = cacheId(id, moduleId)
        val until = exclusions[key] ?: return false
        if (until > System.currentTimeMillis()) return true
        exclusions.remove(key, until)
        return false
    }

    private fun currentModule(selection: ModuleAudioSelection): InstalledModule? =
        repository.state.value?.modules?.firstOrNull { it.id == selection.module.id && it.revision == selection.module.revision && it.enabled }

    private fun playbackDiagnostic(module: InstalledModule, failure: ModulePlaybackFailure, started: Long, outcome: String, detail: String = "") =
        ModuleDiagnostic(when (failure.category) {
            "network" -> ModuleCheckStatus.UNAVAILABLE
            "timeout" -> ModuleCheckStatus.TIMEOUT
            else -> ModuleCheckStatus.FAILED
        }, System.currentTimeMillis(), System.currentTimeMillis() - started, when (failure.category) {
            "http", "network" -> ModuleFailure.NETWORK
            "timeout" -> ModuleFailure.TIMEOUT
            else -> ModuleFailure.EXECUTION
        }, module.revision, ModuleResolutionPhase.STREAM, variant = moduleDiagnosticLabel(failure.variant),
            code = listOf("PLAYBACK", failure.category.uppercase(java.util.Locale.ROOT), failure.httpStatus?.toString().orEmpty(), outcome, detail)
                .filter(String::isNotBlank).joinToString("/"))

    private suspend fun selection(id: String, module: InstalledModule, trackId: String, metadata: ModuleTrack?,
        song: ModuleMatchMetadata?, retry: ModulePlaybackFailure? = null): ModuleAudioSelection {
        val stream = repository.runtime.stream(engineId(module), module.source, module.origin, trackId, module.configuration, metadata,
            song?.moduleSong(id), retry)
        val identity = listOf(id, module.id, module.revision, trackId, stream.upstreamProvider, stream.upstreamId, stream.variant, stream.mimeType, stream.codecs,
            stream.bitrate.toString(), stream.sampleRate?.toString().orEmpty()).joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return ModuleAudioSelection(id, module, trackId, stream, "module:$digest", metadata, song)
    }

    fun invalidateFailures(moduleId: String) {
        cache.entries.removeIf { it.value.selection == null && it.value.moduleId == moduleId }
    }

    private fun engineId(module: InstalledModule) = "${module.id}:${module.revision}"
    private companion object { const val EXCLUSION_MS = 10 * 60_000L }
    private fun cacheId(id: String, moduleId: String) = "${id.length}:$id$moduleId"

    private fun stillCurrent(expected: ModuleRegistry, module: InstalledModule): Boolean {
        val current = repository.state.value ?: return false
        if (!current.enabled) return false
        val preceding = expected.modules.filter { it.enabled }.takeWhile { it.id != module.id } + module
        return current.modules.filter { it.enabled }.take(preceding.size).map { it.id to it.revision } == preceding.map { it.id to it.revision }
    }
}
