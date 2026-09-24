package moe.rukamori.archivetune.sources

import android.content.Context
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceModuleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: SourceHttpClient,
) {
    private val slots = Semaphore(3)
    private data class CachedScript(val code: String, val loadedAt: Long)
    private val scripts = object : LruCache<String, CachedScript>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedScript): Int = value.code.length * 2
    }
    private val runtimes = object : LruCache<String, SourceModuleRuntime>(12) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: SourceModuleRuntime, newValue: SourceModuleRuntime?) {
            oldValue.retire()
        }
    }
    private val bridge by lazy { context.assets.open("sources/module-runtime.js").bufferedReader().use { it.readText() } }

    internal fun entries(document: JsonObject): List<JsonObject> = document.entries
        .filter { it.key.startsWith("category:") && it.key !in setOf("category:artworks", "category:testing") }
        .flatMap { (_, value) -> (value as? JsonArray).orEmpty() }
        .mapNotNull { it as? JsonObject }.filter { it.text("id").isNotBlank() && it.text("download").isNotBlank() }
        .distinctBy { it.text("id") }.take(32)

    suspend fun search(source: SourceConfiguration, query: String): List<SourceCandidate> = supervisorScope {
        val index = entries(http.document(SourceHttpClient.address(source.url)))
        val responses = Channel<Result<List<SourceCandidate>>>(index.size.coerceAtLeast(1))
        val jobs = index.map { module -> launch {
            val result = try {
                val document = call(source, module, "searchTracks", listOf(JsonPrimitive(query), JsonPrimitive(40), JsonObject(mapOf("settings" to JsonObject(emptyMap())))))
                Result.success(document.array("tracks").mapNotNull { element ->
                    val track = element as? JsonObject ?: return@mapNotNull null
                    val id = track.text("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    SourceCandidate(id, TrackIdentity(track.text("title"), track.text("artist"), track.text("album"), track.number("duration")?.toInt()?.takeIf { it > 0 },
                        (track["explicit"] as? JsonPrimitive)?.booleanOrNull),
                        moduleId = module.text("id"), format = track.text("format"))
                })
            } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
                Result.failure(SourceException(SourceProblem.UNAVAILABLE, failure))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Result.failure(failure as? SourceException ?: SourceException(SourceProblem.INVALID_RESPONSE, failure))
            }
            responses.send(result)
        } }
        val candidates = mutableListOf<SourceCandidate>()
        var lastFailure: Throwable? = null
        try {
            withTimeoutOrNull(8_000) {
                repeat(index.size) {
                    responses.receive().fold({ candidates += it }, { lastFailure = it })
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
            responses.close()
        }
        if (candidates.isEmpty() && lastFailure != null) throw lastFailure!!
        val groups = candidates.groupBy { it.moduleId }.values.toList()
        buildList {
            repeat(groups.maxOfOrNull { it.size } ?: 0) { position ->
                groups.forEach { group -> group.getOrNull(position)?.let(::add) }
            }
        }
    }

    suspend fun stream(source: SourceConfiguration, candidate: SourceCandidate, lossless: Boolean, low: Boolean): SourceAudio {
        val module = entries(http.document(SourceHttpClient.address(source.url))).firstOrNull { it.text("id") == candidate.moduleId }
            ?: throw SourceException(SourceProblem.NO_MATCH)
        val quality = when { low -> "LOW"; lossless -> "HI_RES_LOSSLESS"; else -> "HIGH" }
        val context = JsonObject(mapOf(
            "settings" to JsonObject(mapOf("quality" to JsonObject(mapOf("value" to JsonPrimitive(quality))))),
            "track" to JsonObject(mapOf(
                "title" to JsonPrimitive(candidate.identity.title),
                "artist" to JsonPrimitive(candidate.identity.artist),
                "album" to JsonPrimitive(candidate.identity.album),
            )),
        ))
        val response = call(source, module, "getTrackStreamUrl", listOf(
            JsonPrimitive(candidate.id), JsonPrimitive(quality), context,
        ))
        return SourceProviderRepository.parseAudio(JsonObject(response.obj("track") + response + mapOf(
            "url" to JsonPrimitive(response.text("streamUrl").ifBlank { response.text("url") }),
        )))
    }

    private suspend fun call(source: SourceConfiguration, module: JsonObject, function: String, args: List<kotlinx.serialization.json.JsonElement>): JsonObject = slots.withPermit {
        withTimeout(12_000) {
            val base = SourceHttpClient.address(source.url)
            val address = base.resolve(module.text("download")) ?: throw SourceException(SourceProblem.INVALID_ADDRESS)
            val key = listOf(source.id, source.url, address.toString(), module.text("version"), module.text("code")).joinToString("\u0000")
            val cached = scripts.get(key)?.takeIf { System.currentTimeMillis() - it.loadedAt < 60 * 60_000 }
            val script = cached?.code ?: http.text(Request.Builder().url(address).build()).let {
                if (it.status !in 200..299) throw SourceException(SourceProblem.UNAVAILABLE)
                val decoded = SourceModuleDecoder.decode(it.body)
                scripts.put(key, CachedScript(decoded, System.currentTimeMillis()))
                decoded
            }
            val polyfills = withContext(Dispatchers.IO) { bridge }
            val runtimeKey = key + script.sourceHash()
            val session = synchronized(runtimes) {
                val session = runtimes.get(runtimeKey) ?: SourceModuleRuntime(http, address, script, polyfills).also {
                    runtimes.put(runtimeKey, it)
                }
                session.acquire()
                session
            }
            try {
                session.call(function, args)
            } finally {
                session.release()
            }
        }
    }
}
