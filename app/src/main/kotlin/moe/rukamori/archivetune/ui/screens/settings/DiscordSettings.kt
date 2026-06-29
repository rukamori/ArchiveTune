/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.discord.DiscordAuthCoordinator
import moe.rukamori.archivetune.discord.DiscordOAuthRepository
import moe.rukamori.archivetune.ui.component.EditTextPreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.extractThemeColor
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.ArtworkStorage
import moe.rukamori.archivetune.utils.discordAlbumMusicUrl
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import timber.log.Timber

enum class ActivitySource { ARTIST, ALBUM, SONG, APP }

private enum class DiscordAuthorizationUiMode { Idle, Waiting, Success, Failure }

private val DiscordImageOptions = listOf("thumbnail", "artist", "appicon", "custom")
private val DiscordSmallImageOptions = listOf("thumbnail", "artist", "appicon", "custom", "dontshow")
private val DiscordActivityStatusOptions = listOf("online", "dnd", "idle", "streaming")
private val DiscordPlatformOptions = listOf("desktop", "xbox", "samsung", "ios", "android", "embedded", "ps4", "ps5")
private val DiscordActivityTypeOptions = listOf("PLAYING", "STREAMING", "LISTENING", "WATCHING", "COMPETING")
private val DiscordLargeTextOptions = listOf("song", "artist", "album", "app", "custom", "dontshow")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val scrollBehavior = appBarScrollBehavior()
    val song by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var discordAvatarUrl by rememberPreference(DiscordAvatarUrlKey, "")
    var authorizedToken by rememberSaveable { mutableStateOf("") }
    var authorizedUsername by rememberSaveable { mutableStateOf("") }
    var authorizedName by rememberSaveable { mutableStateOf("") }
    var authorizedAvatarUrl by rememberSaveable { mutableStateOf("") }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var authorizationSession by remember {
        mutableStateOf(DiscordOAuthRepository.createAuthorizationSession())
    }
    var authorizationUiModeName by rememberSaveable {
        mutableStateOf(DiscordAuthorizationUiMode.Idle.name)
    }
    var authorizationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val authorizationUiMode =
        remember(authorizationUiModeName) {
            DiscordAuthorizationUiMode.valueOf(authorizationUiModeName)
        }

    val discordTokenExpiresAt by rememberPreference(DiscordTokenExpiresAtKey, 0L)

    LaunchedEffect(discordTokenExpiresAt) {
        currentTimeMillis = System.currentTimeMillis()
        if (discordTokenExpiresAt > currentTimeMillis) {
            delay((discordTokenExpiresAt - currentTimeMillis).coerceAtLeast(1_000L))
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(discordToken) {
        val token = discordToken
        if (token.isBlank()) {
            authorizedToken = ""
            authorizedUsername = ""
            authorizedName = ""
            authorizedAvatarUrl = ""
            return@LaunchedEffect
        }

        if (token == authorizedToken) {
            authorizedToken = ""
        }

        if (token.isNotBlank()) {
            runCatching {
                DiscordOAuthRepository.fetchAccount(token)
            }.onSuccess {
                discordUsername = it.username
                discordName = it.displayName
                discordAvatarUrl = it.avatarUrl.orEmpty()
            }.onFailure {
                Timber.tag("DiscordSettings").w(it, "Discord account lookup failed")
            }
        }
    }

    val (discordRPC, onDiscordRPCChange) =
        rememberPreference(
            key = EnableDiscordRPCKey,
            defaultValue = true,
        )

    LaunchedEffect(discordToken, discordRPC) {
        if (discordRPC && discordToken.isNotBlank()) {
            Timber.tag("DiscordSettings").d("Discord Rich Presence enabled, MusicService will handle start")
        } else {
            Timber.tag("DiscordSettings").d("Discord Rich Presence disabled or not authorized, MusicService will handle stop")
        }
    }

    val activeDiscordToken = authorizedToken.ifBlank { discordToken }
    val activeDiscordUsername = authorizedUsername.ifBlank { discordUsername }
    val activeDiscordName = authorizedName.ifBlank { discordName }
    val activeDiscordAvatarUrl = authorizedAvatarUrl.ifBlank { discordAvatarUrl }
    val isLoggedIn = remember(activeDiscordToken) { activeDiscordToken.isNotBlank() }
    val isAccessTokenExpired =
        remember(isLoggedIn, discordTokenExpiresAt, currentTimeMillis) {
            isLoggedIn && discordTokenExpiresAt > 0L && currentTimeMillis >= discordTokenExpiresAt
        }
    val accountDisplayName =
        remember(isLoggedIn, activeDiscordName, activeDiscordUsername, context) {
            when {
                activeDiscordName.isNotBlank() -> activeDiscordName
                activeDiscordUsername.isNotBlank() -> activeDiscordUsername
                isLoggedIn -> context.getString(R.string.account)
                else -> context.getString(R.string.not_logged_in)
            }
        }

    val launchAuthorization: () -> Unit = {
        val session = DiscordOAuthRepository.createAuthorizationSession()
        authorizationSession = session
        authorizationMessage = null
        authorizationUiModeName = DiscordAuthorizationUiMode.Waiting.name

        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, session.authorizationUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                },
            )
        }.onFailure {
            authorizationUiModeName = DiscordAuthorizationUiMode.Failure.name
            authorizationMessage = it.message ?: context.getString(R.string.discord_authorization_failed)
        }
    }

    LaunchedEffect(authorizationSession.state, authorizationUiMode) {
        if (authorizationUiMode != DiscordAuthorizationUiMode.Waiting) {
            return@LaunchedEffect
        }

        DiscordAuthCoordinator.redirects.collectLatest { redirect ->
            if (redirect.getQueryParameter("state") != authorizationSession.state) {
                return@collectLatest
            }

            DiscordOAuthRepository
                .completeAuthorization(
                    context = context,
                    session = authorizationSession,
                    redirect = redirect,
                ).onSuccess { session ->
                    val account =
                        session.account
                            ?: runCatching { DiscordOAuthRepository.fetchAccount(session.accessToken) }.getOrNull()

                    authorizedToken = session.accessToken
                    authorizedUsername = account?.username.orEmpty()
                    authorizedName = account?.displayName.orEmpty()
                    authorizedAvatarUrl = account?.avatarUrl.orEmpty()
                    discordUsername = authorizedUsername
                    discordName = authorizedName
                    discordAvatarUrl = authorizedAvatarUrl
                    authorizationMessage = context.getString(R.string.discord_authorization_success)
                    authorizationUiModeName = DiscordAuthorizationUiMode.Success.name
                    authorizationSession = DiscordOAuthRepository.createAuthorizationSession()
                }.onFailure {
                    authorizationMessage = it.message ?: context.getString(R.string.discord_authorization_failed)
                    authorizationUiModeName = DiscordAuthorizationUiMode.Failure.name
                    authorizationSession = DiscordOAuthRepository.createAuthorizationSession()
                }
        }
    }

    LaunchedEffect(authorizationUiMode) {
        if (authorizationUiMode == DiscordAuthorizationUiMode.Success ||
            authorizationUiMode == DiscordAuthorizationUiMode.Failure
        ) {
            delay(2600)
            if (authorizationUiModeName == authorizationUiMode.name) {
                authorizationUiModeName = DiscordAuthorizationUiMode.Idle.name
                authorizationMessage = null
            }
        }
    }

    BackHandler(enabled = authorizationUiMode == DiscordAuthorizationUiMode.Waiting) {
        authorizationUiModeName = DiscordAuthorizationUiMode.Idle.name
        authorizationMessage = null
        authorizationSession = DiscordOAuthRepository.createAuthorizationSession()
    }

    val (largeImageType, onLargeImageTypeChange) =
        rememberPreference(
            key = DiscordLargeImageTypeKey,
            defaultValue = "thumbnail",
        )
    val (largeImageCustomUrl, onLargeImageCustomUrlChange) =
        rememberPreference(
            key = DiscordLargeImageCustomUrlKey,
            defaultValue = "",
        )
    val (smallImageType, onSmallImageTypeChange) =
        rememberPreference(
            key = DiscordSmallImageTypeKey,
            defaultValue = "artist",
        )
    val (smallImageCustomUrl, onSmallImageCustomUrlChange) =
        rememberPreference(
            key = DiscordSmallImageCustomUrlKey,
            defaultValue = "",
        )
    var isRefreshing by remember { mutableStateOf(false) }

    val (activityStatusSelection, onActivityStatusSelectionChange) =
        rememberPreference(
            key = DiscordPresenceStatusKey,
            defaultValue = "online",
        )

    val (platformSelection, onPlatformSelectionChange) =
        rememberPreference(
            key = DiscordActivityPlatformKey,
            defaultValue = "android",
        )

    val (nameSource, onNameSourceChange) =
        rememberEnumPreference(
            key = DiscordActivityNameKey,
            defaultValue = ActivitySource.APP,
        )
    val (detailsSource, onDetailsSourceChange) =
        rememberEnumPreference(
            key = DiscordActivityDetailsKey,
            defaultValue = ActivitySource.SONG,
        )
    val (stateSource, onStateSourceChange) =
        rememberEnumPreference(
            key = DiscordActivityStateKey,
            defaultValue = ActivitySource.ARTIST,
        )

    val (button1Label) =
        rememberPreference(
            key = DiscordActivityButton1LabelKey,
            defaultValue = "Listen on YouTube Music",
        )
    val (button1Enabled) =
        rememberPreference(
            key = DiscordActivityButton1EnabledKey,
            defaultValue = true,
        )
    val (button2Label) =
        rememberPreference(
            key = DiscordActivityButton2LabelKey,
            defaultValue = "Go to ArchiveTune",
        )
    val (button2Enabled) =
        rememberPreference(
            key = DiscordActivityButton2EnabledKey,
            defaultValue = true,
        )
    val (button1UrlSource) =
        rememberPreference(
            key = DiscordActivityButton1UrlSourceKey,
            defaultValue = "songurl",
        )
    val (button1CustomUrl) =
        rememberPreference(
            key = DiscordActivityButton1CustomUrlKey,
            defaultValue = "",
        )
    val (button2UrlSource) =
        rememberPreference(
            key = DiscordActivityButton2UrlSourceKey,
            defaultValue = "custom",
        )
    val (button2CustomUrl) =
        rememberPreference(
            key = DiscordActivityButton2CustomUrlKey,
            defaultValue = "https://github.com/ArchiveTuneApp/ArchiveTune",
        )

    val (activityType, onActivityTypeChange) =
        rememberPreference(
            key = DiscordActivityTypeKey,
            defaultValue = "LISTENING",
        )
    var showWhenPaused by rememberPreference(
        key = DiscordShowWhenPausedKey,
        defaultValue = false,
    )

    val (largeTextSource, onLargeTextSourceChange) =
        rememberPreference(
            key = DiscordLargeTextSourceKey,
            defaultValue = "album",
        )
    val (largeTextCustom, onLargeTextCustomChange) =
        rememberPreference(
            key = DiscordLargeTextCustomKey,
            defaultValue = "",
        )

    LaunchedEffect(largeImageType, smallImageType) {
        ArtworkStorage.removeBySongId(context, song?.song?.id ?: return@LaunchedEffect)
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.discord_integration),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    var threeDotMenuExpanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { threeDotMenuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }

                    DropdownMenu(
                        expanded = threeDotMenuExpanded,
                        onDismissRequest = { threeDotMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.experiment_settings)) },
                            onClick = {
                                threeDotMenuExpanded = false
                                navController.navigate("settings/discord/experimental")
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.experiment),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PreferenceGroup(title = stringResource(R.string.account)) {
                    item {
                        DiscordAccountGroupCard(
                            displayName = accountDisplayName,
                            username = activeDiscordUsername,
                            avatarUrl = activeDiscordAvatarUrl.takeIf { it.isNotBlank() },
                            isLoggedIn = isLoggedIn,
                            authorizationUiMode = authorizationUiMode,
                            authorizationMessage = authorizationMessage,
                            isAccessTokenExpired = isAccessTokenExpired,
                            discordRpcEnabled = discordRPC,
                            onDiscordRpcEnabledChange = onDiscordRPCChange,
                            onReauthorize = launchAuthorization,
                            onPrimaryAction = {
                                if (isLoggedIn) {
                                    showLogoutConfirm = true
                                } else {
                                    launchAuthorization()
                                }
                            },
                            primaryActionEnabled = authorizationUiMode != DiscordAuthorizationUiMode.Waiting,
                        )
                    }
                }
            }

            item {
                PreferenceGroup(title = stringResource(R.string.options)) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.refresh)) },
                            description = stringResource(R.string.description_refresh),
                            icon = { Icon(painterResource(R.drawable.update), null) },
                            isEnabled = discordRPC && isLoggedIn,
                            trailingContent = {
                                if (isRefreshing) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                    )
                                } else {
                                    OutlinedButton(
                                        enabled = discordRPC && isLoggedIn,
                                        onClick = {
                                            coroutineScope.launch {
                                                isRefreshing = true
                                                val success =
                                                    playerConnection.service.refreshDiscordNow()
                                                isRefreshing = false
                                                snackbarHostState.showSnackbar(
                                                    message =
                                                        if (success) {
                                                            context.getString(R.string.discord_refresh_success)
                                                        } else {
                                                            context.getString(R.string.discord_refresh_failed)
                                                        },
                                                )
                                            }
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(stringResource(R.string.refresh))
                                    }
                                }
                            },
                        )
                    }
                }
            }

            item {
                PreferenceGroup(title = stringResource(R.string.discord_connection_settings)) {
                    item {
                        ListPreference(
                            title = { Text(stringResource(R.string.activity_status)) },
                            icon = { Icon(painterResource(R.drawable.status), null) },
                            selectedValue = activityStatusSelection,
                            values = DiscordActivityStatusOptions,
                            valueText = { discordPresenceStatusLabel(it) },
                            onValueSelected = onActivityStatusSelectionChange,
                        )
                    }

                    item {
                        ListPreference(
                            title = { Text(stringResource(R.string.platform_status)) },
                            icon = { Icon(painterResource(R.drawable.desktop_windows), null) },
                            selectedValue = platformSelection,
                            values = DiscordPlatformOptions,
                            valueText = { discordPlatformLabel(it) },
                            onValueSelected = onPlatformSelectionChange,
                        )
                    }
                }
            }

            item {
                PreferenceGroup(title = stringResource(R.string.discord_activity_content)) {
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.discord_activity_name)) },
                            selectedValue = nameSource,
                            onValueSelected = onNameSourceChange,
                            valueText = { activitySourceLabel(it) },
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        )
                    }
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.discord_activity_details)) },
                            selectedValue = detailsSource,
                            onValueSelected = onDetailsSourceChange,
                            valueText = { activitySourceLabel(it) },
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        )
                    }
                    item {
                        EnumListPreference(
                            title = { Text(stringResource(R.string.discord_activity_state)) },
                            selectedValue = stateSource,
                            onValueSelected = onStateSourceChange,
                            valueText = { activitySourceLabel(it) },
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.discord_show_when_paused)) },
                            description = stringResource(R.string.discord_show_when_paused_desc),
                            icon = { Icon(painterResource(R.drawable.ic_pause_white), null) },
                            checked = showWhenPaused,
                            onCheckedChange = { showWhenPaused = it },
                        )
                    }

                    item {
                        ListPreference(
                            title = { Text(stringResource(R.string.discord_activity_type)) },
                            icon = { Icon(painterResource(R.drawable.discord), null) },
                            selectedValue = activityType,
                            values = DiscordActivityTypeOptions,
                            valueText = { discordActivityTypeLabel(it) },
                            onValueSelected = onActivityTypeChange,
                        )
                    }
                }
            }

            item {
                PreferenceGroup(title = stringResource(R.string.discord_image_options)) {
                    item {
                        ListPreference(
                            title = { Text(stringResource(R.string.large_image)) },
                            icon = { Icon(painterResource(R.drawable.image), null) },
                            selectedValue = largeImageType,
                            values = DiscordImageOptions,
                            valueText = { discordImageTypeLabel(it) },
                            onValueSelected = onLargeImageTypeChange,
                        )
                    }

                    item(visible = largeImageType == "custom") {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.large_image_custom_url)) },
                            icon = { Icon(painterResource(R.drawable.link), null) },
                            value = largeImageCustomUrl,
                            onValueChange = onLargeImageCustomUrlChange,
                            isInputValid = { true },
                        )
                    }

                    item {
                        ListPreference(
                            title = { Text(stringResource(R.string.large_text)) },
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                            selectedValue = largeTextSource,
                            values = DiscordLargeTextOptions,
                            valueText = { discordLargeTextSourceLabel(it) },
                            onValueSelected = onLargeTextSourceChange,
                        )
                    }

                    item(visible = largeTextSource == "custom") {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.custom_large_text)) },
                            icon = { Icon(painterResource(R.drawable.text_fields), null) },
                            value = largeTextCustom,
                            onValueChange = onLargeTextCustomChange,
                            isInputValid = { true },
                        )
                    }

                    item {
                        ListPreference(
                            title = { Text(stringResource(R.string.small_image)) },
                            icon = { Icon(painterResource(R.drawable.image), null) },
                            selectedValue = smallImageType,
                            values = DiscordSmallImageOptions,
                            valueText = { discordImageTypeLabel(it) },
                            onValueSelected = onSmallImageTypeChange,
                        )
                    }

                    item(visible = smallImageType == "custom") {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.small_image_custom_url)) },
                            icon = { Icon(painterResource(R.drawable.link), null) },
                            value = smallImageCustomUrl,
                            onValueChange = onSmallImageCustomUrlChange,
                            isInputValid = { true },
                        )
                    }
                }
            }

            item {
                RichPresence(
                    song = song,
                    currentPlaybackTimeMillis = playerConnection.player.currentPosition,
                    nameSource = nameSource,
                    detailsSource = detailsSource,
                    stateSource = stateSource,
                    activityType = activityType,
                    largeImageType = largeImageType,
                    largeImageCustomUrl = largeImageCustomUrl,
                    largeTextSource = largeTextSource,
                    largeTextCustom = largeTextCustom,
                    smallImageType = smallImageType,
                    smallImageCustomUrl = smallImageCustomUrl,
                    button1Label = button1Label,
                    button1Enabled = button1Enabled,
                    button1UrlSource = button1UrlSource,
                    button1CustomUrl = button1CustomUrl,
                    button2Label = button2Label,
                    button2Enabled = button2Enabled,
                    button2UrlSource = button2UrlSource,
                    button2CustomUrl = button2CustomUrl,
                    isPlaying = playerConnection.player.isPlaying,
                )
            }
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text(stringResource(R.string.logout_confirm_title)) },
                text = { Text(stringResource(R.string.logout_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                DiscordOAuthRepository.clearSession(context)
                            }
                            authorizedToken = ""
                            authorizedUsername = ""
                            authorizedName = ""
                            authorizedAvatarUrl = ""
                            authorizationUiModeName = DiscordAuthorizationUiMode.Idle.name
                            authorizationMessage = null
                            authorizationSession = DiscordOAuthRepository.createAuthorizationSession()
                            showLogoutConfirm = false
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.logout_confirm_yes))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutConfirm = false },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.logout_confirm_no))
                    }
                },
            )
        }
    }
}

