/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import me.bush.translator.Language
import me.bush.translator.Translator
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DiscordActivityButton1CustomUrlKey
import moe.rukamori.archivetune.constants.DiscordActivityButton1EnabledKey
import moe.rukamori.archivetune.constants.DiscordActivityButton1LabelKey
import moe.rukamori.archivetune.constants.DiscordActivityButton1UrlSourceKey
import moe.rukamori.archivetune.constants.DiscordActivityButton2CustomUrlKey
import moe.rukamori.archivetune.constants.DiscordActivityButton2EnabledKey
import moe.rukamori.archivetune.constants.DiscordActivityButton2LabelKey
import moe.rukamori.archivetune.constants.DiscordActivityButton2UrlSourceKey
import moe.rukamori.archivetune.constants.DiscordActivityDetailsKey
import moe.rukamori.archivetune.constants.DiscordActivityNameKey
import moe.rukamori.archivetune.constants.DiscordActivityPlatformKey
import moe.rukamori.archivetune.constants.DiscordActivityStateKey
import moe.rukamori.archivetune.constants.DiscordActivityTypeKey
import moe.rukamori.archivetune.constants.DiscordLargeImageCustomUrlKey
import moe.rukamori.archivetune.constants.DiscordLargeImageTypeKey
import moe.rukamori.archivetune.constants.DiscordLargeTextCustomKey
import moe.rukamori.archivetune.constants.DiscordLargeTextSourceKey
import moe.rukamori.archivetune.constants.DiscordPresenceStatusKey
import moe.rukamori.archivetune.constants.DiscordSmallImageCustomUrlKey
import moe.rukamori.archivetune.constants.DiscordSmallImageTypeKey
import moe.rukamori.archivetune.constants.EnableTranslatorKey
import moe.rukamori.archivetune.constants.TranslatorContextsKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.discord.DiscordActivityPlatform
import moe.rukamori.archivetune.discord.DiscordActivityType
import moe.rukamori.archivetune.discord.DiscordOnlineStatus
import moe.rukamori.archivetune.discord.DiscordPresenceActivity
import moe.rukamori.archivetune.discord.DiscordPresenceAssets
import moe.rukamori.archivetune.discord.DiscordPresenceButton
import moe.rukamori.archivetune.discord.DiscordPresenceTimestamps
import moe.rukamori.archivetune.discord.DiscordSocialPresenceClient
import moe.rukamori.archivetune.discord.DiscordStatusDisplayType
import timber.log.Timber

