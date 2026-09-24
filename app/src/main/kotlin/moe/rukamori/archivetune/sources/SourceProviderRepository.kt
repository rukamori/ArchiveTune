package moe.rukamori.archivetune.sources

import android.text.Html
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceProviderRepository @Inject constructor(
    private val http: SourceHttpClient,
    private val modules: SourceModuleRepository,
) {
    suspend fun identify(raw: String, id: String = UUID.randomUUID().toString()): SourceConfiguration {
        val url = SourceHttpClient.address(raw)
        val base = addonBase(url)
        val addresses = if (url.encodedPath.endsWith(".json")) listOf(url)
            else listOf(endpoint(base, "index.json"), endpoint(base, "manifest.json"), url)
        var problem: SourceException = SourceException(SourceProblem.INVALID_RESPONSE)
        for (address in addresses.distinct()) {
            try {
                val document = http.document(address)
                if (document.keys.any { it.startsWith("category:") }) {
                    val count = modules.entries(document).size
                    if (count == 0) throw SourceException(SourceProblem.UNSUPPORTED)
                    return SourceConfiguration(id, SourceKind.MODULE, url.host, address.toString(), moduleCount = count)
                }
                if (document["resources"] is JsonArray || document.text("id").isNotBlank()) {
                    val resources = document.array("resources").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    if (resources.isNotEmpty() && "search" !in resources) throw SourceException(SourceProblem.UNSUPPORTED)
                    return SourceConfiguration(id, SourceKind.ADDON, document.text("name").ifBlank { url.host }, base.toString())
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: SourceException) {
                problem = failure
            }
        }
        throw problem
    }

    suspend fun check(source: SourceConfiguration) {
        when (source.kind) {
            SourceKind.ADDON, SourceKind.MODULE -> identify(source.url, source.id)
            SourceKind.JIOSAAVN -> http.document(saavn("search.getResults").newBuilder().addQueryParameter("q", "music").addQueryParameter("n", "1").build())
            SourceKind.YOUTUBE -> Unit
        }
    }

    suspend fun search(source: SourceConfiguration, target: TrackIdentity): List<SourceCandidate> {
        val query = "${target.title} ${target.artist}"
        return when (source.kind) {
            SourceKind.ADDON -> {
                val base = SourceHttpClient.address(source.url)
                val result = http.document(endpoint(base, "search").newBuilder().addQueryParameter("q", query).build())
                result.array("tracks").mapNotNull { (it as? JsonObject)?.candidate() }.take(50)
            }
            SourceKind.MODULE -> modules.search(source, query)
            SourceKind.JIOSAAVN -> http.document(saavn("search.getResults").newBuilder()
                .addQueryParameter("q", query).addQueryParameter("n", "40").build()).array("results").mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val more = item.obj("more_info")
                val artists = more.obj("artistMap").array("primary_artists").mapNotNull { (it as? JsonObject)?.text("name") }
                val artist = artists.joinToString(", ").ifBlank { more.text("singers").ifBlank { item.text("subtitle") } }
                val id = item.text("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                SourceCandidate(id, TrackIdentity(
                    clean(item.text("title")), clean(artist), clean(more.text("album")),
                    more.number("duration")?.toInt(), item.text("explicit_content").takeIf(String::isNotBlank)?.let { it == "1" },
                ))
            }
            SourceKind.YOUTUBE -> emptyList()
        }
    }

    suspend fun stream(source: SourceConfiguration, candidate: SourceCandidate, lossless: Boolean, low: Boolean): SourceAudio = when (source.kind) {
        SourceKind.MODULE -> modules.stream(source, candidate, lossless, low)
        SourceKind.ADDON -> {
            if (candidate.directUrl != null) {
                parseAudio(JsonObject(mapOf("url" to JsonPrimitive(candidate.directUrl), "format" to JsonPrimitive(candidate.format.orEmpty()))))
            } else {
                val base = SourceHttpClient.address(source.url)
                val manifest = http.document(endpoint(base, "manifest.json"))
                val builder = endpoint(base, "stream", candidate.id).newBuilder()
                manifest.array("settings").forEach { element ->
                    val setting = element as? JsonObject ?: return@forEach
                    val key = setting.text("key").takeIf(String::isNotBlank) ?: return@forEach
                    val options = setting.array("options").mapNotNull { (it as? JsonObject)?.text("value") }
                    val preferred = when { low -> listOf("96", "low", "128"); lossless -> listOf("hires", "hi_res_lossless", "lossless", "flac", "best"); else -> listOf("320", "high", "aac", "mp3") }
                    val value = if (key.equals("quality", true)) preferred.firstNotNullOfOrNull { wanted -> options.firstOrNull { it.equals(wanted, true) } }
                        ?: setting.text("default") else setting.text("default")
                    if (value.isNotEmpty()) builder.addQueryParameter(key, value)
                }
                parseAudio(http.document(builder.build()))
            }
        }
        SourceKind.JIOSAAVN -> {
            val response = http.document(saavn("song.getDetails").newBuilder().addQueryParameter("pids", candidate.id).build())
            val song = response.array("songs").firstOrNull() as? JsonObject
                ?: response[candidate.id] as? JsonObject ?: throw SourceException(SourceProblem.NO_MATCH)
            val info = song.obj("more_info")
            val encoded = info.text("encrypted_media_url").ifBlank { song.text("encrypted_media_url") }
            if (encoded.isBlank()) throw SourceException(SourceProblem.NO_MATCH)
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES"))
            val decoded = cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT)).toString(Charsets.UTF_8)
            val rate = if (low) 96 else if (info.text("320kbps") == "true" || song.text("320kbps") == "true") 320 else 160
            val url = decoded.replace(Regex("_(96|160|320)\\.mp4"), "_${rate}.mp4")
            SourceHttpClient.address(url)
            SourceAudio(url, codec = "aac", mimeType = "audio/mp4", bitrate = rate * 1000,
                expiresAtMs = System.currentTimeMillis() + 30 * 60_000, rendition = rate.toString())
        }
        SourceKind.YOUTUBE -> throw SourceException(SourceProblem.UNSUPPORTED)
    }

    private fun JsonObject.candidate(): SourceCandidate? {
        val id = text("id").takeIf(String::isNotBlank) ?: return null
        return SourceCandidate(id, TrackIdentity(text("title"), text("artist"), text("album"), number("duration")?.toInt(),
            (get("explicit") as? JsonPrimitive)?.booleanOrNull), directUrl = text("streamURL").takeIf(String::isNotBlank), format = text("format"))
    }

    private fun saavn(call: String): HttpUrl = SourceHttpClient.address("https://www.jiosaavn.com/api.php").newBuilder()
        .addQueryParameter("__call", call).addQueryParameter("_format", "json")
        .addQueryParameter("_marker", "0").addQueryParameter("api_version", "4").addQueryParameter("ctx", "web6dot0").build()

    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

    companion object {
        private val BITRATE_LABEL = Regex("""\b(\d{1,4})\s*(?:kbps|kbit/s)\b""", RegexOption.IGNORE_CASE)
        private val SAAVN_BITRATE = Regex("""_(12|48|96|128|160|320)\.(?:mp4|mp3)$""")

        internal fun addonBase(url: HttpUrl): HttpUrl = url.newBuilder().apply {
            if (url.encodedPath.endsWith("/manifest.json")) removePathSegment(url.pathSegments.lastIndex)
        }.build()
        internal fun endpoint(base: HttpUrl, vararg segments: String): HttpUrl = base.newBuilder().apply {
            if (base.pathSegments.lastOrNull().isNullOrEmpty()) removePathSegment(base.pathSegments.lastIndex)
            segments.forEach(::addPathSegment)
        }.build()

        internal fun parseAudio(item: JsonObject): SourceAudio {
            val encrypted = item.text("encrypted")
            if (encrypted.isNotEmpty() && encrypted !in listOf("false", "none", "null")) throw SourceException(SourceProblem.UNSUPPORTED)
            val url = item.text("url").ifBlank { item.text("streamUrl") }
            SourceHttpClient.address(url)
            val transportValue = item.text("manifest").ifBlank { item.text("mediaType").ifBlank { item.text("format") } }.lowercase()
            val path = SourceHttpClient.address(url).encodedPath.lowercase()
            val transport = when {
                transportValue in listOf("dash", "mpd", "application/dash+xml") || path.endsWith(".mpd") -> StreamTransport.DASH
                transportValue in listOf("hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl") || path.endsWith(".m3u8") -> StreamTransport.HLS
                else -> StreamTransport.PROGRESSIVE
            }
            val reportedMime = item.text("mimeType").substringBefore(';').trim().lowercase()
            val quality = item.text("audioQuality").ifBlank { item.text("quality") }
            val reportedCodec = item.text("codec").ifBlank { item.text("fileCodec").ifBlank { item.text("format") } }.lowercase()
            val codec = reportedCodec.ifBlank {
                when {
                    reportedMime == "audio/flac" -> "flac"
                    reportedMime == "audio/mpeg" || path.endsWith(".mp3") -> "mp3"
                    reportedMime == "audio/mp4" || reportedMime == "audio/aac" || path.endsWith(".m4a") -> "aac"
                    reportedMime == "audio/opus" || path.endsWith(".opus") -> "opus"
                    reportedMime == "audio/wav" || path.endsWith(".wav") -> "pcm"
                    quality.contains("flac", true) || quality.contains("lossless", true) || path.endsWith(".flac") -> "flac"
                    else -> ""
                }
            }
            val mime = reportedMime.ifBlank { when (codec) {
                "flac" -> "audio/flac"; "alac", "aac", "m4a", "mp4" -> "audio/mp4"
                "mp3", "mpeg" -> "audio/mpeg"; "opus", "ogg", "vorbis" -> "audio/ogg"
                "wav", "pcm" -> "audio/wav"; else -> ""
            } }
            val expiry = item.number("expiresAt")?.toLong()?.let { if (it < 10_000_000_000L) it * 1000 else it }
                ?: System.currentTimeMillis() + 10 * 60_000
            val reportedBitrate = item.number("bitrate")?.takeIf { it > 0 }
            val pathBitrate = SAAVN_BITRATE.find(path)?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { SourceHttpClient.address(url).host.endsWith(".saavncdn.com") }
            val labelledBitrate = BITRATE_LABEL.find(quality)?.groupValues?.get(1)?.toIntOrNull()
            val bitrate = reportedBitrate?.let { if (it <= 3000) it * 1000 else it }?.toInt()
                ?: ((pathBitrate ?: labelledBitrate ?: 0) * 1000)
            return SourceAudio(url, item.obj("headers").mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }.toMap(),
                codec, mime, transport, bitrate, item.number("sampleRate")?.let { if (it < 1000) it * 1000 else it }?.toInt(),
                item.number("bitDepth")?.toInt(), item.number("contentLength")?.toLong() ?: -1, expiry,
                item.text("quality").ifBlank { item.text("audioQuality") })
        }
    }
}

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
