/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.App.Companion.forgetAccount
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.ForceSyncOnAccountSwitchKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.SavedAccountsKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.UseLoginForBrowse
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.decodeSavedAccounts
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.utils.putLegacyPoToken
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.UUID
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.AccountChannelUiModel
import moe.rukamori.archivetune.viewmodels.AccountChannelsState

private val CardShape = RoundedCornerShape(28.dp)
private val InnerTileShape = RoundedCornerShape(22.dp)
private val AvatarSize = 88.dp
private val QuickTileIconSize = 48.dp
private val RowIconSize = 42.dp
private const val PressScale = 0.96f

@Immutable
private data class SavedAccountCollection(
    val accounts: List<SavedAccount>,
)

@Composable
fun AccountSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val accountLabel = stringResource(R.string.account)
    val generalLabel = stringResource(R.string.general)
    val integrationLabel = stringResource(R.string.integration)
    val miscLabel = stringResource(R.string.misc)
    val loginLabel = stringResource(R.string.login)
    val notLoggedInLabel = stringResource(R.string.not_logged_in)
    val tokenDescription = stringResource(R.string.token_adv_login_description)

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)
    val (forceSyncOnAccountSwitch, onForceSyncOnAccountSwitchChange) =
        rememberPreference(ForceSyncOnAccountSwitchKey, false)
    val (selectedYtmPlaylists, _) = rememberPreference(SelectedYtmPlaylistsKey, "")
    val (savedAccountsJson, onSavedAccountsJsonChange) = rememberPreference(SavedAccountsKey, "")
    val savedAccounts = remember(savedAccountsJson) {
        SavedAccountCollection(decodeSavedAccounts(savedAccountsJson))
    }

    val onLegacyPoTokenChange: (String) -> Unit = { value ->
        PreferenceStore.launchEdit(context.dataStore) {
            putLegacyPoToken(value)
        }
    }

    val isLoggedIn = remember(innerTubeCookie) {
        hasYouTubeLoginCookie(innerTubeCookie)
    }

    LaunchedEffect(useLoginForBrowse) {
        YouTube.useLoginForBrowse = useLoginForBrowse
    }

    val viewModel: HomeViewModel = hiltViewModel()
    val accountNameFromViewModel by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountChannelsState by viewModel.accountChannelsState.collectAsStateWithLifecycle()

    val displayName = when {
        accountNameFromViewModel.isNotBlank() -> accountNameFromViewModel
        accountNamePref.isNotBlank() -> accountNamePref
        isLoggedIn -> accountLabel
        else -> loginLabel
    }

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showUnsavedAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            showToken = false
        }
    }

    val hasUpdate = BuildConfig.UPDATER_AVAILABLE &&
        Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
    val tokenActionTitle = when {
        !isLoggedIn -> stringResource(R.string.advanced_login)
        showToken -> stringResource(R.string.token_shown)
        else -> stringResource(R.string.token_hidden)
    }

    val saveCurrentAccount: () -> Unit = {
        val existing = decodeSavedAccounts(savedAccountsJson)
        if (isLoggedIn && existing.none { it.innerTubeCookie == innerTubeCookie }) {
            val newAccount = SavedAccount(
                id = UUID.randomUUID().toString(),
                name = if (accountNameFromViewModel.isNotBlank()) accountNameFromViewModel else accountNamePref,
                email = accountEmail,
                channelHandle = accountChannelHandle,
                innerTubeCookie = innerTubeCookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
                ytmSync = ytmSync,
                selectedYtmPlaylists = selectedYtmPlaylists,
            )
            onSavedAccountsJsonChange(encodeSavedAccounts(existing + newAccount))
        }
    }

    val switchToAccount: (SavedAccount) -> Unit = { account ->
        viewModel.switchToAccount(
            account = account,
            forceSyncOnSwitch = forceSyncOnAccountSwitch,
        )
    }

    val switchToAccountChannel: (AccountChannelUiModel) -> Unit = { channel ->
        viewModel.switchToAccountChannel(
            channel = channel,
            forceSyncOnSwitch = forceSyncOnAccountSwitch,
        )
    }

    val removeAccount: (SavedAccount) -> Unit = { account ->
        val existing = decodeSavedAccounts(savedAccountsJson)
        onSavedAccountsJsonChange(encodeSavedAccounts(existing.filter { it.id != account.id }))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Column {
                        Text(
                            text = accountLabel,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                    OutlinedIconButton(
                        onClick = { showTokenEditor = true },
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = null,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.token),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (hasUpdate) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            },
                        ) {
                            OutlinedIconButton(
                                onClick = { uriHandler.openUri(Updater.getLatestDownloadUrl()) },
                                colors = IconButtonDefaults.outlinedIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                                border = null,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.update),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ProfileIdentityCard(
                    isLoggedIn = isLoggedIn,
                    accountName = displayName,
                    accountEmail = accountEmail,
                    accountHandle = accountChannelHandle,
                    accountImageUrl = accountImageUrl,
                    savedAccounts = savedAccounts,
                    activeInnerTubeCookie = innerTubeCookie,
                    activeDataSyncId = dataSyncId,
                    accountChannelsState = accountChannelsState,
                    onPrimaryAction = {
                        if (isLoggedIn) {
                            navController.navigate("account")
                        } else {
                            navController.navigate(buildLoginRoute())
                        }
                    },
                    onSecondaryAction = {
                        if (isLoggedIn) {
                            showToken = false
                            onInnerTubeCookieChange("")
                            forgetAccount(context, clearWebAuthSession = true)
                        } else {
                            showTokenEditor = true
                        }
                    },
                    onSaveAccount = saveCurrentAccount,
                    onSwitchAccount = switchToAccount,
                    onSwitchAccountChannel = switchToAccountChannel,
                    onRemoveAccount = removeAccount,
                    onAddAnotherAccount = {
                        val isSaved = savedAccounts.accounts.any { it.innerTubeCookie == innerTubeCookie }
                        if (isLoggedIn && !isSaved) {
                            showUnsavedAccountDialog = true
                        } else {
                            navController.navigate(buildLoginRoute())
                        }
                    },
                )
            }

            if (hasUpdate) {
                item {
                    UpdateBannerStrip(
                        latestVersion = latestVersionName,
                        onClick = { uriHandler.openUri(Updater.getLatestDownloadUrl()) },
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = isLoggedIn,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + expandVertically(
                        spring(stiffness = Spring.StiffnessLow),
                    ),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ExpressiveSectionCard(title = generalLabel) {
                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.add_circle),
                            title = stringResource(R.string.more_content),
                            subtitle = stringResource(R.string.use_login_for_browse_desc),
                            checked = useLoginForBrowse,
                            onCheckedChange = onUseLoginForBrowseChange,
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.cached),
                            title = stringResource(R.string.yt_sync),
                            checked = ytmSync,
                            onCheckedChange = onYtmSyncChange,
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.sync),
                            title = stringResource(R.string.force_sync_on_switch_account),
                            subtitle = stringResource(R.string.force_sync_on_switch_account_desc),
                            checked = forceSyncOnAccountSwitch,
                            onCheckedChange = onForceSyncOnAccountSwitchChange,
                        )
                    }
                }
            }

            item {
                ExpressiveSectionCard(title = integrationLabel) {
                    ExpressiveActionRow(
                        icon = painterResource(R.drawable.integration),
                        title = integrationLabel,
                        subtitle = stringResource(R.string.account_integrations_summary),
                        onClick = { navController.navigate("settings/integration") },
                    )

                    ExpressiveDivider()

                    ExpressiveActionRow(
                        icon = painterResource(R.drawable.fire),
                        title = stringResource(R.string.music_together),
                        onClick = { navController.navigate("settings/music_together") },
                    )
                }
            }

            item {
                ExpressiveSectionCard(title = miscLabel) {
                    ExpressiveActionRow(
                        icon = painterResource(R.drawable.token),
                        title = tokenActionTitle,
                        subtitle = tokenDescription,
                        accent = if (isLoggedIn && showToken) MaterialTheme.colorScheme.tertiary else null,
                        onClick = {
                            if (!isLoggedIn) {
                                showTokenEditor = true
                            } else if (!showToken) {
                                showToken = true
                            } else {
                                showTokenEditor = true
                            }
                        },
                    )
                }
            }

            item {
                VersionStamp()
            }
        }
    }

    if (showTokenEditor) {
        TokenEditorDialog(
            innerTubeCookie = innerTubeCookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            accountNamePref = accountNamePref,
            accountEmail = accountEmail,
            accountChannelHandle = accountChannelHandle,
            onInnerTubeCookieChange = onInnerTubeCookieChange,
            onPoTokenChange = onLegacyPoTokenChange,
            onVisitorDataChange = onVisitorDataChange,
            onDataSyncIdChange = onDataSyncIdChange,
            onAccountNameChange = onAccountNameChange,
            onAccountEmailChange = onAccountEmailChange,
            onAccountChannelHandleChange = onAccountChannelHandleChange,
            onDismiss = { showTokenEditor = false },
        )
    }

    if (showUnsavedAccountDialog) {
        Dialog(onDismissRequest = { showUnsavedAccountDialog = false }) {
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.unsaved_account_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.unsaved_account_dialog_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { showUnsavedAccountDialog = false },
                        ) {
                            Text(text = stringResource(R.string.unsaved_account_dialog_cancel))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                showUnsavedAccountDialog = false
                                navController.navigate(buildLoginRoute())
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.unsaved_account_dialog_no_thanks),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                showUnsavedAccountDialog = false
                                saveCurrentAccount()
                                navController.navigate(buildLoginRoute())
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.unsaved_account_dialog_save_yes),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    isLoggedIn: Boolean,
    accountName: String,
    accountEmail: String,
    accountHandle: String,
    accountImageUrl: String?,
    savedAccounts: SavedAccountCollection,
    activeInnerTubeCookie: String,
    activeDataSyncId: String,
    accountChannelsState: AccountChannelsState,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onSaveAccount: () -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
    onSwitchAccountChannel: (AccountChannelUiModel) -> Unit,
    onRemoveAccount: (SavedAccount) -> Unit,
    onAddAnotherAccount: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PressScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "heroScale",
    )
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val menuChevronRotation by animateFloatAsState(
        targetValue = if (accountMenuExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "accountMenuChevron",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onPrimaryAction,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f),
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                ),
                            ),
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoggedIn && !accountImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = accountImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (isLoggedIn) R.drawable.account else R.drawable.login,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoggedIn,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessHigh)),
                    exit = scaleOut(),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AnimatedContent(
                    targetState = accountName,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessHigh)))
                    },
                    label = "nameTransition",
                ) { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (accountHandle.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            text = accountHandle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!isLoggedIn) {
                    Text(
                        text = stringResource(R.string.not_logged_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    )
                }
            }

            accountEmail
                .takeIf { it.isNotBlank() }
                ?.let { email ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        )
                    }
                }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Box {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.ElevatedLeadingButton(
                                onClick = onPrimaryAction,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(
                                    defaultElevation = 1.dp,
                                    pressedElevation = 0.dp,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isLoggedIn) R.drawable.account else R.drawable.login,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isLoggedIn) stringResource(R.string.account) else stringResource(R.string.login),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.ElevatedTrailingButton(
                                checked = accountMenuExpanded,
                                onCheckedChange = { accountMenuExpanded = it },
                                enabled = isLoggedIn || savedAccounts.accounts.isNotEmpty(),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(
                                    defaultElevation = 1.dp,
                                    pressedElevation = 0.dp,
                                ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.expand_more),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(SplitButtonDefaults.TrailingIconSize)
                                        .rotate(menuChevronRotation),
                                )
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false },
                    ) {
                        val accountChannels = (accountChannelsState as? AccountChannelsState.Success)?.channels
                        if (accountChannels != null && accountChannels.items.size > 1) {
                            Text(
                                text = stringResource(R.string.youtube_channels),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            accountChannels.items.forEach { channel ->
                                val isActive = channel.isSelected || channel.dataSyncId == activeDataSyncId
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val channelSubtitle = channel.channelHandle.ifBlank { channel.byline }
                                            if (channelSubtitle.isNotBlank()) {
                                                Text(
                                                    text = channelSubtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.account),
                                            contentDescription = null,
                                            tint = if (isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    onClick = {
                                        if (!isActive) onSwitchAccountChannel(channel)
                                        accountMenuExpanded = false
                                    },
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        Text(
                            text = stringResource(R.string.saved_accounts),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        if (savedAccounts.accounts.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.no_saved_accounts),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                        } else {
                            savedAccounts.accounts.forEach { account ->
                                val isActive = account.innerTubeCookie == activeInnerTubeCookie
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = account.name.ifBlank { account.email },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (account.email.isNotBlank()) {
                                                Text(
                                                    text = account.email,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.account),
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        OutlinedIconButton(
                                            onClick = { onRemoveAccount(account) },
                                            modifier = Modifier.size(32.dp),
                                            border = null,
                                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.delete),
                                                contentDescription = stringResource(R.string.remove_account),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (!isActive) onSwitchAccount(account)
                                        accountMenuExpanded = false
                                    },
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                        if (isLoggedIn) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.save_current_account),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.bookmark),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    onSaveAccount()
                                    accountMenuExpanded = false
                                },
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.add_another_account),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.add_circle),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    accountMenuExpanded = false
                                    onAddAnotherAccount()
                                },
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSecondaryAction,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = if (isLoggedIn) stringResource(R.string.action_logout) else stringResource(R.string.advanced_login),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateBannerStrip(
    latestVersion: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PressScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "updateScale",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = CardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = { Badge(containerColor = MaterialTheme.colorScheme.error) },
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.update),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = latestVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                )
            }

            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(R.string.update_text),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ExpressiveActionRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "rowScale",
    )
    val tint = accent ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(InnerTileShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExpressiveRowIcon(icon = icon, tint = tint)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ExpressiveSwitchRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "switchRowBg",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(InnerTileShape)
            .background(containerColor)
            .clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExpressiveRowIcon(
                icon = icon,
                tint = MaterialTheme.colorScheme.primary,
                emphasized = checked,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
                ),
            )
        }
    }
}

