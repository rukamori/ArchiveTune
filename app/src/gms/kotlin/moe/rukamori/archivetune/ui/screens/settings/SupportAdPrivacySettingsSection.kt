/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ads.presentation.StartIoConsentDialog
import moe.rukamori.archivetune.ads.presentation.SupportArchiveTuneUiEvent
import moe.rukamori.archivetune.ads.presentation.SupportArchiveTuneViewModel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup

@Composable
internal fun SupportAdPrivacySettingsSection(
    onMessage: (String) -> Unit,
    viewModel: SupportArchiveTuneViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val updatedMessage = stringResource(R.string.ad_privacy_updated)
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

    LaunchedEffect(viewModel, onMessage, updatedMessage, activityUnavailableMessage) {
        viewModel.events.collect { event ->
            when (event) {
                SupportArchiveTuneUiEvent.PrivacyOptionsUpdated -> onMessage(updatedMessage)

                SupportArchiveTuneUiEvent.ActivityUnavailable -> onMessage(activityUnavailableMessage)

                SupportArchiveTuneUiEvent.RewardEarned,
                SupportArchiveTuneUiEvent.AdFailed,
                -> Unit
            }
        }
    }

    if (state.model.privacyOptionsRequired) {
        PreferenceGroup(title = stringResource(R.string.ad_privacy_section)) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.ad_privacy_title)) },
                    description = stringResource(R.string.ad_privacy_description),
                    icon = { Icon(painterResource(R.drawable.security), contentDescription = null) },
                    onClick = viewModel::onPrivacyOptionsClick,
                )
            }
        }
    }
}

private const val START_IO_PRIVACY_POLICY_URL = "https://www.start.io/policy/privacy-policy/"
