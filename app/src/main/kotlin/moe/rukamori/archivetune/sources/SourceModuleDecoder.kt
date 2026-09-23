package moe.rukamori.archivetune.sources

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object SourceModuleDecoder {
    private const val PREFIX = "8SM1."
    private const val ALPHABET = "8spinezkxvqrwmht"
    private const val MAX_SOURCE_CHARS = 4 * 1024 * 1024
    private val formatKey = "7ad5daf8bba8043469dcb3149e204d25a768ec011d4bd2d27a5f4e510d088104"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    suspend fun decode(source: String): String = withContext(Dispatchers.Default) {
        val text = source.removePrefix("\uFEFF").trim()
        if (text.isEmpty() || text.length > MAX_SOURCE_CHARS) throw SourceException(SourceProblem.INVALID_RESPONSE)
        if (!text.startsWith(PREFIX)) {
            if (text.startsWith("8SM")) throw SourceException(SourceProblem.UNSUPPORTED)
            return@withContext text
        }
        val length = text.length - PREFIX.length
        if (length < 66 || length % 2 != 0) throw SourceException(SourceProblem.INVALID_RESPONSE)
        val payload = ByteArray(length / 2) { index ->
            val high = ALPHABET.indexOf(text[PREFIX.length + index * 2])
            val low = ALPHABET.indexOf(text[PREFIX.length + index * 2 + 1])
            if (high < 0 || low < 0) throw SourceException(SourceProblem.INVALID_RESPONSE)
            ((high shl 4) or low).toByte()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(formatKey)
        digest.update(payload, 0, 16)
        digest.update(payload, 32, payload.size - 32)
        val actualTag = digest.digest().copyOf(16)
        if (!MessageDigest.isEqual(actualTag, payload.copyOfRange(16, 32))) {
            throw SourceException(SourceProblem.INVALID_RESPONSE)
        }
        digest.update(formatKey)
        digest.update(payload, 0, 16)
        val seed = digest.digest()
        val plaintext = ByteArray(payload.size - 32)
        val counterBytes = ByteArray(4)
        var offset = 0
        var counter = 0
        while (offset < plaintext.size) {
            ensureActive()
            counterBytes[0] = (counter ushr 24).toByte()
            counterBytes[1] = (counter ushr 16).toByte()
            counterBytes[2] = (counter ushr 8).toByte()
            counterBytes[3] = counter.toByte()
            digest.update(seed)
            val block = digest.digest(counterBytes)
            val count = minOf(block.size, plaintext.size - offset)
            repeat(count) { index ->
                plaintext[offset + index] = (payload[32 + offset + index].toInt() xor block[index].toInt()).toByte()
            }
            offset += count
            counter++
        }
        try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext)).toString()
        } catch (failure: CharacterCodingException) {
            throw SourceException(SourceProblem.INVALID_RESPONSE, failure)
        }
    }
}