@Composable
private fun DiscordAccountGroupCard(
    displayName: String,
    username: String,
    avatarUrl: String?,
    isLoggedIn: Boolean,
    authorizationUiMode: DiscordAuthorizationUiMode,
    authorizationMessage: String?,
    isAccessTokenExpired: Boolean,
    discordRpcEnabled: Boolean,
    onDiscordRpcEnabledChange: (Boolean) -> Unit,
    onReauthorize: () -> Unit,
    onPrimaryAction: () -> Unit,
    primaryActionEnabled: Boolean,
) {
    val context = LocalContext.current
    var extractedGlowColor by remember(avatarUrl, isLoggedIn) { mutableStateOf(Color.Transparent) }
    val avatarGlowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(durationMillis = 420),
        label = "discordAvatarGlow",
    )
    val avatarImageRequest =
        remember(context, avatarUrl) {
            avatarUrl?.takeIf { it.isNotBlank() }?.let {
                ImageRequest
                    .Builder(context)
                    .data(it)
                    .size(256, 256)
                    .build()
            }
        }

    LaunchedEffect(avatarUrl, isLoggedIn) {
        if (!isLoggedIn || avatarUrl.isNullOrBlank()) {
            extractedGlowColor = Color.Transparent
            return@LaunchedEffect
        }

        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(avatarUrl)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull()

        extractedGlowColor =
            if (bitmap != null) {
                withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
            } else {
                Color.Transparent
            }
    }

    val sessionSummary =
        when (authorizationUiMode) {
            DiscordAuthorizationUiMode.Waiting -> {
                stringResource(R.string.discord_waiting_for_authorization)
            }

            DiscordAuthorizationUiMode.Success -> {
                authorizationMessage ?: stringResource(R.string.discord_authorization_success)
            }

            DiscordAuthorizationUiMode.Failure -> {
                authorizationMessage ?: stringResource(R.string.discord_authorization_failed)
            }

            DiscordAuthorizationUiMode.Idle -> {
                if (isLoggedIn) {
                    stringResource(R.string.discord_account_ready)
                } else {
                    stringResource(R.string.discord_login_description)
                }
            }
        }

    val sessionContainerColor =
        when (authorizationUiMode) {
            DiscordAuthorizationUiMode.Waiting -> MaterialTheme.colorScheme.secondaryContainer
            DiscordAuthorizationUiMode.Success -> MaterialTheme.colorScheme.primaryContainer
            DiscordAuthorizationUiMode.Failure -> MaterialTheme.colorScheme.errorContainer
            DiscordAuthorizationUiMode.Idle -> MaterialTheme.colorScheme.surfaceContainerHighest
        }

    val sessionContentColor =
        when (authorizationUiMode) {
            DiscordAuthorizationUiMode.Waiting -> MaterialTheme.colorScheme.onSecondaryContainer
            DiscordAuthorizationUiMode.Success -> MaterialTheme.colorScheme.onPrimaryContainer
            DiscordAuthorizationUiMode.Failure -> MaterialTheme.colorScheme.onErrorContainer
            DiscordAuthorizationUiMode.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(88.dp)
                            .shadow(
                                elevation = 30.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = avatarGlowColor.copy(alpha = 0.56f),
                                spotColor = avatarGlowColor.copy(alpha = 0.74f),
                            ),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.discord),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )

                            avatarImageRequest?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = displayName,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (username.isNotBlank()) {
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible =
                    authorizationUiMode != DiscordAuthorizationUiMode.Idle ||
                        (isLoggedIn && !isAccessTokenExpired),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = sessionContainerColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (authorizationUiMode == DiscordAuthorizationUiMode.Waiting) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = sessionContentColor,
                            )
                        } else {
                            Icon(
                                painter =
                                    painterResource(
                                        when (authorizationUiMode) {
                                            DiscordAuthorizationUiMode.Success -> R.drawable.check
                                            DiscordAuthorizationUiMode.Failure -> R.drawable.close
                                            DiscordAuthorizationUiMode.Idle -> R.drawable.discord
                                            DiscordAuthorizationUiMode.Waiting -> R.drawable.discord
                                        },
                                    ),
                                contentDescription = null,
                                tint = sessionContentColor,
                            )
                        }

                        Text(
                            text = sessionSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = sessionContentColor,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color =
                    if (discordRpcEnabled && isLoggedIn) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.status),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enable_discord_rpc),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Switch(
                        checked = discordRpcEnabled,
                        onCheckedChange = onDiscordRpcEnabledChange,
                        enabled = isLoggedIn,
                    )
                }
            }

            if (isLoggedIn) {
                OutlinedButton(
                    onClick = onPrimaryAction,
                    enabled = primaryActionEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_logout))
                }
            } else {
                Button(
                    onClick = onPrimaryAction,
                    enabled = primaryActionEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.discord_open_authorization))
                }
            }

            AnimatedVisibility(
                visible = isAccessTokenExpired && authorizationUiMode == DiscordAuthorizationUiMode.Idle,
            ) {
                DiscordReauthorizeWarningRow(
                    onReauthorize = onReauthorize,
                    enabled = primaryActionEnabled,
                )
            }
        }
    }
}

