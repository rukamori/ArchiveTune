/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ads.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@Composable
internal fun StartIoConsentDialog(
    onPersonalizedAdsSelected: () -> Unit,
    onNonPersonalizedAdsSelected: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_io_privacy_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.start_io_privacy_description))
                TextButton(
                    onClick = onPrivacyPolicyClick,
                ) {
                    Text(stringResource(R.string.start_io_privacy_policy))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onPersonalizedAdsSelected,
            ) {
                Text(stringResource(R.string.start_io_personalized_ads))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onNonPersonalizedAdsSelected,
            ) {
                Text(stringResource(R.string.start_io_non_personalized_ads))
            }
        },
    )
}
