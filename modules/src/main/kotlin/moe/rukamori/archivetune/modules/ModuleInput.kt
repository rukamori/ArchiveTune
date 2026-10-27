/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InterruptedIOException

fun InputStream.readModuleBytes(maximum: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Module input interrupted")
        val size = read(buffer)
        if (size < 0) break
        if (output.size() + size > maximum) throw ModuleException(ModuleFailure.INVALID_DOCUMENT)
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
