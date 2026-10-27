/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ModuleNetworkPermissions(private val domains: List<String>) {
    fun requireAllowed(url: String) {
        val host = URI(ModuleHttpClient.webUrl(url)).host.lowercase(Locale.ROOT)
        if (domains.none { host == it || (it.startsWith("*.") && host.endsWith(it.substring(1)) && host != it.substring(2)) }) {
            throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "EXT_NETWORK_PERMISSION")
        }
    }
}

internal class ModuleExtensionHost(
    context: Context,
    private val http: ModuleHttpClient,
    private val origin: String,
    private val namespace: String,
    installation: String?,
    private val problem: (ModuleFailure, String) -> Unit,
) {
    private val storage = installation?.let { ModuleRuntimeStorage(context, it.substringBefore(':')) }
    private val cookies = storage?.let { ModuleCookieJar(it) }
    private val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    private var permissions = ModuleNetworkPermissions(emptyList())
    var domains: List<String>? = null
        private set
    val networkPermissions: ModuleNetworkPermissions? get() = if (domains == null) null else permissions
    private var storageAllowed = false
    private var session: ModuleSignedSession? = null
    var authentication: Boolean = false
        private set
    @Volatile private var cancelled = false
    @Volatile private var signal = CountDownLatch(1)
    private var deadline = SystemClock.elapsedRealtime() + 5_000
    private var inspectionStorage = JSONObject()

    fun begin() {
        if (cancelled) throw kotlinx.coroutines.CancellationException("Module operation cancelled")
        deadline = SystemClock.elapsedRealtime() + 15_000
        val next = CountDownLatch(1)
        signal = next
        if (cancelled) next.countDown()
    }
    fun cancel() { cancelled = true; signal.countDown(); http.closeNamespace(namespace) }

    fun authorize(): String? {
        begin()
        return requireNotNull(session) { "This module has no signed session" }.begin()?.also(permissions::requireAllowed)
    }

    fun complete(callback: ModuleSessionCallback): Boolean {
        begin()
        return session?.complete(callback) == true
    }

    fun authenticated(): Boolean = session?.authenticated() ?: true

    fun invoke(json: String): String {
        if (json.length > 1024 * 1024) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val input = ModulePackageReader.manifest(json.toByteArray(Charsets.UTF_8))
        val action = input.getString("action")
        if (action == "configure") {
            if (domains != null) throw ModuleException(ModuleFailure.UNSUPPORTED, diagnosticCode = "EXT_HOST_ALREADY_CONFIGURED")
            val metadata = input.getJSONObject("metadata")
            val declared = metadata.optJSONObject("permissions") ?: JSONObject()
            domains = declared.optJSONArray("network").strings().map { it.lowercase(Locale.ROOT) }
            permissions = ModuleNetworkPermissions(checkNotNull(domains))
            storageAllowed = declared.optBoolean("storage")
            authentication = metadata.optJSONObject("signedSession") != null
            session = metadata.optJSONObject("signedSession")?.let { config ->
                storage?.let { ModuleSignedSession(config, it, ::request) }
            }
            return "null"
        }
        if (action == "begin") { begin(); return "null" }
        if (action == "remaining") return (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0).toString()
        if (action == "cancelled") return (signal.count == 0L).toString()
        checkActive()
        try {
            val value: Any? = when (action) {
                "request" -> request(input)
                "sessionFetch" -> {
                    val signed = session ?: throw ModuleException(ModuleFailure.INVALID_CONFIGURATION)
                    signed.fetch(input.optString("method", "GET"), input.getString("path"), input.optString("body"),
                        input.optJSONObject("headers") ?: JSONObject()).also {
                        if (it.optBoolean("needsVerification")) problem(ModuleFailure.INVALID_CONFIGURATION, "SESSION_VERIFICATION_REQUIRED")
                    }
                }
                "sessionStatus" -> JSONObject().put("authenticated", session?.authenticated() == true)
                "clearCookies" -> { cookies?.clear(); true }
                "sessionClear" -> { (session ?: throw ModuleException(ModuleFailure.INVALID_CONFIGURATION)).clear(); JSONObject().put("success", true) }
                "storageGet", "storageSet", "storageRemove", "storageClear", "storageKeys" -> {
                    if (!storageAllowed) throw ModuleException(ModuleFailure.UNSUPPORTED, diagnosticCode = "EXT_STORAGE_PERMISSION")
                    val values = storage?.read("storage") ?: inspectionStorage
                    val key = input.optString("key")
                    if (key.length > 1024) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                    when (action) {
                        "storageGet" -> values.opt(key)
                        "storageKeys" -> JSONArray(values.keys().asSequence().toList())
                        "storageSet" -> { values.put(key, input.opt("value") ?: JSONObject.NULL); writeStorage(values); true }
                        "storageRemove" -> { values.remove(key); writeStorage(values); true }
                        else -> { writeStorage(JSONObject()); true }
                    }
                }
                "digest" -> {
                    val algorithm = when (input.getString("algorithm")) { "sha256" -> "SHA-256"; "sha1" -> "SHA-1"; "md5" -> "MD5"; else -> throw ModuleException(ModuleFailure.UNSUPPORTED) }
                    MessageDigest.getInstance(algorithm).digest(input.getString("value").toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                }
                "sleep" -> {
                    val millis = input.getLong("millis").coerceAtLeast(0)
                    if (millis >= deadline - SystemClock.elapsedRealtime()) throw ModuleException(ModuleFailure.TIMEOUT)
                    signal.await(millis, TimeUnit.MILLISECONDS)
                    checkActive()
                    true
                }
                "version" -> version
                "userAgent" -> "Arch/$version"
                "uuid" -> UUID.randomUUID().toString()
                else -> throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "EXT_HOST_ACTION")
            }
            return jsonValue(value)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) {
            val failure = (error as? ModuleException)?.failure ?: ModuleFailure.EXECUTION
            problem(failure, (error as? ModuleException)?.diagnosticCode ?: "EXT_${failure.name}")
            throw error
        }
    }

    private fun request(input: JSONObject): JSONObject {
        checkActive()
        if (storage == null) throw ModuleException(ModuleFailure.EXECUTION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "EXT_INITIALIZATION_IO")
        val request = JSONObject(input.toString()).put("id", UUID.randomUUID().toString())
        val value = JSONObject(http.fetchBlocking(request.toString(), origin, namespace, permissions, cookies) { status ->
            if (status !in 200..299) problem(ModuleFailure.NETWORK, "HTTP_$status")
        })
        checkActive()
        return JSONObject().put("statusCode", value.getInt("status")).put("status", value.getInt("status"))
            .put("ok", value.getBoolean("ok")).put("statusText", value.optString("statusText"))
            .put("body", value.getString("text")).put("url", value.getString("url"))
            .put("headers", value.getJSONObject("headers")).put("base64", value.getString("base64"))
    }

    private fun checkActive() {
        if (cancelled || signal.count == 0L) throw kotlinx.coroutines.CancellationException("Module operation cancelled")
        if (SystemClock.elapsedRealtime() >= deadline) throw ModuleException(ModuleFailure.TIMEOUT)
    }

    private fun writeStorage(value: JSONObject) {
        if (value.toString().toByteArray(Charsets.UTF_8).size > 1024 * 1024) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        if (storage == null) inspectionStorage = value else storage.write("storage", value)
    }
}

