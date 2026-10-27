/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.graphics.BitmapFactory
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject

enum class ModuleFailure { INVALID_DOCUMENT, UNSUPPORTED, INVALID_CONFIGURATION, NETWORK, TIMEOUT, EXECUTION, INTEGRITY }

enum class ModuleLoadStage { DOWNLOAD, DECODE, ENGINE, HOST, PROVIDER, MANIFEST, STORAGE }

class ModuleException(val failure: ModuleFailure, cause: Throwable? = null, val stage: ModuleLoadStage? = null,
    val diagnosticCode: String? = null) : Exception(failure.name, cause)

enum class ModuleSettingType { TEXT, PASSWORD, NUMBER, BOOLEAN, SELECT, UNSUPPORTED }

data class ModuleOption(val label: String, val valueJson: String, val description: String = "", val kind: String = "")

data class ModuleSetting(
    val key: String,
    val label: String,
    val description: String,
    val type: ModuleSettingType,
    val defaultJson: String,
    val required: Boolean,
    val secret: Boolean,
    val options: List<ModuleOption>,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val integerOnly: Boolean = false,
)

data class ModuleManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val labels: List<String>,
    val settings: List<ModuleSetting>,
    val capabilities: List<String>,
    val displayName: String = "",
    val homepage: String = "",
    val repository: String = "",
    val license: String = "",
    val types: List<String> = emptyList(),
    val minAppVersion: String = "",
    val requiredRuntimeFeatures: List<String> = emptyList(),
    val icon: ByteString? = null,
    val authentication: Boolean = false,
) {
    companion object {
        fun parse(value: JSONObject): ModuleManifest {
            val schema = value.optJSONObject("settings") ?: JSONObject()
            if (schema.length() > 128) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            val fields = schema.keys().asSequence().map { key ->
                val field = schema.optJSONObject(key) ?: JSONObject()
                val rawType = field.optString("type", "text").lowercase(java.util.Locale.ROOT)
                val secret = field.optBoolean("secret") || rawType == "password" ||
                    Regex("(?i)token|password|secret|credential|api.?key|cookie|authorization").containsMatchIn(key)
                val type = when (rawType) {
                    "text", "string", "url" -> if (secret) ModuleSettingType.PASSWORD else ModuleSettingType.TEXT
                    "password" -> ModuleSettingType.PASSWORD
                    "number", "integer" -> ModuleSettingType.NUMBER
                    "boolean", "switch", "toggle", "checkbox" -> ModuleSettingType.BOOLEAN
                    "select", "selector", "dropdown" -> ModuleSettingType.SELECT
                    else -> ModuleSettingType.UNSUPPORTED
                }
                val options = field.optJSONArray("options") ?: JSONArray()
                if (options.length() > 256 || key.length > 128 || field.optString("label").length > 256) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                ModuleSetting(
                    key, field.optString("label", key), field.optString("description"), type,
                    jsonValue(field.opt("defaultValue") ?: field.opt("value") ?: JSONObject.NULL),
                    field.optBoolean("required"), secret,
                    (0 until options.length()).map { index ->
                        val option = options.opt(index)
                        if (option is JSONObject) ModuleOption(option.optString("label", option.opt("value").toString()), jsonValue(option.opt("value")), option.optString("description"), option.optString("kind"))
                        else ModuleOption(option.toString(), jsonValue(option))
                    },
                    field.optDouble("min").takeIf(Double::isFinite),
                    field.optDouble("max").takeIf(Double::isFinite),
                    rawType == "integer",
                )
            }.toList()
            return ModuleManifest(
                value.optString("id"), value.optString("name"), value.optString("version"),
                value.optString("author"), value.optString("description"),
                (value.optJSONArray("labels").strings() + value.optJSONArray("tags").strings()).distinct(), fields, value.optJSONArray("capabilities").strings(),
                value.optString("displayName"), projectUrl(value, "homepage"), projectUrl(value, "repository"), value.optString("license"),
                stringList(value, "type"), value.optString("minAppVersion"), stringList(value, "requiredRuntimeFeatures"),
                value.optString("iconBase64").takeIf(String::isNotBlank)?.let(::moduleIcon),
                value.optBoolean("authentication"),
            ).also {
                if (it.id.isBlank() || it.name.isBlank() || !it.capabilities.containsAll(listOf("searchTracks", "getTrackStreamUrl"))) {
                    throw ModuleException(ModuleFailure.UNSUPPORTED)
                }
                if (it.id.length > 256 || it.name.length > 256 || it.version.length > 128 || it.description.length > 8192) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                if (it.displayName.length > 256 || it.license.length > 256 ||
                    (it.minAppVersion.isNotBlank() && !Regex("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}").matches(it.minAppVersion))) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            }
        }

        private fun projectUrl(value: JSONObject, key: String): String = value.optString(key).takeIf(String::isNotBlank)?.let {
            if (it.length > 2048) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            ModuleHttpClient.webUrl(it)
        }.orEmpty()

        private fun stringList(value: JSONObject, key: String): List<String> {
            if (!value.has(key)) return emptyList()
            val values = when (val raw = value.get(key)) {
                is String -> listOf(raw)
                is JSONArray -> (0 until raw.length()).map { raw.get(it) as? String ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT) }
                else -> throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            }
            if (values.size > 32 || values.any { it.isBlank() || it.length > 128 }) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            return values.distinct()
        }
    }
}

