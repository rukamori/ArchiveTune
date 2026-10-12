/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 一覧が空／未取得のときの空状態。
 *
 * 「何も無い」ことと「読み込み中」を混同しないよう、`LoadingState` とは分けてある。
 * ここがダイミーのカードになってはいけない。実データが本当に0件のときにだけ使う。
 *
 * @param actionLabel 指定すると [CapsuleButton] を表示する。
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.gutter.dp * 2, vertical = metrics.block.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.height(metrics.gutter.dp))
            }
            Text(
                text = title,
                style = type.title2,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(metrics.xs.dp))
            Text(
                text = message,
                style = type.callout,
                color = palette.labelSecondary,
                textAlign = TextAlign.Center,
                maxLines = 4,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(metrics.gutter.dp))
                CapsuleButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * 読み込み中。[EmptyState] と区別するための専用状態。
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val palette = applePalette()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(appleMetrics().block.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = palette.accent,
            strokeWidth = 2.5.dp,
        )
    }
}

/**
 * 取得に失敗したときの状態。再試行の導線を持たせる。
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()
    val type = appleType()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = appleMetrics().gutter.dp * 2, vertical = appleMetrics().block.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = palette.labelTertiary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(appleMetrics().gutter.dp))
            Text(
                text = title,
                style = type.title3,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(appleMetrics().xs.dp))
            Text(
                text = message,
                style = type.callout,
                color = palette.labelSecondary,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(appleMetrics().gutter.dp))
                CapsuleButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    filled = false,
                )
            }
        }
    }
}
