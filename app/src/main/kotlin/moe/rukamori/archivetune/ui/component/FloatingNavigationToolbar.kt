/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.constants.FloatingBarJunctionCornerRadius
import moe.rukamori.archivetune.constants.FloatingBarOuterCornerRadius
import moe.rukamori.archivetune.constants.FloatingBarStandaloneCornerRadius
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.rememberAppleMusicDesign

private val NavigationItemsMaxWidth = 360.dp
private val NavigationItemVerticalPadding = 8.dp

/**
 * Apple Music 風の浮遊カプセルナビゲーション。
 *
 * [LocalBackdropHazeState] が背後コンテンツを捕捉していれば、それを実際にブラーした
 * 「ガラス」面になる。無ければ半透明の塗りに落ちるだけで、
 * どの環境でもアイコンとラベルは常に読める。
 *
 * 素の透明度を下げただけのバーにしないため、上から順に
 * ベース / ブラー / ティント / 上端ハイライト / ヘアライン縁 の5層で組み立てている。
 */
@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    miniPlayerProximityProvider: () -> Float = { 0f },
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val hazeState = LocalBackdropHazeState.current
    val miniPlayerProximity = miniPlayerProximityProvider()
    val navigationShape =
        RoundedCornerShape(
            topStart = lerp(FloatingBarStandaloneCornerRadius.value, FloatingBarJunctionCornerRadius.value, miniPlayerProximity).dp,
            topEnd = lerp(FloatingBarStandaloneCornerRadius.value, FloatingBarJunctionCornerRadius.value, miniPlayerProximity).dp,
            bottomStart = lerp(FloatingBarStandaloneCornerRadius.value, FloatingBarOuterCornerRadius.value, miniPlayerProximity).dp,
            bottomEnd = lerp(FloatingBarStandaloneCornerRadius.value, FloatingBarOuterCornerRadius.value, miniPlayerProximity).dp,
        )
    val motionScheme = MaterialTheme.motionScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = NavigationBarMaxWidth)
                    .fillMaxWidth()
                    .height(NavigationBarHeight),
            shape = navigationShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            GlassSurface(
                shape = navigationShape,
                pureBlack = pureBlack,
                hazeState = hazeState,
            ) {
                ShortNavigationBar(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    arrangement = ShortNavigationBarArrangement.EqualWeight,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .widthIn(max = NavigationItemsMaxWidth)
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .padding(vertical = NavigationItemVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items.forEach { screen ->
                                val selected = isSelected(screen)
                                val onDoubleClick =
                                    remember(screen, onSearchItemDoubleClick) {
                                        if (screen == Screens.Search) onSearchItemDoubleClick else null
                                    }
                                val lastClickTime = remember(screen) { mutableLongStateOf(0L) }
                                val onClick =
                                    remember(screen, selected, onItemClick, onDoubleClick) {
                                        {
                                            val currentTime = SystemClock.uptimeMillis()
                                            val isDoubleClick =
                                                onDoubleClick != null &&
                                                    currentTime - lastClickTime.longValue <= ViewConfiguration.getDoubleTapTimeout()
                                            lastClickTime.longValue = if (isDoubleClick) 0L else currentTime
                                            if (isDoubleClick) {
                                                onDoubleClick?.invoke()
                                                Unit
                                            } else {
                                                onItemClick(screen, selected)
                                            }
                                        }
                                    }

                                ShortNavigationBarItem(
                                    selected = selected,
                                    onClick = onClick,
                                    modifier = Modifier.weight(1f),
                                    icon = {
                                        Crossfade(
                                            targetState = selected,
                                            animationSpec = motionScheme.fastEffectsSpec(),
                                            label = "navigationItemIcon",
                                        ) { itemSelected ->
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        if (itemSelected) screen.iconIdActive else screen.iconIdInactive,
                                                    ),
                                                contentDescription = null,
                                                tint = if (itemSelected) selectedColor() else unselectedColor(pureBlack),
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(screen.titleId),
                                            maxLines = 1,
                                            style = navLabelStyle(selected),
                                            color = if (selected) selectedColor() else unselectedColor(pureBlack),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 選択中アイコン/ラベルの色。ユーザーが選んだテーマ色（=primary）を優先する。 */
@Composable
private fun selectedColor(): Color = MaterialTheme.colorScheme.primary

/** 未選択アイコン/ラベルの色。Apple Music ほど沈めない 55%。 */
@Composable
private fun unselectedColor(pureBlack: Boolean): Color =
    if (pureBlack) {
        Color.White.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

/** 選択中だけ太くして、Apple Music と同じく選択ラベルが前に出る。 */
@Composable
private fun navLabelStyle(selected: Boolean): TextStyle =
    if (selected) {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.labelMedium
    }

/**
 * ガラス面。下から [base] → ブラー → ティント → 上端ハイライト → ヘアライン縁。
 *
 * [hazeState] が null（またはブラーが使えない）場合は base だけが残るが、
 * そのときも不透明度は 86% あるのでラベルは読める。
 */
@Composable
private fun GlassSurface(
    shape: RoundedCornerShape,
    pureBlack: Boolean,
    hazeState: HazeState?,
    content: @Composable () -> Unit,
) {
    val design = rememberAppleMusicDesign()
    val isDark = pureBlack || MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base =
        if (isDark) {
            Color.Black.copy(alpha = design.glass.fallbackAlpha)
        } else {
            Color.White.copy(alpha = design.glass.fallbackAlpha)
        }
    // ブラーの上に薄くのせるティント。暗い画面では白文字を立たせるため黒系。
    val tint =
        if (isDark) {
            Color.Black.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.22f)
        }
    val edge =
        if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.Black.copy(alpha = 0.10f)
        }
    val highlight =
        if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.85f)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(base),
    ) {
        if (hazeState != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style =
                                HazeBlurStyle {
                                    blurRadius(design.glass.navigationBlurRadius)
                                    backgroundColor(Color.Transparent)
                                    blurredEdgeTreatment(BlurredEdgeTreatment.Rectangle)
                                },
                        ),
            )
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(tint),
        )
        // 上端にだけ差し込む細いハイライト。枠線で囲んだ印象を避けるためグラデーションにする。
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to highlight,
                            0.045f to Color.Transparent,
                            0.5f to Color.Transparent,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .border(width = design.glass.hairline, color = edge, shape = shape),
        )
        content()
    }
}

