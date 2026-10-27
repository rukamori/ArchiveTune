/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleDownloadRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val modules: ModuleRepository,
) {
    private val file = AtomicFile(File(context.filesDir, "modules/downloads.bin"))
    private val mutex = Mutex()
    private var loaded: Map<String, ModuleAudioSelection>? = null

    suspend fun find(id: String): ModuleAudioSelection? = withContext(Dispatchers.IO) { mutex.withLock { read()[id] } }

    suspend fun put(selection: ModuleAudioSelection) = change { it + (selection.originalId to selection) }

    suspend fun remove(id: String) = change { it - id }

    private suspend fun change(transform: (Map<String, ModuleAudioSelection>) -> Map<String, ModuleAudioSelection>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(read())
            val definitions = next.values.map { it.module }.distinctBy { it.revision }
            val root = JSONObject().put("definitions", modules.encode(ModuleRegistry(modules = definitions), portable = false))
                .put("bindings", JSONArray().apply { next.values.forEach { binding ->
                    val stream = binding.stream
                    put(JSONObject().put("id", binding.originalId).put("revision", binding.module.revision).put("track", binding.trackId)
                        .put("cacheKey", binding.cacheKey).put("url", stream.url).put("headers", JSONObject(stream.headers))
                        .put("mime", stream.mimeType).put("codecs", stream.codecs).put("bitrate", stream.bitrate)
                        .put("sampleRate", stream.sampleRate).put("length", stream.contentLength).put("expires", stream.expiresAtMs).put("variant", stream.variant)
                        .put("upstreamProvider", stream.upstreamProvider).put("upstreamId", stream.upstreamId)
                        .put("inlineManifest", stream.inlineManifest).put("allowedDomains", JSONArray(stream.allowedDomains))
                        .put("metadata", binding.metadata?.let { track ->
                            JSONObject().put("id", track.id).put("title", track.title).put("artists", JSONArray(track.artists))
                                .put("album", track.album).put("duration", track.durationSeconds).put("explicit", track.explicit).put("isrc", track.identifier)
                        }))
                } })
            val bytes = modules.encrypt(root.toString(), "downloads").toByteArray()
            if (bytes.size > ModuleRepository.MAX_REGISTRY_BYTES * 2) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            file.baseFile.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw java.io.IOException("Module storage unavailable") }
            val output = file.startWrite()
            try { output.write(bytes); file.finishWrite(output) }
            catch (error: Throwable) { file.failWrite(output); throw error }
            loaded = next
        }
    }

    private fun read(): Map<String, ModuleAudioSelection> {
        loaded?.let { return it }
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyMap<String, ModuleAudioSelection>().also { loaded = it }
        val raw = file.openRead().use { input -> input.readModuleBytes(ModuleRepository.MAX_REGISTRY_BYTES * 2) }
        if (raw.size > ModuleRepository.MAX_REGISTRY_BYTES * 2) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val root = JSONObject(modules.decrypt(raw.toString(Charsets.UTF_8), "downloads"))
        val definitions = modules.decode(root.getJSONObject("definitions"), portable = false, allowRepeatedInstallations = true).modules.associateBy { it.revision }
        val array = root.getJSONArray("bindings")
        return (0 until array.length()).associate { index ->
            val value = array.getJSONObject(index)
            val id = value.getString("id")
            val headers = value.getJSONObject("headers")
            id to ModuleAudioSelection(id, definitions.getValue(value.getString("revision")), value.getString("track"),
                ModuleStream(value.getString("url"), headers.keys().asSequence().associateWith(headers::getString), value.getString("mime"),
                    value.getString("codecs"), value.optInt("bitrate"), value.optInt("sampleRate").takeIf { it > 0 }, value.optLong("length"),
                    value.getLong("expires"), value.getString("variant"), value.optString("upstreamProvider"), value.optString("upstreamId"),
                    value.optString("inlineManifest").takeIf(String::isNotBlank), value.optJSONArray("allowedDomains").strings()),
                value.getString("cacheKey"), value.optJSONObject("metadata")?.let { track ->
                    val artists = track.getJSONArray("artists")
                    ModuleTrack(track.getString("id"), track.getString("title"), (0 until artists.length()).map(artists::getString),
                        track.optString("album").takeIf(String::isNotBlank), track.optInt("duration").takeIf { it > 0 },
                        if (track.has("explicit") && !track.isNull("explicit")) track.getBoolean("explicit") else null,
                        track.optString("isrc").takeIf(String::isNotBlank))
                })
        }.also { loaded = it }
    }
}
