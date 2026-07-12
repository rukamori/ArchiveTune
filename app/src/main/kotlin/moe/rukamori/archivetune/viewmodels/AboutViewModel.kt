/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.about.AboutContributor
import moe.rukamori.archivetune.about.AboutContributorCollection
import moe.rukamori.archivetune.about.AboutDependencyLicense
import moe.rukamori.archivetune.about.AboutDependencyLicenseCollection
import moe.rukamori.archivetune.about.AboutTranslationContributor
import moe.rukamori.archivetune.about.AboutTranslationContributorCollection
import moe.rukamori.archivetune.about.FetchAboutContributorsUseCase
import moe.rukamori.archivetune.about.FetchAboutDependencyLicensesUseCase
import moe.rukamori.archivetune.about.FetchAboutTranslationContributorsUseCase
import moe.rukamori.archivetune.currentBuildHash
import javax.inject.Inject

sealed interface AboutScreenState {
    data object Loading : AboutScreenState

    data class Success(
        val model: AboutUiModel,
    ) : AboutScreenState

    data object Empty : AboutScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutScreenState
}

@Immutable
data class AboutUiModel(
    @StringRes val appNameResId: Int,
    val versionName: String,
    val buildHash: String?,
    val buildVariant: String,
    val primaryLinks: AboutLinkCollection,
    val leadDeveloper: TeamMember,
    val collaborators: TeamMemberCollection,
    val respecters: TeamMemberCollection,
    val contributorsState: AboutContributorsUiState,
    val contributorsReadMoreUrl: String,
    val isOverflowMenuExpanded: Boolean,
    val activeDialog: AboutDialog,
    val translationContributorsState: AboutTranslationContributorsUiState,
    val dependencyLicensesState: AboutDependencyLicensesUiState,
)

@Immutable
data class TeamMember(
    val avatarUrl: String,
    val name: String,
    @StringRes val positionResId: Int,
    val profileUrl: String?,
    val links: AboutLinkCollection,
)

@Immutable
data class TeamMemberCollection private constructor(
    private val values: List<TeamMember>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): TeamMember = values[index]

    companion object {
        fun of(vararg values: TeamMember): TeamMemberCollection = TeamMemberCollection(values.toList())
    }
}

@Immutable
data class AboutLinkUiModel(
    val id: String,
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int,
    val url: String,
)

@Immutable
data class AboutLinkCollection private constructor(
    private val values: List<AboutLinkUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): AboutLinkUiModel = values[index]

    fun forEach(action: (AboutLinkUiModel) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutLinkCollection(emptyList())

        fun of(vararg values: AboutLinkUiModel): AboutLinkCollection = AboutLinkCollection(values.toList())
    }
}

sealed interface AboutContributorsUiState {
    data object Loading : AboutContributorsUiState

    data class Success(
        val contributors: AboutContributorUiCollection,
    ) : AboutContributorsUiState

    data object Empty : AboutContributorsUiState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutContributorsUiState
}

enum class AboutDialog {
    NONE,
    TRANSLATION_CONTRIBUTORS,
    DEPENDENCY_LICENSES,
}

sealed interface AboutTranslationContributorsUiState {
    data object Loading : AboutTranslationContributorsUiState

    data class Success(
        val contributors: AboutTranslationContributorUiCollection,
    ) : AboutTranslationContributorsUiState

    data object Empty : AboutTranslationContributorsUiState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutTranslationContributorsUiState
}

@Immutable
data class AboutTranslationContributorUiModel(
    val language: String,
    val contributors: String?,
)

@Immutable
data class AboutTranslationContributorUiCollection private constructor(
    private val values: List<AboutTranslationContributorUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutTranslationContributorUiModel = values[index]

    companion object {
        fun from(values: List<AboutTranslationContributorUiModel>): AboutTranslationContributorUiCollection =
            AboutTranslationContributorUiCollection(values.toList())
    }
}

sealed interface AboutDependencyLicensesUiState {
    data object Loading : AboutDependencyLicensesUiState

    data class Success(
        val licenses: AboutDependencyLicenseUiCollection,
    ) : AboutDependencyLicensesUiState

    data object Empty : AboutDependencyLicensesUiState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutDependencyLicensesUiState
}

@Immutable
data class AboutDependencyLicenseUiModel(
    val name: String,
    val version: String?,
    val licenses: String?,
)

