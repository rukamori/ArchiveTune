# ArchiveTune Privacy Notice

Last updated: 2026-04-20

## Scope

This notice covers the Android ArchiveTune app in this repository. It explains what the app stores on your device, what it can send to external services when you use specific features, and what Android permissions it requests.

This notice is based on the current source code and build configuration. It does not replace the privacy terms of YouTube or YouTube Music, Last.fm, ListenBrainz, Discord, GitHub, lyrics providers, the ArchiveTune canvas service, or any Together server you choose to use.

## Privacy Summary

- Most core app data is stored locally on your device.
- ArchiveTune does not secretly harvest, sell, or broker your personal data.
- ArchiveTune does not silently send your data to unrelated third-party services.
- Optional network features send only the data needed to provide those features.
- If data leaves your device, it is because you used a specific online feature or integration that requires that transfer.
- Android backup and device-transfer features may copy part of the app's local data unless excluded by the app's backup rules.
- The app also includes a user-triggered backup export feature.
- The current Android build configuration does not show mobile advertising SDKs, third-party analytics SDKs, or automatic crash-reporting SDKs.
- The current Android manifest does not request location, contacts, camera, calendar, SMS, or call log permissions.

## Data the App May Store on Your Device

The app stores data locally to provide playback, library, search, lyrics, sync, and customization features.

| Category | Examples visible in the codebase | Why it is stored |
| --- | --- | --- |
| Library and playback data | Song, artist, album, playlist, like state, download state, total play time, audio format metadata | Library management, playback, downloads, and statistics |
| Search and lyrics data | Search queries and cached lyrics | Search history and lyrics features |
| Listening history data | Playback event records with song ID, timestamp, and play time | Listening stats and history-related features |
| App settings | Language, country, UI settings, audio settings, proxy settings, cache settings, history pause toggles, Together settings | Personalization and feature configuration |
| Optional account and session data | YouTube account name, email, channel handle, visitor data, data sync ID, cookie, PO token values | Signed-in YouTube and YouTube Music functionality |
| Optional third-party integration data | Last.fm session and username, ListenBrainz token, Discord OAuth access and refresh tokens plus related profile fields, Together display name, Together client ID, last join link | External integrations you choose to enable |
| Cached files | Streaming cache, download cache, and other app-managed files | Faster playback, offline use, and feature performance |

## Data the App May Send Off Your Device

ArchiveTune does not silently forward your data to unrelated services. It only contacts external services when you use online features, and the exact payload depends on the feature you use and how you configure it.

| Service or feature | Data that may be sent | When it happens |
| --- | --- | --- |
| YouTube or YouTube Music | Search terms, media playback requests, library or playlist requests, and signed-in session values such as visitor data, sync identifiers, cookies, or token values | When you browse, stream, sync, or sign in |
| Lyrics providers | Song title, artist name, album identifiers, or similar lookup data needed to fetch lyrics | When lyrics features are enabled or lyrics are requested |
| ArchiveTune canvas service | Song and artist names, album ID, or album URL, plus a bearer token if configured in the app build | When canvas or artwork lookup features are used |
| Last.fm | Now playing and scrobble metadata, plus your Last.fm session information | When Last.fm scrobbling is enabled |
| ListenBrainz | Playback history or scrobble metadata and your ListenBrainz token | When ListenBrainz sync is enabled |
| Discord Rich Presence | Current track, artist, album, images, configured URLs or labels for presence cards, and Discord OAuth tokens required by the official Social SDK | When Discord Rich Presence is enabled |
| GitHub releases | Update-check requests and cached release metadata used to show new versions | When the app checks for updates |
| Together | Display name, client ID, session code or keys, playback state, queue metadata, and room actions | When you host or join a Together session |

## Android Permissions

The app declares the following Android permissions in the current manifest.