@Composable
private fun ExpressiveRowIcon(
    icon: Painter,
    tint: Color,
    emphasized: Boolean = false,
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (emphasized) 0.20f else 0.10f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconBgAlpha",
    )

    Surface(
        modifier = Modifier.size(RowIconSize),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = bgAlpha),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun ExpressiveDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
private fun VersionStamp() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
        )
    }
}

@Composable
private fun TokenEditorDialog(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    accountNamePref: String,
    accountEmail: String,
    accountChannelHandle: String,
    onInnerTubeCookieChange: (String) -> Unit,
    onPoTokenChange: (String) -> Unit,
    onVisitorDataChange: (String) -> Unit,
    onDataSyncIdChange: (String) -> Unit,
    onAccountNameChange: (String) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountChannelHandleChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = """
        ***INNERTUBE COOKIE*** =$innerTubeCookie
        ***VISITOR DATA*** =$visitorData
        ***DATASYNC ID*** =$dataSyncId
        ***PO TOKEN*** =${YouTube.poToken.orEmpty()}
        ***ACCOUNT NAME*** =$accountNamePref
        ***ACCOUNT EMAIL*** =$accountEmail
        ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
    """.trimIndent()

    TextFieldDialog(
        initialTextFieldValue = TextFieldValue(text),
        onDone = { data ->
            data.split("\n").forEach {
                when {
                    it.startsWith("***INNERTUBE COOKIE*** =") -> onInnerTubeCookieChange(it.substringAfter("="))
                    it.startsWith("***VISITOR DATA*** =") -> onVisitorDataChange(it.substringAfter("="))
                    it.startsWith("***DATASYNC ID*** =") -> onDataSyncIdChange(it.substringAfter("="))
                    it.startsWith("***PO TOKEN*** =") -> onPoTokenChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT NAME*** =") -> onAccountNameChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT EMAIL*** =") -> onAccountEmailChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> onAccountChannelHandleChange(it.substringAfter("="))
                }
            }
        },
        onDismiss = onDismiss,
        singleLine = false,
        maxLines = 20,
        isInputValid = {
            hasYouTubeLoginCookie(it)
        },
        extraContent = {
            InfoLabel(text = stringResource(R.string.token_adv_login_description))
        },
    )
}

private fun hasVisibleSecureDetails(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    poToken: String,
): Boolean {
    return innerTubeCookie.isNotBlank() || visitorData.isNotBlank() || dataSyncId.isNotBlank() || poToken.isNotBlank()
}

private fun previewSecureValue(value: String): String {
    val normalized = value.replace("\n", " ").replace("\r", " ").trim()
    if (normalized.length <= 76) {
        return normalized
    }
    return normalized.take(52) + "\u2025" + normalized.takeLast(18)
}

