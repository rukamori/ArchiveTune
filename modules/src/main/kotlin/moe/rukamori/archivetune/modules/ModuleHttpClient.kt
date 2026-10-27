/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ModuleHttpClient(configuredClient: OkHttpClient? = null) {
    private val calls = ConcurrentHashMap<String, Call>()
    private val callLock = Any()
    private val abortedCalls = LinkedHashSet<String>()
    private val closedNamespaces = LinkedHashSet<String>()
    private val requestSlots = Semaphore(64)
    private val activeRequests = Semaphore(8)
    private val client = (configuredClient ?: OkHttpClient()).newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            try { request.tag(ModuleNetworkPermissions::class.java)?.requireAllowed(request.url.toString()) }
            catch (error: ModuleException) { throw IOException("Module network permission denied", error) }
            val initial = chain.call().request().url
            val redirected = if (request.url.host != initial.host || request.url.port != initial.port || request.url.scheme != initial.scheme) {
                request.newBuilder().removeHeader("Authorization").removeHeader("Cookie").removeHeader("Proxy-Authorization").build()
            } else request
            chain.proceed(redirected)
        }.build()
    private val exactClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun document(url: String): Pair<String, String> {
        val (bytes, origin) = source(url)
        return bytes.toString(Charsets.UTF_8) to origin
    }

    suspend fun source(url: String): Pair<ByteArray, String> {
        val response = execute(Request.Builder().url(webUrl(url)).build(), "document:${java.util.UUID.randomUUID()}", ModuleSourceDecoder.MAX_SOURCE_BYTES)
        if (!response.getBoolean("ok")) throw ModuleException(ModuleFailure.NETWORK)
        return Base64.decode(response.getString("base64"), Base64.DEFAULT) to response.getString("url")
    }

    suspend fun fetch(json: String, baseUrl: String, namespace: String, permissions: ModuleNetworkPermissions? = null, onStatus: (Int) -> Unit = {}): String {
        val input = JSONObject(json)
        val id = namespace + ":" + input.getString("id")
        val request = request(input, baseUrl).newBuilder().tag(ModuleNetworkPermissions::class.java, permissions).build()
        permissions?.requireAllowed(request.url.toString())
        return execute(request, id, MAX_BODY_BYTES).also { onStatus(it.getInt("status")) }.toString()
    }

    internal fun fetchBlocking(json: String, baseUrl: String, namespace: String, permissions: ModuleNetworkPermissions,
        cookieJar: CookieJar? = null,
        onStatus: (Int) -> Unit = {}): String {
        val input = JSONObject(json)
        val id = namespace + ":" + input.getString("id")
        if (!activeRequests.tryAcquire()) throw ModuleException(ModuleFailure.EXECUTION)
        try {
            val request = request(input, baseUrl).newBuilder().tag(ModuleNetworkPermissions::class.java, permissions).build()
            permissions.requireAllowed(request.url.toString())
            val selected = if (input.optBoolean("exact")) exactClient else client
            val call = (cookieJar?.let { selected.newBuilder().cookieJar(it).build() } ?: selected).newCall(request)
            if (!register(id, call)) throw ModuleException(ModuleFailure.EXECUTION)
            try {
                return call.execute().use { response ->
                    onStatus(response.code)
                    responseValue(response, MAX_BODY_BYTES).toString()
                }
            } catch (error: IOException) {
                throw (error.cause as? ModuleException) ?: ModuleException(
                    if (error is java.io.InterruptedIOException) ModuleFailure.TIMEOUT else ModuleFailure.NETWORK,
                    error, ModuleLoadStage.PROVIDER)
            } finally { calls.remove(id, call) }
        } finally { activeRequests.release() }
    }

    fun closeNamespace(namespace: String) {
        synchronized(callLock) {
            closedNamespaces.add(namespace)
            while (closedNamespaces.size > 128) closedNamespaces.remove(closedNamespaces.first())
            calls.filterKeys { it.startsWith("$namespace:") }.values.forEach(Call::cancel)
        }
    }

    private fun request(input: JSONObject, baseUrl: String): Request {
        val url = webUrl(resolveUrl(input.getString("url"), baseUrl))
        val method = input.optString("method", "GET").uppercase(java.util.Locale.ROOT)
        val headers = input.optJSONObject("headers") ?: JSONObject()
        val builder = Request.Builder().url(url)
        headers.keys().forEach { key -> builder.header(key, headers.getString(key)) }
        val body = when {
            input.has("bodyBase64") -> Base64.decode(input.getString("bodyBase64"), Base64.DEFAULT)
            input.has("body") && !input.isNull("body") -> input.getString("body").toByteArray()
            else -> null
        }
        if (body != null && body.size > MAX_BODY_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val requestBody = if (method in listOf("GET", "HEAD")) null else
            (body ?: byteArrayOf()).toRequestBody(headers.optString("content-type", headers.optString("Content-Type")).toMediaTypeOrNull())
        builder.method(method, requestBody)
        return builder.build()
    }

    private fun register(id: String, call: Call): Boolean = synchronized(callLock) {
        if (id.substringBefore(':') in closedNamespaces || abortedCalls.remove(id) || calls.containsKey(id)) false
        else { calls[id] = call; true }
    }

    fun abort(id: String) {
        synchronized(callLock) {
            val call = calls[id]
            if (call != null) call.cancel() else {
                abortedCalls.add(id)
                while (abortedCalls.size > 128) abortedCalls.remove(abortedCalls.first())
            }
        }
    }

    private suspend fun execute(request: Request, id: String, maximumBytes: Int): JSONObject {
        if (!requestSlots.tryAcquire()) throw ModuleException(ModuleFailure.EXECUTION)
        try {
            return activeRequests.withPermit { executeActive(request, id, maximumBytes) }
        } finally { requestSlots.release() }
    }

    private suspend fun executeActive(request: Request, id: String, maximumBytes: Int): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            val accepted = register(id, call)
            if (!accepted) {
                continuation.resumeWithException(ModuleException(ModuleFailure.EXECUTION))
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { call.cancel(); calls.remove(id, call) }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(id, call)
                    if (continuation.isActive) continuation.resumeWithException((e.cause as? ModuleException) ?: ModuleException(ModuleFailure.NETWORK, e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use { responseValue(it, maximumBytes) }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally { calls.remove(id, call) }
                }
            })
        }

    private fun responseValue(response: Response, maximumBytes: Int): JSONObject {
        val body = response.body
        if (body.contentLength() > maximumBytes) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val bytes = body.byteStream().use { it.readModuleBytes(maximumBytes) }
        return JSONObject().put("status", response.code).put("ok", response.isSuccessful)
            .put("statusText", response.message).put("url", response.request.url.toString())
            .put("headers", JSONObject().apply {
                response.headers.names().forEach { name ->
                    val values = response.headers.values(name)
                    put(name, if (values.size == 1) values.single() else org.json.JSONArray(values))
                }
            })
            .put("text", bytes.toString(Charsets.UTF_8)).put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    companion object {
        const val MAX_BODY_BYTES = 8 * 1024 * 1024

        fun webUrl(value: String): String {
            val uri = try { URI(value) } catch (error: Exception) { throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error) }
            if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            }
            return uri.toASCIIString()
        }

        fun resolveUrl(value: String, base: String): String = try {
            val uri = URI(value)
            if (uri.isAbsolute) uri.toString() else URI(base).resolve(uri).toString()
        } catch (error: Exception) { throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error) }
    }
}
