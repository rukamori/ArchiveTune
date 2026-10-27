/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class ModuleSessionCallback(val state: String, val grant: String) {
    companion object {
        fun parse(value: String): ModuleSessionCallback {
            fun invalid(): Nothing = throw ModuleException(ModuleFailure.INVALID_CONFIGURATION, stage = ModuleLoadStage.PROVIDER,
                diagnosticCode = "SESSION_CALLBACK")
            if (value.length > 16_384) invalid()
            val uri = Uri.parse(value)
            if (uri.scheme != "archivetune" || uri.host != "module-session" || uri.port != -1 || uri.encodedUserInfo != null ||
                !uri.path.isNullOrEmpty() || uri.fragment != null) invalid()
            val states = uri.getQueryParameters("state")
            if (states.size != 1 || states.single().length !in 16..256 || states.single().isBlank()) invalid()
            val grants = uri.getQueryParameters("grant")
            val codes = uri.getQueryParameters("code")
            if (grants.isNotEmpty() && codes.isNotEmpty()) invalid()
            val tokens = grants.ifEmpty { codes }
            if (tokens.size != 1 || tokens.single().isBlank() || tokens.single().length > 12_288) invalid()
            return ModuleSessionCallback(states.single(), tokens.single())
        }
    }
}

internal class ModuleSignedSession(
    private val config: JSONObject,
    private val storage: ModuleRuntimeStorage,
    private val request: (JSONObject) -> JSONObject,
) {
    private val scope = "session:" + ModuleRuntimeStorage.hash(listOf(config.optString("namespace"), config.getString("baseUrl"),
        config.optString("appVersion", "ext-1.0"), config.optString("platform", "extension")).joinToString("\n"))
    private val base = config.getString("baseUrl").trimEnd('/') + "/"
    private val endpoints = config.optJSONObject("endpoints") ?: JSONObject()
    private val timestamp = DateTimeFormatterBuilder().appendInstant(3).toFormatter(Locale.ROOT)

    fun authenticated(): Boolean = usable(storage.read(scope))

    fun begin(): String? {
        val record = record()
        if (usable(record)) return null
        if (record.optLong("pendingUntil") > System.currentTimeMillis()) {
            return record.optString("authorizationUrl").takeIf(String::isNotBlank)
        }
        val url = Uri.parse(endpoint("bootstrap", "/bootstrap")).buildUpon()
            .appendQueryParameter("app_version", config.optString("appVersion", "ext-1.0"))
            .appendQueryParameter("install_id", record.getString("install_id")).build().toString()
        val response = request(JSONObject().put("url", url).put("method", "GET").put("exact", true))
        requireSuccess(response)
        val value = responseDocument(response)
        if (value.optString("session_id").isNotBlank() && value.optString("session_secret").isNotBlank()) {
            saveSession(record, value)
            return null
        }
        val randomState = randomHex(32)
        val supplied = value.optString("auth_url").ifBlank { value.optString("challenge_url") }
        val state = if (supplied.isBlank()) randomState else Uri.parse(supplied).getQueryParameter("state")?.takeIf { it.length in 16..256 } ?: randomState
        val callback = Uri.parse(CALLBACK).buildUpon().appendQueryParameter("cb_version", "v2grant")
            .appendQueryParameter("state", state).build().toString()
        val target = if (supplied.isNotBlank()) {
            val parsed = Uri.parse(ModuleHttpClient.webUrl(supplied))
            parsed.buildUpon().clearQuery().apply {
                parsed.queryParameterNames.filter { it !in setOf("cb", "callback", "state") }.forEach { key ->
                    parsed.getQueryParameters(key).forEach { appendQueryParameter(key, it) }
                }
                appendQueryParameter("cb", callback)
                appendQueryParameter("state", state)
            }.build().toString()
        } else {
            val challenge = value.optString("challenge_id").takeIf { it.isNotBlank() && it.length <= 1024 }
                ?: fail("SESSION_CHALLENGE")
            Uri.parse(endpoint("challenge", "/challenge")).buildUpon().appendQueryParameter("id", challenge)
                .appendQueryParameter("cb", callback).build().toString()
        }
        if (URI(target).scheme != "https" || target.length > 8192) fail("SESSION_CHALLENGE")
        record.put("pendingState", state).put("pendingUntil", System.currentTimeMillis() + 180_000).put("authorizationUrl", target)
        storage.write(scope, record)
        storage.write("authorization", JSONObject().put("state", state).put("until", record.getLong("pendingUntil")))
        return target
    }

    fun complete(callback: ModuleSessionCallback): Boolean {
        val record = storage.read(scope)
        val pending = record.optString("pendingState")
        if (pending.isBlank() || !MessageDigest.isEqual(pending.toByteArray(), callback.state.toByteArray())) return false
        if (record.optLong("pendingUntil") <= System.currentTimeMillis()) fail("SESSION_CALLBACK_EXPIRED")
        val body = JSONObject().put("grant", callback.grant).put("install_id", record.getString("install_id"))
            .put("app_version", config.optString("appVersion", "ext-1.0")).put("platform", config.optString("platform", "extension"))
        val response = request(JSONObject().put("url", endpoint("exchange", "/session/exchange")).put("method", "POST")
            .put("body", body.toString()).put("headers", JSONObject().put("Content-Type", "application/json")).put("exact", true))
        requireSuccess(response)
        saveSession(record, responseDocument(response))
        return true
    }

    fun clear() {
        storage.write(scope, JSONObject().put("install_id", record().getString("install_id")))
        storage.write("authorization", JSONObject())
    }

    fun fetch(method: String, path: String, body: String, declared: JSONObject): JSONObject {
        val target = resolve(path)
        val privateEndpoints = listOf(endpoint("bootstrap", "/bootstrap"), endpoint("exchange", "/session/exchange")) +
            endpoints.optString("refresh").takeIf(String::isNotBlank)?.let { listOf(resolve(it)) }.orEmpty()
        fun normalized(url: String): String = URI(null, null, URI(url).path, null).normalize().path.trimEnd('/')
        if (privateEndpoints.any { normalized(it) == normalized(target) }) fail("SESSION_PRIVATE_ENDPOINT")
        var record = record()
        if (!usable(record)) {
            if (begin() != null) return verification()
            record = record()
        }
        val expiry = expires(record)
        if (expiry - System.currentTimeMillis() < 3_600_000 && endpoints.optString("refresh").isNotBlank()) {
            val response = signed(record, "POST", endpoint("refresh", ""),
                JSONObject().put("install_id", record.getString("install_id")).toString(), JSONObject())
            if (response.getBoolean("ok")) {
                val updated = responseDocument(response)
                for (key in listOf("session_id", "session_secret", "expires_at")) if (updated.optString(key).isBlank()) updated.put(key, record.getString(key))
                saveSession(record, updated)
                record = record()
            } else return response
        }
        val response = signed(record, method.uppercase(Locale.ROOT), target, body, declared)
        if (!response.getBoolean("ok")) {
            val contract = try { JSONObject(response.getString("body")) } catch (_: org.json.JSONException) { JSONObject() }
            val origin = contract.optString("origin")
            val action = contract.optString("action")
            val status = response.getInt("statusCode")
            if (origin == "gateway" && ((status == 401 && contract.optString("code") == "SESSION_INVALID" && action == "bootstrap_session") ||
                (status == 428 && contract.optString("code") == "VERIFY_REQUIRED" && action == "verify"))) {
                clear()
                return verification()
            }
            for ((key, source) in listOf("code" to "code", "origin" to "origin", "action" to "action", "retryMode" to "retry_mode",
                "retryable" to "retryable", "retryAfterSeconds" to "retry_after_seconds")) if (contract.has(source)) response.put(key, contract.get(source))
        }
        return response
    }

    private fun signed(record: JSONObject, method: String, url: String, body: String, declared: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        val date = timestamp.format(Instant.ofEpochMilli(now))
        val nonce = randomHex(16)
        val window = now / 1000 / config.optInt("timeWindowSeconds", 300)
        val session = record.getString("session_id")
        val appVersion = config.optString("appVersion", "ext-1.0")
        val platform = config.optString("platform", "extension")
        val rolling = hmac(record.getString("session_secret"), "$window:$session")
        val bodyHash = ModuleRuntimeStorage.hash(body)
        val message = listOf(config.getString("schemeLabel"), method, URI(url).rawPath.ifBlank { "/" }, "", bodyHash,
            date, nonce, session, appVersion, platform).joinToString("\n")
        val prefix = config.optString("headerPrefix", "X-Sig-")
        val headers = JSONObject()
        declared.keys().forEach { key -> if (!key.startsWith(prefix, true)) headers.put(key, declared.getString(key)) }
        headers.put("Content-Type", "application/json")
        for ((key, value) in listOf("Session" to session, "Timestamp" to date, "Nonce" to nonce, "Body-SHA256" to bodyHash,
            "Signature" to hmac(rolling, message), "App-Version" to appVersion, "Platform" to platform)) headers.put(prefix + key, value)
        return request(JSONObject().put("url", url).put("method", method).put("body", body).put("headers", headers).put("exact", true))
    }

    private fun record(): JSONObject = storage.read(scope).apply {
        if (optString("install_id").isBlank()) {
            put("install_id", UUID.randomUUID().toString())
            storage.write(scope, this)
        }
    }

    private fun saveSession(record: JSONObject, value: JSONObject) {
        if (value.optString("session_id").length !in 1..4096 || value.optString("session_secret").length !in 1..4096 || expires(value) <= System.currentTimeMillis()) fail("SESSION_RESPONSE")
        val saved = JSONObject().put("install_id", record.getString("install_id"))
        for (key in listOf("session_id", "session_secret", "expires_at")) saved.put(key, value.getString(key))
        storage.write(scope, saved)
        storage.write("authorization", JSONObject())
    }

    private fun usable(record: JSONObject): Boolean = record.optString("session_id").isNotBlank() && record.optString("session_secret").isNotBlank() && expires(record) > System.currentTimeMillis()
    private fun expires(record: JSONObject): Long = try { Instant.parse(record.optString("expires_at")).toEpochMilli() } catch (_: java.time.format.DateTimeParseException) { 0 }
    private fun endpoint(key: String, fallback: String): String = resolve(endpoints.optString(key, fallback))
    private fun resolve(path: String): String {
        val url = ModuleHttpClient.webUrl(ModuleHttpClient.resolveUrl(path.trimStart('/'), base))
        val expected = URI(base)
        val target = URI(url)
        if (target.scheme != "https" || target.host != expected.host || (target.port.takeIf { it >= 0 } ?: 443) != (expected.port.takeIf { it >= 0 } ?: 443)) fail("SESSION_ORIGIN")
        return url
    }

    private fun requireSuccess(response: JSONObject) {
        if (!response.getBoolean("ok")) throw ModuleException(ModuleFailure.NETWORK, stage = ModuleLoadStage.PROVIDER,
            diagnosticCode = "SESSION_HTTP_${response.getInt("statusCode")}")
    }

    private fun responseDocument(response: JSONObject): JSONObject = ModulePackageReader.manifest(response.getString("body").toByteArray(Charsets.UTF_8))

    private fun verification(): JSONObject = JSONObject().put("ok", false).put("needsVerification", true).put("error", "VERIFY_REQUIRED")
        .put("statusCode", 428).put("status", 428).put("code", "VERIFY_REQUIRED").put("auth_url", "").put("open_auth_url", "")

    private fun hmac(key: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun fail(code: String): Nothing = throw ModuleException(ModuleFailure.INVALID_CONFIGURATION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = code)

    companion object {
        const val CALLBACK = "archivetune://module-session"
        private val random = SecureRandom()
        private fun randomHex(size: Int): String = ByteArray(size).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
