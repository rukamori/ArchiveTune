/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Immutable
@Serializable
data class SavedAccount(
    val id: String,
    val name: String,
    val email: String,
    val channelHandle: String,
    val innerTubeCookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val ytmSync: Boolean,
    val selectedYtmPlaylists: String,
    val accountIndex: Int? = null,
)

private val savedAccountJson = Json { ignoreUnknownKeys = true }

fun decodeSavedAccounts(raw: String): List<SavedAccount> {
    if (raw.isBlank()) return emptyList()
    return runCatching { savedAccountJson.decodeFromString<List<SavedAccount>>(raw) }.getOrDefault(emptyList())
}

fun encodeSavedAccounts(accounts: List<SavedAccount>): String = savedAccountJson.encodeToString(accounts)
