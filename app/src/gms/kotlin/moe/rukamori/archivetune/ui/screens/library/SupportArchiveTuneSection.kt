/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ads.presentation.StartIoConsentDialog
import moe.rukamori.archivetune.ads.presentation.SupportArchiveTuneScreenState
import moe.rukamori.archivetune.ads.presentation.SupportArchiveTuneUiEvent
import moe.rukamori.archivetune.ads.presentation.SupportArchiveTuneViewModel

internal const val supportArchiveTuneAvailable = true

@Composable
internal fun SupportArchiveTuneSection(
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit,
    viewModel: SupportArchiveTuneViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val rewardMessage = stringResource(R.string.support_archivetune_thanks)
    val failureMessage = stringResource(R.string.support_archivetune_failed)
    val activityUnavailableMessage = stringResource(R.string.support_archivetune_activity_unavailable)
    val uriHandler = LocalUriHandler.current
    val openPrivacyPolicy = remember(uriHandler) { { uriHandler.openUri(START_IO_PRIVACY_POLICY_URL) } }
    val selectPersonalizedAds =
        remember(viewModel) { { viewModel.onConsentSelected(personalized = true) } }
    val selectNonPersonalizedAds =
        remember(viewModel) { { viewModel.onConsentSelected(personalized = false) } }

    if (state.model.consentDialogPurpose != null) {
        StartIoConsentDialog(
            onPersonalizedAdsSelected = selectPersonalizedAds,
            onNonPersonalizedAdsSelected = selectNonPersonalizedAds,
            onPrivacyPolicyClick = openPrivacyPolicy,
            onDismiss = viewModel::onConsentDialogDismissed,
        )
    }

    LaunchedEffect(viewModel, onMessage, rewardMessage, failureMessage, activityUnavailableMessage) {
        viewModel.events.collect { event ->
            when (event) {
                SupportArchiveTuneUiEvent.RewardEarned -> onMessage(rewardMessage)
                SupportArchiveTuneUiEvent.AdFailed -> onMessage(failureMessage)
                SupportArchiveTuneUiEvent.ActivityUnavailable -> onMessage(activityUnavailableMessage)
                SupportArchiveTuneUiEvent.PrivacyOptionsUpdated -> Unit
            }
        }
    }

    SupportArchiveTuneCard(
        state = state,
        onClick = viewModel::onSupportArchiveTuneClick,
        modifier = modifier,
    )
}

private const val START_IO_PRIVACY_POLICY_URL = "https://www.start.io/policy/privacy-policy/"

@Composable
private fun SupportArchiveTuneCard(
    state: SupportArchiveTuneScreenState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val iconContainerModifier =
        remember(iconContainerColor) {
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(iconContainerColor)
        }
    val description =
        when (state) {
            is SupportArchiveTuneScreenState.Loading -> {
                stringResource(R.string.support_archivetune_preparing)
            }

            is SupportArchiveTuneScreenState.Success -> {
                stringResource(R.string.support_archivetune_description)
            }

            is SupportArchiveTuneScreenState.Empty -> {
                stringResource(R.string.support_archivetune_unavailable)
            }

            is SupportArchiveTuneScreenState.Error -> {
                stringResource(R.string.support_archivetune_retry)
            }
        }

    Card(
        onClick = onClick,
        enabled = state !is SupportArchiveTuneScreenState.Empty,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor.copy(alpha = 0.7f),
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = iconContainerModifier,
            ) {
                Icon(
                    painter = painterResource(R.drawable.star),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(34.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.support_archivetune_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp),
            ) {
                if (state is SupportArchiveTuneScreenState.Loading) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