@Composable
private fun DiscordReauthorizeWarningRow(
    onReauthorize: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.discord_reauthorize_required_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(stringResource(R.string.discord_reauthorize_required_description))
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = null,
                )
            },
            trailingContent = {
                TextButton(
                    onClick = onReauthorize,
                    enabled = enabled,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.discord_reauthorize_action))
                }
            },
            colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onErrorContainer,
                    supportingColor = MaterialTheme.colorScheme.onErrorContainer,
                    leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        )
    }
}

@Composable
private fun activitySourceLabel(source: ActivitySource): String =
    when (source) {
        ActivitySource.ARTIST -> stringResource(R.string.artist_name)
        ActivitySource.ALBUM -> stringResource(R.string.album_name)
        ActivitySource.SONG -> stringResource(R.string.song_title)
        ActivitySource.APP -> stringResource(R.string.app_name)
    }

@Composable
private fun discordPresenceStatusLabel(value: String): String =
    when (value) {
        "online" -> stringResource(R.string.discord_presence_online)
        "dnd" -> stringResource(R.string.discord_presence_do_not_disturb)
        "idle" -> stringResource(R.string.discord_presence_idle)
        "streaming" -> stringResource(R.string.discord_presence_streaming)
        else -> stringResource(R.string.discord_presence_online)
    }

