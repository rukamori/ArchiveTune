/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Two-band ducking processor used only during Automix beat-matched blends: a low-shelf cut
 * (bass swap, so two full basslines don't sum into mud) cascaded with a mid peaking cut
 * (vocal presence, so two lead vocals don't stack during the overlap). Not the user's EQ.
 *
 * The filters' coefficients are fixed once at configure time and never change again —
 * changing an IIR filter's coefficients on the fly while its delay-line memory carries state
 * from the old coefficients produces audible clicks ("zipper noise"). Instead, the filters run
 * continuously (their own signal stays perfectly smooth) and the duck amount is a linear blend
 * between the dry and fully-filtered signal, ramped smoothly per-sample toward whatever mix the
 * blend loop last requested.
 */
@UnstableApi
class AutomixDuckAudioProcessor(
    private val shelfFrequencyHz: Double = 150.0,
    private val shelfDuckDb: Double = -10.0,
    private val midFrequencyHz: Double = 1_200.0,
    private val midDuckDb: Double = -7.0,
    private val midQ: Double = 0.9,
) : AudioProcessor {
    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    /** Target blend, 0=dry (no duck) .. 1=fully filtered. Set from the blend loop. */
    @Volatile
    private var targetMix: Float = 0f

    /** Currently applied blend; eases toward targetMix a little every sample, never jumps. */
    private var currentMix: Float = 0f

    /** Biquad coefficients + per-channel state: [0]=low shelf, [1]=mid peaking cut. */
    private val stages = Array(2) { Biquad() }

    private class Biquad {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var x1L = 0.0
        var x2L = 0.0
        var y1L = 0.0
        var y2L = 0.0
        var x1R = 0.0
        var x2R = 0.0
        var y1R = 0.0
        var y2R = 0.0

        fun clearState() {
            x1L = 0.0
            x2L = 0.0
            y1L = 0.0
            y2L = 0.0
            x1R = 0.0
            x2R = 0.0
            y1R = 0.0
            y2R = 0.0
        }

        fun processLeft(input: Double): Double {
            val output = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
            x2L = x1L
            x1L = input
            y2L = y1L
            y1L = output
            return output
        }

        fun processRight(input: Double): Double {
            val output = b0 * input + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
            x2R = x1R
            x1R = input
            y2R = y1R
            y1R = output
            return output
        }
    }

    /** Duck toward [fraction] of the full two-band cut, where 0=no duck and 1=full duck. */
    fun setMix(fraction: Float) {
        targetMix = fraction.coerceIn(0f, 1f)
    }

    fun resetGain() {
        targetMix = 0f
    }

    private fun computeCoefficients() {
        // RBJ low shelf.
        run {
            val stage = stages[0]
            val a = sqrt(10.0.pow(shelfDuckDb / 20.0))
            val omega = 2.0 * PI * shelfFrequencyHz / sampleRate
            val sinOmega = sin(omega)
            val cosOmega = cos(omega)
            val alpha = sinOmega / 2.0 * sqrt(2.0) // S=1 shelf slope
            val sqrtA = sqrt(a)
            val aPlusOne = a + 1.0
            val aMinusOne = a - 1.0
            val twoSqrtAAlpha = 2.0 * sqrtA * alpha

            var b0 = a * (aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha)
            var b1 = 2.0 * a * (aMinusOne - aPlusOne * cosOmega)
            var b2 = a * (aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha)
            val a0 = aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha
            var a1 = -2.0 * (aMinusOne + aPlusOne * cosOmega)
            var a2 = aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha

            b0 /= a0
            b1 /= a0
            b2 /= a0
            a1 /= a0
            a2 /= a0
            stage.b0 = b0
            stage.b1 = b1
            stage.b2 = b2
            stage.a1 = a1
            stage.a2 = a2
        }
        // RBJ peaking cut for the vocal presence band.
        run {
            val stage = stages[1]
            val a = 10.0.pow(midDuckDb / 40.0)
            val omega = 2.0 * PI * midFrequencyHz / sampleRate
            val sinOmega = sin(omega)
            val cosOmega = cos(omega)
            val alpha = sinOmega / (2.0 * midQ)

            var b0 = 1.0 + alpha * a
            var b1 = -2.0 * cosOmega
            var b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            var a1 = -2.0 * cosOmega
            var a2 = 1.0 - alpha / a

            b0 /= a0
            b1 /= a0
            b2 /= a0
            a1 /= a0
            a2 /= a0
            stage.b0 = b0
            stage.b1 = b1
            stage.b2 = b2
            stage.a1 = a1
            stage.a2 = a2
        }
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        computeCoefficients()
        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        // Fast path: fully dry and settled — outside a blend this processor must cost ~nothing.
        if (targetMix == 0f && currentMix < SETTLED_MIX_EPSILON) {
            currentMix = 0f
            stages.forEach { it.clearState() }
            outputBuffer = inputBuffer.slice().order(inputBuffer.order())
            inputBuffer.position(inputBuffer.limit())
            return
        }

        if (outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        // Per-sample ease toward targetMix: at 44.1-48kHz this settles in a few ms, fast
        // enough to track the blend loop's updates while never stepping abruptly.
        val easePerSample = (1000.0 / (MIX_EASE_MS * sampleRate)).toFloat().coerceIn(0f, 1f)
        val shelf = stages[0]
        val mid = stages[1]

        val sampleCount = inputSize / 2
        when (channelCount) {
            1 ->
                repeat(sampleCount) {
                    currentMix += (targetMix - currentMix) * easePerSample
                    val input = inputBuffer.getShort().toDouble() / 32768.0
                    val filtered = mid.processLeft(shelf.processLeft(input))
                    val output = input + (filtered - input) * currentMix
                    outputBuffer.putShort((output * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                }

            2 ->
                repeat(sampleCount / 2) {
                    currentMix += (targetMix - currentMix) * easePerSample
                    val inL = inputBuffer.getShort().toDouble() / 32768.0
                    val inR = inputBuffer.getShort().toDouble() / 32768.0

                    val filteredL = mid.processLeft(shelf.processLeft(inL))
                    val filteredR = mid.processRight(shelf.processRight(inR))

                    val outL = inL + (filteredL - inL) * currentMix
                    val outR = inR + (filteredR - inR) * currentMix

                    outputBuffer.putShort((outL * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                    outputBuffer.putShort((outR * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                }

            else ->
                repeat(sampleCount) {
                    outputBuffer.putShort(inputBuffer.getShort())
                }
        }

        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        stages.forEach { it.clearState() }
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        currentMix = 0f
        targetMix = 0f
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Time constant for the per-sample mix ease, independent of sample rate. */
        private const val MIX_EASE_MS = 15.0
        private const val SETTLED_MIX_EPSILON = 0.001f
    }
}
