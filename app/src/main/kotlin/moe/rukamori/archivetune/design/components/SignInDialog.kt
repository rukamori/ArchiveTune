/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.viewmodels.LoginScreenState

/**
 * cookie を貼り付けて YouTube にログインする窓。
 *
 * ウェブ画面は開かない。貼り付けた値だけを [moe.rukamori.archivetune.viewmodels.LoginViewModel]
 * へ渡し、結果（読み込み・失敗）を中に残す。成功したら自動で閉じる。
 *
 * 入口は複数ある（設定のアカウント行、ホームの促し）が、窓はこの1つに絞る。
 *
 * @param state [moe.rukamori.archivetune.viewmodels.LoginViewModel] の現在値。
 * @param onSubmit cookie を切り出す。空文字は下のボタンで弾いている。
 */
@Composable
fun SignInDialog(
    state: LoginScreenState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    var cookie by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    // 通りたら手を離す。失敗は閉じずに理由を残す。
    LaunchedEffect(state) {
        if (state is LoginScreenState.Success) onDismiss()
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.youtube_login), style = type.title2) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel_button),
                    color = palette.accent,
                )
            }
            TextButton(
                onClick = {
                    submitted = true
                    onSubmit(cookie.trim())
                },
                enabled = cookie.isNotBlank() && state !is LoginScreenState.Loading,
            ) {
                Text(
                    text = stringResource(R.string.login),
                    color = palette.accent,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.youtube_login_body),
            style = type.footnote,
            color = palette.labelSecondary,
        )

        Spacer(Modifier.height(metrics.sm.dp))

        OutlinedTextField(
            value = cookie,
            onValueChange = { cookie = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("SID=...; HSID=...; ...", style = type.footnote) },
            textStyle = type.footnote,
            minLines = 4,
            maxLines = 6,
        )

        if (submitted) {
            Spacer(Modifier.height(metrics.sm.dp))
            when (state) {
                is LoginScreenState.Loading -> {
                    Text(
                        text = stringResource(R.string.loading),
                        style = type.footnote,
                        color = palette.labelSecondary,
                    )
                }

                is LoginScreenState.Error -> {
                    Text(
                        text = stringResource(R.string.youtube_login_failed),
                        style = type.footnote,
                        color = palette.accent,
                    )
                }

                else -> Unit
            }
        }
    }
}
