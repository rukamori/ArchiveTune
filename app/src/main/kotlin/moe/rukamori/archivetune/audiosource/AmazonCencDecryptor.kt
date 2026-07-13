/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * On-device decryptor for Amazon Music's CENC-protected fragmented MP4 streams (FLAC/Opus in MP4).
 *
 * Amazon's instance API (as used by monochrome.tf) returns a `stream_url` pointing at a
 * CENC-encrypted (scheme `cenc`, AES-128-CTR) fragmented MP4 plus a raw `decryption_key`. The web
 * player hands the manifest + key to the browser's EME/ClearKey stack, which decrypts natively.
 * This app has no EME, so we replicate the decryption ourselves.
 *
 * Because AES-CTR is length-preserving, we can decrypt every media sample *in place* and then simply
 * neutralize the protection metadata rather than rebuilding the file:
 *   - the `moov` sample entry `enca`/`encv` is renamed back to its original codec (`fLaC`/`Opus`),
 *   - the protection child box `sinf` and the per-fragment `senc`/`saiz`/`saio` boxes, plus any
 *     `pssh`, are renamed to `free` (an ignored box).
 * Every byte offset (`sidx`, `trun` data offsets, sample sizes) stays identical, so no index needs
 * recomputation. The result is a standard unencrypted FLAC/Opus-in-MP4 that ExoPlayer plays as a
 * plain progressive stream.
 *
 * All parsing is confined to the well-structured `moov`/`moof` metadata containers; `mdat` bytes are
 * only ever touched at the exact sample ranges computed from `trun`. Any structural anomaly causes
 * the whole operation to fail (return false) so the caller can fall through to another source
 * instead of playing garbage.
 */
object AmazonCencDecryptor {
    private const val TAG = "AmazonCenc"

    private data class Box(
        val type: String,
        val start: Int,
        val headerSize: Int,
        val size: Long,
    ) {
        val end: Int get() = (start + size).toInt() // exclusive
        val contentStart: Int get() = start + headerSize
    }

