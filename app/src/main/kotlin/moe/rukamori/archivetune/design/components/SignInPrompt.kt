/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LoginViewModel

/**
 * サインインしていないときにだけ、ホームへ差す促し。
 *
 * Apple Music が「サインインしないと聴けない」ことを画面の上で前置きするのと
 * 同じ考えで、**再生できない状態を放置せず、入口を同じ場所に出しておく**。
 * 説明とボタンはこの1枚に畳み、押すと設定のアカウント行と同じ窓
 * ([SignInDialog]) を開く。
 *
 * サインイン済みなら描画せず、高さも取らない。1つのガラス面に収まり、
 * 見た目はリストの他のカードと同じ地に載る。
 *
 * @param modifier 外側の余白。一覧の item から渡す。
 */
@Composable
fun SignInPrompt(
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    val cookie by rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val loginViewModel: LoginViewModel = hiltViewModel()
    val loginState by loginViewModel.screenState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    // ここで返す。上の remember は先に呼んでいるので、状態は失われない。
    if (hasYouTubeLoginCookie(cookie)) return

    GlassSurface(
        shape = RoundedCornerShape(metrics.cardCorner.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.sm.dp,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showDialog = true }
                    .padding(metrics.md.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.youtube_login),
                    style = type.headline,
                    color = palette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.youtube_login_summary),
                    style = type.footnote,
                    color = palette.labelSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(metrics.sm.dp))

            Text(
                text = stringResource(R.string.login),
                style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = palette.accent,
                maxLines = 1,
            )
        }
    }

    if (showDialog) {
        SignInDialog(
            state = loginState,
            onDismiss = { showDialog = false },
            onSubmit = { value -> loginViewModel.onCookiesCaptured(value) },
        )
    }
}
