/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

internal fun shouldSynchronizeAuthenticatedSession(
    previousLoginState: Boolean?,
    isLoggedIn: Boolean,
): Boolean = isLoggedIn && previousLoginState != true