    /** Container boxes whose children start immediately after the 8-byte box header. */
    private val PLAIN_CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "mvex", "moof", "traf", "edts", "dinf")

    /**
     * Decrypts [data] in place. Returns the codec string ("flac"/"opus") on success, or null if the
     * input is not a recognizable CENC MP4 (in which case [data] may be partially modified and must
     * not be used).
     */
    fun decryptInPlace(
        data: ByteArray,
        keyHex: String,
    ): String? {
        val key = hexToBytes(keyHex)
        if (key == null || key.size != 16) {
            Timber.tag(TAG).w("Invalid CENC key (need 16 bytes, got %s)", keyHex.length)
            return null
        }
        return runCatching { decryptInternal(data, key) }
            .getOrElse {
                Timber.tag(TAG).w(it, "CENC decrypt failed")
                null
            }
    }

    private fun decryptInternal(
        data: ByteArray,
        key: ByteArray,
    ): String? {
        val topBoxes = readBoxes(data, 0, data.size)
        val moov = topBoxes.firstOrNull { it.type == "moov" } ?: run {
            Timber.tag(TAG).w("No moov box")
            return null
        }

        // --- Parse the sample encryption defaults + original codec from moov ---
        val tenc = findDescendant(data, moov, listOf("trak", "mdia", "minf", "stbl", "stsd"), "tenc", deep = true)
        if (tenc == null) {
            Timber.tag(TAG).w("No tenc box; stream may be unencrypted")
            return null
        }
        val tencInfo = parseTenc(data, tenc)

        // Locate the encrypted audio sample entry (enca) and its original format (frma).
        val stsd = findDescendant(data, moov, listOf("trak", "mdia", "minf", "stbl"), "stsd", deep = false)
            ?: return null
        // stsd is a FullBox (4) + entry_count (4) before the first sample entry.
        val sampleEntry = readBoxes(data, stsd.contentStart + 8, stsd.end).firstOrNull()
            ?: return null
        val codec = neutralizeSampleEntry(data, sampleEntry)

        // Neutralize any pssh boxes in moov.
        renameChildrenMatching(data, moov, setOf("pssh"))

        // --- Decrypt every fragment ---
        var fragments = 0
        var samplesDecrypted = 0
        var idx = 0
        while (idx < data.size) {
            val box = readBox(data, idx) ?: break
            if (box.type == "moof") {
                val (frags, samples) = decryptFragment(data, box, key, tencInfo)
                fragments += frags
                samplesDecrypted += samples
            }
            idx = box.end
            if (box.size <= 0) break
        }
        Timber.tag(TAG).d("Decrypted %d samples across %d fragment(s), codec=%s", samplesDecrypted, fragments, codec)
        return codec
    }

    private data class TencInfo(
        val perSampleIvSize: Int,
        val constantIv: ByteArray?,
    )

    private fun parseTenc(
        data: ByteArray,
        tenc: Box,
    ): TencInfo {
        // FullBox header = 12 bytes. Then: reserved(1), reserved/blocks(1), isProtected(1),
        // perSampleIvSize(1), default_KID(16), [constant IV if perSampleIvSize==0].
        val base = tenc.start + 12
        val isProtected = data[base + 2].toInt() and 0xFF
        val perSampleIvSize = data[base + 3].toInt() and 0xFF
        var constantIv: ByteArray? = null
        if (isProtected == 1 && perSampleIvSize == 0) {
            val ivSizePos = base + 4 + 16
            val ivSize = data[ivSizePos].toInt() and 0xFF
            constantIv = data.copyOfRange(ivSizePos + 1, ivSizePos + 1 + ivSize)
        }
        return TencInfo(perSampleIvSize, constantIv)
    }

    /**
     * Renames an `enca`/`encv` sample entry back to its original codec (read from the child `frma`
     * box) and renames the protection `sinf` child to `free`. Returns the codec label.
     */
    private fun neutralizeSampleEntry(
        data: ByteArray,
        entry: Box,
    ): String {
        if (entry.type != "enca" && entry.type != "encv") {
            // Already unencrypted (unexpected here, but be lenient).
            return entry.type.trim().lowercase()
        }
        // Audio sample entry: child boxes start at +36 (8 header + 28 audio fields, version 0).
        val childStart = entry.start + 36
        val children = readBoxes(data, childStart, entry.end)
        val sinf = children.firstOrNull { it.type == "sinf" }
        var format = "fLaC"
        if (sinf != null) {
            val frma = readBoxes(data, sinf.contentStart, sinf.end).firstOrNull { it.type == "frma" }
            if (frma != null) {
                format = String(data, frma.contentStart, 4, Charsets.US_ASCII)
            }
            writeType(data, sinf, "free")
        }
        writeType(data, entry, format)
        return when (format.trim().lowercase()) {
            "flac", "fla" -> "flac"
            "opus" -> "opus"
            else -> format.trim().lowercase()
        }
    }

    /** Decrypts all samples in one `moof`+`mdat` fragment. Returns (fragmentCount=1, sampleCount). */
    private fun decryptFragment(
        data: ByteArray,
        moof: Box,
        key: ByteArray,
        tenc: TencInfo,
    ): Pair<Int, Int> {
        val traf = findDescendant(data, moof, emptyList(), "traf", deep = false) ?: return 0 to 0
        val tfhd = readBoxes(data, traf.contentStart, traf.end).firstOrNull { it.type == "tfhd" }
        val trun = readBoxes(data, traf.contentStart, traf.end).firstOrNull { it.type == "trun" } ?: return 0 to 0
        val senc = readBoxes(data, traf.contentStart, traf.end).firstOrNull { it.type == "senc" } ?: return 0 to 0

        val tfhdInfo = tfhd?.let { parseTfhd(data, it) } ?: TfhdInfo(0, null, null, false)
        val sampleSizes = parseTrunSampleSizes(data, trun, tfhdInfo)
        val dataOffset = parseTrunDataOffset(data, trun)

        // Anchor for sample data: base_data_offset if present, else moof start (default-base-is-moof).
        val anchor = tfhdInfo.baseDataOffset ?: moof.start.toLong()
        var samplePos = (anchor + dataOffset).toInt()

        val perSampleIvSize = if (tenc.perSampleIvSize > 0) tenc.perSampleIvSize else 8
        val ivsAndSubsamples = parseSenc(data, senc, sampleSizes.size, perSampleIvSize)

        var decrypted = 0
        for (i in sampleSizes.indices) {
            val size = sampleSizes[i]
            val iv = ivsAndSubsamples.ivs.getOrNull(i) ?: tenc.constantIv
            if (iv == null) {
                Timber.tag(TAG).w("No IV for sample %d", i)
                return 0 to 0
            }
            val subsamples = ivsAndSubsamples.subsamples.getOrNull(i)
            if (subsamples == null || subsamples.isEmpty()) {
                // Whole-sample encryption (typical for audio).
                aesCtrDecryptInPlace(data, samplePos, size, key, iv)
            } else {
                // Subsample encryption: decrypt only the protected spans, as one CTR stream.
                val cipher = newCtrCipher(key, iv)
                var pos = samplePos
                for (ss in subsamples) {
                    pos += ss.clearBytes
                    if (ss.protectedBytes > 0) {
                        val out = cipher.update(data, pos, ss.protectedBytes)
                        if (out != null) System.arraycopy(out, 0, data, pos, out.size)
                        pos += ss.protectedBytes
                    }
                }
            }
            samplePos += size
            decrypted++
        }

        // Neutralize the per-fragment protection boxes so ExoPlayer treats samples as clear.
        renameChildrenMatching(data, traf, setOf("senc", "saiz", "saio"))
        return 1 to decrypted
    }

    private data class TfhdInfo(
        val flags: Int,
        val baseDataOffset: Long?,
        val defaultSampleSize: Long?,
        val hasDefaultSampleSize: Boolean,
    )

    private fun parseTfhd(
        data: ByteArray,
        tfhd: Box,
    ): TfhdInfo {
        val flags = readU24(data, tfhd.start + 9)
        var pos = tfhd.start + 12
        pos += 4 // track_ID
        var baseDataOffset: Long? = null
        if (flags and 0x000001 != 0) {
            baseDataOffset = readU64(data, pos); pos += 8
        }
        if (flags and 0x000002 != 0) pos += 4 // sample_description_index
        if (flags and 0x000008 != 0) pos += 4 // default_sample_duration
        var defaultSampleSize: Long? = null
        if (flags and 0x000010 != 0) {
            defaultSampleSize = readU32(data, pos); pos += 4
        }
        return TfhdInfo(flags, baseDataOffset, defaultSampleSize, defaultSampleSize != null)
    }

    private fun parseTrunDataOffset(
        data: ByteArray,
        trun: Box,
    ): Long {
        val flags = readU24(data, trun.start + 9)
        var pos = trun.start + 12
        pos += 4 // sample_count
        if (flags and 0x000001 != 0) {
            return readS32(data, pos)
        }
        return 0L
    }

    private fun parseTrunSampleSizes(
        data: ByteArray,
        trun: Box,
        tfhd: TfhdInfo,
    ): IntArray {
        val flags = readU24(data, trun.start + 9)
        var pos = trun.start + 12
        val sampleCount = readU32(data, pos).toInt(); pos += 4
        if (flags and 0x000001 != 0) pos += 4 // data_offset
        if (flags and 0x000004 != 0) pos += 4 // first_sample_flags

        val hasDuration = flags and 0x000100 != 0
        val hasSize = flags and 0x000200 != 0
        val hasFlags = flags and 0x000400 != 0
        val hasCto = flags and 0x000800 != 0

        val sizes = IntArray(sampleCount)
        for (i in 0 until sampleCount) {
            if (hasDuration) pos += 4
            if (hasSize) {
                sizes[i] = readU32(data, pos).toInt(); pos += 4
            } else {
                sizes[i] = (tfhd.defaultSampleSize ?: 0L).toInt()
            }
            if (hasFlags) pos += 4
            if (hasCto) pos += 4
        }
        return sizes
    }

    private data class Subsample(
        val clearBytes: Int,
        val protectedBytes: Int,
    )

    private data class SencData(
        val ivs: List<ByteArray>,
        val subsamples: List<List<Subsample>>,
    )

    private fun parseSenc(
        data: ByteArray,
        senc: Box,
        expectedCount: Int,
        perSampleIvSize: Int,
    ): SencData {
        val flags = readU24(data, senc.start + 9)
        var pos = senc.start + 12
        val sampleCount = readU32(data, pos).toInt(); pos += 4
        val useSubsamples = flags and 0x000002 != 0
        val ivs = ArrayList<ByteArray>(sampleCount)
        val subs = ArrayList<List<Subsample>>(sampleCount)
        for (i in 0 until sampleCount) {
            val iv = ByteArray(16)
            for (b in 0 until perSampleIvSize) iv[b] = data[pos + b]
            pos += perSampleIvSize
            ivs.add(iv)
            if (useSubsamples) {
                val ssCount = readU16(data, pos); pos += 2
                val list = ArrayList<Subsample>(ssCount)
                for (j in 0 until ssCount) {
                    val clear = readU16(data, pos); pos += 2
                    val prot = readU32(data, pos).toInt(); pos += 4
                    list.add(Subsample(clear, prot))
                }
                subs.add(list)
            } else {
                subs.add(emptyList())
            }
        }
        return SencData(ivs, subs)
    }

    // --- AES-CTR ---

    private fun newCtrCipher(
        key: ByteArray,
        iv16: ByteArray,
    ): Cipher {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv16))
        return cipher
    }

    private fun aesCtrDecryptInPlace(
        data: ByteArray,
        offset: Int,
        length: Int,
        key: ByteArray,
        iv16: ByteArray,
    ) {
        if (length <= 0) return
        val cipher = newCtrCipher(key, iv16)
        val out = cipher.doFinal(data, offset, length)
        System.arraycopy(out, 0, data, offset, out.size)
    }

    // --- Box helpers (all reads big-endian) ---

    private fun readBox(
        data: ByteArray,
        start: Int,
    ): Box? {
        if (start + 8 > data.size) return null
        var size = readU32(data, start)
        var headerSize = 8
        val type = String(data, start + 4, 4, Charsets.US_ASCII)
        if (size == 1L) {
            if (start + 16 > data.size) return null
            size = readU64(data, start + 8)
            headerSize = 16
        } else if (size == 0L) {
            size = (data.size - start).toLong()
        }
        if (size < headerSize || start + size > data.size) return null
        return Box(type, start, headerSize, size)
    }

    private fun readBoxes(
        data: ByteArray,
        start: Int,
        end: Int,
    ): List<Box> {
        val boxes = ArrayList<Box>()
        var pos = start
        while (pos + 8 <= end) {
            val box = readBox(data, pos) ?: break
            boxes.add(box)
            if (box.size <= 0) break
            pos = box.end
        }
        return boxes
    }

    /**
     * Finds a descendant box by walking [path] container types from [root], then locating [target].
     * When [deep] is true and the direct path lookup fails, falls back to a recursive search through
     * known container boxes (used for `tenc`, which is nested under `stsd/enca/sinf/schi`).
     */
    private fun findDescendant(
        data: ByteArray,
        root: Box,
        path: List<String>,
        target: String,
        deep: Boolean,
    ): Box? {
        var current = root
        for (type in path) {
            val next = childrenOf(data, current).firstOrNull { it.type == type } ?: return if (deep) deepFind(data, root, target) else null
            current = next
        }
        val direct = childrenOf(data, current).firstOrNull { it.type == target }
        if (direct != null) return direct
        return if (deep) deepFind(data, current, target) else null
    }

    private fun deepFind(
        data: ByteArray,
        root: Box,
        target: String,
    ): Box? {
        for (child in childrenOf(data, root)) {
            if (child.type == target) return child
            val nested = deepFind(data, child, target)
            if (nested != null) return nested
        }
        return null
    }

    /** Returns children of a container box, accounting for special header layouts. */
    private fun childrenOf(
        data: ByteArray,
        box: Box,
    ): List<Box> {
        val childStart =
            when (box.type) {
                in PLAIN_CONTAINERS -> box.contentStart
                "stsd" -> box.contentStart + 8 // FullBox(4) + entry_count(4)
                "enca", "encv" -> box.start + 36 // audio sample entry fields
                "sinf", "schi" -> box.contentStart
                else -> return emptyList()
            }
        return readBoxes(data, childStart, box.end)
    }

    /** Renames any direct-child boxes of [parent] whose type is in [types] to `free`. */
    private fun renameChildrenMatching(
        data: ByteArray,
        parent: Box,
        types: Set<String>,
    ) {
        for (child in childrenOf(data, parent)) {
            if (child.type in types) writeType(data, child, "free")
        }
    }

    private fun writeType(
        data: ByteArray,
        box: Box,
        newType: String,
    ) {
        val bytes = newType.toByteArray(Charsets.US_ASCII)
        for (i in 0 until 4) data[box.start + 4 + i] = bytes[i]
    }

    private fun readU16(
        data: ByteArray,
        pos: Int,
    ): Int = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun readU24(
        data: ByteArray,
        pos: Int,
    ): Int = ((data[pos].toInt() and 0xFF) shl 16) or ((data[pos + 1].toInt() and 0xFF) shl 8) or (data[pos + 2].toInt() and 0xFF)

    private fun readU32(
        data: ByteArray,
        pos: Int,
    ): Long =
        ((data[pos].toLong() and 0xFF) shl 24) or
            ((data[pos + 1].toLong() and 0xFF) shl 16) or
            ((data[pos + 2].toLong() and 0xFF) shl 8) or
            (data[pos + 3].toLong() and 0xFF)

    private fun readS32(
        data: ByteArray,
        pos: Int,
    ): Long = readU32(data, pos).toInt().toLong()

    private fun readU64(
        data: ByteArray,
        pos: Int,
    ): Long {
        var result = 0L
        for (i in 0 until 8) result = (result shl 8) or (data[pos + i].toLong() and 0xFF)
        return result
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.trim().removePrefix("0x").filter { !it.isWhitespace() }
        if (clean.length % 2 != 0) return null
        return runCatching {
            ByteArray(clean.length / 2) { i ->
                ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
            }
        }.getOrNull()
    }
}
