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
import moe.rukamori.archivetune.about.FetchAboutContributorsUseCase
import moe.rukamori.archivetune.currentBuildHash
import javax.inject.Inject

sealed interface AboutScreenState {
    data object Loading : AboutScreenState
    data class Success(val model: AboutUiModel) : AboutScreenState
    data object Empty : AboutScreenState
    data class Error(@StringRes val messageResId: Int) : AboutScreenState
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
        fun of(vararg values: TeamMember): TeamMemberCollection =
            TeamMemberCollection(values.toList())
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

        fun of(vararg values: AboutLinkUiModel): AboutLinkCollection =
            AboutLinkCollection(values.toList())
    }
}

sealed interface AboutContributorsUiState {
    data object Loading : AboutContributorsUiState
    data class Success(val contributors: AboutContributorUiCollection) : AboutContributorsUiState
    data object Empty : AboutContributorsUiState
    data class Error(@StringRes val messageResId: Int) : AboutContributorsUiState
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

        fun from(values: List<AboutContributorUiModel>): AboutContributorUiCollection =
            AboutContributorUiCollection(values.toList())
    }
}

sealed interface AboutScreenEffect {
    data class OpenUri(val uri: String) : AboutScreenEffect
}

@HiltViewModel
class AboutViewModel
@Inject
constructor(
    private val fetchAboutContributors: FetchAboutContributorsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<AboutScreenState>(AboutScreenState.Loading)
    val state: StateFlow<AboutScreenState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AboutScreenEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var contributorsJob: Job? = null

    init {
        loadContributors()
    }

    fun retryContributors() {
        loadContributors(force = true)
    }

    fun openUri(uri: String) {
        if (uri.isBlank()) return
        _effects.tryEmit(AboutScreenEffect.OpenUri(uri))
    }

    private fun loadContributors(force: Boolean = false) {
        if (!force && contributorsJob?.isActive == true) return
        contributorsJob?.cancel()
        _state.value = AboutScreenState.Success(buildUiModel(AboutContributorsUiState.Loading))
        contributorsJob = viewModelScope.launch(Dispatchers.IO) {
            val contributorsState =
                try {
                    fetchAboutContributors()
                        .fold(
                            onSuccess = { contributors ->
                                val contributorUiModels = contributors
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
            _state.value = AboutScreenState.Success(buildUiModel(contributorsState))
        }
    }

    private fun buildUiModel(
        contributorsState: AboutContributorsUiState,
    ): AboutUiModel =
        AboutUiModel(
            appNameResId = R.string.app_name,
            versionName = BuildConfig.VERSION_NAME,
            buildHash = currentBuildHash,
            buildVariant = if (BuildConfig.DEBUG) DebugBuildBadge else BuildConfig.ARCHITECTURE.uppercase(),
            primaryLinks = AboutLinkCollection.of(
                AboutLinkUiModel(
                    id = "github",
                    iconResId = R.drawable.github,
                    labelResId = R.string.about_content_desc_github,
                    url = "https://github.com/ArchiveTuneApp/ArchiveTune",
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
                    url = "https://sociabuzz.com/chrtrxwstia",
                ),
            ),
            leadDeveloper = TeamMember(
                avatarUrl = "https://avatars.githubusercontent.com/u/107134739?v=4",
                name = "morieeattonkatsu",
                positionResId = R.string.about_position_lead_dev,
                profileUrl = "https://github.com/rukamori",
                links = AboutLinkCollection.of(
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
            collaborators = TeamMemberCollection.of(
                TeamMember(
                    avatarUrl = "https://avatars.githubusercontent.com/u/93458424?v=4",
                    name = "WTTexe",
                    positionResId = R.string.about_position_wttexe,
                    profileUrl = "https://github.com/Windowstechtips",
                    links = AboutLinkCollection.of(
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
                    avatarUrl = "https://avatars.githubusercontent.com/u/89002922?v=4",
                    name = "Miko",
                    positionResId = R.string.about_position_miko,
                    profileUrl = "https://github.com/mikooochi",
                    links = AboutLinkCollection.of(
                        AboutLinkUiModel(
                            id = "github",
                            iconResId = R.drawable.github,
                            labelResId = R.string.about_content_desc_github,
                            url = "https://github.com/mikooochi",
                        ),
                    ),
                ),
                TeamMember(
                    avatarUrl = "https://avatars.githubusercontent.com/u/80249864?v=4",
                    name = "sang765",
                    positionResId = R.string.about_position_sang765,
                    profileUrl = "https://github.com/sang765",
                    links = AboutLinkCollection.of(
                        AboutLinkUiModel(
                            id = "github",
                            iconResId = R.drawable.github,
                            labelResId = R.string.about_content_desc_github,
                            url = "https://github.com/sang765",
                        ),
                    ),
                ),
            ),
            respecters = TeamMemberCollection.of(
                TeamMember(
                    avatarUrl = "https://avatars.githubusercontent.com/u/80542861?v=4",
                    name = "MO AGAMY",
                    positionResId = R.string.about_position_mo_agamy,
                    profileUrl = "https://github.com/mostafaalagamy",
                    links = AboutLinkCollection.of(
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
                    links = AboutLinkCollection.of(
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

    private companion object {
        const val MaxDisplayedContributors = 20
        const val DebugBuildBadge = "DEBUG"
    }
}
