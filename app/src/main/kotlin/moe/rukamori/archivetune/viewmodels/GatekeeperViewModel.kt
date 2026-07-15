/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.gatekeeper.GatekeeperResult
import moe.rukamori.archivetune.gatekeeper.RunGatekeeperCheckUseCase
import javax.inject.Inject

@HiltViewModel
class GatekeeperViewModel
    @Inject
    constructor(
        runGatekeeperCheckUseCase: RunGatekeeperCheckUseCase,
    ) : ViewModel() {
        private val blockedMessageChannel = Channel<String>(capacity = 1)
        val blockedMessages: Flow<String> = blockedMessageChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                when (val result = runGatekeeperCheckUseCase()) {
                    GatekeeperResult.Allowed -> Unit
                    is GatekeeperResult.Blocked -> blockedMessageChannel.send(result.message)
                }
            }
        }

        override fun onCleared() {
            blockedMessageChannel.close()
        }
    }
