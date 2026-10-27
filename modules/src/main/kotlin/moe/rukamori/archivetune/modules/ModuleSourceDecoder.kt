/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

object ModuleSourceDecoder {
    private val alphabet = intArrayOf(56, 115, 112, 105, 110, 101, 122, 107, 120, 118, 113, 114, 119, 109, 104, 116)
    private val mask = sha256("2233412666".toByteArray())
    private val key by lazy {
        val material = unpack("wkriekqeswpwtzesmeiwqttqxenqkekenrwrqrpmehptsvvxkkkwpnzrzknvxisswxxzinxtzi8wte8vvkpqtzq8mnnekv")
        sha256(ByteArray(material.size) { index -> (material[index].toInt() xor mask[index % mask.size].toInt()).toByte() })
    }

    fun decode(input: String): String {
        if (input.length > MAX_SOURCE_BYTES) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        val text = input.removePrefix("\uFEFF").trim()
        val source = if (text.startsWith("8SM1.")) decodeEnvelope(text.substring(5)) else text
        if (source.isBlank()) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        return unwrap(withoutHeader(source))
    }

    private fun decodeEnvelope(payload: String): String {
        val bytes = unpack(payload)
        if (bytes.size < 32) throw ModuleException(ModuleFailure.INTEGRITY)
        val nonce = bytes.copyOfRange(0, 16)
        val tag = bytes.copyOfRange(16, 32)
        val ciphertext = bytes.copyOfRange(32, bytes.size)
        if (!MessageDigest.isEqual(tag, sha256(key + nonce + ciphertext).copyOf(16))) {
            throw ModuleException(ModuleFailure.INTEGRITY)
        }
        val seed = sha256(key + nonce)
        val plaintext = ByteArray(ciphertext.size)
        var offset = 0
        var counter = 0
        while (offset < ciphertext.size) {
            val block = sha256(seed + ByteBuffer.allocate(4).putInt(counter++).array())
            for (index in 0 until minOf(block.size, ciphertext.size - offset)) {
                plaintext[offset + index] = (ciphertext[offset + index].toInt() xor block[index].toInt()).toByte()
            }
            offset += block.size
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plaintext)).toString()
        } catch (error: Exception) {
            throw ModuleException(ModuleFailure.INTEGRITY, error)
        }
    }

    private fun unpack(value: String): ByteArray {
        if (value.length % 2 != 0) throw ModuleException(ModuleFailure.INTEGRITY)
        return ByteArray(value.length / 2) { index ->
            val high = alphabet.indexOf(value[index * 2].code)
            val low = alphabet.indexOf(value[index * 2 + 1].code)
            if (high < 0 || low < 0) throw ModuleException(ModuleFailure.INTEGRITY)
            ((high shl 4) or low).toByte()
        }
    }

    private fun unwrap(value: String): String {
        val match = Regex("^export\\s+const\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*=\\s*`").find(value) ?: return value
        val result = StringBuilder()
        var index = match.range.last + 1
        while (index < value.length) {
            val character = value[index++]
            if (character == '`') {
                if (value.substring(index).trim().removeSuffix(";").isNotBlank()) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                return result.toString()
            }
            if (character == '$' && value.getOrNull(index) == '{') throw ModuleException(ModuleFailure.UNSUPPORTED)
            if (character != '\\') {
                result.append(character)
                continue
            }
            val escaped = value.getOrNull(index++) ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
            when (escaped) {
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'v' -> result.append('\u000B')
                '0' -> result.append('\u0000')
                '\n' -> Unit
                '\r' -> if (value.getOrNull(index) == '\n') index++
                'x', 'u' -> {
                    val count = if (escaped == 'x') 2 else 4
                    val digits = value.substring(index, minOf(index + count, value.length))
                    if (digits.length != count) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                    result.append((digits.toIntOrNull(16) ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)).toChar())
                    index += count
                }
                else -> result.append(escaped)
            }
        }
        throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
    }

    private fun withoutHeader(value: String): String {
        var index = 0
        while (index < value.length) {
            when {
                value[index].isWhitespace() -> index++
                value.startsWith("//", index) -> index = value.indexOf('\n', index).takeIf { it >= 0 }?.plus(1) ?: value.length
                value.startsWith("/*", index) -> index = value.indexOf("*/", index + 2).takeIf { it >= 0 }?.plus(2)
                    ?: throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
                else -> return value.substring(index)
            }
        }
        throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    const val MAX_SOURCE_BYTES = 4 * 1024 * 1024
}