class DiscordRPC(
    private val context: Context,
    private val accessToken: String,
) {
    companion object {
        private const val PAUSE_IMAGE_URL =
            "https://raw.githubusercontent.com/ArchiveTuneApp/ArchiveTune/main/fastlane/metadata/android/en-US/images/RPC/pause_icon.png"
        private const val APP_ICON_URL =
            "https://raw.githubusercontent.com/ArchiveTuneApp/ArchiveTune/main/fastlane/metadata/android/en-US/images/icon.png"
        private const val TAG = "DiscordRPC"
    }

    private val translationCache: MutableMap<String, String> = mutableMapOf()
    private var lastSongId: String? = null

    fun isRpcRunning(): Boolean = DiscordSocialPresenceClient.isStarted

    suspend fun stopActivity() {
        DiscordSocialPresenceClient.clearPresence(accessToken).getOrElse {
            Timber.tag(TAG).v(it, "clearPresence failed")
        }
    }

    suspend fun closeRPC() {
        DiscordSocialPresenceClient.close().getOrElse {
            Timber.tag(TAG).v(it, "close failed")
        }
    }

    suspend fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        isPaused: Boolean = false,
    ) = runCatching {
        if (lastSongId != song.song.id) {
            translationCache.clear()
            DiscordImageResolver.clearCache()
            lastSongId = song.song.id
        }

        val translatedMap = translateSongFields(song)
        val appName = context.getString(R.string.app_name)
        val namePref = context.dataStore[DiscordActivityNameKey] ?: "APP"
        val detailsPref = context.dataStore[DiscordActivityDetailsKey] ?: "SONG"
        val statePref = context.dataStore[DiscordActivityStateKey] ?: "ARTIST"

        val activityName =
            sourceValue(
                pref = namePref,
                song = song,
                translatedMap = translatedMap,
                default = appName,
            )

        val activityDetails =
            sourceValue(
                pref = detailsPref,
                song = song,
                translatedMap = translatedMap,
                default = song.song.title.ifBlank { appName },
            ).toDiscordText(maxLength = 128, fallback = song.song.title.ifBlank { appName })

        val activityState =
            sourceValue(
                pref = statePref,
                song = song,
                translatedMap = translatedMap,
                default = song.artists.joinToString { it.name }.ifBlank { appName },
            ).toDiscordText(maxLength = 128, fallback = appName)

        val baseSongUrl = song.youtubeMusicUrl()
        val resolvedImages = DiscordImageResolver.resolveImagesForSong(context, song)
        val largeImageType = context.dataStore[DiscordLargeImageTypeKey] ?: "thumbnail"
        val largeImageCustomUrl = context.dataStore[DiscordLargeImageCustomUrlKey] ?: ""
        val smallImageType = context.dataStore[DiscordSmallImageTypeKey] ?: "artist"
        val smallImageCustomUrl = context.dataStore[DiscordSmallImageCustomUrlKey] ?: ""

        val largeImage =
            when (largeImageType.lowercase()) {
                "appicon" -> {
                    APP_ICON_URL
                }

                else -> {
                    DiscordImageResolver.buildImageUrl(
                        imageType = largeImageType,
                        customUrl = largeImageCustomUrl,
                        resolvedImages = resolvedImages,
                        song = song,
                    )
                }
            }

        val smallImage =
            when {
                isPaused -> {
                    PAUSE_IMAGE_URL
                }

                smallImageType.equals("appicon", ignoreCase = true) -> {
                    APP_ICON_URL
                }

                else -> {
                    DiscordImageResolver.buildImageUrl(
                        imageType = smallImageType,
                        customUrl = smallImageCustomUrl,
                        resolvedImages = resolvedImages,
                        song = song,
                    )
                }
            }

        val largeText = resolveLargeText(song, translatedMap)
        val smallText =
            if (isPaused) {
                context.getString(R.string.discord_paused)
            } else {
                resolveSmallText(song, translatedMap, smallImageType, appName)
            }

        val buttons = resolveButtons(song)
        val activityType =
            DiscordActivityType.fromPreference(
                context.dataStore[DiscordActivityTypeKey] ?: "LISTENING",
            )
        val platform =
            DiscordActivityPlatform.fromPreference(
                context.dataStore[DiscordActivityPlatformKey] ?: "android",
            )
        val status =
            DiscordOnlineStatus.fromPreference(
                context.dataStore[DiscordPresenceStatusKey] ?: "online",
            )

        val timestamps =
            buildTimestamps(
                song = song,
                currentPlaybackTimeMillis = currentPlaybackTimeMillis,
                isPaused = isPaused,
            )

        val activity =
            DiscordPresenceActivity(
                applicationId = BuildConfig.DISCORD_APPLICATION_ID_LONG,
                name = activityName.toDiscordText(maxLength = 128, fallback = appName),
                type = activityType,
                details = activityDetails,
                state = activityState,
                detailsUrl = baseSongUrl.toDiscordUrl(),
                assets =
                    DiscordPresenceAssets(
                        largeImage = largeImage.toDiscordUrl(),
                        largeText = largeText.toDiscordText(maxLength = 128),
                        largeUrl = largeImage.toDiscordUrl(),
                        smallImage = smallImage.toDiscordUrl(),
                        smallText = smallText.toDiscordText(maxLength = 128),
                        smallUrl = baseSongUrl.toDiscordUrl(),
                    ),
                buttons = buttons,
                timestamps = timestamps,
                statusDisplayType = DiscordStatusDisplayType.State,
                supportedPlatforms = platform,
                onlineStatus = status,
            )

        DiscordSocialPresenceClient
            .updatePresence(
                accessToken = accessToken,
                activity = activity,
            ).getOrThrow()

        Timber.tag(TAG).i(
            "Updated Discord presence via Gateway name=%s details=%s state=%s",
            activityName,
            activityDetails,
            activityState,
        )
    }

    suspend fun refreshActivity(
        song: Song,
        currentPlaybackTimeMillis: Long,
        isPaused: Boolean = false,
    ) = runCatching {
        updateSong(song, currentPlaybackTimeMillis, isPaused).getOrThrow()
    }

    private suspend fun translateSongFields(song: Song): Map<String, String> {
        val translatorEnabled = context.dataStore[EnableTranslatorKey] ?: false
        if (!translatorEnabled) return emptyMap()

        val contextList =
            (context.dataStore[TranslatorContextsKey] ?: "{song}")
                .split(",")
                .map { it.trim() }
        val targetLang = context.dataStore[TranslatorTargetLangKey] ?: "ENGLISH"
        val rawMap =
            mapOf(
                "{song}" to song.song.title,
                "{artist}" to song.artists.joinToString { it.name },
                "{album}" to (song.song.albumName ?: song.album?.title ?: ""),
            )
        val translatedMap = mutableMapOf<String, String>()

        runCatching {
            val translator = Translator()
            contextList.forEach { key ->
                val value = rawMap[key]?.takeIf { it.isNotBlank() } ?: return@forEach
                val cacheKey = "${song.song.id}:$key:$targetLang"
                val cached = translationCache[cacheKey]
                if (cached != null) {
                    translatedMap[key] = cached
                } else {
                    val translated =
                        runCatching {
                            translator
                                .translateBlocking(
                                    value,
                                    Language.valueOf(targetLang.uppercase()),
                                ).translatedText
                        }.getOrElse {
                            Timber.tag(TAG).e(it, "Translation failed for %s", key)
                            value
                        }
                    translationCache[cacheKey] = translated
                    translatedMap[key] = translated
                }
            }
        }.onFailure {
            Timber.tag(TAG).e(it, "Translator init failed")
        }

        return translatedMap
    }

    private fun sourceValue(
        pref: String,
        song: Song,
        translatedMap: Map<String, String>,
        default: String,
    ): String =
        when (pref.uppercase()) {
            "ARTIST" -> translatedMap["{artist}"] ?: song.artists.firstOrNull()?.name ?: default
            "ALBUM" -> translatedMap["{album}"] ?: song.song.albumName ?: song.album?.title ?: default
            "SONG" -> translatedMap["{song}"] ?: song.song.title.ifBlank { default }
            "APP" -> context.getString(R.string.app_name)
            else -> default
        }

    private suspend fun resolveLargeText(
        song: Song,
        translatedMap: Map<String, String>,
    ): String? =
        when ((context.dataStore[DiscordLargeTextSourceKey] ?: "album").lowercase()) {
            "song" -> translatedMap["{song}"] ?: song.song.title
            "artist" -> translatedMap["{artist}"] ?: song.artists.firstOrNull()?.name
            "album" -> translatedMap["{album}"] ?: song.song.albumName ?: song.album?.title ?: song.song.title
            "app" -> context.getString(R.string.app_name)
            "custom" -> (context.dataStore[DiscordLargeTextCustomKey] ?: "").ifBlank { null }
            "dontshow" -> null
            else -> translatedMap["{album}"] ?: song.song.albumName ?: song.album?.title
        }

    private fun resolveSmallText(
        song: Song,
        translatedMap: Map<String, String>,
        smallImageType: String,
        appName: String,
    ): String? {
        val base =
            when (smallImageType.lowercase()) {
                "song" -> translatedMap["{song}"] ?: song.song.title
                "artist" -> translatedMap["{artist}"] ?: song.artists.firstOrNull()?.name
                "thumbnail", "album" -> translatedMap["{album}"] ?: song.song.albumName ?: song.album?.title
                "appicon", "app" -> appName
                "dontshow", "none" -> null
                else -> translatedMap["{artist}"] ?: song.artists.firstOrNull()?.name
            }

        return base?.let { "$it on $appName" }
    }

    private suspend fun resolveButtons(song: Song): List<DiscordPresenceButton> {
        val button1Label = context.dataStore[DiscordActivityButton1LabelKey] ?: "Listen on YouTube Music"
        val button1Enabled = context.dataStore[DiscordActivityButton1EnabledKey] ?: true
        val button2Label = context.dataStore[DiscordActivityButton2LabelKey] ?: "Go to ArchiveTune"
        val button2Enabled = context.dataStore[DiscordActivityButton2EnabledKey] ?: true
        val button1UrlSource = context.dataStore[DiscordActivityButton1UrlSourceKey] ?: "songurl"
        val button1CustomUrl = context.dataStore[DiscordActivityButton1CustomUrlKey] ?: ""
        val button2UrlSource = context.dataStore[DiscordActivityButton2UrlSourceKey] ?: "custom"
        val button2CustomUrl =
            context.dataStore[DiscordActivityButton2CustomUrlKey]
                ?: "https://github.com/ArchiveTuneApp/ArchiveTune"

        return buildList {
            if (button1Enabled) {
                val url = resolveUrl(button1UrlSource, song, button1CustomUrl)
                if (button1Label.isNotBlank() && !url.isNullOrBlank()) {
                    add(DiscordPresenceButton(button1Label.toButtonLabel(), url))
                }
            }

            if (button2Enabled) {
                val url = resolveUrl(button2UrlSource, song, button2CustomUrl)
                if (button2Label.isNotBlank() && !url.isNullOrBlank()) {
                    add(DiscordPresenceButton(button2Label.toButtonLabel(), url))
                }
            }
        }.take(2)
    }

    private fun resolveUrl(
        source: String,
        song: Song,
        custom: String,
    ): String? =
        when (source.lowercase()) {
            "songurl" -> {
                song.youtubeMusicUrl()
            }

            "artisturl" -> {
                song.discordArtistMusicUrl()
            }

            "albumurl" -> {
                song.discordAlbumMusicUrl()
            }

            "custom" -> {
                custom.normalizeUrl()
            }

            else -> {
                null
            }
        }?.toDiscordUrl()

    private fun buildTimestamps(
        song: Song,
        currentPlaybackTimeMillis: Long,
        isPaused: Boolean,
    ): DiscordPresenceTimestamps {
        if (isPaused) {
            val pausedStartMs = System.currentTimeMillis() - currentPlaybackTimeMillis.coerceAtLeast(0L)
            return DiscordPresenceTimestamps(
                startEpochSeconds = pausedStartMs / 1000L,
            )
        }

        val durationSeconds = song.song.duration.toLong()
        if (isPaused || durationSeconds <= 0L) {
            return DiscordPresenceTimestamps()
        }

        val startMs = System.currentTimeMillis() - currentPlaybackTimeMillis.coerceAtLeast(0L)
        return DiscordPresenceTimestamps(
            startEpochSeconds = startMs / 1000L,
            endEpochSeconds = (startMs + durationSeconds * 1000L) / 1000L,
        )
    }

    private fun String.normalizeUrl(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun String?.toDiscordText(
        maxLength: Int,
        fallback: String? = null,
    ): String? {
        val value = this?.trim().orEmpty().ifBlank { fallback?.trim().orEmpty() }
        return value
            .takeIf { it.length >= 2 }
            ?.take(maxLength)
    }

    private fun String?.toDiscordUrl(): String? = this?.normalizeUrl()?.take(256)

    private fun String.toButtonLabel(): String = trim().take(32).ifBlank { context.getString(R.string.app_name) }

    private fun Song.youtubeMusicUrl(): String? =
        song.id
            .takeUnless { song.isLocal || it.isLocalMediaId() }
            ?.let { "https://music.youtube.com/watch?v=$it" }

    private fun Song.discordArtistMusicUrl(): String? {
        if (song.isLocal || song.id.isLocalMediaId()) return null

        return artists.firstNotNullOfOrNull { artist ->
            (artist.channelId ?: artist.id)
                .takeUnless { it.isBlank() || it.isLocalArtistId() || it.isLocalMediaId() }
                ?.let { "https://music.youtube.com/channel/$it" }
        }
    }

    private fun String.isLocalArtistId(): Boolean =
        startsWith("LOCAL_ARTIST_") ||
            startsWith("LA") ||
            contains("privately_owned_artist", ignoreCase = true)
}
