/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import javax.inject.Inject

internal sealed interface ModuleAuthorizationState {
    data object Loading : ModuleAuthorizationState
    data object Success : ModuleAuthorizationState
    data object Empty : ModuleAuthorizationState
    data object Error : ModuleAuthorizationState
}

@HiltViewModel
internal class ModuleAuthorizationViewModel @Inject constructor(private val useCases: ModuleSettingsUseCases) : ViewModel() {
    private val mutableState = MutableStateFlow<ModuleAuthorizationState>(ModuleAuthorizationState.Empty)
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun complete(callback: String?) {
        if (operation?.isActive == true || mutableState.value == ModuleAuthorizationState.Success) return
        mutableState.value = ModuleAuthorizationState.Loading
        operation = viewModelScope.launch {
            try {
                requireNotNull(callback)
                useCases.completeAuthorization(callback)
                mutableState.value = ModuleAuthorizationState.Success
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = ModuleAuthorizationState.Error }
        }
    }
}

@AndroidEntryPoint
class ModuleAuthorizationActivity : ComponentActivity() {
    private val viewModel: ModuleAuthorizationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accept(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ModuleAuthorizationState.Loading, ModuleAuthorizationState.Empty -> Unit
                        ModuleAuthorizationState.Success, ModuleAuthorizationState.Error -> {
                            Toast.makeText(this@ModuleAuthorizationActivity,
                                if (state == ModuleAuthorizationState.Success) R.string.modules_session_connected else R.string.modules_session_failed,
                                Toast.LENGTH_LONG).show()
                            if (state == ModuleAuthorizationState.Success) startActivity(Intent(this@ModuleAuthorizationActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(value: Intent) {
        val callback = value.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()
        value.data = null
        viewModel.complete(callback)
    }
}
