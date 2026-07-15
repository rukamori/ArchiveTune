/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.gatekeeper

import javax.inject.Inject

class RunGatekeeperCheckUseCase
    @Inject
    constructor(
        private val repository: GatekeeperRepository,
    ) {
        suspend operator fun invoke(): GatekeeperResult = repository.checkAccess()
    }
