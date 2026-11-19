/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
    viewModel: HomeViewModel,
    onClearUpdateBadge: () -> Unit = {},
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()
    val revealState = rememberSettingsRevealState()
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    val storagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            isStorageGranted = result[storagePermission] == true || isStorageGranted
            if (notificationPermission != null) {
                isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
            }
        }

    val accountNamePref by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountHandle by rememberPreference(AccountChannelHandleKey, "")
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountBannerUrl by viewModel.accountBannerUrl.collectAsStateWithLifecycle()
    val loginState by remember(context) {
        context.dataStore.data
            .map<Preferences, Boolean?> { hasYouTubeLoginCookie(it[InnerTubeCookieKey]) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val accountFallbackName = stringResource(R.string.account)
    val profileSlot =
        when (loginState) {
            null -> {
                SettingsProfileSlot.Loading
            }

            false -> {
                SettingsProfileSlot.Hidden
            }

            true -> {
                SettingsProfileSlot.Profile(
                    SettingsProfileState(
                        accountName = accountName.ifBlank { accountNamePref }.ifBlank { accountFallbackName },
                        accountEmail = accountEmail,
                        accountHandle = accountHandle,
                        accountImageUrl = accountImageUrl,
                        accountBannerUrl = accountBannerUrl,
                    ),
                )
            }
        }

    val scrollBehavior = appBarScrollBehavior()
    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate =
        BuildConfig.UPDATER_AVAILABLE &&
            Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
    var isUpdateDismissed by remember { mutableStateOf(false) }
    val settingsContent =
        buildSettingsContent(
            navController = navController,
            isAndroid12OrLater = isAndroid12OrLater,
            hasUpdate = hasUpdate,
            showAccountEntry = loginState == false,
            context = context,
        )

    val itemFadeInSpec = motion.defaultEffectsSpec<Float>()
    val itemPlacementSpec = motion.defaultSpatialSpec<IntOffset>()
    val itemFadeOutSpec = motion.fastEffectsSpec<Float>()
    val slotSizeSpec = motion.defaultSpatialSpec<IntSize>()
    val blockModifier =
        Modifier
            .widthIn(max = SettingsDimensions.HubContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)

    fun LazyItemScope.hubItemModifier(): Modifier =
        if (animationsDisabled) {
            blockModifier
        } else {
            blockModifier.animateItem(
                fadeInSpec = itemFadeInSpec,
                placementSpec = itemPlacementSpec,
                fadeOutSpec = itemFadeOutSpec,
            )
        }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
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
                            contentDescription = stringResource(R.string.back_button_desc),
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
            state = listState,
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
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var revealOrder = 0

            item(key = "profile_slot", contentType = "settings_profile") {
                AnimatedContent(
                    targetState = profileSlot,
                    contentKey = { it::class },
                    transitionSpec = {
                        if (animationsDisabled) {
                            (EnterTransition.None togetherWith ExitTransition.None)
                                .using(SizeTransform { _, _ -> snap() })
                        } else {
                            (fadeIn(itemFadeInSpec) togetherWith fadeOut(itemFadeOutSpec))
                                .using(SizeTransform { _, _ -> slotSizeSpec })
                        }
                    },
                    label = "settingsProfileSlot",
                    modifier = hubItemModifier(),
                ) { slot ->
                    when (slot) {
                        SettingsProfileSlot.Loading -> {
                            SettingsProfileLoadingRow(
                                modifier = Modifier.padding(bottom = SettingsDimensions.SectionSpacing),
                            )
                        }

                        is SettingsProfileSlot.Profile -> {
                            SettingsProfileCard(
                                state = slot.state,
                                revealState = revealState,
                                onClick = { navController.navigate("settings/account") },
                                coverScrollOffset = {
                                    if (listState.firstVisibleItemIndex == 0) {
                                        listState.firstVisibleItemScrollOffset.toFloat()
                                    } else {
                                        0f
                                    }
                                },
                                modifier = Modifier.padding(bottom = SettingsDimensions.SectionSpacing),
                            )
                        }

                        SettingsProfileSlot.Hidden -> {
                            Spacer(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (hasUpdate && !isUpdateDismissed) {
                val order = revealOrder++
                item(key = "update", contentType = "settings_banner") {
                    SettingsUpdateBanner(
                        latestVersion = latestVersionName,
                        onClick = { navController.navigate("settings/update") },
                        onDismiss = { isUpdateDismissed = true },
                        modifier =
                            hubItemModifier()
                                .padding(bottom = SettingsDimensions.SectionSpacing)
                                .settingsReveal(revealState, "update", order),
                    )
                }
            }

            if (shouldShowPermissionHint) {
                val order = revealOrder++
                item(key = "permission", contentType = "settings_banner") {
                    SettingsPermissionBanner(
                        onRequestPermission = {
                            val toRequest =
                                buildList {
                                    if (!isStorageGranted) add(storagePermission)
                                    if (!isNotificationGranted && notificationPermission != null) {
                                        add(notificationPermission)
                                    }
                                }
                            if (toRequest.isNotEmpty()) {
                                permissionLauncher.launch(toRequest.toTypedArray())
                            }
                        },
                        modifier =
                            hubItemModifier()
                                .padding(bottom = SettingsDimensions.SectionSpacing)
                                .settingsReveal(revealState, "permission", order),
                    )
                }
            }

            val highlightsHeaderOrder = revealOrder++
            item(key = "highlights_header", contentType = "settings_section_header") {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_essentials),
                    modifier =
                        hubItemModifier()
                            .padding(top = 8.dp)
                            .settingsReveal(revealState, "highlights_header", highlightsHeaderOrder),
                )
            }

            val highlightsOrder = revealOrder
            revealOrder += settingsContent.highlights.size
            item(key = "highlights", contentType = "settings_highlights") {
                SettingsHighlightGrid(
                    highlights = settingsContent.highlights,
                    revealState = revealState,
                    revealOrderStart = highlightsOrder,
                    modifier = hubItemModifier(),
                )
            }

            settingsContent.groups.forEach { group ->
                val headerOrder = revealOrder++
                item(key = "header_${group.key}", contentType = "settings_section_header") {
                    SettingsSectionHeader(
                        title = group.title,
                        modifier =
                            hubItemModifier()
                                .padding(top = SettingsDimensions.HubBlockSpacing)
                                .settingsReveal(revealState, "header_${group.key}", headerOrder),
                    )
                }

                val groupOrder = revealOrder
                revealOrder += group.items.size
                itemsIndexed(
                    items = group.items,
                    key = { _, item -> item.key },
                    contentType = { _, _ -> "settings_item" },
                ) { index, settingsItem ->
                    SettingsHubListItem(
                        item = settingsItem,
                        index = index,
                        count = group.items.size,
                        modifier =
                            hubItemModifier()
                                .padding(
                                    bottom =
                                        if (index < group.items.lastIndex) {
                                            ListItemDefaults.SegmentedGap
                                        } else {
                                            0.dp
                                        },
                                ).settingsReveal(revealState, settingsItem.key, groupOrder + index),
                    )
                }
            }
        }
    }
}
