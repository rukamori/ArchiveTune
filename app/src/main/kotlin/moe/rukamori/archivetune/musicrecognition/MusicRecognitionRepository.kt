/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.shazamkit.Shazam
import moe.rukamori.archivetune.shazamkit.ShazamSignatureGenerator
import moe.rukamori.archivetune.shazamkit.models.RecognitionResult
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class MusicRecognitionRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun observeHistory(): Flow<List<RecognitionHistoryEntry>> =
            context.dataStore.data
                .map { preferences ->
                    decodeHistory(preferences[MusicRecognitionHistoryJsonKey])
                        .map(RecognitionHistoryRecord::toDomain)
                }.catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.e(throwable, "Failed to observe music recognition history")
                    emit(emptyList())
                }.flowOn(Dispatchers.IO)

        fun observeBackgroundRecognitionEnabled(): Flow<Boolean> =
            context.dataStore.data
                .map { preferences ->
                    preferences[BackgroundRecognitionEnabledKey] ?: BackgroundRecognitionEnabledDefault
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)

        suspend fun isBackgroundRecognitionEnabled(): Boolean =
            withContext(Dispatchers.IO) {
                context.dataStore.data.first()[BackgroundRecognitionEnabledKey]
                    ?: BackgroundRecognitionEnabledDefault
            }

        suspend fun setBackgroundRecognitionEnabled(enabled: Boolean) {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { preferences ->
                    preferences[BackgroundRecognitionEnabledKey] = enabled
                }
            }
        }

        suspend fun captureAudio(): ShortArray =
            withContext(Dispatchers.IO) {
                val channel = AudioFormat.CHANNEL_IN_MONO
                val encoding = AudioFormat.ENCODING_PCM_16BIT
                val minimumBufferSize =
                    AudioRecord
                        .getMinBufferSize(SampleRateHz, channel, encoding)
                        .coerceAtLeast(MinimumBufferSizeBytes)
                val audioRecord =
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SampleRateHz,
                        channel,
                        encoding,
                        minimumBufferSize,
                    )

                captureSamples(audioRecord, minimumBufferSize)
            }

        suspend fun captureDevicePlayback(mediaProjection: MediaProjection): ShortArray =
            withContext(Dispatchers.IO) {
                val audioFormat =
                    AudioFormat
                        .Builder()
                        .setSampleRate(SampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                val minimumBufferSize =
                    AudioRecord
                        .getMinBufferSize(
                            SampleRateHz,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ).coerceAtLeast(MinimumBufferSizeBytes)
                val captureConfiguration =
                    AudioPlaybackCaptureConfiguration
                        .Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()
                val audioRecord =
                    AudioRecord
                        .Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(minimumBufferSize)
                        .setAudioPlaybackCaptureConfig(captureConfiguration)
                        .build()

                captureSamples(audioRecord, minimumBufferSize)
            }

        suspend fun recognize(samples: ShortArray): Result<RecognizedTrack> {
            val signature =
                withContext(Dispatchers.Default) {
                    ShazamSignatureGenerator()
                        .apply { feedPcm16Mono(samples) }
                        .nextSignatureOrNull()
                } ?: return Result.failure(
                    MusicRecognitionException(MusicRecognitionFailure.SignatureFailed),
                )

            return withContext(Dispatchers.IO) {
                Shazam
                    .recognize(signature.uri, signature.sampleDurationMs)
                    .fold(
                        onSuccess = { Result.success(it.toDomain()) },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) throw throwable
                            val failure =
                                if (
                                    throwable.message?.contains("no match", ignoreCase = true) == true ||
                                    throwable.message?.contains("404") == true
                                ) {
                                    MusicRecognitionFailure.NoMatch
                                } else {
                                    MusicRecognitionFailure.RecognitionFailed
                                }
                            Result.failure(MusicRecognitionException(failure, throwable))
                        },
                    )
            }
        }

        suspend fun saveToHistory(track: RecognizedTrack) {
            val entry = track.toHistoryRecord(System.currentTimeMillis())
            withContext(Dispatchers.IO) {
                context.dataStore.edit { preferences ->
                    val current = decodeHistory(preferences[MusicRecognitionHistoryJsonKey])
                    val next =
                        buildList {
                            add(entry)
                            addAll(current.filterNot { it.stableKey == entry.stableKey })
                        }.take(MusicRecognitionHistoryLimit)
                    preferences[MusicRecognitionHistoryJsonKey] = HistoryJson.encodeToString(next)
                }
            }
        }
    }

