/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

internal object AudioWaveformAnalyzer {
    suspend fun generate(
        dataSourceFactory: DataSource.Factory,
        dataSpec: DataSpec,
        fallbackDurationMs: Int,
    ): ByteArray {
        val squaredAmplitudes = DoubleArray(BinCount)
        val frameCounts = IntArray(BinCount)
        val extractor = MediaExtractor()
        val mediaDataSource = DataSourceMediaDataSource(dataSourceFactory, dataSpec)
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(mediaDataSource)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { track ->
                extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("The audio source contains no audio track")
            val trackFormat = extractor.getTrackFormat(audioTrack)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("The audio source has no decoder MIME type")
            val durationUs =
                trackFormat
                    .takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                    ?.getLong(MediaFormat.KEY_DURATION)
                    ?.takeIf { it > 0L }
                    ?: fallbackDurationMs.toLong() * 1_000L
            if (durationUs <= 0L) throw IOException("The audio duration is unavailable")

            extractor.selectTrack(audioTrack)
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CodecBufferTimeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IOException("The audio decoder returned no input buffer")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CodecBufferTimeoutUs)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding =
                            if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                    -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            if (bufferInfo.size > 0) {
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                    ?: throw IOException("The audio decoder returned no output buffer")
                                accumulateSamples(
                                    buffer = outputBuffer,
                                    info = bufferInfo,
                                    sampleRate = sampleRate,
                                    channelCount = channelCount,
                                    pcmEncoding = pcmEncoding,
                                    durationUs = durationUs,
                                    squaredAmplitudes = squaredAmplitudes,
                                    frameCounts = frameCounts,
                                )
                            }
                            outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
        } finally {
            try {
                codec?.stop()
            } finally {
                codec?.release()
                extractor.release()
                mediaDataSource.close()
            }
        }

        val rmsAmplitudes =
            DoubleArray(BinCount) { index ->
                if (frameCounts[index] == 0) {
                    0.0
                } else {
                    kotlin.math.sqrt(squaredAmplitudes[index] / frameCounts[index])
                }
            }
        val maximumAmplitude = rmsAmplitudes.maxOrNull() ?: 0.0
        if (maximumAmplitude <= 0.0) throw IOException("The audio waveform contains no samples")
        return ByteArray(BinCount) { index ->
            val normalized = (rmsAmplitudes[index] / maximumAmplitude).coerceIn(0.0, 1.0)
            (normalized.pow(WaveformSensitivity) * UnsignedByteMaximum)
                .toInt()
                .coerceIn(0, UnsignedByteMaximum)
                .toByte()
        }
    }

    private fun accumulateSamples(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int,
        durationUs: Long,
        squaredAmplitudes: DoubleArray,
        frameCounts: IntArray,
    ) {
        if (sampleRate <= 0 || channelCount <= 0) return
        val sampleBytes =
            when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> Float.SIZE_BYTES
                else -> Short.SIZE_BYTES
            }
        val frameSize = sampleBytes * channelCount
        val frameCount = info.size / frameSize
        val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        samples.position(info.offset)
        samples.limit(info.offset + info.size)

        repeat(frameCount) { frame ->
            var frameSquaredAmplitude = 0.0
            repeat(channelCount) {
                val value =
                    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        abs(samples.float).coerceIn(0f, 1f).toDouble()
                    } else {
                        abs(samples.short.toInt() / Short.MAX_VALUE.toFloat()).coerceAtMost(1f).toDouble()
                    }
                frameSquaredAmplitude += value * value
            }
            val frameTimeUs = info.presentationTimeUs + frame * 1_000_000L / sampleRate
            val bin =
                (frameTimeUs * squaredAmplitudes.size / durationUs)
                    .toInt()
                    .coerceIn(squaredAmplitudes.indices)
            squaredAmplitudes[bin] += frameSquaredAmplitude / channelCount
            frameCounts[bin]++
        }
    }

    private class DataSourceMediaDataSource(
        private val dataSourceFactory: DataSource.Factory,
        private val dataSpec: DataSpec,
    ) : MediaDataSource() {
        private var dataSource: DataSource? = null
        private var nextPosition = 0L

        @Synchronized
        override fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            size: Int,
        ): Int {
            if (position < 0L) return -1
            if (size == 0) return 0
            if (dataSource == null || position != nextPosition) openAt(position)
            val source = checkNotNull(dataSource)
            val bytesRead = source.read(buffer, offset, size)
            if (bytesRead > 0) {
                nextPosition += bytesRead
            } else if (bytesRead == C.RESULT_END_OF_INPUT) {
                closeSource()
            }
            return bytesRead
        }

        override fun getSize(): Long = C.LENGTH_UNSET.toLong()

        @Synchronized
        override fun close() {
            closeSource()
        }

        private fun openAt(position: Long) {
            closeSource()
            val source = dataSourceFactory.createDataSource()
            try {
                source.open(
                    dataSpec
                        .buildUpon()
                        .setPosition(position)
                        .setLength(C.LENGTH_UNSET.toLong())
                        .build(),
                )
            } catch (exception: IOException) {
                source.close()
                throw exception
            }
            dataSource = source
            nextPosition = position
        }

        private fun closeSource() {
            val source = dataSource
            dataSource = null
            nextPosition = 0L
            source?.close()
        }
    }

    internal const val BinCount = 48
    private const val CodecBufferTimeoutUs = 10_000L
    private const val UnsignedByteMaximum = 255
    private const val WaveformSensitivity = 0.65
}
