/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Small shared helpers reused by both the Tidal and Qobuz instance managers.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import moe.rukamori.archivetune.R

/** Copies [entries] to the clipboard as a newline-separated list and toasts the count. */
fun copyToClipboard(
    context: Context,
    label: String,
    entries: List<String>,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, entries.joinToString("\n")))
    Toast.makeText(
        context,
        context.getString(R.string.source_copied, entries.size),
        Toast.LENGTH_SHORT,
    ).show()
}
