/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class PreferenceValueType {
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    STRING,
    STRING_SET,
}

private val declaredPreferenceTypes = LinkedHashMap<String, PreferenceValueType>()

val DeclaredPreferenceTypes: Map<String, PreferenceValueType>
    get() = declaredPreferenceTypes

private fun <T> declarePreference(
    type: PreferenceValueType,
    key: Preferences.Key<T>,
): Preferences.Key<T> {
    val declaredType = declaredPreferenceTypes[key.name]
    check(declaredType == null || declaredType == type) {
        "Preference \"${key.name}\" is declared as both $declaredType and $type"
    }
    declaredPreferenceTypes[key.name] = type
    return key
}

private fun booleanKey(name: String) = declarePreference(PreferenceValueType.BOOLEAN, booleanPreferencesKey(name))

private fun intKey(name: String) = declarePreference(PreferenceValueType.INT, intPreferencesKey(name))

private fun longKey(name: String) = declarePreference(PreferenceValueType.LONG, longPreferencesKey(name))

private fun floatKey(name: String) = declarePreference(PreferenceValueType.FLOAT, floatPreferencesKey(name))

private fun stringKey(name: String) = declarePreference(PreferenceValueType.STRING, stringPreferencesKey(name))

private fun stringSetKey(name: String) = declarePreference(PreferenceValueType.STRING_SET, stringSetPreferencesKey(name))

val DynamicThemeKey = booleanKey("dynamicTheme")
val CustomThemeColorKey = stringKey("customThemeColor")
val RandomThemeOnStartupKey = booleanKey("randomThemeOnStartup")
val DarkModeKey = stringKey("darkMode")
val PureBlackKey = booleanKey("pureBlack")
val DisableAnimationsKey = booleanKey("disableAnimations")
val ForceHighRefreshRateKey = booleanKey("forceHighRefreshRate")
val WallpaperExtractionFailedKey = booleanKey("wallpaperExtractionFailed")
val EnableHapticFeedbackKey = booleanKey("enableHapticFeedback")
val UseSystemFontKey = booleanKey("useSystemFont")
val FontPreferenceKey = stringKey("fontPreference")
val CustomFontUriKey = stringKey("customFontUri")
val CustomFontNameKey = stringKey("customFontName")
val DefaultOpenTabKey = stringKey("defaultOpenTab")
val GridItemsSizeKey = stringKey("gridItemSize")
val SliderStyleKey = stringKey("sliderStyle")
val SwipeToSongKey = booleanKey("SwipeToSong")
val PlayerDesignStyleKey = stringKey("playerDesignStyle")
val ShowPlayerVolumeBarKey = booleanKey("showPlayerVolumeBar")
val HidePlayerThumbnailKey = booleanKey("hidePlayerThumbnail")
val ArchiveTuneCanvasKey = booleanKey("archiveTuneCanvas")
val CanvasSourceKey = stringKey("canvasSource")
val CanvasWifiOnlyKey = booleanKey("canvasWifiOnly")
val AndroidAutoOnlineRecommendationsKey = booleanKey("androidAutoOnlineRecommendations")
val AndroidAutoOnlineVoiceSearchKey = booleanKey("androidAutoOnlineVoiceSearch")
val AndroidAutoLocalSongsKey = booleanKey("androidAutoLocalSongs")
val AndroidAutoMeteredPlaybackKey = booleanKey("androidAutoMeteredPlayback")
val AndroidAutoMeteredArtworkKey = booleanKey("androidAutoMeteredArtwork")
val AndroidAutoPrimaryActionKey = stringKey("androidAutoPrimaryAction")
val AndroidAutoSecondaryActionKey = stringKey("androidAutoSecondaryAction")
val ThumbnailCornerRadiusKey = floatKey("thumbnailCornerRadius")
val CropThumbnailToSquareKey = booleanKey("cropThumbnailToSquare")

