/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.notification

import androidx.compose.runtime.Immutable

enum class MediaNotificationAction {
    LIKE,
    REPEAT,
    SHUFFLE,
    START_RADIO,
    LIBRARY,
    DOWNLOAD,
    SLEEP_TIMER,
    CLOSE,
    ;

    companion object {
        const val MAX_ACTIONS = 4

        val DefaultActions: List<MediaNotificationAction> = listOf(LIKE, REPEAT, SHUFFLE, START_RADIO)

        fun decode(value: String?): List<MediaNotificationAction> {
            if (value == null) return DefaultActions
            if (value.isBlank()) return emptyList()
            return normalize(value.split(SEPARATOR).mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } })
        }

        fun encode(actions: List<MediaNotificationAction>): String = normalize(actions).joinToString(SEPARATOR) { it.name }

        fun normalize(actions: List<MediaNotificationAction>): List<MediaNotificationAction> = actions.distinct().take(MAX_ACTIONS)

        private const val SEPARATOR = ","
    }
}

enum class MediaNotificationNavigation {
    TRACKS,
    SEEK,
    ;

    companion object {
        fun fromPreference(value: String?): MediaNotificationNavigation = entries.firstOrNull { it.name == value } ?: TRACKS
    }
}

@Immutable
data class MediaNotificationConfiguration(
    val actions: List<MediaNotificationAction> = MediaNotificationAction.DefaultActions,
    val navigation: MediaNotificationNavigation = MediaNotificationNavigation.TRACKS,
    val seekIncrementSeconds: Int = DEFAULT_SEEK_INCREMENT_SECONDS,
    val sleepTimerMinutes: Int = DEFAULT_SLEEP_TIMER_MINUTES,
    val stopOnDismiss: Boolean = false,
) {
    val seekIncrementMs: Long
        get() = seekIncrementSeconds * 1_000L

    companion object {
        const val DEFAULT_SEEK_INCREMENT_SECONDS = 10
        const val DEFAULT_SLEEP_TIMER_MINUTES = 30

        val SeekIncrementOptions: List<Int> = listOf(5, 10, 15, 30)
        val SleepTimerOptions: List<Int> = listOf(15, 30, 45, 60, 90)

        fun coerceSeekIncrement(seconds: Int?): Int =
            seconds?.takeIf { it in SeekIncrementOptions } ?: DEFAULT_SEEK_INCREMENT_SECONDS

        fun coerceSleepTimerMinutes(minutes: Int?): Int =
            minutes?.takeIf { it in SleepTimerOptions } ?: DEFAULT_SLEEP_TIMER_MINUTES
    }
}

enum class MediaNotificationHealth {
    READY,
    PERMISSION_REQUIRED,
    APP_BLOCKED,
    CHANNEL_BLOCKED,
    CHANNEL_NOT_CREATED,
}

enum class MediaNotificationChannelImportance {
    NONE,
    MIN,
    LOW,
    DEFAULT,
    HIGH,
    NOT_CREATED,
}

@Immutable
data class MediaNotificationSystemStatus(
    val health: MediaNotificationHealth,
    val channelImportance: MediaNotificationChannelImportance,
    val permissionRequestable: Boolean,
)
