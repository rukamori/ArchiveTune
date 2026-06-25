/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val CustomThemeColorKey = stringPreferencesKey("customThemeColor")
val RandomThemeOnStartupKey = booleanPreferencesKey("randomThemeOnStartup")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val DisableAnimationsKey = booleanPreferencesKey("disableAnimations")
val EnableHapticFeedbackKey = booleanPreferencesKey("enableHapticFeedback")
val UseSystemFontKey = booleanPreferencesKey("useSystemFont")
val FontPreferenceKey = stringPreferencesKey("fontPreference")
val CustomFontUriKey = stringPreferencesKey("customFontUri")
val CustomFontNameKey = stringPreferencesKey("customFontName")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val PlayerDesignStyleKey = stringPreferencesKey("playerDesignStyle")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val ArchiveTuneCanvasKey = booleanPreferencesKey("archiveTuneCanvas")
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropThumbnailToSquareKey = booleanPreferencesKey("cropThumbnailToSquare")

val AodThumbnailShapeKey = stringPreferencesKey("aodThumbnailShape")
val AodThumbnailSizeKey = floatPreferencesKey("aodThumbnailSize")
val AodThumbnailShapeRotationKey = intPreferencesKey("aodThumbnailShapeRotation")
val AodShowThumbnailKey = booleanPreferencesKey("aodShowThumbnail")
val AodShowArtistKey = booleanPreferencesKey("aodShowArtist")
val AodShowAlbumKey = booleanPreferencesKey("aodShowAlbum")
val AodShowProgressKey = booleanPreferencesKey("aodShowProgress")
val AodShowTimeLabelsKey = booleanPreferencesKey("aodShowTimeLabels")
val AodShowControlsKey = booleanPreferencesKey("aodShowControls")
val AodShowExitButtonKey = booleanPreferencesKey("aodShowExitButton")
val AodArtworkGlowKey = booleanPreferencesKey("aodArtworkGlow")
val AodBackgroundStyleKey = stringPreferencesKey("aodBackgroundStyle")
val AodAccentStyleKey = stringPreferencesKey("aodAccentStyle")
val AodContentPositionKey = stringPreferencesKey("aodContentPosition")
val AodTextAlignmentKey = stringPreferencesKey("aodTextAlignment")
val AodControlStyleKey = stringPreferencesKey("aodControlStyle")
val AodControlSizeKey = floatPreferencesKey("aodControlSize")
val AodHorizontalPaddingKey = floatPreferencesKey("aodHorizontalPadding")
val AodVerticalSpacingKey = floatPreferencesKey("aodVerticalSpacing")
val AodTitleMaxLinesKey = intPreferencesKey("aodTitleMaxLines")
val AodAmbientIntensityKey = floatPreferencesKey("aodAmbientIntensity")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val DisableBlurKey = booleanPreferencesKey("disableBlur")
val BlurRadiusKey = floatPreferencesKey("blurRadius")

// Backdrop blur for detail pages
val BackdropEnabledKey = booleanPreferencesKey("backdropEnabled")
val BackdropBlurAmountKey = intPreferencesKey("backdropBlurAmount")
val MiniPlayerLastAnchorKey = intPreferencesKey("miniPlayerLastAnchor")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")

enum class AodThumbnailShape {
    ROUNDED,
    SQUARE,
    CIRCLE,
    PILL,
    ARCH,
    SLANTED,
    DIAMOND,
    PENTAGON,
    TRIANGLE,
    HEART,
    FLOWER,
    CLOVER_4,
    COOKIE_6,
    COOKIE_9,
    SUNNY,
    SOFT_BURST,
    GHOSTISH,
    PIXEL_CIRCLE,
}

enum class AodBackgroundStyle {
    PURE_BLACK,
    SOFT_RADIAL,
    TONAL_EDGE,
    AMBIENT_GLOW,
}

enum class AodAccentStyle {
    MONOCHROME,
    THEME,
}

enum class AodContentPosition {
    TOP,
    CENTER,
    BOTTOM,
}

enum class AodTextAlignment {
    START,
    CENTER,
    END,
}

