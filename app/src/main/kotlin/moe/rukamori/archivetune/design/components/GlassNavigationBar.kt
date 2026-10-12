/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * ボトムナビの1タブ。
 *
 * [iconInactive] と [iconActive] は同じ絵でもよい（選択の強調は色と字重が主役）。
 */
@Immutable
data class NavigationTab(
    @StringRes val labelId: Int,
    @DrawableRes val iconInactive: Int,
    @DrawableRes val iconActive: Int,
    val route: String,
)

/**
 * 浮遊カプセル型のボトムナビゲーション。
 *
 * 背後の画面を [dev.chrisbanes.haze.HazeState] で捕捉して実際にブラーする
 * 「ガラス」の面を使う。透明度を下げただけの帯にはしない。
 *
 * 選択中は [NavigationTab] と逆、アクセント色＋SemiBold で立ちたてる。
 * 非選択は二次ラベル色に落とし、幅の差は付けない（Apple Music 同様、レイアウトを崩さない）。
 *
 * @param selectedRoute 現在選択されているルート。`null` で全タブ非選択。
 * @param onSelect タブが押されたとき。既に選択中のタブが再押された回数は数えない側で持つ。
 */
@Composable
fun GlassNavigationBar(
    tabs: List<NavigationTab>,
    selectedRoute: String?,
    onSelect: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                // ジェスチャバーのすぐ上に浮かせるためのわずかな隙間。
                .padding(bottom = metrics.xs.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .height(metrics.navBarHeight.dp),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            GlassSurface(shape = shape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        TabItem(
                            tab = tab,
                            selected = tab.route == selectedRoute,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: NavigationTab,
    selected: Boolean,
    onSelect: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()
    val type = appleType()
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (selected) palette.accent else palette.labelSecondary

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onSelect(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(if (selected) tab.iconActive else tab.iconInactive),
                contentDescription = stringResource(tab.labelId),
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(tab.labelId),
                style =
                    if (selected) {
                        type.micro.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        type.micro
                    },
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
