/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences

@Immutable
internal data class WidgetMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artPath: String?,
    val queueIndex: Int,
)

internal fun WidgetMediaItem.widgetItemId(): Long = (id.hashCode().toLong() and 0xFFFFFFFFL) + (queueIndex.coerceAtLeast(0).toLong() shl 32) + 1L

internal fun Preferences.widgetUpNext(): List<WidgetMediaItem> = this[MusicWidgetKeys.UP_NEXT].decodeWidgetMediaItems()

internal fun Preferences.widgetRecentItems(): List<WidgetMediaItem> = this[MusicWidgetKeys.RECENT_ITEMS].decodeWidgetMediaItems()

internal fun List<WidgetMediaItem>.encodeWidgetMediaItems(): String =
    joinToString(separator = ItemSeparator) { item ->
        listOf(
            item.id,
            item.title,
            item.subtitle,
            item.artPath.orEmpty(),
            item.queueIndex.toString(),
        ).joinToString(separator = FieldSeparator) { field -> field.sanitizeField() }
    }

private fun String?.decodeWidgetMediaItems(): List<WidgetMediaItem> =
    orEmpty()
        .split(ItemSeparator)
        .mapNotNull { encoded ->
            val fields = encoded.split(FieldSeparator)
            if (fields.size != FieldCount) return@mapNotNull null
            val id = fields[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WidgetMediaItem(
                id = id,
                title = fields[1],
                subtitle = fields[2],
                artPath = fields[3].takeIf { it.isNotBlank() },
                queueIndex = fields[4].toIntOrNull() ?: NoQueueIndex,
            )
        }

private fun String.sanitizeField(): String =
    replace(ItemSeparator, " ")
        .replace(FieldSeparator, " ")
        .replace('\n', ' ')
        .trim()

internal const val NoQueueIndex = -1
private const val ItemSeparator = "\u001F"
private const val FieldSeparator = "\u001E"
private const val FieldCount = 5
