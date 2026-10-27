/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

data class ModuleCatalogDocument(val origin: String, val name: String, val entries: List<ModuleCatalogEntry>)

class ModuleCatalogReader(private val http: ModuleHttpClient) {
    suspend fun read(address: String, initialText: String? = null): ModuleCatalogDocument =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(30_000) { readDocument(address.trim(), initialText) }
                ?: throw ModuleException(ModuleFailure.TIMEOUT)
        }

    private suspend fun readDocument(address: String, initialText: String?): ModuleCatalogDocument {
        val entries = LinkedHashMap<String, ModuleCatalogEntry>()
        val visited = HashSet<String>()
        var documents = 0
        var origin = address
        var name = ""

        suspend fun load(url: String, depth: Int, baseOverride: String? = null, suppliedText: String? = null) {
            if (depth > 4) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            val normalized = if (url.isBlank() && suppliedText != null) "file" else ModuleHttpClient.webUrl(url)
            if (!visited.add(normalized)) return
            documents++
            if (documents > 32) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            var (text, finalUrl) = if (suppliedText != null) suppliedText to url else http.document(normalized)
            if (finalUrl.isNotBlank() && finalUrl != normalized && !visited.add(finalUrl)) return
            text = text.removePrefix("\uFEFF").trim()
            if (text.startsWith("<")) {
                val indexUrl = discoverIndex(text, finalUrl)
                if (!visited.add(indexUrl)) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                documents++
                if (documents > 32) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                val document = http.document(indexUrl)
                text = document.first.removePrefix("\uFEFF").trim()
                finalUrl = document.second
                if (finalUrl != indexUrl && !visited.add(finalUrl)) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            }
            val root = try { JSONObject(text) }
            catch (error: JSONException) { throw ModuleException(ModuleFailure.INVALID_DOCUMENT, error) }
            val keys = root.keys().asSequence().toList()
            if (keys.none { it.startsWith("category:") || it == "modules" || it == "external_sources" || it == "extensions" }) {
                throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            }
            if (depth == 0) {
                origin = finalUrl
                name = root.optString("name").ifBlank { root.optString("title") }
            }
            val base = baseOverride?.takeIf(String::isNotBlank)?.let {
                ModuleHttpClient.webUrl(ModuleHttpClient.resolveUrl(it.trimEnd('/') + "/", finalUrl))
            } ?: finalUrl

            fun append(items: JSONArray, providerCategory: Boolean, entryBase: String = base) {
                if (items.length() > 1000) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val direct = item.optString("download").ifBlank { item.optString("download_url") }.ifBlank { item.optString("url") }
                    val file = item.optString("file")
                    val reference = direct.ifBlank {
                        val folder = item.optString("folder").trim('/')
                        if (file.isBlank() || folder.isBlank()) file else "$folder/$file"
                    }
                    val download = reference.takeIf(String::isNotBlank)?.let {
                        ModuleHttpClient.webUrl(ModuleHttpClient.resolveUrl(it, entryBase))
                    }
                    val id = item.optString("id").ifBlank { download.orEmpty() }
                    if (id.isBlank()) continue
                    val checksum = item.optString("sha256")
                    if (checksum.isNotBlank() && !Regex("[0-9a-f]{64}").matches(checksum)) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                    val entry = ModuleCatalogEntry(id, item.optString("display_name").ifBlank { item.optString("name", id) }, item.optString("version"),
                        item.optString("author"), item.optString("description"), download,
                        providerCategory && item.optString("type", "MODULE").equals("MODULE", true) && download != null,
                        (item.optJSONArray("labels").strings() + item.optJSONArray("tags").strings()).distinct(), item.optJSONArray("capabilities").strings(), checksum)
                    entries.putIfAbsent(download ?: "$entryBase#$id", entry)
                    if (entries.size > 1000) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                }
            }

            keys.filter { it.startsWith("category:") || it == "modules" || it == "extensions" }.forEach { key ->
                root.optJSONArray(key)?.let { append(it, key !in listOf("category:artworks", "category:testing")) }
            }
            val external = root.optJSONArray("external_sources") ?: JSONArray()
            if (external.length() > 32) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            for (index in 0 until external.length()) {
                val source = external.optJSONObject(index) ?: continue
                val sourceBase = source.optString("baseUrl").takeIf(String::isNotBlank)?.let {
                    ModuleHttpClient.webUrl(ModuleHttpClient.resolveUrl(it.trimEnd('/') + "/", finalUrl))
                }
                val inline = source.optJSONArray("modules")
                if (inline != null) append(inline, true, sourceBase ?: base)
                else source.optString("url").takeIf(String::isNotBlank)?.let {
                    load(ModuleHttpClient.resolveUrl(it, finalUrl), depth + 1, sourceBase)
                }
            }
        }

        load(address, 0, suppliedText = initialText)
        return ModuleCatalogDocument(origin, name.ifBlank { URI(origin).host.orEmpty() }, entries.values.toList())
    }

    private fun discoverIndex(html: String, origin: String): String {
        if (origin.isBlank()) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val page = Jsoup.parse(html, origin)
        val linked = page.select("link[href], a[href]").firstOrNull { element ->
            val type = element.attr("type").substringBefore(';').trim()
            val path = element.attr("href").substringBefore('?').substringBefore('#').substringAfterLast('/')
            type.equals("application/json", true) || path.lowercase(java.util.Locale.ROOT) in INDEX_NAMES
        }?.attr("href")
        val target = if (linked != null) ModuleHttpClient.resolveUrl(linked, origin) else {
            val uri = URI(origin)
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && !path.endsWith('/')) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            ModuleHttpClient.resolveUrl("index.json", origin)
        }
        return ModuleHttpClient.webUrl(target)
    }

    private companion object {
        val INDEX_NAMES = setOf("index.json", "catalog.json", "source.json")
    }
}
