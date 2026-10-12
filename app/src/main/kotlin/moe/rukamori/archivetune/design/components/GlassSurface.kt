/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.design.LocalAppleIsDark
import moe.rukamori.archivetune.design.LocalBackdropHazeState
import moe.rukamori.archivetune.design.appleMetrics

/**
 * Liquid Glass を意識した半透明面。
 *
 * 単に塗りを半透明にするのではなく、**5層**で光沢を組み立てる。
 *
 * 1. 地色（不透明度72〜78%：これがないとラベルが読めない）
 * 2. 背後コンテンツのブラー（[hazeState] があるとき）
 * 3. ティント（ブラーした絵柄を沈める）
 * 4. 上端に差し込むハイライト（枠線で囲んだ印象を避けるためグラデーション）
 * 5. 0.5dp のヘアライン縁
 *
 * [hazeState] が無い環境では 2 が抜けるだけで、1・3・4・5 が残るので
 * どの端末でもアイコンとラベルは読める。
 *
 * @param shape 面の輪郭。カプセルなら `RoundedCornerShape(percent = 50)`。
 * @param hazeState 背後コンテンツの捕捉結果。`null` ならブラーを省略する。
 * @param blurRadius ブラー半径。`0.dp` でブラーを明示的に無効化する。
 * @param tint アートワーク由来の色など、既定のティントに上書きして重ねたい色。
 *   `Color.Unspecified` なら面のモード（暗/明）に応じた既定のティントを使う。
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalBackdropHazeState.current,
    blurRadius: Dp = appleMetrics().glassBlur.dp,
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = LocalAppleIsDark.current
    val metrics = appleMetrics()

    // 1. 地色。暗い画面は黒系、明るい画面は白系。
    val base =
        if (isDark) {
            Color.Black.copy(alpha = 0.72f)
        } else {
            Color.White.copy(alpha = 0.78f)
        }
    // 3. ティント。暗い画面では白文字を立たせるためさらに黒を重ねる。
    val defaultTint =
        if (isDark) {
            Color.Black.copy(alpha = 0.20f)
        } else {
            Color.White.copy(alpha = 0.24f)
        }
    val appliedTint = if (tint == Color.Unspecified) defaultTint else tint
    // 5. ヘアライン縁。
    val edge =
        if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.Black.copy(alpha = 0.10f)
        }
    // 4. 上端ハイライト。
    val highlight =
        if (isDark) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.92f)
        }

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(base),
    ) {
        if (hazeState != null && blurRadius > 0.dp) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style =
                                HazeBlurStyle {
                                    blurRadius(blurRadius)
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
                    .background(appliedTint),
        )

        // 上端にだけ差し込む光。全周を囲む枠線に見えないよう、上端だけ濃くする。
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to highlight,
                            0.05f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1.0f to edge,
                        ),
                    ),
        )

        // 最前面のヘアライン。描画のみでタッチを食わない。
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .border(
                        width = metrics.hairline.dp,
                        brush =
                            Brush.verticalGradient(
                                0.0f to highlight,
                                0.5f to edge,
                                1.0f to edge,
                            ),
                        shape = shape,
                    ),
        )

        content()
    }
}
