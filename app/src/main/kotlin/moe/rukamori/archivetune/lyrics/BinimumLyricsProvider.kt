package moe.rukamori.archivetune.lyrics

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.constants.EnableBinimumLyricsKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.io.IOException
import kotlin.math.abs

object BinimumLyricsProvider : LyricsProvider {
    override val name = "Binimum Lyrics"

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 8_000
            }
            expectSuccess = true
        }
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBinimumLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val match = lookup(title, artist, album, duration).firstOrNull()
                ?: throw NoSuchElementException("Lyrics not found on Binimum Lyrics")
            Result.success(fetchTtml(match))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        lookup(title, artist, album, duration).forEach { match ->
            callback(fetchTtml(match))
        }
    }

    private suspend fun lookup(
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): List<TrackMatch> {
        require(title.isNotBlank() && artist.isNotBlank()) { "Song title and artist are required" }

        val response = client.get("https://lyrics-api.binimum.org/") {
            header("Accept", "application/json")
            parameter("track", title.trim())
            parameter("artist", artist.trim())
            album?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("album", it) }
            if (duration > 0) parameter("duration", duration)
        }
        return json.decodeFromString<LookupResponse>(response.bodyAsText()).results
            .sortedWith(
                compareByDescending<TrackMatch> { it.timingType == "word" }
                    .thenBy {
                        if (duration > 0 && it.duration != null) abs(it.duration - duration) else Double.MAX_VALUE
                    },
            )
            .distinctBy { it.lyricsUrl }
    }

    private suspend fun fetchTtml(match: TrackMatch): String {
        val url = Url(match.lyricsUrl)
        require(url.protocol.name == "https" && url.host.isNotBlank()) { "Invalid Binimum lyrics URL" }

        val lyrics = client.get(url) {
            header("Accept", "application/ttml+xml, application/xml, text/xml")
        }.bodyAsText().trim()
        if (!LyricsUtils.isTtml(lyrics)) throw IOException("Invalid TTML response from Binimum Lyrics")
        return lyrics
    }

    @Serializable
    private data class LookupResponse(
        val results: List<TrackMatch>,
    )

    @Serializable
    private data class TrackMatch(
        val lyricsUrl: String,
        @SerialName("timing_type") val timingType: String? = null,
        val duration: Double? = null,
    )
}
