package moe.rukamori.archivetune.sources

import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SourceSettingsUseCases @Inject constructor(
    private val repository: SourceSettingsRepository,
    private val providers: SourceProviderRepository,
    private val resolver: ResolveExternalSourceUseCase,
) {
    val settings = repository.settings
    val health = resolver.health

    suspend fun save(id: String?, url: String, name: String) {
        val found = if (id == null) providers.identify(url) else providers.identify(url, id)
        repository.update { current ->
            if (current.sources.any { it.id != found.id && it.url.isNotBlank() && canonical(it.url) == canonical(found.url) }) {
                throw SourceException(SourceProblem.DUPLICATE)
            }
            val previous = current.sources.firstOrNull { it.id == found.id }
            val saved = found.copy(name = name.trim().ifEmpty { found.name }, enabled = previous?.enabled ?: true)
            val sources = if (previous != null) current.sources.map { if (it.id == saved.id) saved else it }
                else listOf(saved) + current.sources
            current.copy(sources = sources)
        }
    }

    suspend fun enable(id: String, enabled: Boolean) = repository.update { current ->
        current.copy(sources = current.sources.map { if (it.id == id && it.kind != SourceKind.YOUTUBE) it.copy(enabled = enabled) else it })
    }

    suspend fun remove(id: String) = repository.update { current ->
        current.copy(sources = current.sources.filterNot { it.id == id && it.kind in setOf(SourceKind.ADDON, SourceKind.MODULE) })
    }

    suspend fun move(id: String, offset: Int) = repository.update { current ->
        val sources = current.sources.toMutableList()
        val index = sources.indexOfFirst { it.id == id && it.kind != SourceKind.YOUTUBE }
        val destination = index + offset
        if (index >= 0 && destination in sources.indices && sources[destination].kind != SourceKind.YOUTUBE) {
            sources.add(destination, sources.removeAt(index))
        }
        current.copy(sources = sources.toList())
    }

    suspend fun source(id: String): SourceConfiguration? = settings.first().sources.firstOrNull { it.id == id }
    suspend fun check(id: String) { source(id)?.let { resolver.check(it) } }

    private fun canonical(raw: String): String = SourceHttpClient.address(raw).newBuilder().fragment(null).build().toString().trimEnd('/')
}
