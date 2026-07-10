/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline BPM + beat-grid analyzer used by the Automix crossfade. Classical DSP, no ML:
 * decode -> mono PCM -> STFT -> spectral flux onset envelope ->
 * autocorrelation tempo estimate -> comb-filter phase for the beat offset.
 *
 * Beat times of the track are: firstBeatOffsetMs + k * (60000 / bpm).
 */
object BeatAnalyzer {
    /** Bump when analysis output gains fields; stale rows re-analyze lazily in the background. */
    const val ANALYSIS_VERSION = 2

    data class Result(
        val bpm: Float,
        val firstBeatOffsetMs: Long,
        val confidence: Float,
        /** First sustained-energy downbeat past the intro; null when the track starts hot. */
        val mixInPointMs: Long? = null,
        /** Where the body of the song ends (outro starts); null when it stays loud to the end. */
        val mixOutPointMs: Long? = null,
        /** Musical key: 0-11 major C..B, 12-23 minor C..B; null when tonality is too weak. */
        val keyIndex: Int? = null,
        val keyConfidence: Float = 0f,
        /** Fraction of spectral energy below 250Hz; null when the profile pass failed. */
        val bassFraction: Float? = null,
        /** Dominant frequency of the 500-4000Hz presence band; null when unavailable. */
        val midPeakHz: Float? = null,
    )

    /**
     * @param result null when the (possibly partial) data couldn't be analyzed.
     * @param complete true when the fetched bytes are known to be the whole stream —
     *   only then is a failed analysis worth negative-caching.
     */
    class CachedAnalysis(
        val result: Result?,
        val complete: Boolean,
    )

    private const val TAG = "BeatAnalyzer"

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 180f

    /** Analysis window: 18s taken from the middle of the track. */
    private const val WINDOW_US = 18_000_000L

    /** Energy-scan windows for dynamic mix points. */
    private const val HEAD_WINDOW_US = 16_000_000L
    private const val TAIL_WINDOW_US = 24_000_000L

    /** Canonical BPM range; octave-fold estimates into it (61.9 -> 123.8, 160 -> 80...). */
    private const val MIN_CANONICAL_BPM = 70f
    private const val MAX_CANONICAL_BPM = 140f
    private const val ENERGY_BLOCK_MS = 500
    private const val MAX_INTRO_SKIP_MS = 20_000L
    private const val MAX_OUTRO_CUT_MS = 45_000L

    /** Hard cap on bytes fetched for analysis. Keeps Automix responsive on slow streams. */
    private const val MAX_FETCH_BYTES = 5L * 1024 * 1024
    private const val MIN_ANALYZABLE_BYTES = 256L * 1024

    fun analyzeUri(
        context: Context,
        uri: Uri,
        shouldCancel: () -> Boolean = { false },
    ): Result? = analyze(shouldCancel) { extractor -> extractor.setDataSource(context, uri, null) }

    /**
     * Fetches the track through the app's playback data-source chain (resolver + caches +
     * network) into a temp file and analyzes that. Cached data is served locally; otherwise
     * this costs one audio download — once per track, results are stored in the DB.
     */
    fun analyzeStream(
        dataSourceFactory: DataSource.Factory,
        mediaId: String,
        tempDir: File,
        shouldCancel: () -> Boolean = { false },
    ): CachedAnalysis? {
        // Unique per call, not just per mediaId: a priority upgrade can cancel an in-flight
        // analysis and start a new one for the same track before the old one's cleanup runs.
        // A shared filename means the old job's `finally { tempFile.delete() }` can race-delete
        // the file the new job is reading, corrupting the result into a false negative cache.
        val tempFile = File(tempDir, "beat_${mediaId.hashCode()}_${System.nanoTime()}.tmp")
        try {
            var copied = 0L
            var reachedEnd = false
            val source = dataSourceFactory.createDataSource()
            try {
                source.open(
                    DataSpec
                        .Builder()
                        .setUri(mediaId.toUri())
                        .setKey(mediaId)
                        .build(),
                )
                FileOutputStream(tempFile).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (copied < MAX_FETCH_BYTES) {
                        if (shouldCancel()) {
                            Timber.tag(TAG).d("Stream fetch for %s cancelled at %d bytes", mediaId, copied)
                            return null
                        }
                        val read = source.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) {
                            reachedEnd = true
                            break
                        }
                        out.write(buffer, 0, read)
                        copied += read
                    }
                }
            } catch (error: Exception) {
                Timber.tag(TAG).d("Stream fetch for %s stopped at %d bytes (%s)", mediaId, copied, error.message)
            } finally {
                runCatching { source.close() }
            }

