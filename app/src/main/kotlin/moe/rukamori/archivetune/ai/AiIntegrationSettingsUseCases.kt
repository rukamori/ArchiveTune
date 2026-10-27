/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiProvider
import javax.inject.Inject

class ObserveAiIntegrationSettingsUseCase
    @Inject
    constructor(
        private val repository: AiIntegrationSettingsRepository,
    ) {
        operator fun invoke(): Flow<AiIntegrationSettingsData> = repository.observeSettings()
    }

class UpdateAiIntegrationSettingsUseCase
    @Inject
    constructor(
        private val repository: AiIntegrationSettingsRepository,
    ) {
        suspend fun setProvider(provider: AiProvider) {
            repository.setProvider(provider)
        }

        suspend fun setApiKey(apiKey: String) {
            repository.setApiKey(apiKey)
        }

        suspend fun setCustomEndpoint(endpoint: String) {
            repository.setCustomEndpoint(endpoint)
        }

        suspend fun setSelectedModel(model: String) {
            repository.setSelectedModel(model)
        }

        suspend fun setCustomModel(model: String) {
            repository.setCustomModel(model)
        }

        suspend fun setCustomPrompt(prompt: String) {
            repository.setCustomPrompt(prompt)
        }

        suspend fun setRequestParameters(value: String) {
            repository.setRequestParameters(value)
        }
    }

class FetchAiModelsUseCase
    @Inject
    constructor(
        private val repository: AiIntegrationSettingsRepository,
    ) {
        suspend operator fun invoke(config: AiServiceConfig): List<AiModelOption> =
            repository.fetchModels(config)
    }

class TestAiIntegrationUseCase
    @Inject
    constructor(
        private val repository: AiIntegrationSettingsRepository,
    ) {
        suspend operator fun invoke(): Boolean {
            val config = repository.currentSettings().toServiceConfig()
            try {
                repository.test(config)
                return repository.setValidationStatus(AiApiValidationStatus.SUCCESS, config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (repository.setValidationStatus(AiApiValidationStatus.FAILED, config)) throw e
                return false
            }
        }
    }
