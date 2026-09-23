package moe.rukamori.archivetune.sources

import android.content.Context
import android.util.Base64
import android.util.LruCache
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                    SourceCandidate(id, TrackIdentity(track.text("title"), track.text("artist"), track.text("album"), track.number("duration")?.toInt(),
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
        candidates
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
            val key = address.toString() + module.text("version") + module.text("code")
            val cached = scripts.get(key)?.takeIf { System.currentTimeMillis() - it.loadedAt < 60 * 60_000 }
            val script = cached?.code ?: http.text(Request.Builder().url(address).build()).let {
                if (it.status !in 200..299) throw SourceException(SourceProblem.UNAVAILABLE)
                val decoded = SourceModuleDecoder.decode(it.body)
                scripts.put(key, CachedScript(decoded, System.currentTimeMillis()))
                decoded
            }
            val runtime = QuickJs.create(Dispatchers.Default)
            try {
                runtime.memoryLimit = 32L * 1024 * 1024
                runtime.maxStackSize = 512L * 1024
                runtime.asyncFunction<String, String>("__sourceFetch") { value ->
                    val request = http.json.parseToJsonElement(value) as JsonObject
                    val url = address.resolve(request.text("url")) ?: throw SourceException(SourceProblem.INVALID_ADDRESS)
                    val method = request.text("method").ifBlank { "GET" }.uppercase()
                    val builder = Request.Builder().url(url)
                    request.obj("headers").forEach { (name, value) -> builder.header(name, (value as JsonPrimitive).content) }
                    val body = if (method in listOf("GET", "HEAD")) null else request.text("body").toRequestBody("application/json".toMediaType())
                    val result = http.text(builder.method(method, body).build())
                    JsonObject(mapOf("status" to JsonPrimitive(result.status), "body" to JsonPrimitive(result.body),
                        "headers" to JsonObject(result.headers.mapValues { JsonPrimitive(it.value) }))).toString()
                }
                runtime.asyncFunction<Long, Unit>("__sourceDelay") { delay(it.coerceIn(0, 12_000)) }
                runtime.function("__sourceBase64Decode") { values ->
                    Base64.decode(values.first().toString(), Base64.DEFAULT).toString(Charsets.ISO_8859_1)
                }
                runtime.function("__sourceBase64Encode") { values ->
                    Base64.encodeToString(values.first().toString().toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP)
                }
                runtime.function("__sourceUrl") { values ->
                    val raw = values[0].toString()
                    val parent = values.getOrNull(1)?.toString()?.takeIf { it != "undefined" && it != "null" } ?: address.toString()
                    val url = SourceHttpClient.address(parent).resolve(raw) ?: throw SourceException(SourceProblem.INVALID_ADDRESS)
                    JsonObject(mapOf("href" to JsonPrimitive(url.toString()), "protocol" to JsonPrimitive(url.scheme + ":"),
                        "host" to JsonPrimitive(url.host + if (url.port != 80 && url.port != 443) ":${url.port}" else ""),
                        "hostname" to JsonPrimitive(url.host), "pathname" to JsonPrimitive(url.encodedPath),
                        "search" to JsonPrimitive(url.encodedQuery?.let { "?$it" }.orEmpty()), "hash" to JsonPrimitive(url.encodedFragment?.let { "#$it" }.orEmpty()))).toString()
                }
                val polyfills = withContext(Dispatchers.IO) { bridge }
                runtime.evaluate<Unit>(polyfills)
                val wrapped = Regex("^\\s*export\\s+const\\s+\\w+\\s*=\\s*(`.*`)\\s*;?\\s*$", RegexOption.DOT_MATCHES_ALL).matchEntire(script)
                val code = wrapped?.groupValues?.get(1)?.removeSurrounding("`") ?: script
                runtime.evaluate<Unit>("globalThis.__module = (() => { const module = {exports:{}}; const exports = module.exports; const self = globalThis;\n" + code + "\nreturn module.exports; })();")
                val available = runtime.evaluate<Boolean>("typeof __module?.searchTracks === 'function' && typeof __module?.getTrackStreamUrl === 'function'")
                if (!available) throw SourceException(SourceProblem.UNSUPPORTED)
                val result = runtime.evaluate<String>("JSON.stringify(await __module[${JsonPrimitive(function)}](...${JsonArray(args)}));")
                http.json.parseToJsonElement(result) as? JsonObject ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
            } finally {
                runtime.close()
            }
        }
    }
}