enum class AodControlStyle {
    FILLED,
    TONAL,
    MINIMAL,
}

enum class SliderStyle {
    Standard,
    Wavy,
    Thick,
    Circular,
    Simple,
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"

enum class AppFontPreference {
    DEFAULT,
    SYSTEM,
    CUSTOM,
}

enum class PlaylistSuggestionSource {
    PLAYLIST_TITLE,
    PLAYLIST_CONTENT,
    BOTH,
}

val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val PlaylistSuggestionSourceKey = stringPreferencesKey("playlistSuggestionSource")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableSimpMusicLyricsKey = booleanPreferencesKey("enableSimpMusicLyrics")
val EnablePaxsenixLyricsKey = booleanPreferencesKey("enablePaxsenixLyrics")
val EnablePaxsenixAppleMusicLyricsKey = booleanPreferencesKey("enablePaxsenixAppleMusicLyrics")
val EnablePaxsenixNeteaseLyricsKey = booleanPreferencesKey("enablePaxsenixNeteaseLyrics")
val EnablePaxsenixSpotifyLyricsKey = booleanPreferencesKey("enablePaxsenixSpotifyLyrics")
val EnablePaxsenixMusixmatchLyricsKey = booleanPreferencesKey("enablePaxsenixMusixmatchLyrics")
val EnablePaxsenixYouTubeLyricsKey = booleanPreferencesKey("enablePaxsenixYouTubeLyrics")
val EnableUnisonLyricsKey = booleanPreferencesKey("enableUnisonLyrics")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoKey = booleanPreferencesKey("hideVideo")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyHostKey = stringPreferencesKey("proxyHost")
val ProxyPortKey = intPreferencesKey("proxyPort")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val EnableDnsOverHttpsKey = booleanPreferencesKey("enableDnsOverHttps")
val DnsOverHttpsProviderKey = stringPreferencesKey("dnsOverHttpsProvider")
val StreamBypassProxyKey = booleanPreferencesKey("streamBypassProxy")
val IpRotationEnabledKey = booleanPreferencesKey("ipRotationEnabled")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
val ForceSyncOnAccountSwitchKey = booleanPreferencesKey("forceSyncOnAccountSwitch")
val SelectedYtmPlaylistsKey = stringPreferencesKey("ytm_selected_playlists")
val LocalSongsMinDurationSecondsKey = intPreferencesKey("local_songs_min_duration_seconds")
val LocalSongsIncludedFoldersKey = stringSetPreferencesKey("local_songs_included_folders")
val LocalSongsExcludedFoldersKey = stringSetPreferencesKey("local_songs_excluded_folders")
val LocalSongsSortTypeKey = stringPreferencesKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = booleanPreferencesKey("local_songs_sort_descending")

val TogetherDisplayNameKey = stringPreferencesKey("together_display_name")
val TogetherClientIdKey = stringPreferencesKey("together_client_id")
val TogetherDefaultPortKey = intPreferencesKey("together_default_port")
val TogetherAllowGuestsToAddTracksKey = booleanPreferencesKey("together_allow_guests_add_tracks")
val TogetherAllowGuestsToControlPlaybackKey = booleanPreferencesKey("together_allow_guests_control_playback")
val TogetherRequireHostApprovalToJoinKey = booleanPreferencesKey("together_require_host_approval_to_join")
val TogetherLastJoinLinkKey = stringPreferencesKey("together_last_join_link")
val TogetherWelcomeShownKey = booleanPreferencesKey("together_welcome_shown")

// ListenBrainz scrobbling
val ListenBrainzEnabledKey = booleanPreferencesKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenbrainz_token")

val AiProviderKey = stringPreferencesKey("ai_provider")
val AiCustomEndpointKey = stringPreferencesKey("ai_custom_endpoint")
val AiApiKeyKey = stringPreferencesKey("ai_api_key")
val AiApiValidationStatusKey = stringPreferencesKey("ai_api_validation_status")
val AiSelectedModelKey = stringPreferencesKey("ai_selected_model")
val AiCustomModelKey = stringPreferencesKey("ai_custom_model")

enum class AiProvider {
    CHATGPT,
    GEMINI,
    CLAUDE,
    OPENROUTER,
    CUSTOM,
    NONE,
}

enum class AiApiValidationStatus {
    UNKNOWN,
    SUCCESS,
    FAILED,
}

// Last.fm scrobbling
val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val LastFMProviderKey = stringPreferencesKey("lastfmProvider")
val LastFMCustomEndpointKey = stringPreferencesKey("lastfmCustomEndpoint")
val LastFMApiKeyOverrideKey = stringPreferencesKey("lastfmApiKeyOverride")
val LastFMSecretOverrideKey = stringPreferencesKey("lastfmSecretOverride")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

enum class LastFmProvider {
    LASTFM,
    LIBREFM,
    CUSTOM,
}

val AudioQualityKey = stringPreferencesKey("audioQuality")

val NetworkMeteredKey = booleanPreferencesKey("networkMetered")
val LowDataModeKey = NetworkMeteredKey

enum class AudioQuality {
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

val PlayerStreamClientKey = stringPreferencesKey("playerStreamClient")

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    ARCHIVETUNE_EXTRACTOR,
    HI_RES_LOSSLESS,
    IOS,
    TVHTML5,
    ANDROID_MUSIC,
}

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PermanentShuffleKey = booleanPreferencesKey("permanentShuffle")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AudioOffload = booleanPreferencesKey("audioOffload")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val PauseOnDeviceMuteKey = booleanPreferencesKey("pauseOnDeviceMute")
val DeviceMutePlaybackRecoveryVolumeKey = intPreferencesKey("deviceMutePlaybackRecoveryVolume")
val AutoStartOnBluetoothKey = booleanPreferencesKey("autoStartOnBluetooth")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val WakelockKey = booleanPreferencesKey("wakelock")
val ArtistSeparatorsKey = stringPreferencesKey("artistSeparators")
val ExternalDownloaderEnabledKey = booleanPreferencesKey("externalDownloaderEnabled")
val ExternalDownloaderPackageKey = stringPreferencesKey("externalDownloaderPackage")
val PlaylistTagsFilterKey = stringPreferencesKey("playlistTagsFilter")
val ShowHomeCategoryChipsKey = booleanPreferencesKey("showHomeCategoryChips")
val ShowTagsInLibraryKey = booleanPreferencesKey("showTagsInLibrary")

val EqualizerEnabledKey = booleanPreferencesKey("equalizerEnabled")
val EqualizerBandLevelsMbKey = stringPreferencesKey("equalizerBandLevelsMb")
val EqualizerOutputGainEnabledKey = booleanPreferencesKey("equalizerOutputGainEnabled")
val EqualizerOutputGainMbKey = intPreferencesKey("equalizerOutputGainMb")
val EqualizerBassBoostEnabledKey = booleanPreferencesKey("equalizerBassBoostEnabled")
val EqualizerBassBoostStrengthKey = intPreferencesKey("equalizerBassBoostStrength")
val EqualizerVirtualizerEnabledKey = booleanPreferencesKey("equalizerVirtualizerEnabled")
val EqualizerVirtualizerStrengthKey = intPreferencesKey("equalizerVirtualizerStrength")
val EqualizerSelectedProfileIdKey = stringPreferencesKey("equalizerSelectedProfileId")
val EqualizerCustomProfilesJsonKey = stringPreferencesKey("equalizerCustomProfilesJson")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val SmartTrimmerKey = booleanPreferencesKey("smartTrimmer")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val MaxCanvasCacheSizeKey = intPreferencesKey("maxCanvasCacheSize")
val StorageFolderIdKey = stringPreferencesKey("storageFolderId")
val StorageFolderTreeUriKey = stringPreferencesKey("storageFolderTreeUri")
val StorageFolderPathKey = stringPreferencesKey("storageFolderPath")
val StorageFolderDisplayNameKey = stringPreferencesKey("storageFolderDisplayName")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

val DiscordTokenKey = stringPreferencesKey("discordToken")
val DiscordRefreshTokenKey = stringPreferencesKey("discordRefreshToken")
val DiscordTokenExpiresAtKey = longPreferencesKey("discordTokenExpiresAt")
val DiscordInfoDismissedKey = booleanPreferencesKey("discordInfoDismissed")
val DiscordUsernameKey = stringPreferencesKey("discordUsername")
val DiscordNameKey = stringPreferencesKey("discordName")
val DiscordAvatarUrlKey = stringPreferencesKey("discordAvatarUrl")
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")

// Discord activity customization keys
val DiscordActivityNameKey = stringPreferencesKey("discordActivityName")
val DiscordActivityDetailsKey = stringPreferencesKey("discordActivityDetails")
val DiscordActivityStateKey = stringPreferencesKey("discordActivityState")

// Custom button labels and urls for Discord activity buttons
val DiscordActivityButton1LabelKey = stringPreferencesKey("discordActivityButton1Label")
val DiscordActivityButton1UrlSourceKey = stringPreferencesKey("discordActivityButton1UrlSource")
val DiscordActivityButton1CustomUrlKey = stringPreferencesKey("discordActivityButton1CustomUrl")
val DiscordActivityButton2LabelKey = stringPreferencesKey("discordActivityButton2Label")
val DiscordActivityButton2UrlSourceKey = stringPreferencesKey("discordActivityButton2UrlSource")
val DiscordActivityButton2CustomUrlKey = stringPreferencesKey("discordActivityButton2CustomUrl")
val DiscordActivityButton1EnabledKey = booleanPreferencesKey("discordActivityButton1Enabled")
val DiscordActivityButton2EnabledKey = booleanPreferencesKey("discordActivityButton2Enabled")
val DiscordShowWhenPausedKey = booleanPreferencesKey("discordShowWhenPaused")

// Activity type for Discord presence (PLAYING, STREAMING, LISTENING, WATCHING, COMPETING)
val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordPresenceStatusKey = stringPreferencesKey("discordPresenceStatus") // "ONLINE", "IDLE", "DND", "INVISIBLE"

// Discord image selection keys
// Values for type keys: "thumbnail", "artist", "appicon", "custom"
val DiscordLargeImageTypeKey = stringPreferencesKey("discordLargeImageType")
val DiscordLargeTextSourceKey = stringPreferencesKey("discordLargeTextSource")
val DiscordLargeTextCustomKey = stringPreferencesKey("discordLargeTextCustom")
val DiscordLargeImageCustomUrlKey = stringPreferencesKey("discordLargeImageCustomUrl")
val DiscordSmallImageTypeKey = stringPreferencesKey("discordSmallImageType")
val DiscordSmallImageCustomUrlKey = stringPreferencesKey("discordSmallImageCustomUrl")

// Activity platform (discord client platform) selection
val DiscordActivityPlatformKey = stringPreferencesKey("discordActivityPlatform")

val TranslatorContextsKey = stringPreferencesKey("translatorContexts")
val TranslatorTargetLangKey = stringPreferencesKey("translatorTargetLang")
val EnableTranslatorKey = booleanPreferencesKey("enableTranslator")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastLikeSongSyncKey = longPreferencesKey("last_like_song_sync")
val LastLibSongSyncKey = longPreferencesKey("last_library_song_sync")
val LastAlbumSyncKey = longPreferencesKey("last_album_sync")
val LastArtistSyncKey = longPreferencesKey("last_artist_sync")
val LastPlaylistSyncKey = longPreferencesKey("last_playlist_sync")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")

val NewsLastReadTimestampKey = longPreferencesKey("news_last_read_timestamp")
val SpeedDialSongIdsKey = stringPreferencesKey("speedDialSongIds")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class QuickPicksDisplayMode {
    CARD,
    LIST,
}

val QuickPicksDisplayModeKey = stringPreferencesKey("quickPicksDisplayMode")

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
}

