/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LyricsV2BounceFactorKey
import moe.rukamori.archivetune.constants.LyricsV2FillTransitionWidthKey
import moe.rukamori.archivetune.constants.LyricsV2GlowFactorKey
import moe.rukamori.archivetune.constants.LyricsV2LrcBounceEnabledKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsAnimationSettings(navController: NavController) {
    val (bounceFactor, onBounceFactorChange) = rememberPreference(LyricsV2BounceFactorKey, defaultValue = 1f)
    val (glowFactor, onGlowFactorChange) = rememberPreference(LyricsV2GlowFactorKey, defaultValue = 1f)
    val (fillTransitionWidth, onFillTransitionWidthChange) = rememberPreference(LyricsV2FillTransitionWidthKey, defaultValue = 8f)
    val (lrcBounceEnabled, onLrcBounceEnabledChange) = rememberPreference(LyricsV2LrcBounceEnabledKey, defaultValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lyrics_animation_style)) },
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
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            modifier =
                Modifier
                    .padding(top = topPadding)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = "Animation Tuning") {
                item {
                    PreferenceEntry(
                        title = { Text("Line Bounce Effect") },
                        description = "Enable bounce animation for line-synced (LRC) lyrics",
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        trailingContent = {
                            Switch(
                                checked = lrcBounceEnabled,
                                onCheckedChange = onLrcBounceEnabledChange,
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text("Bounce Amplitude") },
                        description = "Adjust the bounce effect when a word is sung (${(bounceFactor * 100).toInt()}%)",
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        content = {
                            Slider(
                                value = bounceFactor,
                                onValueChange = onBounceFactorChange,
                                valueRange = 0f..2f,
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text("Glow Intensity") },
                        description = "Adjust the glow brightness of the sung word (${(glowFactor * 100).toInt()}%)",
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        content = {
                            Slider(
                                value = glowFactor,
                                onValueChange = onGlowFactorChange,
                                valueRange = 0f..2f,
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text("Fill Transition Smoothness") },
                        description = "Adjust the gradient edge width of the liquid fill effect (${fillTransitionWidth.toInt()} dp)",
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        content = {
                            Slider(
                                value = fillTransitionWidth,
                                onValueChange = onFillTransitionWidthChange,
                                valueRange = 2f..24f,
                            )
                        },
                    )
                }
            }
        }
    }
}
