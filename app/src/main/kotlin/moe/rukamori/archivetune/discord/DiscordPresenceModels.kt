/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.discord

import androidx.compose.runtime.Immutable

@Immutable
data class DiscordPresenceButton(
    val label: String,
    val url: String,
)

@Immutable
data class DiscordPresenceAssets(
    val largeImage: String? = null,
    val largeText: String? = null,
    val largeUrl: String? = null,
    val smallImage: String? = null,
    val smallText: String? = null,
    val smallUrl: String? = null,
)

@Immutable
data class DiscordPresenceTimestamps(
    val startEpochSeconds: Long? = null,
    val endEpochSeconds: Long? = null,
)

@Immutable
data class DiscordPresenceActivity(
    val applicationId: Long,
    val name: String?,
    val type: DiscordActivityType,
    val details: String?,
    val state: String?,
    val detailsUrl: String? = null,
    val stateUrl: String? = null,
    val assets: DiscordPresenceAssets = DiscordPresenceAssets(),
    val buttons: List<DiscordPresenceButton> = emptyList(),
    val timestamps: DiscordPresenceTimestamps = DiscordPresenceTimestamps(),
    val statusDisplayType: DiscordStatusDisplayType = DiscordStatusDisplayType.State,
    val onlineStatus: DiscordOnlineStatus = DiscordOnlineStatus.Online,
)

enum class DiscordActivityType(
    val nativeValue: Int,
) {
    Playing(0),
    Streaming(1),
    Listening(2),
    Watching(3),
    Competing(5),
    ;

    companion object {
        fun fromPreference(value: String): DiscordActivityType =
            when (value.uppercase()) {
                "PLAYING" -> Playing
                "STREAMING" -> Streaming
                "WATCHING" -> Watching
                "COMPETING" -> Competing
                else -> Listening
            }
    }
}

enum class DiscordStatusDisplayType(
    val nativeValue: Int,
) {
    Name(0),
    State(1),
    Details(2),
}

enum class DiscordOnlineStatus(
    val nativeValue: Int,
) {
    Online(0),
    Idle(3),
    Dnd(4),
    Invisible(5),
    Streaming(6),
    ;

    companion object {
        fun fromPreference(value: String): DiscordOnlineStatus =
            when (value.lowercase()) {
                "idle" -> Idle
                "dnd" -> Dnd
                "invisible" -> Invisible
                "streaming" -> Streaming
                else -> Online
            }
    }
}

