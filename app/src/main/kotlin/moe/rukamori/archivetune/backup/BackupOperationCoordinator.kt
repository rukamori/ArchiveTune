/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val mutableRunning = MutableStateFlow(false)
    val isRunning = mutableRunning.asStateFlow()

    suspend fun <T> withLock(operation: suspend () -> T): T = mutex.withLock {
        mutableRunning.value = true
        try {
            operation()
        } finally {
            mutableRunning.value = false
        }
    }
}
