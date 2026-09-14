/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import moe.rukamori.archivetune.spotify.SpotifyHashProvider
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.UUID

object SpotifyCanvasProvider {
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val SEARCH_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val SEARCH_OPERATION = "searchDesktop"
    private const val WEB_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val APP_USER_AGENT = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"
    private val trackUriPattern = Regex("spotify:track:[A-Za-z0-9]{22}")
    private val configPattern = Regex("""<script[^>]*id="appServerConfig"[^>]*>([^<]+)</script>""")
    private val json = Json { ignoreUnknownKeys = true }
    private val clientTokenMutex = Mutex()
    private var cachedClientToken: ClientToken? = null
    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    addInterceptor { CanvasRequestPolicy.intercept(it, CanvasSource.SPOTIFY) }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            expectSuccess = false
        }
    }

    suspend fun getBySongArtist(song: String, artist: String, accessToken: String, clientId: String): CanvasArtwork? {
        CanvasRequestPolicy.check(CanvasSource.SPOTIFY)
        val query = "$song $artist"
        val variables = buildJsonObject {
            put("searchTerm", query)
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includeArtistHasConcertsField", false)
            put("includePreReleases", false)
            put("includeLocalConcertsField", false)
            put("includeAuthors", false)
        }
        val candidates = searchTracks(variables, accessToken)
            .filter { it.second.matchesSongIdentity(song, artist) }
        if (candidates.isEmpty()) return null
        val urls = getCanvases(candidates.map { it.first }.distinct().take(10), accessToken, clientId)
        return candidates.firstNotNullOfOrNull { (uri, artwork) ->
            urls[uri]?.let { artwork.copy(videoUrl = it, videoUrlVertical = it) }
        }
    }

    private suspend fun searchTracks(variables: JsonObject, accessToken: String): List<Pair<String, CanvasArtwork>> {
        val hashes = listOfNotNull(
            SpotifyHashProvider.getHash(SEARCH_OPERATION),
            SpotifyHashProvider.getPreviousHash(SEARCH_OPERATION),
        ).distinct()
        for (hash in hashes) {
            CanvasRequestPolicy.check(CanvasSource.SPOTIFY)
            val body = buildJsonObject {
                put("variables", variables)
                put("operationName", SEARCH_OPERATION)
                putJsonObject("extensions") {
                    putJsonObject("persistedQuery") {
                        put("version", 1)
                        put("sha256Hash", hash)
                    }
                }
            }
            val response = client.post(SEARCH_URL) {
                header("Authorization", "Bearer $accessToken")
                header("App-Platform", "WebPlayer")
                header("User-Agent", WEB_USER_AGENT)
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
                header("Accept", "application/json")
                setBody(
                    TextContent(
                        body.toString(),
                        ContentType.Application.Json.withParameter("charset", "UTF-8"),
                    ),
                )
            }
            if (response.status.value == 401 || response.status.value == 429) {
                throw RequestException(response.status.value)
            }
            val responseBody = response.bodyAsText()
            if (response.status == HttpStatusCode.PreconditionFailed ||
                responseBody.contains("PersistedQueryNotFound", ignoreCase = true)
            ) {
                continue
            }
            if (response.status != HttpStatusCode.OK) throw RequestException(response.status.value)
            val root = json.parseToJsonElement(responseBody) as? JsonObject
                ?: throw IOException("Spotify search response is invalid")
            val errors = root.array("errors")
            if (errors.isNotEmpty()) throw IOException("Spotify search returned an error")
            return root.obj("data").obj("searchV2").obj("tracksV2").array("items").mapNotNull { item ->
                val wrapper = (item as? JsonObject).obj("item")
                parseTrack(
                    track = wrapper.obj("data"),
                    uriOverride = wrapper.string("_uri") ?: wrapper.string("uri"),
                )
            }
        }
        throw RequestException(HttpStatusCode.PreconditionFailed.value)
    }

    private fun parseTrack(track: JsonObject?, uriOverride: String? = null): Pair<String, CanvasArtwork>? {
        val uri = uriOverride ?: track.string("uri") ?: track.string("id")?.let { "spotify:track:$it" } ?: return null
        if (!trackUriPattern.matches(uri)) return null
        val title = track.string("name") ?: return null
        val artists = track.obj("artists").array("items")
        val credits = artists.mapNotNull {
            val value = it as? JsonObject
            value.obj("profile").string("name")
        }
        val album = track.obj("albumOfTrack")
        val images = album.obj("coverArt").array("sources")
        val image = images.mapNotNull { it as? JsonObject }.minByOrNull {
            kotlin.math.abs(((it["width"] as? JsonPrimitive)?.longOrNull ?: 640) - 640)
        }.string("url")
        return uri to CanvasArtwork(
            source = CanvasSource.SPOTIFY,
            name = title,
            artist = credits.joinToString(", "),
            albumName = album.string("name"),
            static = image,
        )
    }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
    private fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.array(key: String): JsonArray = this?.get(key) as? JsonArray ?: JsonArray(emptyList())

    suspend fun getCanvases(trackUris: List<String>, accessToken: String, clientId: String): Map<String, String> {
        require(trackUris.size <= 10)
        require(trackUris.all { trackUriPattern.matches(it) })
        CanvasRequestPolicy.check(CanvasSource.SPOTIFY)
        val clientToken = clientToken(clientId)
        val response = client.post(CANVAS_URL) {
            header("Authorization", "Bearer $accessToken")
            header("Client-Token", clientToken)
            header("User-Agent", APP_USER_AGENT)
            header("Accept", "application/protobuf")
            header("Content-Type", "application/protobuf")
            header("Accept-Language", "en")
            setBody(encodeRequest(trackUris))
        }
        CanvasRequestPolicy.check(CanvasSource.SPOTIFY)
        if (response.status != HttpStatusCode.OK) throw RequestException(response.status.value)
        val bytes = response.body<ByteArray>()
        if (bytes.size > 1_048_576) throw IOException("Spotify Canvas response is too large")
        return decodeResponse(bytes).filterKeys { it in trackUris }
    }

    suspend fun isHealthy(accessToken: String, clientId: String): Boolean {
        val trackUri = "spotify:track:0VjIjW4GlUZAMYd2vXMi3b"
        getCanvases(listOf(trackUri), accessToken, clientId)
        return true
    }

    private suspend fun clientToken(clientId: String): String = clientTokenMutex.withLock {
        require(clientId.isNotBlank())
        CanvasRequestPolicy.check(CanvasSource.SPOTIFY)
        val now = System.nanoTime()
        cachedClientToken?.takeIf { it.clientId == clientId && now < it.expiresAtNanos }?.let {
            return@withLock it.value
        }
        val page = client.get("https://open.spotify.com/") { header("User-Agent", WEB_USER_AGENT) }
        if (page.status != HttpStatusCode.OK) throw RequestException(page.status.value)
        val encoded = configPattern.find(page.bodyAsText())?.groupValues?.get(1)
            ?: throw IOException("Spotify client configuration is missing")
        val config = json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)) as? JsonObject
            ?: throw IOException("Spotify client configuration is invalid")
        val version = (config["clientVersion"] as? JsonPrimitive)?.contentOrNull
            ?: throw IOException("Spotify client version is missing")
        val deviceId = page.headers.getAll("Set-Cookie").orEmpty().firstNotNullOfOrNull {
            it.substringBefore(';').takeIf { cookie -> cookie.startsWith("sp_t=") }?.substringAfter('=')
        } ?: UUID.randomUUID().toString()
        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", version)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "android")
                    put("os_version", "14")
                    put("device_id", deviceId)
                    put("device_type", "smartphone")
                }
            }
        }
        CanvasRequestPolicy.check(CanvasSource.SPOTIFY)
        val response = client.post("https://clienttoken.spotify.com/v1/clienttoken") {
            header("User-Agent", WEB_USER_AGENT)
            header("Accept", "application/json")
            header("Content-Type", "application/json")
            setBody(payload.toString().toByteArray(Charsets.UTF_8))
        }
        if (response.status != HttpStatusCode.OK) throw RequestException(response.status.value)
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        val granted = root?.get("granted_token") as? JsonObject
            ?: throw IOException("Spotify client token was not granted")
        val value = (granted["token"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IOException("Spotify client token was not granted")
        val ttlSeconds = (granted["expires_after_seconds"] as? JsonPrimitive)?.longOrNull
            ?.coerceIn(0, 86_400) ?: 0
        cachedClientToken = ClientToken(clientId, value, now + (ttlSeconds - 30).coerceAtLeast(0) * 1_000_000_000)
        value
    }

    private fun encodeRequest(trackUris: List<String>): ByteArray {
        val buffer = ByteArrayOutputStream()
        val output = CodedOutputStream.newInstance(buffer)
        for (uri in trackUris) {
            val trackBuffer = ByteArrayOutputStream()
            val track = CodedOutputStream.newInstance(trackBuffer)
            track.writeString(1, uri)
            track.flush()
            output.writeByteArray(1, trackBuffer.toByteArray())
        }
        output.flush()
        return buffer.toByteArray()
    }

    private fun decodeResponse(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 10) {
                val canvas = CodedInputStream.newInstance(input.readByteArray())
                var uri: String? = null
                var url: String? = null
                while (!canvas.isAtEnd) {
                    val field = canvas.readTag()
                    when (field) {
                        18 -> url = canvas.readStringRequireUtf8()
                        42 -> uri = canvas.readStringRequireUtf8()
                        else -> if (!canvas.skipField(field)) throw IOException("Invalid Spotify Canvas field")
                    }
                }
                if (uri != null && url != null && isCanvasUrl(url)) result[uri] = url
            } else if (!input.skipField(tag)) {
                throw IOException("Invalid Spotify Canvas response")
            }
        }
        return result
    }

    private fun isCanvasUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "canvaz.scdn.co" && uri.path.endsWith(".mp4")
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.net.URISyntaxException) {
        false
    }

    private data class ClientToken(val clientId: String, val value: String, val expiresAtNanos: Long)

    class RequestException(val statusCode: Int) : IOException("Spotify Canvas request failed: HTTP $statusCode")
}
