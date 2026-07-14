/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class DownloadedSongExporterTest {

    @Test
    fun testCompletedExportRequiresExactFinalContentLength() {
        assertTrue(isCompleteExport(exportedBytes = 1_024L, completedContentLength = 1_024L))
        assertTrue(!isCompleteExport(exportedBytes = 1_023L, completedContentLength = 1_024L))
        assertTrue(!isCompleteExport(exportedBytes = 1_025L, completedContentLength = 1_024L))
        assertTrue(!isCompleteExport(exportedBytes = 0L, completedContentLength = 0L))
    }

    @Test
    fun testExportFileExtensionMapping() {
        assertEquals("aac", exportFileExtension("audio/aac"))
        assertEquals("aac", exportFileExtension("audio/aac-adts"))
        assertEquals("aac", exportFileExtension("audio/x-aac"))
        assertEquals("flac", exportFileExtension("audio/flac"))
        assertEquals("mp3", exportFileExtension("audio/mpeg"))
        assertEquals("ogg", exportFileExtension("audio/opus"))
        assertEquals("ogg", exportFileExtension("application/ogg"))
        assertEquals("webm", exportFileExtension("audio/webm"))
        assertEquals("wav", exportFileExtension("audio/wav"))
        assertEquals("m4a", exportFileExtension("audio/x-m4a")) // fallback/default
        assertEquals("m4a", exportFileExtension(null))
    }

    @Test
    fun testExportMimeTypeMapping() {
        assertEquals("audio/mp4", exportMimeType("video/mp4"))
        assertEquals("audio/mp4", exportMimeType("application/mp4"))
        assertEquals("audio/mp4", exportMimeType("audio/x-m4a"))
        assertEquals("audio/webm", exportMimeType("video/webm"))
        assertEquals("audio/mpeg", exportMimeType("audio/mpeg"))
        assertEquals("audio/mp4", exportMimeType(""))
        assertEquals("audio/mp4", exportMimeType(null))
    }

    @Test
    fun testAdjustStcoAtomOffsets() {
        // Build a mock stco atom structure:
        // Size (4 bytes) | Type 'stco' (4 bytes) | Version/Flags (4 bytes) | Entry Count (4 bytes) | Entries...
        val entryCount = 3
        val headerSize = 8
        val fullBoxHeaderSize = 4
        val entryCountOffset = headerSize + fullBoxHeaderSize
        val atomSize = entryCountOffset + 4 + (entryCount * 4)

        val buffer = ByteArray(atomSize)
        // Write size
        buffer.writeIntAt(0, atomSize)
        // Write type 'stco'
        System.arraycopy("stco".toByteArray(Charsets.ISO_8859_1), 0, buffer, 4, 4)
        // Write entry count
        buffer.writeIntAt(entryCountOffset, entryCount)

        // Write entries: original offsets 100, 200, 300
        buffer.writeIntAt(entryCountOffset + 4, 100)
        buffer.writeIntAt(entryCountOffset + 8, 200)
        buffer.writeIntAt(entryCountOffset + 12, 300)

        val atom = Mp4Atom(start = 0, headerSize = headerSize, size = atomSize, type = "stco")
        val delta = 50L
        buffer.adjustStcoAtom(atom, delta)

        // Verify offsets shifted: 100 -> 150, 200 -> 250, 300 -> 350
        assertEquals(150L, buffer.readUInt32(entryCountOffset + 4))
        assertEquals(250L, buffer.readUInt32(entryCountOffset + 8))
        assertEquals(350L, buffer.readUInt32(entryCountOffset + 12))
    }

    @Test
    fun testAdjustStcoAtomOverflowThrows() {
        val entryCount = 1
        val headerSize = 8
        val fullBoxHeaderSize = 4
        val entryCountOffset = headerSize + fullBoxHeaderSize
        val atomSize = entryCountOffset + 4 + 4

        val buffer = ByteArray(atomSize)
        buffer.writeIntAt(0, atomSize)
        System.arraycopy("stco".toByteArray(Charsets.ISO_8859_1), 0, buffer, 4, 4)
        buffer.writeIntAt(entryCountOffset, entryCount)

        // Set original offset to UIntMaxValue - 10
        val originalOffset = 0xFFFF_FFF0L
        buffer.writeIntAt(entryCountOffset + 4, originalOffset.toInt())

        val atom = Mp4Atom(start = 0, headerSize = headerSize, size = atomSize, type = "stco")
        // delta + originalOffset will be 0x10000000A (> 0xFFFFFFFF)
        val delta = 30L

        try {
            buffer.adjustStcoAtom(atom, delta)
            fail("Expected IllegalArgumentException or require validation check failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Chunk offset overflow"))
        }
    }

    @Test
    fun testAdjustCo64AtomOffsets() {
        // Build a mock co64 atom structure:
        // Size (4 bytes) | Type 'co64' (4 bytes) | Version/Flags (4 bytes) | Entry Count (4 bytes) | Entries (8 bytes each)...
        val entryCount = 2
        val headerSize = 8
        val fullBoxHeaderSize = 4
        val entryCountOffset = headerSize + fullBoxHeaderSize
        val atomSize = entryCountOffset + 4 + (entryCount * 8)

        val buffer = ByteArray(atomSize)
        buffer.writeIntAt(0, atomSize)
        System.arraycopy("co64".toByteArray(Charsets.ISO_8859_1), 0, buffer, 4, 4)
        buffer.writeIntAt(entryCountOffset, entryCount)

        // Write entries: original offsets 1000L, 2000L
        buffer.writeLongAt(entryCountOffset + 4, 1000L)
        buffer.writeLongAt(entryCountOffset + 12, 2000L)

        val atom = Mp4Atom(start = 0, headerSize = headerSize, size = atomSize, type = "co64")
        val delta = 500L
        buffer.adjustCo64Atom(atom, delta)

        // Verify offsets shifted: 1000 -> 1500, 2000 -> 2500
        assertEquals(1500L, buffer.readUInt64(entryCountOffset + 4))
        assertEquals(2500L, buffer.readUInt64(entryCountOffset + 12))
    }

    @Test
    fun testBuildId3TagStructure() {
        val metadata = ExportedSongMetadata(
            id = "test-id-123",
            title = "Song Title",
            artists = listOf("Artist A", "Artist B"),
            album = "The Album",
            durationSeconds = 240,
            thumbnailUrl = "https://example.com/image.jpg",
            downloadedAt = "2026-07-07T12:00:00"
        )

        val tagBytes = buildId3Tag(
            metadata = metadata,
            format = null,
            artworkBytes = null
        )

        assertNotNull(tagBytes)
        assertTrue(tagBytes.size > 10)

        // Verify ID3 header 'ID3' (first 3 bytes)
        val headerMarker = String(tagBytes.copyOfRange(0, 3), Charsets.ISO_8859_1)
        assertEquals("ID3", headerMarker)

        // Verify ID3 version is 4.0 (byte index 3, 4)
        assertEquals(4.toByte(), tagBytes[3])
        assertEquals(0.toByte(), tagBytes[4])
    }

    @Test
    fun testBuildExportBaseNameAndFileNameWithIdMarker() {
        val metadata = ExportedSongMetadata(
            id = "abcdef123",
            title = "My Favorite Song",
            artists = listOf("Awesome Artist"),
            album = "Cool Album",
            durationSeconds = 180,
            thumbnailUrl = null,
            downloadedAt = null
        )

        val baseName = buildExportBaseName(metadata, "abcdef123")
        assertEquals("My Favorite Song - Awesome Artist [abcdef123]", baseName)

        val fileName = buildExportFileName(metadata, "audio/mpeg")
        assertEquals("My Favorite Song - Awesome Artist [abcdef123].mp3", fileName)
    }

    @Test
    fun testReadTopLevelMp4Atoms() {
        // Build mock MP4 bytes representing two atoms: 'ftyp' (size 12) and 'moov' (size 20)
        val ftypSize = 12
        val moovSize = 20
        val totalSize = ftypSize + moovSize
        val bytes = ByteArray(totalSize)

        // Atom 1: 'ftyp' at offset 0
        bytes.writeIntAt(0, ftypSize)
        System.arraycopy("ftyp".toByteArray(Charsets.ISO_8859_1), 0, bytes, 4, 4)

        // Atom 2: 'moov' at offset 12
        bytes.writeIntAt(ftypSize, moovSize)
        System.arraycopy("moov".toByteArray(Charsets.ISO_8859_1), 0, bytes, ftypSize + 4, 4)

        val tempFile = File.createTempFile("mock-mp4-test", ".mp4")
        try {
            tempFile.writeBytes(bytes)
            val parsedAtoms = tempFile.readTopLevelMp4Atoms()

            assertEquals(2, parsedAtoms.size)
            assertEquals("ftyp", parsedAtoms[0].type)
            assertEquals(0, parsedAtoms[0].start)
            assertEquals(ftypSize, parsedAtoms[0].size)

            assertEquals("moov", parsedAtoms[1].type)
            assertEquals(ftypSize, parsedAtoms[1].start)
            assertEquals(moovSize, parsedAtoms[1].size)
        } finally {
            tempFile.delete()
        }
    }
}
