/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * カプセル型のボタン。
 *
 * Material の ElevatedButton/FilledButton は使わず、背景・角丸・字重を自前で組む。
 * 高さ44dp は触覚目標としても iOS の標準に合わせている。
 *
 * @param filled `true` でアクセント塗り＋白文字、`false` で塗りを抜いて縁だけ残す。
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val shape = RoundedCornerShape(percent = 50)

    val background = if (filled) palette.accent else Color.Transparent
    val contentColor = if (filled) palette.onAccent else palette.accent

    Row(
        modifier =
            modifier
                .height(44.dp)
                .defaultMinSize(minWidth = 96.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .background(background, shape)
                .border(
                    width = metrics.hairline.dp,
                    color = if (filled) Color.Transparent else palette.separator,
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { onClick() }
                .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = type.headline,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * 横幅いっぱいの主ボタン。設定画面の「保存」など、確定系の操作に使う。
 */
@Composable
fun CapsuleButtonWide(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CapsuleButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        filled = true,
    )
}
