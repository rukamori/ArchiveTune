/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.util.JsonReader
import android.util.JsonToken
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ModulePackageReader {
    private const val MANIFEST_LIMIT = 128 * 1024
    private const val SCRIPT_LIMIT = 3 * 1024 * 1024
    private val capabilities = listOf("searchTracks", "getTrackStreamUrl", "getAlbum", "getArtist", "getPlaylist")

    fun isPackage(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) || (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()))

    suspend fun read(bytes: ByteArray, appVersion: String): String = withContext(Dispatchers.Default) {
        if (bytes.size > ModuleSourceDecoder.MAX_SOURCE_BYTES) invalid("SIZE")
        val files = HashMap<String, ByteArray>()
        try {
            ZipInputStream(bytes.inputStream()).use { archive ->
                while (true) {
                    ensureActive()
                    val entry = archive.nextEntry ?: break
                    val limit = when (entry.name) {
                        "manifest.json" -> MANIFEST_LIMIT
                        "provider.js" -> SCRIPT_LIMIT
                        "index.js" -> SCRIPT_LIMIT
                        "icon.png" -> 256 * 1024
                        "icon.jpg", "icon.jpeg" -> 256 * 1024
                        "LICENSE", "COPYING", "NOTICE" -> 128 * 1024
                        else -> invalid("ENTRY")
                    }
                    if (entry.isDirectory || entry.name in files || entry.size > limit) invalid("ENTRY")
                    files[entry.name] = archive.readModuleBytes(limit)
                    archive.closeEntry()
                }
            }
        } catch (error: IOException) {
            throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error, ModuleLoadStage.DECODE, "ARCH_ZIP")
        }
        ensureActive()
        val manifestBytes = files["manifest.json"] ?: invalid("MANIFEST_MISSING")
        if ("index.js" in files) return@withContext ModuleExtensionReader.read(files, manifest(manifestBytes))
        if (files.keys.any { it !in setOf("manifest.json", "provider.js", "icon.png") }) invalid("ENTRY")
        val scriptBytes = files["provider.js"] ?: invalid("SCRIPT_MISSING")
        try {
            val root = manifest(manifestBytes)
            if (root.optString("format") != "archivetune.module" || root.opt("formatVersion") != 1 || root.opt("apiVersion") != 1) {
                throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.MANIFEST, diagnosticCode = "ARCH_VERSION")
            }
            if (root.optString("entry") != "provider.js") invalid("ENTRY")
            checksum(scriptBytes, root.opt("sha256"), "CHECKSUM")
            val metadata = root.getJSONObject("module")
            if (root.optString("runtime") == "extension") {
                if (metadata.has("icon")) checksum(files["icon.png"] ?: invalid("ICON"), root.opt("iconSha256"), "ICON_CHECKSUM")
                else if ("icon.png" in files || root.has("iconSha256")) invalid("ICON")
                return@withContext ModuleExtensionReader.read(files, metadata, arch = true, appVersion = appVersion)
            }
            if (root.has("runtime") && root.optString("runtime") != "archivetune") invalid("RUNTIME")
            for (key in listOf("signedSession", "permissions", "serviceHealth", "searchBehavior", "urlHandler")) {
                if (metadata.has(key)) throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.MANIFEST, diagnosticCode = "ARCH_UNSUPPORTED_$key")
            }
            for (key in listOf("id", "name", "version")) if (metadata.opt(key) !is String) invalid("IDENTITY")
            for (key in listOf("author", "description", "displayName", "homepage", "repository", "license", "minAppVersion", "icon", "qualityLabel")) {
                if (metadata.has(key) && metadata.get(key) !is String) invalid("IDENTITY")
            }
            if (metadata.has("iconBase64")) invalid("ICON")
            val icon = files["icon.png"]
            if (metadata.has("icon")) {
                if (metadata.getString("icon") != "icon.png" || icon == null) invalid("ICON")
                checksum(icon, root.opt("iconSha256"), "ICON_CHECKSUM")
                metadata.put("iconBase64", Base64.encodeToString(icon, Base64.NO_WRAP))
            } else if (icon != null || root.has("iconSha256")) invalid("ICON")
            normalizeSettings(metadata)
            for (key in listOf("labels", "tags")) if (metadata.has(key)) {
                val labels = metadata.getJSONArray(key)
                for (index in 0 until labels.length()) if (labels.get(index) !is String) invalid("LABELS")
            }
            val manifest = ModuleManifest.parse(metadata)
            manifest.requireCompatible(appVersion)
            if (!Regex("[a-z0-9][a-z0-9._-]{0,127}").matches(manifest.id) || manifest.version.isBlank()) invalid("IDENTITY")
            val declared = metadata.getJSONArray("capabilities")
            if (declared.length() != manifest.capabilities.size || manifest.capabilities.distinct().size != declared.length() ||
                manifest.capabilities.any { it !in capabilities }) invalid("CAPABILITIES")
            val settings = if (metadata.has("settings")) metadata.getJSONObject("settings") else JSONObject()
            manifest.settings.forEach { field ->
                val schema = settings.getJSONObject(field.key)
                if (field.type == ModuleSettingType.UNSUPPORTED) {
                    throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.MANIFEST, diagnosticCode = "ARCH_SETTING_TYPE")
                }
                if (field.key.isBlank() || (field.minimum != null && field.maximum != null && field.minimum > field.maximum) ||
                    !field.copy(required = false).acceptsValue(field.defaultJson) ||
                    (field.type == ModuleSettingType.SELECT && field.options.isEmpty())) invalid("SETTINGS")
                if (field.options.any { it.label.length > 256 || it.description.length > 2048 || it.kind.length > 64 }) invalid("SETTINGS")
                for (key in listOf("required", "secret")) if (schema.has(key) && schema.get(key) !is Boolean) invalid("SETTINGS")
                for (key in listOf("min", "max")) if (schema.has(key) && (schema.get(key) !is Number || !schema.getDouble(key).isFinite())) invalid("SETTINGS")
            }
            val script = utf8(scriptBytes).removePrefix("\uFEFF")
            if (script.isBlank()) invalid("SCRIPT_EMPTY")
            val source = """
                return (function() {
                    const module = {exports: {}};
                    const exports = module.exports;
                    (function() {
                $script
                    })();
                    const provider = module.exports;
                    const metadata = JSON.parse(${JSONObject.quote(metadata.toString())});
                    const known = ${JSONArray(capabilities)};
                    if (!provider || typeof provider !== 'object' || Array.isArray(provider)) throw new TypeError('Invalid provider export');
                    const actual = known.filter(key => typeof provider[key] === 'function');
                    if (actual.length !== metadata.capabilities.length || metadata.capabilities.some(key => !actual.includes(key))) throw new TypeError('Provider capability mismatch');
                    return Object.assign(provider, {id: metadata.id, name: metadata.name, version: metadata.version,
                        author: metadata.author || '', description: metadata.description || '',
                        labels: metadata.labels || [], tags: metadata.tags || [], settings: metadata.settings || {},
                        displayName: metadata.displayName || '', homepage: metadata.homepage || '', repository: metadata.repository || '',
                        license: metadata.license || '', type: metadata.type || [], minAppVersion: metadata.minAppVersion || '',
                        requiredRuntimeFeatures: metadata.requiredRuntimeFeatures || [], iconBase64: metadata.iconBase64 || ''});
                })();
            """.trimIndent()
            if (source.toByteArray(Charsets.UTF_8).size > ModuleSourceDecoder.MAX_SOURCE_BYTES) invalid("SIZE")
            source
        } catch (error: JSONException) {
            throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error, ModuleLoadStage.MANIFEST, "ARCH_MANIFEST")
        }
    }

    private fun checksum(bytes: ByteArray, expected: Any?, code: String) {
        if (expected !is String || !Regex("[0-9a-f]{64}").matches(expected)) invalid(code)
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != expected) throw ModuleException(ModuleFailure.INTEGRITY, stage = ModuleLoadStage.DECODE, diagnosticCode = "ARCH_$code")
    }

    internal fun normalizeSettings(metadata: JSONObject) {
        val fields = when (val raw = metadata.opt("settings")) {
            null -> JSONObject()
            is JSONObject -> raw
            is JSONArray -> JSONObject().apply {
                for (index in 0 until raw.length()) {
                    val field = raw.getJSONObject(index)
                    val key = field.opt("key") as? String ?: invalid("SETTINGS")
                    if (key.isBlank() || has(key)) invalid("SETTINGS")
                    put(key, field)
                }
            }
            else -> invalid("SETTINGS")
        }
        fields.keys().forEach { key ->
            val field = fields.getJSONObject(key)
            if (!field.has("defaultValue") && !field.has("value") && field.has("default")) field.put("defaultValue", field.get("default"))
        }
        if (metadata.has("qualityOptions")) {
            val declared = metadata.getJSONArray("qualityOptions")
            if (declared.length() !in 1..256 || fields.has("quality")) invalid("QUALITY_OPTIONS")
            val options = JSONArray()
            val ids = ArrayList<String>()
            for (index in 0 until declared.length()) {
                val option = declared.getJSONObject(index)
                val id = option.get("id")
                if (id == JSONObject.NULL || id is JSONObject || id is JSONArray || ids.any { equivalentJsonValue(it, jsonValue(id)) }) invalid("QUALITY_OPTIONS")
                ids.add(jsonValue(id))
                for (key in listOf("label", "description", "kind")) if (option.has(key) && option.get(key) !is String) invalid("QUALITY_OPTIONS")
                val label = option.getString("label")
                if (label.isBlank() || label.length > 256 || option.optString("description").length > 2048 || option.optString("kind").length > 64) invalid("QUALITY_OPTIONS")
                options.put(JSONObject().put("label", label).put("value", id).put("description", option.optString("description")).put("kind", option.optString("kind")))
            }
            val default = if (metadata.has("defaultQuality")) metadata.get("defaultQuality") else declared.getJSONObject(0).get("id")
            if (ids.none { equivalentJsonValue(it, jsonValue(default)) }) invalid("QUALITY_OPTIONS")
            val label = metadata.optString("qualityLabel", "quality")
            if (label.isBlank() || label.length > 256) invalid("QUALITY_OPTIONS")
            fields.put("quality", JSONObject().put("type", "select").put("label", label)
                .put("defaultValue", default).put("options", options))
        } else if (metadata.has("defaultQuality")) invalid("QUALITY_OPTIONS")
        metadata.put("settings", fields)
    }

    internal fun manifest(bytes: ByteArray): JSONObject {
        val text = utf8(bytes).removePrefix("\uFEFF")
        try {
            JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                if (reader.peek() != JsonToken.BEGIN_OBJECT) invalid("MANIFEST")
                var depth = 0
                while (reader.peek() != JsonToken.END_DOCUMENT) {
                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> { depth++; if (depth > 32) invalid("MANIFEST_DEPTH"); reader.beginObject() }
                        JsonToken.BEGIN_ARRAY -> { depth++; if (depth > 32) invalid("MANIFEST_DEPTH"); reader.beginArray() }
                        JsonToken.END_OBJECT -> { reader.endObject(); depth-- }
                        JsonToken.END_ARRAY -> { reader.endArray(); depth-- }
                        JsonToken.NAME -> reader.nextName()
                        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                        JsonToken.BOOLEAN -> reader.nextBoolean()
                        JsonToken.NULL -> reader.nextNull()
                        JsonToken.END_DOCUMENT -> Unit
                    }
                }
            }
        } catch (error: IOException) {
            throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error, ModuleLoadStage.MANIFEST, "ARCH_MANIFEST")
        }
        return JSONObject(text)
    }

    internal fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (error: CharacterCodingException) {
        throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error, ModuleLoadStage.DECODE, "ARCH_UTF8")
    }

    private fun invalid(code: String): Nothing = throw ModuleException(ModuleFailure.INVALID_DOCUMENT,
        stage = ModuleLoadStage.DECODE, diagnosticCode = "ARCH_$code")
}