            Timber.tag(TAG).d("Stream fetch for %s: %d bytes, complete=%s", mediaId, copied, reachedEnd)
            if (copied < MIN_ANALYZABLE_BYTES) return null

            if (shouldCancel()) return null
            val result = analyze(shouldCancel) { extractor -> extractor.setDataSource(tempFile.absolutePath) }
            return CachedAnalysis(result, reachedEnd)
        } finally {
            tempFile.delete()
        }
    }

    private fun analyze(
        shouldCancel: () -> Boolean,
        setSource: (MediaExtractor) -> Unit,
    ): Result? {
        val extractor = MediaExtractor()
        try {
            if (shouldCancel()) return null
            setSource(extractor)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)
            val durationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    0L
                }

            val windowStartUs = max(0L, durationUs / 2 - WINDOW_US / 2)
            extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = max(0L, extractor.sampleTime)

            if (shouldCancel()) return null
            val pcm = decodeMono(extractor, format, WINDOW_US, shouldCancel) ?: return null
            if (pcm.samples.size < FFT_SIZE * 8) return null

            if (shouldCancel()) return null
            val grid = analyzeBeatGrid(pcm.samples, pcm.sampleRate, actualStartUs / 1000) ?: return null

            // Key + spectral profile from the same mid-window PCM (no extra decode).
            val profile = analyzeSpectralProfile(pcm.samples, pcm.sampleRate)

            // Head pass: find where the low-energy intro ends so the incoming track can
            // start on its first sustained-energy downbeat instead.
            var mixInPointMs: Long? = null
            if (durationUs > HEAD_WINDOW_US) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                decodeMono(extractor, format, HEAD_WINDOW_US, shouldCancel)?.let { head ->
                    mixInPointMs =
                        detectMixIn(
                            energyEnvelope(head.samples, head.sampleRate),
                            grid.firstBeatOffsetMs,
                            grid.periodMs,
                        )
                }
            }

            // Tail pass: detect where the body of the song ends (outro starts) so the
            // transition can begin there instead of a fixed distance from the end.
            var mixOutPointMs: Long? = null
            val durationMs = durationUs / 1000
            if (durationUs > TAIL_WINDOW_US) {
                val tailStartUs = durationUs - TAIL_WINDOW_US
                extractor.seekTo(tailStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val tailActualStartMs = max(0L, extractor.sampleTime) / 1000
                decodeMono(extractor, format, TAIL_WINDOW_US, shouldCancel)?.let { tail ->
                    mixOutPointMs =
                        detectMixOut(
                            energyEnvelope(tail.samples, tail.sampleRate),
                            tailActualStartMs,
                            durationMs,
                        )
                }
            }

            return Result(
                bpm = grid.bpm,
                firstBeatOffsetMs = grid.firstBeatOffsetMs,
                confidence = grid.confidence,
                mixInPointMs = mixInPointMs,
                mixOutPointMs = mixOutPointMs,
                keyIndex = profile?.keyIndex,
                keyConfidence = profile?.keyConfidence ?: 0f,
                bassFraction = profile?.bassFraction,
                midPeakHz = profile?.midPeakHz,
            )
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Beat analysis failed")
            return null
        } finally {
            extractor.release()
        }
    }

    internal class BeatGrid(
        val bpm: Float,
        val firstBeatOffsetMs: Long,
        val periodMs: Float,
        val confidence: Float,
    )

    /**
     * Pure-DSP part of the pipeline, separated from MediaCodec so it is unit-testable:
     * onset envelope -> tempo period -> beat phase -> grid extrapolated to track start.
     */
    internal fun analyzeBeatGrid(
        samples: FloatArray,
        sampleRate: Int,
        windowStartMs: Long,
    ): BeatGrid? {
        val flux = spectralFlux(samples)
        val frameRate = sampleRate.toFloat() / HOP_SIZE

        val (periodFrames, confidence) = estimateTempoPeriod(flux, frameRate) ?: return null
        val phaseFrames = estimateBeatPhase(flux, periodFrames)

        var periodMs = periodFrames / frameRate * 1000f
        var bpm = 60_000f / periodMs
        // Octave-fold into the canonical range: the beat grid stays valid because
        // doubling/halving the period keeps the same phase anchor.
        while (bpm < MIN_CANONICAL_BPM) {
            bpm *= 2f
            periodMs /= 2f
        }
        while (bpm >= MAX_CANONICAL_BPM) {
            bpm /= 2f
            periodMs *= 2f
        }
        val anchorMs = windowStartMs + (phaseFrames / frameRate * 1000f).roundToLong()
        // Extrapolate the periodic grid back to the start of the track.
        val periodMsLong = periodMs.roundToLong().coerceAtLeast(1L)
        val firstBeatOffsetMs = (anchorMs % periodMsLong + periodMsLong) % periodMsLong
        return BeatGrid(bpm, firstBeatOffsetMs, periodMs, confidence)
    }

    private class MonoPcm(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    private fun decodeMono(
        extractor: MediaExtractor,
        format: MediaFormat,
        maxDurationUs: Long,
        shouldCancel: () -> Boolean,
    ): MonoPcm? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        val chunks = ArrayList<FloatArray>()
        var sampleRate = 0
        var decodedUs = 0L
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            while (!outputDone && decodedUs < maxDurationUs) {
                if (shouldCancel()) return null
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (info.size > 0) {
                        val outFormat = codec.outputFormat
                        val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val shorts = buffer.asShortBuffer()
                        val frames = shorts.remaining() / channels
                        val mono = FloatArray(frames)
                        for (frame in 0 until frames) {
                            var acc = 0f
                            for (channel in 0 until channels) acc += shorts.get(frame * channels + channel) / 32768f
                            mono[frame] = acc / channels
                        }
                        chunks.add(mono)
                        decodedUs += frames * 1_000_000L / max(1, sampleRate)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Decode failed")
            return null
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        if (sampleRate == 0 || chunks.isEmpty()) return null

        val total = chunks.sumOf { it.size }
        val samples = FloatArray(total)
        var position = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, position)
            position += chunk.size
        }
        return MonoPcm(samples, sampleRate)
    }

    /** RMS energy per ENERGY_BLOCK_MS block. */
    internal fun energyEnvelope(
        samples: FloatArray,
        sampleRate: Int,
    ): FloatArray {
        val blockSize = (sampleRate * ENERGY_BLOCK_MS / 1000).coerceAtLeast(1)
        val numBlocks = samples.size / blockSize
        val envelope = FloatArray(numBlocks)
        for (block in 0 until numBlocks) {
            var sum = 0f
            val offset = block * blockSize
            for (i in 0 until blockSize) {
                val sample = samples[offset + i]
                sum += sample * sample
            }
            envelope[block] = sqrt(sum / blockSize)
        }
        return envelope
    }

    private fun percentile(
        values: FloatArray,
        p: Float,
    ): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)]
    }

    /**
     * Strongest sustained energy rise past the intro (the "drop"), snapped onto the beat grid.
     * Null when the track starts hot.
     */
    internal fun detectMixIn(
        envelope: FloatArray,
        firstBeatOffsetMs: Long,
        periodMs: Float,
    ): Long? {
        if (envelope.size < 8) return null
        val reference = percentile(envelope, 0.75f)
        if (reference <= 0f) return null

        var bestBlock = -1
        var bestScore = 0f
        var prefixSum = 0f
        for (i in 0 until envelope.size - 4) {
            val sustained =
                envelope[i] >= 0.55f * reference &&
                    envelope[i + 1] >= 0.4f * reference &&
                    envelope[i + 2] >= 0.4f * reference &&
                    envelope[i + 3] >= 0.4f * reference
            if (sustained && i > 0 && i.toLong() * ENERGY_BLOCK_MS <= MAX_INTRO_SKIP_MS) {
                val after = (envelope[i] + envelope[i + 1] + envelope[i + 2] + envelope[i + 3]) / 4f
                val before = prefixSum / i
                val score = after - before
                if (score > bestScore) {
                    bestScore = score
                    bestBlock = i
                }
            }
            prefixSum += envelope[i]
        }
        if (bestBlock <= 0) return null // Starts hot; keep the default first-downbeat start.

        val candidateMs = bestBlock.toLong() * ENERGY_BLOCK_MS
        // Snap forward to the next downbeat.
        val k = ceil((candidateMs - firstBeatOffsetMs) / periodMs.toDouble()).toLong()
        return (firstBeatOffsetMs + max(0L, k) * periodMs.toDouble()).roundToLong()
    }

    /**
     * Body/outro boundary: last loud->quiet energy cliff, else last block at body loudness.
     * Null when the track stays loud to the end.
     */
    internal fun detectMixOut(
        envelope: FloatArray,
        windowStartMs: Long,
        durationMs: Long,
    ): Long? {
        if (envelope.size < 8 || durationMs <= 0) return null
        val reference = percentile(envelope, 0.75f)
        if (reference <= 0f) return null

        var boundaryBlock = -1
        for (i in envelope.size - 1 downTo 1) {
            if (envelope[i - 1] >= 0.7f * reference && envelope[i] < 0.45f * reference) {
                boundaryBlock = i - 1
                break
            }
        }
        if (boundaryBlock < 0) {
            for (i in envelope.indices.reversed()) {
                if (envelope[i] >= 0.5f * reference) {
                    boundaryBlock = i
                    break
                }
            }
        }
        if (boundaryBlock < 0) return null

        val mixOutMs = windowStartMs + (boundaryBlock + 1).toLong() * ENERGY_BLOCK_MS
        // Loud almost to the end: nothing to cut.
        if (durationMs - mixOutMs < 3_000) return null
        // Never cut more than MAX_OUTRO_CUT_MS.
        return max(mixOutMs, durationMs - MAX_OUTRO_CUT_MS)
    }

    internal class SpectralProfile(
        val keyIndex: Int?,
        val keyConfidence: Float,
        val bassFraction: Float,
        val midPeakHz: Float,
    )

    private const val PROFILE_FFT_SIZE = 4096
    private const val PROFILE_HOP_SIZE = 2048
    private const val BASS_MAX_HZ = 250f
    private const val MID_MIN_HZ = 500f
    private const val MID_MAX_HZ = 4_000f
    private const val CHROMA_MIN_HZ = 55f
    private const val CHROMA_MAX_HZ = 5_000f

    /** Krumhansl-Kessler probe-tone key profiles, index 0 = tonic. */
    private val MAJOR_KEY_PROFILE =
        floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_KEY_PROFILE =
        floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    /**
     * Power spectrum -> chroma -> key (best of 24 Krumhansl profiles), plus bass fraction and
     * presence-band peak for the adaptive duck EQ.
     */
    internal fun analyzeSpectralProfile(
        samples: FloatArray,
        sampleRate: Int,
    ): SpectralProfile? {
        val numFrames = (samples.size - PROFILE_FFT_SIZE) / PROFILE_HOP_SIZE
        if (numFrames < 4 || sampleRate <= 0) return null
        val bins = PROFILE_FFT_SIZE / 2
        val window = FloatArray(PROFILE_FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / PROFILE_FFT_SIZE).toFloat() }
        val avgPower = FloatArray(bins)
        val re = FloatArray(PROFILE_FFT_SIZE)
        val im = FloatArray(PROFILE_FFT_SIZE)
        for (frame in 0 until numFrames) {
            val offset = frame * PROFILE_HOP_SIZE
            for (i in 0 until PROFILE_FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            for (bin in 0 until bins) avgPower[bin] += re[bin] * re[bin] + im[bin] * im[bin]
        }

        val binHz = sampleRate.toFloat() / PROFILE_FFT_SIZE
        val chroma = FloatArray(12)
        var bass = 0f
        var total = 0f
        var midPeakBin = -1
        var midPeakPower = 0f
        for (bin in 1 until bins - 1) {
            val frequency = bin * binHz
            if (frequency < 20f) continue
            val power = avgPower[bin]
            total += power
            if (frequency <= BASS_MAX_HZ) bass += power
            if (frequency in CHROMA_MIN_HZ..CHROMA_MAX_HZ) {
                val semitonesFromA4 = (12.0 * ln(frequency / 440.0) / ln(2.0)).roundToInt()
                chroma[(9 + semitonesFromA4).mod(12)] += power
            }
            if (frequency in MID_MIN_HZ..MID_MAX_HZ) {
                val smoothed = avgPower[bin - 1] + power + avgPower[bin + 1]
                if (smoothed > midPeakPower) {
                    midPeakPower = smoothed
                    midPeakBin = bin
                }
            }
        }
        if (total <= 0f) return null

        var bestKey = -1
        var bestScore = Float.NEGATIVE_INFINITY
        var secondScore = Float.NEGATIVE_INFINITY
        var worstScore = Float.POSITIVE_INFINITY
        for (mode in 0..1) {
            val profile = if (mode == 0) MAJOR_KEY_PROFILE else MINOR_KEY_PROFILE
            for (tonic in 0..11) {
                val score = chromaKeyCorrelation(chroma, profile, tonic)
                if (score > bestScore) {
                    secondScore = bestScore
                    bestScore = score
                    bestKey = tonic + mode * 12
                } else if (score > secondScore) {
                    secondScore = score
                }
                if (score < worstScore) worstScore = score
            }
        }
        val keyConfidence =
            if (bestScore > worstScore) ((bestScore - secondScore) / (bestScore - worstScore)).coerceIn(0f, 1f) else 0f
        return SpectralProfile(
            keyIndex = if (bestKey >= 0 && bestScore > 0f) bestKey else null,
            keyConfidence = keyConfidence,
            bassFraction = bass / total,
            midPeakHz = if (midPeakBin > 0) midPeakBin * binHz else MID_MIN_HZ * 2.4f,
        )
    }

    /** Pearson correlation of the chroma vector against a key profile rotated to [tonic]. */
    private fun chromaKeyCorrelation(
        chroma: FloatArray,
        profile: FloatArray,
        tonic: Int,
    ): Float {
        var chromaMean = 0f
        var profileMean = 0f
        for (i in 0 until 12) {
            chromaMean += chroma[i]
            profileMean += profile[i]
        }
        chromaMean /= 12f
        profileMean /= 12f
        var numerator = 0f
        var chromaVariance = 0f
        var profileVariance = 0f
        for (pitchClass in 0 until 12) {
            val c = chroma[pitchClass] - chromaMean
            val p = profile[(pitchClass - tonic).mod(12)] - profileMean
            numerator += c * p
            chromaVariance += c * c
            profileVariance += p * p
        }
        val denominator = sqrt(chromaVariance * profileVariance)
        return if (denominator > 0f) numerator / denominator else 0f
    }

    /** Half-wave-rectified spectral flux per hop, log-compressed magnitudes. */
    internal fun spectralFlux(samples: FloatArray): FloatArray {
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        val bins = FFT_SIZE / 2
        val flux = FloatArray(numFrames)
        val prevMagnitude = FloatArray(bins)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var sum = 0f
            for (bin in 0 until bins) {
                val magnitude = ln(1f + 10f * sqrt(re[bin] * re[bin] + im[bin] * im[bin]))
                val diff = magnitude - prevMagnitude[bin]
                if (diff > 0) sum += diff
                prevMagnitude[bin] = magnitude
            }
            flux[frame] = sum
        }

        // Subtract the local mean so autocorrelation sees onsets, not slow dynamics.
        val meanWindow = (0.5f * FFT_SIZE / HOP_SIZE * 8).roundToInt().coerceAtLeast(8)
        val detrended = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val lo = max(0, i - meanWindow)
            val hi = min(numFrames - 1, i + meanWindow)
            var mean = 0f
            for (j in lo..hi) mean += flux[j]
            mean /= hi - lo + 1
            detrended[i] = max(0f, flux[i] - mean)
        }
        return detrended
    }

    /**
     * Autocorrelation over the beat-period lag range. Returns (periodInFrames, confidence),
     * favoring candidates whose double period is also supported (down-weights half-period picks).
     */
    internal fun estimateTempoPeriod(
        flux: FloatArray,
        frameRate: Float,
    ): Pair<Float, Float>? {
        val minLag = (frameRate * 60f / MAX_BPM).roundToInt()
        val maxLag = (frameRate * 60f / MIN_BPM).roundToInt()
        if (flux.size < maxLag * 2) return null

        val autocorrelation = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until flux.size - lag) sum += flux[i] * flux[i + lag]
            autocorrelation[lag] = sum / (flux.size - lag)
        }

        var mean = 0f
        for (lag in minLag..maxLag) mean += autocorrelation[lag]
        mean /= maxLag - minLag + 1
        if (mean <= 0f) return null

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            if (lag > minLag &&
                lag < maxLag &&
                (autocorrelation[lag] < autocorrelation[lag - 1] || autocorrelation[lag] < autocorrelation[lag + 1])
            ) {
                continue
            }
            var score = autocorrelation[lag]
            // Reward candidates whose 2x lag also correlates (true beat vs half-beat).
            val doubleLag = lag * 2
            if (doubleLag <= maxLag) score += 0.5f * autocorrelation[doubleLag]
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        // Parabolic interpolation around the peak for sub-frame period accuracy.
        val refined =
            if (bestLag in minLag + 1 until maxLag) {
                val y0 = autocorrelation[bestLag - 1]
                val y1 = autocorrelation[bestLag]
                val y2 = autocorrelation[bestLag + 1]
                val denominator = y0 - 2 * y1 + y2
                if (denominator != 0f) bestLag + 0.5f * (y0 - y2) / denominator else bestLag.toFloat()
            } else {
                bestLag.toFloat()
            }

        // Peak prominence over the median autocorrelation: flat (beatless) material
        // scores near 0, a strong periodic pulse scores near 1.
        val sortedAutocorrelation = autocorrelation.copyOfRange(minLag, maxLag + 1).sorted()
        val median = sortedAutocorrelation[sortedAutocorrelation.size / 2]
        val confidence =
            if (autocorrelation[bestLag] > 0f) {
                ((autocorrelation[bestLag] - median) / autocorrelation[bestLag]).coerceIn(0f, 1f)
            } else {
                0f
            }
        return refined to confidence
    }

    /** Comb filter: phase (in frames) maximizing summed flux at phase + k*period. */
    internal fun estimateBeatPhase(
        flux: FloatArray,
        periodFrames: Float,
    ): Float {
        val period = periodFrames.roundToInt().coerceAtLeast(1)
        var bestPhase = 0
        var bestSum = -1f
        for (phase in 0 until period) {
            var sum = 0f
            var i = phase
            while (i < flux.size) {
                sum += flux[i]
                i += period
            }
            if (sum > bestSum) {
                bestSum = sum
                bestPhase = phase
            }
        }
        return bestPhase.toFloat()
    }

    /** In-place iterative radix-2 FFT. Arrays must be a power-of-two length. */
    private fun fft(
        re: FloatArray,
        im: FloatArray,
    ) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
