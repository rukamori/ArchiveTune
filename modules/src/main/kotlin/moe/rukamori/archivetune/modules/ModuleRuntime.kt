/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ModuleRuntime(context: Context, private val http: ModuleHttpClient) {
    private val applicationContext = context.applicationContext
    private val lock = Mutex()
    private val engines = LinkedHashMap<String, Engine>(8, 0.75f, true)
    private data class RequestProblem(val failure: ModuleFailure, val code: String)
    private class Engine(val source: String, val runtime: QuickJs, val manifest: ModuleManifest,
        val timers: ConcurrentHashMap<String, CompletableDeferred<Unit>>, val requestProblem: AtomicReference<RequestProblem?>,
        val host: ModuleExtensionHost) {
        fun close() {
            host.cancel()
            timers.values.forEach { it.complete(Unit) }
            timers.clear()
            runtime.close()
        }
    }

    suspend fun inspect(source: String, origin: String): ModuleManifest =
        withTimeoutOrNull(5_000) {
            withContext(Dispatchers.Default) {
                lock.withLock {
                    val engine = create(source, origin)
                    try { engine.manifest } finally {
                        try { engine.close() }
                        catch (error: Exception) { throw ModuleException(ModuleFailure.EXECUTION, error, ModuleLoadStage.ENGINE) }
                    }
                }
            }
        } ?: throw ModuleException(ModuleFailure.TIMEOUT)

    suspend fun search(id: String, source: String, origin: String, query: String, settings: Map<String, String>): List<ModuleTrack> {
        val result = call(id, source, origin, "searchTracks", JSONArray().put(query).put(10).put(context(settings)))
        val tracks = when (result) {
            is JSONArray -> result
            is JSONObject -> result.optJSONArray("tracks")
            else -> null
        } ?: throw ModuleException(ModuleFailure.EXECUTION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "INVALID_SEARCH_RESULT")
        return (0 until minOf(tracks.length(), 100)).mapNotNull { index ->
            val track = tracks.optJSONObject(index) ?: return@mapNotNull null
            val trackId = track.text("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val artist = track.opt("artists")?.takeUnless { it == JSONObject.NULL } ?: track.opt("artist")
            val artists = when (artist) {
                is JSONArray -> (0 until artist.length()).mapNotNull { position ->
                    val item = artist.opt(position)
                    (if (item is JSONObject) item.text("name") else item?.takeUnless { it == JSONObject.NULL }?.toString())?.takeIf(String::isNotBlank)
                }
                is JSONObject -> listOfNotNull(artist.text("name").takeIf(String::isNotBlank))
                else -> listOfNotNull(artist?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank))
            }
            ModuleTrack(trackId, track.text("title").ifBlank { track.text("name") }, artists,
                (track.optJSONObject("album")?.let { it.text("title").ifBlank { it.text("name") } }
                    ?: track.text("album")).takeIf(String::isNotBlank),
                (if (track.has("durationMs")) track.optLong("durationMs").div(1000).toInt()
                    else track.optInt("duration")).takeIf { it > 0 },
                if (track.has("explicit") && !track.isNull("explicit")) track.optBoolean("explicit") else null,
                track.text("isrc").takeIf(String::isNotBlank))
        }
    }

    suspend fun stream(id: String, source: String, origin: String, trackId: String, settings: Map<String, String>, metadata: ModuleTrack? = null): ModuleStream {
        val quality = settings["quality"]?.let(::parseJsonValue) ?: JSONObject.NULL
        val value = call(id, source, origin, "getTrackStreamUrl", JSONArray().put(trackId).put(quality).put(context(settings, metadata))) as? JSONObject
            ?: throw ModuleException(ModuleFailure.EXECUTION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "INVALID_STREAM_RESULT")
        val url = ModuleHttpClient.webUrl(value.text("streamUrl").ifBlank { value.text("url") })
        val track = value.optJSONObject("track") ?: JSONObject()
        val headers = java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
        listOf(track.optJSONObject("headers"), value.optJSONObject("streamHeaders"),
            value.optJSONObject("requestHeaders"), value.optJSONObject("headers")).forEach { declared ->
            declared?.keys()?.forEach { key -> if (!declared.isNull(key)) headers[key] = declared.getString(key) }
        }
        value.text("userAgent").takeIf(String::isNotBlank)?.let { headers.putIfAbsent("User-Agent", it) }
        val mime = value.text("mimeType").ifBlank { track.text("mimeType") }.ifBlank {
            when (value.text("streamType").lowercase(java.util.Locale.ROOT)) {
                "hls" -> "application/x-mpegURL"
                "dash" -> "application/dash+xml"
                else -> when (URI(url).path.orEmpty().substringAfterLast('.').lowercase(java.util.Locale.ROOT)) {
                    "m3u8" -> "application/x-mpegURL"
                    "mpd" -> "application/dash+xml"
                    "flac" -> "audio/flac"
                    "m4a", "mp4" -> "audio/mp4"
                    "opus", "ogg" -> "audio/ogg"
                    "wav" -> "audio/wav"
                    "aac" -> "audio/aac"
                    "mp3" -> "audio/mpeg"
                    else -> "application/octet-stream"
                }
            }
        }
        val now = System.currentTimeMillis()
        val lifetime = value.optLong("expiresInSeconds", value.optLong("expiresIn", 300)).coerceIn(1, 86_400)
        val expiry = value.optLong("expiresAtMs", value.optLong("expiresAt", 0)).let {
            when { it <= 0 -> now + lifetime * 1000; it < 10_000_000_000L -> it * 1000; else -> it }
        }
        if (expiry <= now) throw ModuleException(ModuleFailure.EXECUTION)
        val inline = value.text("inlineManifest").takeIf(String::isNotBlank)
        if (inline != null && (inline.toByteArray(Charsets.UTF_8).size > 1024 * 1024 || mime !in listOf("application/dash+xml", "application/x-mpegURL"))) {
            throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        }
        if (inline != null && mime == "application/dash+xml" && Regex("<(?:[A-Za-z0-9_-]+:)?ContentProtection\\b", RegexOption.IGNORE_CASE).containsMatchIn(inline)) {
            throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "PROTECTED_MANIFEST")
        }
        return ModuleStream(url, headers.toMap(), mime,
            value.text("codecs").ifBlank { value.text("codec") }.ifBlank { track.text("codecs") }.ifBlank { track.text("codec") },
            track.optInt("bitrate", value.optInt("bitrate")),
            track.optDouble("sampleRate", value.optDouble("sampleRate")).takeIf { it.isFinite() && it > 0 }
                ?.let { if (it < 1000) (it * 1000).toInt() else it.toInt() },
            value.optLong("contentLength", track.optLong("contentLength", 0)), expiry,
            value.text("variant").ifBlank { value.text("audioQuality") }.ifBlank { track.text("audioQuality") }
                .ifBlank { track.text("formatId") }.ifBlank { track.text("format") }.ifBlank { mime },
            value.text("provider").ifBlank { track.text("provider") },
            track.text("sourceId").ifBlank { track.text("id") }.ifBlank { trackId }, inline, value.optJSONArray("allowedDomains").strings())
    }

    suspend fun authorize(id: String, source: String, origin: String): String? = withContext(Dispatchers.Default) { withTimeout(30_000) {
        lock.withLock {
            val engine = create(source, origin, id)
            try { hostOperation(engine.host) { engine.host.authorize() } } finally { engine.close() }
        }
    } }

    suspend fun completeAuthorization(id: String, source: String, origin: String, callback: String): Boolean = withContext(Dispatchers.Default) { withTimeout(30_000) {
        val parsed = ModuleSessionCallback.parse(callback)
        val valid = withContext(Dispatchers.IO) {
            val pending = ModuleRuntimeStorage(applicationContext, id).read("authorization")
            pending.optString("state").isNotBlank() && pending.optLong("until") > System.currentTimeMillis() &&
                java.security.MessageDigest.isEqual(pending.optString("state").toByteArray(), parsed.state.toByteArray())
        }
        if (!valid) return@withTimeout false
        lock.withLock {
            val engine = create(source, origin, id)
            try { hostOperation(engine.host) { engine.host.complete(parsed) } } finally { engine.close() }
        }
    } }

    suspend fun checkAuthorization(id: String, source: String, origin: String) = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = create(source, origin, id)
            try {
                if (!hostOperation(engine.host) { engine.host.authenticated() }) throw ModuleException(ModuleFailure.INVALID_CONFIGURATION,
                    stage = ModuleLoadStage.PROVIDER, diagnosticCode = "SESSION_VERIFICATION_REQUIRED")
            } finally { engine.close() }
        }
    }

    suspend fun forget(id: String) = withContext(Dispatchers.IO) {
        unload(id)
        ModuleRuntimeStorage(applicationContext, id).remove()
    }

    suspend fun unload(id: String) {
        lock.withLock { engines.keys.filter { it == id || it.startsWith("$id:") }.forEach { engines.remove(it)?.close() } }
    }

    private suspend fun call(id: String, source: String, origin: String, function: String, args: JSONArray): Any = withContext(Dispatchers.Default) {
        lock.withLock {
            var engine = engines[id]
            if (engine?.source != source) {
                engines.remove(id)?.close()
                engine = create(source, origin, id)
                engines[id] = engine
                while (engines.size > 2) engines.remove(engines.keys.first())?.close()
            }
            val session = checkNotNull(engine)
            val runtime = session.runtime
            session.requestProblem.set(null)
            try {
                evaluate(runtime, session.host,
                    """
                    globalThis.__moduleResult = null;
                    (async () => {
                        try {
                            const args = JSON.parse(${JSONObject.quote(args.toString())});
                            ${if (function == "getTrackStreamUrl") "if(args[1]===null)args[1]=undefined;" else ""}
                            const value = await globalThis.__provider[${JSONObject.quote(function)}](...args);
                            globalThis.__moduleResult = JSON.stringify({value: value === undefined ? null : value});
                        } catch (error) {
                            const name = ['Error','TypeError','ReferenceError','SyntaxError','RangeError','URIError','EvalError','AbortError'].includes(error?.name) ? error.name : 'Error';
                            const frame = String(error?.stack || '').match(/module-(?:host|provider|call)\.js:\d+(?::\d+)?/);
                            const code = ['RESOLUTION_TIMEOUT','VERIFY_REQUIRED'].includes(error?.code) ? error.code : '';
                            globalThis.__moduleResult = JSON.stringify({failed: true, name, code, frame: frame ? frame[0] : ''});
                        } finally {
                            globalThis.__moduleClearTimers();
                        }
                    })();
                    "module-call-started";
                    """.trimIndent(),
                    filename = "module-call.js",
                )
                val result = evaluate(runtime, session.host,
                    "(()=>{const value=globalThis.__moduleResult;globalThis.__moduleResult=null;return value||'';})();",
                    filename = "module-result.js",
                )
                if (result.isBlank() || result.length > ModuleHttpClient.MAX_BODY_BYTES) {
                    throw ModuleException(ModuleFailure.EXECUTION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "INVALID_RESULT")
                }
                val envelope = JSONObject(result)
                if (envelope.optBoolean("failed")) {
                    val name = envelope.optString("name", "Error")
                    val problem = session.requestProblem.get()
                    val code = envelope.optString("code")
                    val failure = when (code) {
                        "RESOLUTION_TIMEOUT" -> ModuleFailure.TIMEOUT
                        "VERIFY_REQUIRED" -> ModuleFailure.INVALID_CONFIGURATION
                        else -> if (name == "Error" || name == "AbortError") problem?.failure ?: ModuleFailure.EXECUTION else ModuleFailure.EXECUTION
                    }
                    val detail = listOf(name, code, envelope.optString("frame"), problem?.code.orEmpty()).filter(String::isNotBlank).joinToString("/")
                    throw ModuleException(failure, stage = ModuleLoadStage.PROVIDER, diagnosticCode = detail)
                }
                if (!envelope.has("value") || envelope.isNull("value")) {
                    throw ModuleException(ModuleFailure.EXECUTION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "INVALID_RESULT")
                }
                val value = envelope.get("value")
                if (function == "getTrackStreamUrl" && value is JSONObject) session.host.domains?.let { domains ->
                    session.host.networkPermissions?.requireAllowed(value.optString("streamUrl", value.optString("url")))
                    value.put("allowedDomains", JSONArray(domains))
                }
                val problem = session.requestProblem.get()
                val tracks = (value as? JSONObject)?.optJSONArray("tracks") ?: value as? JSONArray
                if (function == "searchTracks" && tracks?.length() == 0 && problem != null) {
                    throw ModuleException(problem.failure, stage = ModuleLoadStage.PROVIDER, diagnosticCode = problem.code)
                }
                value
            } catch (error: Throwable) {
                closeAfterFailure(error) { engines.remove(id)?.close() }
                when (error) {
                    is CancellationException -> throw error
                    is ModuleException -> throw error
                    is QuickJsInterruptedException -> throw ModuleException(ModuleFailure.TIMEOUT, error, ModuleLoadStage.PROVIDER)
                    is Exception, is LinkageError -> throw ModuleException(ModuleFailure.EXECUTION, error, ModuleLoadStage.PROVIDER,
                        session.requestProblem.get()?.code)
                    else -> throw error
                }
            }
        }
    }

    private suspend fun create(source: String, origin: String, installation: String? = null): Engine {
        var stage = ModuleLoadStage.DECODE
        var runtime: QuickJs? = null
        val timers = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        val requestProblem = AtomicReference<RequestProblem?>(null)
        val namespace = java.util.UUID.randomUUID().toString()
        val extensionHost = ModuleExtensionHost(applicationContext, http, origin, namespace, installation) { failure, code ->
            requestProblem.set(RequestProblem(failure, code))
        }
        try {
            val code = ModuleSourceDecoder.decode(source).replaceFirst(Regex("^export\\s+default\\s+"), "return ")
            stage = ModuleLoadStage.ENGINE
            val engine = QuickJs.create(Dispatchers.Default)
            runtime = engine
            engine.memoryLimit = 32L * 1024 * 1024
            engine.maxStackSize = 1024L * 1024
            engine.evaluationTimeoutMillis = 15_000
            engine.function("__moduleExtensionCall") { args -> extensionHost.invoke(args.firstOrNull()?.toString().orEmpty()) }
            engine.asyncFunction<String, String>("__moduleFetch") { json ->
                try {
                    http.fetch(json, origin, namespace, extensionHost.networkPermissions) { status ->
                        if (status !in 200..299) requestProblem.set(RequestProblem(ModuleFailure.NETWORK, "HTTP_$status"))
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    val failure = (error as? ModuleException)?.failure ?: ModuleFailure.NETWORK
                    requestProblem.set(RequestProblem(failure, "REQUEST_${failure.name}"))
                    throw error
                }
            }
            engine.function("__moduleTimerCreate") { args ->
                val id = args.firstOrNull()?.toString() ?: throw ModuleException(ModuleFailure.EXECUTION)
                if (id.length > 64 || timers.size >= 128 || timers.putIfAbsent(id, CompletableDeferred()) != null) {
                    throw ModuleException(ModuleFailure.EXECUTION)
                }
                Unit
            }
            engine.asyncFunction<String, Unit>("__moduleDelay") { json ->
                val value = JSONObject(json)
                val id = value.getString("id")
                val signal = timers[id]
                if (signal != null) {
                    try { withTimeoutOrNull(value.optLong("millis").coerceIn(0, 15_000)) { signal.await() } }
                    finally { timers.remove(id, signal) }
                }
                Unit
            }
            engine.function("__moduleClearTimer") { args -> args.firstOrNull()?.toString()?.let { timers.remove(it)?.complete(Unit) }; Unit }
            engine.function("__moduleAbort") { args -> args.firstOrNull()?.toString()?.let { http.abort("$namespace:$it") }; Unit }
            engine.function("__moduleUrl") { args ->
                val input = args.firstOrNull()?.toString().orEmpty()
                val base = args.getOrNull(1)?.toString()?.takeUnless { it == "undefined" } ?: origin
                val uri = URI(ModuleHttpClient.resolveUrl(input, base))
                JSONObject().put("href", uri.toString()).put("protocol", "${uri.scheme}:")
                    .put("hostname", uri.host.orEmpty()).put("host", uri.rawAuthority.orEmpty())
                    .put("pathname", uri.rawPath.orEmpty().ifBlank { "/" }).put("search", uri.rawQuery?.let { "?$it" }.orEmpty())
                    .put("hash", uri.rawFragment?.let { "#$it" }.orEmpty()).put("origin", "${uri.scheme}://${uri.rawAuthority}").toString()
            }
            stage = ModuleLoadStage.HOST
            val host = withContext(Dispatchers.IO) {
                listOf("module-host.js", "module-extension.js").joinToString("\n") { name ->
                    applicationContext.assets.open(name).bufferedReader().use { it.readText() }
                }
            }
            evaluate(engine, extensionHost, host + "\n;\"module-host-ready\";", filename = "module-host.js")
            stage = ModuleLoadStage.PROVIDER
            evaluate(engine, extensionHost,
                "globalThis.__provider=(function(){const module={exports:{}};const exports=module.exports;const self=globalThis;" +
                    code + "\n;return module.exports;})();\n\"module-provider-ready\";",
                filename = "module-provider.js",
            )
            stage = ModuleLoadStage.MANIFEST
            return Engine(source, engine, manifest(engine, extensionHost), timers, requestProblem, extensionHost)
        } catch (error: Throwable) {
            closeAfterFailure(error) {
                timers.values.forEach { it.complete(Unit) }
                timers.clear()
                extensionHost.cancel()
                runtime?.close()
            }
            when (error) {
                is CancellationException -> throw error
                is ModuleException -> throw ModuleException(error.failure, error, error.stage ?: stage, error.diagnosticCode)
                is QuickJsInterruptedException -> throw ModuleException(ModuleFailure.TIMEOUT, error, stage)
                is Exception, is LinkageError -> throw ModuleException(ModuleFailure.EXECUTION, error, stage)
                else -> throw error
            }
        }
    }

    private fun closeAfterFailure(error: Throwable, close: () -> Unit) {
        try { close() }
        catch (closing: Throwable) { if (closing !== error) error.addSuppressed(closing) }
    }

    private suspend fun manifest(runtime: QuickJs, host: ModuleExtensionHost): ModuleManifest {
        val manifest = evaluate(runtime, host,
            "JSON.stringify((()=>{const p=globalThis.__provider;if(!p||typeof p!=='object')return {};" +
                "return {id:p.id,name:p.name,version:p.version,author:p.author,description:p.description," +
                "displayName:p.displayName,homepage:p.homepage,repository:p.repository,license:p.license,type:p.type," +
                "minAppVersion:p.minAppVersion,requiredRuntimeFeatures:p.requiredRuntimeFeatures,iconBase64:p.iconBase64,authentication:p.authentication," +
                "labels:[...(Array.isArray(p.labels)?p.labels:[]),...(Array.isArray(p.tags)?p.tags:[])]," +
                "settings:p.settings||{},capabilities:['searchTracks','getTrackStreamUrl','getAlbum','getArtist','getPlaylist'].filter(k=>typeof p[k]==='function')};})());",
        )
        return ModuleManifest.parse(JSONObject(manifest).put("authentication", host.authentication))
    }

    private suspend fun evaluate(runtime: QuickJs, host: ModuleExtensionHost, code: String, filename: String = "module-manifest.js"): String =
        hostOperation(host) { runtime.evaluate<String>(code, filename = filename) }

    private suspend fun <T> hostOperation(host: ModuleExtensionHost, operation: suspend () -> T): T = coroutineScope {
        val work = async(Dispatchers.IO) { operation() }
        try { work.await() } catch (error: CancellationException) { host.cancel(); throw error }
    }

    private fun JSONObject.text(key: String): String = when (val value = opt(key)) {
        is String, is Number -> value.toString()
        else -> ""
    }

    private fun context(settings: Map<String, String>, track: ModuleTrack? = null): JSONObject = JSONObject()
        .put("settings", JSONObject().apply {
            settings.forEach { (key, value) -> put(key, JSONObject().put("value", parseJsonValue(value))) }
        }).apply {
            track?.let {
                val metadata = JSONObject().put("id", it.id).put("title", it.title)
                    .put("artist", it.artists.joinToString(", ")).put("artists", JSONArray(it.artists))
                    .put("album", it.album).put("duration", it.durationSeconds).put("explicit", it.explicit).put("isrc", it.identifier)
                put("track", metadata).put("metadata", metadata).put("item", metadata)
            }
        }
}
