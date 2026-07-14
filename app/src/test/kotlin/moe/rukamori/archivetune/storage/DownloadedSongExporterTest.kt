/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.playback.DefaultDownloadRemovalExportPolicy
import moe.rukamori.archivetune.playback.DownloadRemovalExportPolicy

class DownloadedSongExporterTest {

    @Test
    fun testCompletedExportRequiresExactFinalContentLength() {
        assertTrue(isCompleteExport(exportedBytes = 1_024L, completedContentLength = 1_024L))
        assertTrue(!isCompleteExport(exportedBytes = 1_023L, completedContentLength = 1_024L))
        assertTrue(!isCompleteExport(exportedBytes = 1_025L, completedContentLength = 1_024L))
        assertTrue(!isCompleteExport(exportedBytes = 0L, completedContentLength = 0L))
    }

    @Test
    fun testMalformedMp4MetadataWriteFails() {
        val tempFile = File.createTempFile("malformed-export-", ".m4a")
        try {
            tempFile.writeText("not an mp4 file")
            assertFalse(tempFile.writeMp4Metadata(testMetadata(), artworkBytes = null))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSafReplacementDoesNotDeletePreviousDocumentBeforeRename() {
        val events = mutableListOf<String>()
        val committed =
            commitSafReplacement(
                stagingDocument = "staging",
                finalName = "song.m4a",
                renameDocument = { document, _ ->
                    events += "rename:$document"
                    if (document == "staging") "replacement" else document
                },
                deleteStagingDocument = { document ->
                    events += "delete-staging:$document"
                    true
                },
                deletePreviousDocuments = { replacement ->
                    events += "delete-previous:$replacement"
                    true
                },
            )

        assertTrue(committed)
        assertEquals(
            listOf("rename:staging", "delete-previous:replacement", "rename:replacement"),
            events,
        )
    }

    @Test
    fun testSafRenameFailurePreservesPreviousDocument() {
        val events = mutableListOf<String>()
        val committed =
            commitSafReplacement(
                stagingDocument = "staging",
                finalName = "song.m4a",
                renameDocument = { document, _ ->
                    events += "rename:$document"
                    null
                },
                deleteStagingDocument = { document ->
                    events += "delete-staging:$document"
                    true
                },
                deletePreviousDocuments = { replacement ->
                    events += "delete-previous:$replacement"
                    true
                },
            )

        assertFalse(committed)
        assertEquals(listOf("rename:staging", "delete-staging:staging"), events)
    }

    @Test
    fun testConcurrentFolderChangeExportIsQueuedInsteadOfDropped() =
        runBlocking {
            val coordinator = SongExportCoordinator()
            val selectedFolder = AtomicReference("old-folder")
            val destinations = mutableListOf<String>()
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondRequested = CompletableDeferred<Unit>()

            val first =
                async(Dispatchers.Default) {
                    coordinator.run("song-id") {
                        destinations += selectedFolder.get()
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                }
            firstStarted.await()
            selectedFolder.set("new-folder")
            val second =
                async(Dispatchers.Default) {
                    secondRequested.complete(Unit)
                    coordinator.run("song-id") {
                        destinations += selectedFolder.get()
                    }
                }
            secondRequested.await()
            releaseFirst.complete(Unit)
            first.await()
            second.await()

            assertEquals(listOf("old-folder", "new-folder"), destinations)
        }

    @Test
    fun testDownloadRemovalPreservesExportByDefault() {
        assertEquals(DownloadRemovalExportPolicy.PRESERVE, DefaultDownloadRemovalExportPolicy)
    }

    @Test
    fun testExportFileExtensionMapping() {
        assertEquals("m4a", exportFileExtension("audio/aac"))
        assertEquals("m4a", exportFileExtension("audio/flac"))
        assertEquals("m4a", exportFileExtension("audio/mpeg"))
        assertEquals("m4a", exportFileExtension("audio/opus"))
        assertEquals("m4a", exportFileExtension("audio/webm"))
        assertEquals("m4a", exportFileExtension("audio/wav"))
        assertEquals("m4a", exportFileExtension("audio/x-m4a"))
        assertEquals("m4a", exportFileExtension(null))
    }

    @Test
    fun testExportMimeTypeMapping() {
        assertEquals("audio/mp4", exportMimeType("video/mp4"))
        assertEquals("audio/mp4", exportMimeType("application/mp4"))
        assertEquals("audio/mp4", exportMimeType("audio/x-m4a"))
        assertEquals("audio/mp4", exportMimeType("video/webm"))
        assertEquals("audio/mp4", exportMimeType("audio/mpeg"))
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
        assertEquals("My Favorite Song - Awesome Artist [abcdef123].m4a", fileName)
    }

    @Test
    fun testOnlyMp4AacOrAlacCanUseDirectM4aFastPath() {
        assertTrue(canCopyDirectlyToM4a(testFormat("audio/mp4", "mp4a.40.2")))
        assertTrue(canCopyDirectlyToM4a(testFormat("audio/mp4; codecs=\"mp4a.40.2\"", "")))
        assertTrue(canCopyDirectlyToM4a(testFormat("audio/x-m4a", "alac")))
        assertFalse(canCopyDirectlyToM4a(testFormat("audio/webm", "opus")))
        assertFalse(canCopyDirectlyToM4a(testFormat("audio/mpeg", "mp3")))
        assertFalse(canCopyDirectlyToM4a(null))
    }

    @Test
    fun testLyricsAndSourceAreWrittenToM4aMetadata() {
        val metadata =
            testMetadata().copy(
                lyrics = "[00:01.20]First line\n[00:03.40]Second line",
                lyricsSource = "REMOTE",
                synchronizedLyrics = true,
            )

        val bytes = buildMp4IlstAtom(metadata, artworkBytes = null)
        val text = bytes.toString(Charsets.ISO_8859_1)

        assertTrue(text.contains("First line"))
        assertTrue(text.contains("Lyrics Source"))
        assertTrue(text.contains("Synchronized LRC"))
        assertTrue(metadata.lyrics!!.hasSynchronizedLyrics())
        assertFalse("Plain lyrics without timestamps".hasSynchronizedLyrics())
    }

    @Test
    fun testM4aStructureRejectsTruncatedExport() {
        val file = File.createTempFile("m4a-structure-", ".m4a")
        try {
            file.writeBytes(
                topLevelAtom("ftyp", byteArrayOf(0, 0, 0, 0)) +
                    topLevelAtom("moov", byteArrayOf(0, 0, 0, 0)) +
                    topLevelAtom("mdat", byteArrayOf(1, 2, 3, 4)),
            )
            assertTrue(file.isStructurallyValidM4a())

            file.writeBytes(file.readBytes().dropLast(1).toByteArray())
            assertFalse(file.isStructurallyValidM4a())
        } finally {
            file.delete()
        }
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

    private fun testMetadata() =
        ExportedSongMetadata(
            id = "test-id",
            title = "Test Song",
            artists = listOf("Test Artist"),
            album = "Test Album",
            durationSeconds = 180,
            thumbnailUrl = null,
            downloadedAt = null,
        )

    private fun testFormat(
        mimeType: String,
        codecs: String,
    ) =
        FormatEntity(
            id = "test-id",
            itag = 1,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = 128_000,
            sampleRate = 44_100,
            contentLength = 1_024L,
            loudnessDb = null,
            playbackUrl = null,
        )

    private fun topLevelAtom(
        type: String,
        payload: ByteArray,
    ): ByteArray {
        val bytes = ByteArray(payload.size + 8)
        bytes.writeIntAt(0, bytes.size)
        System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, bytes, 4, 4)
        System.arraycopy(payload, 0, bytes, 8, payload.size)
        return bytes
    }
}
