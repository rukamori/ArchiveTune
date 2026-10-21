/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSongScanConfigTest {
    @Test
    fun defaultScanExcludesAndroidStorage() {
        val config = LocalSongScanConfig()

        assertTrue(config.shouldScanFolder("Music/Albums"))
        assertFalse(config.shouldScanFolder("Android/media/com.whatsapp"))
    }

    @Test
    fun includedFoldersLimitScanToSelectedFolderAndDescendants() {
        val config = LocalSongScanConfig(includedFolders = setOf("Music/Albums"))

        assertTrue(config.shouldScanFolder("Music/Albums/Artist"))
        assertFalse(config.shouldScanFolder("Music/Podcasts"))
        assertFalse(config.shouldScanFolder("Android/media/com.whatsapp"))
    }

    @Test
    fun excludedFoldersTakePrecedenceOverIncludedFolders() {
        val config =
            LocalSongScanConfig(
                includedFolders = setOf("Music"),
                excludedFolders = setOf("Music/WhatsApp"),
            )

        assertTrue(config.shouldScanFolder("Music/Albums"))
        assertFalse(config.shouldScanFolder("Music/WhatsApp/Audio"))
    }

    @Test
    fun selectedAndroidSubfolderCanBeScannedExplicitly() {
        val config = LocalSongScanConfig(includedFolders = setOf("Android/media/MyMusic"))

        assertTrue(config.shouldScanFolder("Android/media/MyMusic"))
        assertFalse(config.shouldScanFolder("Android/media/com.whatsapp"))
    }
}
