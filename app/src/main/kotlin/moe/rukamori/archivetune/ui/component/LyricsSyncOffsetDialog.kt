/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LyricsSyncOffsetLimitMs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSyncOffsetDialog(
    initialOffsetMs: Int,
    supportingText: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val limit = LyricsSyncOffsetLimitMs.toFloat()
    var tempOffset by rememberSaveable { mutableFloatStateOf(initialOffsetMs.toFloat().coerceIn(-limit, limit)) }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            Icon(painter = painterResource(R.drawable.speed), contentDescription = null)
        },
        title = { Text(stringResource(R.string.lyrics_sync_offset)) },
        buttons = {
            TextButton(
                onClick = { tempOffset = 0f },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onConfirm(tempOffset.roundToInt().coerceIn(-LyricsSyncOffsetLimitMs, LyricsSyncOffsetLimitMs))
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = formatLyricsSyncOffset(tempOffset.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = if (supportingText != null) 4.dp else 16.dp),
            )

            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            Slider(
                value = tempOffset,
                onValueChange = { tempOffset = it },
                valueRange = -limit..limit,
                steps = (LyricsSyncOffsetLimitMs * 2 / 50) - 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun formatLyricsSyncOffset(offsetMs: Int): String = if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms"