private suspend fun captureSamples(
    audioRecord: AudioRecord,
    bufferSizeBytes: Int,
): ShortArray {
    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
        audioRecord.release()
        error("AudioRecord failed to initialize")
    }

    val output = ShortArray((RecordingDurationMillis * SampleRateHz / 1_000L).toInt())
    val buffer = ShortArray(bufferSizeBytes / Short.SIZE_BYTES)

    try {
        audioRecord.startRecording()
        var written = 0
        while (written < output.size) {
            coroutineContext.ensureActive()
            val read = audioRecord.read(buffer, 0, minOf(buffer.size, output.size - written))
            if (read < 0) error("AudioRecord read failed with code $read")
            if (read == 0) continue
            buffer.copyInto(output, destinationOffset = written, endIndex = read)
            written += read
        }
        return output.copyOf(written)
    } finally {
        runCatching { audioRecord.stop() }
        audioRecord.release()
    }
}

private const val SampleRateHz = 16_000
private const val RecordingDurationMillis = 4_200L
private const val MinimumBufferSizeBytes = 4_096
private const val MusicRecognitionHistoryLimit = 50
private const val BackgroundRecognitionEnabledDefault = true

private val MusicRecognitionHistoryJsonKey = stringPreferencesKey("musicRecognitionHistoryJson")
private val BackgroundRecognitionEnabledKey =
    booleanPreferencesKey("musicRecognitionBackgroundEnabled")

private val HistoryJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

private fun decodeHistory(raw: String?): List<RecognitionHistoryRecord> =
    raw
        ?.takeIf(String::isNotBlank)
        ?.let {
            runCatching {
                HistoryJson.decodeFromString<List<RecognitionHistoryRecord>>(it)
            }.onFailure { throwable ->
                Timber.w(throwable, "Failed to decode music recognition history")
            }.getOrDefault(emptyList())
        }
        ?: emptyList()

@Serializable
private data class RecognitionHistoryRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverArtUrl: String?,
    val coverArtHqUrl: String?,
    val genre: String?,
    val releaseDate: String?,
    val shazamUrl: String?,
    val isrc: String?,
    val recognizedAtEpochMillis: Long,
) {
    val stableKey: String
        get() =
            trackId.takeIf { it.isNotBlank() }
                ?: listOf(title, artist, isrc.orEmpty())
                    .joinToString("|") { it.trim().lowercase() }

    fun toDomain(): RecognitionHistoryEntry =
        RecognitionHistoryEntry(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            coverArtUrl = coverArtUrl,
            coverArtHqUrl = coverArtHqUrl,
            genre = genre,
            releaseDate = releaseDate,
            shazamUrl = shazamUrl,
            isrc = isrc,
            recognizedAtEpochMillis = recognizedAtEpochMillis,
        )
}

private fun RecognizedTrack.toHistoryRecord(recognizedAtEpochMillis: Long): RecognitionHistoryRecord =
    RecognitionHistoryRecord(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        coverArtUrl = coverArtUrl,
        coverArtHqUrl = coverArtHqUrl,
        genre = genre,
        releaseDate = releaseDate,
        shazamUrl = shazamUrl,
        isrc = isrc,
        recognizedAtEpochMillis = recognizedAtEpochMillis,
    )

private fun RecognitionResult.toDomain(): RecognizedTrack =
    RecognizedTrack(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        coverArtUrl = coverArtUrl,
        coverArtHqUrl = coverArtHqUrl,
        genre = genre,
        releaseDate = releaseDate,
        label = label,
        lyrics = lyrics.orEmpty().toList(),
        shazamUrl = shazamUrl,
        isrc = isrc,
    )
