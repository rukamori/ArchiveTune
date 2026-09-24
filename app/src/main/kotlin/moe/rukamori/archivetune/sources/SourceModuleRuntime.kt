package moe.rukamori.archivetune.sources

import android.util.Base64
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class SourceModuleRuntime(
    private val http: SourceHttpClient,
    private val address: HttpUrl,
    private val script: String,
    private val bridge: String,
) {
    private val mutex = Mutex()
    private val lifecycleLock = Any()
    private val timers = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
    private var users = 0
    private var retired = false
    private var engine: QuickJs? = null

    fun acquire() = synchronized(lifecycleLock) {
        check(!retired)
        users++
    }

    fun release() = synchronized(lifecycleLock) {
        users--
        if (retired && users == 0) dispose()
    }

    fun retire() = synchronized(lifecycleLock) {
        retired = true
        if (users == 0) dispose()
    }

    suspend fun call(function: String, args: List<JsonElement>): JsonObject = mutex.withLock {
        try {
            val runtime = engine ?: initialize().also { engine = it }
            runtime.evaluate<String>(
                """
                globalThis.__sourceResult = null;
                (async function() {
                    try {
                        const value = await __module[${JsonPrimitive(function)}](...${JsonArray(args)});
                        globalThis.__sourceResult = JSON.stringify({value: value});
                    } catch (error) {
                        globalThis.__sourceResult = JSON.stringify({failed: true});
                    } finally {
                        __sourceClearTimers();
                    }
                })();
                'started';
                """.trimIndent(),
            )
            val result = runtime.evaluate<String>("__sourceResult || '{}'")
            val envelope = http.json.parseToJsonElement(result) as? JsonObject
                ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
            if ((envelope["failed"] as? JsonPrimitive)?.booleanOrNull == true) {
                throw SourceException(SourceProblem.MODULE_EXECUTION)
            }
            envelope["value"] as? JsonObject ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
        } catch (failure: CancellationException) {
            dispose()
            throw failure
        } catch (failure: SourceException) {
            throw failure
        } catch (failure: Exception) {
            throw SourceException(SourceProblem.MODULE_EXECUTION, failure)
        }
    }

    private suspend fun initialize(): QuickJs {
        val runtime = QuickJs.create(Dispatchers.Default)
        try {
            runtime.memoryLimit = 8L * 1024 * 1024
            runtime.maxStackSize = 512L * 1024
            bind(runtime)
            runtime.evaluate<String>(bridge + "\n'ready';")
            val wrapped = WRAPPER.matchEntire(script)
            val code = wrapped?.groupValues?.get(1)?.removeSurrounding("`") ?: script
            runtime.evaluate<String>("globalThis.__module = (() => { const module = {exports:{}}; const exports = module.exports; const self = globalThis;\n" + code + "\nreturn module.exports; })(); 'ready';")
            val available = runtime.evaluate<Boolean>("typeof __module?.searchTracks === 'function' && typeof __module?.getTrackStreamUrl === 'function'")
            if (!available) throw SourceException(SourceProblem.UNSUPPORTED)
            return runtime
        } catch (failure: Throwable) {
            timers.values.forEach { it.complete(Unit) }
            timers.clear()
            runtime.close()
            throw failure
        }
    }

    private fun dispose() {
        timers.values.forEach { it.complete(Unit) }
        timers.clear()
        engine?.close()
        engine = null
    }

    private fun bind(runtime: QuickJs) {
        runtime.asyncFunction<String, String>("__sourceFetch") { value ->
            val request = http.json.parseToJsonElement(value) as JsonObject
            val url = address.resolve(request.text("url")) ?: throw SourceException(SourceProblem.INVALID_ADDRESS)
            val method = request.text("method").ifBlank { "GET" }.uppercase(Locale.ROOT)
            val builder = Request.Builder().url(url)
            request.obj("headers").forEach { (name, value) -> builder.header(name, (value as JsonPrimitive).content) }
            val body = if (method in listOf("GET", "HEAD")) null else request.text("body").toRequestBody("application/json".toMediaType())
            val result = http.text(builder.method(method, body).build())
            JsonObject(mapOf("status" to JsonPrimitive(result.status), "body" to JsonPrimitive(result.body),
                "headers" to JsonObject(result.headers.mapValues { JsonPrimitive(it.value) }))).toString()
        }
        runtime.function("__sourceTimerCreate") { values ->
            timers[(values[0] as Number).toLong()] = CompletableDeferred()
            Unit
        }
        runtime.function("__sourceTimerClear") { values ->
            timers.remove((values[0] as Number).toLong())?.complete(Unit)
            Unit
        }
        runtime.asyncFunction<String, Unit>("__sourceDelay") { value ->
            val timer = http.json.parseToJsonElement(value) as JsonObject
            val id = timer.number("id")?.toLong() ?: throw SourceException(SourceProblem.INVALID_RESPONSE)
            val signal = timers[id]
            if (signal != null) {
                try {
                    withTimeoutOrNull((timer.number("ms")?.toLong() ?: 0L).coerceIn(1L, 12_000L)) { signal.await() }
                } finally {
                    timers.remove(id, signal)
                }
            }
        }
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
    }

    private companion object {
        val WRAPPER = Regex("""^\s*export\s+const\s+\w+\s*=\s*(`.*`)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)
    }
}
