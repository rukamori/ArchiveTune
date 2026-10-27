/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class ModuleQualityTag { HI_RES, MAX }

class ModuleSettingsUseCases @Inject constructor(
    private val repository: ModuleRepository,
    private val diagnostics: ModuleDiagnostics,
    private val resolver: ResolveModuleAudioUseCase,
) {
    val state = flow { repository.snapshot(); emitAll(repository.state.filterNotNull()) }
    val health = combine(diagnostics.checks, diagnostics.state) { checks, playback ->
        (checks.keys + playback.keys).associateWith { ModuleHealth(checks[it], playback[it]) }
    }
    suspend fun snapshot() = repository.snapshot()
    suspend fun setEnabled(value: Boolean) = repository.setEnabled(value)
    suspend fun enable(id: String, value: Boolean) = repository.setModuleEnabled(id, value)
    suspend fun reorder(ids: List<String>) = repository.reorder(ids)
    suspend fun remove(id: String, catalog: Boolean) = if (catalog) repository.removeCatalog(id) else repository.removeModule(id)
    suspend fun save(id: String, values: Map<String, String>) = repository.settings(id, values)
    suspend fun saveMetadata(id: String, name: String, author: String, description: String) {
        require(name.isNotBlank())
        repository.metadata(id, ModuleMetadata(name.trim(), author.trim(), description.trim()))
    }
    suspend fun file(uri: Uri) = repository.importFile(uri)
    suspend fun url(value: String, sha256: String = "") = repository.importUrl(value, expectedDigest = sha256)
    suspend fun catalog(value: String, id: String?) = repository.catalog(value, id)
    suspend fun refresh(id: String) = repository.refreshCatalog(id)
    suspend fun commit(value: ModuleImport) = repository.commit(value)
    suspend fun authorize(id: String): String? = repository.authorize(id).also {
        if (it == null) resolver.invalidateFailures(id)
    }
    suspend fun completeAuthorization(callback: String) {
        val id = repository.completeAuthorization(callback)
        resolver.invalidateFailures(id)
        check(id)
    }

    fun qualityTags(labels: List<String>, settings: List<ModuleSetting> = emptyList()): List<ModuleQualityTag> {
        val declarations = labels + settings.filter { field ->
            !field.secret && QUALITY_FIELD.containsMatchIn(field.key + " " + field.label)
        }.flatMap { field ->
            field.options.flatMap { option -> listOf(option.label, qualityValue(option.valueJson)) } + qualityValue(field.defaultJson)
        }
        val normalized = declarations.map { it.lowercase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ') }
        return buildList {
            if (normalized.any(HI_RES::containsMatchIn)) add(ModuleQualityTag.HI_RES)
            if (normalized.any(MAX_QUALITY::containsMatchIn)) add(ModuleQualityTag.MAX)
        }
    }

    private fun qualityValue(value: String): String = try { parseJsonValue(value) as? String ?: "" }
    catch (_: org.json.JSONException) { "" }

    suspend fun update(id: String, sha256: String = ""): ModuleImport.Provider? {
        val module = repository.snapshot().modules.first { it.id == id }
        if (module.updateUrl.isBlank()) return null
        val proposed = repository.importUrl(module.updateUrl, id, sha256) as? ModuleImport.Provider
            ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        return proposed.takeIf { it.document.source != module.source }
    }

    suspend fun check(id: String) {
        val module = repository.snapshot().modules.first { it.id == id }
        val started = System.currentTimeMillis()
        try {
            val checked = withTimeoutOrNull(15_000) { repository.check(module); true } == true
            if (checked) resolver.invalidateFailures(id)
            diagnostics.recordCheck(id, ModuleDiagnostic(if (checked) ModuleCheckStatus.READY else ModuleCheckStatus.TIMEOUT,
                System.currentTimeMillis(), System.currentTimeMillis() - started,
                if (checked) null else ModuleFailure.TIMEOUT, module.revision))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val failure = (error as? ModuleException)?.failure ?: ModuleFailure.EXECUTION
            diagnostics.recordCheck(id, ModuleDiagnostic(when (failure) {
                ModuleFailure.INVALID_CONFIGURATION -> ModuleCheckStatus.CONFIGURATION
                ModuleFailure.NETWORK -> ModuleCheckStatus.UNAVAILABLE
                ModuleFailure.TIMEOUT -> ModuleCheckStatus.TIMEOUT
                else -> ModuleCheckStatus.FAILED
            },
                System.currentTimeMillis(), System.currentTimeMillis() - started, failure, module.revision,
                code = listOfNotNull((error as? ModuleException)?.stage?.name, (error as? ModuleException)?.diagnosticCode, error.javaClass.simpleName,
                    error.cause?.javaClass?.simpleName).joinToString("/")))
        }
    }

    private companion object {
        val QUALITY_FIELD = Regex("quality|resolution|format", RegexOption.IGNORE_CASE)
        val HI_RES = Regex("""\b(?:hi(?:gh)?\s*res(?:olution)?|24\s*bit)\b""")
        val MAX_QUALITY = Regex("""\bmax(?:imum)?(?:\s+quality)?\b""")
    }
}

class RestoreModuleBackupUseCase @Inject constructor(private val repository: ModuleRepository) {
    suspend operator fun invoke(bytes: ByteArray) = repository.restorePortable(bytes)
}