@Immutable
data class AboutDependencyLicenseUiCollection private constructor(
    private val values: List<AboutDependencyLicenseUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutDependencyLicenseUiModel = values[index]

    companion object {
        fun from(values: List<AboutDependencyLicenseUiModel>): AboutDependencyLicenseUiCollection =
            AboutDependencyLicenseUiCollection(values.toList())
    }
}

@Immutable
data class AboutContributorUiModel(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

@Immutable
data class AboutContributorUiCollection private constructor(
    private val values: List<AboutContributorUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): AboutContributorUiModel = values[index]

    fun forEach(action: (AboutContributorUiModel) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutContributorUiCollection(emptyList())

        fun from(values: List<AboutContributorUiModel>): AboutContributorUiCollection = AboutContributorUiCollection(values.toList())
    }
}

sealed interface AboutScreenEffect {
    data class OpenUri(
        val uri: String,
    ) : AboutScreenEffect
}

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val fetchAboutContributors: FetchAboutContributorsUseCase,
        private val fetchTranslationContributors: FetchAboutTranslationContributorsUseCase,
        private val fetchDependencyLicenses: FetchAboutDependencyLicensesUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<AboutScreenState>(AboutScreenState.Loading)
        val state: StateFlow<AboutScreenState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<AboutScreenEffect>(extraBufferCapacity = 1)
        val effects = _effects.asSharedFlow()

        private var contributorsJob: Job? = null
        private var translationContributorsJob: Job? = null
        private var dependencyLicensesJob: Job? = null
        private var contributorsState: AboutContributorsUiState = AboutContributorsUiState.Loading
        private var translationContributorsState: AboutTranslationContributorsUiState =
            AboutTranslationContributorsUiState.Loading
        private var dependencyLicensesState: AboutDependencyLicensesUiState = AboutDependencyLicensesUiState.Loading
        private var isOverflowMenuExpanded = false
        private var activeDialog = AboutDialog.NONE

        init {
            loadContributors()
        }

        fun retryContributors() {
            loadContributors(force = true)
        }

        fun showOverflowMenu() {
            isOverflowMenuExpanded = true
            updateState()
        }

        fun dismissOverflowMenu() {
            isOverflowMenuExpanded = false
            updateState()
        }

        fun openTranslationContributors() {
            isOverflowMenuExpanded = false
            activeDialog = AboutDialog.TRANSLATION_CONTRIBUTORS
            updateState()
            loadTranslationContributors()
        }

        fun openDependencyLicenses() {
            isOverflowMenuExpanded = false
            activeDialog = AboutDialog.DEPENDENCY_LICENSES
            updateState()
            loadDependencyLicenses()
        }

        fun dismissDialog() {
            activeDialog = AboutDialog.NONE
            updateState()
        }

        fun retryTranslationContributors() {
            loadTranslationContributors(force = true)
        }

        fun retryDependencyLicenses() {
            loadDependencyLicenses(force = true)
        }

        fun openUri(uri: String) {
            if (uri.isBlank()) return
            _effects.tryEmit(AboutScreenEffect.OpenUri(uri))
        }

        private fun loadContributors(force: Boolean = false) {
            if (!force && contributorsJob?.isActive == true) return
            contributorsJob?.cancel()
            contributorsState = AboutContributorsUiState.Loading
            updateState()
            contributorsJob =
                viewModelScope.launch(Dispatchers.IO) {
                    contributorsState =
                        try {
                            fetchAboutContributors()
                                .fold(
                                    onSuccess = { contributors ->
                                        val contributorUiModels =
                                            contributors
                                                .take(MaxDisplayedContributors)
                                                .toUiCollection()
                                        if (contributorUiModels.isEmpty) {
                                            AboutContributorsUiState.Empty
                                        } else {
                                            AboutContributorsUiState.Success(contributorUiModels)
                                        }
                                    },
                                    onFailure = {
                                        AboutContributorsUiState.Error(R.string.error_unknown)
                                    },
                                )
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            AboutContributorsUiState.Error(R.string.error_unknown)
                        }
                    updateState()
                }
        }

        private fun loadTranslationContributors(force: Boolean = false) {
            if (!force && translationContributorsJob?.isActive == true) return
            if (!force && translationContributorsState is AboutTranslationContributorsUiState.Success) return
            translationContributorsJob?.cancel()
            translationContributorsState = AboutTranslationContributorsUiState.Loading
            updateState()
            translationContributorsJob =
                viewModelScope.launch(Dispatchers.IO) {
                    translationContributorsState =
                        try {
                            fetchTranslationContributors()
                                .fold(
                                    onSuccess = { contributors ->
                                        val contributorUiModels = contributors.toUiCollection()
                                        if (contributorUiModels.isEmpty) {
                                            AboutTranslationContributorsUiState.Empty
                                        } else {
                                            AboutTranslationContributorsUiState.Success(contributorUiModels)
                                        }
                                    },
                                    onFailure = {
                                        AboutTranslationContributorsUiState.Error(R.string.error_unknown)
                                    },
                                )
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            AboutTranslationContributorsUiState.Error(R.string.error_unknown)
                        }
                    updateState()
                }
        }

        private fun loadDependencyLicenses(force: Boolean = false) {
            if (!force && dependencyLicensesJob?.isActive == true) return
            if (!force && dependencyLicensesState is AboutDependencyLicensesUiState.Success) return
            dependencyLicensesJob?.cancel()
            dependencyLicensesState = AboutDependencyLicensesUiState.Loading
            updateState()
            dependencyLicensesJob =
                viewModelScope.launch(Dispatchers.IO) {
                    dependencyLicensesState =
                        try {
                            fetchDependencyLicenses()
                                .fold(
                                    onSuccess = { licenses ->
                                        val licenseUiModels = licenses.toUiCollection()
                                        if (licenseUiModels.isEmpty) {
                                            AboutDependencyLicensesUiState.Empty
                                        } else {
                                            AboutDependencyLicensesUiState.Success(licenseUiModels)
                                        }
                                    },
                                    onFailure = {
                                        AboutDependencyLicensesUiState.Error(R.string.error_unknown)
                                    },
                                )
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            AboutDependencyLicensesUiState.Error(R.string.error_unknown)
                        }
                    updateState()
                }
        }

        private fun updateState() {
            _state.value = AboutScreenState.Success(buildUiModel())
        }

        private fun buildUiModel(): AboutUiModel =
            AboutUiModel(
                appNameResId = R.string.app_name,
                versionName = BuildConfig.VERSION_NAME,
                buildHash = currentBuildHash,
                buildVariant = if (BuildConfig.DEBUG) DebugBuildBadge else BuildConfig.ARCHITECTURE.uppercase(),
                primaryLinks =
                    AboutLinkCollection.of(
                        AboutLinkUiModel(
                            id = "github",
                            iconResId = R.drawable.github,
                            labelResId = R.string.about_content_desc_github,
                            url = "https://github.com/rukamori/ArchiveTune",
                        ),
                        AboutLinkUiModel(
                            id = "website",
                            iconResId = R.drawable.website,
                            labelResId = R.string.about_content_desc_website,
                            url = "https://archivetune.koiiverse.cloud",
                        ),
                        AboutLinkUiModel(
                            id = "telegram",
                            iconResId = R.drawable.telegram,
                            labelResId = R.string.about_content_desc_telegram,
                            url = "https://t.me/ArchiveTuneGC",
                        ),
                        AboutLinkUiModel(
                            id = "donate",
                            iconResId = R.drawable.coffee,
                            labelResId = R.string.about_content_desc_donate,
                            url = "https://koiiverse.cloud/donate",
                        ),
                        AboutLinkUiModel(
                            id = "discord",
                            iconResId = R.drawable.discord,
                            labelResId = R.string.discord,
                            url = "https://discord.gg/XF2fpb9rTq",
                        ),
                        AboutLinkUiModel(
                            id = "privacy_policy",
                            iconResId = R.drawable.lock,
                            labelResId = R.string.privacy,
                            url = "https://archivetune.koiiverse.cloud/privacy",
                        ),
                    ),
                leadDeveloper =
                    TeamMember(
                        avatarUrl = "https://avatars.githubusercontent.com/u/107134739?v=4",
                        name = "morie",
                        positionResId = R.string.about_position_lead_dev,
                        profileUrl = "https://github.com/rukamori",
                        links =
                            AboutLinkCollection.of(
                                AboutLinkUiModel(
                                    id = "github",
                                    iconResId = R.drawable.github,
                                    labelResId = R.string.about_content_desc_github,
                                    url = "https://github.com/rukamori",
                                ),
                                AboutLinkUiModel(
                                    id = "website",
                                    iconResId = R.drawable.website,
                                    labelResId = R.string.about_content_desc_website,
                                    url = "https://koiiverse.cloud",
                                ),
                                AboutLinkUiModel(
                                    id = "discord",
                                    iconResId = R.drawable.alternate_email,
                                    labelResId = R.string.about_content_desc_discord,
                                    url = "https://discord.com/users/886971572668219392",
                                ),
                            ),
                    ),
                collaborators =
                    TeamMemberCollection.of(
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/89002922?v=4",
                            name = "Miko",
                            positionResId = R.string.about_position_developers,
                            profileUrl = "https://github.com/mikooochi",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/mikooochi",
                                    ),
                                ),
                        ),
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/93458424?v=4",
                            name = "WTTexe",
                            positionResId = R.string.about_position_developers,
                            profileUrl = "https://github.com/Windowstechtips",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/Windowstechtips",
                                    ),
                                    AboutLinkUiModel(
                                        id = "discord",
                                        iconResId = R.drawable.alternate_email,
                                        labelResId = R.string.about_content_desc_discord,
                                        url = "https://discord.com/users/840839409640800258",
                                    ),
                                ),
                        ),
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/203143605?v=4",
                            name = "Yuki/Reze",
                            positionResId = R.string.about_position_yuki,
                            profileUrl = "https://github.com/4nx3b",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/4nx3b",
                                    ),
                                ),
                        ),
                    ),
                respecters =
                    TeamMemberCollection.of(
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/80542861?v=4",
                            name = "MO AGAMY",
                            positionResId = R.string.about_position_mo_agamy,
                            profileUrl = "https://github.com/mostafaalagamy",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/mostafaalagamy",
                                    ),
                                ),
                        ),
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/110614797?v=4",
                            name = "Zion Huang",
                            positionResId = R.string.about_position_zion_huang,
                            profileUrl = "https://github.com/z-huang",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/z-huang",
                                    ),
                                ),
                        ),
                    ),
                contributorsState = contributorsState,
                contributorsReadMoreUrl = ContributorsReadMoreUrl,
                isOverflowMenuExpanded = isOverflowMenuExpanded,
                activeDialog = activeDialog,
                translationContributorsState = translationContributorsState,
                dependencyLicensesState = dependencyLicensesState,
            )

        private fun AboutContributorCollection.toUiCollection(): AboutContributorUiCollection {
            val contributors = ArrayList<AboutContributorUiModel>(MaxDisplayedContributors)
            forEach { contributor ->
                contributors.add(contributor.toUiModel())
            }
            return AboutContributorUiCollection.from(contributors)
        }

        private fun AboutContributor.toUiModel(): AboutContributorUiModel =
            AboutContributorUiModel(
                login = login,
                avatarUrl = avatarUrl,
                profileUrl = profileUrl,
            )

        private fun AboutTranslationContributorCollection.toUiCollection(): AboutTranslationContributorUiCollection {
            val contributors = ArrayList<AboutTranslationContributorUiModel>(size)
            for (index in 0 until size) {
                contributors.add(this[index].toUiModel())
            }
            return AboutTranslationContributorUiCollection.from(contributors)
        }

        private fun AboutTranslationContributor.toUiModel(): AboutTranslationContributorUiModel =
            AboutTranslationContributorUiModel(
                language = language,
                contributors = contributors.joinToString().takeIf(String::isNotBlank),
            )

        private fun AboutDependencyLicenseCollection.toUiCollection(): AboutDependencyLicenseUiCollection {
            val licenses = ArrayList<AboutDependencyLicenseUiModel>(size)
            for (index in 0 until size) {
                licenses.add(this[index].toUiModel())
            }
            return AboutDependencyLicenseUiCollection.from(licenses)
        }

        private fun AboutDependencyLicense.toUiModel(): AboutDependencyLicenseUiModel =
            AboutDependencyLicenseUiModel(
                name = name,
                version = version,
                licenses = licenses,
            )

        private companion object {
            const val MaxDisplayedContributors = 20
            const val DebugBuildBadge = "DEBUG"
            const val ContributorsReadMoreUrl = "https://github.com/rukamori/ArchiveTune/graphs/contributors"
        }
    }