| Permission | Why the app requests it |
| --- | --- |
| `INTERNET` | Connect to YouTube, lyrics services, update endpoints, canvas services, Together, and other network-backed features |
| `POST_NOTIFICATIONS` | Show playback and download notifications |
| `ACCESS_NETWORK_STATE` | Detect connectivity and adapt network behavior |
| `READ_MEDIA_AUDIO` | Read local audio files on supported Android versions |
| `READ_EXTERNAL_STORAGE` on Android 12 and below | Support local audio access on older Android versions |
| `RECORD_AUDIO` | Support music-recognition features |
| `BLUETOOTH_CONNECT` | Integrate with Bluetooth audio devices and playback controls |
| `RECEIVE_BOOT_COMPLETED` | Restore playback-related behavior after a device restart when supported by the app |
| `WAKE_LOCK` | Keep playback-related work running when needed |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC` | Support background playback, downloads, and related foreground work |

If you deny a permission, the related feature may stop working or provide reduced functionality.

## Backups, Device Transfer, and Local Retention

ArchiveTune currently enables Android backup support. The backup and data-transfer rules exclude some cache and download paths, including the ExoPlayer cache, the download directory, and `exoplayer_internal.db`. Other app data, including local database content and app preferences, may still be included in Android cloud backup or device transfer depending on your Android settings and device behavior.

The app also provides a manual backup feature that creates a ZIP archive containing app settings and database files. This is a user-triggered export action.

Unless you remove it, app data can remain on your device until one of the following happens:

- you clear app data,
- you uninstall the app,
- you remove or overwrite it through app actions, or
- Android backup or device-transfer systems copy it to another device or restore it.

For Together and other third-party services, remote retention depends on the service you use. This notice focuses on the Android app behavior in this repository.

## User Controls and Choices

You can control a significant amount of privacy-related behavior from the app and from Android itself.

- You can use many core features without enabling optional third-party integrations.
- You can choose whether to sign in to YouTube or YouTube Music.
- You can enable or disable Last.fm scrobbling, ListenBrainz sync, Discord Rich Presence, lyrics providers, and Together features.
- You can grant or deny Android runtime permissions such as media access, notifications, and microphone access.
- You can configure or disable proxy-related settings.
- The codebase includes settings to pause search history and listening history.
- You can create a local backup export.
- You can clear app data or uninstall the app to remove local app storage from your device.
- If you connected external services, you may also need to revoke access or rotate tokens with those external providers.

## Security Notes and Limitations

- The current Android manifest allows cleartext traffic. That means some connections may use HTTP instead of HTTPS if a feature or configured endpoint uses it.
- The current Android manifest also enables Android audio playback capture. Under Android platform rules, compatible system features or authorized apps may be able to capture app audio playback.
- This repository does not clearly document encryption at rest for the app database, preferences, or cache files, so this notice does not promise local encryption.
- This notice is limited to what can be supported from the current repository contents. Third-party services and self-hosted or official Together servers may have their own logging, retention, and security practices.
- If future code changes add new integrations, SDKs, or data flows, this notice should be updated with them.

## Changes to This Notice

This file should be reviewed whenever ArchiveTune changes its permissions, storage model, external integrations, backup behavior, or network architecture.

## Project Contact

For questions or corrections, use the project repository and issue tracker.

- Repository: [https://github.com/rukamori/ArchiveTune](https://github.com/rukamori/ArchiveTune)
- Issues: [https://github.com/rukamori/ArchiveTune/issues](https://github.com/rukamori/ArchiveTune/issues)

## Technical Appendix

This appendix maps the main statements above to concrete implementation surfaces in the codebase.

| Topic | What the code shows | Main files |
| --- | --- | --- |
| Permissions and backup behavior | The manifest declares network, media, microphone, Bluetooth, notification, boot, wake-lock, and foreground-service permissions. It also enables backup, cleartext traffic, and audio playback capture. Separate XML files exclude selected caches and internal playback database files from Android backup and device transfer. | `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml` |
| Local database contents | The Room schema includes songs, artists, albums, playlists, search history, lyrics, audio format metadata, and playback event records. | `app/schemas/moe.rukamori.archivetune.db.InternalDatabase/9.json` |
| Settings and tokens stored locally | DataStore preference keys include UI settings, proxy settings, history toggles, Together values, YouTube session values, account name or email fields, Last.fm session values, ListenBrainz token values, Discord values, and update-cache keys. | `app/src/main/kotlin/moe/archivetuneapp/archivetune/constants/PreferenceKeys.kt` |
| YouTube signed-in state | The Innertube layer exposes visitor data, data sync ID, cookie, PO token values, proxy state, and login-for-browse behavior as part of the current playback auth state. | `innertube/src/main/kotlin/moe/archivetuneapp/archivetune/innertube/YouTube.kt` |
| Manual backup export | The backup view model writes app settings plus database files into a ZIP archive chosen by the user. | `app/src/main/kotlin/moe/archivetuneapp/archivetune/viewmodels/BackupRestoreViewModel.kt` |
| External network integrations | Build configuration defines keys for Last.fm, Together, and canvas services. The updater fetches release information and caches related metadata in app preferences. | `app/build.gradle.kts`, `app/src/main/kotlin/moe/archivetuneapp/archivetune/utils/Updater.kt` |
| Canvas service requests | The canvas module sends song and artist names, album IDs, or album URLs to `https://artwork-archivetune.koiiverse.cloud/` and can attach a bearer token. | `canvas/src/main/kotlin/moe/archivetuneapp/archivetune/canvas/ArchiveTuneCanvas.kt` |
| Public feature claims | The repository README and store metadata describe privacy, YouTube integration, lyrics, music recognition, Last.fm, ListenBrainz, Discord Rich Presence, and other network-backed features that must stay aligned with this notice. | `README.md`, `fastlane/metadata/android/en-US/full_description.txt` |
| Current dependency posture | The current Android dependency declarations show Compose, Room, Hilt, Ktor, Media3, Coil, Timber, and related libraries. They do not currently show Firebase, Crashlytics, Sentry, mobile ad SDKs, or mobile analytics SDKs in the Android app dependency definitions reviewed for this notice. | `app/build.gradle.kts`, `gradle/libs.versions.toml` |

## Open Documentation Boundaries

The following areas should be documented carefully in the future if the project wants stronger privacy claims.

- Whether any self-hosted or official Together deployment logs IP addresses, user agents, or participant history outside the Android app itself.
- Whether all network endpoints used by optional features are always HTTPS in real deployments, since the Android manifest allows cleartext traffic.
- Whether local app storage is encrypted at rest on all supported devices and configurations.
- Whether canvas, lyrics, or future service providers apply their own independent retention or profiling practices.