fun moduleStreamCookieJar(context: Context, installation: String): CookieJar = ModuleCookieJar(ModuleRuntimeStorage(context, installation), persist = false)

private class ModuleCookieJar(private val storage: ModuleRuntimeStorage, private val persist: Boolean = true) : CookieJar {
    private var snapshot: List<Pair<String, Cookie>>? = null
    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieOperation { read().map { it.second }.filter { it.matches(url) } }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>): Unit = cookieOperation {
        val next = read().associateBy { (_, cookie) -> listOf(cookie.domain, cookie.path, cookie.name) }.toMutableMap()
        cookies.forEach { cookie -> next[listOf(cookie.domain, cookie.path, cookie.name)] = url.toString() to cookie }
        val array = JSONArray()
        val retained = next.values.filter { it.second.expiresAt > System.currentTimeMillis() }.takeLast(512)
        retained.forEach { (origin, cookie) ->
            array.put(JSONObject().put("url", origin).put("cookie", cookie.toString()))
        }
        if (persist) storage.write("cookies", JSONObject().put("values", array))
        else snapshot = retained
    }

    @Synchronized
    fun clear() { storage.write("cookies", JSONObject()); snapshot = null }

    private fun <T> cookieOperation(operation: () -> T): T = try { operation() }
    catch (error: Exception) { throw if (error is java.io.IOException) error else java.io.IOException("Module cookie storage unavailable", error) }

    private fun read(): List<Pair<String, Cookie>> {
        if (!persist) snapshot?.let { return it.filter { (_, cookie) -> cookie.expiresAt > System.currentTimeMillis() } }
        val array = storage.read("cookies").optJSONArray("values") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val origin = item.getString("url")
            val cookie = Cookie.parse(origin.toHttpUrl(), item.getString("cookie")) ?: return@mapNotNull null
            (origin to cookie).takeIf { cookie.expiresAt > System.currentTimeMillis() }
        }.also { if (!persist) snapshot = it }
    }
}