val AodModeEnabledKey = booleanKey("aodModeEnabled")
val AodThumbnailShapeKey = stringKey("aodThumbnailShape")
val AodThumbnailSizeKey = floatKey("aodThumbnailSize")
val AodThumbnailShapeRotationKey = intKey("aodThumbnailShapeRotation")
val AodShowThumbnailKey = booleanKey("aodShowThumbnail")
val AodShowArtistKey = booleanKey("aodShowArtist")
val AodShowAlbumKey = booleanKey("aodShowAlbum")
val AodShowProgressKey = booleanKey("aodShowProgress")
val AodShowTimeLabelsKey = booleanKey("aodShowTimeLabels")
val AodShowControlsKey = booleanKey("aodShowControls")
val AodShowExitButtonKey = booleanKey("aodShowExitButton")
val AodArtworkGlowKey = booleanKey("aodArtworkGlow")
val AodBackgroundStyleKey = stringKey("aodBackgroundStyle")
val AodAccentStyleKey = stringKey("aodAccentStyle")
val AodContentPositionKey = stringKey("aodContentPosition")
val AodTextAlignmentKey = stringKey("aodTextAlignment")
val AodControlStyleKey = stringKey("aodControlStyle")
val AodControlSizeKey = floatKey("aodControlSize")
val AodHorizontalPaddingKey = floatKey("aodHorizontalPadding")
val AodVerticalSpacingKey = floatKey("aodVerticalSpacing")
val AodTitleMaxLinesKey = intKey("aodTitleMaxLines")
val AodAmbientIntensityKey = floatKey("aodAmbientIntensity")
val AodTouchLockEnabledKey = booleanKey("aodTouchLockEnabled")
val AodUnlockMethodKey = stringKey("aodUnlockMethod")
val AodShowClockKey = booleanKey("aodShowClock")
val AodClockStyleKey = stringKey("aodClockStyle")
val AodShowBatteryKey = booleanKey("aodShowBattery")
val AodPixelShiftEnabledKey = booleanKey("aodPixelShiftEnabled")
val AodShowLyricTickerKey = booleanKey("aodShowLyricTicker")
val AodAutoDimmingKey = booleanKey("aodAutoDimming")
val AodAutoDimTimeoutKey = intKey("aodAutoDimTimeout")
val AodGesturesEnabledKey = booleanKey("aodGesturesEnabled")

val AodShakeToUnlockKey = booleanKey("aodShakeToUnlock")
val AodAutoLockEnabledKey = booleanKey("aodAutoLockEnabled")
val AodAutoLockTimeoutKey = intKey("aodAutoLockTimeout")
val AodMarqueeTitlesKey = booleanKey("aodMarqueeTitles")
val AodMinimalLockedStateKey = booleanKey("aodMinimalLockedState")
val AodBrightnessKey = floatKey("aodBrightness")
val AodProximityBlackoutKey = booleanKey("aodProximityBlackout")
val AodTrueAmbientModeKey = booleanKey("aodTrueAmbientMode")
val AodAutoStartScreenOffKey = booleanKey("aodAutoStartScreenOff")
val SeekExtraSeconds = booleanKey("seekExtraSeconds")
val DisableBlurKey = booleanKey("disableBlur")
val BlurRadiusKey = floatKey("blurRadius")

// Backdrop blur for detail pages
val BackdropEnabledKey = booleanKey("backdropEnabled")
val BackdropBlurAmountKey = intKey("backdropBlurAmount")
val MiniPlayerLastAnchorKey = intKey("miniPlayerLastAnchor")
val MiniPlayerBackgroundStyleKey = stringKey("miniPlayerBackgroundStyle")
val NavigationBarBackgroundStyleKey = stringKey("navigationBarBackgroundStyle")
val NavigationBarModeKey = stringKey("navigationBarMode")

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
    ADAPTIVE_ART,
    FROSTED_WALLPAPER,
    ADAPTIVE_FROSTED,
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

enum class AodUnlockMethod {
    SLIDE,
    HOLD,
}