@Composable
private fun discordPlatformLabel(value: String): String =
    when (value) {
        "desktop" -> stringResource(R.string.discord_platform_desktop)
        "xbox" -> stringResource(R.string.discord_platform_xbox)
        "samsung" -> stringResource(R.string.discord_platform_samsung)
        "ios" -> stringResource(R.string.discord_platform_ios)
        "android" -> stringResource(R.string.discord_platform_android)
        "embedded" -> stringResource(R.string.discord_platform_embedded)
        "ps4" -> stringResource(R.string.discord_platform_ps4)
        "ps5" -> stringResource(R.string.discord_platform_ps5)
        else -> stringResource(R.string.discord_platform_android)
    }

@Composable
private fun discordActivityTypeLabel(value: String): String =
    when (value) {
        "PLAYING" -> stringResource(R.string.discord_activity_type_playing_label)
        "STREAMING" -> stringResource(R.string.discord_activity_type_streaming_label)
        "LISTENING" -> stringResource(R.string.discord_activity_type_listening_label)
        "WATCHING" -> stringResource(R.string.discord_activity_type_watching_label)
        "COMPETING" -> stringResource(R.string.discord_activity_type_competing_label)
        else -> value
    }

@Composable
private fun discordImageTypeLabel(value: String): String =
    when (value.lowercase()) {
        "thumbnail" -> stringResource(R.string.discord_image_album_artwork)
        "artist" -> stringResource(R.string.discord_image_artist_artwork)
        "appicon" -> stringResource(R.string.app_icon)
        "custom" -> stringResource(R.string.custom)
        "dontshow" -> stringResource(R.string.dont_show)
        else -> value
    }

