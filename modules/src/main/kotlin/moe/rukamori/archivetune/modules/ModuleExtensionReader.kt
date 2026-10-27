/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI

object ModuleExtensionReader {
    private val runtimeFeatures = setOf("signedSession@3", "sessionGrant@1", "downloadSegments@1", "preparedContext@1")

    fun isManifest(value: JSONObject): Boolean = value.opt("name") is String && value.opt("version") is String &&
        (value.has("permissions") || value.optJSONArray("type")?.strings()?.any { it.endsWith("_provider") } == true)

    fun read(files: Map<String, ByteArray>, supplied: JSONObject, arch: Boolean = false, appVersion: String = ""): String = try {
        if (!arch && "provider.js" in files) invalid("ENTRY")
        if (supplied.toString().toByteArray(Charsets.UTF_8).size > 128 * 1024 || supplied.has("iconBase64")) invalid("MANIFEST")
        val metadata = JSONObject(supplied.toString())
        for (key in listOf("name", "version")) if (metadata.opt(key) !is String) invalid("IDENTITY")
        val name = metadata.optString("name")
        if (!arch && !metadata.optJSONArray("type").strings().contains("download_provider")) unsupported("PLAYBACK_PROVIDER")
        val id = if (arch) metadata.optString("id") else "extension.$name"
        if (!Regex("[a-z0-9][a-z0-9._-]{0,127}").matches(id) || name.isBlank() || metadata.optString("version").isBlank()) invalid("IDENTITY")
        val required = when (val value = metadata.opt("requiredRuntimeFeatures")) {
            null -> emptyList()
            is String -> listOf(value)
            is JSONArray -> (0 until value.length()).map { value.get(it) as? String ?: invalid("FEATURES") }
            else -> invalid("FEATURES")
        }
        if (required.size > 32) invalid("FEATURES")
        if (required.any { it !in runtimeFeatures }) throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.MANIFEST,
            diagnosticCode = "EXT_RUNTIME_FEATURE_${required.first { it !in runtimeFeatures }.replace(Regex("[^A-Za-z0-9@._-]"), "_").take(64)}")
        if (metadata.has("permissions") && metadata.opt("permissions") !is JSONObject) invalid("PERMISSIONS")
        if (metadata.has("signedSession") && metadata.opt("signedSession") !is JSONObject) invalid("SESSION")
        val permissions = metadata.optJSONObject("permissions") ?: JSONObject()
        if (permissions.has("network") && permissions.opt("network") !is JSONArray) invalid("NETWORK")
        val network = permissions.optJSONArray("network") ?: JSONArray()
        if (network.length() > 128) invalid("NETWORK")
        for (index in 0 until network.length()) {
            val domain = network.getString(index)
            if (domain.length > 253 || !Regex("(?:\\*\\.)?[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?").matches(domain)) invalid("NETWORK")
        }
        for (key in listOf("storage", "file")) if (permissions.has(key) && permissions.get(key) !is Boolean) invalid("PERMISSIONS")
        metadata.optJSONObject("signedSession")?.let { signed ->
            val uri = URI(ModuleHttpClient.webUrl(signed.getString("baseUrl")))
            if (uri.scheme != "https" || uri.rawQuery != null || uri.rawFragment != null) invalid("SESSION")
            ModuleNetworkPermissions(network.strings().map { it.lowercase(java.util.Locale.ROOT) }).requireAllowed(uri.toString())
            if (!Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}").matches(signed.optString("namespace")) ||
                signed.optInt("timeWindowSeconds", 300) !in 1..86400) invalid("SESSION")
            if (signed.has("timeWindowSeconds")) {
                val window = signed.get("timeWindowSeconds") as? Number ?: invalid("SESSION")
                if (window.toDouble() % 1.0 != 0.0) invalid("SESSION")
            }
            if (signed.has("endpoints")) {
                val declared = signed.getJSONObject("endpoints")
                for (key in listOf("bootstrap", "challenge", "exchange", "refresh")) if (declared.has(key)) {
                    val path = declared.getString(key)
                    if (path.length > 2048 || (path.isBlank() && key != "refresh")) invalid("SESSION")
                    if (path.isNotBlank()) {
                        val endpoint = URI(ModuleHttpClient.webUrl(ModuleHttpClient.resolveUrl(path.trimStart('/'), uri.toString().trimEnd('/') + "/")))
                        if (endpoint.scheme != "https" || endpoint.host != uri.host || (endpoint.port.takeIf { it >= 0 } ?: 443) != (uri.port.takeIf { it >= 0 } ?: 443)) invalid("SESSION")
                    }
                }
            }
            if (!signed.has("schemeLabel")) invalid("SESSION_SCHEME")
            for (key in listOf("appVersion", "platform", "schemeLabel", "headerPrefix")) if (signed.has(key)) {
                val value = signed.get(key) as? String ?: invalid("SESSION")
                if (value.isBlank() || value.length > 256 || value.any { it.code < 32 || it.code > 126 }) invalid("SESSION")
            }
            if (!Regex("[A-Za-z0-9-]+").matches(signed.optString("headerPrefix", "X-Sig-"))) invalid("SESSION")
            signed.remove("callbackUrl")
        }
        val iconName = metadata.optString("icon")
        if (iconName.isNotBlank()) {
            if (iconName !in setOf("icon.png", "icon.jpg", "icon.jpeg")) invalid("ICON")
            val bytes = files[iconName] ?: invalid("ICON")
            metadata.put("iconBase64", icon(bytes))
        }
        metadata.opt("settings")?.let { raw ->
            metadata.put("extensionSettings", when (raw) {
                is JSONArray -> JSONArray(raw.toString())
                is JSONObject -> JSONObject(raw.toString())
                else -> invalid("SETTINGS")
            })
        }
        ModulePackageReader.normalizeSettings(metadata)
        val schema = metadata.getJSONObject("settings")
        schema.keys().asSequence().toList().forEach { key ->
            val field = schema.getJSONObject(key)
            for (flag in listOf("required", "secret")) if (field.has(flag) && field.get(flag) !is Boolean) invalid("SETTINGS")
            for (bound in listOf("min", "max")) if (field.has(bound) && (field.get(bound) !is Number || !field.getDouble(bound).isFinite())) invalid("SETTINGS")
            if (field.optString("type") == "button") {
                if (field.optBoolean("required")) unsupported("SETTING_ACTION")
                schema.remove(key)
            }
        }
        if (arch) {
            val declared = metadata.getJSONArray("capabilities")
            val known = listOf("searchTracks", "getTrackStreamUrl", "getAlbum", "getArtist", "getPlaylist")
            val values = (0 until declared.length()).map { declared.get(it) as? String ?: invalid("CAPABILITIES") }
            if (values.size != values.distinct().size || values.any { it !in known } || !values.containsAll(known.take(2))) invalid("CAPABILITIES")
            metadata.put("declaredCapabilities", declared)
        }
        metadata.put("id", id).put("capabilities", JSONArray(listOf("searchTracks", "getTrackStreamUrl")))
        if (!arch) metadata.put("extensionMinAppVersion", metadata.optString("minAppVersion")).put("minAppVersion", "")
        metadata.put("extensionRuntimeFeatures", JSONArray(required))
        metadata.put("requiredRuntimeFeatures", JSONArray())
        val manifest = ModuleManifest.parse(metadata)
        if (arch) manifest.requireCompatible(appVersion)
        if (manifest.settings.any { it.type == ModuleSettingType.UNSUPPORTED }) unsupported("SETTING_TYPE")
        if (manifest.settings.any { it.key.isBlank() || !it.copy(required = false).acceptsValue(it.defaultJson) ||
                it.options.any { option -> option.label.length > 256 || option.description.length > 2048 || option.kind.length > 64 } ||
                (it.minimum != null && it.maximum != null && it.minimum > it.maximum) || (it.type == ModuleSettingType.SELECT && it.options.isEmpty()) }) invalid("SETTINGS")
        val scriptBytes = files[if (arch) "provider.js" else "index.js"] ?: invalid("SCRIPT")
        if (scriptBytes.size > 3 * 1024 * 1024) invalid("SCRIPT")
        val script = ModulePackageReader.utf8(scriptBytes).removePrefix("\uFEFF")
        if (script.isBlank()) invalid("SCRIPT")
        val source = """
            return __moduleCreateExtension(JSON.parse(${JSONObject.quote(metadata.toString())}), function(environment) {
                (function(http,storage,utils,session,file,fetch,matching,log,gobackend,ffmpeg,registerExtension) {
            $script
                }).call(globalThis,environment.http,environment.storage,environment.utils,environment.session,environment.file,
                    environment.fetch,environment.matching,environment.log,environment.gobackend,environment.ffmpeg,environment.registerExtension);
            });
        """.trimIndent()
        if (source.toByteArray(Charsets.UTF_8).size > ModuleSourceDecoder.MAX_SOURCE_BYTES) invalid("SIZE")
        source
    } catch (error: org.json.JSONException) {
        throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error, ModuleLoadStage.MANIFEST, "EXT_MANIFEST")
    }

    private fun icon(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024 || bounds.outMimeType !in listOf("image/png", "image/jpeg")) invalid("ICON")
        if (bounds.outMimeType == "image/png") return Base64.encodeToString(bytes, Base64.NO_WRAP).also { moduleIcon(it) }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: invalid("ICON")
        val output = ByteArrayOutputStream()
        try { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) invalid("ICON") } finally { bitmap.recycle() }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP).also { moduleIcon(it) }
    }

    private fun invalid(code: String): Nothing = throw ModuleException(ModuleFailure.INVALID_DOCUMENT, stage = ModuleLoadStage.MANIFEST, diagnosticCode = "EXT_$code")
    private fun unsupported(code: String): Nothing = throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.MANIFEST, diagnosticCode = "EXT_$code")
}
