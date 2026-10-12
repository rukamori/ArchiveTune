/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.GlassNavigationBar
import moe.rukamori.archivetune.design.components.MiniPlayer
import moe.rukamori.archivetune.design.components.NavigationTab

/**
 * 新UIの共通シェル。
 *
 * 画面は [NavHost] が持つ。浮遊ナビとミニプレイヤーはその**外**に置くことで、
 * 「下端のガラスが画面の背後を覆っている」状態を作る。両者を同じ列に並べると
 * ブラー対象に自身が混ざるので、NavHost 側だけを [dev.chrisbanes.haze.hazeSource] に
 * し、ガラス面だけを消費者側に回す。
 *
 * 下端の重なりはシェルが一括で決め、子画面は [LocalPlayerAwareWindowInsets] を読むだけで
 * よい。全画面プレイヤー・歌詞・キューでは下端の面を隠すので、そのときだけインセットも
 * 縮める（背後に余白を残さない）。
 *
 * @param hazeState 背後コンテンツの捕捉先。[MiniPlayer] と [GlassNavigationBar] が読む。
 */
@Composable
fun AppShell(
    navController: NavHostController,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val density = LocalDensity.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // `let` はインラインなので、中の collectAsState はそのまま composable の中で評価される。
    val hasTrack = connection?.let { it.mediaMetadata.collectAsState().value } != null

    // 全画面プレイヤー・歌詞・キューでは下端の面を隠す。設定は通常画面として扱い、
    // ナビを残す（Apple の設定もシートではなく押した先の画面として扱う）。
    val immersive = currentRoute == Routes.Player || currentRoute == Routes.Lyrics ||
        currentRoute == Routes.Queue

    val systemBarBottomDp =
        with(density) { WindowInsets.systemBars.getBottom(density).toDp() }

    val chromeBottomDp =
        if (immersive) {
            0.dp
        } else {
            metrics.navBarHeight.dp +
                (if (hasTrack) metrics.miniPlayerHeight.dp else 0.dp) +
                systemBarBottomDp
        }

    val contentInsets =
        WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            .add(
                WindowInsets(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = with(density) { chromeBottomDp.roundToPx() },
                ),
            )

    val mainTabs =
        remember {
            listOf(
                NavigationTab(
                    labelId = R.string.home,
                    iconInactive = R.drawable.home_outlined,
                    iconActive = R.drawable.home_filled,
                    route = Routes.Home,
                ),
                NavigationTab(
                    labelId = R.string.new_releases,
                    iconInactive = R.drawable.new_release,
                    iconActive = R.drawable.new_release,
                    route = Routes.New,
                ),
                NavigationTab(
                    labelId = R.string.filter_library,
                    iconInactive = R.drawable.library_outlined,
                    iconActive = R.drawable.library_filled,
                    route = Routes.Library,
                ),
                NavigationTab(
                    labelId = R.string.search,
                    iconInactive = R.drawable.search,
                    iconActive = R.drawable.search,
                    route = Routes.Search,
                ),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        CompositionLocalProvider(LocalPlayerAwareWindowInsets provides contentInsets) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                enterTransition = {
                    when (targetState.destination.route) {
                        // タブの入れ替えは重ね替え。面が見え切っているので動きを立てない。
                        in Routes.MainTabs -> fadeIn(tween(200))
                        // 全画面プレイヤー系は下から持ち上げる（Apple のシートと同じ向き）。
                        Routes.Player, Routes.Lyrics, Routes.Queue ->
                            slideInVertically(tween(320)) { it } + fadeIn(tween(240))
                        // それ以外（詳細・設定）は右から押す。
                        else -> slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(220))
                    }
                },
                exitTransition = {
                    when (targetState.destination.route) {
                        in Routes.MainTabs -> fadeOut(tween(160))
                        Routes.Player, Routes.Lyrics, Routes.Queue ->
                            slideOutVertically(tween(300)) { it } + fadeOut(tween(200))
                        else -> slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(180))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    when (initialState.destination.route) {
                        Routes.Player, Routes.Lyrics, Routes.Queue ->
                            slideOutVertically(tween(300)) { it } + fadeOut(tween(200))
                        Routes.Home, Routes.New, Routes.Library, Routes.Search -> fadeOut(tween(160))
                        else -> slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(180))
                    }
                },
            ) {
                composable(Routes.Home) { HomeScreen(navController) }
                composable(Routes.New) { NewScreen(navController) }
                composable(Routes.Library) { LibraryScreen(navController) }
                composable(Routes.Search) { SearchScreen(navController) }

                composable(Routes.Player) {
                    PlayerScreen(
                        onDismiss = { navController.popBackStack() },
                        onOpenLyrics = { navController.navigate(Routes.Lyrics) },
                        onOpenQueue = { navController.navigate(Routes.Queue) },
                    )
                }
                composable(Routes.Lyrics) {
                    LyricsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Queue) {
                    QueueScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Settings) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.OnlineSearch) {
                    OnlineSearchResultsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.Album) {
                    AlbumScreen(
                        onBack = { navController.popBackStack() },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                    )
                }
                composable(Routes.Artist) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenPlaylist = { id -> navController.navigate(Routes.onlinePlaylist(id)) },
                    )
                }
                composable(Routes.OnlinePlaylist) {
                    OnlinePlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                    )
                }
                composable(Routes.LocalPlaylist) {
                    LocalPlaylistScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Browse) {
                    BrowseScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                        onOpenPlaylist = { id -> navController.navigate(Routes.onlinePlaylist(id)) },
                    )
                }
            }
        }

        if (!immersive) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(1f),
            ) {
                MiniPlayer(onOpenPlayer = { navController.navigate(Routes.Player) })
                GlassNavigationBar(
                    tabs = mainTabs,
                    selectedRoute = currentRoute,
                    onSelect = { tab ->
                        if (tab.route == currentRoute) {
                            // 選択済みタブの再タップは一覧を先頭へ戻す。
                            navController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.set(ScrollToTopKey, true)
                        } else {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.Home) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 再タップで一覧を先頭へ戻すための合図。各タブがこのキーを監視する。 */
internal const val ScrollToTopKey = "scrollToTop"
