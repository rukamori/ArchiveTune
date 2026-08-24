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
        suspend operator fun invoke() {
            try {
                repository.test(repository.currentSettings().toServiceConfig())
                repository.setValidationStatus(AiApiValidationStatus.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repository.setValidationStatus(AiApiValidationStatus.FAILED)
                throw e
            }
        }
    }
