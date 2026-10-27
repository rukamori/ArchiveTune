/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.auth

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Immutable
data class MenuProfile(
    val name: String,
    val email: String,
)

class ObserveMenuProfileUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        operator fun invoke(): Flow<MenuProfile?> =
            repository.observeAccountIdentity().map { identity ->
                if (identity.isLoggedIn) {
                    MenuProfile(name = identity.name.trim(), email = identity.email.trim())
                } else {
                    null
                }
            }
    }