@Composable
private fun discordLargeTextSourceLabel(value: String): String =
    when (value.lowercase()) {
        "song" -> stringResource(R.string.song_title)
        "artist" -> stringResource(R.string.artist_name)
        "album" -> stringResource(R.string.album_name)
        "app" -> stringResource(R.string.app_name)
        "custom" -> stringResource(R.string.custom)
        "dontshow" -> stringResource(R.string.dont_show)
        else -> value
    }

@Composable
fun EditablePreference(
    title: String,
    iconRes: Int,
    value: String,
    defaultValue: String,
    onValueChange: (String) -> Unit,
    description: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    PreferenceEntry(
        title = { Text(title) },
        description = description ?: if (value.isEmpty()) defaultValue else value,
        icon = { Icon(painterResource(iconRes), null) },
        trailingContent = {
            TextButton(onClick = { showDialog = true }, shapes = ButtonDefaults.shapes()) { Text("Edit") }
        },
    )
    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(if (text.isBlank()) "" else text)
                    showDialog = false
                }, shapes = ButtonDefaults.shapes()) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            },
            title = { Text("Edit $title") },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(defaultValue) },
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            },
        )
    }
}

@Composable
fun RichPresence(
    song: Song?,
    currentPlaybackTimeMillis: Long = 0L,
    nameSource: ActivitySource = ActivitySource.APP,
    detailsSource: ActivitySource = ActivitySource.SONG,
    stateSource: ActivitySource = ActivitySource.ARTIST,
    activityType: String = "LISTENING",
    largeImageType: String = "thumbnail",
    largeImageCustomUrl: String = "",
    largeTextSource: String = "album",
    largeTextCustom: String = "",
    smallImageType: String = "artist",
    smallImageCustomUrl: String = "",
    button1Label: String = "Listen on YouTube Music",
    button1Enabled: Boolean = true,
    button1UrlSource: String = "songurl",
    button1CustomUrl: String = "",
    button2Label: String = "Go to ArchiveTune",
    button2Enabled: Boolean = true,
    button2UrlSource: String = "custom",
    button2CustomUrl: String = "https://github.com/ArchiveTuneApp/ArchiveTune",
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val artistNameFallback = stringResource(R.string.artist_name)
    val albumNameFallback = stringResource(R.string.album_name)
    val songTitleFallback = stringResource(R.string.song_title)
    val customLargeTextFallback = stringResource(R.string.custom_large_text)

    fun resolveUrl(
        source: String,
        song: Song?,
        custom: String,
    ): String? =
        when (source.lowercase()) {
            "songurl" -> {
                song?.song?.id?.let { "https://music.youtube.com/watch?v=$it" }
            }

            "artisturl" -> {
                song
                    ?.artists
                    ?.firstOrNull()
                    ?.id
                    ?.let { "https://music.youtube.com/channel/$it" }
            }

            "albumurl" -> {
                song?.discordAlbumMusicUrl()
            }

            "custom" -> {
                custom.takeIf { it.isNotBlank() }
            }

            else -> {
                null
            }
        }

    fun previewSourceValue(source: ActivitySource): String =
        when (source) {
            ActivitySource.ARTIST -> song?.artists?.firstOrNull()?.name ?: artistNameFallback
            ActivitySource.ALBUM -> song?.song?.albumName ?: song?.album?.title ?: albumNameFallback
            ActivitySource.SONG -> song?.song?.title?.ifBlank { songTitleFallback } ?: songTitleFallback
            ActivitySource.APP -> appName
        }

    val previewName = previewSourceValue(nameSource)
    val previewDetails = previewSourceValue(detailsSource)
    val previewState = previewSourceValue(stateSource)
    val previewLargeText =
        when (largeTextSource.lowercase()) {
            "song" -> previewSourceValue(ActivitySource.SONG)
            "artist" -> previewSourceValue(ActivitySource.ARTIST)
            "album" -> previewSourceValue(ActivitySource.ALBUM)
            "app" -> appName
            "custom" -> largeTextCustom.ifBlank { customLargeTextFallback }
            "dontshow" -> null
            else -> previewSourceValue(ActivitySource.ALBUM)
        }
    val visiblePreviewDetails = previewDetails.takeUnless { it == previewName }
    val visiblePreviewState =
        previewState.takeUnless {
            it == previewName || it == visiblePreviewDetails
        }
    val visiblePreviewLargeText =
        previewLargeText?.takeUnless {
            it == previewName || it == visiblePreviewDetails || it == visiblePreviewState
        }
    val resolvedButton1Url = resolveUrl(button1UrlSource, song, button1CustomUrl)
    val resolvedButton2Url = resolveUrl(button2UrlSource, song, button2CustomUrl)
    val activityTypeLabel = discordActivityTypeLabel(activityType)

    PreferenceEntry(
        title = {
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        },
        content = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = activityTypeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(108.dp)) {
                            val largeImageModel =
                                when (largeImageType.lowercase()) {
                                    "thumbnail" -> song?.song?.thumbnailUrl
                                    "artist" -> song?.artists?.firstOrNull()?.thumbnailUrl
                                    "appicon" -> "https://raw.githubusercontent.com/ArchiveTuneApp/ArchiveTune/main/fastlane/metadata/android/en-US/images/icon.png"
                                    "custom" -> largeImageCustomUrl.ifBlank { song?.song?.thumbnailUrl }
                                    else -> song?.song?.thumbnailUrl
                                }
                            AsyncImage(
                                model = largeImageModel,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .align(Alignment.TopStart)
                                        .run {
                                            if (song == null) {
                                                border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    RoundedCornerShape(12.dp),
                                                )
                                            } else {
                                                this
                                            }
                                        },
                            )
                            val songThumb = song?.song?.thumbnailUrl
                            val artistThumb = song?.artists?.firstOrNull()?.thumbnailUrl
                            val smallModel =
                                when (smallImageType.lowercase()) {
                                    "thumbnail" -> songThumb
                                    "artist" -> artistThumb
                                    "appicon" -> "https://raw.githubusercontent.com/ArchiveTuneApp/ArchiveTune/main/fastlane/metadata/android/en-US/images/icon.png"
                                    "custom" -> smallImageCustomUrl.takeIf { it.isNotBlank() } ?: songThumb
                                    "dontshow", "none" -> null
                                    else -> artistThumb
                                }
                            smallModel?.let {
                                Box(
                                    modifier =
                                        Modifier
                                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                                            .padding(2.dp)
                                            .align(Alignment.BottomEnd),
                                ) {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        ) {
                            Text(
                                text = previewName,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            visiblePreviewDetails?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            visiblePreviewState?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            visiblePreviewLargeText?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (song != null) {
                                SongProgressBar(
                                    currentTimeMillis = currentPlaybackTimeMillis,
                                    durationMillis = song.song.duration * 1000L,
                                    isPlaying = isPlaying,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = button1Enabled && button1Label.isNotBlank()) {
                        Button(
                            enabled = !resolvedButton1Url.isNullOrBlank(),
                            onClick = {
                                resolvedButton1Url?.let {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(button1Label)
                        }
                    }

                    AnimatedVisibility(visible = button2Enabled && button2Label.isNotBlank()) {
                        Button(
                            enabled = !resolvedButton2Url.isNullOrBlank(),
                            onClick = {
                                resolvedButton2Url?.let {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(button2Label)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun SongProgressBar(
    currentTimeMillis: Long,
    durationMillis: Long,
    isPlaying: Boolean = false,
) {
    var displayedTime by remember { mutableStateOf(currentTimeMillis) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                delay(500)
                displayedTime += 500
                if (displayedTime >= durationMillis) {
                    displayedTime = durationMillis
                    break
                }
            }
        }
    }

    val progress =
        if (durationMillis > 0) {
            displayedTime.toFloat() / durationMillis
        } else {
            0f
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = makeTimeString(displayedTime),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = makeTimeString(durationMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
