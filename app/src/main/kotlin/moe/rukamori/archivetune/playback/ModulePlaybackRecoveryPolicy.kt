/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.modules.ModulePlaybackDecision

enum class ModulePlaybackErrorCategory(val id: String) {
    HTTP("http"),
    NETWORK("network"),
    TIMEOUT("timeout"),
    PARSING("parsing"),
    DECODING("decoding"),
    AUDIO_OUTPUT("audio_output"),
    OTHER("other"),
}

sealed interface ModulePlaybackRecoveryStep {
    data class Retry(val attempt: Int) : ModulePlaybackRecoveryStep
    data object Fallback : ModulePlaybackRecoveryStep
    data class Fail(val message: String?) : ModulePlaybackRecoveryStep
    data object Default : ModulePlaybackRecoveryStep
}

internal class ModulePlaybackRecoveryPolicy {
    private class Budget(var retries: Int = 0, var fallbackUsed: Boolean = false)

    private var mediaId: String? = null
    private val budgets = HashMap<String, Budget>()

    @Synchronized
    fun step(mediaId: String, moduleId: String, decision: ModulePlaybackDecision): ModulePlaybackRecoveryStep {
        if (this.mediaId != mediaId) {
            this.mediaId = mediaId
            budgets.clear()
        }
        val budget = budgets.getOrPut(moduleId, ::Budget)
        return when (decision) {
            ModulePlaybackDecision.Retry -> if (budget.retries < MAX_RETRIES) {
                budget.retries++
                ModulePlaybackRecoveryStep.Retry(budget.retries)
            } else ModulePlaybackRecoveryStep.Default
            ModulePlaybackDecision.Fallback -> if (!budget.fallbackUsed) {
                budget.fallbackUsed = true
                ModulePlaybackRecoveryStep.Fallback
            } else ModulePlaybackRecoveryStep.Default
            is ModulePlaybackDecision.Fail -> ModulePlaybackRecoveryStep.Fail(sanitize(decision.message))
            ModulePlaybackDecision.Default -> ModulePlaybackRecoveryStep.Default
        }
    }

    @Synchronized
    fun retriesUsed(mediaId: String, moduleId: String): Int =
        if (this.mediaId == mediaId) budgets[moduleId]?.retries ?: 0 else 0

    @Synchronized
    fun onMediaItemChanged(currentMediaId: String?) {
        if (mediaId != currentMediaId) {
            mediaId = null
            budgets.clear()
        }
    }

    @Synchronized
    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && mediaId == this.mediaId) budgets.values.forEach { it.retries = 0 }
    }

    companion object {
        const val MAX_RETRIES = 2
        const val MAX_MESSAGE_LENGTH = 200

        fun category(errorCode: Int, httpStatus: Int?): ModulePlaybackErrorCategory = when {
            httpStatus != null || errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> ModulePlaybackErrorCategory.HTTP
            else -> when (errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> ModulePlaybackErrorCategory.NETWORK
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_TIMEOUT -> ModulePlaybackErrorCategory.TIMEOUT
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> ModulePlaybackErrorCategory.PARSING
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> ModulePlaybackErrorCategory.DECODING
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED -> ModulePlaybackErrorCategory.AUDIO_OUTPUT
                else -> ModulePlaybackErrorCategory.OTHER
            }
        }

        private fun sanitize(message: String?): String? = message
            ?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")
            ?.replace(Regex("\\s+"), " ")?.trim()
            ?.take(MAX_MESSAGE_LENGTH)?.trimEnd()
            ?.takeIf(String::isNotEmpty)
    }
}
