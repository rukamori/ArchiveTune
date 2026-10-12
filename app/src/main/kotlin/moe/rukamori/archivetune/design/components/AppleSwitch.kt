/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.applePalette

/**
 * iOSのスイッチ（塗りのカプセル＋白いつまみ）。
 *
 * `Switch` を色だけ差し替えていないのは、部品としての形がMaterialと違うため。
 * ここの意図は次の3点。
 *
 *  - トラックは必ずカプセル。丸と四角の折衷にしない
 *  - つまみは等速で走らせず、ばねで軽く弾ませる
 *  - 切り替えにリップルを出さない（面が滲まない）
 *
 * 見た目は 51x31dp だが、それだけだと押しにくい。実際の操作は原則
 * 行全体で行う（`SettingsRow` が行をトグル可能にする）ので、
 * この部品自体のタップ領域は意図的に広げていない。
 *
 * @param onCheckedChange 変更後の値を返す。
 * @param enabled false なら操作を受け付け、減光する。
 */
@Composable
fun AppleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = applePalette()

    val knobOffset by
        animateDpAsState(
            targetValue = if (checked) 22.dp else 2.dp,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "switchKnob",
        )

    val trackColor =
        if (!enabled) {
            palette.fill
        } else if (checked) {
            palette.accent
        } else {
            palette.fill
        }

    Box(
        modifier =
            modifier
                .size(width = 51.dp, height = 31.dp)
                .clip(CircleShape)
                .background(trackColor)
                .semantics { role = Role.Switch }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = knobOffset)
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(Color.White),
        )
    }
}