fun moduleIcon(encoded: String): ByteString {
    if (encoded.length > 349528) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
    val bytes = encoded.decodeBase64() ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
    if (bytes.size > 256 * 1024 || bytes.size < 24 || bytes.substring(0, 8).hex() != "89504e470d0a1a0a") throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
    val data = bytes.toByteArray()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outMimeType != "image/png" || bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
    return bytes
}

fun ModuleManifest.requireCompatible(appVersion: String) {
    val features = setOf("fetch@1", "settings@1", "streamResolution@1", "packagedIcon@1", "qualityOptions@1")
    if (requiredRuntimeFeatures.any { it !in features }) throw ModuleException(ModuleFailure.UNSUPPORTED,
        stage = ModuleLoadStage.MANIFEST, diagnosticCode = "ARCH_RUNTIME_FEATURE")
    if (minAppVersion.isBlank()) return
    val current = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+").find(appVersion)?.value?.split('.')?.mapNotNull(String::toIntOrNull)
    val required = minAppVersion.split('.').map(String::toInt)
    if (current == null || current.size != 3 || current.zip(required).firstOrNull { it.first != it.second }?.let { it.first < it.second } == true) {
        throw ModuleException(ModuleFailure.UNSUPPORTED, stage = ModuleLoadStage.MANIFEST, diagnosticCode = "ARCH_APP_VERSION")
    }
}

data class ModuleTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationSeconds: Int?,
    val explicit: Boolean?,
    val identifier: String?,
)

data class ModuleStream(
    val url: String,
    val headers: Map<String, String>,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val expiresAtMs: Long,
    val variant: String,
    val upstreamProvider: String = "",
    val upstreamId: String = "",
    val inlineManifest: String? = null,
    val allowedDomains: List<String> = emptyList(),
)

data class ModuleDocument(val source: String, val origin: String, val manifest: ModuleManifest, val updateUrl: String = origin)

fun ModuleSetting.acceptsValue(json: String): Boolean = try {
    val value = parseJsonValue(json)
    val minimum = this.minimum
    val maximum = this.maximum
    if (value == JSONObject.NULL) !required else if (required && value.toString().isBlank()) false else when (type) {
        ModuleSettingType.TEXT, ModuleSettingType.PASSWORD -> value is String
        ModuleSettingType.BOOLEAN -> value is Boolean
        ModuleSettingType.NUMBER -> value is Number && value.toDouble().isFinite() &&
            (!integerOnly || value.toDouble() % 1.0 == 0.0) &&
            (minimum == null || value.toDouble() >= minimum) && (maximum == null || value.toDouble() <= maximum)
        ModuleSettingType.SELECT -> options.any { equivalentJsonValue(it.valueJson, json) }
        ModuleSettingType.UNSUPPORTED -> !required
    }
} catch (_: org.json.JSONException) { false }

data class ModuleCatalogEntry(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val downloadUrl: String?,
    val supported: Boolean,
    val labels: List<String>,
    val capabilities: List<String> = emptyList(),
    val sha256: String = "",
)

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

fun jsonValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

fun parseJsonValue(value: String): Any {
    val input = org.json.JSONTokener("[$value]")
    val array = input.nextValue() as? JSONArray ?: throw org.json.JSONException("Invalid setting value")
    if (array.length() != 1 || input.nextClean() != '\u0000') throw org.json.JSONException("Invalid setting value")
    return array.get(0)
}

fun equivalentJsonValue(first: String, second: String): Boolean = try {
    fun equal(left: Any?, right: Any?): Boolean = when {
        left is Number && right is Number -> left.toDouble() == right.toDouble()
        left is JSONObject && right is JSONObject -> left.length() == right.length() &&
            left.keys().asSequence().all { right.has(it) && equal(left.opt(it), right.opt(it)) }
        left is JSONArray && right is JSONArray -> left.length() == right.length() && (0 until left.length()).all { equal(left.opt(it), right.opt(it)) }
        else -> left == right
    }
    equal(parseJsonValue(first), parseJsonValue(second))
} catch (_: org.json.JSONException) { false }
