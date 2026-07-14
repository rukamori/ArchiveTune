package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ads.presentation.SupportArchiveTuneScreenState
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
    val failureMessage = stringResource(R.string.ad_privacy_failed)
    val activityUnavailableMessage = stringResource(R.string.support_archivetune_activity_unavailable)

    LaunchedEffect(viewModel, onMessage, updatedMessage, failureMessage, activityUnavailableMessage) {
        viewModel.events.collect { event ->
            when (event) {
                SupportArchiveTuneUiEvent.PrivacyOptionsUpdated -> onMessage(updatedMessage)
                SupportArchiveTuneUiEvent.PrivacyOptionsFailed -> onMessage(failureMessage)
                SupportArchiveTuneUiEvent.ActivityUnavailable -> onMessage(activityUnavailableMessage)
                SupportArchiveTuneUiEvent.RewardEarned,
                SupportArchiveTuneUiEvent.AdFailed,
                -> Unit
            }
        }
    }

    val privacyOptionsRequired =
        when (state) {
            is SupportArchiveTuneScreenState.Loading -> state.privacyOptionsRequired
            is SupportArchiveTuneScreenState.Success -> state.model.privacyOptionsRequired
            is SupportArchiveTuneScreenState.Empty -> state.privacyOptionsRequired
            is SupportArchiveTuneScreenState.Error -> state.privacyOptionsRequired
        }
    if (privacyOptionsRequired) {
        PreferenceGroup(title = stringResource(R.string.ad_privacy_section)) {
            item(key = "ad_privacy_options") {
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
