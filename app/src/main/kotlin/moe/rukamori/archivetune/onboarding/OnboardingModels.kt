/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList

sealed interface OnboardingScreenState {
    data object Loading : OnboardingScreenState

    data class Success(
        val uiState: OnboardingUiState,
    ) : OnboardingScreenState

    data object Empty : OnboardingScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : OnboardingScreenState
}

@Immutable
data class OnboardingUiState(
    val shouldShowOnboarding: Boolean,
    val currentPage: Int,
    @StringRes val variantLabelResId: Int,
    val versionName: String,
    val pages: ImmutableList<OnboardingPageUiModel>,
    val permissions: ImmutableList<OnboardingPermissionUiModel>,
    val communityActions: ImmutableList<OnboardingCommunityActionUiModel>,
)

@Immutable
data class OnboardingPageUiModel(
    val id: OnboardingPageId,
    @StringRes val titleResId: Int,
    @StringRes val subtitleResId: Int,
    @DrawableRes val iconResId: Int,
)

enum class OnboardingPageId {
    WELCOME,
    PERMISSIONS,
    COMMUNITY,
}

@Immutable
data class OnboardingPermissionUiModel(
    val id: OnboardingPermissionId,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    @DrawableRes val iconResId: Int,
    val status: OnboardingPermissionStatus,
    val action: OnboardingPermissionAction?,
)

data class OnboardingPermissionData(
    val id: OnboardingPermissionId,
    val status: OnboardingPermissionStatus,
    val action: OnboardingPermissionAction?,
)

enum class OnboardingPermissionId {
    NOTIFICATIONS,
    LOCAL_AUDIO,
    MICROPHONE,
    BLUETOOTH_CONNECT,
    NETWORK,
    PLAYBACK_SERVICE,
    AUDIO_SETTINGS,
    APP_INSTALLATION,
    BLUETOOTH_SCAN,
}

enum class OnboardingPermissionStatus {
    ALLOWED,
    NEEDS_ACTION,
    ALLOWED_BY_INSTALL,
    UNAVAILABLE,
}

sealed interface OnboardingPermissionAction {
    data class RequestRuntimePermission(
        val permission: String,
    ) : OnboardingPermissionAction

    data object OpenInstallPackagesSettings : OnboardingPermissionAction
}

@Immutable
data class OnboardingCommunityActionUiModel(
    val id: String,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    @DrawableRes val iconResId: Int,
    val url: String,
)

data class OnboardingData(
    val shouldShowOnboarding: Boolean,
    val permissions: ImmutableList<OnboardingPermissionData>,
)

sealed interface OnboardingEvent {
    data class RequestPermission(
        val permission: String,
    ) : OnboardingEvent

    data object OpenInstallPackagesSettings : OnboardingEvent

    data class OpenUri(
        val url: String,
    ) : OnboardingEvent
}
