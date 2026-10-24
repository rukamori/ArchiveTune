package moe.rukamori.archivetune.modules

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModulePlaybackSource(val id: String, val name: String)

@Singleton
class ModulePlaybackSourceRepository @Inject constructor() {
    private val mutableSources = MutableStateFlow<Map<String, ModulePlaybackSource?>>(emptyMap())
    val sources = mutableSources.asStateFlow()

    fun resolving(mediaId: String) {
        mutableSources.update { it - mediaId }
    }

    fun selected(mediaId: String, source: ModulePlaybackSource?) {
        mutableSources.update { current ->
            val updated = current - mediaId + (mediaId to source)
            if (updated.size > 256) updated - updated.keys.first() else updated
        }
    }
}
