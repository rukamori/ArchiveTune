/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import moe.rukamori.archivetune.BuildConfig

data class ModuleMetadata(val name: String, val author: String, val description: String)

data class InstalledModule(
    val id: String,
    val manifest: ModuleManifest,
    val source: String,
    val origin: String,
    val enabled: Boolean,
    val configuration: Map<String, String>,
    val revision: String,
    val updateUrl: String = origin,
    val metadata: ModuleMetadata? = null,
)

data class SavedModuleCatalog(
    val id: String,
    val name: String,
    val url: String,
    val refreshedAtMs: Long,
    val entries: List<ModuleCatalogEntry>,
)

data class ModuleRegistry(
    val enabled: Boolean = false,
    val modules: List<InstalledModule> = emptyList(),
    val catalogs: List<SavedModuleCatalog> = emptyList(),
    val revision: String = UUID.randomUUID().toString(),
)

sealed interface ModuleImport {
    data class Provider(val document: ModuleDocument, val replacementId: String?) : ModuleImport
    data class Catalog(val catalog: SavedModuleCatalog) : ModuleImport
}

@Singleton
class ModuleRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val storage = AtomicFile(File(context.filesDir, "modules/registry.json"))
    private val mutex = Mutex()
    private val keyLock = Any()
    private val mutableState = MutableStateFlow<ModuleRegistry?>(null)
    val state = mutableState.asStateFlow()
    val http = ModuleHttpClient(ModuleNetworkPolicy.client(streaming = false))
    val runtime = ModuleRuntime(context, http)

    suspend fun snapshot(): ModuleRegistry = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableState.value ?: read().also { mutableState.value = it }
        }
    }

    suspend fun setEnabled(value: Boolean) = change { it.copy(enabled = value) }

    suspend fun setModuleEnabled(id: String, value: Boolean) = change { registry ->
        registry.copy(modules = registry.modules.map { if (it.id == id) it.copy(enabled = value) else it })
    }

    suspend fun metadata(id: String, value: ModuleMetadata) = change { registry ->
        require(registry.modules.any { it.id == id })
        registry.copy(modules = registry.modules.map { if (it.id == id) it.copy(metadata = value) else it })
    }

    suspend fun reorder(ids: List<String>) = change { registry ->
        require(ids.size == registry.modules.size && ids.toSet() == registry.modules.map { it.id }.toSet())
        val modules = registry.modules.associateBy { it.id }
        registry.copy(modules = ids.map { modules.getValue(it) })
    }

    suspend fun removeModule(id: String) {
        change { it.copy(modules = it.modules.filterNot { module -> module.id == id }) }
        runtime.forget(id)
    }

    suspend fun removeCatalog(id: String) = change { it.copy(catalogs = it.catalogs.filterNot { catalog -> catalog.id == id }) }

    suspend fun settings(id: String, values: Map<String, String>) {
        change { registry ->
            registry.copy(modules = registry.modules.map { module ->
                if (module.id != id) module else {
                    val configuration = module.manifest.settings.associate { it.key to (values[it.key] ?: it.defaultJson) }
                    validateConfiguration(module.manifest, configuration)
                    module.copy(configuration = configuration, revision = UUID.randomUUID().toString())
                }
            })
        }
        runtime.unload(id)
    }

    suspend fun importFile(uri: Uri): ModuleImport = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readModuleBytes(ModuleSourceDecoder.MAX_SOURCE_BYTES) }
            ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        if (bytes.size > ModuleSourceDecoder.MAX_SOURCE_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        prepare(bytes, "", null)
    }

    suspend fun importUrl(url: String, replacementId: String? = null, expectedDigest: String = ""): ModuleImport = try {
        if (expectedDigest.isNotBlank() && !Regex("[0-9a-f]{64}").matches(expectedDigest)) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val (bytes, origin) = http.source(url.trim())
        if (expectedDigest.isNotBlank()) {
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            if (actual != expectedDigest) throw ModuleException(ModuleFailure.INTEGRITY, stage = ModuleLoadStage.DOWNLOAD, diagnosticCode = "CATALOG_CHECKSUM")
        }
        val prepared = prepare(bytes, origin, replacementId)
        if (prepared is ModuleImport.Provider) prepared.copy(document = prepared.document.copy(updateUrl = ModuleHttpClient.webUrl(url.trim()))) else prepared
    } catch (error: CancellationException) { throw error }
    catch (error: ModuleException) { throw error }
    catch (error: java.io.IOException) { throw ModuleException(ModuleFailure.NETWORK, error, ModuleLoadStage.DOWNLOAD) }
    catch (error: org.json.JSONException) { throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error, ModuleLoadStage.DECODE) }

    suspend fun catalog(url: String, existingId: String? = null, initialText: String? = null): ModuleImport.Catalog {
        val existing = snapshot().catalogs.firstOrNull { it.id == existingId }
        val document = ModuleCatalogReader(http).read(url, initialText)
        return ModuleImport.Catalog(SavedModuleCatalog(existing?.id ?: UUID.randomUUID().toString(),
            existing?.name ?: document.name, document.origin, System.currentTimeMillis(), document.entries))
    }

    suspend fun refreshCatalog(id: String) {
        val source = snapshot().catalogs.first { it.id == id }
        commit(catalog(source.url, id))
    }

    suspend fun commit(pending: ModuleImport) {
        when (pending) {
            is ModuleImport.Catalog -> change { registry ->
                val existing = registry.catalogs.indexOfFirst { it.id == pending.catalog.id || it.url == pending.catalog.url }
                registry.copy(catalogs = registry.catalogs.toMutableList().apply {
                    if (existing < 0) add(pending.catalog) else set(existing, pending.catalog.copy(id = get(existing).id))
                })
            }
            is ModuleImport.Provider -> {
                change { registry ->
                    val existing = registry.modules.firstOrNull { it.id == pending.replacementId }
                        ?: registry.modules.firstOrNull { it.manifest.id == pending.document.manifest.id &&
                            (it.origin == pending.document.origin || it.updateUrl == pending.document.updateUrl) }
                    if (existing != null && existing.manifest.id != pending.document.manifest.id) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                    val manifest = pending.document.manifest
                    val configuration = manifest.settings.associate { field ->
                        field.key to (existing?.configuration?.get(field.key)?.takeIf { value -> validValue(field, value) } ?: field.defaultJson)
                    }
                    val module = InstalledModule(existing?.id ?: UUID.randomUUID().toString(), manifest, pending.document.source,
                        pending.document.origin, existing?.enabled ?: true, configuration, UUID.randomUUID().toString(), pending.document.updateUrl, existing?.metadata)
                    registry.copy(modules = registry.modules.toMutableList().apply {
                        if (existing == null) add(module) else set(indexOfFirst { it.id == existing.id }, module)
                    })
                }
                pending.replacementId?.let { runtime.unload(it) }
            }
        }
    }

    suspend fun check(module: InstalledModule) {
        validateConfiguration(module.manifest, module.configuration)
        runtime.inspect(module.source, module.origin)
        if (module.manifest.authentication) runtime.checkAuthorization(module.id, module.source, module.origin)
    }

    suspend fun authorize(id: String): String? {
        val module = snapshot().modules.first { it.id == id }
        return runtime.authorize(id, module.source, module.origin)
    }

    suspend fun completeAuthorization(callback: String): String {
        for (module in snapshot().modules.filter { it.manifest.authentication }) {
            if (runtime.completeAuthorization(module.id, module.source, module.origin, callback)) {
                runtime.unload(module.id)
                return module.id
            }
        }
        throw ModuleException(ModuleFailure.INVALID_CONFIGURATION, stage = ModuleLoadStage.PROVIDER, diagnosticCode = "SESSION_CALLBACK_STATE")
    }

    suspend fun exportPortable(): ByteArray {
        val registry = snapshot()
        return withContext(Dispatchers.IO) { encode(registry, portable = true).toString().toByteArray() }
    }

    suspend fun restorePortable(bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_REGISTRY_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val restored = decode(JSONObject(bytes.toString(Charsets.UTF_8)), portable = true)
        change(replacement = restored) { restored.copy(enabled = false, modules = restored.modules.map { it.copy(enabled = false) }) }
    }

    private suspend fun prepare(bytes: ByteArray, origin: String, replacementId: String?,
        visited: Set<String> = emptySet(), depth: Int = 0): ModuleImport = withContext(Dispatchers.Default) {
        ensureActive()
        if (depth > 4 || (origin.isNotBlank() && origin in visited)) throw ModuleException(ModuleFailure.INVALID_DOCUMENT, stage = ModuleLoadStage.DECODE)
        if (ModulePackageReader.isPackage(bytes)) {
            val source = ModulePackageReader.read(bytes, BuildConfig.VERSION_NAME)
            val manifest = runtime.inspect(source, origin)
            return@withContext ModuleImport.Provider(ModuleDocument(source, origin, manifest), replacementId)
        }
        val text = bytes.toString(Charsets.UTF_8)
        val trimmed = text.removePrefix("\uFEFF").trim()
        val json = if (trimmed.startsWith("{")) try { JSONObject(trimmed) } catch (_: org.json.JSONException) { null } else null
        if (json != null && json.keys().asSequence().any { it.startsWith("category:") || it in listOf("modules", "external_sources", "extensions") }) {
            return@withContext catalog(origin, initialText = trimmed)
        }
        if (json != null) {
            if (ModuleExtensionReader.isManifest(json)) {
                val base = origin.takeIf(String::isNotBlank) ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                val files = mutableMapOf("index.js" to http.source(ModuleHttpClient.resolveUrl("index.js", base)).first)
                json.optString("icon").takeIf(String::isNotBlank)?.let { icon ->
                    if (icon !in setOf("icon.png", "icon.jpg", "icon.jpeg")) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                    files[icon] = http.source(ModuleHttpClient.resolveUrl(icon, base)).first
                }
                val source = ModuleExtensionReader.read(files, json)
                return@withContext ModuleImport.Provider(ModuleDocument(source, origin, runtime.inspect(source, origin)), replacementId)
            }
            val target = json.optString("download").ifBlank { json.optString("download_url") }.ifBlank { json.optString("url") }
            if (target.isBlank()) throw ModuleException(ModuleFailure.UNSUPPORTED)
            val (source, address) = http.source(ModuleHttpClient.resolveUrl(target, origin))
            val prepared = prepare(source, address, replacementId, visited + origin, depth + 1)
            return@withContext if (prepared is ModuleImport.Provider) prepared.copy(document = prepared.document.copy(updateUrl = origin.ifBlank { address })) else prepared
        }
        val manifest = runtime.inspect(text, origin)
        ModuleImport.Provider(ModuleDocument(text, origin, manifest), replacementId)
    }

    private suspend fun change(replacement: ModuleRegistry? = null, transform: (ModuleRegistry) -> ModuleRegistry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = replacement ?: mutableState.value ?: read()
            val next = transform(current).copy(revision = UUID.randomUUID().toString())
            if (next.modules.size > 256 || next.catalogs.size > 128) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            val bytes = encode(next, portable = false).toString().toByteArray()
            if (bytes.size > MAX_REGISTRY_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            storage.baseFile.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw java.io.IOException("Module storage unavailable") }
            val output = storage.startWrite()
            try { output.write(bytes); storage.finishWrite(output) }
            catch (error: Throwable) { storage.failWrite(output); throw error }
            mutableState.value = next
        }
    }

    private fun read(): ModuleRegistry {
        if (!storage.baseFile.exists() && !File(storage.baseFile.path + ".bak").exists()) return ModuleRegistry()
        val bytes = storage.openRead().use { it.readModuleBytes(MAX_REGISTRY_BYTES) }
        if (bytes.size > MAX_REGISTRY_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        return decode(JSONObject(bytes.toString(Charsets.UTF_8)), portable = false)
    }

    internal fun encode(registry: ModuleRegistry, portable: Boolean): JSONObject = JSONObject()
        .put("version", 1).put("enabled", registry.enabled).put("revision", registry.revision)
        .put("modules", JSONArray().apply {
            registry.modules.forEach { module ->
                val settings = JSONObject()
                module.configuration.forEach { (key, value) ->
                    if (!portable || module.manifest.settings.none { it.key == key && it.secret }) settings.put(key, parseJsonValue(value))
                }
                put(JSONObject().put("id", module.id).put("manifest", manifestJson(module.manifest)).put("source", module.source)
                    .put("origin", module.origin).put("enabled", module.enabled).put("revision", module.revision)
                    .put("updateUrl", module.updateUrl)
                    .put("metadata", module.metadata?.let { JSONObject().put("name", it.name).put("author", it.author).put("description", it.description) })
                    .put("configuration", if (portable) settings else encrypt(settings.toString(), module.id)))
            }
        }).put("catalogs", JSONArray().apply {
            registry.catalogs.forEach { catalog ->
                put(JSONObject().put("id", catalog.id).put("name", catalog.name).put("url", catalog.url).put("refreshed", catalog.refreshedAtMs)
                    .put("entries", JSONArray().apply { catalog.entries.forEach { entry ->
                        put(JSONObject().put("id", entry.id).put("name", entry.name).put("version", entry.version).put("author", entry.author)
                            .put("description", entry.description).put("download", entry.downloadUrl).put("supported", entry.supported).put("labels", JSONArray(entry.labels))
                            .put("capabilities", JSONArray(entry.capabilities)).put("sha256", entry.sha256))
                    } }))
            }
        })

    internal fun decode(root: JSONObject, portable: Boolean, allowRepeatedInstallations: Boolean = false): ModuleRegistry {
        if (root.getInt("version") != 1) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val modules = root.getJSONArray("modules")
        val catalogs = root.getJSONArray("catalogs")
        if (modules.length() > (if (allowRepeatedInstallations) 2048 else 256) || catalogs.length() > 128) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        return ModuleRegistry(root.optBoolean("enabled"), (0 until modules.length()).map { index ->
            val item = modules.getJSONObject(index)
            val id = UUID.fromString(item.getString("id")).toString()
            val manifest = ModuleManifest.parse(item.getJSONObject("manifest"))
            val configuration = if (portable) item.getJSONObject("configuration") else JSONObject(decrypt(item.getString("configuration"), id))
            val values = manifest.settings.associate { field ->
                field.key to if (portable && field.secret) "null" else if (configuration.has(field.key)) jsonValue(configuration.get(field.key)) else field.defaultJson
            }
            val source = item.getString("source")
            if (source.length > ModuleSourceDecoder.MAX_SOURCE_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            InstalledModule(id, manifest, source, item.optString("origin"), item.optBoolean("enabled"), values, item.getString("revision"),
                item.optString("updateUrl", item.optString("origin")), item.optJSONObject("metadata")?.let {
                    ModuleMetadata(it.getString("name").also { name -> require(name.isNotBlank()) }, it.getString("author"), it.getString("description"))
                })
        }, (0 until catalogs.length()).map { index ->
            val item = catalogs.getJSONObject(index)
            val entries = item.getJSONArray("entries")
            SavedModuleCatalog(item.getString("id"), item.getString("name"), item.getString("url").takeIf(String::isNotBlank)?.let(ModuleHttpClient::webUrl).orEmpty(), item.optLong("refreshed"),
                (0 until entries.length()).map { position ->
                    val entry = entries.getJSONObject(position)
                    ModuleCatalogEntry(entry.getString("id"), entry.getString("name"), entry.optString("version"), entry.optString("author"),
                        entry.optString("description"), entry.optString("download").takeIf { it.isNotBlank() && it != "null" }, entry.optBoolean("supported"), entry.optJSONArray("labels").strings(),
                        entry.optJSONArray("capabilities").strings(), entry.optString("sha256"))
                })
        }, root.optString("revision", UUID.randomUUID().toString())).also { registry ->
            if ((!allowRepeatedInstallations && registry.modules.distinctBy { it.id }.size != registry.modules.size) ||
                registry.catalogs.distinctBy { it.id }.size != registry.catalogs.size) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        }
    }

    private fun manifestJson(manifest: ModuleManifest): JSONObject = JSONObject()
        .put("id", manifest.id).put("name", manifest.name).put("version", manifest.version).put("author", manifest.author)
        .put("description", manifest.description).put("labels", JSONArray(manifest.labels)).put("capabilities", JSONArray(manifest.capabilities))
        .put("displayName", manifest.displayName).put("homepage", manifest.homepage).put("repository", manifest.repository).put("license", manifest.license)
        .put("type", JSONArray(manifest.types)).put("minAppVersion", manifest.minAppVersion).put("requiredRuntimeFeatures", JSONArray(manifest.requiredRuntimeFeatures))
        .put("iconBase64", manifest.icon?.base64()).put("authentication", manifest.authentication)
        .put("settings", JSONObject().apply {
            manifest.settings.forEach { field -> put(field.key, JSONObject().put("label", field.label).put("description", field.description)
                .put("type", if (field.integerOnly) "integer" else field.type.name.lowercase(java.util.Locale.ROOT)).put("defaultValue", parseJsonValue(field.defaultJson)).put("required", field.required)
                .put("secret", field.secret).put("min", field.minimum).put("max", field.maximum)
                .put("options", JSONArray().apply { field.options.forEach { put(JSONObject().put("label", it.label).put("value", parseJsonValue(it.valueJson)).put("description", it.description).put("kind", it.kind)) } })) }
        })

    private fun secretKey(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    internal fun encrypt(text: String, id: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(id.toByteArray())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    internal fun decrypt(text: String, id: String): String {
        val bytes = Base64.decode(text, Base64.DEFAULT)
        if (bytes.size < 28) throw ModuleException(ModuleFailure.INVALID_CONFIGURATION)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(id.toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    companion object {
        const val BACKUP_ENTRY = "modules.json"
        const val MAX_REGISTRY_BYTES = 32 * 1024 * 1024
        private const val KEY_ALIAS = "module_configuration_v1"

        fun validateConfiguration(manifest: ModuleManifest, values: Map<String, String>) {
            manifest.requireCompatible(BuildConfig.VERSION_NAME)
            if (manifest.settings.any { !validValue(it, values[it.key] ?: it.defaultJson) }) throw ModuleException(ModuleFailure.INVALID_CONFIGURATION)
        }

        fun validValue(field: ModuleSetting, json: String): Boolean = field.acceptsValue(json)
    }
}