enum class ArtistFilter {
    LIBRARY,
    LIKED,
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    DOWNLOADED_FULL,
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
    CUSTOM,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY -> {
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            WEEK -> {
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH -> {
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            YEAR -> {
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            ALL_TIME -> {
                0
            }
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
    DONT_SHOW,
}

enum class PreferredLyricsProvider {
    BETTER_LYRICS,
    LRCLIB,
    KUGOU,
    SIMPMUSIC,
    UNISON,
    PAXSENIX_APPLE_MUSIC,
    PAXSENIX_NETEASE,
    PAXSENIX_SPOTIFY,
    PAXSENIX_MUSIXMATCH,
    PAXSENIX_YOUTUBE,
}

val DefaultLyricsProviderOrder =
    listOf(
        PreferredLyricsProvider.BETTER_LYRICS,
        PreferredLyricsProvider.LRCLIB,
        PreferredLyricsProvider.KUGOU,
        PreferredLyricsProvider.SIMPMUSIC,
        PreferredLyricsProvider.UNISON,
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC,
        PreferredLyricsProvider.PAXSENIX_NETEASE,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
        PreferredLyricsProvider.PAXSENIX_YOUTUBE,
    )

fun deserializeLyricsProviderOrder(orderStr: String?): List<PreferredLyricsProvider> {
    if (orderStr.isNullOrBlank()) return DefaultLyricsProviderOrder

    val parsed =
        orderStr
            .split(",")
            .mapNotNull { name ->
                PreferredLyricsProvider.entries.find { it.name == name.trim() }
            }.distinct()

    val normalized =
        when {
            parsed.take(3) ==
                listOf(
                    PreferredLyricsProvider.LRCLIB,
                    PreferredLyricsProvider.KUGOU,
                    PreferredLyricsProvider.BETTER_LYRICS,
                )
            -> listOf(PreferredLyricsProvider.BETTER_LYRICS) + parsed.filterNot { it == PreferredLyricsProvider.BETTER_LYRICS }

            else -> parsed
        }

    val missing = DefaultLyricsProviderOrder.filterNot { it in normalized }
    return normalized + missing
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY,
}

enum class PlayerDesignStyle {
    V1,
    V2,
    V3,
    V4,
    V5,
    V6,
    V7,
    V8,
    V9,
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
}

enum class MiniPlayerBackgroundStyle {
    THEME,
    GRADIENT,
    GLOW,
}

// Keys for customized background
val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")
val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")

val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")

enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
}

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")
val LyricsLineBlurKey = booleanPreferencesKey("lyricsLineBlur")

val TopSize = stringPreferencesKey("topSize")

const val HISTORY_DURATION_DEFAULT = 30
const val HISTORY_DURATION_MIN = 5
const val HISTORY_DURATION_MAX = 60
val HISTORY_DURATION_RANGE = HISTORY_DURATION_MIN.toFloat()..HISTORY_DURATION_MAX.toFloat()
val HISTORY_DURATION_LEGACY_FLOAT_KEY = floatPreferencesKey("historyDuration")
val HistoryDuration = intPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizeOtherLanguagesKey = booleanPreferencesKey("lyricsRomanizeOtherLanguages")
val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")
val UseLyricsV2Key = booleanPreferencesKey("useLyricsV2")
val LyricsModeKey = stringPreferencesKey("lyricsMode")

enum class LyricsMode {
    V2,
    ENHANCED,
}

val LyricsV2BounceFactorKey = floatPreferencesKey("lyricsV2BounceFactor")
val LyricsV2GlowFactorKey = floatPreferencesKey("lyricsV2GlowFactor")
val LyricsV2FillTransitionWidthKey = floatPreferencesKey("lyricsV2FillTransitionWidth")
val LyricsV2LrcBounceEnabledKey = booleanPreferencesKey("lyricsV2LrcBounceEnabled")

// Queue lyrics pre-load settings
val PreloadQueueLyricsEnabledKey = booleanPreferencesKey("preload_queue_lyrics_enabled")
val QueueLyricsPreloadCountKey = intPreferencesKey("queue_lyrics_preload_count")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val PoTokenKey = stringPreferencesKey("poToken")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val SavedAccountsKey = stringPreferencesKey("savedAccounts")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")
val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val ShowSpotifyPlaylistsKey = booleanPreferencesKey("show_spotify_playlists")
val SpotifyLibraryPlaylistsCacheKey = stringPreferencesKey("spotify_library_playlists_cache")

val WebClientPoTokenEnabledKey = booleanPreferencesKey("webClientPoTokenEnabled")
val PoTokenGvsKey = stringPreferencesKey("poTokenGvs")
val PoTokenPlayerKey = stringPreferencesKey("poTokenPlayer")
val UseVisitorDataKey = booleanPreferencesKey("useVisitorData")
val PoTokenSourceUrlKey = stringPreferencesKey("poTokenSourceUrl")

val LanguageCodeToName =
    mapOf(
        "en" to "English (US)",
        "en-GB" to "English (UK)",
        "en-NG" to "English (Nigeria)",
        "ja" to "日本語",
        "ko" to "한국어",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "uk" to "Українська",
        "cs" to "Čeština",
        "el" to "Ελληνικά",
        "he" to "עברית",
        "hu" to "Magyar",
        "ro" to "Română",
        "fi" to "Suomi",
        "da" to "Dansk",
        "no" to "Norsk",
        "sv" to "Svenska",
        "sk" to "Slovenčina",
        "bg" to "Български",
        "hr" to "Hrvatski",
        "sr" to "Срpsки",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "et" to "Eesti",
    )

val CountryCodeToName =
    mapOf(
        "JP" to "Japan",
        "KR" to "South Korea",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CN" to "China",
        "TW" to "Taiwan",
        "HK" to "Hong Kong",
        "FR" to "France",
        "DE" to "Germany",
        "ES" to "Spain",
        "MX" to "Mexico",
        "BR" to "Brazil",
        "RU" to "Russia",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "PL" to "Poland",
        "TR" to "Turkey",
        "AU" to "Australia",
        "CA" to "Canada",
        "IN" to "India",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "NG" to "Nigeria",
        "PH" to "Philippines",
        "MY" to "Malaysia",
        "SG" to "Singapore",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "PE" to "Peru",
        "ZA" to "South Africa",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
    )

// App rating / star prompt preferences
val LaunchCountKey = intPreferencesKey("launch_count")
val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
val HasPressedStarKey = booleanPreferencesKey("has_pressed_star")
val RemindAfterKey = intPreferencesKey("remind_after")

// Update settings
val EnableUpdateNotificationKey = booleanPreferencesKey("enableUpdateNotification")
val UpdateChannelKey = stringPreferencesKey("updateChannel")
val LastUpdateCheckKey = longPreferencesKey("lastUpdateCheck")
val LastNotifiedVersionKey = stringPreferencesKey("lastNotifiedVersion")

val GitHubContributorsEtagKey = stringPreferencesKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringPreferencesKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longPreferencesKey("github_contributors_last_checked_at")
val GitHubTranslationContributorsJsonKey = stringPreferencesKey("github_translation_contributors_json")
val GitHubTranslationContributorsLastCheckedAtKey = longPreferencesKey("github_translation_contributors_last_checked_at")

val GitHubReleasesEtagKey = stringPreferencesKey("github_releases_etag")
val GitHubReleasesJsonKey = stringPreferencesKey("github_releases_json")
val GitHubReleasesLastCheckedAtKey = longPreferencesKey("github_releases_last_checked_at")
val GitHubReleasesFingerprintKey = stringPreferencesKey("github_releases_fingerprint")

val DailyNightlyReleasesEtagKey = stringPreferencesKey("daily_nightly_releases_etag")
val DailyNightlyReleasesJsonKey = stringPreferencesKey("daily_nightly_releases_json")
val DailyNightlyReleasesLastCheckedAtKey = longPreferencesKey("daily_nightly_releases_last_checked_at")
val DailyNightlyReleasesFingerprintKey = stringPreferencesKey("daily_nightly_releases_fingerprint")

val TogetherOnlineEndpointCacheKey = stringPreferencesKey("together_online_endpoint_cache")
val TogetherOnlineEndpointLastCheckedAtKey = longPreferencesKey("together_online_endpoint_last_checked_at")

enum class UpdateChannel {
    STABLE,
    NIGHTLY,
    DAILY_NIGHTLY,
}
