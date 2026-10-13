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
 * 1. 地色 ＝ 常に敷く下敷き。ブラーが空でもこれが残るので必ず読める
 * 2. 背後コンテンツのブラー（[hazeState] があるとき）
 * 3. スクリム ＝ 「透けかえ」を決める層。ブラーした背後を3〜4割沈め、
 *    絵は**うっすら透ける**程度に落とす。文字はこの面の上に乗る
 * 4. 上端に差し込むハイライト（枠線で囲んだ印象を避けるためグラデーション）
 * 5. 0.5dp のヘアライン縁
 *
 * 2 が効く環境では地色はブラーに隠れ、背後の絵だけが透ける。2 が空でも 1 が
 * 残るので、どちらの環境でも「下に何かが沈んでいる」面に見える。
 *
 * @param shape 面の輪郭。カプセルなら `RoundedCornerShape(percent = 50)`。
 * @param hazeState 背後コンテンツの捕捉結果。`null` ならブラーを省略する。
 * @param blurRadius ブラー半径。`0.dp` でブラーを明示的に無効化する。
 * @param tint スクリム／ティントの色。`Color.Unspecified` なら暗/明に応じた
 *   既定の値を使う。ブラーが無い環境では地色の**上に**重ねる。
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

    // ブラーが効くかどうか。`hazeState` はローカル変数を経由しないと
    // スマートキャストできないため、ここで一度受けている。
    val haze = hazeState
    val hasBlur = haze != null && blurRadius > 0.dp
    // 1. 地色。
    val base =
        if (isDark) {
            Color.Black.copy(alpha = 0.72f)
        } else {
            Color.White.copy(alpha = 0.78f)
        }
    // 3. スクリム。ブラーした背後をどこまで見せるかをここで決める。
    // 3〜4割沈めると絵は「うっすら透ける」だけになり、ラベルは常に読める。
    val defaultTint =
        if (isDark) {
            Color.Black.copy(alpha = 0.32f)
        } else {
            Color.White.copy(alpha = 0.40f)
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
                // 1. 地色。いつも敷いておく。ブラーが空のとき（背後が未捕捉）に
                //    これがないと、スクリムだけの半透明面になって文字が読めない。
                .background(base),
    ) {
        if (hasBlur) {
            // 2. 背後コンテンツのブラー。地色の上に重なる。
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .hazeBlur(
                            input = HazeInput.Sources(haze),
                            style =
                                HazeBlurStyle {
                                    blurRadius(blurRadius)
                                    backgroundColor(Color.Transparent)
                                    blurredEdgeTreatment(BlurredEdgeTreatment.Rectangle)
                                },
                        ),
            )

            // 3. スクリム。透けかえを決める。
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(appliedTint),
            )
        } else if (tint != Color.Unspecified) {
            // 3. ティント。ブラーが無い環境でも、指定があれば地色の上に重ねる。
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(tint),
            )
        }

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