enum class AodClockStyle {
    BOLD_DIGITAL,
    MINIMAL,
    ELEGANT_THIN,
    PIXEL_STACKED,
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

val AppLanguageKey = stringKey("appLanguage")
val UseSystemLanguageKey = booleanKey("useSystemLanguage")
val ContentLanguageKey = stringKey("contentLanguage")
val ContentCountryKey = stringKey("contentCountry")
val PlaylistSuggestionSourceKey = stringKey("playlistSuggestionSource")
val EnableKugouKey = booleanKey("enableKugou")
val EnableLrcLibKey = booleanKey("enableLrclib")
val EnableBetterLyricsKey = booleanKey("enableBetterLyrics")
val EnableBetterLyricsPortatoKey = booleanKey("enableBetterLyricsPortato")
val EnableYouLyPlusLyricsKey = booleanKey("enableYouLyPlusLyrics")
val EnableBinimumLyricsKey = booleanKey("enableBinimumLyrics")
val EnableLrcRedLyricsKey = booleanKey("enableLrcRedLyrics")
val EnableSimpMusicLyricsKey = booleanKey("enableSimpMusicLyrics")
val EnableMegalobizLyricsKey = booleanKey("enableMegalobizLyrics")
val EnablePaxsenixLyricsKey = booleanKey("enablePaxsenixLyrics")
val PaxsenixApiKeyKey = stringKey("paxsenixApiKey")
val EnablePaxsenixAppleMusicLyricsKey = booleanKey("enablePaxsenixAppleMusicLyrics")
val EnablePaxsenixSpotifyLyricsKey = booleanKey("enablePaxsenixSpotifyLyrics")
val EnablePaxsenixMusixmatchLyricsKey = booleanKey("enablePaxsenixMusixmatchLyrics")
val EnableUnisonLyricsKey = booleanKey("enableUnisonLyrics")
val EnableMusixmatchLyricsKey = booleanKey("enableMusixmatchLyrics")
val HideExplicitKey = booleanKey("hideExplicit")
val HideVideoKey = booleanKey("hideVideo")
val PlayVideoAsOfficialAudioKey = booleanKey("playVideoAsOfficialAudio")
val AiContentFilterEnabledKey = booleanKey("aiContentFilterEnabled")
val AiContentFilterIncludeModerateKey = booleanKey("aiContentFilterIncludeModerate")
val AiContentFilterLastUpdatedKey = longKey("aiContentFilterLastUpdated")
val ProxyEnabledKey = booleanKey("proxyEnabled")
val ProxyHostKey = stringKey("proxyHost")
val ProxyPortKey = intKey("proxyPort")
val ProxyUsernameKey = stringKey("proxyUsername")
val ProxyPasswordKey = stringKey("proxyPassword")
val ProxyTypeKey = stringKey("proxyType")
val EnableDnsOverHttpsKey = booleanKey("enableDnsOverHttps")
val DnsOverHttpsProviderKey = stringKey("dnsOverHttpsProvider")
val CustomDnsUrlKey = stringKey("customDnsUrl")
val StreamBypassProxyKey = booleanKey("streamBypassProxy")
val YtmSyncKey = booleanKey("ytmSync")
val ForceSyncOnAccountSwitchKey = booleanKey("forceSyncOnAccountSwitch")
val SelectedYtmPlaylistsKey = stringKey("ytm_selected_playlists")
val ImportSourcePriorityKey = booleanKey("import_local_first")
val LocalSongsMinDurationSecondsKey = intKey("local_songs_min_duration_seconds")
val LocalSongsIncludedFoldersKey = stringSetKey("local_songs_included_folders")
val LocalSongsExcludedFoldersKey = stringSetKey("local_songs_excluded_folders")
val LocalSongsSortTypeKey = stringKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = booleanKey("local_songs_sort_descending")
val LocalSongsAutoScanKey = booleanKey("local_songs_auto_scan")

val TogetherDisplayNameKey = stringKey("together_display_name")
val TogetherClientIdKey = stringKey("together_client_id")
val TogetherDefaultPortKey = intKey("together_default_port")
val TogetherAllowGuestsToAddTracksKey = booleanKey("together_allow_guests_add_tracks")
val TogetherAllowGuestsToControlPlaybackKey = booleanKey("together_allow_guests_control_playback")
val TogetherRequireHostApprovalToJoinKey = booleanKey("together_require_host_approval_to_join")
val TogetherLastJoinLinkKey = stringKey("together_last_join_link")
val TogetherWelcomeShownKey = booleanKey("together_welcome_shown")

// ListenBrainz scrobbling
val ListenBrainzEnabledKey = booleanKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringKey("listenbrainz_token")

val AiProviderKey = stringKey("ai_provider")
val AiCustomEndpointKey = stringKey("ai_custom_endpoint")
val AiApiKeyKey = stringKey("ai_api_key")
val AiApiValidationStatusKey = stringKey("ai_api_validation_status")
val AiSelectedModelKey = stringKey("ai_selected_model")
val AiCustomModelKey = stringKey("ai_custom_model")
val AiCustomPromptKey = stringKey("ai_custom_prompt")
val AiRequestParametersKey = stringKey("ai_request_parameters")

enum class AiProvider {
    CHATGPT,
    GEMINI,
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
val LastFMSessionKey = stringKey("lastfmSession")
val LastFMUsernameKey = stringKey("lastfmUsername")
val LastFMProviderKey = stringKey("lastfmProvider")
val LastFMCustomEndpointKey = stringKey("lastfmCustomEndpoint")
val LastFMApiKeyOverrideKey = stringKey("lastfmApiKeyOverride")
val LastFMSecretOverrideKey = stringKey("lastfmSecretOverride")
val EnableLastFMScrobblingKey = booleanKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanKey("lastfmUseNowPlaying")
val ScrobbleDelayPercentKey = floatKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intKey("scrobbleDelaySeconds")

enum class LastFmProvider {
    LASTFM,
    LIBREFM,
    CUSTOM,
}

val AudioQualityKey = stringKey("audioQuality")

val NetworkMeteredKey = booleanKey("networkMetered")
val LowDataModeKey = NetworkMeteredKey

enum class AudioQuality {
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    HI_RES_LOSSLESS,
    IOS,
    TVHTML5,
    ANDROID_MUSIC,
}

val PersistentQueueKey = booleanKey("persistentQueue")
val PreloadNextSongKey = booleanKey("preloadNextSong")
val PermanentShuffleKey = booleanKey("permanentShuffle")
val SkipSilenceKey = booleanKey("skipSilence")
val AudioNormalizationKey = booleanKey("audioNormalization")
val AudioOffload = booleanKey("audioOffload")
val UsbBitPerfectKey = booleanKey("usbBitPerfect")
val UsbBitPerfectDriverKey = stringKey("usbBitPerfectDriver")
val UsbBitPerfectFixedVolumeKey = booleanKey("usbBitPerfectFixedVolume")
val UsbDacVolumeKey = intKey("usbDacVolume")
const val DEFAULT_USB_DAC_VOLUME_PERCENT = 25

enum class UsbBitPerfectDriver {
    ANDROID,
    DIRECT,
}
val CrossfadeEnabledKey = booleanKey("crossfadeEnabled")
val CrossfadeDurationKey = floatKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanKey("crossfadeGapless")
val SleepTimerFinishSongKey = booleanKey("sleepTimerFinishSong")
val SleepTimerFadeOutKey = booleanKey("sleepTimerFadeOut")
val AutoLoadMoreKey = booleanKey("autoLoadMore")
val AutoDownloadOnLikeKey = booleanKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanKey("autoSkipNextOnError")
val PauseOnDeviceMuteKey = booleanKey("pauseOnDeviceMute")
val DeviceMutePlaybackRecoveryVolumeKey = intKey("deviceMutePlaybackRecoveryVolume")
val AutoStartOnBluetoothKey = booleanKey("autoStartOnBluetooth")
val StopMusicOnTaskClearKey = booleanKey("stopMusicOnTaskClear")
val MediaNotificationActionsKey = stringKey("mediaNotificationActions")
val MediaNotificationNavigationKey = stringKey("mediaNotificationNavigation")
val MediaNotificationSeekIncrementKey = intKey("mediaNotificationSeekIncrement")
val MediaNotificationSleepTimerMinutesKey = intKey("mediaNotificationSleepTimerMinutes")
val MediaNotificationStopOnDismissKey = booleanKey("mediaNotificationStopOnDismiss")
val WakelockKey = booleanKey("wakelock")
val ArtistSeparatorsKey = stringKey("artistSeparators")
val ExternalDownloaderEnabledKey = booleanKey("externalDownloaderEnabled")
val ExternalDownloaderPackageKey = stringKey("externalDownloaderPackage")
val PlaylistTagsFilterKey = stringKey("playlistTagsFilter")
val LibraryChipOrderKey = stringKey("libraryChipOrder")
val PlaylistTagOrderKey = stringKey("playlistTagOrder")
val ShowHomeCategoryChipsKey = booleanKey("showHomeCategoryChips")
val ShowTagsInLibraryKey = booleanKey("showTagsInLibrary")

val EqualizerEnabledKey = booleanKey("equalizerEnabled")
val EqualizerControlModeKey = stringKey("equalizerControlMode")
val EqualizerBandLevelsMbKey = stringKey("equalizerBandLevelsMb")
val EqualizerAutoHeadroomEnabledKey = booleanKey("equalizerAutoHeadroomEnabled")
val EqualizerOutputGainEnabledKey = booleanKey("equalizerOutputGainEnabled")
val EqualizerOutputGainMbKey = intKey("equalizerOutputGainMb")
val EqualizerBassBoostEnabledKey = booleanKey("equalizerBassBoostEnabled")
val EqualizerBassBoostStrengthKey = intKey("equalizerBassBoostStrength")
val EqualizerVirtualizerEnabledKey = booleanKey("equalizerVirtualizerEnabled")
val EqualizerVirtualizerStrengthKey = intKey("equalizerVirtualizerStrength")
val EqualizerSelectedProfileIdKey = stringKey("equalizerSelectedProfileId")
val EqualizerCustomProfilesJsonKey = stringKey("equalizerCustomProfilesJson")

val MaxImageCacheSizeKey = intKey("maxImageCacheSize")
val SmartTrimmerKey = booleanKey("smartTrimmer")
val MaxSongCacheSizeKey = intKey("maxSongCacheSize")
val MaxCanvasCacheSizeKey = intKey("maxCanvasCacheSize")
val StorageFolderIdKey = stringKey("storageFolderId")
val StorageFolderTreeUriKey = stringKey("storageFolderTreeUri")
val StorageFolderPathKey = stringKey("storageFolderPath")
val StorageFolderDisplayNameKey = stringKey("storageFolderDisplayName")

val PauseListenHistoryKey = booleanKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanKey("pauseSearchHistory")
val DisableScreenshotKey = booleanKey("disableScreenshot")

val DiscordTokenKey = stringKey("discordToken")
val DiscordRefreshTokenKey = stringKey("discordRefreshToken")
val DiscordTokenExpiresAtKey = longKey("discordTokenExpiresAt")
val DiscordInfoDismissedKey = booleanKey("discordInfoDismissed")
val DiscordUsernameKey = stringKey("discordUsername")
val DiscordNameKey = stringKey("discordName")
val DiscordAvatarUrlKey = stringKey("discordAvatarUrl")
val EnableDiscordRPCKey = booleanKey("discordRPCEnable")

// Discord activity customization keys
val DiscordActivityNameKey = stringKey("discordActivityName")
val DiscordActivityDetailsKey = stringKey("discordActivityDetails")
val DiscordActivityStateKey = stringKey("discordActivityState")

// Custom button labels and urls for Discord activity buttons
val DiscordActivityButton1LabelKey = stringKey("discordActivityButton1Label")
val DiscordActivityButton1UrlSourceKey = stringKey("discordActivityButton1UrlSource")
val DiscordActivityButton1CustomUrlKey = stringKey("discordActivityButton1CustomUrl")
val DiscordActivityButton2LabelKey = stringKey("discordActivityButton2Label")
val DiscordActivityButton2UrlSourceKey = stringKey("discordActivityButton2UrlSource")
val DiscordActivityButton2CustomUrlKey = stringKey("discordActivityButton2CustomUrl")
val DiscordActivityButton1EnabledKey = booleanKey("discordActivityButton1Enabled")
val DiscordActivityButton2EnabledKey = booleanKey("discordActivityButton2Enabled")
val DiscordShowWhenPausedKey = booleanKey("discordShowWhenPaused")

// Activity type for Discord presence (PLAYING, STREAMING, LISTENING, WATCHING, COMPETING)
val DiscordActivityTypeKey = stringKey("discordActivityType")
val DiscordPresenceStatusKey = stringKey("discordPresenceStatus") // "ONLINE", "IDLE", "DND", "INVISIBLE"

// Discord image selection keys
// Values for type keys: "thumbnail", "artist", "appicon", "custom"
val DiscordLargeImageTypeKey = stringKey("discordLargeImageType")
val DiscordLargeTextSourceKey = stringKey("discordLargeTextSource")
val DiscordLargeTextCustomKey = stringKey("discordLargeTextCustom")
val DiscordLargeImageCustomUrlKey = stringKey("discordLargeImageCustomUrl")
val DiscordSmallImageTypeKey = stringKey("discordSmallImageType")
val DiscordSmallImageCustomUrlKey = stringKey("discordSmallImageCustomUrl")


val TranslatorContextsKey = stringKey("translatorContexts")
val TranslatorTargetLangKey = stringKey("translatorTargetLang")
val EnableTranslatorKey = booleanKey("enableTranslator")

val ChipSortTypeKey = stringKey("chipSortType")
val SongSortTypeKey = stringKey("songSortType")
val SongSortDescendingKey = booleanKey("songSortDescending")
val PlaylistSongSortTypeKey = stringKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringKey("artistSortType")
val ArtistSortDescendingKey = booleanKey("artistSortDescending")
val AlbumSortTypeKey = stringKey("albumSortType")
val AlbumSortDescendingKey = booleanKey("albumSortDescending")
val PlaylistSortTypeKey = stringKey("playlistSortType")
val PlaylistSortDescendingKey = booleanKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanKey("artistSongSortDescending")
val MixSortTypeKey = stringKey("mixSortType")
val MixSortDescendingKey = booleanKey("albumSortDescending")

val SongFilterKey = stringKey("songFilter")
val ArtistFilterKey = stringKey("artistFilter")
val AlbumFilterKey = stringKey("albumFilter")

val LastLikeSongSyncKey = longKey("last_like_song_sync")
val LastLibSongSyncKey = longKey("last_library_song_sync")
val LastAlbumSyncKey = longKey("last_album_sync")
val LastArtistSyncKey = longKey("last_artist_sync")
val LastPlaylistSyncKey = longKey("last_playlist_sync")

val ArtistViewTypeKey = stringKey("artistViewType")
val AlbumViewTypeKey = stringKey("albumViewType")

val PlaylistEditLockKey = booleanKey("playlistEditLock")
val QuickPicksKey = stringKey("discover")

val NewsLastReadTimestampKey = longKey("news_last_read_timestamp")
val SpeedDialSongIdsKey = stringKey("speedDialSongIds")
val PreferredLyricsProviderKey = stringKey("lyricsProvider")
val LyricsProviderOrderKey = stringKey("lyricsProviderOrder")
val QueueEditLockKey = booleanKey("queueEditLock")

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

val QuickPicksDisplayModeKey = stringKey("quickPicksDisplayMode")

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
    BETTER_LYRICS_PORTATO,
    YOULY_PLUS,
    BINIMUM,
    LRC_RED,
    LRCLIB,
    KUGOU,
    MEGALOBIZ,
    SIMPMUSIC,
    UNISON,
    PAXSENIX_APPLE_MUSIC,
    PAXSENIX_SPOTIFY,
    PAXSENIX_MUSIXMATCH,
    MUSIXMATCH,
}

val DefaultLyricsProviderOrder =
    listOf(
        PreferredLyricsProvider.BETTER_LYRICS,
        PreferredLyricsProvider.BETTER_LYRICS_PORTATO,
        PreferredLyricsProvider.YOULY_PLUS,
        PreferredLyricsProvider.BINIMUM,
        PreferredLyricsProvider.LRC_RED,
        PreferredLyricsProvider.LRCLIB,
        PreferredLyricsProvider.KUGOU,
        PreferredLyricsProvider.MEGALOBIZ,
        PreferredLyricsProvider.SIMPMUSIC,
        PreferredLyricsProvider.UNISON,
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
        PreferredLyricsProvider.MUSIXMATCH,
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
    V10,
    V11,
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

enum class LyricsBackgroundStyle {
    DEFAULT,
    FOLLOW_THEME,
    COLORING,
    CUSTOM;

    fun resolveFor(playerBackgroundStyle: PlayerBackgroundStyle): LyricsBackgroundStyle =
        when {
            playerBackgroundStyle == PlayerBackgroundStyle.CUSTOM -> CUSTOM
            this == CUSTOM -> DEFAULT
            else -> this
        }
}

enum class MiniPlayerBackgroundStyle {
    THEME,
    GRADIENT,
    GLOW,
    ACRYLIC,
    BLUR,
    GLASS,
}

enum class NavigationBarBackgroundStyle {
    THEME,
    BLUR,
    ACRYLIC,
    GLASS,
}

enum class NavigationBarMode {
    CONNECTED,
    STANDALONE,
    APPLE_MUSIC,
}

// Keys for customized background
val PlayerCustomImageUriKey = stringKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatKey("playerCustomBlur")
val PlayerCustomContrastKey = floatKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatKey("playerCustomBrightness")

val LyricsAnimationStyleKey = stringKey("lyricsAnimationStyle")

enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
}

val LyricsTextSizeKey = floatKey("lyricsTextSize")
val LyricsLineSpacingKey = floatKey("lyricsLineSpacing")
val LyricsLineBlurKey = booleanKey("lyricsLineBlur")
val LyricsControlsVisibilityKey = stringKey("lyricsControlsVisibility")

enum class LyricsControlsVisibility {
    HIDE,
    AUTO,
    SHOW,
}

val TopSize = stringKey("topSize")

const val HISTORY_DURATION_DEFAULT = 30
const val HISTORY_DURATION_MIN = 5
const val HISTORY_DURATION_MAX = 60
val HISTORY_DURATION_RANGE = HISTORY_DURATION_MIN.toFloat()..HISTORY_DURATION_MAX.toFloat()
val HistoryDuration = intKey("historyDuration")

val PlayerButtonsStyleKey = stringKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringKey("playerBackgroundStyle")
val LyricsBackgroundStyleKey = stringKey("lyricsBackgroundStyle")
val ShowLyricsKey = booleanKey("showLyrics")
val LyricsTextPositionKey = stringKey("lyricsTextPosition")
val LyricsClickKey = booleanKey("lyricsClick")
val LyricsScrollKey = booleanKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanKey("lyricsRomanizeChinese")
val LyricsRomanizeHindiKey = booleanKey("lyricsRomanizeHindi")
val LyricsRomanizeOtherLanguagesKey = booleanKey("lyricsRomanizeOtherLanguages")
val TranslateLyricsKey = booleanKey("translateLyrics")
val UseLyricsV2Key = booleanKey("useLyricsV2")
val LyricsModeKey = stringKey("lyricsMode")

enum class LyricsMode {
    V2,
    ENHANCED,
}

val LyricsSyncOffsetModeKey = stringKey("lyricsSyncOffsetMode")
val LyricsGlobalSyncOffsetKey = intKey("lyricsGlobalSyncOffset")

const val LyricsSyncOffsetLimitMs = 5000

enum class LyricsSyncOffsetMode {
    GLOBAL,
    PER_SONG,
}

val DefaultLyricsSyncOffsetMode = LyricsSyncOffsetMode.PER_SONG

val LyricsV2BounceFactorKey = floatKey("lyricsV2BounceFactor")
val LyricsV2GlowFactorKey = floatKey("lyricsV2GlowFactor")
val LyricsV2FillTransitionWidthKey = floatKey("lyricsV2FillTransitionWidth")
val LyricsV2LrcBounceEnabledKey = booleanKey("lyricsV2LrcBounceEnabled")

val PlayerVolumeKey = floatKey("playerVolume")
val RepeatModeKey = intKey("repeatMode")
val SponsorBlockEnabledKey = booleanKey("sponsorBlockEnabled")
val SponsorBlockCategoriesKey = stringSetKey("sponsorBlockCategories")
val SponsorBlockApiUrlKey = stringKey("sponsorBlockApiUrl")

val SearchSourceKey = stringKey("searchSource")
val SwipeThumbnailKey = booleanKey("swipeThumbnail")
val SwipeSensitivityKey = floatKey("swipeSensitivity")

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

val VisitorDataKey = stringKey("visitorData")
val DataSyncIdKey = stringKey("dataSyncId")
val AccountSessionIndexKey = intKey("accountSessionIndex")
val InnerTubeCookieKey = stringKey("innerTubeCookie")
val PoTokenKey = stringKey("poToken")
val AccountNameKey = stringKey("accountName")
val AccountEmailKey = stringKey("accountEmail")
val AccountChannelHandleKey = stringKey("accountChannelHandle")
val SavedAccountsKey = stringKey("savedAccounts")
val UseLoginForBrowse = booleanKey("useLoginForBrowse")
val SpotifySpDcKey = stringKey("spotify_sp_dc")
val SpotifySpKeyKey = stringKey("spotify_sp_key")
val SpotifyAccessTokenKey = stringKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longKey("spotify_access_token_expires_at")
val SpotifyAccountNameKey = stringKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringKey("spotify_account_avatar_url")
val ShowSpotifyPlaylistsKey = booleanKey("show_spotify_playlists")
val SpotifyLibraryPlaylistsCacheKey = stringKey("spotify_library_playlists_cache")

val WebClientPoTokenEnabledKey = booleanKey("webClientPoTokenEnabled")
val PoTokenGvsKey = stringKey("poTokenGvs")
val PoTokenPlayerKey = stringKey("poTokenPlayer")
val UseVisitorDataKey = booleanKey("useVisitorData")
val PoTokenSourceUrlKey = stringKey("poTokenSourceUrl")

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
val LaunchCountKey = intKey("launch_count")
val OnboardingCompletedKey = booleanKey("onboarding_completed")
val HasPressedStarKey = booleanKey("has_pressed_star")
val RemindAfterKey = intKey("remind_after")

// Update settings
val AutomaticUpdateCheckKey = booleanKey("automaticUpdateCheck")
val EnableUpdateNotificationKey = booleanKey("enableUpdateNotification")
val LastUpdateCheckKey = longKey("lastUpdateCheck")
val LastNotifiedVersionKey = stringKey("lastNotifiedVersion")
val WhatsNewVersionKey = stringKey("whats_new_version")
val WhatsNewPendingKey = booleanKey("whats_new_pending")

val GitHubContributorsEtagKey = stringKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longKey("github_contributors_last_checked_at")
val GitHubTranslationContributorsJsonKey = stringKey("github_translation_contributors_json")
val GitHubTranslationContributorsLastCheckedAtKey = longKey("github_translation_contributors_last_checked_at")

val GitHubReleasesEtagKey = stringKey("github_releases_etag")
val GitHubReleasesJsonKey = stringKey("github_releases_json")
val GitHubReleasesLastCheckedAtKey = longKey("github_releases_last_checked_at")
val GitHubReleasesFingerprintKey = stringKey("github_releases_fingerprint")

val TogetherOnlineEndpointCacheKey = stringKey("together_online_endpoint_cache")
val TogetherOnlineEndpointLastCheckedAtKey = longKey("together_online_endpoint_last_checked_at")

val RedownloadOnRestoreKey = booleanKey("redownloadOnRestore")

val ScheduledBackupEnabledKey = booleanKey("scheduledBackupEnabled")
val ScheduledBackupFrequencyKey = stringKey("scheduledBackupFrequency")
val ScheduledBackupCustomDateEpochDayKey = longKey("scheduledBackupCustomDateEpochDay")
val ScheduledBackupDirectoryUriKey = stringKey("scheduledBackupDirectoryUri")
val ScheduledBackupDirectoryNameKey = stringKey("scheduledBackupDirectoryName")
val ScheduledBackupOverwriteExistingKey = booleanKey("scheduledBackupOverwriteExisting")

val MusicRecognitionHistoryJsonKey = stringKey("musicRecognitionHistoryJson")
val MusicRecognitionFloatingEnabledKey = booleanKey("musicRecognitionFloatingEnabled")

val ShowCodecOnPlayerKey = booleanKey("show_codec_on_player")
val DevShowDiscordDebugKey = booleanKey("dev_show_discord_debug")
val DevShowNerdStatsKey = booleanKey("dev_show_nerd_stats")
